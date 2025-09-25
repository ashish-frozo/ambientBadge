package com.frozo.ambientscribe.telemetry

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Telemetry Manager for PT-8 implementation
 * Handles collection, storage, and reporting of telemetry events
 */
class TelemetryManager private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var INSTANCE: TelemetryManager? = null
        
        fun getInstance(context: Context): TelemetryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelemetryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val PREFS_NAME = "telemetry_prefs"
        private const val KEY_PILOT_MODE_ENABLED = "pilot_mode_enabled"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_EVENT_COUNT = "event_count"
        private const val KEY_CRASH_FREE_SESSIONS = "crash_free_sessions"
        
        // Privacy compliance settings
        private const val MAX_LOCAL_EVENTS = 1000
        private const val SYNC_INTERVAL_MS = 300000L // 5 minutes
        private const val RETENTION_DAYS = 30
    }
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val eventQueue = ConcurrentLinkedQueue<TelemetryEvent>()
    private val eventCount = AtomicLong(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Telemetry storage directory
    private val telemetryDir = File(context.filesDir, "telemetry")
    private val eventsFile = File(telemetryDir, "events.jsonl")
    
    // Metrics aggregation
    private val metricsAggregator = MetricsAggregator()
    private val privacyValidator = PrivacyValidator()
    
    init {
        // Ensure telemetry directory exists
        if (!telemetryDir.exists()) {
            telemetryDir.mkdirs()
        }
        
        // Load existing event count
        eventCount.set(prefs.getLong(KEY_EVENT_COUNT, 0))
        
        // Start background processing
        startBackgroundProcessing()
    }
    
    /**
     * Log a telemetry event
     */
    fun logEvent(event: TelemetryEvent) {
        // Validate privacy compliance
        if (!privacyValidator.validateEvent(event)) {
            android.util.Log.w("TelemetryManager", "Event rejected due to privacy violation: ${event.eventType}")
            return
        }
        
        // Add to queue
        eventQueue.offer(event)
        eventCount.incrementAndGet()
        
        // Persist to local storage
        persistEvent(event)
        
        // Update metrics
        metricsAggregator.updateMetrics(event)
        
        android.util.Log.d("TelemetryManager", "Logged event: ${event.eventType} for encounter: ${event.encounterId}")
    }
    
    /**
     * Log encounter start event (EVT-1)
     */
    fun logEncounterStart(
        encounterId: String,
        deviceTier: String,
        clinicId: String?,
        audioQuality: String? = null,
        batteryLevel: Int? = null
    ) {
        val event = EncounterStartEvent(
            encounterId = encounterId,
            timestamp = Instant.now().toString(),
            deviceTier = deviceTier,
            clinicId = clinicId,
            audioQuality = audioQuality,
            batteryLevel = batteryLevel
        )
        logEvent(event)
    }
    
    /**
     * Log transcription complete event (EVT-2)
     */
    fun logTranscriptionComplete(
        encounterId: String,
        deviceTier: String,
        clinicId: String?,
        werEstimate: Double,
        processingTimeMs: Long,
        modelVersion: String,
        audioDurationMs: Long,
        confidenceScore: Double? = null,
        languageDetected: String? = null
    ) {
        val event = TranscriptionCompleteEvent(
            encounterId = encounterId,
            timestamp = Instant.now().toString(),
            deviceTier = deviceTier,
            clinicId = clinicId,
            werEstimate = werEstimate,
            processingTimeMs = processingTimeMs,
            modelVersion = modelVersion,
            audioDurationMs = audioDurationMs,
            confidenceScore = confidenceScore,
            languageDetected = languageDetected
        )
        logEvent(event)
    }
    
    /**
     * Log review complete event (EVT-3)
     */
    fun logReviewComplete(
        encounterId: String,
        deviceTier: String,
        clinicId: String?,
        editRatePercent: Double,
        reviewDurationS: Long,
        confidenceOverrides: Int,
        totalEdits: Int,
        prescriptionEdits: Int,
        soapEdits: Int,
        redFlagsResolved: Int = 0
    ) {
        val event = ReviewCompleteEvent(
            encounterId = encounterId,
            timestamp = Instant.now().toString(),
            deviceTier = deviceTier,
            clinicId = clinicId,
            editRatePercent = editRatePercent,
            reviewDurationS = reviewDurationS,
            confidenceOverrides = confidenceOverrides,
            totalEdits = totalEdits,
            prescriptionEdits = prescriptionEdits,
            soapEdits = soapEdits,
            redFlagsResolved = redFlagsResolved
        )
        logEvent(event)
    }
    
    /**
     * Log export success event (EVT-4)
     */
    fun logExportSuccess(
        encounterId: String,
        deviceTier: String,
        clinicId: String?,
        pdfSizeKb: Long,
        exportDurationMs: Long,
        batteryLevelPercent: Int,
        storageUsedKb: Long? = null,
        qrCodeGenerated: Boolean = true,
        encryptionApplied: Boolean = true
    ) {
        val event = ExportSuccessEvent(
            encounterId = encounterId,
            timestamp = Instant.now().toString(),
            deviceTier = deviceTier,
            clinicId = clinicId,
            pdfSizeKb = pdfSizeKb,
            exportDurationMs = exportDurationMs,
            batteryLevelPercent = batteryLevelPercent,
            storageUsedKb = storageUsedKb,
            qrCodeGenerated = qrCodeGenerated,
            encryptionApplied = encryptionApplied
        )
        logEvent(event)
    }
    
    /**
     * Log thermal event (EVT-5)
     */
    fun logThermalEvent(
        encounterId: String,
        deviceTier: String,
        clinicId: String?,
        thermalState: String,
        mitigationAction: String,
        cpuUsagePercent: Double,
        temperature: Double? = null,
        recoveryTimeMs: Long? = null
    ) {
        val event = ThermalEvent(
            encounterId = encounterId,
            timestamp = Instant.now().toString(),
            deviceTier = deviceTier,
            clinicId = clinicId,
            thermalState = thermalState,
            mitigationAction = mitigationAction,
            cpuUsagePercent = cpuUsagePercent,
            temperature = temperature,
            recoveryTimeMs = recoveryTimeMs
        )
        logEvent(event)
    }
    
    /**
     * Log edit cause code event (ST-8.10)
     */
    fun logEditCauseCode(
        encounterId: String,
        deviceTier: String,
        clinicId: String?,
        editType: String,
        fieldName: String,
        originalValue: String,
        correctedValue: String,
        confidenceScore: Double? = null
    ) {
        val event = EditCauseCodeEvent(
            encounterId = encounterId,
            timestamp = Instant.now().toString(),
            deviceTier = deviceTier,
            clinicId = clinicId,
            editType = editType,
            fieldName = fieldName,
            originalValue = originalValue,
            correctedValue = correctedValue,
            confidenceScore = confidenceScore
        )
        logEvent(event)
    }
    
    /**
     * Log policy toggle event (ST-8.14)
     */
    fun logPolicyToggle(
        encounterId: String,
        deviceTier: String,
        clinicId: String?,
        policyType: String,
        actor: String,
        beforeValue: String,
        afterValue: String,
        reason: String? = null
    ) {
        val event = PolicyToggleEvent(
            encounterId = encounterId,
            timestamp = Instant.now().toString(),
            deviceTier = deviceTier,
            clinicId = clinicId,
            policyType = policyType,
            actor = actor,
            beforeValue = beforeValue,
            afterValue = afterValue,
            reason = reason
        )
        logEvent(event)
    }
    
    /**
     * Log bulk edit applied event (ST-8.14)
     */
    fun logBulkEditApplied(
        encounterId: String,
        deviceTier: String,
        clinicId: String?,
        actor: String,
        editType: String,
        beforeValue: String,
        afterValue: String,
        affectedCount: Int
    ) {
        val event = BulkEditAppliedEvent(
            encounterId = encounterId,
            timestamp = Instant.now().toString(),
            deviceTier = deviceTier,
            clinicId = clinicId,
            actor = actor,
            editType = editType,
            beforeValue = beforeValue,
            afterValue = afterValue,
            affectedCount = affectedCount
        )
        logEvent(event)
    }
    
    /**
     * Get aggregated metrics
     */
    fun getAggregatedMetrics(): AggregatedMetrics {
        return metricsAggregator.getMetrics()
    }
    
    /**
     * Get crash-free session rate (ST-8.5)
     */
    fun getCrashFreeSessionRate(): Double {
        val totalSessions = prefs.getLong(KEY_CRASH_FREE_SESSIONS, 0)
        val crashCount = prefs.getLong("crash_count", 0)
        
        return if (totalSessions > 0) {
            ((totalSessions - crashCount) / totalSessions.toDouble()) * 100.0
        } else {
            100.0
        }
    }
    
    /**
     * Update crash-free session metrics
     */
    fun updateCrashFreeSessionMetrics(sessionCompleted: Boolean, hadCrash: Boolean = false) {
        val totalSessions = prefs.getLong(KEY_CRASH_FREE_SESSIONS, 0) + 1
        prefs.edit()
            .putLong(KEY_CRASH_FREE_SESSIONS, totalSessions)
            .apply()
        
        if (hadCrash) {
            val crashCount = prefs.getLong("crash_count", 0) + 1
            prefs.edit()
                .putLong("crash_count", crashCount)
                .apply()
        }
    }
    
    /**
     * Check if pilot mode is enabled (ST-8.13)
     */
    fun isPilotModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_PILOT_MODE_ENABLED, false)
    }
    
    /**
     * Set pilot mode (ST-8.13)
     */
    fun setPilotMode(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_PILOT_MODE_ENABLED, enabled)
            .apply()
        
        // Log the policy toggle
        logPolicyToggle(
            encounterId = "system",
            deviceTier = "unknown",
            clinicId = null,
            policyType = "pilot_mode",
            actor = "system",
            beforeValue = (!enabled).toString(),
            afterValue = enabled.toString(),
            reason = "User preference change"
        )
    }
    
    /**
     * Export edit cause codes to CSV (ST-8.10)
     */
    fun exportEditCauseCodesToCsv(): String {
        val csvBuilder = StringBuilder()
        csvBuilder.appendLine("timestamp,encounter_id,edit_type,field_name,original_value,corrected_value,confidence_score")
        
        // Read events from file and filter edit cause codes
        if (eventsFile.exists()) {
            eventsFile.readLines().forEach { line ->
                try {
                    val event = moshi.adapter(EditCauseCodeEvent::class.java).fromJson(line)
                    event?.let {
                        csvBuilder.appendLine("${it.timestamp},${it.encounterId},${it.editType},${it.fieldName},${it.originalValue},${it.correctedValue},${it.confidenceScore ?: ""}")
                    }
                } catch (e: Exception) {
                    // Skip non-edit-cause-code events
                }
            }
        }
        
        return csvBuilder.toString()
    }
    
    /**
     * Persist event to local storage
     */
    private fun persistEvent(event: TelemetryEvent) {
        try {
            val eventJson = event.toJsonString()
            eventsFile.appendText("$eventJson\n")
            
            // Update event count
            prefs.edit()
                .putLong(KEY_EVENT_COUNT, eventCount.get())
                .apply()
            
            // Cleanup old events if needed
            cleanupOldEvents()
            
        } catch (e: Exception) {
            android.util.Log.e("TelemetryManager", "Failed to persist event", e)
        }
    }
    
    /**
     * Cleanup old events based on retention policy
     */
    private fun cleanupOldEvents() {
        if (eventCount.get() > MAX_LOCAL_EVENTS) {
            scope.launch {
                try {
                    val cutoffTime = Instant.now().minusSeconds(RETENTION_DAYS * 24 * 60 * 60L)
                    val tempFile = File(telemetryDir, "events_temp.jsonl")
                    
                    eventsFile.readLines()
                        .filter { line ->
                            try {
                                val event = moshi.adapter(TelemetryEvent::class.java).fromJson(line)
                                event?.let { Instant.parse(it.timestamp).isAfter(cutoffTime) } ?: false
                            } catch (e: Exception) {
                                false
                            }
                        }
                        .forEach { line ->
                            tempFile.appendText("$line\n")
                        }
                    
                    eventsFile.delete()
                    tempFile.renameTo(eventsFile)
                    
                } catch (e: Exception) {
                    android.util.Log.e("TelemetryManager", "Failed to cleanup old events", e)
                }
            }
        }
    }
    
    /**
     * Start background processing for metrics and cleanup
     */
    private fun startBackgroundProcessing() {
        scope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                
                // Process queued events
                processQueuedEvents()
                
                // Update aggregated metrics
                metricsAggregator.updateAggregatedMetrics()
            }
        }
    }
    
    /**
     * Process queued events for reporting
     */
    private fun processQueuedEvents() {
        val eventsToProcess = mutableListOf<TelemetryEvent>()
        
        // Drain the queue
        while (eventQueue.isNotEmpty()) {
            eventQueue.poll()?.let { eventsToProcess.add(it) }
        }
        
        // Process events in batches
        if (eventsToProcess.isNotEmpty()) {
            android.util.Log.d("TelemetryManager", "Processing ${eventsToProcess.size} queued events")
            // Here we would typically send to backend if available
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
    }
}
