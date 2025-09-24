package com.frozo.ambientscribe.telemetry

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Metrics collector for telemetry and performance monitoring
 */
class MetricsCollector(private val context: Context) {

    fun isMetricsCollectionAllowed(): Boolean {
        val prefs = context.getSharedPreferences("metrics_prefs", Context.MODE_PRIVATE)
        val pilotModeEnabled = prefs.getBoolean("pilot_mode_enabled", false)
        val consentGranted = prefs.getBoolean("metrics_consent", false)
        return pilotModeEnabled && consentGranted
    }

    fun clearAllMetrics() {
        val metricsDir = context.filesDir.resolve("metrics")
        if (metricsDir.exists()) {
            metricsDir.deleteRecursively()
        }
    }

    suspend fun logASRAccuracy(
        wer: Float,
        medF1Score: Float,
        confidenceScore: Float,
        sampleId: String,
        durationMs: Long,
        accuracy: Float = 0.0f,
        metadata: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return logMetricEvent("asr_accuracy", mapOf(
            "wer" to wer,
            "med_f1_score" to medF1Score,
            "confidence_score" to confidenceScore,
            "sample_id" to sampleId,
            "duration_ms" to durationMs,
            "accuracy" to accuracy
        ) + metadata)
    }

    suspend fun logPerformanceMetrics(
        processingTimeMs: Long,
        memoryUsageMb: Float,
        cpuUsagePercent: Float,
        threadCount: Int,
        contextSize: Int,
        thermalLevel: Int = 0
    ): Result<Unit> {
        return logMetricEvent("performance", mapOf(
            "processing_time_ms" to processingTimeMs,
            "memory_usage_mb" to memoryUsageMb,
            "cpu_usage_percent" to cpuUsagePercent,
            "thread_count" to threadCount,
            "context_size" to contextSize,
            "thermal_level" to thermalLevel
        ))
    }
    
    companion object {
        private const val TAG = "MetricsCollector"
    }
    
    private val metricsScope = CoroutineScope(Dispatchers.IO)
    
    fun recordEvent(eventName: String, properties: Map<String, String> = emptyMap()) {
        metricsScope.launch {
            try {
                Log.d(TAG, "Event: $eventName, Properties: $properties")
                // In real implementation, would send to analytics service
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record event: $eventName", e)
            }
        }
    }
    
    fun recordMetric(metricName: String, value: Double) {
        metricsScope.launch {
            try {
                Log.d(TAG, "Metric: $metricName = $value")
                // In real implementation, would send to metrics service
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record metric: $metricName", e)
            }
        }
    }
    
    suspend fun logEvent(eventName: String, properties: Map<String, String> = emptyMap()) {
        recordEvent(eventName, properties)
    }
    
    suspend fun logMetricEvent(metricName: String, properties: Map<String, Any>): Result<Unit> {
        return try {
            recordEvent(metricName, properties.mapValues { it.value.toString() })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun setPilotModeEnabled(enabled: Boolean) {
        // Mock implementation - in real app would configure pilot mode
        Log.d(TAG, "Pilot mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun setUserConsent(consent: Boolean) {
        // Mock implementation - in real app would handle user consent
        Log.d(TAG, "User consent ${if (consent) "granted" else "denied"}")
    }
    
    fun logASRAccuracy(accuracy: Float, metadata: Map<String, String>) {
        recordEvent("asr_accuracy", metadata + mapOf("accuracy" to accuracy.toString()))
    }
    
    fun getMetricsSummary(): Map<String, Any> {
        // Mock implementation - return empty summary
        return emptyMap()
    }
    
    // Removed duplicate logPerformanceMetrics function
}