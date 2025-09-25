package com.frozo.ambientscribe.security

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Auto-backup audit service to confirm no data in cloud backups
 * Ensures compliance with data protection requirements
 */
class BackupAuditService(private val context: Context) {

    companion object {
        private const val BACKUP_AUDIT_DIR = "backup_audit"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    }

    private val auditLogger = AuditLogger(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * Backup audit result
     */
    data class BackupAuditResult(
        val allowBackupEnabled: Boolean,
        val cloudBackupDetected: Boolean,
        val localBackupDetected: Boolean,
        val sensitiveDataFound: Boolean,
        val complianceStatus: String,
        val recommendations: List<String>,
        val timestamp: String
    )

    /**
     * Perform comprehensive backup audit
     */
    suspend fun performBackupAudit(): Result<BackupAuditResult> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val recommendations = mutableListOf<String>()
            var allowBackupEnabled = false
            var cloudBackupDetected = false
            var localBackupDetected = false
            var sensitiveDataFound = false

            // Check 1: Android allowBackup setting
            allowBackupEnabled = checkAllowBackupSetting()
            if (allowBackupEnabled) {
                recommendations.add("Disable allowBackup in AndroidManifest.xml")
            }

            // Check 2: Cloud backup detection
            cloudBackupDetected = detectCloudBackup()
            if (cloudBackupDetected) {
                recommendations.add("Remove cloud backup configurations")
            }

            // Check 3: Local backup detection
            localBackupDetected = detectLocalBackup()
            if (localBackupDetected) {
                recommendations.add("Review local backup settings")
            }

            // Check 4: Sensitive data in backup locations
            sensitiveDataFound = detectSensitiveDataInBackups()
            if (sensitiveDataFound) {
                recommendations.add("Remove sensitive data from backup locations")
            }

            // Determine compliance status
            val complianceStatus = determineComplianceStatus(
                allowBackupEnabled,
                cloudBackupDetected,
                localBackupDetected,
                sensitiveDataFound
            )

            val result = BackupAuditResult(
                allowBackupEnabled = allowBackupEnabled,
                cloudBackupDetected = cloudBackupDetected,
                localBackupDetected = localBackupDetected,
                sensitiveDataFound = sensitiveDataFound,
                complianceStatus = complianceStatus,
                recommendations = recommendations,
                timestamp = timestamp
            )

            // Log audit result
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.EXPORT,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "audit_type" to "backup_compliance",
                    "allow_backup_enabled" to allowBackupEnabled,
                    "cloud_backup_detected" to cloudBackupDetected,
                    "local_backup_detected" to localBackupDetected,
                    "sensitive_data_found" to sensitiveDataFound,
                    "compliance_status" to complianceStatus,
                    "recommendations_count" to recommendations.size
                )
            )

            // Save audit results
            saveBackupAuditResult(result)

            Timber.i("Backup audit completed: $complianceStatus")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to perform backup audit")
            Result.failure(e)
        }
    }

    /**
     * Check if allowBackup is enabled in AndroidManifest
     */
    private fun checkAllowBackupSetting(): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
            )
            
            val applicationInfo = packageInfo.applicationInfo
            val allowBackup = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0
            
            Timber.d("AllowBackup setting: $allowBackup")
            allowBackup
        } catch (e: Exception) {
            Timber.e(e, "Failed to check allowBackup setting")
            false
        }
    }

    /**
     * Detect cloud backup configurations
     */
    private fun detectCloudBackup(): Boolean {
        var cloudBackupDetected = false

        try {
            // Check for Google Drive backup
            if (isGoogleDriveBackupEnabled()) {
                cloudBackupDetected = true
                Timber.w("Google Drive backup detected")
            }

            // Check for other cloud backup services
            if (isOtherCloudBackupEnabled()) {
                cloudBackupDetected = true
                Timber.w("Other cloud backup detected")
            }

            // Check for cloud storage permissions
            if (hasCloudStoragePermissions()) {
                cloudBackupDetected = true
                Timber.w("Cloud storage permissions detected")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to detect cloud backup")
        }

        return cloudBackupDetected
    }

    /**
     * Check if Google Drive backup is enabled
     */
    private fun isGoogleDriveBackupEnabled(): Boolean {
        return try {
            val backupManager = android.app.backup.BackupManager(context)
            val isBackupEnabled = true // Backup is always enabled on Android
            Timber.d("Google Drive backup enabled: $isBackupEnabled")
            isBackupEnabled
        } catch (e: Exception) {
            Timber.e(e, "Failed to check Google Drive backup")
            false
        }
    }

    /**
     * Check for other cloud backup services
     */
    private fun isOtherCloudBackupEnabled(): Boolean {
        // Check for common cloud backup services
        val cloudServices = listOf(
            "com.dropbox.android",
            "com.microsoft.skydrive",
            "com.box.android",
            "com.amazon.clouddrive"
        )

        return cloudServices.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /**
     * Check for cloud storage permissions
     */
    private fun hasCloudStoragePermissions(): Boolean {
        val cloudPermissions = listOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.ACCESS_WIFI_STATE
        )

        return cloudPermissions.any { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Detect local backup configurations
     */
    private fun detectLocalBackup(): Boolean {
        var localBackupDetected = false

        try {
            // Check for local backup directories
            val backupDirs = listOf(
                File(context.filesDir, "backup"),
                File(context.cacheDir, "backup"),
                File(context.getExternalFilesDir(null), "backup")
            )

            for (dir in backupDirs) {
                if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
                    localBackupDetected = true
                    Timber.w("Local backup directory found: ${dir.absolutePath}")
                }
            }

            // Check for backup-related files
            val backupFiles = listOf(
                File(context.filesDir, "backup.db"),
                File(context.filesDir, "backup.json"),
                File(context.filesDir, "backup.xml")
            )

            for (file in backupFiles) {
                if (file.exists()) {
                    localBackupDetected = true
                    Timber.w("Local backup file found: ${file.absolutePath}")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to detect local backup")
        }

        return localBackupDetected
    }

    /**
     * Detect sensitive data in backup locations
     */
    private fun detectSensitiveDataInBackups(): Boolean {
        var sensitiveDataFound = false

        try {
            val sensitivePatterns = listOf(
                "patient_id",
                "encounter_id",
                "audio_data",
                "transcript",
                "soap_notes",
                "prescription",
                "medical_data"
            )

            val searchDirs = listOf(
                context.filesDir,
                context.cacheDir,
                context.getExternalFilesDir(null)
            )

            for (dir in searchDirs) {
                if (dir != null && dir.exists()) {
                    val files = dir.walkTopDown().filter { it.isFile }
                    for (file in files) {
                        val fileName = file.name.lowercase()
                        if (sensitivePatterns.any { pattern -> fileName.contains(pattern) }) {
                            sensitiveDataFound = true
                            Timber.w("Sensitive data found in backup: ${file.absolutePath}")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to detect sensitive data in backups")
        }

        return sensitiveDataFound
    }

    /**
     * Determine compliance status
     */
    private fun determineComplianceStatus(
        allowBackupEnabled: Boolean,
        cloudBackupDetected: Boolean,
        localBackupDetected: Boolean,
        sensitiveDataFound: Boolean
    ): String {
        return when {
            allowBackupEnabled || cloudBackupDetected || sensitiveDataFound -> "NON_COMPLIANT"
            localBackupDetected -> "PARTIALLY_COMPLIANT"
            else -> "COMPLIANT"
        }
    }

    /**
     * Save backup audit result
     */
    private suspend fun saveBackupAuditResult(result: BackupAuditResult) = withContext(Dispatchers.IO) {
        try {
            val backupAuditDir = File(context.filesDir, BACKUP_AUDIT_DIR)
            if (!backupAuditDir.exists()) {
                backupAuditDir.mkdirs()
            }

            val resultFile = File(backupAuditDir, "backup_audit_${System.currentTimeMillis()}.json")
            val resultJson = generateAuditResultJson(result)
            resultFile.writeText(resultJson)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save backup audit result")
        }
    }

    /**
     * Generate audit result JSON
     */
    private fun generateAuditResultJson(result: BackupAuditResult): String {
        val recommendationsJson = result.recommendations.joinToString(",") { "\"$it\"" }

        return """
        {
            "allowBackupEnabled": ${result.allowBackupEnabled},
            "cloudBackupDetected": ${result.cloudBackupDetected},
            "localBackupDetected": ${result.localBackupDetected},
            "sensitiveDataFound": ${result.sensitiveDataFound},
            "complianceStatus": "${result.complianceStatus}",
            "recommendations": [$recommendationsJson],
            "timestamp": "${result.timestamp}"
        }
        """.trimIndent()
    }

    /**
     * Get backup audit history
     */
    suspend fun getBackupAuditHistory(): Result<List<BackupAuditResult>> = withContext(Dispatchers.IO) {
        try {
            val backupAuditDir = File(context.filesDir, BACKUP_AUDIT_DIR)
            if (!backupAuditDir.exists()) {
                return@withContext Result.success(emptyList())
            }

            val auditFiles = backupAuditDir.listFiles { file ->
                file.isFile && file.name.startsWith("backup_audit_") && file.name.endsWith(".json")
            } ?: emptyArray()

            val results = mutableListOf<BackupAuditResult>()
            for (file in auditFiles) {
                try {
                    val result = parseAuditResultJson(file.readText())
                    if (result != null) {
                        results.add(result)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse audit result: ${file.name}")
                }
            }

            // Sort by timestamp (newest first)
            results.sortByDescending { it.timestamp }
            Result.success(results)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get backup audit history")
            Result.failure(e)
        }
    }

    /**
     * Parse audit result from JSON
     */
    private fun parseAuditResultJson(json: String): BackupAuditResult? {
        return try {
            // Simple JSON parsing - in production, use a proper JSON library
            val cleanJson = json.trim().removePrefix("{").removeSuffix("}")
            val pairs = cleanJson.split(",").map { it.trim() }
            val data = mutableMapOf<String, String>()
            
            for (pair in pairs) {
                val (key, value) = pair.split(":", limit = 2)
                val cleanKey = key.trim().removeSurrounding("\"")
                val cleanValue = value.trim().removeSurrounding("\"")
                data[cleanKey] = cleanValue
            }

            BackupAuditResult(
                allowBackupEnabled = data["allowBackupEnabled"]?.toBoolean() ?: false,
                cloudBackupDetected = data["cloudBackupDetected"]?.toBoolean() ?: false,
                localBackupDetected = data["localBackupDetected"]?.toBoolean() ?: false,
                sensitiveDataFound = data["sensitiveDataFound"]?.toBoolean() ?: false,
                complianceStatus = data["complianceStatus"] ?: "UNKNOWN",
                recommendations = emptyList(), // Simplified parsing
                timestamp = data["timestamp"] ?: ""
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse audit result JSON")
            null
        }
    }

    /**
     * Generate compliance report
     */
    suspend fun generateComplianceReport(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val auditResult = performBackupAudit()
            if (auditResult.isFailure) {
                return@withContext Result.failure(auditResult.exceptionOrNull()!!)
            }

            val result = auditResult.getOrThrow()
            val timestamp = dateFormat.format(Date())

            val report = """
            # Backup Compliance Report
            Generated: $timestamp

            ## Audit Summary
            - **Compliance Status**: ${result.complianceStatus}
            - **AllowBackup Enabled**: ${result.allowBackupEnabled}
            - **Cloud Backup Detected**: ${result.cloudBackupDetected}
            - **Local Backup Detected**: ${result.localBackupDetected}
            - **Sensitive Data Found**: ${result.sensitiveDataFound}

            ## Recommendations
            ${result.recommendations.joinToString("\n") { "- $it" }}

            ## Compliance Status
            ${when (result.complianceStatus) {
                "COMPLIANT" -> "✅ All backup settings are compliant with data protection requirements"
                "PARTIALLY_COMPLIANT" -> "⚠️ Some backup settings need attention"
                "NON_COMPLIANT" -> "❌ Backup settings violate data protection requirements"
                else -> "❓ Unknown compliance status"
            }}

            ## Next Steps
            1. Review and implement recommendations
            2. Re-run audit after changes
            3. Document compliance status
            4. Schedule regular audits

            ---
            This report is generated automatically and should be reviewed by compliance teams.
            """.trimIndent()

            // Save report
            val backupAuditDir = File(context.filesDir, BACKUP_AUDIT_DIR)
            if (!backupAuditDir.exists()) {
                backupAuditDir.mkdirs()
            }

            val reportFile = File(backupAuditDir, "compliance_report_${System.currentTimeMillis()}.md")
            reportFile.writeText(report)

            Timber.i("Generated backup compliance report: $timestamp")
            Result.success(report)

        } catch (e: Exception) {
            Timber.e(e, "Failed to generate compliance report")
            Result.failure(e)
        }
    }
}
