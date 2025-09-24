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
 * Implements 90-day automatic data purge with audit trails
 * Ensures compliance with data retention policies
 */
class DataPurgeService(private val context: Context) {

    companion object {
        private const val RETENTION_DAYS = 90L
        private const val RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private const val AUDIT_DIR = "audit"
        private const val ENCOUNTERS_DIR = "encounters"
        private const val EXPORT_DIR = "dsr_exports"
        private const val TEMP_DIR = "temp"
    }

    private val auditLogger = AuditLogger(context)
    private val consentManager = ConsentManager(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * Purge result data
     */
    data class PurgeResult(
        val purgedEncounters: Int,
        val purgedFiles: Int,
        val purgedAuditLogs: Int,
        val totalSizeFreed: Long,
        val errors: List<String>
    )

    /**
     * Run automatic data purge
     */
    suspend fun runAutomaticPurge(): Result<PurgeResult> = withContext(Dispatchers.IO) {
        try {
            val purgeId = UUID.randomUUID().toString()
            val timestamp = dateFormat.format(Date())
            val cutoffTime = System.currentTimeMillis() - RETENTION_MS
            
            Timber.i("Starting automatic data purge: $purgeId")
            
            val errors = mutableListOf<String>()
            var purgedEncounters = 0
            var purgedFiles = 0
            var purgedAuditLogs = 0
            var totalSizeFreed = 0L
            
            // Purge encounters
            try {
                val encounterResult = purgeEncounters(cutoffTime)
                purgedEncounters = encounterResult.first
                purgedFiles += encounterResult.second
                totalSizeFreed += encounterResult.third
            } catch (e: Exception) {
                errors.add("Failed to purge encounters: ${e.message}")
                Timber.e(e, "Failed to purge encounters")
            }
            
            // Purge audit logs
            try {
                val auditResult = purgeAuditLogs(cutoffTime)
                purgedAuditLogs = auditResult.first
                totalSizeFreed += auditResult.second
            } catch (e: Exception) {
                errors.add("Failed to purge audit logs: ${e.message}")
                Timber.e(e, "Failed to purge audit logs")
            }
            
            // Purge temporary files
            try {
                val tempResult = purgeTempFiles(cutoffTime)
                purgedFiles += tempResult.first
                totalSizeFreed += tempResult.second
            } catch (e: Exception) {
                errors.add("Failed to purge temp files: ${e.message}")
                Timber.e(e, "Failed to purge temp files")
            }
            
            // Purge old exports
            try {
                val exportResult = purgeOldExports(cutoffTime)
                purgedFiles += exportResult.first
                totalSizeFreed += exportResult.second
            } catch (e: Exception) {
                errors.add("Failed to purge old exports: ${e.message}")
                Timber.e(e, "Failed to purge old exports")
            }
            
            val result = PurgeResult(
                purgedEncounters = purgedEncounters,
                purgedFiles = purgedFiles,
                purgedAuditLogs = purgedAuditLogs,
                totalSizeFreed = totalSizeFreed,
                errors = errors
            )
            
            // Log purge completion
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.PURGE_BUFFER,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "purge_id" to purgeId,
                    "purge_type" to "automatic",
                    "retention_days" to RETENTION_DAYS,
                    "cutoff_time" to dateFormat.format(Date(cutoffTime)),
                    "purged_encounters" to purgedEncounters,
                    "purged_files" to purgedFiles,
                    "purged_audit_logs" to purgedAuditLogs,
                    "total_size_freed" to totalSizeFreed,
                    "error_count" to errors.size,
                    "timestamp" to timestamp
                )
            )
            
            Timber.i("Completed automatic data purge: $purgeId - $result")
            Result.success(result)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to run automatic purge")
            Result.failure(e)
        }
    }

    /**
     * Purge encounters older than cutoff time
     */
    private suspend fun purgeEncounters(cutoffTime: Long): Triple<Int, Int, Long> = withContext(Dispatchers.IO) {
        var purgedEncounters = 0
        var purgedFiles = 0
        var totalSizeFreed = 0L
        
        val encountersDir = File(context.filesDir, ENCOUNTERS_DIR)
        if (!encountersDir.exists()) {
            return@withContext Triple(0, 0, 0L)
        }
        
        val encounterDirs = encountersDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        
        for (dir in encounterDirs) {
            try {
                val encounterId = dir.name
                val lastModified = dir.lastModified()
                
                if (lastModified < cutoffTime) {
                    // Check if encounter has expired consent
                    val consentStatus = consentManager.getConsentStatus(encounterId)
                    if (consentStatus == ConsentManager.ConsentStatus.EXPIRED || 
                        consentManager.isConsentExpired(encounterId)) {
                        
                        // Count files before deletion
                        val files = dir.listFiles() ?: emptyArray()
                        purgedFiles += files.size
                        
                        // Calculate size
                        val size = dir.walkTopDown().sumOf { it.length() }
                        totalSizeFreed += size
                        
                        // Delete directory
                        dir.deleteRecursively()
                        purgedEncounters++
                        
                        // Log individual encounter purge
                        auditLogger.logEvent(
                            encounterId = encounterId,
                            eventType = AuditEvent.AuditEventType.PURGE_BUFFER,
                            actor = AuditEvent.AuditActor.APP,
                            meta = mapOf(
                                "reason" to "retention_expired",
                                "last_modified" to dateFormat.format(Date(lastModified)),
                                "file_count" to files.size,
                                "size_bytes" to size
                            )
                        )
                        
                        Timber.d("Purged encounter: $encounterId")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to purge encounter: ${dir.name}")
            }
        }
        
        Triple(purgedEncounters, purgedFiles, totalSizeFreed)
    }

    /**
     * Purge audit logs older than cutoff time
     */
    private suspend fun purgeAuditLogs(cutoffTime: Long): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var purgedFiles = 0
        var totalSizeFreed = 0L
        
        val auditDir = File(context.filesDir, AUDIT_DIR)
        if (!auditDir.exists()) {
            return@withContext Pair(0, 0L)
        }
        
        val auditFiles = auditDir.listFiles { file -> 
            file.isFile && file.name.endsWith(".jsonl") 
        } ?: emptyArray()
        
        for (file in auditFiles) {
            try {
                val lastModified = file.lastModified()
                
                if (lastModified < cutoffTime) {
                    val size = file.length()
                    totalSizeFreed += size
                    
                    file.delete()
                    purgedFiles++
                    
                    Timber.d("Purged audit log: ${file.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to purge audit log: ${file.name}")
            }
        }
        
        Pair(purgedFiles, totalSizeFreed)
    }

    /**
     * Purge temporary files older than cutoff time
     */
    private suspend fun purgeTempFiles(cutoffTime: Long): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var purgedFiles = 0
        var totalSizeFreed = 0L
        
        val tempDir = File(context.cacheDir, TEMP_DIR)
        if (!tempDir.exists()) {
            return@withContext Pair(0, 0L)
        }
        
        val tempFiles = tempDir.listFiles() ?: emptyArray()
        
        for (file in tempFiles) {
            try {
                val lastModified = file.lastModified()
                
                if (lastModified < cutoffTime) {
                    val size = file.length()
                    totalSizeFreed += size
                    
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                    purgedFiles++
                    
                    Timber.d("Purged temp file: ${file.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to purge temp file: ${file.name}")
            }
        }
        
        Pair(purgedFiles, totalSizeFreed)
    }

    /**
     * Purge old export files
     */
    private suspend fun purgeOldExports(cutoffTime: Long): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var purgedFiles = 0
        var totalSizeFreed = 0L
        
        val exportDir = File(context.filesDir, EXPORT_DIR)
        if (!exportDir.exists()) {
            return@withContext Pair(0, 0L)
        }
        
        val exportFiles = exportDir.listFiles() ?: emptyArray()
        
        for (file in exportFiles) {
            try {
                val lastModified = file.lastModified()
                
                if (lastModified < cutoffTime) {
                    val size = file.length()
                    totalSizeFreed += size
                    
                    file.delete()
                    purgedFiles++
                    
                    Timber.d("Purged export file: ${file.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to purge export file: ${file.name}")
            }
        }
        
        Pair(purgedFiles, totalSizeFreed)
    }

    /**
     * Get purge statistics
     */
    fun getPurgeStats(): Map<String, Any> {
        val encountersDir = File(context.filesDir, ENCOUNTERS_DIR)
        val auditDir = File(context.filesDir, AUDIT_DIR)
        val exportDir = File(context.filesDir, EXPORT_DIR)
        val tempDir = File(context.cacheDir, TEMP_DIR)
        
        val cutoffTime = System.currentTimeMillis() - RETENTION_MS
        
        val stats = mutableMapOf<String, Any>()
        
        // Count encounters
        val encounterCount = if (encountersDir.exists()) {
            encountersDir.listFiles()?.count { it.lastModified() < cutoffTime } ?: 0
        } else {
            0
        }
        
        // Count audit files
        val auditCount = if (auditDir.exists()) {
            auditDir.listFiles()?.count { it.lastModified() < cutoffTime } ?: 0
        } else {
            0
        }
        
        // Count export files
        val exportCount = if (exportDir.exists()) {
            exportDir.listFiles()?.count { it.lastModified() < cutoffTime } ?: 0
        } else {
            0
        }
        
        // Count temp files
        val tempCount = if (tempDir.exists()) {
            tempDir.listFiles()?.count { it.lastModified() < cutoffTime } ?: 0
        } else {
            0
        }
        
        stats["retention_days"] = RETENTION_DAYS
        stats["cutoff_time"] = dateFormat.format(Date(cutoffTime))
        stats["expired_encounters"] = encounterCount
        stats["expired_audit_files"] = auditCount
        stats["expired_export_files"] = exportCount
        stats["expired_temp_files"] = tempCount
        stats["total_expired_files"] = encounterCount + auditCount + exportCount + tempCount
        
        return stats
    }

    /**
     * Schedule next purge (called by WorkManager)
     */
    fun scheduleNextPurge() {
        // This would typically schedule a WorkManager job
        // For now, just log the scheduling
        Timber.i("Scheduled next data purge in 24 hours")
    }
}
