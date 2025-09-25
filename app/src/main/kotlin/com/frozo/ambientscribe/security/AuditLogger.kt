package com.frozo.ambientscribe.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Secure audit logging system with HMAC-chaining for tamper-evident logs.
 * Implements AuditEvent v1.0 schema with proper key management and chaining.
 */
class AuditLogger(private val context: Context) {

    companion object {
        private const val AUDIT_DIR = "audit"
        private const val AUDIT_FILE_PREFIX = "audit_"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private const val KEY_ROTATION_INTERVAL = 90L * 24 * 60 * 60 * 1000 // 90 days
        
        // Current key ID (rotates every 90 days)
        private val currentKid = "kid-2025Q3"
        
        // Event type constants
        const val EVENT_PURGE_BUFFER = "purge_buffer"
        const val EVENT_PURGE_30S = "purge_30s"
        const val EVENT_SESSION_END = "session_end"
        const val EVENT_CONSENT_ON = "consent_on"
        const val EVENT_CONSENT_OFF = "consent_off"
    }

    // In-memory previous hash for HMAC chaining
    private var previousHash: String? = null
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)
    
    // HMAC key management with rotation
    private val hmacKeyManager = HMACKeyManager(context)
    
    /**
     * Log an audit event using AuditEvent v1.0 schema
     */
    suspend fun logEvent(
        encounterId: String,
        eventType: AuditEvent.AuditEventType,
        actor: AuditEvent.AuditActor = AuditEvent.AuditActor.APP,
        meta: Map<String, Any> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val formattedDate = dateFormat.format(Date(timestamp))
            
            // Create AuditEvent v1.0
            val auditEvent = AuditEvent(
                encounterId = encounterId,
                kid = currentKid,
                prevHash = previousHash ?: "null",
                event = eventType,
                ts = formattedDate,
                actor = actor,
                meta = meta
            )
            
            // Generate HMAC for this event
            val eventContent = auditEvent.toJsonString()
            val hmac = calculateHmac(eventContent)
            
            // Create final event with HMAC
            val finalEvent = eventContent.removeSuffix("}") + ",\"hmac\":\"$hmac\"}"
            
            // Save the current HMAC as previous hash for next event
            previousHash = hmac
            
            // Write to audit file
            writeAuditEvent(finalEvent)
            
            Timber.i("Audit event logged: ${eventType.name} for encounter $encounterId")
            Result.success(encounterId)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to log audit event")
            Result.failure(e)
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    suspend fun logEvent(
        eventType: String,
        details: Map<String, Any> = emptyMap(),
        sessionId: String = UUID.randomUUID().toString()
    ): Result<String> = withContext(Dispatchers.IO) {
        val auditEventType = when (eventType) {
            EVENT_PURGE_BUFFER -> AuditEvent.AuditEventType.PURGE_BUFFER
            EVENT_PURGE_30S -> AuditEvent.AuditEventType.PURGE_30S
            EVENT_SESSION_END -> AuditEvent.AuditEventType.SESSION_END
            EVENT_CONSENT_ON -> AuditEvent.AuditEventType.CONSENT_ON
            EVENT_CONSENT_OFF -> AuditEvent.AuditEventType.CONSENT_OFF
            else -> AuditEvent.AuditEventType.ERROR
        }
        
        logEvent(sessionId, auditEventType, AuditEvent.AuditActor.APP, details)
    }
    
    /**
     * Write audit event to file
     */
    private fun writeAuditEvent(eventJson: String) {
        val auditDir = File(context.filesDir, AUDIT_DIR).apply {
            if (!exists()) mkdirs()
        }
        
        // Use current date for file naming
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val auditFile = File(auditDir, "${AUDIT_FILE_PREFIX}${today}.jsonl")
        
        // Append event to file
        auditFile.appendText("$eventJson\n")
        
        // Force sync to disk for critical events
        auditFile.outputStream().fd.sync()
    }
    
    /**
     * Calculate HMAC for event content using current key
     */
    private fun calculateHmac(content: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = hmacKeyManager.getCurrentKey()
        mac.init(secretKey)
        
        val hmacBytes = mac.doFinal(content.toByteArray())
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get boot ID for tracking across reboots
     */
    private fun getBootId(): String {
        return try {
            File("/proc/sys/kernel/random/boot_id").readText().trim()
        } catch (e: Exception) {
            UUID.randomUUID().toString() // Fallback if boot_id not available
        }
    }
    
    /**
     * Get monotonic timestamp for wall-clock jump detection
     */
    private fun getMonotonicTimestamp(): Long {
        return System.nanoTime() / 1_000_000 // Convert to milliseconds
    }
    
    /**
     * Verify the integrity of the audit log chain
     */
    suspend fun verifyAuditChain(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (!auditDir.exists()) {
                return@withContext Result.success(true) // No audit logs yet
            }
            
            val auditFiles = auditDir.listFiles { file -> 
                file.name.startsWith(AUDIT_FILE_PREFIX) && file.name.endsWith(".jsonl") 
            }
            
            if (auditFiles.isNullOrEmpty()) {
                return@withContext Result.success(true) // No audit logs yet
            }
            
            // Sort files by name (which includes date)
            auditFiles.sortBy { it.name }
            
            var lastHash: String? = null
            var isValid = true
            
            // Check each file in sequence
            for (file in auditFiles) {
                val lines = file.readLines()
                
                for (line in lines) {
                    val event = JSONObject(line)
                    val prevHash = event.optString("prev_hash", "null")
                    val hmac = event.getString("hmac")
                    
                    // Remove HMAC before verification
                    val eventWithoutHmac = JSONObject(line)
                    eventWithoutHmac.remove("hmac")
                    
                    // Verify HMAC
                    val calculatedHmac = calculateHmac(eventWithoutHmac.toString())
                    if (calculatedHmac != hmac) {
                        Timber.e("HMAC mismatch in audit log: ${event.optString("event_id")}")
                        isValid = false
                        break
                    }
                    
                    // Verify chain
                    if (lastHash != null && prevHash != lastHash) {
                        Timber.e("Broken audit chain: ${event.optString("event_id")}")
                        isValid = false
                        break
                    }
                    
                    lastHash = hmac
                }
                
                if (!isValid) break
            }
            
            Result.success(isValid)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify audit chain")
            Result.failure(e)
        }
    }
}
