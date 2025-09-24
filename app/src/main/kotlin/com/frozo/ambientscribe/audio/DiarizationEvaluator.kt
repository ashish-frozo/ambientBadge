package com.frozo.ambientscribe.audio

import android.content.Context
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Evaluates diarization performance and manages fallback to single-speaker mode
 * when diarization quality is poor.
 */
class DiarizationEvaluator(
    private val context: Context,
    private val metricsCollector: MetricsCollector? = null
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    companion object {
        // Diarization Error Rate thresholds
        const val DER_THRESHOLD_GOOD = 0.18f // 18% or less is acceptable
        const val DER_THRESHOLD_MODERATE = 0.30f // 30% is moderate
        const val DER_THRESHOLD_POOR = 0.40f // 40% or more is poor
        
        // Swap accuracy threshold
        const val SWAP_ACCURACY_THRESHOLD = 0.95f // 95% accuracy required
        
        // Minimum samples needed for evaluation
        const val MIN_SAMPLES_FOR_EVALUATION = 20
        
        // Maximum samples to keep in history
        const val MAX_HISTORY_SAMPLES = 100
        
        // Fallback mode duration (in samples)
        const val FALLBACK_DURATION_SAMPLES = 50
    }
    
    // Diarization quality state
    private val _diarizationQuality = MutableStateFlow(DiarizationQuality.UNKNOWN)
    val diarizationQuality: StateFlow<DiarizationQuality> = _diarizationQuality
    
    // Fallback mode state
    private val isFallbackModeEnabled = AtomicBoolean(false)
    private val fallbackCountdown = AtomicInteger(0)
    
    // History of diarization results for evaluation
    private val diarizationHistory = mutableListOf<DiarizationSample>()
    private val manualSwapHistory = mutableListOf<SwapEvent>()
    
    // Current DER estimate
    private var currentDER = 0.0f
    private var currentSwapAccuracy = 1.0f
    
    /**
     * Diarization quality levels
     */
    enum class DiarizationQuality {
        UNKNOWN,    // Not enough data to evaluate
        GOOD,       // DER ≤ 18%
        MODERATE,   // 18% < DER ≤ 30%
        POOR        // DER > 30%
    }
    
    /**
     * Sample of diarization result for evaluation
     */
    data class DiarizationSample(
        val timestamp: Long,
        val speakerId: Int,
        val energyLevel: Float,
        val confidence: Float,
        val isManuallyAssigned: Boolean
    )
    
    /**
     * Speaker role swap event
     */
    data class SwapEvent(
        val timestamp: Long,
        val fromSpeakerId: Int,
        val toSpeakerId: Int,
        val isAutomatic: Boolean
    )
    
    /**
     * Add a diarization sample for evaluation
     */
    fun addDiarizationSample(
        speakerId: Int,
        energyLevel: Float,
        confidence: Float,
        isManuallyAssigned: Boolean
    ) {
        val sample = DiarizationSample(
            timestamp = System.currentTimeMillis(),
            speakerId = speakerId,
            energyLevel = energyLevel,
            confidence = confidence,
            isManuallyAssigned = isManuallyAssigned
        )
        
        synchronized(diarizationHistory) {
            diarizationHistory.add(sample)
            
            // Limit history size
            if (diarizationHistory.size > MAX_HISTORY_SAMPLES) {
                diarizationHistory.removeAt(0)
            }
        }
        
        // Update DER estimate if we have enough samples
        if (diarizationHistory.size >= MIN_SAMPLES_FOR_EVALUATION) {
            updateDEREstimate()
        }
        
        // Handle fallback mode countdown
        if (isFallbackModeEnabled.get() && fallbackCountdown.get() > 0) {
            fallbackCountdown.decrementAndGet()
            
            // Exit fallback mode when countdown reaches zero
            if (fallbackCountdown.get() <= 0) {
                isFallbackModeEnabled.set(false)
                Timber.i("Exiting single-speaker fallback mode")
            }
        }
    }
    
    /**
     * Record a speaker swap event
     */
    fun recordSpeakerSwap(fromSpeakerId: Int, toSpeakerId: Int, isAutomatic: Boolean) {
        val swapEvent = SwapEvent(
            timestamp = System.currentTimeMillis(),
            fromSpeakerId = fromSpeakerId,
            toSpeakerId = toSpeakerId,
            isAutomatic = isAutomatic
        )
        
        synchronized(manualSwapHistory) {
            manualSwapHistory.add(swapEvent)
            
            // Limit history size
            if (manualSwapHistory.size > MAX_HISTORY_SAMPLES) {
                manualSwapHistory.removeAt(0)
            }
        }
        
        // Update swap accuracy if we have enough samples
        if (manualSwapHistory.size >= 5) {
            updateSwapAccuracy()
        }
    }
    
    /**
     * Check if fallback to single-speaker mode is needed
     */
    fun isFallbackModeNeeded(): Boolean {
        return isFallbackModeEnabled.get()
    }
    
    /**
     * Get current Diarization Error Rate estimate
     */
    fun getCurrentDER(): Float {
        return currentDER
    }
    
    /**
     * Get current swap accuracy
     */
    fun getSwapAccuracy(): Float {
        return currentSwapAccuracy
    }
    
    /**
     * Update DER estimate based on history
     */
    private fun updateDEREstimate() {
        synchronized(diarizationHistory) {
            if (diarizationHistory.size < MIN_SAMPLES_FOR_EVALUATION) {
                return
            }
            
            // Group samples by time windows (500ms)
            val timeWindows = diarizationHistory
                .groupBy { it.timestamp / 500 }
                .values
                .filter { it.size > 1 }
            
            if (timeWindows.isEmpty()) {
                return
            }
            
            // Calculate errors in each time window
            var totalErrors = 0
            var totalSamples = 0
            
            for (window in timeWindows) {
                // Find dominant speaker in this window (by energy or manual assignment)
                val manualSpeaker = window.find { it.isManuallyAssigned }?.speakerId
                val dominantSpeaker = manualSpeaker ?: window
                    .groupBy { it.speakerId }
                    .maxByOrNull { (_, samples) -> samples.sumOf { it.energyLevel.toDouble() } }
                    ?.key
                
                if (dominantSpeaker != null) {
                    // Count samples not matching dominant speaker as errors
                    val errors = window.count { it.speakerId != dominantSpeaker }
                    totalErrors += errors
                    totalSamples += window.size
                }
            }
            
            // Calculate DER
            currentDER = if (totalSamples > 0) {
                totalErrors.toFloat() / totalSamples
            } else {
                0.0f
            }
            
            // Update quality state
            val newQuality = when {
                currentDER <= DER_THRESHOLD_GOOD -> DiarizationQuality.GOOD
                currentDER <= DER_THRESHOLD_MODERATE -> DiarizationQuality.MODERATE
                else -> DiarizationQuality.POOR
            }
            
            if (_diarizationQuality.value != newQuality) {
                _diarizationQuality.value = newQuality
                Timber.i("Diarization quality updated: $newQuality (DER: ${currentDER * 100}%)")
                
                // Log metrics
                metricsCollector?.let {
                    coroutineScope.launch {
                        it.recordEvent(
                            "diarization_quality_change",
                            mapOf(
                                "quality" to newQuality.name,
                                "der" to currentDER.toString(),
                                "swap_accuracy" to currentSwapAccuracy.toString()
                            )
                        )
                    }
                }
            }
            
            // Enable fallback mode if quality is poor
            if (newQuality == DiarizationQuality.POOR && !isFallbackModeEnabled.get()) {
                enableFallbackMode()
            }
        }
    }
    
    /**
     * Update swap accuracy based on history
     */
    private fun updateSwapAccuracy() {
        synchronized(manualSwapHistory) {
            if (manualSwapHistory.isEmpty()) {
                return
            }
            
            // Count automatic swaps that were corrected by manual swaps
            val automaticSwaps = manualSwapHistory.filter { !it.isAutomatic }
            val manualCorrections = automaticSwaps.count { auto ->
                // Look for manual correction within 5 seconds
                manualSwapHistory.any { manual ->
                    !manual.isAutomatic &&
                    abs(manual.timestamp - auto.timestamp) < 5000 &&
                    manual.fromSpeakerId == auto.toSpeakerId &&
                    manual.toSpeakerId == auto.fromSpeakerId
                }
            }
            
            // Calculate swap accuracy
            currentSwapAccuracy = if (automaticSwaps.isNotEmpty()) {
                1.0f - (manualCorrections.toFloat() / automaticSwaps.size)
            } else {
                1.0f
            }
            
            // Enable fallback mode if swap accuracy is too low
            if (currentSwapAccuracy < SWAP_ACCURACY_THRESHOLD && !isFallbackModeEnabled.get()) {
                enableFallbackMode()
            }
        }
    }
    
    /**
     * Enable fallback mode
     */
    private fun enableFallbackMode() {
        isFallbackModeEnabled.set(true)
        fallbackCountdown.set(FALLBACK_DURATION_SAMPLES)
        Timber.w("Enabling single-speaker fallback mode due to poor diarization quality")
        
        // Log fallback event
        metricsCollector?.let {
            coroutineScope.launch {
                it.recordEvent(
                    "diarization_fallback_enabled",
                    mapOf(
                        "der" to currentDER.toString(),
                        "swap_accuracy" to currentSwapAccuracy.toString(),
                        "reason" to if (currentDER > DER_THRESHOLD_POOR) "high_der" else "low_swap_accuracy"
                    )
                )
            }
        }
    }
    
    /**
     * Reset evaluation state
     */
    fun reset() {
        synchronized(diarizationHistory) {
            diarizationHistory.clear()
        }
        synchronized(manualSwapHistory) {
            manualSwapHistory.clear()
        }
        currentDER = 0.0f
        currentSwapAccuracy = 1.0f
        _diarizationQuality.value = DiarizationQuality.UNKNOWN
        isFallbackModeEnabled.set(false)
        fallbackCountdown.set(0)
    }
    
    /**
     * Get diarization metrics for reporting
     */
    suspend fun getDiarizationMetrics(): Map<String, Any> = withContext(Dispatchers.Default) {
        mapOf(
            "der" to currentDER,
            "swap_accuracy" to currentSwapAccuracy,
            "quality" to _diarizationQuality.value.name,
            "fallback_mode" to isFallbackModeEnabled.get(),
            "sample_count" to diarizationHistory.size
        )
    }
}
