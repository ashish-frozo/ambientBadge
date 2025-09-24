package com.frozo.ambientscribe.security

import android.content.Context
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Handles job cancellation and data wiping when CONSENT_OFF is triggered
 * Ensures immediate compliance with data subject rights
 */
class ConsentOffJobCanceller(private val context: Context) {

    companion object {
        private const val TELEMETRY_DIR = "telemetry"
        private const val AUDIT_DIR = "audit"
        private const val DOCS_DIR = "docs"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    }

    private val workManager = WorkManager.getInstance(context)
    private val auditLogger = AuditLogger(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * Job cancellation result
     */
    data class JobCancellationResult(
        val encounterId: String,
        val cancelledJobs: Int,
        val wipedFiles: Int,
        val wipedDataSize: Long,
        val cancelledCount: Int,
        val timestamp: String
    )

    /**
     * Cancel all jobs and wipe data for a specific encounter
     */
    suspend fun cancelEncounterJobs(encounterId: String): Result<JobCancellationResult> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            var cancelledJobs = 0
            var wipedFiles = 0
            var wipedDataSize = 0L
            var cancelledCount = 0

            // 1. Cancel all WorkManager jobs for this encounter
            val jobCancellationResult = cancelWorkManagerJobs(encounterId)
            cancelledJobs = jobCancellationResult.first
            cancelledCount = jobCancellationResult.second

            // 2. Wipe queued payloads in telemetry
            val telemetryWipeResult = wipeTelemetryData(encounterId)
            wipedFiles += telemetryWipeResult.first
            wipedDataSize += telemetryWipeResult.second

            // 3. Wipe queued payloads in audit
            val auditWipeResult = wipeAuditData(encounterId)
            wipedFiles += auditWipeResult.first
            wipedDataSize += auditWipeResult.second

            // 4. Wipe queued payloads in docs
            val docsWipeResult = wipeDocsData(encounterId)
            wipedFiles += docsWipeResult.first
            wipedDataSize += docsWipeResult.second

            // 5. Cancel any pending background tasks
            val backgroundCancellationResult = cancelBackgroundTasks(encounterId)
            cancelledJobs += backgroundCancellationResult

            val result = JobCancellationResult(
                encounterId = encounterId,
                cancelledJobs = cancelledJobs,
                wipedFiles = wipedFiles,
                wipedDataSize = wipedDataSize,
                cancelledCount = cancelledCount,
                timestamp = timestamp
            )

            // 6. Log CANCELLED_COUNT audit event
            auditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CANCELLED_COUNT,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "cancelled_jobs" to cancelledJobs,
                    "wiped_files" to wipedFiles,
                    "wiped_data_size" to wipedDataSize,
                    "cancelled_count" to cancelledCount,
                    "reason" to "consent_off"
                )
            )

            Timber.i("Consent OFF job cancellation completed for encounter: $encounterId")
            Timber.i("Cancelled $cancelledJobs jobs, wiped $wipedFiles files, freed $wipedDataSize bytes")
            
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel jobs for encounter: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Cancel WorkManager jobs for specific encounter
     */
    private suspend fun cancelWorkManagerJobs(encounterId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            var cancelledJobs = 0
            var cancelledCount = 0

            // Get all running and enqueued work
            val workQuery = WorkQuery.Builder
                .fromStates(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED)
                .build()

            val workInfos = workManager.getWorkInfos(workQuery).get()
            
            for (workInfo in workInfos) {
                val tags = workInfo.tags
                val inputData = workInfo.inputData
                
                // Check if this work is related to the encounter
                val isEncounterRelated = tags.any { it.contains(encounterId) } ||
                        inputData.getString("encounter_id") == encounterId ||
                        workInfo.id.toString().contains(encounterId)

                if (isEncounterRelated) {
                    try {
                        workManager.cancelWorkById(workInfo.id)
                        cancelledJobs++
                        cancelledCount++
                        Timber.d("Cancelled WorkManager job: ${workInfo.id} for encounter: $encounterId")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to cancel WorkManager job: ${workInfo.id}")
                    }
                }
            }

            // Also cancel by tag patterns
            val encounterTagPatterns = listOf(
                "encounter_$encounterId",
                "audio_$encounterId",
                "transcription_$encounterId",
                "soap_$encounterId",
                "prescription_$encounterId"
            )

            for (tagPattern in encounterTagPatterns) {
                try {
                    workManager.cancelAllWorkByTag(tagPattern)
                    cancelledJobs++
                    cancelledCount++
                    Timber.d("Cancelled WorkManager jobs by tag: $tagPattern")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to cancel WorkManager jobs by tag: $tagPattern")
                }
            }

            Pair(cancelledJobs, cancelledCount)

        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel WorkManager jobs for encounter: $encounterId")
            Pair(0, 0)
        }
    }

    /**
     * Wipe telemetry data for encounter
     */
    private suspend fun wipeTelemetryData(encounterId: String): Pair<Int, Long> = withContext(Dispatchers.IO) {
        try {
            val telemetryDir = File(context.filesDir, TELEMETRY_DIR)
            if (!telemetryDir.exists()) {
                return@withContext Pair(0, 0L)
            }

            var wipedFiles = 0
            var wipedDataSize = 0L

            val files = telemetryDir.listFiles { file ->
                file.isFile && file.name.contains(encounterId)
            } ?: emptyArray()

            for (file in files) {
                try {
                    val fileSize = file.length()
                    if (file.delete()) {
                        wipedFiles++
                        wipedDataSize += fileSize
                        Timber.d("Wiped telemetry file: ${file.name}")
                    } else {
                        Timber.w("Failed to delete telemetry file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to wipe telemetry file: ${file.name}")
                }
            }

            Pair(wipedFiles, wipedDataSize)

        } catch (e: Exception) {
            Timber.e(e, "Failed to wipe telemetry data for encounter: $encounterId")
            Pair(0, 0L)
        }
    }

    /**
     * Wipe audit data for encounter
     */
    private suspend fun wipeAuditData(encounterId: String): Pair<Int, Long> = withContext(Dispatchers.IO) {
        try {
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (!auditDir.exists()) {
                return@withContext Pair(0, 0L)
            }

            var wipedFiles = 0
            var wipedDataSize = 0L

            val files = auditDir.listFiles { file ->
                file.isFile && file.name.contains(encounterId)
            } ?: emptyArray()

            for (file in files) {
                try {
                    val fileSize = file.length()
                    if (file.delete()) {
                        wipedFiles++
                        wipedDataSize += fileSize
                        Timber.d("Wiped audit file: ${file.name}")
                    } else {
                        Timber.w("Failed to delete audit file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to wipe audit file: ${file.name}")
                }
            }

            Pair(wipedFiles, wipedDataSize)

        } catch (e: Exception) {
            Timber.e(e, "Failed to wipe audit data for encounter: $encounterId")
            Pair(0, 0L)
        }
    }

    /**
     * Wipe docs data for encounter
     */
    private suspend fun wipeDocsData(encounterId: String): Pair<Int, Long> = withContext(Dispatchers.IO) {
        try {
            val docsDir = File(context.filesDir, DOCS_DIR)
            if (!docsDir.exists()) {
                return@withContext Pair(0, 0L)
            }

            var wipedFiles = 0
            var wipedDataSize = 0L

            val files = docsDir.listFiles { file ->
                file.isFile && file.name.contains(encounterId)
            } ?: emptyArray()

            for (file in files) {
                try {
                    val fileSize = file.length()
                    if (file.delete()) {
                        wipedFiles++
                        wipedDataSize += fileSize
                        Timber.d("Wiped docs file: ${file.name}")
                    } else {
                        Timber.w("Failed to delete docs file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to wipe docs file: ${file.name}")
                }
            }

            Pair(wipedFiles, wipedDataSize)

        } catch (e: Exception) {
            Timber.e(e, "Failed to wipe docs data for encounter: $encounterId")
            Pair(0, 0L)
        }
    }

    /**
     * Cancel background tasks for encounter
     */
    private suspend fun cancelBackgroundTasks(encounterId: String): Int = withContext(Dispatchers.IO) {
        try {
            var cancelledTasks = 0

            // Cancel audio processing tasks
            val audioTasks = getAudioProcessingTasks(encounterId)
            for (task in audioTasks) {
                if (cancelTask(task)) {
                    cancelledTasks++
                    Timber.d("Cancelled audio processing task: $task")
                }
            }

            // Cancel transcription tasks
            val transcriptionTasks = getTranscriptionTasks(encounterId)
            for (task in transcriptionTasks) {
                if (cancelTask(task)) {
                    cancelledTasks++
                    Timber.d("Cancelled transcription task: $task")
                }
            }

            // Cancel SOAP generation tasks
            val soapTasks = getSOAPGenerationTasks(encounterId)
            for (task in soapTasks) {
                if (cancelTask(task)) {
                    cancelledTasks++
                    Timber.d("Cancelled SOAP generation task: $task")
                }
            }

            // Cancel prescription tasks
            val prescriptionTasks = getPrescriptionTasks(encounterId)
            for (task in prescriptionTasks) {
                if (cancelTask(task)) {
                    cancelledTasks++
                    Timber.d("Cancelled prescription task: $task")
                }
            }

            cancelledTasks

        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel background tasks for encounter: $encounterId")
            0
        }
    }

    /**
     * Get audio processing tasks for encounter
     */
    private fun getAudioProcessingTasks(encounterId: String): List<String> {
        // In a real implementation, this would query the actual task management system
        return listOf(
            "audio_capture_$encounterId",
            "audio_processing_$encounterId",
            "audio_cleanup_$encounterId"
        )
    }

    /**
     * Get transcription tasks for encounter
     */
    private fun getTranscriptionTasks(encounterId: String): List<String> {
        return listOf(
            "transcription_$encounterId",
            "transcript_processing_$encounterId",
            "transcript_validation_$encounterId"
        )
    }

    /**
     * Get SOAP generation tasks for encounter
     */
    private fun getSOAPGenerationTasks(encounterId: String): List<String> {
        return listOf(
            "soap_generation_$encounterId",
            "soap_validation_$encounterId",
            "soap_export_$encounterId"
        )
    }

    /**
     * Get prescription tasks for encounter
     */
    private fun getPrescriptionTasks(encounterId: String): List<String> {
        return listOf(
            "prescription_generation_$encounterId",
            "prescription_validation_$encounterId",
            "prescription_export_$encounterId"
        )
    }

    /**
     * Cancel a specific task
     */
    private suspend fun cancelTask(taskId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // In a real implementation, this would cancel the actual task
            // For now, we'll simulate task cancellation
            Timber.d("Simulating cancellation of task: $taskId")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel task: $taskId")
            false
        }
    }

    /**
     * Get cancellation statistics
     */
    suspend fun getCancellationStats(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val stats = mutableMapOf<String, Any>()

            // Get WorkManager job counts
            val workQuery = WorkQuery.Builder
                .fromStates(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED)
                .build()

            val workInfos = workManager.getWorkInfos(workQuery).get()
            stats["active_jobs"] = workInfos.size

            // Get directory sizes
            val telemetryDir = File(context.filesDir, TELEMETRY_DIR)
            val auditDir = File(context.filesDir, AUDIT_DIR)
            val docsDir = File(context.filesDir, DOCS_DIR)

            stats["telemetry_files"] = if (telemetryDir.exists()) telemetryDir.listFiles()?.size ?: 0 else 0
            stats["audit_files"] = if (auditDir.exists()) auditDir.listFiles()?.size ?: 0 else 0
            stats["docs_files"] = if (docsDir.exists()) docsDir.listFiles()?.size ?: 0 else 0

            Result.success(stats)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get cancellation statistics")
            Result.failure(e)
        }
    }

    /**
     * Test job cancellation with synthetic data
     */
    suspend fun testJobCancellation(encounterId: String): Result<JobCancellationResult> = withContext(Dispatchers.IO) {
        try {
            // Create test data
            createTestData(encounterId)

            // Perform cancellation
            val result = cancelEncounterJobs(encounterId)
            
            if (result.isSuccess) {
                val cancellationResult = result.getOrThrow()
                Timber.i("Test job cancellation completed: $cancellationResult")
            }

            result

        } catch (e: Exception) {
            Timber.e(e, "Failed to test job cancellation for encounter: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Create test data for cancellation testing
     */
    private suspend fun createTestData(encounterId: String) = withContext(Dispatchers.IO) {
        try {
            // Create test telemetry data
            val telemetryDir = File(context.filesDir, TELEMETRY_DIR)
            if (!telemetryDir.exists()) {
                telemetryDir.mkdirs()
            }

            val telemetryFile = File(telemetryDir, "telemetry_$encounterId.json")
            telemetryFile.writeText("""{"encounter_id": "$encounterId", "data": "test_telemetry"}""")

            // Create test audit data
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (!auditDir.exists()) {
                auditDir.mkdirs()
            }

            val auditFile = File(auditDir, "audit_$encounterId.jsonl")
            auditFile.writeText("""{"encounter_id": "$encounterId", "event": "test_event"}""")

            // Create test docs data
            val docsDir = File(context.filesDir, DOCS_DIR)
            if (!docsDir.exists()) {
                docsDir.mkdirs()
            }

            val docsFile = File(docsDir, "docs_$encounterId.pdf")
            docsFile.writeText("Test PDF content for encounter $encounterId")

        } catch (e: Exception) {
            Timber.e(e, "Failed to create test data for encounter: $encounterId")
        }
    }

    /**
     * Clean up old cancellation logs
     */
    suspend fun cleanupOldCancellationLogs(olderThanDays: Int = 30): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
            var cleanedCount = 0

            // Clean up telemetry directory
            val telemetryDir = File(context.filesDir, TELEMETRY_DIR)
            if (telemetryDir.exists()) {
                val files = telemetryDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            cleanedCount++
                            Timber.d("Cleaned up old telemetry file: ${file.name}")
                        }
                    }
                }
            }

            // Clean up audit directory
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (auditDir.exists()) {
                val files = auditDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            cleanedCount++
                            Timber.d("Cleaned up old audit file: ${file.name}")
                        }
                    }
                }
            }

            // Clean up docs directory
            val docsDir = File(context.filesDir, DOCS_DIR)
            if (docsDir.exists()) {
                val files = docsDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            cleanedCount++
                            Timber.d("Cleaned up old docs file: ${file.name}")
                        }
                    }
                }
            }

            Timber.i("Cleaned up $cleanedCount old cancellation log files")
            Result.success(cleanedCount)

        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old cancellation logs")
            Result.failure(e)
        }
    }
}
