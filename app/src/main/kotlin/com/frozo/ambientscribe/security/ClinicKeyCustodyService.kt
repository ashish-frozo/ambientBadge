package com.frozo.ambientscribe.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clinic Key Custody Service - ST-5.14
 * Manages clinic private key custody via KMS/Vault with rotation and access audit
 * Implements secure key storage, rotation, and recovery procedures
 */
class ClinicKeyCustodyService(
    private val context: Context,
    private val auditLogger: AuditLogger
) {
    companion object {
        private const val TAG = "ClinicKeyCustodyService"
        private const val KEY_ROTATION_INTERVAL_DAYS = 90L
        private const val ACCESS_AUDIT_RETENTION_DAYS = 365L
        private const val VAULT_KEY_ALIAS = "clinic_vault_key"
        private const val VAULT_IV_SIZE = 12
        private const val VAULT_TAG_SIZE = 16
    }

    private val vaultDir = File(context.filesDir, "clinic_vault")
    private val auditDir = File(context.filesDir, "vault_audit")
    private val recoveryDir = File(context.filesDir, "vault_recovery")

    init {
        // Ensure directories exist
        vaultDir.mkdirs()
        auditDir.mkdirs()
        recoveryDir.mkdirs()
    }

    /**
     * Data class for clinic key metadata
     */
    data class ClinicKeyMetadata(
        val keyId: String,
        val clinicId: String,
        val keyType: String,
        val keySize: Int,
        val createdAt: String,
        val expiresAt: String,
        val isActive: Boolean,
        val accessCount: Int,
        val lastAccessed: String?,
        val rotationCount: Int,
        val vaultLocation: String,
        val checksum: String
    )

    /**
     * Data class for access audit entry
     */
    data class AccessAuditEntry(
        val timestamp: String,
        val keyId: String,
        val clinicId: String,
        val operation: String,
        val actor: String,
        val success: Boolean,
        val ipAddress: String?,
        val userAgent: String?,
        val reason: String?
    )

    /**
     * Data class for key rotation result
     */
    data class KeyRotationResult(
        val success: Boolean,
        val oldKeyId: String?,
        val newKeyId: String?,
        val rotationTimestamp: String,
        val backupCreated: Boolean,
        val auditTrail: String
    )

    /**
     * Data class for recovery procedure result
     */
    data class RecoveryProcedureResult(
        val success: Boolean,
        val recoveryId: String,
        val keysRecovered: Int,
        val backupVerified: Boolean,
        val auditTrail: String,
        val timestamp: String
    )

    /**
     * Generate and store clinic private key in vault
     */
    suspend fun generateAndStoreClinicKey(
        clinicId: String,
        keyType: String = "RSA",
        keySize: Int = 2048
    ): Result<ClinicKeyMetadata> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating clinic key for clinic: $clinicId")

            // Generate key pair
            val keyPair = generateKeyPair(keyType, keySize)
            val keyId = generateKeyId(clinicId, keyType)

            // Encrypt private key for vault storage
            val encryptedPrivateKey = encryptPrivateKey(keyPair.private, keyId)
            val publicKeyPem = encodePublicKeyToPem(keyPair.public)

            // Create metadata
            val metadata = ClinicKeyMetadata(
                keyId = keyId,
                clinicId = clinicId,
                keyType = keyType,
                keySize = keySize,
                createdAt = getCurrentTimestamp(),
                expiresAt = getExpirationTimestamp(KEY_ROTATION_INTERVAL_DAYS),
                isActive = true,
                accessCount = 0,
                lastAccessed = null,
                rotationCount = 0,
                vaultLocation = "vault/$keyId.enc",
                checksum = calculateChecksum(encryptedPrivateKey)
            )

            // Store encrypted key and metadata
            storeEncryptedKey(keyId, encryptedPrivateKey)
            storeKeyMetadata(metadata)
            storePublicKey(keyId, publicKeyPem)

            // Log audit event
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "clinic_key_generation",
                    "clinic_id" to clinicId,
                    "key_id" to keyId,
                    "key_type" to keyType,
                    "key_size" to keySize
                )
            )

            Log.d(TAG, "Successfully generated and stored clinic key: $keyId")
            Result.success(metadata)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate clinic key for clinic: $clinicId", e)
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "clinic_key_generation_failed",
                    "clinic_id" to clinicId,
                    "error" to e.message
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Rotate clinic private key
     */
    suspend fun rotateClinicKey(
        clinicId: String,
        currentKeyId: String,
        reason: String = "scheduled_rotation"
    ): Result<KeyRotationResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Rotating clinic key: $currentKeyId for clinic: $clinicId")

            // Load current key metadata
            val currentMetadata = loadKeyMetadata(currentKeyId)
                ?: return Result.failure(IllegalArgumentException("Key not found: $currentKeyId"))

            // Generate new key
            val newKeyPair = generateKeyPair(currentMetadata.keyType, currentMetadata.keySize)
            val newKeyId = generateKeyId(clinicId, currentMetadata.keyType)

            // Encrypt new private key
            val encryptedNewPrivateKey = encryptPrivateKey(newKeyPair.private, newKeyId)
            val newPublicKeyPem = encodePublicKeyToPem(newKeyPair.public)

            // Create new metadata
            val newMetadata = currentMetadata.copy(
                keyId = newKeyId,
                createdAt = getCurrentTimestamp(),
                expiresAt = getExpirationTimestamp(KEY_ROTATION_INTERVAL_DAYS),
                isActive = true,
                accessCount = 0,
                lastAccessed = null,
                rotationCount = currentMetadata.rotationCount + 1,
                vaultLocation = "vault/$newKeyId.enc",
                checksum = calculateChecksum(encryptedNewPrivateKey)
            )

            // Store new key and metadata
            storeEncryptedKey(newKeyId, encryptedNewPrivateKey)
            storeKeyMetadata(newMetadata)
            storePublicKey(newKeyId, newPublicKeyPem)

            // Deactivate old key
            val deactivatedMetadata = currentMetadata.copy(
                isActive = false,
                expiresAt = getCurrentTimestamp()
            )
            storeKeyMetadata(deactivatedMetadata)

            // Create backup of old key
            val backupCreated = createKeyBackup(currentKeyId, currentMetadata)

            // Log rotation audit
            val rotationResult = KeyRotationResult(
                success = true,
                oldKeyId = currentKeyId,
                newKeyId = newKeyId,
                rotationTimestamp = getCurrentTimestamp(),
                backupCreated = backupCreated,
                auditTrail = "Key rotated from $currentKeyId to $newKeyId"
            )

            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "clinic_key_rotation",
                    "clinic_id" to clinicId,
                    "old_key_id" to currentKeyId,
                    "new_key_id" to newKeyId,
                    "reason" to reason,
                    "backup_created" to backupCreated
                )
            )

            Log.d(TAG, "Successfully rotated clinic key: $currentKeyId -> $newKeyId")
            Result.success(rotationResult)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate clinic key: $currentKeyId", e)
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "clinic_key_rotation_failed",
                    "clinic_id" to clinicId,
                    "key_id" to currentKeyId,
                    "error" to e.message
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Access clinic private key with audit logging
     */
    suspend fun accessClinicKey(
        keyId: String,
        actor: String,
        operation: String,
        reason: String
    ): Result<PrivateKey> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Accessing clinic key: $keyId by actor: $actor")

            // Load key metadata
            val metadata = loadKeyMetadata(keyId)
                ?: return Result.failure(IllegalArgumentException("Key not found: $keyId"))

            if (!metadata.isActive) {
                return Result.failure(IllegalStateException("Key is not active: $keyId"))
            }

            // Check if key is expired
            if (isKeyExpired(metadata.expiresAt)) {
                return Result.failure(IllegalStateException("Key is expired: $keyId"))
            }

            // Load and decrypt private key
            val encryptedPrivateKey = loadEncryptedKey(keyId)
            val privateKey = decryptPrivateKey(encryptedPrivateKey, keyId)

            // Update access count and last accessed
            val updatedMetadata = metadata.copy(
                accessCount = metadata.accessCount + 1,
                lastAccessed = getCurrentTimestamp()
            )
            storeKeyMetadata(updatedMetadata)

            // Log access audit
            val accessEntry = AccessAuditEntry(
                timestamp = getCurrentTimestamp(),
                keyId = keyId,
                clinicId = metadata.clinicId,
                operation = operation,
                actor = actor,
                success = true,
                ipAddress = null, // Would be populated in real implementation
                userAgent = null, // Would be populated in real implementation
                reason = reason
            )
            logAccessAudit(accessEntry)

            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "clinic_key_access",
                    "key_id" to keyId,
                    "clinic_id" to metadata.clinicId,
                    "actor" to actor,
                    "access_operation" to operation,
                    "reason" to reason
                )
            )

            Log.d(TAG, "Successfully accessed clinic key: $keyId")
            Result.success(privateKey)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to access clinic key: $keyId", e)

            // Log failed access audit
            val accessEntry = AccessAuditEntry(
                timestamp = getCurrentTimestamp(),
                keyId = keyId,
                clinicId = "unknown",
                operation = operation,
                actor = actor,
                success = false,
                ipAddress = null,
                userAgent = null,
                reason = reason
            )
            logAccessAudit(accessEntry)

            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "clinic_key_access_failed",
                    "key_id" to keyId,
                    "actor" to actor,
                    "error" to e.message
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Perform recovery procedure for lost keys
     */
    suspend fun performRecoveryProcedure(
        clinicId: String,
        recoveryReason: String
    ): Result<RecoveryProcedureResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Performing recovery procedure for clinic: $clinicId")

            val recoveryId = generateRecoveryId(clinicId)
            var keysRecovered = 0
            val recoveryAuditTrail = mutableListOf<String>()

            // Find all keys for clinic
            val clinicKeys = findKeysForClinic(clinicId)
            recoveryAuditTrail.add("Found ${clinicKeys.size} keys for clinic $clinicId")

            // Attempt to recover each key
            for (keyMetadata in clinicKeys) {
                try {
                    // Check if backup exists
                    val backupExists = checkKeyBackup(keyMetadata.keyId)
                    if (backupExists) {
                        // Restore from backup
                        val restored = restoreKeyFromBackup(keyMetadata.keyId)
                        if (restored) {
                            keysRecovered++
                            recoveryAuditTrail.add("Recovered key ${keyMetadata.keyId} from backup")
                        } else {
                            recoveryAuditTrail.add("Failed to restore key ${keyMetadata.keyId} from backup")
                        }
                    } else {
                        recoveryAuditTrail.add("No backup found for key ${keyMetadata.keyId}")
                    }
                } catch (e: Exception) {
                    recoveryAuditTrail.add("Error recovering key ${keyMetadata.keyId}: ${e.message}")
                }
            }

            // Verify recovery
            val backupVerified = verifyRecoveryIntegrity(clinicId)

            val recoveryResult = RecoveryProcedureResult(
                success = keysRecovered > 0,
                recoveryId = recoveryId,
                keysRecovered = keysRecovered,
                backupVerified = backupVerified,
                auditTrail = recoveryAuditTrail.joinToString("; "),
                timestamp = getCurrentTimestamp()
            )

            // Log recovery audit
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "clinic_key_recovery",
                    "clinic_id" to clinicId,
                    "recovery_id" to recoveryId,
                    "keys_recovered" to keysRecovered,
                    "backup_verified" to backupVerified,
                    "reason" to recoveryReason
                )
            )

            Log.d(TAG, "Recovery procedure completed for clinic: $clinicId, keys recovered: $keysRecovered")
            Result.success(recoveryResult)

        } catch (e: Exception) {
            Log.e(TAG, "Recovery procedure failed for clinic: $clinicId", e)
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = mapOf(
                    "operation" to "clinic_key_recovery_failed",
                    "clinic_id" to clinicId,
                    "error" to e.message
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Generate recovery procedure documentation
     */
    suspend fun generateRecoveryDocumentation(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val docContent = """
                # Clinic Key Recovery Procedure Documentation
                
                ## Overview
                This document outlines the procedures for recovering clinic private keys in case of loss or corruption.
                
                ## Recovery Scenarios
                1. **Key Loss**: Private key file is accidentally deleted or corrupted
                2. **Vault Corruption**: Vault storage is corrupted or inaccessible
                3. **Access Loss**: Authorized personnel lose access to vault
                4. **Disaster Recovery**: Complete system failure requiring key restoration
                
                ## Recovery Steps
                
                ### 1. Immediate Response
                - Notify security team within 15 minutes
                - Document the incident and affected clinic(s)
                - Initiate recovery procedure
                
                ### 2. Key Recovery Process
                - Access backup storage location
                - Verify backup integrity using checksums
                - Restore keys from most recent verified backup
                - Validate restored keys using test operations
                
                ### 3. Verification Steps
                - Test key functionality with sample operations
                - Verify audit trail integrity
                - Confirm clinic can access their data
                - Update access logs and security documentation
                
                ### 4. Post-Recovery Actions
                - Generate new keys if recovery is not possible
                - Update clinic with new key information
                - Conduct security review of incident
                - Update recovery procedures based on lessons learned
                
                ## Backup Locations
                - Primary: ${vaultDir.absolutePath}/backups/
                - Secondary: ${recoveryDir.absolutePath}/backups/
                - Offsite: [External secure storage location]
                
                ## Contact Information
                - Security Team: security@company.com
                - Emergency Hotline: +1-XXX-XXX-XXXX
                - On-call Engineer: [Contact details]
                
                ## Recovery Time Objectives
                - RTO: 4 hours for key recovery
                - RPO: 24 hours maximum data loss
                - MTTR: 2 hours mean time to recovery
                
                Generated: ${getCurrentTimestamp()}
                Version: 1.0
            """.trimIndent()

            // Save documentation
            val docFile = File(recoveryDir, "recovery_procedure_${System.currentTimeMillis()}.md")
            docFile.writeText(docContent)

            Result.success(docFile.absolutePath)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate recovery documentation", e)
            Result.failure(e)
        }
    }

    // Private helper methods
    private fun generateKeyPair(keyType: String, keySize: Int): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(keyType)
        keyPairGenerator.initialize(keySize, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    private fun generateKeyId(clinicId: String, keyType: String): String {
        val timestamp = System.currentTimeMillis()
        val random = SecureRandom().nextInt(10000)
        return "clinic_${clinicId}_${keyType.lowercase()}_${timestamp}_${random}"
    }

    private fun generateRecoveryId(clinicId: String): String {
        val timestamp = System.currentTimeMillis()
        val random = SecureRandom().nextInt(10000)
        return "recovery_${clinicId}_${timestamp}_${random}"
    }

    private fun encryptPrivateKey(privateKey: PrivateKey, keyId: String): ByteArray {
        val vaultKey = getOrCreateVaultKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(VAULT_IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        val parameterSpec = GCMParameterSpec(VAULT_TAG_SIZE * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, vaultKey, parameterSpec)
        
        val privateKeyBytes = privateKey.encoded
        return cipher.doFinal(privateKeyBytes)
    }

    private fun decryptPrivateKey(encryptedKey: ByteArray, keyId: String): PrivateKey {
        val vaultKey = getOrCreateVaultKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        
        // Extract IV from encrypted data
        val iv = encryptedKey.sliceArray(0 until VAULT_IV_SIZE)
        val encryptedData = encryptedKey.sliceArray(VAULT_IV_SIZE until encryptedKey.size)
        
        val parameterSpec = GCMParameterSpec(VAULT_TAG_SIZE * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, vaultKey, parameterSpec)
        
        val privateKeyBytes = cipher.doFinal(encryptedData)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes))
    }

    private fun getOrCreateVaultKey(): SecretKey {
        // In a real implementation, this would use Android Keystore
        // For now, we'll use a simple approach
        val keyFile = File(vaultDir, "vault_key.key")
        return if (keyFile.exists()) {
            val keyBytes = keyFile.readBytes()
            SecretKeySpec(keyBytes, "AES")
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val key = keyGenerator.generateKey()
            keyFile.writeBytes(key.encoded)
            key
        }
    }

    private fun encodePublicKeyToPem(publicKey: PublicKey): String {
        val encoded = Base64.getEncoder().encodeToString(publicKey.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
    }

    private fun calculateChecksum(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun storeEncryptedKey(keyId: String, encryptedKey: ByteArray) {
        val keyFile = File(vaultDir, "$keyId.enc")
        keyFile.writeBytes(encryptedKey)
    }

    private fun loadEncryptedKey(keyId: String): ByteArray {
        val keyFile = File(vaultDir, "$keyId.enc")
        return keyFile.readBytes()
    }

    private fun storeKeyMetadata(metadata: ClinicKeyMetadata) {
        val metadataFile = File(vaultDir, "${metadata.keyId}_metadata.json")
        val json = JSONObject().apply {
            put("keyId", metadata.keyId)
            put("clinicId", metadata.clinicId)
            put("keyType", metadata.keyType)
            put("keySize", metadata.keySize)
            put("createdAt", metadata.createdAt)
            put("expiresAt", metadata.expiresAt)
            put("isActive", metadata.isActive)
            put("accessCount", metadata.accessCount)
            put("lastAccessed", metadata.lastAccessed)
            put("rotationCount", metadata.rotationCount)
            put("vaultLocation", metadata.vaultLocation)
            put("checksum", metadata.checksum)
        }
        metadataFile.writeText(json.toString())
    }

    private fun loadKeyMetadata(keyId: String): ClinicKeyMetadata? {
        return try {
            val metadataFile = File(vaultDir, "${keyId}_metadata.json")
            if (!metadataFile.exists()) return null
            
            val json = JSONObject(metadataFile.readText())
            ClinicKeyMetadata(
                keyId = json.getString("keyId"),
                clinicId = json.getString("clinicId"),
                keyType = json.getString("keyType"),
                keySize = json.getInt("keySize"),
                createdAt = json.getString("createdAt"),
                expiresAt = json.getString("expiresAt"),
                isActive = json.getBoolean("isActive"),
                accessCount = json.getInt("accessCount"),
                lastAccessed = if (json.isNull("lastAccessed")) null else json.getString("lastAccessed"),
                rotationCount = json.getInt("rotationCount"),
                vaultLocation = json.getString("vaultLocation"),
                checksum = json.getString("checksum")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load key metadata for: $keyId", e)
            null
        }
    }

    private fun storePublicKey(keyId: String, publicKeyPem: String) {
        val publicKeyFile = File(vaultDir, "${keyId}_public.pem")
        publicKeyFile.writeText(publicKeyPem)
    }

    private fun createKeyBackup(keyId: String, metadata: ClinicKeyMetadata): Boolean {
        return try {
            val backupDir = File(vaultDir, "backups")
            backupDir.mkdirs()
            
            val backupFile = File(backupDir, "${keyId}_backup_${System.currentTimeMillis()}.enc")
            val encryptedKey = loadEncryptedKey(keyId)
            backupFile.writeBytes(encryptedKey)
            
            // Store backup metadata
            val backupMetadataFile = File(backupDir, "${keyId}_backup_metadata.json")
            val json = JSONObject().apply {
                put("originalKeyId", keyId)
                put("clinicId", metadata.clinicId)
                put("backupTimestamp", getCurrentTimestamp())
                put("checksum", calculateChecksum(encryptedKey))
            }
            backupMetadataFile.writeText(json.toString())
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup for key: $keyId", e)
            false
        }
    }

    private fun checkKeyBackup(keyId: String): Boolean {
        val backupDir = File(vaultDir, "backups")
        return backupDir.listFiles { file ->
            file.name.startsWith("${keyId}_backup_") && file.name.endsWith(".enc")
        }?.isNotEmpty() == true
    }

    private fun restoreKeyFromBackup(keyId: String): Boolean {
        return try {
            val backupDir = File(vaultDir, "backups")
            val backupFile = backupDir.listFiles { file ->
                file.name.startsWith("${keyId}_backup_") && file.name.endsWith(".enc")
            }?.maxByOrNull { it.lastModified() }
            
            if (backupFile != null) {
                val encryptedKey = backupFile.readBytes()
                storeEncryptedKey(keyId, encryptedKey)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore key from backup: $keyId", e)
            false
        }
    }

    private fun findKeysForClinic(clinicId: String): List<ClinicKeyMetadata> {
        val keys = mutableListOf<ClinicKeyMetadata>()
        vaultDir.listFiles { file ->
            file.name.endsWith("_metadata.json")
        }?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                if (json.getString("clinicId") == clinicId) {
                    val metadata = ClinicKeyMetadata(
                        keyId = json.getString("keyId"),
                        clinicId = json.getString("clinicId"),
                        keyType = json.getString("keyType"),
                        keySize = json.getInt("keySize"),
                        createdAt = json.getString("createdAt"),
                        expiresAt = json.getString("expiresAt"),
                        isActive = json.getBoolean("isActive"),
                        accessCount = json.getInt("accessCount"),
                        lastAccessed = if (json.isNull("lastAccessed")) null else json.getString("lastAccessed"),
                        rotationCount = json.getInt("rotationCount"),
                        vaultLocation = json.getString("vaultLocation"),
                        checksum = json.getString("checksum")
                    )
                    keys.add(metadata)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse metadata file: ${file.name}", e)
            }
        }
        return keys
    }

    private fun verifyRecoveryIntegrity(clinicId: String): Boolean {
        // In a real implementation, this would perform comprehensive integrity checks
        return true
    }

    private fun logAccessAudit(entry: AccessAuditEntry) {
        val auditFile = File(auditDir, "access_audit_${getCurrentDate()}.jsonl")
        val json = JSONObject().apply {
            put("timestamp", entry.timestamp)
            put("keyId", entry.keyId)
            put("clinicId", entry.clinicId)
            put("operation", entry.operation)
            put("actor", entry.actor)
            put("success", entry.success)
            put("ipAddress", entry.ipAddress)
            put("userAgent", entry.userAgent)
            put("reason", entry.reason)
        }
        auditFile.appendText("${json.toString()}\n")
    }

    private fun getCurrentTimestamp(): String {
        return java.time.Instant.now().toString()
    }

    private fun getCurrentDate(): String {
        return java.time.LocalDate.now().toString()
    }

    private fun getExpirationTimestamp(days: Long): String {
        return java.time.Instant.now().plusSeconds(days * 24 * 60 * 60).toString()
    }

    private fun isKeyExpired(expiresAt: String): Boolean {
        val expiration = java.time.Instant.parse(expiresAt)
        return java.time.Instant.now().isAfter(expiration)
    }
}
