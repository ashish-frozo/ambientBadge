package com.frozo.ambientscribe.telemetry

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Telemetry event collection system for tracking metrics in pilot mode.
 * Collects performance and accuracy metrics while ensuring privacy compliance.
 */
class MetricsCollector(private val context: Context) {

    companion object {
        // Event types
        const val EVENT_TRANSCRIPTION_COMPLETED = "transcription_completed"
        const val EVENT_ASR_ERROR = "asr_error"
        const val EVENT_PERFORMANCE_METRIC = "performance_metric"
        const val EVENT_ACCURACY_METRIC = "accuracy_metric"
        const val EVENT_USER_CORRECTION = "user_correction"
        
        // Shared preferences keys
        private const val PREFS_NAME = "metrics_prefs"
        private const val KEY_PILOT_MODE_ENABLED = "pilot_mode_enabled"
        private const val KEY_USER_CONSENT = "metrics_consent"
        private const val KEY_DEVICE_ID = "device_id"
        
        // File storage
        private const val METRICS_DIR = "metrics"
        private const val METRICS_FILE_PREFIX = "metrics_"
        private const val METRICS_FILE_EXTENSION = ".jsonl"
        
        // Limits
        private const val MAX_METRICS_FILE_SIZE_BYTES = 1024 * 1024 // 1MB
        private const val MAX_METRICS_STORAGE_BYTES = 10 * 1024 * 1024 // 10MB
        private const val MAX_METRICS_AGE_DAYS = 30
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isPilotModeEnabled = AtomicBoolean(prefs.getBoolean(KEY_PILOT_MODE_ENABLED, false))
    private val isUserConsentGranted = AtomicBoolean(prefs.getBoolean(KEY_USER_CONSENT, false))
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val metricsAdapter: JsonAdapter<Map<String, Any>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )
    
    /**
     * Enable or disable pilot mode for metrics collection
     */
    fun setPilotModeEnabled(enabled: Boolean) {
        isPilotModeEnabled.set(enabled)
        prefs.edit().putBoolean(KEY_PILOT_MODE_ENABLED, enabled).apply()
        Timber.i("Pilot mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Set user consent for metrics collection
     */
    fun setUserConsent(granted: Boolean) {
        isUserConsentGranted.set(granted)
        prefs.edit().putBoolean(KEY_USER_CONSENT, granted).apply()
        Timber.i("User metrics consent ${if (granted) "granted" else "revoked"}")
        
        // If consent is revoked, clear all metrics
        if (!granted) {
            clearAllMetrics()
        }
    }
    
    /**
     * Check if metrics collection is allowed
     */
    fun isMetricsCollectionAllowed(): Boolean {
        return isPilotModeEnabled.get() && isUserConsentGranted.get()
    }
    
    /**
     * Log a telemetry event with metrics
     */
    suspend fun logMetricEvent(
        eventType: String,
        metrics: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if metrics collection is allowed
            if (!isMetricsCollectionAllowed()) {
                return@withContext Result.failure(IllegalStateException("Metrics collection not allowed"))
            }
            
            val timestamp = System.currentTimeMillis()
            val eventId = UUID.randomUUID().toString()
            val deviceId = getOrCreateDeviceId()
            
            // Create event JSON
            val eventJson = JSONObject().apply {
                put("event_id", eventId)
                put("event_type", eventType)
                put("timestamp", timestamp)
                put("device_id", deviceId)
                put("pilot_mode", true)
                
                // Add all metrics
                val metricsJson = JSONObject()
                metrics.forEach { (key, value) ->
                    metricsJson.put(key, value.toString())
                }
                put("metrics", metricsJson)
            }
            
            // Write to metrics file
            writeMetricEvent(eventJson.toString())
            
            // Prune old metrics if necessary
            pruneOldMetrics()
            
            Timber.d("Metric event logged: $eventType")
            Result.success(eventId)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to log metric event")
            Result.failure(e)
        }
    }
    
    /**
     * Log ASR accuracy metrics
     */
    suspend fun logASRAccuracy(
        wer: Float,
        medF1Score: Float,
        confidenceScore: Float,
        sampleId: String,
        durationMs: Long
    ): Result<String> {
        val metrics = mapOf(
            "wer" to wer,
            "med_f1_score" to medF1Score,
            "confidence_score" to confidenceScore,
            "sample_id" to sampleId,
            "duration_ms" to durationMs
        )
        
        return logMetricEvent(EVENT_ACCURACY_METRIC, metrics)
    }
    
    /**
     * Log performance metrics
     */
    suspend fun logPerformanceMetrics(
        processingTimeMs: Long,
        memoryUsageMb: Float,
        cpuUsagePercent: Float,
        threadCount: Int,
        contextSize: Int
    ): Result<String> {
        val metrics = mapOf(
            "processing_time_ms" to processingTimeMs,
            "memory_usage_mb" to memoryUsageMb,
            "cpu_usage_percent" to cpuUsagePercent,
            "thread_count" to threadCount,
            "context_size" to contextSize
        )
        
        return logMetricEvent(EVENT_PERFORMANCE_METRIC, metrics)
    }
    
    /**
     * Log user corrections to ASR output
     */
    suspend fun logUserCorrection(
        originalText: String,
        correctedText: String,
        correctionType: String,
        wordErrorCount: Int
    ): Result<String> {
        val metrics = mapOf(
            "correction_type" to correctionType,
            "word_error_count" to wordErrorCount,
            "text_length" to originalText.length,
            "correction_ratio" to (wordErrorCount.toFloat() / originalText.split(" ").size)
        )
        
        return logMetricEvent(EVENT_USER_CORRECTION, metrics)
    }
    
    /**
     * Get or create a unique device ID for metrics
     */
    private fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        
        return deviceId
    }
    
    /**
     * Write metric event to file
     */
    private fun writeMetricEvent(eventJson: String) {
        val metricsDir = File(context.filesDir, METRICS_DIR).apply {
            if (!exists()) mkdirs()
        }
        
        // Use current date for file naming
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        val metricsFile = File(metricsDir, "$METRICS_FILE_PREFIX$today$METRICS_FILE_EXTENSION")
        
        // Append event to file
        metricsFile.appendText("$eventJson\n")
    }
    
    /**
     * Prune old metrics files
     */
    private fun pruneOldMetrics() {
        val metricsDir = File(context.filesDir, METRICS_DIR)
        if (!metricsDir.exists()) return
        
        val currentTime = System.currentTimeMillis()
        val maxAgeMs = MAX_METRICS_AGE_DAYS * 24 * 60 * 60 * 1000L
        
        val metricFiles = metricsDir.listFiles { file ->
            file.name.startsWith(METRICS_FILE_PREFIX) && 
            file.name.endsWith(METRICS_FILE_EXTENSION)
        } ?: return
        
        // Sort files by last modified time (oldest first)
        metricFiles.sortBy { it.lastModified() }
        
        // Remove files that are too old
        for (file in metricFiles) {
            if (currentTime - file.lastModified() > maxAgeMs) {
                file.delete()
                Timber.d("Pruned old metrics file: ${file.name}")
            }
        }
        
        // Calculate total size
        var totalSize = metricFiles.sumOf { it.length() }
        
        // If total size exceeds limit, delete oldest files until under limit
        var i = 0
        while (totalSize > MAX_METRICS_STORAGE_BYTES && i < metricFiles.size) {
            val file = metricFiles[i]
            val fileSize = file.length()
            file.delete()
            totalSize -= fileSize
            i++
            Timber.d("Pruned metrics file due to size limit: ${file.name}")
        }
    }
    
    /**
     * Clear all metrics data
     */
    fun clearAllMetrics() {
        val metricsDir = File(context.filesDir, METRICS_DIR)
        if (metricsDir.exists()) {
            metricsDir.deleteRecursively()
            Timber.i("All metrics data cleared")
        }
    }
    
    /**
     * Get metrics summary for the last N days
     */
    suspend fun getMetricsSummary(days: Int = 7): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val metricsDir = File(context.filesDir, METRICS_DIR)
            if (!metricsDir.exists()) {
                return@withContext Result.success(emptyMap())
            }
            
            val summary = mutableMapOf<String, Any>()
            var totalEvents = 0
            var totalWER = 0f
            var totalMedF1 = 0f
            var werSampleCount = 0
            var medF1SampleCount = 0
            var totalProcessingTime = 0L
            var processingTimeCount = 0
            
            // Get metrics files from last N days
            val calendar = java.util.Calendar.getInstance()
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            val fileNames = mutableListOf<String>()
            
            for (i in 0 until days) {
                val date = dateFormat.format(calendar.time)
                fileNames.add("$METRICS_FILE_PREFIX$date$METRICS_FILE_EXTENSION")
                calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
            }
            
            // Process each file
            for (fileName in fileNames) {
                val file = File(metricsDir, fileName)
                if (!file.exists()) continue
                
                file.useLines { lines ->
                    for (line in lines) {
                        val event = JSONObject(line)
                        val eventType = event.getString("event_type")
                        totalEvents++
                        
                        if (eventType == EVENT_ACCURACY_METRIC) {
                            val metrics = event.getJSONObject("metrics")
                            if (metrics.has("wer")) {
                                totalWER += metrics.getString("wer").toFloat()
                                werSampleCount++
                            }
                            if (metrics.has("med_f1_score")) {
                                totalMedF1 += metrics.getString("med_f1_score").toFloat()
                                medF1SampleCount++
                            }
                        } else if (eventType == EVENT_PERFORMANCE_METRIC) {
                            val metrics = event.getJSONObject("metrics")
                            if (metrics.has("processing_time_ms")) {
                                totalProcessingTime += metrics.getString("processing_time_ms").toLong()
                                processingTimeCount++
                            }
                        }
                    }
                }
            }
            
            // Calculate averages
            val avgWER = if (werSampleCount > 0) totalWER / werSampleCount else 0f
            val avgMedF1 = if (medF1SampleCount > 0) totalMedF1 / medF1SampleCount else 0f
            val avgProcessingTime = if (processingTimeCount > 0) totalProcessingTime / processingTimeCount else 0L
            
            // Build summary
            summary["total_events"] = totalEvents
            summary["avg_wer"] = avgWER
            summary["avg_med_f1"] = avgMedF1
            summary["avg_processing_time_ms"] = avgProcessingTime
            summary["wer_sample_count"] = werSampleCount
            summary["med_f1_sample_count"] = medF1SampleCount
            summary["days_included"] = days
            
            Result.success(summary)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get metrics summary")
            Result.failure(e)
        }
    }
}
