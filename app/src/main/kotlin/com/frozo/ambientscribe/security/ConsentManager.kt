package com.frozo.ambientscribe.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Manages consent for DPDP compliance
 * Tracks CONSENT_ON/OFF events and provides consent status
 */
class ConsentManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "consent_manager"
        private const val KEY_CONSENT_STATUS = "consent_status"
        private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val KEY_ENCOUNTER_CONSENT = "encounter_consent_"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        
        // Consent versions for tracking changes
        private const val CURRENT_CONSENT_VERSION = "1.0"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auditLogger = AuditLogger(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * Consent status for an encounter
     */
    enum class ConsentStatus {
        NOT_SET,
        GIVEN,
        WITHDRAWN,
        EXPIRED
    }

    /**
     * Consent event data
     */
    data class ConsentEvent(
        val encounterId: String,
        val status: ConsentStatus,
        val timestamp: String,
        val version: String,
        val actor: AuditEvent.AuditActor,
        val meta: Map<String, Any> = emptyMap()
    )

    /**
     * Check if consent is given for an encounter
     */
    suspend fun hasConsent(encounterId: String): Boolean = withContext(Dispatchers.IO) {
        val status = getConsentStatus(encounterId)
        status == ConsentStatus.GIVEN
    }

    /**
     * Get consent status for an encounter
     */
    fun getConsentStatus(encounterId: String): ConsentStatus {
        val statusString = prefs.getString("${KEY_ENCOUNTER_CONSENT}$encounterId", null)
        return when (statusString) {
            "GIVEN" -> ConsentStatus.GIVEN
            "WITHDRAWN" -> ConsentStatus.WITHDRAWN
            "EXPIRED" -> ConsentStatus.EXPIRED
            else -> ConsentStatus.NOT_SET
        }
    }

    /**
     * Give consent for an encounter
     */
    suspend fun giveConsent(
        encounterId: String,
        actor: AuditEvent.AuditActor = AuditEvent.AuditActor.DOCTOR,
        meta: Map<String, Any> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            
            // Store consent status
            prefs.edit()
                .putString("${KEY_ENCOUNTER_CONSENT}$encounterId", "GIVEN")
                .putLong("${KEY_ENCOUNTER_CONSENT}${encounterId}_timestamp", System.currentTimeMillis())
                .putString("${KEY_ENCOUNTER_CONSENT}${encounterId}_version", CURRENT_CONSENT_VERSION)
                .apply()

            // Log audit event
            val consentEvent = ConsentEvent(
                encounterId = encounterId,
                status = ConsentStatus.GIVEN,
                timestamp = timestamp,
                version = CURRENT_CONSENT_VERSION,
                actor = actor,
                meta = meta
            )

            auditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CONSENT_ON,
                actor = actor,
                meta = mapOf(
                    "consent_version" to CURRENT_CONSENT_VERSION,
                    "timestamp" to timestamp
                ) + meta
            )

            Timber.i("Consent given for encounter: $encounterId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to give consent for encounter: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Withdraw consent for an encounter
     */
    suspend fun withdrawConsent(
        encounterId: String,
        actor: AuditEvent.AuditActor = AuditEvent.AuditActor.DOCTOR,
        meta: Map<String, Any> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            
            // Store consent status
            prefs.edit()
                .putString("${KEY_ENCOUNTER_CONSENT}$encounterId", "WITHDRAWN")
                .putLong("${KEY_ENCOUNTER_CONSENT}${encounterId}_timestamp", System.currentTimeMillis())
                .apply()

            // Log audit event
            auditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CONSENT_OFF,
                actor = actor,
                meta = mapOf(
                    "consent_version" to CURRENT_CONSENT_VERSION,
                    "timestamp" to timestamp
                ) + meta
            )

            Timber.i("Consent withdrawn for encounter: $encounterId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to withdraw consent for encounter: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Check if consent has expired (24 hours)
     */
    fun isConsentExpired(encounterId: String): Boolean {
        val consentTimestamp = prefs.getLong("${KEY_ENCOUNTER_CONSENT}${encounterId}_timestamp", 0)
        if (consentTimestamp == 0L) return true
        
        val now = System.currentTimeMillis()
        val consentAge = now - consentTimestamp
        val maxAge = 24L * 60 * 60 * 1000 // 24 hours
        
        return consentAge > maxAge
    }

    /**
     * Mark consent as expired
     */
    suspend fun markConsentExpired(encounterId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            prefs.edit()
                .putString("${KEY_ENCOUNTER_CONSENT}$encounterId", "EXPIRED")
                .apply()

            auditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CONSENT_OFF,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf("reason" to "expired")
            )

            Timber.i("Consent marked as expired for encounter: $encounterId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to mark consent as expired for encounter: $encounterId")
            Result.failure(e)
        }
    }

    /**
     * Get consent history for an encounter
     */
    fun getConsentHistory(encounterId: String): List<ConsentEvent> {
        // This would typically query audit logs
        // For now, return current status
        val status = getConsentStatus(encounterId)
        val timestamp = prefs.getString("${KEY_ENCOUNTER_CONSENT}${encounterId}_timestamp", null) ?: ""
        val version = prefs.getString("${KEY_ENCOUNTER_CONSENT}${encounterId}_version", CURRENT_CONSENT_VERSION) ?: CURRENT_CONSENT_VERSION
        
        return listOf(
            ConsentEvent(
                encounterId = encounterId,
                status = status,
                timestamp = timestamp,
                version = version,
                actor = AuditEvent.AuditActor.DOCTOR
            )
        )
    }

    /**
     * Clean up expired consent records
     */
    suspend fun cleanupExpiredConsent(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val allKeys = prefs.all.keys
            var cleanedCount = 0
            
            for (key in allKeys) {
                if (key.startsWith(KEY_ENCOUNTER_CONSENT) && key.endsWith("_timestamp")) {
                    val encounterId = key.removePrefix(KEY_ENCOUNTER_CONSENT).removeSuffix("_timestamp")
                    
                    if (isConsentExpired(encounterId)) {
                        // Remove all related keys
                        prefs.edit()
                            .remove("${KEY_ENCOUNTER_CONSENT}$encounterId")
                            .remove("${KEY_ENCOUNTER_CONSENT}${encounterId}_timestamp")
                            .remove("${KEY_ENCOUNTER_CONSENT}${encounterId}_version")
                            .apply()
                        
                        cleanedCount++
                    }
                }
            }
            
            Timber.i("Cleaned up $cleanedCount expired consent records")
            Result.success(cleanedCount)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup expired consent")
            Result.failure(e)
        }
    }

    /**
     * Get consent statistics
     */
    fun getConsentStats(): Map<String, Int> {
        val allKeys = prefs.all.keys
        var given = 0
        var withdrawn = 0
        var expired = 0
        var notSet = 0
        
        for (key in allKeys) {
            if (key.startsWith(KEY_ENCOUNTER_CONSENT) && !key.contains("_timestamp") && !key.contains("_version")) {
                val encounterId = key.removePrefix(KEY_ENCOUNTER_CONSENT)
                val status = getConsentStatus(encounterId)
                
                when (status) {
                    ConsentStatus.GIVEN -> given++
                    ConsentStatus.WITHDRAWN -> withdrawn++
                    ConsentStatus.EXPIRED -> expired++
                    ConsentStatus.NOT_SET -> notSet++
                }
            }
        }
        
        return mapOf(
            "given" to given,
            "withdrawn" to withdrawn,
            "expired" to expired,
            "not_set" to notSet
        )
    }
}
