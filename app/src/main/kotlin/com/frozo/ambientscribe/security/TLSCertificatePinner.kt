package com.frozo.ambientscribe.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
// TLS classes not available in this OkHttp version
// import okhttp3.tls.HandshakeCertificates
// import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import java.io.File
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

/**
 * TLS Certificate Pinner - ST-5.21
 * Implements certificate pinning for CDN + Railway with rotation playbook and pin-break tests
 * Provides secure communication with pinned certificates
 */
class TLSCertificatePinner(
    private val context: Context,
    private val auditLogger: AuditLogger
) {
    companion object {
        private const val TAG = "TLSCertificatePinner"
        private const val PIN_ROTATION_INTERVAL_DAYS = 30L
        private const val PIN_BREAK_TEST_INTERVAL_DAYS = 7L
        private const val MAX_PIN_AGE_DAYS = 90L
    }

    private val pinDir = File(context.filesDir, "cert_pins")
    private val rotationDir = File(context.filesDir, "pin_rotation")
    private val testDir = File(context.filesDir, "pin_tests")

    init {
        pinDir.mkdirs()
        rotationDir.mkdirs()
        testDir.mkdirs()
    }

    /**
     * Data class for certificate pin metadata
     */
    data class CertPinMetadata(
        val pinId: String,
        val hostname: String,
        val pinType: String, // "sha256", "sha1", "publickey"
        val pinValue: String,
        val certificate: String,
        val issuer: String,
        val validFrom: String,
        val validTo: String,
        val createdAt: String,
        val expiresAt: String,
        val isActive: Boolean,
        val rotationCount: Int,
        val lastTested: String?
    )

    /**
     * Data class for pin rotation result
     */
    data class PinRotationResult(
        val success: Boolean,
        val oldPinId: String?,
        val newPinId: String?,
        val rotationTimestamp: String,
        val backupCreated: Boolean,
        val rollbackAvailable: Boolean,
        val auditTrail: String
    )

    /**
     * Data class for pin break test result
     */
    data class PinBreakTestResult(
        val success: Boolean,
        val testId: String,
        val hostname: String,
        val testType: String,
        val connectionSuccessful: Boolean,
        val pinValidationPassed: Boolean,
        val responseTime: Long,
        val errorMessage: String?,
        val timestamp: String
    )

    /**
     * Data class for rotation playbook entry
     */
    data class RotationPlaybookEntry(
        val step: Int,
        val action: String,
        val description: String,
        val estimatedTime: String,
        val rollbackAction: String?,
        val verification: String
    )

    /**
     * Create OkHttpClient with certificate pinning
     */
    fun createPinnedHttpClient(hostname: String): OkHttpClient {
        val certificatePinner = createCertificatePinner(hostname)
        
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Create certificate pinner for specific hostname
     */
    private fun createCertificatePinner(hostname: String): CertificatePinner {
        val pins = loadActivePinsForHost(hostname)
        
        val builder = CertificatePinner.Builder()
        pins.forEach { pin ->
            builder.add(hostname, "${pin.pinType}=${pin.pinValue}")
        }
        
        return builder.build()
    }

    /**
     * Add certificate pin for hostname
     */
    suspend fun addCertificatePin(
        hostname: String,
        certificate: X509Certificate,
        pinType: String = "sha256"
    ): Result<CertPinMetadata> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Adding certificate pin for hostname: $hostname")

            val pinValue = calculatePinValue(certificate, pinType)
            val pinId = generatePinId(hostname, pinType)

            val metadata = CertPinMetadata(
                pinId = pinId,
                hostname = hostname,
                pinType = pinType,
                pinValue = pinValue,
                certificate = certificate.encoded.toString(Charsets.UTF_8),
                issuer = certificate.issuerX500Principal.name,
                validFrom = certificate.notBefore.toString(),
                validTo = certificate.notAfter.toString(),
                createdAt = getCurrentTimestamp(),
                expiresAt = getExpirationTimestamp(PIN_ROTATION_INTERVAL_DAYS),
                isActive = true,
                rotationCount = 0,
                lastTested = null
            )

            // Store pin metadata
            storePinMetadata(metadata)

            // Log audit event
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "cert_pin_add",
                    "hostname" to hostname,
                    "pin_id" to pinId,
                    "pin_type" to pinType,
                    "pin_value" to pinValue
                )
            )

            Log.d(TAG, "Successfully added certificate pin: $pinId")
            Result.success(metadata)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add certificate pin for hostname: $hostname", e)
            auditLogger.logEvent(
                "system",
                AuditEvent.AuditEventType.ERROR,
                AuditEvent.AuditActor.ADMIN,
                mapOf(
                    "operation" to "cert_pin_add_failed",
                    "hostname" to hostname,
                    "error" to (e.message ?: "Unknown error")
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Rotate certificate pin
     */
    suspend fun rotateCertificatePin(
        hostname: String,
        newCertificate: X509Certificate,
        reason: String = "scheduled_rotation"
    ): Result<PinRotationResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Rotating certificate pin for hostname: $hostname")

            // Find current active pin
            val currentPin = loadActivePinForHost(hostname)
                ?: return@withContext Result.failure(IllegalArgumentException("No active pin found for hostname: $hostname"))

            // Create new pin
            val newPinResult = addCertificatePin(hostname, newCertificate, currentPin.pinType)
            if (newPinResult.isFailure) {
                return@withContext Result.failure(newPinResult.exceptionOrNull()!!)
            }
            val newPin = newPinResult.getOrThrow()

            // Deactivate old pin
            val deactivatedPin = currentPin.copy(
                isActive = false,
                expiresAt = getCurrentTimestamp()
            )
            storePinMetadata(deactivatedPin)

            // Create backup of old pin
            val backupCreated = createPinBackup(currentPin)

            // Generate rotation playbook
            val playbook = generateRotationPlaybook(hostname, currentPin, newPin)

            val rotationResult = PinRotationResult(
                success = true,
                oldPinId = currentPin.pinId,
                newPinId = newPin.pinId,
                rotationTimestamp = getCurrentTimestamp(),
                backupCreated = backupCreated,
                rollbackAvailable = true,
                auditTrail = "Pin rotated from ${currentPin.pinId} to ${newPin.pinId}"
            )

            // Log rotation audit
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "cert_pin_rotation",
                    "hostname" to hostname,
                    "old_pin_id" to currentPin.pinId,
                    "new_pin_id" to newPin.pinId,
                    "reason" to reason,
                    "backup_created" to backupCreated
                )
            )

            Log.d(TAG, "Successfully rotated certificate pin: ${currentPin.pinId} -> ${newPin.pinId}")
            Result.success(rotationResult)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate certificate pin for hostname: $hostname", e)
            auditLogger.logEvent(
                "system",
                AuditEvent.AuditEventType.ERROR,
                AuditEvent.AuditActor.ADMIN,
                mapOf(
                    "operation" to "cert_pin_rotation_failed",
                    "hostname" to hostname,
                    "error" to (e.message ?: "Unknown error")
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Perform pin break test
     */
    suspend fun performPinBreakTest(hostname: String): Result<PinBreakTestResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Performing pin break test for hostname: $hostname")

            val testId = generateTestId(hostname)
            val startTime = System.currentTimeMillis()

            // Test connection with current pins
            val connectionSuccessful = testConnection(hostname)
            val responseTime = System.currentTimeMillis() - startTime

            // Test pin validation
            val pinValidationPassed = testPinValidation(hostname)

            val testResult = PinBreakTestResult(
                success = connectionSuccessful && pinValidationPassed,
                testId = testId,
                hostname = hostname,
                testType = "pin_break_test",
                connectionSuccessful = connectionSuccessful,
                pinValidationPassed = pinValidationPassed,
                responseTime = responseTime,
                errorMessage = if (!connectionSuccessful) "Connection failed" else if (!pinValidationPassed) "Pin validation failed" else null,
                timestamp = getCurrentTimestamp()
            )

            // Store test result
            storeTestResult(testResult)

            // Update pin metadata with last tested
            updatePinLastTested(hostname, getCurrentTimestamp())

            // Log test audit
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "operation" to "cert_pin_break_test",
                    "hostname" to hostname,
                    "test_id" to testId,
                    "success" to testResult.success,
                    "connection_successful" to connectionSuccessful,
                    "pin_validation_passed" to pinValidationPassed,
                    "response_time" to responseTime
                )
            )

            Log.d(TAG, "Pin break test completed for hostname: $hostname, success: ${testResult.success}")
            Result.success(testResult)

        } catch (e: Exception) {
            Log.e(TAG, "Pin break test failed for hostname: $hostname", e)
            val testResult = PinBreakTestResult(
                success = false,
                testId = generateTestId(hostname),
                hostname = hostname,
                testType = "pin_break_test",
                connectionSuccessful = false,
                pinValidationPassed = false,
                responseTime = 0,
                errorMessage = e.message,
                timestamp = getCurrentTimestamp()
            )
            Result.success(testResult)
        }
    }

    /**
     * Generate rotation playbook
     */
    suspend fun generateRotationPlaybook(
        hostname: String,
        oldPin: CertPinMetadata,
        newPin: CertPinMetadata
    ): List<RotationPlaybookEntry> = withContext(Dispatchers.IO) {
        val playbook = listOf(
            RotationPlaybookEntry(
                step = 1,
                action = "Backup Current Pin",
                description = "Create backup of current certificate pin before rotation",
                estimatedTime = "5 minutes",
                rollbackAction = "Restore from backup if rotation fails",
                verification = "Verify backup file exists and is valid"
            ),
            RotationPlaybookEntry(
                step = 2,
                action = "Validate New Certificate",
                description = "Validate new certificate is valid and trusted",
                estimatedTime = "10 minutes",
                rollbackAction = "Reject new certificate if validation fails",
                verification = "Check certificate chain and expiration"
            ),
            RotationPlaybookEntry(
                step = 3,
                action = "Add New Pin",
                description = "Add new certificate pin to pinning configuration",
                estimatedTime = "5 minutes",
                rollbackAction = "Remove new pin if addition fails",
                verification = "Confirm new pin is active and accessible"
            ),
            RotationPlaybookEntry(
                step = 4,
                action = "Test New Pin",
                description = "Test connection with new pin to ensure it works",
                estimatedTime = "15 minutes",
                rollbackAction = "Revert to old pin if test fails",
                verification = "Verify successful connection and pin validation"
            ),
            RotationPlaybookEntry(
                step = 5,
                action = "Deactivate Old Pin",
                description = "Deactivate old certificate pin after successful testing",
                estimatedTime = "5 minutes",
                rollbackAction = "Reactivate old pin if issues arise",
                verification = "Confirm old pin is deactivated"
            ),
            RotationPlaybookEntry(
                step = 6,
                action = "Monitor and Validate",
                description = "Monitor system for 24 hours to ensure stability",
                estimatedTime = "24 hours",
                rollbackAction = "Rollback if issues detected",
                verification = "Check logs and metrics for any issues"
            )
        )

        // Save playbook
        val playbookFile = File(rotationDir, "rotation_playbook_${hostname}_${System.currentTimeMillis()}.json")
        val playbookJson = JSONObject().apply {
            put("hostname", hostname)
            put("oldPinId", oldPin.pinId)
            put("newPinId", newPin.pinId)
            put("generatedAt", getCurrentTimestamp())
            put("steps", playbook.map { entry ->
                JSONObject().apply {
                    put("step", entry.step)
                    put("action", entry.action)
                    put("description", entry.description)
                    put("estimatedTime", entry.estimatedTime)
                    put("rollbackAction", entry.rollbackAction)
                    put("verification", entry.verification)
                }
            })
        }
        playbookFile.writeText(playbookJson.toString())

        playbook
    }

    /**
     * Test connection to hostname
     */
    private suspend fun testConnection(hostname: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = createPinnedHttpClient(hostname)
            val request = okhttp3.Request.Builder()
                .url("https://$hostname")
                .build()
            
            val response = client.newCall(request).execute()
            response.close()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed for hostname: $hostname", e)
            false
        }
    }

    /**
     * Test pin validation
     */
    private suspend fun testPinValidation(hostname: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pins = loadActivePinsForHost(hostname)
            if (pins.isEmpty()) return@withContext false

            val client = createPinnedHttpClient(hostname)
            val request = okhttp3.Request.Builder()
                .url("https://$hostname")
                .build()
            
            val response = client.newCall(request).execute()
            response.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pin validation test failed for hostname: $hostname", e)
            false
        }
    }

    // Private helper methods
    private fun calculatePinValue(certificate: X509Certificate, pinType: String): String {
        return when (pinType.lowercase()) {
            "sha256" -> {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(certificate.encoded)
                "sha256/${Base64.getEncoder().encodeToString(hash)}"
            }
            "sha1" -> {
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                val hash = digest.digest(certificate.encoded)
                "sha1/${Base64.getEncoder().encodeToString(hash)}"
            }
            else -> throw IllegalArgumentException("Unsupported pin type: $pinType")
        }
    }

    private fun generatePinId(hostname: String, pinType: String): String {
        val timestamp = System.currentTimeMillis()
        val random = Random.nextInt(1000, 10000)
        return "pin_${hostname.replace(".", "_")}_${pinType}_${timestamp}_${random}"
    }

    private fun generateTestId(hostname: String): String {
        val timestamp = System.currentTimeMillis()
        val random = Random.nextInt(1000, 10000)
        return "test_${hostname.replace(".", "_")}_${timestamp}_${random}"
    }

    private fun loadActivePinsForHost(hostname: String): List<CertPinMetadata> {
        val pins = mutableListOf<CertPinMetadata>()
        pinDir.listFiles { file ->
            file.name.endsWith("_metadata.json")
        }?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                if (json.getString("hostname") == hostname && json.getBoolean("isActive")) {
                    val pin = CertPinMetadata(
                        pinId = json.getString("pinId"),
                        hostname = json.getString("hostname"),
                        pinType = json.getString("pinType"),
                        pinValue = json.getString("pinValue"),
                        certificate = json.getString("certificate"),
                        issuer = json.getString("issuer"),
                        validFrom = json.getString("validFrom"),
                        validTo = json.getString("validTo"),
                        createdAt = json.getString("createdAt"),
                        expiresAt = json.getString("expiresAt"),
                        isActive = json.getBoolean("isActive"),
                        rotationCount = json.getInt("rotationCount"),
                        lastTested = if (json.isNull("lastTested")) null else json.getString("lastTested")
                    )
                    pins.add(pin)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse pin metadata file: ${file.name}", e)
            }
        }
        return pins
    }

    private fun loadActivePinForHost(hostname: String): CertPinMetadata? {
        return loadActivePinsForHost(hostname).firstOrNull()
    }

    private fun storePinMetadata(metadata: CertPinMetadata) {
        val metadataFile = File(pinDir, "${metadata.pinId}_metadata.json")
        val json = JSONObject().apply {
            put("pinId", metadata.pinId)
            put("hostname", metadata.hostname)
            put("pinType", metadata.pinType)
            put("pinValue", metadata.pinValue)
            put("certificate", metadata.certificate)
            put("issuer", metadata.issuer)
            put("validFrom", metadata.validFrom)
            put("validTo", metadata.validTo)
            put("createdAt", metadata.createdAt)
            put("expiresAt", metadata.expiresAt)
            put("isActive", metadata.isActive)
            put("rotationCount", metadata.rotationCount)
            put("lastTested", metadata.lastTested)
        }
        metadataFile.writeText(json.toString())
    }

    private fun createPinBackup(pin: CertPinMetadata): Boolean {
        return try {
            val backupDir = File(pinDir, "backups")
            backupDir.mkdirs()
            
            val backupFile = File(backupDir, "${pin.pinId}_backup_${System.currentTimeMillis()}.json")
            val json = JSONObject().apply {
                put("pinId", pin.pinId)
                put("hostname", pin.hostname)
                put("pinType", pin.pinType)
                put("pinValue", pin.pinValue)
                put("certificate", pin.certificate)
                put("issuer", pin.issuer)
                put("validFrom", pin.validFrom)
                put("validTo", pin.validTo)
                put("createdAt", pin.createdAt)
                put("expiresAt", pin.expiresAt)
                put("isActive", pin.isActive)
                put("rotationCount", pin.rotationCount)
                put("lastTested", pin.lastTested)
                put("backupTimestamp", getCurrentTimestamp())
            }
            backupFile.writeText(json.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup for pin: ${pin.pinId}", e)
            false
        }
    }

    private fun storeTestResult(testResult: PinBreakTestResult) {
        val testFile = File(testDir, "test_${testResult.testId}.json")
        val json = JSONObject().apply {
            put("testId", testResult.testId)
            put("hostname", testResult.hostname)
            put("testType", testResult.testType)
            put("success", testResult.success)
            put("connectionSuccessful", testResult.connectionSuccessful)
            put("pinValidationPassed", testResult.pinValidationPassed)
            put("responseTime", testResult.responseTime)
            put("errorMessage", testResult.errorMessage)
            put("timestamp", testResult.timestamp)
        }
        testFile.writeText(json.toString())
    }

    private fun updatePinLastTested(hostname: String, timestamp: String) {
        val pins = loadActivePinsForHost(hostname)
        pins.forEach { pin ->
            val updatedPin = pin.copy(lastTested = timestamp)
            storePinMetadata(updatedPin)
        }
    }

    private fun getCurrentTimestamp(): String {
        return java.time.Instant.now().toString()
    }

    private fun getExpirationTimestamp(days: Long): String {
        return java.time.Instant.now().plusSeconds(days * 24 * 60 * 60).toString()
    }
}
