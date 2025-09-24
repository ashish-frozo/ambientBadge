package com.frozo.ambientscribe.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Clinic key provisioning service for RSA/ECC public key management
 * Handles key upload, rotation, pinning, and rollback operations
 */
class ClinicKeyProvisioningService(private val context: Context) {

    companion object {
        private const val CLINIC_KEYS_DIR = "clinic_keys"
        private const val KEY_METADATA_DIR = "key_metadata"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private const val RSA_KEY_SIZE = 2048
        private const val ECC_CURVE = "secp256r1"
        private const val KEY_ROTATION_INTERVAL_DAYS = 90L
        private const val KEY_RETENTION_DAYS = 365L
    }

    private val auditLogger = AuditLogger(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * Key provisioning result
     */
    data class KeyProvisioningResult(
        val success: Boolean,
        val keyId: String,
        val keyType: String,
        val keySize: Int,
        val clinicId: String,
        val operation: String,
        val timestamp: String,
        val error: String? = null
    )

    /**
     * Key metadata
     */
    data class KeyMetadata(
        val keyId: String,
        val clinicId: String,
        val keyType: String,
        val keySize: Int,
        val publicKey: PublicKey,
        val createdDate: Date,
        val expiresDate: Date,
        val isActive: Boolean,
        val isPinned: Boolean,
        val version: Int
    )

    /**
     * Upload clinic public key
     */
    suspend fun uploadClinicKey(
        clinicId: String,
        publicKeyPem: String,
        keyType: String = "RSA"
    ): Result<KeyProvisioningResult> = withContext(Dispatchers.IO) {
        try {
            val keyId = generateKeyId(clinicId, keyType)
            val timestamp = dateFormat.format(Date())
            
            // Parse public key
            val publicKey = parsePublicKeyFromPem(publicKeyPem, keyType)
            if (publicKey == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid public key format")
                )
            }

            // Validate key size
            val keySize = getKeySize(publicKey, keyType)
            if (!isValidKeySize(keySize, keyType)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid key size: $keySize for $keyType")
                )
            }

            // Save key and metadata
            val metadata = KeyMetadata(
                keyId = keyId,
                clinicId = clinicId,
                keyType = keyType,
                keySize = keySize,
                publicKey = publicKey,
                createdDate = Date(),
                expiresDate = Date(System.currentTimeMillis() + KEY_ROTATION_INTERVAL_DAYS * 24 * 60 * 60 * 1000),
                isActive = true,
                isPinned = false,
                version = 1
            )

            saveKeyAndMetadata(metadata)

            // Log key upload
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "key_upload",
                    "clinic_id" to clinicId,
                    "key_id" to keyId,
                    "key_type" to keyType,
                    "key_size" to keySize,
                    "timestamp" to timestamp
                )
            )

            val result = KeyProvisioningResult(
                success = true,
                keyId = keyId,
                keyType = keyType,
                keySize = keySize,
                clinicId = clinicId,
                operation = "upload",
                timestamp = timestamp
            )

            Timber.i("Uploaded clinic key: $keyId for clinic: $clinicId")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to upload clinic key for clinic: $clinicId")
            Result.failure(e)
        }
    }

    /**
     * Rotate clinic key
     */
    suspend fun rotateClinicKey(
        clinicId: String,
        currentKeyId: String,
        newPublicKeyPem: String,
        keyType: String = "RSA"
    ): Result<KeyProvisioningResult> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            
            // Validate current key exists and is active
            val currentMetadata = getKeyMetadata(currentKeyId)
            if (currentMetadata == null || currentMetadata.clinicId != clinicId) {
                return@withContext Result.failure(
                    IllegalArgumentException("Current key not found or invalid")
                )
            }

            // Parse new public key
            val newPublicKey = parsePublicKeyFromPem(newPublicKeyPem, keyType)
            if (newPublicKey == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid new public key format")
                )
            }

            val keySize = getKeySize(newPublicKey, keyType)
            if (!isValidKeySize(keySize, keyType)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid key size: $keySize for $keyType")
                )
            }

            // Generate new key ID
            val newKeyId = generateKeyId(clinicId, keyType)
            
            // Create new metadata
            val newMetadata = KeyMetadata(
                keyId = newKeyId,
                clinicId = clinicId,
                keyType = keyType,
                keySize = keySize,
                publicKey = newPublicKey,
                createdDate = Date(),
                expiresDate = Date(System.currentTimeMillis() + KEY_ROTATION_INTERVAL_DAYS * 24 * 60 * 60 * 1000),
                isActive = true,
                isPinned = false,
                version = currentMetadata.version + 1
            )

            // Deactivate current key
            deactivateKey(currentKeyId)
            
            // Save new key
            saveKeyAndMetadata(newMetadata)

            // Log key rotation
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "key_rotation",
                    "clinic_id" to clinicId,
                    "old_key_id" to currentKeyId,
                    "new_key_id" to newKeyId,
                    "key_type" to keyType,
                    "key_size" to keySize,
                    "version" to newMetadata.version,
                    "timestamp" to timestamp
                )
            )

            val result = KeyProvisioningResult(
                success = true,
                keyId = newKeyId,
                keyType = keyType,
                keySize = keySize,
                clinicId = clinicId,
                operation = "rotation",
                timestamp = timestamp
            )

            Timber.i("Rotated clinic key: $currentKeyId -> $newKeyId for clinic: $clinicId")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate clinic key for clinic: $clinicId")
            Result.failure(e)
        }
    }

    /**
     * Pin key (mark as trusted)
     */
    suspend fun pinKey(keyId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val metadata = getKeyMetadata(keyId)
            if (metadata == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Key not found: $keyId")
                )
            }

            // Update metadata to mark as pinned
            val updatedMetadata = metadata.copy(isPinned = true)
            saveKeyAndMetadata(updatedMetadata)

            // Log key pinning
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "key_pinning",
                    "key_id" to keyId,
                    "clinic_id" to metadata.clinicId,
                    "timestamp" to dateFormat.format(Date())
                )
            )

            Timber.i("Pinned key: $keyId")
            Result.success(true)

        } catch (e: Exception) {
            Timber.e(e, "Failed to pin key: $keyId")
            Result.failure(e)
        }
    }

    /**
     * Rollback to previous key
     */
    suspend fun rollbackKey(
        clinicId: String,
        currentKeyId: String
    ): Result<KeyProvisioningResult> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            
            // Find previous active key for this clinic
            val previousKey = findPreviousActiveKey(clinicId, currentKeyId)
            if (previousKey == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("No previous key found for rollback")
                )
            }

            // Deactivate current key
            deactivateKey(currentKeyId)
            
            // Reactivate previous key
            val reactivatedMetadata = previousKey.copy(
                isActive = true,
                version = previousKey.version + 1
            )
            saveKeyAndMetadata(reactivatedMetadata)

            // Log key rollback
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "key_rollback",
                    "clinic_id" to clinicId,
                    "from_key_id" to currentKeyId,
                    "to_key_id" to previousKey.keyId,
                    "timestamp" to timestamp
                )
            )

            val result = KeyProvisioningResult(
                success = true,
                keyId = previousKey.keyId,
                keyType = previousKey.keyType,
                keySize = previousKey.keySize,
                clinicId = clinicId,
                operation = "rollback",
                timestamp = timestamp
            )

            Timber.i("Rolled back key: $currentKeyId -> ${previousKey.keyId} for clinic: $clinicId")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to rollback key for clinic: $clinicId")
            Result.failure(e)
        }
    }

    /**
     * Generate key ID
     */
    private fun generateKeyId(clinicId: String, keyType: String): String {
        val timestamp = System.currentTimeMillis()
        val random = UUID.randomUUID().toString().take(8)
        return "${clinicId}_${keyType.lowercase()}_${timestamp}_${random}"
    }

    /**
     * Parse public key from PEM format
     */
    private fun parsePublicKeyFromPem(pemString: String, keyType: String): PublicKey? {
        return try {
            val cleanPem = pemString
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            
            val keyBytes = android.util.Base64.decode(cleanPem, android.util.Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            
            when (keyType.uppercase()) {
                "RSA" -> KeyFactory.getInstance("RSA").generatePublic(keySpec)
                "ECC", "EC" -> KeyFactory.getInstance("EC").generatePublic(keySpec)
                else -> null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse public key from PEM")
            null
        }
    }

    /**
     * Get key size
     */
    private fun getKeySize(publicKey: PublicKey, keyType: String): Int {
        return when (keyType.uppercase()) {
            "RSA" -> {
                val rsaKey = publicKey as java.security.interfaces.RSAPublicKey
                rsaKey.modulus.bitLength()
            }
            "ECC", "EC" -> {
                val ecKey = publicKey as java.security.interfaces.ECPublicKey
                ecKey.params.curve.field.size
            }
            else -> 0
        }
    }

    /**
     * Validate key size
     */
    private fun isValidKeySize(keySize: Int, keyType: String): Boolean {
        return when (keyType.uppercase()) {
            "RSA" -> keySize >= 2048
            "ECC", "EC" -> keySize >= 256
            else -> false
        }
    }

    /**
     * Save key and metadata
     */
    private suspend fun saveKeyAndMetadata(metadata: KeyMetadata) = withContext(Dispatchers.IO) {
        try {
            val clinicKeysDir = File(context.filesDir, CLINIC_KEYS_DIR)
            if (!clinicKeysDir.exists()) {
                clinicKeysDir.mkdirs()
            }

            val keyMetadataDir = File(context.filesDir, KEY_METADATA_DIR)
            if (!keyMetadataDir.exists()) {
                keyMetadataDir.mkdirs()
            }

            // Save public key
            val keyFile = File(clinicKeysDir, "${metadata.keyId}.key")
            keyFile.writeBytes(metadata.publicKey.encoded)

            // Save metadata
            val metadataFile = File(keyMetadataDir, "${metadata.keyId}.json")
            val metadataJson = generateMetadataJson(metadata)
            metadataFile.writeText(metadataJson)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save key and metadata: ${metadata.keyId}")
        }
    }

    /**
     * Generate metadata JSON
     */
    private fun generateMetadataJson(metadata: KeyMetadata): String {
        return """
        {
            "keyId": "${metadata.keyId}",
            "clinicId": "${metadata.clinicId}",
            "keyType": "${metadata.keyType}",
            "keySize": ${metadata.keySize},
            "createdDate": "${dateFormat.format(metadata.createdDate)}",
            "expiresDate": "${dateFormat.format(metadata.expiresDate)}",
            "isActive": ${metadata.isActive},
            "isPinned": ${metadata.isPinned},
            "version": ${metadata.version}
        }
        """.trimIndent()
    }

    /**
     * Get key metadata
     */
    private suspend fun getKeyMetadata(keyId: String): KeyMetadata? = withContext(Dispatchers.IO) {
        try {
            val metadataFile = File(context.filesDir, KEY_METADATA_DIR, "$keyId.json")
            if (!metadataFile.exists()) {
                return@withContext null
            }

            val json = metadataFile.readText()
            parseMetadataFromJson(json)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get key metadata: $keyId")
            null
        }
    }

    /**
     * Parse metadata from JSON
     */
    private fun parseMetadataFromJson(json: String): KeyMetadata? {
        return try {
            val cleanJson = json.trim().removePrefix("{").removeSuffix("}")
            val pairs = cleanJson.split(",").map { it.trim() }
            val data = mutableMapOf<String, String>()
            
            for (pair in pairs) {
                val (key, value) = pair.split(":", limit = 2)
                val cleanKey = key.trim().removeSurrounding("\"")
                val cleanValue = value.trim().removeSurrounding("\"")
                data[cleanKey] = cleanValue
            }

            // Load public key
            val keyFile = File(context.filesDir, CLINIC_KEYS_DIR, "${data["keyId"]}.key")
            val publicKeyBytes = keyFile.readBytes()
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance(data["keyType"] ?: "RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            KeyMetadata(
                keyId = data["keyId"] ?: "",
                clinicId = data["clinicId"] ?: "",
                keyType = data["keyType"] ?: "",
                keySize = data["keySize"]?.toIntOrNull() ?: 0,
                publicKey = publicKey,
                createdDate = dateFormat.parse(data["createdDate"] ?: "") ?: Date(),
                expiresDate = dateFormat.parse(data["expiresDate"] ?: "") ?: Date(),
                isActive = data["isActive"]?.toBoolean() ?: false,
                isPinned = data["isPinned"]?.toBoolean() ?: false,
                version = data["version"]?.toIntOrNull() ?: 1
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse metadata from JSON")
            null
        }
    }

    /**
     * Deactivate key
     */
    private suspend fun deactivateKey(keyId: String) = withContext(Dispatchers.IO) {
        try {
            val metadata = getKeyMetadata(keyId)
            if (metadata != null) {
                val deactivatedMetadata = metadata.copy(isActive = false)
                saveKeyAndMetadata(deactivatedMetadata)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to deactivate key: $keyId")
        }
    }

    /**
     * Find previous active key
     */
    private suspend fun findPreviousActiveKey(clinicId: String, currentKeyId: String): KeyMetadata? = withContext(Dispatchers.IO) {
        try {
            val keyMetadataDir = File(context.filesDir, KEY_METADATA_DIR)
            if (!keyMetadataDir.exists()) {
                return@withContext null
            }

            val metadataFiles = keyMetadataDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            } ?: emptyArray()

            val clinicKeys = mutableListOf<KeyMetadata>()
            for (file in metadataFiles) {
                try {
                    val metadata = parseMetadataFromJson(file.readText())
                    if (metadata != null && metadata.clinicId == clinicId && metadata.keyId != currentKeyId) {
                        clinicKeys.add(metadata)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse metadata file: ${file.name}")
                }
            }

            // Sort by version (descending) and return the most recent inactive key
            clinicKeys.sortByDescending { it.version }
            clinicKeys.find { !it.isActive }

        } catch (e: Exception) {
            Timber.e(e, "Failed to find previous active key for clinic: $clinicId")
            null
        }
    }

    /**
     * Get active keys for clinic
     */
    suspend fun getActiveKeysForClinic(clinicId: String): Result<List<KeyMetadata>> = withContext(Dispatchers.IO) {
        try {
            val keyMetadataDir = File(context.filesDir, KEY_METADATA_DIR)
            if (!keyMetadataDir.exists()) {
                return@withContext Result.success(emptyList())
            }

            val metadataFiles = keyMetadataDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            } ?: emptyArray()

            val activeKeys = mutableListOf<KeyMetadata>()
            for (file in metadataFiles) {
                try {
                    val metadata = parseMetadataFromJson(file.readText())
                    if (metadata != null && metadata.clinicId == clinicId && metadata.isActive) {
                        activeKeys.add(metadata)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse metadata file: ${file.name}")
                }
            }

            Result.success(activeKeys)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get active keys for clinic: $clinicId")
            Result.failure(e)
        }
    }

    /**
     * Clean up expired keys
     */
    suspend fun cleanupExpiredKeys(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (KEY_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            var cleanedCount = 0

            val keyMetadataDir = File(context.filesDir, KEY_METADATA_DIR)
            if (!keyMetadataDir.exists()) {
                return@withContext Result.success(0)
            }

            val metadataFiles = keyMetadataDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            } ?: emptyArray()

            for (file in metadataFiles) {
                try {
                    val metadata = parseMetadataFromJson(file.readText())
                    if (metadata != null && metadata.createdDate.time < cutoffTime) {
                        // Delete key file
                        val keyFile = File(context.filesDir, CLINIC_KEYS_DIR, "${metadata.keyId}.key")
                        if (keyFile.exists()) {
                            keyFile.delete()
                        }
                        
                        // Delete metadata file
                        if (file.delete()) {
                            cleanedCount++
                            Timber.d("Cleaned up expired key: ${metadata.keyId}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to process metadata file: ${file.name}")
                }
            }

            Timber.i("Cleaned up $cleanedCount expired keys")
            Result.success(cleanedCount)

        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup expired keys")
            Result.failure(e)
        }
    }

    /**
     * Test key provisioning
     */
    suspend fun testKeyProvisioning(clinicId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Generate test RSA key pair
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(RSA_KEY_SIZE)
            val testKeyPair = keyPairGenerator.generateKeyPair()
            
            // Convert to PEM format
            val publicKeyPem = convertToPem(testKeyPair.public)
            
            // Test upload
            val uploadResult = uploadClinicKey(clinicId, publicKeyPem, "RSA")
            if (uploadResult.isFailure) {
                return@withContext Result.failure(uploadResult.exceptionOrNull()!!)
            }

            val uploadedKey = uploadResult.getOrThrow()
            
            // Test pinning
            val pinResult = pinKey(uploadedKey.keyId)
            if (pinResult.isFailure) {
                return@withContext Result.failure(pinResult.exceptionOrNull()!!)
            }

            // Test rotation
            val newKeyPair = keyPairGenerator.generateKeyPair()
            val newPublicKeyPem = convertToPem(newKeyPair.public)
            val rotationResult = rotateClinicKey(clinicId, uploadedKey.keyId, newPublicKeyPem, "RSA")
            if (rotationResult.isFailure) {
                return@withContext Result.failure(rotationResult.exceptionOrNull()!!)
            }

            val rotatedKey = rotationResult.getOrThrow()
            
            // Test rollback
            val rollbackResult = rollbackKey(clinicId, rotatedKey.keyId)
            if (rollbackResult.isFailure) {
                return@withContext Result.failure(rollbackResult.exceptionOrNull()!!)
            }

            Timber.i("Key provisioning test completed successfully for clinic: $clinicId")
            Result.success(true)

        } catch (e: Exception) {
            Timber.e(e, "Failed to test key provisioning for clinic: $clinicId")
            Result.failure(e)
        }
    }

    /**
     * Convert public key to PEM format
     */
    private fun convertToPem(publicKey: PublicKey): String {
        val encoded = publicKey.encoded
        val base64 = android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }
}
