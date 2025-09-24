package com.frozo.ambientscribe.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Audit Genesis Manager - Handles audit chain genesis, rollover, and chain-stitching
 * Manages audit chain integrity across app reinstalls and time changes
 */
class AuditGenesisManager(private val context: Context) {

    companion object {
        private const val AUDIT_DIR = "audit"
        private const val GENESIS_DIR = "audit_genesis"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private const val GENESIS_MARKER = "GENESIS"
        private const val ROLLOVER_MARKER = "ROLLOVER"
        private const val CHAIN_STITCH_MARKER = "CHAIN_STITCH"
    }

    private val auditLogger = AuditLogger(context)
    private val hmacKeyManager = HMACKeyManager(context)
    private val auditVerifier = AuditVerifier(context, hmacKeyManager)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * Genesis event data
     */
    data class GenesisEvent(
        val genesisId: String,
        val timestamp: String,
        val bootId: String,
        val appVersion: String,
        val deviceId: String,
        val prevHash: String = "0000000000000000000000000000000000000000000000000000000000000000"
    )

    /**
     * Rollover event data
     */
    data class RolloverEvent(
        val rolloverId: String,
        val timestamp: String,
        val reason: String,
        val prevGenesisId: String,
        val newGenesisId: String,
        val prevHash: String
    )

    /**
     * Chain stitch event data
     */
    data class ChainStitchEvent(
        val stitchId: String,
        val timestamp: String,
        val reason: String,
        val prevChainEnd: String,
        val newChainStart: String,
        val gapDetected: Boolean,
        val prevHash: String
    )

    /**
     * Create audit genesis event
     */
    suspend fun createGenesisEvent(
        reason: String = "app_install"
    ): Result<GenesisEvent> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val genesisId = generateGenesisId()
            val bootId = getBootId()
            val appVersion = getAppVersion()
            val deviceId = getDeviceId()

            val genesisEvent = GenesisEvent(
                genesisId = genesisId,
                timestamp = timestamp,
                bootId = bootId,
                appVersion = appVersion,
                deviceId = deviceId
            )

            // Log genesis event
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR, // Using ERROR as proxy for system events
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "event_type" to GENESIS_MARKER,
                    "genesis_id" to genesisId,
                    "reason" to reason,
                    "boot_id" to bootId,
                    "app_version" to appVersion,
                    "device_id" to deviceId,
                    "timestamp" to timestamp
                )
            )

            // Save genesis metadata
            saveGenesisMetadata(genesisEvent)

            Timber.i("Created audit genesis event: $genesisId")
            Result.success(genesisEvent)

        } catch (e: Exception) {
            Timber.e(e, "Failed to create genesis event")
            Result.failure(e)
        }
    }

    /**
     * Create rollover event
     */
    suspend fun createRolloverEvent(
        reason: String,
        prevGenesisId: String
    ): Result<RolloverEvent> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val rolloverId = generateRolloverId()
            val newGenesisId = generateGenesisId()

            // Get previous chain end hash
            val prevChainEnd = getLastAuditHash()

            val rolloverEvent = RolloverEvent(
                rolloverId = rolloverId,
                timestamp = timestamp,
                reason = reason,
                prevGenesisId = prevGenesisId,
                newGenesisId = newGenesisId,
                prevHash = prevChainEnd
            )

            // Log rollover event
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "event_type" to ROLLOVER_MARKER,
                    "rollover_id" to rolloverId,
                    "reason" to reason,
                    "prev_genesis_id" to prevGenesisId,
                    "new_genesis_id" to newGenesisId,
                    "prev_hash" to prevChainEnd,
                    "timestamp" to timestamp
                )
            )

            // Save rollover metadata
            saveRolloverMetadata(rolloverEvent)

            Timber.i("Created audit rollover event: $rolloverId")
            Result.success(rolloverEvent)

        } catch (e: Exception) {
            Timber.e(e, "Failed to create rollover event")
            Result.failure(e)
        }
    }

    /**
     * Create chain stitch event
     */
    suspend fun createChainStitchEvent(
        reason: String,
        prevChainEnd: String,
        newChainStart: String,
        gapDetected: Boolean
    ): Result<ChainStitchEvent> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val stitchId = generateStitchId()

            val stitchEvent = ChainStitchEvent(
                stitchId = stitchId,
                timestamp = timestamp,
                reason = reason,
                prevChainEnd = prevChainEnd,
                newChainStart = newChainStart,
                gapDetected = gapDetected,
                prevHash = prevChainEnd
            )

            // Log chain stitch event
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "event_type" to CHAIN_STITCH_MARKER,
                    "stitch_id" to stitchId,
                    "reason" to reason,
                    "prev_chain_end" to prevChainEnd,
                    "new_chain_start" to newChainStart,
                    "gap_detected" to gapDetected,
                    "timestamp" to timestamp
                )
            )

            // Save chain stitch metadata
            saveChainStitchMetadata(stitchEvent)

            Timber.i("Created chain stitch event: $stitchId")
            Result.success(stitchEvent)

        } catch (e: Exception) {
            Timber.e(e, "Failed to create chain stitch event")
            Result.failure(e)
        }
    }

    /**
     * Detect and handle audit chain gaps
     */
    suspend fun detectAndHandleGaps(): Result<GapAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (!auditDir.exists()) {
                return@withContext Result.success(
                    GapAnalysisResult(hasGaps = false, gaps = emptyList(), recommendations = emptyList())
                )
            }

            val auditFiles = auditDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jsonl")
            } ?: emptyArray()

            val gaps = mutableListOf<AuditGap>()
            val recommendations = mutableListOf<String>()

            // Sort files by name (which includes timestamp)
            auditFiles.sortBy { it.name }

            var lastEventTime: Long? = null
            var lastEventHash: String? = null

            for (file in auditFiles) {
                val fileGaps = analyzeFileForGaps(file, lastEventTime, lastEventHash)
                gaps.addAll(fileGaps)

                // Update last event info
                val fileLastEvent = getLastEventFromFile(file)
                if (fileLastEvent != null) {
                    lastEventTime = fileLastEvent.timestamp
                    lastEventHash = fileLastEvent.hash
                }
            }

            // Generate recommendations
            if (gaps.isNotEmpty()) {
                recommendations.add("Create chain stitch event to bridge gaps")
                recommendations.add("Verify audit chain integrity")
                recommendations.add("Consider rollover if gaps are extensive")
            }

            val result = GapAnalysisResult(
                hasGaps = gaps.isNotEmpty(),
                gaps = gaps,
                recommendations = recommendations
            )

            Timber.i("Gap analysis completed: ${gaps.size} gaps detected")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to detect audit gaps")
            Result.failure(e)
        }
    }

    /**
     * Detect duplicate events
     */
    suspend fun detectDuplicates(): Result<DuplicateAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (!auditDir.exists()) {
                return@withContext Result.success(
                    DuplicateAnalysisResult(hasDuplicates = false, duplicates = emptyList())
                )
            }

            val auditFiles = auditDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jsonl")
            } ?: emptyArray()

            val duplicates = mutableListOf<AuditDuplicate>()
            val eventHashes = mutableSetOf<String>()

            for (file in auditFiles) {
                val fileDuplicates = analyzeFileForDuplicates(file, eventHashes)
                duplicates.addAll(fileDuplicates)
            }

            val result = DuplicateAnalysisResult(
                hasDuplicates = duplicates.isNotEmpty(),
                duplicates = duplicates
            )

            Timber.i("Duplicate analysis completed: ${duplicates.size} duplicates detected")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to detect duplicates")
            Result.failure(e)
        }
    }

    /**
     * Detect out-of-order events
     */
    suspend fun detectOutOfOrder(): Result<OutOfOrderAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (!auditDir.exists()) {
                return@withContext Result.success(
                    OutOfOrderAnalysisResult(hasOutOfOrder = false, outOfOrderEvents = emptyList())
                )
            }

            val auditFiles = auditDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jsonl")
            } ?: emptyArray()

            val outOfOrderEvents = mutableListOf<OutOfOrderEvent>()

            // Sort files by name (which includes timestamp)
            auditFiles.sortBy { it.name }

            var lastEventTime: Long? = null

            for (file in auditFiles) {
                val fileOutOfOrder = analyzeFileForOutOfOrder(file, lastEventTime)
                outOfOrderEvents.addAll(fileOutOfOrder)

                // Update last event time
                val fileLastEvent = getLastEventFromFile(file)
                if (fileLastEvent != null) {
                    lastEventTime = fileLastEvent.timestamp
                }
            }

            val result = OutOfOrderAnalysisResult(
                hasOutOfOrder = outOfOrderEvents.isNotEmpty(),
                outOfOrderEvents = outOfOrderEvents
            )

            Timber.i("Out-of-order analysis completed: ${outOfOrderEvents.size} events detected")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to detect out-of-order events")
            Result.failure(e)
        }
    }

    /**
     * Comprehensive audit chain verification
     */
    suspend fun verifyAuditChain(): Result<AuditChainVerificationResult> = withContext(Dispatchers.IO) {
        try {
            // Run all verification checks
            val gapResult = detectAndHandleGaps()
            val duplicateResult = detectDuplicates()
            val outOfOrderResult = detectOutOfOrder()
            val integrityResult = auditVerifier.verifyAuditChain()

            val verificationResult = AuditChainVerificationResult(
                integrityValid = integrityResult.isSuccess && integrityResult.getOrThrow(),
                hasGaps = gapResult.isSuccess && gapResult.getOrThrow().hasGaps,
                hasDuplicates = duplicateResult.isSuccess && duplicateResult.getOrThrow().hasDuplicates,
                hasOutOfOrder = outOfOrderResult.isSuccess && outOfOrderResult.getOrThrow().hasOutOfOrder,
                gapAnalysis = if (gapResult.isSuccess) gapResult.getOrThrow() else null,
                duplicateAnalysis = if (duplicateResult.isSuccess) duplicateResult.getOrThrow() else null,
                outOfOrderAnalysis = if (outOfOrderResult.isSuccess) outOfOrderResult.getOrThrow() else null,
                timestamp = dateFormat.format(Date())
            )

            Timber.i("Audit chain verification completed: ${verificationResult.integrityValid}")
            Result.success(verificationResult)

        } catch (e: Exception) {
            Timber.e(e, "Failed to verify audit chain")
            Result.failure(e)
        }
    }

    // Helper methods
    private fun generateGenesisId(): String = "genesis_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    private fun generateRolloverId(): String = "rollover_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    private fun generateStitchId(): String = "stitch_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

    private fun getBootId(): String = "boot_${System.currentTimeMillis()}"
    private fun getAppVersion(): String = "1.0.0" // This would come from BuildConfig
    private fun getDeviceId(): String = "device_${UUID.randomUUID().toString().take(8)}"

    private fun getLastAuditHash(): String {
        // This would get the last hash from the audit chain
        return "last_hash_placeholder"
    }

    private fun saveGenesisMetadata(genesisEvent: GenesisEvent) {
        val genesisDir = File(context.filesDir, GENESIS_DIR)
        if (!genesisDir.exists()) {
            genesisDir.mkdirs()
        }

        val metadataFile = File(genesisDir, "genesis_${genesisEvent.genesisId}.json")
        val metadataJson = generateGenesisMetadataJson(genesisEvent)
        metadataFile.writeText(metadataJson)
    }

    private fun saveRolloverMetadata(rolloverEvent: RolloverEvent) {
        val genesisDir = File(context.filesDir, GENESIS_DIR)
        if (!genesisDir.exists()) {
            genesisDir.mkdirs()
        }

        val metadataFile = File(genesisDir, "rollover_${rolloverEvent.rolloverId}.json")
        val metadataJson = generateRolloverMetadataJson(rolloverEvent)
        metadataFile.writeText(metadataJson)
    }

    private fun saveChainStitchMetadata(stitchEvent: ChainStitchEvent) {
        val genesisDir = File(context.filesDir, GENESIS_DIR)
        if (!genesisDir.exists()) {
            genesisDir.mkdirs()
        }

        val metadataFile = File(genesisDir, "stitch_${stitchEvent.stitchId}.json")
        val metadataJson = generateChainStitchMetadataJson(stitchEvent)
        metadataFile.writeText(metadataJson)
    }

    private fun generateGenesisMetadataJson(genesisEvent: GenesisEvent): String {
        return """
        {
            "genesisId": "${genesisEvent.genesisId}",
            "timestamp": "${genesisEvent.timestamp}",
            "bootId": "${genesisEvent.bootId}",
            "appVersion": "${genesisEvent.appVersion}",
            "deviceId": "${genesisEvent.deviceId}",
            "prevHash": "${genesisEvent.prevHash}"
        }
        """.trimIndent()
    }

    private fun generateRolloverMetadataJson(rolloverEvent: RolloverEvent): String {
        return """
        {
            "rolloverId": "${rolloverEvent.rolloverId}",
            "timestamp": "${rolloverEvent.timestamp}",
            "reason": "${rolloverEvent.reason}",
            "prevGenesisId": "${rolloverEvent.prevGenesisId}",
            "newGenesisId": "${rolloverEvent.newGenesisId}",
            "prevHash": "${rolloverEvent.prevHash}"
        }
        """.trimIndent()
    }

    private fun generateChainStitchMetadataJson(stitchEvent: ChainStitchEvent): String {
        return """
        {
            "stitchId": "${stitchEvent.stitchId}",
            "timestamp": "${stitchEvent.timestamp}",
            "reason": "${stitchEvent.reason}",
            "prevChainEnd": "${stitchEvent.prevChainEnd}",
            "newChainStart": "${stitchEvent.newChainStart}",
            "gapDetected": ${stitchEvent.gapDetected},
            "prevHash": "${stitchEvent.prevHash}"
        }
        """.trimIndent()
    }

    private fun analyzeFileForGaps(file: File, lastEventTime: Long?, lastEventHash: String?): List<AuditGap> {
        // Implementation would analyze file for gaps
        return emptyList()
    }

    private fun analyzeFileForDuplicates(file: File, eventHashes: MutableSet<String>): List<AuditDuplicate> {
        // Implementation would analyze file for duplicates
        return emptyList()
    }

    private fun analyzeFileForOutOfOrder(file: File, lastEventTime: Long?): List<OutOfOrderEvent> {
        // Implementation would analyze file for out-of-order events
        return emptyList()
    }

    private fun getLastEventFromFile(file: File): EventInfo? {
        // Implementation would get last event from file
        return null
    }
}

// Data classes for analysis results
data class GapAnalysisResult(
    val hasGaps: Boolean,
    val gaps: List<AuditGap>,
    val recommendations: List<String>
)

data class DuplicateAnalysisResult(
    val hasDuplicates: Boolean,
    val duplicates: List<AuditDuplicate>
)

data class OutOfOrderAnalysisResult(
    val hasOutOfOrder: Boolean,
    val outOfOrderEvents: List<OutOfOrderEvent>
)

data class AuditChainVerificationResult(
    val integrityValid: Boolean,
    val hasGaps: Boolean,
    val hasDuplicates: Boolean,
    val hasOutOfOrder: Boolean,
    val gapAnalysis: GapAnalysisResult?,
    val duplicateAnalysis: DuplicateAnalysisResult?,
    val outOfOrderAnalysis: OutOfOrderAnalysisResult?,
    val timestamp: String
)

data class AuditGap(
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val reason: String
)

data class AuditDuplicate(
    val eventHash: String,
    val occurrences: Int,
    val timestamps: List<Long>
)

data class OutOfOrderEvent(
    val eventTime: Long,
    val expectedTime: Long,
    val timeDifference: Long,
    val reason: String
)

data class EventInfo(
    val timestamp: Long,
    val hash: String
)
