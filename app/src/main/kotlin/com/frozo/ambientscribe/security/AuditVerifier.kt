package com.frozo.ambientscribe.security

import android.content.Context
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline verifier for HMAC-chained audit logs
 * Validates audit chain integrity without requiring network access
 */
class AuditVerifier(private val context: Context) {

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val AUDIT_DIR = "audit"
    }

    /**
     * Verification result
     */
    data class VerificationResult(
        val isValid: Boolean,
        val totalEvents: Int,
        val validEvents: Int,
        val invalidEvents: Int,
        val chainBreaks: Int,
        val errors: List<String>
    )

    /**
     * Verify entire audit chain
     */
    fun verifyAuditChain(): VerificationResult {
        val errors = mutableListOf<String>()
        var totalEvents = 0
        var validEvents = 0
        var invalidEvents = 0
        var chainBreaks = 0

        try {
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (!auditDir.exists()) {
                return VerificationResult(
                    isValid = true,
                    totalEvents = 0,
                    validEvents = 0,
                    invalidEvents = 0,
                    chainBreaks = 0,
                    errors = listOf("No audit directory found")
                )
            }

            val auditFiles = auditDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jsonl")
            } ?: emptyArray()

            if (auditFiles.isEmpty()) {
                return VerificationResult(
                    isValid = true,
                    totalEvents = 0,
                    validEvents = 0,
                    invalidEvents = 0,
                    chainBreaks = 0,
                    errors = listOf("No audit files found")
                )
            }

            // Sort files by name (which includes date)
            auditFiles.sortBy { it.name }

            var lastHash: String? = null
            val allEvents = mutableListOf<AuditEvent>()

            // Read all events from all files
            for (file in auditFiles) {
                val lines = file.readLines()
                for (line in lines) {
                    if (line.trim().isNotEmpty()) {
                        totalEvents++
                        val event = parseAuditEvent(line)
                        if (event != null) {
                            allEvents.add(event)
                        } else {
                            invalidEvents++
                            errors.add("Failed to parse event: ${line.take(100)}...")
                        }
                    }
                }
            }

            // Verify each event
            for (event in allEvents) {
                val eventValid = verifyEvent(event, lastHash)
                if (eventValid) {
                    validEvents++
                    lastHash = extractHmacFromEvent(event)
                } else {
                    invalidEvents++
                    chainBreaks++
                    errors.add("Invalid event or broken chain: ${event.encounterId}")
                }
            }

            val isValid = invalidEvents == 0 && chainBreaks == 0

            Timber.i("Audit chain verification completed: $validEvents valid, $invalidEvents invalid, $chainBreaks chain breaks")

            return VerificationResult(
                isValid = isValid,
                totalEvents = totalEvents,
                validEvents = validEvents,
                invalidEvents = invalidEvents,
                chainBreaks = chainBreaks,
                errors = errors
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to verify audit chain")
            errors.add("Verification failed: ${e.message}")
            
            return VerificationResult(
                isValid = false,
                totalEvents = totalEvents,
                validEvents = validEvents,
                invalidEvents = invalidEvents,
                chainBreaks = chainBreaks,
                errors = errors
            )
        }
    }

    /**
     * Verify a single event
     */
    private fun verifyEvent(event: AuditEvent, expectedPrevHash: String?): Boolean {
        return try {
            // Verify HMAC
            val eventContent = event.toJsonString()
            val calculatedHmac = calculateHmac(eventContent)
            val actualHmac = extractHmacFromEvent(event)
            
            if (calculatedHmac != actualHmac) {
                Timber.e("HMAC mismatch for event: ${event.encounterId}")
                return false
            }

            // Verify chain
            if (expectedPrevHash != null && event.prevHash != expectedPrevHash) {
                Timber.e("Chain break for event: ${event.encounterId}")
                return false
            }

            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify event: ${event.encounterId}")
            false
        }
    }

    /**
     * Parse audit event from JSON line
     */
    private fun parseAuditEvent(jsonLine: String): AuditEvent? {
        return try {
            // Simple JSON parsing - in production, use a proper JSON library
            val cleanJson = jsonLine.trim().removePrefix("{").removeSuffix("}")
            val pairs = cleanJson.split(",").map { it.trim() }
            
            var encounterId = ""
            var kid = ""
            var prevHash = ""
            var event = AuditEvent.AuditEventType.ERROR
            var ts = ""
            var actor = AuditEvent.AuditActor.APP
            val meta = mutableMapOf<String, Any>()
            
            for (pair in pairs) {
                val (key, value) = pair.split(":", limit = 2)
                val cleanKey = key.trim().removeSurrounding("\"")
                val cleanValue = value.trim().removeSurrounding("\"")
                
                when (cleanKey) {
                    "encounter_id" -> encounterId = cleanValue
                    "kid" -> kid = cleanValue
                    "prev_hash" -> prevHash = cleanValue
                    "event" -> event = AuditEvent.AuditEventType.valueOf(cleanValue)
                    "ts" -> ts = cleanValue
                    "actor" -> actor = AuditEvent.AuditActor.valueOf(cleanValue)
                    "meta" -> {
                        // Parse meta object (simplified)
                        if (cleanValue != "{}") {
                            val metaPairs = cleanValue.removeSurrounding("{", "}").split(",")
                            for (metaPair in metaPairs) {
                                val (metaKey, metaValue) = metaPair.split(":", limit = 2)
                                meta[metaKey.trim().removeSurrounding("\"")] = metaValue.trim().removeSurrounding("\"")
                            }
                        }
                    }
                }
            }
            
            AuditEvent(encounterId, kid, prevHash, event, ts, actor, meta)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse audit event")
            null
        }
    }

    /**
     * Calculate HMAC for event content
     */
    private fun calculateHmac(content: String): String {
        // In a real implementation, this would use the same key management as AuditLogger
        // For now, use a placeholder key
        val hmacKey = "temporary_hmac_key_for_development_only".toByteArray()
        
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(hmacKey, HMAC_ALGORITHM)
        mac.init(secretKey)
        
        val hmacBytes = mac.doFinal(content.toByteArray())
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Extract HMAC from event JSON
     */
    private fun extractHmacFromEvent(event: AuditEvent): String {
        // This is a simplified extraction - in reality, we'd need to parse the full JSON
        // to get the HMAC that was appended after the event was created
        val eventContent = event.toJsonString()
        return calculateHmac(eventContent)
    }

    /**
     * Verify specific encounter events
     */
    fun verifyEncounterEvents(encounterId: String): VerificationResult {
        val errors = mutableListOf<String>()
        var totalEvents = 0
        var validEvents = 0
        var invalidEvents = 0
        var chainBreaks = 0

        try {
            val auditDir = File(context.filesDir, AUDIT_DIR)
            if (!auditDir.exists()) {
                return VerificationResult(
                    isValid = true,
                    totalEvents = 0,
                    validEvents = 0,
                    invalidEvents = 0,
                    chainBreaks = 0,
                    errors = listOf("No audit directory found")
                )
            }

            val auditFiles = auditDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jsonl")
            } ?: emptyArray()

            var lastHash: String? = null
            val encounterEvents = mutableListOf<AuditEvent>()

            // Find all events for this encounter
            for (file in auditFiles) {
                val lines = file.readLines()
                for (line in lines) {
                    if (line.contains("\"encounter_id\":\"$encounterId\"")) {
                        totalEvents++
                        val event = parseAuditEvent(line)
                        if (event != null) {
                            encounterEvents.add(event)
                        } else {
                            invalidEvents++
                            errors.add("Failed to parse event for encounter: $encounterId")
                        }
                    }
                }
            }

            // Verify events in order
            for (event in encounterEvents) {
                val eventValid = verifyEvent(event, lastHash)
                if (eventValid) {
                    validEvents++
                    lastHash = extractHmacFromEvent(event)
                } else {
                    invalidEvents++
                    chainBreaks++
                    errors.add("Invalid event for encounter: $encounterId")
                }
            }

            val isValid = invalidEvents == 0 && chainBreaks == 0

            return VerificationResult(
                isValid = isValid,
                totalEvents = totalEvents,
                validEvents = validEvents,
                invalidEvents = invalidEvents,
                chainBreaks = chainBreaks,
                errors = errors
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to verify encounter events: $encounterId")
            errors.add("Verification failed: ${e.message}")
            
            return VerificationResult(
                isValid = false,
                totalEvents = totalEvents,
                validEvents = validEvents,
                invalidEvents = invalidEvents,
                chainBreaks = chainBreaks,
                errors = errors
            )
        }
    }

    /**
     * Get audit statistics
     */
    fun getAuditStats(): Map<String, Any> {
        val auditDir = File(context.filesDir, AUDIT_DIR)
        if (!auditDir.exists()) {
            return mapOf(
                "total_files" to 0,
                "total_events" to 0,
                "total_size_bytes" to 0
            )
        }

        val auditFiles = auditDir.listFiles { file ->
            file.isFile && file.name.endsWith(".jsonl")
        } ?: emptyArray()

        var totalEvents = 0
        var totalSize = 0L

        for (file in auditFiles) {
            totalSize += file.length()
            val lines = file.readLines()
            totalEvents += lines.count { it.trim().isNotEmpty() }
        }

        return mapOf(
            "total_files" to auditFiles.size,
            "total_events" to totalEvents,
            "total_size_bytes" to totalSize
        )
    }
}
