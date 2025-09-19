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
 * Used to track security-sensitive operations like audio data purging.
 */
class AuditLogger(private val context: Context) {

    companion object {
        private const val AUDIT_DIR = "audit"
        private const val AUDIT_FILE_PREFIX = "audit_"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        
        // Event types
        const val EVENT_PURGE_BUFFER = "PURGE_BUFFER"
        const val EVENT_PURGE_30S = "PURGE_30S"
        const val EVENT_SESSION_END = "SESSION_END"
        const val EVENT_CONSENT_ON = "CONSENT_ON"
        const val EVENT_CONSENT_OFF = "CONSENT_OFF"
    }

    // In-memory previous hash for HMAC chaining
    private var previousHash: String? = null
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)
    
    // Temporary key for HMAC (in production, this would be securely stored)
    private val hmacKey = "temporary_hmac_key_for_development_only".toByteArray()
    
    /**
     * Log an audit event
     */
    suspend fun logEvent(
        eventType: String,
        details: Map<String, Any> = emptyMap(),
        sessionId: String = UUID.randomUUID().toString()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val formattedDate = dateFormat.format(Date(timestamp))
            val eventId = UUID.randomUUID().toString()
            
            // Create audit event JSON
            val eventJson = JSONObject().apply {
                put("event_id", eventId)
                put("event_type", eventType)
                put("timestamp", timestamp)
                put("formatted_timestamp", formattedDate)
                put("session_id", sessionId)
                
                // Add boot ID and monotonic timestamp for wall-clock jump detection
                put("boot_id", getBootId())
                put("monotonic_timestamp", getMonotonicTimestamp())
                
                // Add all details
                val detailsJson = JSONObject()
                details.forEach { (key, value) ->
                    detailsJson.put(key, value.toString())
                }
                put("details", detailsJson)
                
                // Add previous hash for HMAC chaining
                put("prev_hash", previousHash ?: "null")
                
                // Generate HMAC for this event
                val eventContent = toString()
                val hmac = calculateHmac(eventContent)
                put("hmac", hmac)
            }
            
            // Save the current HMAC as previous hash for next event
            previousHash = eventJson.getString("hmac")
            
            // Write to audit file
            writeAuditEvent(eventJson.toString())
            
            Timber.i("Audit event logged: $eventType")
            Result.success(eventId)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to log audit event")
            Result.failure(e)
        }
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
     * Calculate HMAC for event content
     */
    private fun calculateHmac(content: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(hmacKey, HMAC_ALGORITHM)
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
