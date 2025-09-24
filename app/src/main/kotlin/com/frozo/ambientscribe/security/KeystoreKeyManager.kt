package com.frozo.ambientscribe.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.KeyStore
import java.util.Date
import javax.crypto.KeyGenerator

/**
 * Enhanced Android Keystore key management with 180-day rotation
 * Manages encryption keys for PDF and JSON data
 */
class KeystoreKeyManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ROTATION_INTERVAL = 180L * 24 * 60 * 60 * 1000 // 180 days
        private const val KEY_RETENTION_INTERVAL = 365L * 24 * 60 * 60 * 1000 // 365 days
        private const val PREFS_NAME = "keystore_key_manager"
    }

    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Key metadata
     */
    data class KeyMetadata(
        val keyAlias: String,
        val keyId: String,
        val creationDate: Date,
        val lastUsed: Date,
        val rotationCount: Int,
        val isActive: Boolean
    )

    /**
     * Get or create key for given purpose
     */
    suspend fun getOrCreateKey(
        keyAlias: String,
        purposes: Int = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    ): String = withContext(Dispatchers.IO) {
        try {
            // Check if key exists and is valid
            val existingKey = keyStore.getKey(keyAlias, null)
            if (existingKey != null && !needsKeyRotation(keyAlias)) {
                updateKeyUsage(keyAlias)
                return@withContext keyAlias
            }

            // Generate new key
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )

            val keySpec = KeyGenParameterSpec.Builder(
                keyAlias,
                purposes
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // For background operations
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keySpec)
            val newKey = keyGenerator.generateKey()

            // Store key metadata
            val keyId = generateKeyId()
            val now = System.currentTimeMillis()
            storeKeyMetadata(keyAlias, keyId, now, 0, true)

            Timber.d("Generated new key: $keyAlias with ID: $keyId")
            keyAlias

        } catch (e: Exception) {
            Timber.e(e, "Failed to get or create key: $keyAlias")
            throw e
        }
    }

    /**
     * Rotate key if needed
     */
    suspend fun rotateKeyIfNeeded(keyAlias: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!needsKeyRotation(keyAlias)) {
                return@withContext Result.success(keyAlias)
            }

            // Create new key with rotated alias
            val newKeyAlias = "${keyAlias}_${System.currentTimeMillis()}"
            val newKeyId = generateKeyId()
            
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )

            val keySpec = KeyGenParameterSpec.Builder(
                newKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keySpec)
            val newKey = keyGenerator.generateKey()

            // Update metadata
            val now = System.currentTimeMillis()
            val rotationCount = getKeyRotationCount(keyAlias) + 1
            
            // Mark old key as inactive
            markKeyInactive(keyAlias)
            
            // Store new key metadata
            storeKeyMetadata(newKeyAlias, newKeyId, now, rotationCount, true)

            Timber.d("Rotated key: $keyAlias -> $newKeyAlias")
            Result.success(newKeyAlias)

        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate key: $keyAlias")
            Result.failure(e)
        }
    }

    /**
     * Get key metadata
     */
    fun getKeyMetadata(keyAlias: String): KeyMetadata? {
        val keyId = encryptedPrefs.getString("${keyAlias}_key_id", null) ?: return null
        val creationDate = encryptedPrefs.getLong("${keyAlias}_creation_date", 0)
        val lastUsed = encryptedPrefs.getLong("${keyAlias}_last_used", 0)
        val rotationCount = encryptedPrefs.getInt("${keyAlias}_rotation_count", 0)
        val isActive = encryptedPrefs.getBoolean("${keyAlias}_is_active", false)

        return KeyMetadata(
            keyAlias = keyAlias,
            keyId = keyId,
            creationDate = Date(creationDate),
            lastUsed = Date(lastUsed),
            rotationCount = rotationCount,
            isActive = isActive
        )
    }

    /**
     * Get all active keys
     */
    fun getActiveKeys(): List<KeyMetadata> {
        val allKeys = encryptedPrefs.all.keys
        val activeKeys = mutableListOf<KeyMetadata>()

        for (key in allKeys) {
            if (key.endsWith("_is_active") && encryptedPrefs.getBoolean(key, false)) {
                val keyAlias = key.removeSuffix("_is_active")
                getKeyMetadata(keyAlias)?.let { activeKeys.add(it) }
            }
        }

        return activeKeys.sortedBy { it.creationDate }
    }

    /**
     * Check if key needs rotation
     */
    fun needsKeyRotation(keyAlias: String): Boolean {
        val creationDate = encryptedPrefs.getLong("${keyAlias}_creation_date", 0)
        if (creationDate == 0L) return true

        return System.currentTimeMillis() - creationDate >= KEY_ROTATION_INTERVAL
    }

    /**
     * Check if key should be retired
     */
    fun shouldRetireKey(keyAlias: String): Boolean {
        val creationDate = encryptedPrefs.getLong("${keyAlias}_creation_date", 0)
        if (creationDate == 0L) return false

        return System.currentTimeMillis() - creationDate >= KEY_RETENTION_INTERVAL
    }

    /**
     * Update key usage timestamp
     */
    private fun updateKeyUsage(keyAlias: String) {
        encryptedPrefs.edit()
            .putLong("${keyAlias}_last_used", System.currentTimeMillis())
            .apply()
    }

    /**
     * Store key metadata
     */
    private fun storeKeyMetadata(
        keyAlias: String,
        keyId: String,
        creationDate: Long,
        rotationCount: Int,
        isActive: Boolean
    ) {
        encryptedPrefs.edit()
            .putString("${keyAlias}_key_id", keyId)
            .putLong("${keyAlias}_creation_date", creationDate)
            .putLong("${keyAlias}_last_used", creationDate)
            .putInt("${keyAlias}_rotation_count", rotationCount)
            .putBoolean("${keyAlias}_is_active", isActive)
            .apply()
    }

    /**
     * Mark key as inactive
     */
    private fun markKeyInactive(keyAlias: String) {
        encryptedPrefs.edit()
            .putBoolean("${keyAlias}_is_active", false)
            .apply()
    }

    /**
     * Get key rotation count
     */
    private fun getKeyRotationCount(keyAlias: String): Int {
        return encryptedPrefs.getInt("${keyAlias}_rotation_count", 0)
    }

    /**
     * Generate unique key ID
     */
    private fun generateKeyId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (Math.random() * 1000).toInt()
        return "kid-${timestamp}-${random}"
    }

    /**
     * Clean up old keys
     */
    suspend fun cleanupOldKeys(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var cleanedCount = 0
            val allKeys = encryptedPrefs.all.keys

            for (key in allKeys) {
                if (key.endsWith("_creation_date")) {
                    val keyAlias = key.removeSuffix("_creation_date")
                    
                    if (shouldRetireKey(keyAlias)) {
                        try {
                            // Delete key from keystore
                            keyStore.deleteEntry(keyAlias)
                            
                            // Remove metadata
                            encryptedPrefs.edit()
                                .remove("${keyAlias}_key_id")
                                .remove("${keyAlias}_creation_date")
                                .remove("${keyAlias}_last_used")
                                .remove("${keyAlias}_rotation_count")
                                .remove("${keyAlias}_is_active")
                                .apply()
                            
                            cleanedCount++
                            Timber.d("Cleaned up old key: $keyAlias")
                            
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to clean up key: $keyAlias")
                        }
                    }
                }
            }

            Timber.i("Cleaned up $cleanedCount old keys")
            Result.success(cleanedCount)

        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old keys")
            Result.failure(e)
        }
    }

    /**
     * Get key statistics
     */
    fun getKeyStats(): Map<String, Any> {
        val activeKeys = getActiveKeys()
        val totalKeys = activeKeys.size
        val keysNeedingRotation = activeKeys.count { needsKeyRotation(it.keyAlias) }
        val keysNeedingRetirement = activeKeys.count { shouldRetireKey(it.keyAlias) }

        return mapOf(
            "total_active_keys" to totalKeys,
            "keys_needing_rotation" to keysNeedingRotation,
            "keys_needing_retirement" to keysNeedingRetirement,
            "rotation_interval_days" to (KEY_ROTATION_INTERVAL / (24 * 60 * 60 * 1000)),
            "retention_interval_days" to (KEY_RETENTION_INTERVAL / (24 * 60 * 60 * 1000))
        )
    }

    /**
     * Verify key integrity
     */
    fun verifyKeyIntegrity(keyAlias: String): Boolean {
        return try {
            val key = keyStore.getKey(keyAlias, null)
            key != null
        } catch (e: Exception) {
            Timber.e(e, "Key integrity check failed for: $keyAlias")
            false
        }
    }
}
