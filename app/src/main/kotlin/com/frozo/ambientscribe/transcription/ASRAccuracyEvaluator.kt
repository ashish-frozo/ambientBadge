package com.frozo.ambientscribe.transcription

import android.content.Context
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Evaluates ASR accuracy in pilot mode using Word Error Rate (WER) and
 * Medical Entity F1 Score metrics.
 */
class ASRAccuracyEvaluator(
    context: Context,
    private val pilotMode: Boolean = false
) {
    private val metricsCollector = MetricsCollector(context)
    private val isPilotModeEnabled = AtomicBoolean(pilotMode)
    
    init {
        metricsCollector.setPilotModeEnabled(pilotMode)
    }
    
    /**
     * Enable or disable pilot mode
     */
    fun setPilotMode(enabled: Boolean) {
        isPilotModeEnabled.set(enabled)
        metricsCollector.setPilotModeEnabled(enabled)
        Timber.i("ASR accuracy evaluation pilot mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Set user consent for metrics collection
     */
    fun setUserConsent(granted: Boolean) {
        metricsCollector.setUserConsent(granted)
    }
    
    /**
     * Calculate Word Error Rate between reference and hypothesis
     */
    fun calculateWER(reference: String, hypothesis: String): Float {
        val refWords = reference.lowercase().split("\\s+".toRegex())
        val hypWords = hypothesis.lowercase().split("\\s+".toRegex())
        
        val distance = levenshteinDistance(refWords, hypWords)
        return if (refWords.isNotEmpty()) {
            distance.toFloat() / refWords.size
        } else {
            if (hypWords.isEmpty()) 0f else 1f
        }
    }
    
    /**
     * Calculate Medical Entity F1 Score
     */
    fun calculateMedF1Score(reference: String, hypothesis: String): Float {
        // Extract medical entities from reference and hypothesis
        val refEntities = extractMedicalEntities(reference)
        val hypEntities = extractMedicalEntities(hypothesis)
        
        // Calculate precision and recall
        val truePositives = refEntities.intersect(hypEntities).size
        val precision = if (hypEntities.isNotEmpty()) {
            truePositives.toFloat() / hypEntities.size
        } else {
            if (refEntities.isEmpty()) 1f else 0f
        }
        
        val recall = if (refEntities.isNotEmpty()) {
            truePositives.toFloat() / refEntities.size
        } else {
            1f
        }
        
        // Calculate F1 score
        return if (precision + recall > 0) {
            2 * precision * recall / (precision + recall)
        } else {
            0f
        }
    }
    
    /**
     * Extract medical entities from text
     * This is a simplified implementation for demonstration
     */
    private fun extractMedicalEntities(text: String): Set<String> {
        // In a real implementation, this would use a medical entity recognition model
        // For demonstration, we'll use a simple list of medical terms
        val medicalTerms = setOf(
            "fever", "cough", "headache", "pain", "nausea", "vomiting",
            "diarrhea", "fatigue", "diabetes", "hypertension", "asthma",
            "mg", "tablet", "capsule", "injection", "syrup", "ointment",
            "paracetamol", "ibuprofen", "aspirin", "antibiotic", "vitamin",
            "prescription", "dose", "daily", "twice", "thrice"
        )
        
        val words = text.lowercase().split("\\s+|\\p{Punct}".toRegex())
        return words.filter { it in medicalTerms }.toSet()
    }
    
    /**
     * Calculate Levenshtein distance between two word lists
     */
    private fun levenshteinDistance(s: List<String>, t: List<String>): Int {
        val m = s.size
        val n = t.size
        
        // Create distance matrix
        val d = Array(m + 1) { IntArray(n + 1) }
        
        // Initialize first row and column
        for (i in 0..m) d[i][0] = i
        for (j in 0..n) d[0][j] = j
        
        // Fill the matrix
        for (j in 1..n) {
            for (i in 1..m) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                d[i][j] = min(
                    min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                )
            }
        }
        
        return d[m][n]
    }
    
    /**
     * Evaluate ASR accuracy and log metrics in pilot mode
     */
    suspend fun evaluateAndLogAccuracy(
        reference: String,
        hypothesis: String,
        sampleId: String,
        durationMs: Long
    ): Result<Map<String, Float>> = withContext(Dispatchers.Default) {
        try {
            // Calculate metrics
            val wer = calculateWER(reference, hypothesis)
            val medF1 = calculateMedF1Score(reference, hypothesis)
            
            // Calculate confidence score based on WER and MedF1
            val confidenceScore = max(0f, 1f - wer) * 0.7f + medF1 * 0.3f
            
            // Log metrics in pilot mode
            if (isPilotModeEnabled.get()) {
                metricsCollector.logASRAccuracy(
                    wer = wer,
                    medF1Score = medF1,
                    confidenceScore = confidenceScore,
                    sampleId = sampleId,
                    durationMs = durationMs
                )
            }
            
            // Return metrics
            val metrics = mapOf(
                "wer" to wer,
                "med_f1" to medF1,
                "confidence" to confidenceScore
            )
            
            Timber.d("ASR accuracy: WER=$wer, MedF1=$medF1, Confidence=$confidenceScore")
            Result.success(metrics)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to evaluate ASR accuracy")
            Result.failure(e)
        }
    }
    
    /**
     * Get accuracy metrics summary
     */
    suspend fun getAccuracySummary(days: Int = 7): Result<Map<String, Any>> {
        return metricsCollector.getMetricsSummary(days)
    }
}
