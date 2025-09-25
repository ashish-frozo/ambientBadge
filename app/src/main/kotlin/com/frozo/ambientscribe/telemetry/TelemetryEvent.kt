package com.frozo.ambientscribe.telemetry

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import java.util.UUID

/**
 * Telemetry event data classes for PT-8 implementation
 * Maps to EVT-1 through EVT-5 as defined in PRD Section 9
 */

sealed class TelemetryEvent {
    abstract val encounterId: String
    abstract val timestamp: String
    abstract val eventType: String
    abstract val deviceTier: String
    abstract val clinicId: String?
    
    fun toJsonString(): String = Companion.moshi.adapter(TelemetryEvent::class.java).toJson(this)
    
    companion object {
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
}

/**
 * EVT-1: Encounter Start Event
 * Tracks when a new encounter session begins
 */
data class EncounterStartEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "encounter_start",
    override val deviceTier: String,
    override val clinicId: String?,
    val sessionId: String = UUID.randomUUID().toString(),
    val audioQuality: String? = null,
    val batteryLevel: Int? = null
) : TelemetryEvent()

/**
 * EVT-2: Transcription Complete Event
 * Tracks completion of audio transcription with quality metrics
 */
data class TranscriptionCompleteEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "transcription_complete",
    override val deviceTier: String,
    override val clinicId: String?,
    val werEstimate: Double,
    val processingTimeMs: Long,
    val modelVersion: String,
    val audioDurationMs: Long,
    val confidenceScore: Double? = null,
    val languageDetected: String? = null
) : TelemetryEvent()

/**
 * EVT-3: Review Complete Event
 * Tracks completion of SOAP/prescription review with edit metrics
 */
data class ReviewCompleteEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "review_complete",
    override val deviceTier: String,
    override val clinicId: String?,
    val editRatePercent: Double,
    val reviewDurationS: Long,
    val confidenceOverrides: Int,
    val totalEdits: Int,
    val prescriptionEdits: Int,
    val soapEdits: Int,
    val redFlagsResolved: Int = 0
) : TelemetryEvent()

/**
 * EVT-4: Export Success Event
 * Tracks successful PDF/JSON export with performance metrics
 */
data class ExportSuccessEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "export_success",
    override val deviceTier: String,
    override val clinicId: String?,
    val pdfSizeKb: Long,
    val exportDurationMs: Long,
    val batteryLevelPercent: Int,
    val storageUsedKb: Long? = null,
    val qrCodeGenerated: Boolean = true,
    val encryptionApplied: Boolean = true
) : TelemetryEvent()

/**
 * EVT-5: Thermal Event
 * Tracks thermal management events and CPU usage
 */
data class ThermalEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "thermal_event",
    override val deviceTier: String,
    override val clinicId: String?,
    val thermalState: String, // NORMAL, WARNING, SEVERE
    val mitigationAction: String, // THROTTLE, PAUSE, REDUCE_THREADS
    val cpuUsagePercent: Double,
    val temperature: Double? = null,
    val recoveryTimeMs: Long? = null
) : TelemetryEvent()

/**
 * Additional telemetry events for comprehensive tracking
 */

/**
 * Policy Toggle Event (ST-8.14)
 * Tracks when clinic policies are modified
 */
data class PolicyToggleEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "policy_toggle",
    override val deviceTier: String,
    override val clinicId: String?,
    val policyType: String, // BRAND_GENERIC, FORMULARY, etc.
    val actor: String, // doctor_id or admin_id
    val beforeValue: String,
    val afterValue: String,
    val reason: String? = null
) : TelemetryEvent()

/**
 * Bulk Edit Applied Event (ST-8.14)
 * Tracks when bulk edit operations are performed
 */
data class BulkEditAppliedEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "bulk_edit_applied",
    override val deviceTier: String,
    override val clinicId: String?,
    val actor: String,
    val editType: String, // FREQUENCY, DURATION, etc.
    val beforeValue: String,
    val afterValue: String,
    val affectedCount: Int
) : TelemetryEvent()

/**
 * Time Skew Event (ST-8.15)
 * Tracks when device-server time difference exceeds threshold
 */
data class TimeSkewEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "time_skew",
    override val deviceTier: String,
    override val clinicId: String?,
    val deviceTime: String,
    val serverTime: String,
    val skewSeconds: Long,
    val timeSource: String // SNTP, HTTPS, LOCAL
) : TelemetryEvent()

/**
 * Crash Free Session Event (ST-8.5)
 * Tracks session reliability metrics
 */
data class CrashFreeSessionEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "crash_free_session",
    override val deviceTier: String,
    override val clinicId: String?,
    val sessionDurationMs: Long,
    val crashCount: Int = 0,
    val anrCount: Int = 0,
    val recoveryActions: Int = 0
) : TelemetryEvent()

/**
 * Edit Cause Code Event (ST-8.10)
 * Tracks reasons for manual edits during review
 */
data class EditCauseCodeEvent(
    override val encounterId: String,
    override val timestamp: String,
    override val eventType: String = "edit_cause_code",
    override val deviceTier: String,
    override val clinicId: String?,
    val editType: String, // HEARD, AMBIGUOUS, UNSUPPORTED_FREQ, etc.
    val fieldName: String,
    val originalValue: String,
    val correctedValue: String,
    val confidenceScore: Double? = null
) : TelemetryEvent()

/**
 * Companion object for telemetry event utilities
 */
object TelemetryEventUtils {
    
    /**
     * Create a base telemetry event with common fields
     */
    fun createBaseEvent(
        encounterId: String,
        deviceTier: String,
        clinicId: String? = null
    ): Map<String, Any> = mapOf(
        "encounter_id" to encounterId,
        "timestamp" to Instant.now().toString(),
        "device_tier" to deviceTier,
        "clinic_id" to (clinicId ?: "")
    )
    
    /**
     * Validate that an event contains no PII
     */
    fun validateNoPII(event: TelemetryEvent): Boolean {
        val eventJson = event.toJsonString().lowercase()
        
        // Check for common PII patterns
        val piiPatterns = listOf(
            "patient", "name", "phone", "email", "address",
            "ssn", "mrn", "id_number", "birth", "age"
        )
        
        return piiPatterns.none { pattern -> eventJson.contains(pattern) }
    }
    
    /**
     * Get event schema version
     */
    fun getSchemaVersion(): String = "1.0"
}
