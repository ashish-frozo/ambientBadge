package com.frozo.ambientscribe.security

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Handles patient ID hashing with clinic-specific salt for privacy protection
 * Implements SHA256 hashing with salt rotation
 */
class PatientIdHasher(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "patient_id_hasher"
        private const val KEY_CLINIC_SALT = "clinic_salt"
        private const val KEY_SALT_VERSION = "salt_version"
        private const val SALT_SIZE = 32 // 256 bits
        private const val HASH_ALGORITHM = "SHA-256"
        private const val SALT_ROTATION_INTERVAL = 180L * 24 * 60 * 60 * 1000 // 180 days
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    /**
     * Patient ID types as defined in PRD
     */
    enum class PatientIdType {
        PHONE,
        MRN,
        OTHER
    }

    /**
     * Hashed patient reference
     */
    data class HashedPatientRef(
        val hash: String,
        val saltVersion: String,
        val idType: PatientIdType,
        val originalLength: Int
    )

    /**
     * Hash a patient identifier with clinic-specific salt
     */
    fun hashPatientId(
        patientId: String,
        idType: PatientIdType,
        clinicId: String
    ): HashedPatientRef {
        try {
            // Normalize the patient ID based on type
            val normalizedId = normalizePatientId(patientId, idType)
            
            // Get or generate clinic salt
            val salt = getOrGenerateClinicSalt(clinicId)
            val saltVersion = getCurrentSaltVersion()
            
            // Create hash input: salt + clinicId + normalizedId
            val hashInput = salt + clinicId.toByteArray() + normalizedId.toByteArray()
            
            // Generate SHA256 hash
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            val hashBytes = digest.digest(hashInput)
            
            // Convert to hex string
            val hashString = hashBytes.joinToString("") { "%02x".format(it) }
            
            // Create reference in format: hash:v1:salt32:SHA256
            val reference = "hash:$saltVersion:${salt.size}:$HASH_ALGORITHM:$hashString"
            
            Timber.d("Hashed patient ID: ${idType.name} -> $reference")
            
            return HashedPatientRef(
                hash = reference,
                saltVersion = saltVersion,
                idType = idType,
                originalLength = patientId.length
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to hash patient ID")
            throw e
        }
    }

    /**
     * Normalize patient ID based on type
     */
    private fun normalizePatientId(patientId: String, idType: PatientIdType): String {
        return when (idType) {
            PatientIdType.PHONE -> normalizePhoneNumber(patientId)
            PatientIdType.MRN -> normalizeMRN(patientId)
            PatientIdType.OTHER -> normalizeOther(patientId)
        }
    }

    /**
     * Normalize phone number to E.164 format
     */
    private fun normalizePhoneNumber(phone: String): String {
        // Remove all non-digit characters
        val digits = phone.replace(Regex("[^0-9]"), "")
        
        // Add country code if missing (assume India +91)
        return when {
            digits.startsWith("91") && digits.length == 12 -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.startsWith("+") -> phone
            else -> "+$digits"
        }
    }

    /**
     * Normalize MRN (Medical Record Number)
     */
    private fun normalizeMRN(mrn: String): String {
        // Remove whitespace and convert to uppercase
        return mrn.trim().uppercase()
    }

    /**
     * Normalize other identifier types
     */
    private fun normalizeOther(id: String): String {
        // Trim whitespace
        return id.trim()
    }

    /**
     * Get or generate clinic-specific salt
     */
    private fun getOrGenerateClinicSalt(clinicId: String): ByteArray {
        val saltKey = "${KEY_CLINIC_SALT}_$clinicId"
        val encodedSalt = prefs.getString(saltKey, null)
        
        return if (encodedSalt != null) {
            try {
                android.util.Base64.decode(encodedSalt, android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                Timber.e(e, "Failed to decode salt for clinic: $clinicId")
                generateNewSalt(clinicId)
            }
        } else {
            generateNewSalt(clinicId)
        }
    }

    /**
     * Generate new salt for clinic
     */
    private fun generateNewSalt(clinicId: String): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        secureRandom.nextBytes(salt)
        
        val encodedSalt = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
        val saltVersion = getCurrentSaltVersion()
        
        prefs.edit()
            .putString("${KEY_CLINIC_SALT}_$clinicId", encodedSalt)
            .putString("${KEY_CLINIC_SALT}_${clinicId}_version", saltVersion)
            .putLong("${KEY_CLINIC_SALT}_${clinicId}_created", System.currentTimeMillis())
            .apply()
        
        Timber.d("Generated new salt for clinic: $clinicId")
        return salt
    }

    /**
     * Get current salt version
     */
    private fun getCurrentSaltVersion(): String {
        val now = System.currentTimeMillis()
        val quarterStart = getQuarterStart(now)
        return "v${quarterStart / (90L * 24 * 60 * 60 * 1000)}"
    }

    /**
     * Get quarter start timestamp
     */
    private fun getQuarterStart(timestamp: Long): Long {
        val date = java.util.Date(timestamp)
        val calendar = java.util.Calendar.getInstance()
        calendar.time = date
        
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
     * Verify a patient ID against a hash
     */
    fun verifyPatientId(
        patientId: String,
        idType: PatientIdType,
        clinicId: String,
        hashedRef: String
    ): Boolean {
        try {
            val newHash = hashPatientId(patientId, idType, clinicId)
            return newHash.hash == hashedRef
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify patient ID")
            return false
        }
    }

    /**
     * Check if salt needs rotation
     */
    fun needsSaltRotation(clinicId: String): Boolean {
        val created = prefs.getLong("${KEY_CLINIC_SALT}_${clinicId}_created", 0)
        if (created == 0L) return true
        
        return System.currentTimeMillis() - created >= SALT_ROTATION_INTERVAL
    }

    /**
     * Rotate salt for clinic
     */
    fun rotateSalt(clinicId: String): ByteArray {
        Timber.d("Rotating salt for clinic: $clinicId")
        return generateNewSalt(clinicId)
    }

    /**
     * Get salt statistics
     */
    fun getSaltStats(): Map<String, Any> {
        val allKeys = prefs.all.keys
        var totalSalts = 0
        var expiredSalts = 0
        val clinics = mutableSetOf<String>()
        
        for (key in allKeys) {
            if (key.startsWith(KEY_CLINIC_SALT) && key.endsWith("_created")) {
                val clinicId = key.removePrefix("${KEY_CLINIC_SALT}_").removeSuffix("_created")
                clinics.add(clinicId)
                totalSalts++
                
                val created = prefs.getLong(key, 0)
                if (System.currentTimeMillis() - created >= SALT_ROTATION_INTERVAL) {
                    expiredSalts++
                }
            }
        }
        
        return mapOf(
            "total_salts" to totalSalts,
            "expired_salts" to expiredSalts,
            "active_clinics" to clinics.size,
            "clinics" to clinics.toList()
        )
    }

    /**
     * Clean up old salts (keep last 2 versions)
     */
    fun cleanupOldSalts() {
        val allKeys = prefs.all.keys
        val currentVersion = getCurrentSaltVersion()
        
        for (key in allKeys) {
            if (key.startsWith(KEY_CLINIC_SALT) && key.endsWith("_version")) {
                val clinicId = key.removePrefix("${KEY_CLINIC_SALT}_").removeSuffix("_version")
                val version = prefs.getString(key, currentVersion) ?: currentVersion
                
                if (version != currentVersion) {
                    // Remove old salt data
                    prefs.edit()
                        .remove("${KEY_CLINIC_SALT}_$clinicId")
                        .remove("${KEY_CLINIC_SALT}_${clinicId}_version")
                        .remove("${KEY_CLINIC_SALT}_${clinicId}_created")
                        .apply()
                    
                    Timber.d("Cleaned up old salt for clinic: $clinicId")
                }
            }
        }
    }
}
