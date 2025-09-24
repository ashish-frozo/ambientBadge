package com.frozo.ambientscribe.security

import java.util.Date

/**
 * AuditEvent v1.0 schema as defined in PRD
 * Represents a single audit event with HMAC chaining for tamper detection
 */
data class AuditEvent(
    val encounterId: String,
    val kid: String, // Key ID for HMAC verification
    val prevHash: String, // Previous event hash for chaining
    val event: AuditEventType,
    val ts: String, // ISO8601 timestamp
    val actor: AuditActor,
    val meta: Map<String, Any> = emptyMap()
) {
    /**
     * Event types as defined in PRD
     */
    enum class AuditEventType {
        CONSENT_ON,
        CONSENT_OFF,
        EXPORT,
        ERROR,
        PURGE_BUFFER,
        PURGE_30S,
        SESSION_END,
        ABANDON_PURGE,
        POLICY_TOGGLE,
        BULK_EDIT_APPLIED,
        CANCELLED_COUNT,
        TIME_SOURCE,
        NET_EGRESS_IP_LIT,
        NET_EGRESS_BLOCKED
    }
    
    /**
     * Actor types as defined in PRD
     */
    enum class AuditActor {
        APP,
        DOCTOR,
        ADMIN
    }
    
    /**
     * Convert to JSON string for logging
     */
    fun toJsonString(): String {
        val json = StringBuilder()
        json.append("{")
        json.append("\"encounter_id\":\"$encounterId\",")
        json.append("\"kid\":\"$kid\",")
        json.append("\"prev_hash\":\"$prevHash\",")
        json.append("\"event\":\"${event.name}\",")
        json.append("\"ts\":\"$ts\",")
        json.append("\"actor\":\"${actor.name}\"")
        
        if (meta.isNotEmpty()) {
            json.append(",\"meta\":{")
            val metaEntries = meta.entries.joinToString(",") { 
                "\"${it.key}\":\"${it.value}\"" 
            }
            json.append(metaEntries)
            json.append("}")
        }
        
        json.append("}")
        return json.toString()
    }
    
    companion object {
        /**
         * Create AuditEvent from JSON string
         */
        fun fromJsonString(json: String): AuditEvent? {
            return try {
                // Simple JSON parsing - in production, use a proper JSON library
                val cleanJson = json.trim().removePrefix("{").removeSuffix("}")
                val pairs = cleanJson.split(",").map { it.trim() }
                
                var encounterId = ""
                var kid = ""
                var prevHash = ""
                var event = AuditEventType.ERROR
                var ts = ""
                var actor = AuditActor.APP
                val meta = mutableMapOf<String, Any>()
                
                for (pair in pairs) {
                    val (key, value) = pair.split(":", limit = 2)
                    val cleanKey = key.trim().removeSurrounding("\"")
                    val cleanValue = value.trim().removeSurrounding("\"")
                    
                    when (cleanKey) {
                        "encounter_id" -> encounterId = cleanValue
                        "kid" -> kid = cleanValue
                        "prev_hash" -> prevHash = cleanValue
                        "event" -> event = AuditEventType.valueOf(cleanValue)
                        "ts" -> ts = cleanValue
                        "actor" -> actor = AuditActor.valueOf(cleanValue)
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
                null
            }
        }
    }
}
