package com.frozo.ambientscribe.security

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 * Manages HMAC keys for audit logging with rotation support
 */
class HMACKeyManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "audit_hmac_keys"
        private const val KEY_ROTATION_INTERVAL = 90L * 24 * 60 * 60 * 1000 // 90 days
        private const val KEY_SIZE = 32 // 256 bits
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    /**
     * Get current HMAC key, creating/rotating as needed
     */
    fun getCurrentKey(): SecretKeySpec {
        val currentKid = getCurrentKeyId()
        val keyData = getKeyData(currentKid)
        
        return if (keyData != null) {
            SecretKeySpec(keyData, HMAC_ALGORITHM)
        } else {
            // Generate new key
            val newKeyData = generateNewKey()
            storeKeyData(currentKid, newKeyData)
            SecretKeySpec(newKeyData, HMAC_ALGORITHM)
        }
    }

    /**
     * Get current key ID based on rotation schedule
     */
    private fun getCurrentKeyId(): String {
        val now = System.currentTimeMillis()
        val quarterStart = getQuarterStart(now)
        return "kid-${getQuarterString(quarterStart)}"
    }

    /**
     * Generate new HMAC key
     */
    private fun generateNewKey(): ByteArray {
        val keyData = ByteArray(KEY_SIZE)
        secureRandom.nextBytes(keyData)
        return keyData
    }

    /**
     * Store key data securely
     */
    private fun storeKeyData(keyId: String, keyData: ByteArray) {
        val encoded = android.util.Base64.encodeToString(keyData, android.util.Base64.NO_WRAP)
        prefs.edit()
            .putString("key_$keyId", encoded)
            .putLong("key_${keyId}_created", System.currentTimeMillis())
            .apply()
        
        Timber.d("Stored new HMAC key: $keyId")
    }

    /**
     * Get key data for given key ID
     */
    private fun getKeyData(keyId: String): ByteArray? {
        val encoded = prefs.getString("key_$keyId", null) ?: return null
        return try {
            android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode key data for $keyId")
            null
        }
    }

    /**
     * Get quarter start timestamp for given time
     */
    private fun getQuarterStart(timestamp: Long): Long {
        val date = java.util.Date(timestamp)
        val calendar = java.util.Calendar.getInstance()
        calendar.time = date
        
        // Set to start of quarter
        val quarter = (calendar.get(java.util.Calendar.MONTH) / 3)
        calendar.set(java.util.Calendar.MONTH, quarter * 3)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        
        return calendar.timeInMillis
    }

    /**
     * Get quarter string (e.g., "2025Q3")
     */
    private fun getQuarterString(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val calendar = java.util.Calendar.getInstance()
        calendar.time = date
        
        val year = calendar.get(java.util.Calendar.YEAR)
        val quarter = (calendar.get(java.util.Calendar.MONTH) / 3) + 1
        
        return "${year}Q$quarter"
    }

    /**
     * Check if key needs rotation
     */
    fun needsRotation(keyId: String): Boolean {
        val created = prefs.getLong("key_${keyId}_created", 0)
        if (created == 0L) return true
        
        return System.currentTimeMillis() - created >= KEY_ROTATION_INTERVAL
    }

    /**
     * Get all available key IDs
     */
    fun getAvailableKeyIds(): List<String> {
        val keys = mutableListOf<String>()
        val allKeys = prefs.all.keys
        
        for (key in allKeys) {
            if (key.startsWith("key_") && key.endsWith("_created")) {
                val keyId = key.removePrefix("key_").removeSuffix("_created")
                if (keyId != "created") { // Skip the old format
                    keys.add(keyId)
                }
            }
        }
        
        return keys.sorted()
    }

    /**
     * Clean up old keys (keep last 2 quarters)
     */
    fun cleanupOldKeys() {
        val availableKeys = getAvailableKeyIds()
        val currentKid = getCurrentKeyId()
        
        // Keep current and previous quarter keys
        val keysToKeep = setOf(currentKid, getPreviousQuarterKeyId())
        
        for (keyId in availableKeys) {
            if (keyId !in keysToKeep) {
                prefs.edit()
                    .remove("key_$keyId")
                    .remove("key_${keyId}_created")
                    .apply()
                
                Timber.d("Cleaned up old HMAC key: $keyId")
            }
        }
    }

    /**
     * Get previous quarter key ID
     */
    private fun getPreviousQuarterKeyId(): String {
        val now = System.currentTimeMillis()
        val currentQuarterStart = getQuarterStart(now)
        val previousQuarterStart = currentQuarterStart - (90L * 24 * 60 * 60 * 1000) // 90 days ago
        return "kid-${getQuarterString(previousQuarterStart)}"
    }
}
