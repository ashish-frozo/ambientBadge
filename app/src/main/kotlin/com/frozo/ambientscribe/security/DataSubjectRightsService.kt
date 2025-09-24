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
 * Implements data subject rights (DSR) for DPDP compliance
 * Provides export and delete functionality by encounter/date
 */
class DataSubjectRightsService(private val context: Context) {

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private const val EXPORT_DIR = "dsr_exports"
        private const val AUDIT_DIR = "audit"
        private const val ENCOUNTERS_DIR = "encounters"
    }

    private val auditLogger = AuditLogger(context)
    private val consentManager = ConsentManager(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * DSR request types
     */
    enum class DSRRequestType {
        EXPORT,
        DELETE,
        RECTIFY
    }

    /**
     * DSR request status
     */
    enum class DSRRequestStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        REJECTED
    }

    /**
     * DSR request data
     */
    data class DSRRequest(
        val requestId: String,
        val requestType: DSRRequestType,
        val encounterId: String?,
        val dateFrom: String?,
        val dateTo: String?,
        val status: DSRRequestStatus,
        val requestedAt: String,
        val completedAt: String?,
        val actor: AuditEvent.AuditActor,
        val meta: Map<String, Any> = emptyMap()
    )

    /**
     * Export data for a specific encounter
     */
    suspend fun exportEncounterData(
        encounterId: String,
        actor: AuditEvent.AuditActor = AuditEvent.AuditActor.DOCTOR
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestId = UUID.randomUUID().toString()
            val timestamp = dateFormat.format(Date())
            
            // Check if encounter exists and has consent
            if (!consentManager.hasConsent(encounterId)) {
                return@withContext Result.failure(
                    IllegalStateException("No consent found for encounter: $encounterId")
                )
            }

            // Create export directory
            val exportDir = File(context.filesDir, EXPORT_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            // Export encounter data
            val exportFile = File(exportDir, "encounter_${encounterId}_${System.currentTimeMillis()}.json")
            val encounterData = collectEncounterData(encounterId)
            
            exportFile.writeText(encounterData)

            // Log audit event
            auditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.EXPORT,
                actor = actor,
                meta = mapOf(
                    "request_id" to requestId,
                    "export_file" to exportFile.name,
                    "timestamp" to timestamp
                )
            )

            Timber.i("Exported data for encounter: $encounterId")
            Result.success(exportFile.absolutePath)

        } catch (e: Exception) {
            Timber.e(e, "Failed to export encounter data: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Export data for date range
     */
    suspend fun exportDateRangeData(
        dateFrom: String,
        dateTo: String,
        actor: AuditEvent.AuditActor = AuditEvent.AuditActor.DOCTOR
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestId = UUID.randomUUID().toString()
            val timestamp = dateFormat.format(Date())
            
            // Find encounters in date range
            val encounters = findEncountersInDateRange(dateFrom, dateTo)
            
            if (encounters.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No encounters found in date range: $dateFrom to $dateTo")
                )
            }

            // Create export directory
            val exportDir = File(context.filesDir, EXPORT_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            // Export all encounters
            val exportFile = File(exportDir, "date_range_${dateFrom}_${dateTo}_${System.currentTimeMillis()}.json")
            val allData = collectDateRangeData(encounters, dateFrom, dateTo)
            
            exportFile.writeText(allData)

            // Log audit event for each encounter
            for (encounterId in encounters) {
                auditLogger.logEvent(
                    encounterId = encounterId,
                    eventType = AuditEvent.AuditEventType.EXPORT,
                    actor = actor,
                    meta = mapOf(
                        "request_id" to requestId,
                        "export_file" to exportFile.name,
                        "date_from" to dateFrom,
                        "date_to" to dateTo,
                        "timestamp" to timestamp
                    )
                )
            }

            Timber.i("Exported data for date range: $dateFrom to $dateTo (${encounters.size} encounters)")
            Result.success(exportFile.absolutePath)

        } catch (e: Exception) {
            Timber.e(e, "Failed to export date range data: $dateFrom to $dateTo")
            Result.failure(e)
        }
    }

    /**
     * Delete data for a specific encounter
     */
    suspend fun deleteEncounterData(
        encounterId: String,
        actor: AuditEvent.AuditActor = AuditEvent.AuditActor.DOCTOR
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            
            // Check if encounter exists
            if (!encounterExists(encounterId)) {
                return@withContext Result.failure(
                    IllegalStateException("Encounter not found: $encounterId")
                )
            }

            // Delete encounter files
            deleteEncounterFiles(encounterId)

            // Withdraw consent
            consentManager.withdrawConsent(encounterId, actor, mapOf("reason" to "dsr_delete"))

            // Log audit event
            auditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CONSENT_OFF,
                actor = actor,
                meta = mapOf(
                    "reason" to "dsr_delete",
                    "timestamp" to timestamp
                )
            )

            Timber.i("Deleted data for encounter: $encounterId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to delete encounter data: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Delete data for date range
     */
    suspend fun deleteDateRangeData(
        dateFrom: String,
        dateTo: String,
        actor: AuditEvent.AuditActor = AuditEvent.AuditActor.DOCTOR
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            
            // Find encounters in date range
            val encounters = findEncountersInDateRange(dateFrom, dateTo)
            
            if (encounters.isEmpty()) {
                return@withContext Result.success(0)
            }

            var deletedCount = 0
            
            // Delete each encounter
            for (encounterId in encounters) {
                try {
                    deleteEncounterFiles(encounterId)
                    consentManager.withdrawConsent(encounterId, actor, mapOf("reason" to "dsr_delete"))
                    deletedCount++
                    
                    // Log audit event
                    auditLogger.logEvent(
                        encounterId = encounterId,
                        eventType = AuditEvent.AuditEventType.CONSENT_OFF,
                        actor = actor,
                        meta = mapOf(
                            "reason" to "dsr_delete",
                            "date_from" to dateFrom,
                            "date_to" to dateTo,
                            "timestamp" to timestamp
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete encounter: $encounterId")
                }
            }

            Timber.i("Deleted data for date range: $dateFrom to $dateTo ($deletedCount encounters)")
            Result.success(deletedCount)

        } catch (e: Exception) {
            Timber.e(e, "Failed to delete date range data: $dateFrom to $dateTo")
            Result.failure(e)
        }
    }

    /**
     * Collect encounter data for export
     */
    private fun collectEncounterData(encounterId: String): String {
        val encounterDir = File(context.filesDir, "$ENCOUNTERS_DIR/$encounterId")
        val data = mutableMapOf<String, Any>()
        
        // Add encounter metadata
        data["encounter_id"] = encounterId
        data["exported_at"] = dateFormat.format(Date())
        data["consent_status"] = consentManager.getConsentStatus(encounterId).name
        
        // Add files if they exist
        if (encounterDir.exists()) {
            val files = encounterDir.listFiles() ?: emptyArray()
            val fileData = mutableMapOf<String, String>()
            
            for (file in files) {
                try {
                    fileData[file.name] = file.readText()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to read file: ${file.name}")
                }
            }
            
            data["files"] = fileData
        }
        
        // Convert to JSON (simplified)
        return data.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }.let { "{$it}" }
    }

    /**
     * Collect date range data
     */
    private fun collectDateRangeData(encounters: List<String>, dateFrom: String, dateTo: String): String {
        val data = mutableMapOf<String, Any>()
        
        data["date_from"] = dateFrom
        data["date_to"] = dateTo
        data["exported_at"] = dateFormat.format(Date())
        data["encounter_count"] = encounters.size
        
        val encounterData = mutableListOf<Map<String, Any>>()
        
        for (encounterId in encounters) {
            try {
                val encounterDataMap = mapOf(
                    "encounter_id" to encounterId,
                    "consent_status" to consentManager.getConsentStatus(encounterId).name
                )
                encounterData.add(encounterDataMap)
            } catch (e: Exception) {
                Timber.e(e, "Failed to collect data for encounter: $encounterId")
            }
        }
        
        data["encounters"] = encounterData
        
        // Convert to JSON (simplified)
        return data.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }.let { "{$it}" }
    }

    /**
     * Find encounters in date range
     */
    private fun findEncountersInDateRange(dateFrom: String, dateTo: String): List<String> {
        val encounters = mutableListOf<String>()
        val encountersDir = File(context.filesDir, ENCOUNTERS_DIR)
        
        if (!encountersDir.exists()) {
            return encounters
        }
        
        val encounterDirs = encountersDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        
        for (dir in encounterDirs) {
            try {
                val encounterId = dir.name
                val consentHistory = consentManager.getConsentHistory(encounterId)
                
                // Check if any consent event is in date range
                val inRange = consentHistory.any { event ->
                    isDateInRange(event.timestamp, dateFrom, dateTo)
                }
                
                if (inRange) {
                    encounters.add(encounterId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check encounter: ${dir.name}")
            }
        }
        
        return encounters
    }

    /**
     * Check if date is in range
     */
    private fun isDateInRange(date: String, dateFrom: String, dateTo: String): Boolean {
        return try {
            val dateTime = dateFormat.parse(date)?.time ?: 0L
            val fromTime = dateFormat.parse(dateFrom)?.time ?: 0L
            val toTime = dateFormat.parse(dateTo)?.time ?: Long.MAX_VALUE
            
            dateTime in fromTime..toTime
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if encounter exists
     */
    private fun encounterExists(encounterId: String): Boolean {
        val encounterDir = File(context.filesDir, "$ENCOUNTERS_DIR/$encounterId")
        return encounterDir.exists()
    }

    /**
     * Delete encounter files
     */
    private fun deleteEncounterFiles(encounterId: String) {
        val encounterDir = File(context.filesDir, "$ENCOUNTERS_DIR/$encounterId")
        if (encounterDir.exists()) {
            encounterDir.deleteRecursively()
        }
    }

    /**
     * Get DSR statistics
     */
    fun getDSRStats(): Map<String, Any> {
        val exportDir = File(context.filesDir, EXPORT_DIR)
        val encountersDir = File(context.filesDir, ENCOUNTERS_DIR)
        
        val exportCount = if (exportDir.exists()) {
            exportDir.listFiles()?.size ?: 0
        } else {
            0
        }
        
        val encounterCount = if (encountersDir.exists()) {
            encountersDir.listFiles()?.size ?: 0
        } else {
            0
        }
        
        val consentStats = consentManager.getConsentStats()
        
        return mapOf(
            "total_exports" to exportCount,
            "total_encounters" to encounterCount,
            "consent_stats" to consentStats
        )
    }
}
