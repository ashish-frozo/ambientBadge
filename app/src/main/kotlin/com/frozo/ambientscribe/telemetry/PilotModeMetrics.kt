package com.frozo.ambientscribe.telemetry

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Pilot Mode Metrics Handler for PT-8 implementation
 * Handles WER and F1 score calculations for pilot mode only (ST-8.2, ST-8.9)
 */
class PilotModeMetrics {
    
    @Volatile
    var isEnabled: Boolean = false
        private set
    
    private val transcriptionEvents = mutableListOf<TranscriptionCompleteEvent>()
    private val modelVersions = ConcurrentHashMap<String, AtomicInteger>()
    private val languageDistribution = ConcurrentHashMap<String, AtomicInteger>()
    
    // WER calculation metrics
    private val totalWords = AtomicLong(0)
    private val totalErrors = AtomicLong(0)
    private val werSamples = mutableListOf<Double>()
    
    // F1 score calculation metrics
    private val f1Samples = mutableListOf<Double>()
    private val confidenceSamples = mutableListOf<Double>()
    
    /**
     * Enable pilot mode metrics collection
     */
    fun enable() {
        isEnabled = true
        android.util.Log.d("PilotModeMetrics", "Pilot mode metrics enabled")
    }
    
    /**
     * Disable pilot mode metrics collection
     */
    fun disable() {
        isEnabled = false
        android.util.Log.d("PilotModeMetrics", "Pilot mode metrics disabled")
    }
    
    /**
     * Add transcription event for pilot mode analysis
     */
    fun addTranscriptionEvent(event: TranscriptionCompleteEvent) {
        if (!isEnabled) return
        
        transcriptionEvents.add(event)
        
        // Update model version distribution
        event.modelVersion?.let { version ->
            modelVersions.computeIfAbsent(version) { AtomicInteger(0) }.incrementAndGet()
        }
        
        // Update language distribution
        event.languageDetected?.let { language ->
            languageDistribution.computeIfAbsent(language) { AtomicInteger(0) }.incrementAndGet()
        }
        
        // Update WER metrics
        updateWERMetrics(event)
        
        // Update confidence metrics
        event.confidenceScore?.let { confidence ->
            confidenceSamples.add(confidence)
        }
        
        android.util.Log.d("PilotModeMetrics", "Added transcription event for pilot analysis")
    }
    
    /**
     * Calculate WER (Word Error Rate) for pilot mode
     * WER = (Substitutions + Insertions + Deletions) / Total Words
     */
    private fun updateWERMetrics(event: TranscriptionCompleteEvent) {
        // For pilot mode, we use the werEstimate from the event
        // In a real implementation, this would be calculated against ground truth
        val wer = event.werEstimate
        
        if (wer >= 0) {
            werSamples.add(wer)
            
            // Simulate word count calculation for demonstration
            // In real implementation, this would be calculated from actual transcription
            val estimatedWordCount = (event.audioDurationMs / 1000.0 * 2.5).toLong() // ~2.5 words per second
            val estimatedErrors = (wer * estimatedWordCount).toLong()
            
            totalWords.addAndGet(estimatedWordCount)
            totalErrors.addAndGet(estimatedErrors)
        }
    }
    
    /**
     * Calculate F1 score for medical entity extraction
     * This is a simplified calculation for demonstration
     */
    fun calculateF1Score(
        truePositives: Int,
        falsePositives: Int,
        falseNegatives: Int
    ): Double {
        if (truePositives + falsePositives == 0) return 0.0
        if (truePositives + falseNegatives == 0) return 0.0
        
        val precision = truePositives.toDouble() / (truePositives + falsePositives)
        val recall = truePositives.toDouble() / (truePositives + falseNegatives)
        
        return if (precision + recall > 0) {
            2 * (precision * recall) / (precision + recall)
        } else {
            0.0
        }
    }
    
    /**
     * Add F1 score sample for pilot mode
     */
    fun addF1ScoreSample(f1Score: Double) {
        if (!isEnabled) return
        
        f1Samples.add(f1Score)
    }
    
    /**
     * Get pilot metrics summary
     */
    fun getSummary(): PilotMetricsSummary {
        val averageWER = if (werSamples.isNotEmpty()) {
            werSamples.average()
        } else {
            0.0
        }
        
        val averageF1Score = if (f1Samples.isNotEmpty()) {
            f1Samples.average()
        } else {
            0.0
        }
        
        val averageConfidence = if (confidenceSamples.isNotEmpty()) {
            confidenceSamples.average()
        } else {
            0.0
        }
        
        val modelVersionMap = modelVersions.mapValues { it.value.get() }
        val languageDistributionMap = languageDistribution.mapValues { it.value.get() }
        
        return PilotMetricsSummary(
            totalTranscriptions = transcriptionEvents.size,
            averageWER = averageWER,
            averageF1Score = averageF1Score,
            averageConfidence = averageConfidence,
            modelVersions = modelVersionMap,
            languageDistribution = languageDistributionMap
        )
    }
    
    /**
     * Get detailed WER analysis
     */
    fun getWERAnalysis(): WERAnalysis {
        val samples = werSamples.sorted()
        
        return WERAnalysis(
            totalSamples = samples.size,
            averageWER = if (samples.isNotEmpty()) samples.average() else 0.0,
            medianWER = if (samples.isNotEmpty()) {
                val mid = samples.size / 2
                if (samples.size % 2 == 0) {
                    (samples[mid - 1] + samples[mid]) / 2.0
                } else {
                    samples[mid].toDouble()
                }
            } else 0.0,
            minWER = if (samples.isNotEmpty()) samples.minOrNull() ?: 0.0 else 0.0,
            maxWER = if (samples.isNotEmpty()) samples.maxOrNull() ?: 0.0 else 0.0,
            standardDeviation = calculateStandardDeviation(samples),
            p95WER = if (samples.size >= 20) {
                val index = (samples.size * 0.95).toInt()
                samples[index]
            } else 0.0
        )
    }
    
    /**
     * Get detailed F1 score analysis
     */
    fun getF1Analysis(): F1Analysis {
        val samples = f1Samples.sorted()
        
        return F1Analysis(
            totalSamples = samples.size,
            averageF1 = if (samples.isNotEmpty()) samples.average() else 0.0,
            medianF1 = if (samples.isNotEmpty()) {
                val mid = samples.size / 2
                if (samples.size % 2 == 0) {
                    (samples[mid - 1] + samples[mid]) / 2.0
                } else {
                    samples[mid].toDouble()
                }
            } else 0.0,
            minF1 = if (samples.isNotEmpty()) samples.minOrNull() ?: 0.0 else 0.0,
            maxF1 = if (samples.isNotEmpty()) samples.maxOrNull() ?: 0.0 else 0.0,
            standardDeviation = calculateStandardDeviation(samples),
            p95F1 = if (samples.size >= 20) {
                val index = (samples.size * 0.95).toInt()
                samples[index]
            } else 0.0
        )
    }
    
    /**
     * Calculate standard deviation
     */
    private fun calculateStandardDeviation(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        
        val mean = samples.average()
        val variance = samples.map { (it - mean).pow(2.0) }.average()
        return sqrt(variance)
    }
    
    /**
     * Clear all pilot mode data
     */
    fun clearData() {
        transcriptionEvents.clear()
        modelVersions.clear()
        languageDistribution.clear()
        totalWords.set(0)
        totalErrors.set(0)
        werSamples.clear()
        f1Samples.clear()
        confidenceSamples.clear()
        
        android.util.Log.d("PilotModeMetrics", "Pilot mode data cleared")
    }
    
    /**
     * Export pilot mode data for analysis
     */
    fun exportPilotData(): String {
        val csvBuilder = StringBuilder()
        csvBuilder.appendLine("timestamp,encounter_id,wer_estimate,processing_time_ms,model_version,confidence_score,language_detected")
        
        transcriptionEvents.forEach { event ->
            csvBuilder.appendLine("${event.timestamp},${event.encounterId},${event.werEstimate},${event.processingTimeMs},${event.modelVersion},${event.confidenceScore ?: ""},${event.languageDetected ?: ""}")
        }
        
        return csvBuilder.toString()
    }
}

/**
 * Data classes for pilot mode analysis
 */

data class WERAnalysis(
    val totalSamples: Int,
    val averageWER: Double,
    val medianWER: Double,
    val minWER: Double,
    val maxWER: Double,
    val standardDeviation: Double,
    val p95WER: Double
)

data class F1Analysis(
    val totalSamples: Int,
    val averageF1: Double,
    val medianF1: Double,
    val minF1: Double,
    val maxF1: Double,
    val standardDeviation: Double,
    val p95F1: Double
)

/**
 * Extension function for power calculation
 */
