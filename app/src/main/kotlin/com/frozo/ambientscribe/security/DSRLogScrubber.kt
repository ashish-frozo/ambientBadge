package com.frozo.ambientscribe.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * DSR log scrubbing job for removing encounter↔patient mapping while preserving audit integrity
 * Implements data subject rights compliance by scrubbing PHI from logs
 */
class DSRLogScrubber(private val context: Context) {

    companion object {
        private const val AUDIT_DIR = "audit"
        private const val SCRUBBED_DIR = "audit_scrubbed"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private const val SCRUB_MARKER = "[SCRUBBED]"
    }

    private val auditLogger = AuditLogger(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * Scrubbing result
     */
    data class ScrubbingResult(
        val scrubbedFiles: Int,
        val scrubbedEvents: Int,
        val preservedEvents: Int,
        val totalSizeReduced: Long,
        val errors: List<String>
    )

    /**
     * Scrub audit logs for specific encounter
     */
    suspend fun scrubEncounterLogs(
        encounterId: String,
        reason: String = "dsr_request"
    ): Result<ScrubbingResult> = withContext(Dispatchers.IO) {
        try {
            val errors = mutableListOf<String>()
            var scrubbedFiles = 0
            var scrubbedEvents = 0
            var preservedEvents = 0
            var totalSizeReduced = 0L

            val auditDir = File(context.filesDir, AUDIT_DIR)
            val scrubbedDir = File(context.filesDir, SCRUBBED_DIR)
            
            if (!auditDir.exists()) {
                return@withContext Result.success(
                    ScrubbingResult(0, 0, 0, 0, listOf("No audit directory found"))
                )
            }

            if (!scrubbedDir.exists()) {
                scrubbedDir.mkdirs()
            }

            val auditFiles = auditDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jsonl")
            } ?: emptyArray()

            for (file in auditFiles) {
                try {
                    val result = scrubAuditFile(file, encounterId, scrubbedDir)
                    scrubbedFiles += result.first
                    scrubbedEvents += result.second
                    preservedEvents += result.third
                    totalSizeReduced += result.fourth
                } catch (e: Exception) {
                    errors.add("Failed to scrub file ${file.name}: ${e.message}")
                    Timber.e(e, "Failed to scrub file: ${file.name}")
                }
            }

            // Log scrubbing operation
            auditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CONSENT_OFF,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "reason" to "dsr_scrub",
                    "scrub_reason" to reason,
                    "scrubbed_files" to scrubbedFiles,
                    "scrubbed_events" to scrubbedEvents,
                    "preserved_events" to preservedEvents,
                    "size_reduced" to totalSizeReduced
                )
            )

            val result = ScrubbingResult(
                scrubbedFiles = scrubbedFiles,
                scrubbedEvents = scrubbedEvents,
                preservedEvents = preservedEvents,
                totalSizeReduced = totalSizeReduced,
                errors = errors
            )

            Timber.i("Scrubbed logs for encounter: $encounterId - $result")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to scrub encounter logs: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Scrub audit file for specific encounter
     */
    private suspend fun scrubAuditFile(
        auditFile: File,
        encounterId: String,
        scrubbedDir: File
    ): Quadruple<Int, Int, Int, Long> = withContext(Dispatchers.IO) {
        var scrubbedFiles = 0
        var scrubbedEvents = 0
        var preservedEvents = 0
        var totalSizeReduced = 0L

        val lines = auditFile.readLines()
        val scrubbedLines = mutableListOf<String>()
        val originalSize = auditFile.length()

        for (line in lines) {
            if (line.trim().isEmpty()) {
                scrubbedLines.add(line)
                continue
            }

            try {
                val event = parseAuditEvent(line)
                if (event != null && event.encounterId == encounterId) {
                    // Scrub this event
                    val scrubbedEvent = scrubAuditEvent(event)
                    scrubbedLines.add(scrubbedEvent)
                    scrubbedEvents++
                } else {
                    // Preserve this event
                    scrubbedLines.add(line)
                    preservedEvents++
                }
            } catch (e: Exception) {
                // If parsing fails, preserve the original line
                scrubbedLines.add(line)
                preservedEvents++
                Timber.w(e, "Failed to parse audit event, preserving original")
            }
        }

        // Write scrubbed file
        val scrubbedFile = File(scrubbedDir, "scrubbed_${auditFile.name}")
        scrubbedFile.writeText(scrubbedLines.joinToString("\n"))
        
        val scrubbedSize = scrubbedFile.length()
        totalSizeReduced = originalSize - scrubbedSize
        
        if (scrubbedEvents > 0) {
            scrubbedFiles = 1
        }

        Quadruple(scrubbedFiles, scrubbedEvents, preservedEvents, totalSizeReduced)
    }

    /**
     * Scrub individual audit event
     */
    private fun scrubAuditEvent(event: AuditEvent): String {
        // Create scrubbed version of the event
        val scrubbedMeta = event.meta.toMutableMap()
        
        // Remove or scrub PHI-related metadata
        scrubbedMeta.remove("patient_hash")
        scrubbedMeta.remove("patient_id")
        scrubbedMeta.remove("patient_name")
        scrubbedMeta.remove("phone")
        scrubbedMeta.remove("mrn")
        
        // Add scrubbing marker
        scrubbedMeta["scrubbed"] = "true"
        scrubbedMeta["scrub_timestamp"] = dateFormat.format(Date())
        scrubbedMeta["scrub_reason"] = "dsr_request"

        val scrubbedEvent = event.copy(
            encounterId = generateScrubbedEncounterId(event.encounterId),
            meta = scrubbedMeta
        )

        return scrubbedEvent.toJsonString()
    }

    /**
     * Generate scrubbed encounter ID
     */
    private fun generateScrubbedEncounterId(originalId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(originalId.toByteArray())
        val hashString = hash.joinToString("") { "%02x".format(it) }
        return "scrubbed_${hashString.take(8)}"
    }

    /**
     * Parse audit event from JSON line
     */
    private fun parseAuditEvent(jsonLine: String): AuditEvent? {
        return try {
            // Simple JSON parsing - in production, use a proper JSON library
            val cleanJson = jsonLine.trim().removePrefix("{").removeSuffix("}")
            val pairs = cleanJson.split(",").map { it.trim() }
            
            var encounterId = ""
            var kid = ""
            var prevHash = ""
            var event = AuditEvent.AuditEventType.ERROR
            var ts = ""
            var actor = AuditEvent.AuditActor.APP
            val meta = mutableMapOf<String, Any>()
            
            for (pair in pairs) {
                val (key, value) = pair.split(":", limit = 2)
                val cleanKey = key.trim().removeSurrounding("\"")
                val cleanValue = value.trim().removeSurrounding("\"")
                
                when (cleanKey) {
                    "encounter_id" -> encounterId = cleanValue
                    "kid" -> kid = cleanValue
                    "prev_hash" -> prevHash = cleanValue
                    "event" -> event = AuditEvent.AuditEventType.valueOf(cleanValue)
                    "ts" -> ts = cleanValue
                    "actor" -> actor = AuditEvent.AuditActor.valueOf(cleanValue)
                    "meta" -> {
                        // Parse meta object (simplified)
                        if (cleanValue != "{}") {
                            val metaPairs = cleanValue.removeSurrounding("{", "}").split(",")
                            for (metaPair in metaPairs) {
                                val (metaKey, metaValue) = metaPair.split(":", limit = 2)
                                meta[metaKey.trim().removeSurrounding("\"")] = metaValue.trim().removeSurrounding("\"")
                            }
                        }
                    }
                }
            }
            
            AuditEvent(encounterId, kid, prevHash, event, ts, actor, meta)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse audit event")
            null
        }
    }

    /**
     * Scrub all logs for date range
     */
    suspend fun scrubDateRangeLogs(
        dateFrom: String,
        dateTo: String,
        reason: String = "dsr_request"
    ): Result<ScrubbingResult> = withContext(Dispatchers.IO) {
        try {
            val errors = mutableListOf<String>()
            var scrubbedFiles = 0
            var scrubbedEvents = 0
            var preservedEvents = 0
            var totalSizeReduced = 0L

            val auditDir = File(context.filesDir, AUDIT_DIR)
            val scrubbedDir = File(context.filesDir, SCRUBBED_DIR)
            
            if (!auditDir.exists()) {
                return@withContext Result.success(
                    ScrubbingResult(0, 0, 0, 0, listOf("No audit directory found"))
                )
            }

            if (!scrubbedDir.exists()) {
                scrubbedDir.mkdirs()
            }

            val auditFiles = auditDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jsonl")
            } ?: emptyArray()

            for (file in auditFiles) {
                try {
                    val result = scrubAuditFileByDateRange(file, dateFrom, dateTo, scrubbedDir)
                    scrubbedFiles += result.first
                    scrubbedEvents += result.second
                    preservedEvents += result.third
                    totalSizeReduced += result.fourth
                } catch (e: Exception) {
                    errors.add("Failed to scrub file ${file.name}: ${e.message}")
                    Timber.e(e, "Failed to scrub file: ${file.name}")
                }
            }

            // Log scrubbing operation
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.CONSENT_OFF,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "reason" to "dsr_scrub",
                    "scrub_reason" to reason,
                    "date_from" to dateFrom,
                    "date_to" to dateTo,
                    "scrubbed_files" to scrubbedFiles,
                    "scrubbed_events" to scrubbedEvents,
                    "preserved_events" to preservedEvents,
                    "size_reduced" to totalSizeReduced
                )
            )

            val result = ScrubbingResult(
                scrubbedFiles = scrubbedFiles,
                scrubbedEvents = scrubbedEvents,
                preservedEvents = preservedEvents,
                totalSizeReduced = totalSizeReduced,
                errors = errors
            )

            Timber.i("Scrubbed logs for date range: $dateFrom to $dateTo - $result")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to scrub date range logs: $dateFrom to $dateTo")
            Result.failure(e)
        }
    }

    /**
     * Scrub audit file by date range
     */
    private suspend fun scrubAuditFileByDateRange(
        auditFile: File,
        dateFrom: String,
        dateTo: String,
        scrubbedDir: File
    ): Quadruple<Int, Int, Int, Long> = withContext(Dispatchers.IO) {
        var scrubbedFiles = 0
        var scrubbedEvents = 0
        var preservedEvents = 0
        var totalSizeReduced = 0L

        val lines = auditFile.readLines()
        val scrubbedLines = mutableListOf<String>()
        val originalSize = auditFile.length()

        for (line in lines) {
            if (line.trim().isEmpty()) {
                scrubbedLines.add(line)
                continue
            }

            try {
                val event = parseAuditEvent(line)
                if (event != null && isEventInDateRange(event, dateFrom, dateTo)) {
                    // Scrub this event
                    val scrubbedEvent = scrubAuditEvent(event)
                    scrubbedLines.add(scrubbedEvent)
                    scrubbedEvents++
                } else {
                    // Preserve this event
                    scrubbedLines.add(line)
                    preservedEvents++
                }
            } catch (e: Exception) {
                // If parsing fails, preserve the original line
                scrubbedLines.add(line)
                preservedEvents++
                Timber.w(e, "Failed to parse audit event, preserving original")
            }
        }

        // Write scrubbed file
        val scrubbedFile = File(scrubbedDir, "scrubbed_${auditFile.name}")
        scrubbedFile.writeText(scrubbedLines.joinToString("\n"))
        
        val scrubbedSize = scrubbedFile.length()
        totalSizeReduced = originalSize - scrubbedSize
        
        if (scrubbedEvents > 0) {
            scrubbedFiles = 1
        }

        Quadruple(scrubbedFiles, scrubbedEvents, preservedEvents, totalSizeReduced)
    }

    /**
     * Check if event is in date range
     */
    private fun isEventInDateRange(event: AuditEvent, dateFrom: String, dateTo: String): Boolean {
        return try {
            val eventTime = dateFormat.parse(event.ts)?.time ?: 0L
            val fromTime = dateFormat.parse(dateFrom)?.time ?: 0L
            val toTime = dateFormat.parse(dateTo)?.time ?: Long.MAX_VALUE
            
            eventTime in fromTime..toTime
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get scrubbing statistics
     */
    fun getScrubbingStats(): Map<String, Any> {
        val auditDir = File(context.filesDir, AUDIT_DIR)
        val scrubbedDir = File(context.filesDir, SCRUBBED_DIR)
        
        val originalFiles = if (auditDir.exists()) {
            auditDir.listFiles()?.size ?: 0
        } else {
            0
        }
        
        val scrubbedFiles = if (scrubbedDir.exists()) {
            scrubbedDir.listFiles()?.size ?: 0
        } else {
            0
        }
        
        return mapOf(
            "original_files" to originalFiles,
            "scrubbed_files" to scrubbedFiles,
            "scrubbing_ratio" to if (originalFiles > 0) scrubbedFiles.toDouble() / originalFiles else 0.0
        )
    }

    /**
     * Clean up old scrubbed files
     */
    suspend fun cleanupOldScrubbedFiles(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val scrubbedDir = File(context.filesDir, SCRUBBED_DIR)
            if (!scrubbedDir.exists()) {
                return@withContext Result.success(0)
            }

            val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
            val files = scrubbedDir.listFiles() ?: emptyArray()
            var cleanedCount = 0

            for (file in files) {
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        cleanedCount++
                        Timber.d("Cleaned up old scrubbed file: ${file.name}")
                    }
                }
            }

            Timber.i("Cleaned up $cleanedCount old scrubbed files")
            Result.success(cleanedCount)

        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old scrubbed files")
            Result.failure(e)
        }
    }
}

/**
 * Data class for returning multiple values
 */
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
