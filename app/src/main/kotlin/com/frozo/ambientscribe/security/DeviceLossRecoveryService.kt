package com.frozo.ambientscribe.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.RSAKeyGenParameterSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher

/**
 * Device-loss recovery service implementing Option A: server re-encryption
 * PDFs are uploaded encrypted to clinic public key for recovery
 */
class DeviceLossRecoveryService(private val context: Context) {

    companion object {
        private const val CLINIC_KEYS_DIR = "clinic_keys"
        private const val RECOVERY_DIR = "recovery_uploads"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private const val RSA_KEY_SIZE = 2048
        private const val RSA_ALGORITHM = "RSA"
        private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    }

    private val auditLogger = AuditLogger(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * Recovery upload result
     */
    data class RecoveryUploadResult(
        val success: Boolean,
        val uploadId: String,
        val encryptedFileSize: Long,
        val clinicKeyId: String,
        val error: String? = null
    )

    /**
     * Clinic key information
     */
    data class ClinicKeyInfo(
        val keyId: String,
        val publicKey: PublicKey,
        val keyType: String,
        val createdDate: Date,
        val isActive: Boolean
    )

    /**
     * Upload PDF for device-loss recovery
     */
    suspend fun uploadPDFForRecovery(
        pdfPath: String,
        encounterId: String,
        clinicId: String
    ): Result<RecoveryUploadResult> = withContext(Dispatchers.IO) {
        try {
            val uploadId = UUID.randomUUID().toString()
            val timestamp = dateFormat.format(Date())
            
            // Get or create clinic public key
            val clinicKey = getOrCreateClinicKey(clinicId)
            if (clinicKey == null) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to get clinic public key for: $clinicId")
                )
            }

            // Read PDF file
            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("PDF file not found: $pdfPath")
                )
            }

            val pdfData = pdfFile.readBytes()

            // Encrypt PDF with clinic public key
            val encryptedData = encryptWithClinicKey(pdfData, clinicKey.publicKey)
            if (encryptedData == null) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to encrypt PDF with clinic key")
                )
            }

            // Save encrypted file for upload
            val recoveryDir = File(context.filesDir, RECOVERY_DIR)
            if (!recoveryDir.exists()) {
                recoveryDir.mkdirs()
            }

            val encryptedFile = File(recoveryDir, "recovery_${encounterId}_${uploadId}.enc")
            encryptedFile.writeBytes(encryptedData)

            // Create recovery metadata
            val metadata = createRecoveryMetadata(encounterId, clinicId, uploadId, clinicKey.keyId, timestamp)
            val metadataFile = File(recoveryDir, "metadata_${uploadId}.json")
            metadataFile.writeText(metadata)

            // Log recovery upload
            auditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.EXPORT,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "recovery_upload" to "true",
                    "upload_id" to uploadId,
                    "clinic_id" to clinicId,
                    "clinic_key_id" to clinicKey.keyId,
                    "encrypted_size" to encryptedData.size,
                    "original_size" to pdfData.size,
                    "timestamp" to timestamp
                )
            )

            val result = RecoveryUploadResult(
                success = true,
                uploadId = uploadId,
                encryptedFileSize = encryptedData.size.toLong(),
                clinicKeyId = clinicKey.keyId
            )

            Timber.i("Uploaded PDF for recovery: $encounterId -> $uploadId")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to upload PDF for recovery: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Get or create clinic public key
     */
    private suspend fun getOrCreateClinicKey(clinicId: String): ClinicKeyInfo? = withContext(Dispatchers.IO) {
        try {
            val clinicKeysDir = File(context.filesDir, CLINIC_KEYS_DIR)
            if (!clinicKeysDir.exists()) {
                clinicKeysDir.mkdirs()
            }

            val keyFile = File(clinicKeysDir, "clinic_${clinicId}_public.key")
            val metadataFile = File(clinicKeysDir, "clinic_${clinicId}_metadata.json")

            if (keyFile.exists() && metadataFile.exists()) {
                // Load existing key
                val publicKeyBytes = keyFile.readBytes()
                val publicKey = java.security.KeyFactory.getInstance(RSA_ALGORITHM)
                    .generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))
                
                val metadata = parseKeyMetadata(metadataFile.readText())
                return@withContext ClinicKeyInfo(
                    keyId = metadata["key_id"] ?: "unknown",
                    publicKey = publicKey,
                    keyType = metadata["key_type"] ?: "RSA",
                    createdDate = Date(metadata["created_date"]?.toLongOrNull() ?: 0L),
                    isActive = metadata["is_active"]?.toBoolean() ?: true
                )
            } else {
                // Generate new key pair
                val keyPair = generateRSAKeyPair()
                val keyId = "clinic_${clinicId}_${System.currentTimeMillis()}"
                
                // Save public key
                val publicKeyBytes = keyPair.public.encoded
                keyFile.writeBytes(publicKeyBytes)
                
                // Save metadata
                val metadata = mapOf(
                    "key_id" to keyId,
                    "key_type" to "RSA",
                    "created_date" to System.currentTimeMillis().toString(),
                    "is_active" to "true",
                    "clinic_id" to clinicId
                )
                metadataFile.writeText(createKeyMetadataJson(metadata))
                
                return@withContext ClinicKeyInfo(
                    keyId = keyId,
                    publicKey = keyPair.public,
                    keyType = "RSA",
                    createdDate = Date(),
                    isActive = true
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get or create clinic key for: $clinicId")
            null
        }
    }

    /**
     * Generate RSA key pair
     */
    private fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        val keySpec = RSAKeyGenParameterSpec(RSA_KEY_SIZE, RSAKeyGenParameterSpec.F4)
        keyPairGenerator.initialize(keySpec, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Encrypt data with clinic public key
     */
    private fun encryptWithClinicKey(data: ByteArray, publicKey: PublicKey): ByteArray? {
        return try {
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            cipher.doFinal(data)
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt data with clinic key")
            null
        }
    }

    /**
     * Create recovery metadata JSON
     */
    private fun createRecoveryMetadata(
        encounterId: String,
        clinicId: String,
        uploadId: String,
        clinicKeyId: String,
        timestamp: String
    ): String {
        return """
        {
            "upload_id": "$uploadId",
            "encounter_id": "$encounterId",
            "clinic_id": "$clinicId",
            "clinic_key_id": "$clinicKeyId",
            "timestamp": "$timestamp",
            "recovery_type": "device_loss",
            "encryption_algorithm": "RSA-OAEP",
            "key_size": $RSA_KEY_SIZE
        }
        """.trimIndent()
    }

    /**
     * Parse key metadata from JSON
     */
    private fun parseKeyMetadata(json: String): Map<String, String> {
        return try {
            val cleanJson = json.trim().removePrefix("{").removeSuffix("}")
            val pairs = cleanJson.split(",").map { it.trim() }
            val metadata = mutableMapOf<String, String>()
            
            for (pair in pairs) {
                val (key, value) = pair.split(":", limit = 2)
                val cleanKey = key.trim().removeSurrounding("\"")
                val cleanValue = value.trim().removeSurrounding("\"")
                metadata[cleanKey] = cleanValue
            }
            
            metadata
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse key metadata")
            emptyMap()
        }
    }

    /**
     * Create key metadata JSON
     */
    private fun createKeyMetadataJson(metadata: Map<String, String>): String {
        val entries = metadata.entries.joinToString(",") { 
            "\"${it.key}\":\"${it.value}\"" 
        }
        return "{$entries}"
    }

    /**
     * Get recovery uploads for clinic
     */
    suspend fun getRecoveryUploads(clinicId: String): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            val recoveryDir = File(context.filesDir, RECOVERY_DIR)
            if (!recoveryDir.exists()) {
                return@withContext Result.success(emptyList())
            }

            val uploads = mutableListOf<Map<String, Any>>()
            val files = recoveryDir.listFiles { file ->
                file.isFile && file.name.startsWith("metadata_") && file.name.endsWith(".json")
            } ?: emptyArray()

            for (file in files) {
                try {
                    val metadata = parseKeyMetadata(file.readText())
                    if (metadata["clinic_id"] == clinicId) {
                        uploads.add(metadata)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse recovery metadata: ${file.name}")
                }
            }

            Result.success(uploads)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get recovery uploads for clinic: $clinicId")
            Result.failure(e)
        }
    }

    /**
     * Clean up old recovery uploads
     */
    suspend fun cleanupOldRecoveryUploads(olderThanDays: Int = 30): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val recoveryDir = File(context.filesDir, RECOVERY_DIR)
            if (!recoveryDir.exists()) {
                return@withContext Result.success(0)
            }

            val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
            val files = recoveryDir.listFiles() ?: emptyArray()
            var cleanedCount = 0

            for (file in files) {
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        cleanedCount++
                        Timber.d("Cleaned up old recovery file: ${file.name}")
                    }
                }
            }

            Timber.i("Cleaned up $cleanedCount old recovery uploads")
            Result.success(cleanedCount)

        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old recovery uploads")
            Result.failure(e)
        }
    }

    /**
     * Get recovery statistics
     */
    fun getRecoveryStats(): Map<String, Any> {
        val recoveryDir = File(context.filesDir, RECOVERY_DIR)
        val clinicKeysDir = File(context.filesDir, CLINIC_KEYS_DIR)
        
        val totalUploads = if (recoveryDir.exists()) {
            recoveryDir.listFiles()?.count { it.name.startsWith("recovery_") } ?: 0
        } else {
            0
        }
        
        val totalMetadata = if (recoveryDir.exists()) {
            recoveryDir.listFiles()?.count { it.name.startsWith("metadata_") } ?: 0
        } else {
            0
        }
        
        val totalClinicKeys = if (clinicKeysDir.exists()) {
            clinicKeysDir.listFiles()?.count { it.name.endsWith("_public.key") } ?: 0
        } else {
            0
        }
        
        return mapOf(
            "total_uploads" to totalUploads,
            "total_metadata" to totalMetadata,
            "total_clinic_keys" to totalClinicKeys,
            "recovery_enabled" to true
        )
    }

    /**
     * Test recovery encryption
     */
    suspend fun testRecoveryEncryption(clinicId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val testData = "test recovery data".toByteArray()
            val clinicKey = getOrCreateClinicKey(clinicId)
            
            if (clinicKey == null) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to get clinic key for testing")
                )
            }

            val encryptedData = encryptWithClinicKey(testData, clinicKey.publicKey)
            val success = encryptedData != null && encryptedData.isNotEmpty()

            if (success) {
                Timber.i("Recovery encryption test passed for clinic: $clinicId")
            } else {
                Timber.e("Recovery encryption test failed for clinic: $clinicId")
            }

            Result.success(success)

        } catch (e: Exception) {
            Timber.e(e, "Failed to test recovery encryption for clinic: $clinicId")
            Result.failure(e)
        }
    }
}
