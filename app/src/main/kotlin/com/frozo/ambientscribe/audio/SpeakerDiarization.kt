package com.frozo.ambientscribe.audio

import android.content.Context
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Energy-based speaker diarization system for distinguishing between speakers
 * in a conversation (e.g., doctor and patient).
 */
class SpeakerDiarization(
    private val context: Context? = null,
    private val energyThresholdRatio: Float = 1.5f,
    private val switchHysteresisMs: Long = 1000,
    private val minUtteranceDurationMs: Long = 500,
    private val silenceThresholdMs: Long = 300
) {
    // Diarization quality evaluation
    private val diarizationEvaluator = context?.let { 
        DiarizationEvaluator(it, MetricsCollector(it))
    }
    
    // Single-speaker fallback mode
    private val isSingleSpeakerMode = AtomicBoolean(false)
    companion object {
        // Speaker roles
        const val SPEAKER_UNKNOWN = 0
        const val SPEAKER_DOCTOR = 1
        const val SPEAKER_PATIENT = 2
        
        // Energy window size for analysis
        private const val ENERGY_WINDOW_SIZE = 20
    }

    // Speaker state tracking
    private val currentSpeaker = AtomicInteger(SPEAKER_UNKNOWN)
    private val manualRoleAssignment = AtomicBoolean(false)
    private val doctorEnergyProfile = AtomicReference(SpeakerEnergyProfile())
    private val patientEnergyProfile = AtomicReference(SpeakerEnergyProfile())
    
    // Diarization state
    private var lastSpeakerSwitchTime = 0L
    private var lastSpeechEndTime = 0L
    private var currentUtteranceStartTime = 0L
    private var isSpeaking = false
    
    // Energy tracking
    private val energyWindow = mutableListOf<Float>()
    private var baselineEnergy = 0f
    private var peakEnergy = 0f
    
    // Results channel
    private val diarizationResultChannel = Channel<DiarizationResult>(Channel.UNLIMITED)
    
    /**
     * Speaker energy profile for characterizing speakers
     */
    data class SpeakerEnergyProfile(
        val meanEnergy: Float = 0f,
        val peakEnergy: Float = 0f,
        val minEnergy: Float = 0f,
        val energySamples: Int = 0
    ) {
        /**
         * Update profile with new energy sample
         */
        fun update(energy: Float): SpeakerEnergyProfile {
            val newSamples = energySamples + 1
            val newMean = if (energySamples == 0) {
                energy
            } else {
                (meanEnergy * energySamples + energy) / newSamples
            }
            
            val newPeak = max(peakEnergy, energy)
            val newMin = if (energySamples == 0) energy else min(minEnergy, energy)
            
            return SpeakerEnergyProfile(
                meanEnergy = newMean,
                peakEnergy = newPeak,
                minEnergy = newMin,
                energySamples = newSamples
            )
        }
        
        /**
         * Calculate similarity with another energy sample
         */
        fun similarity(energy: Float): Float {
            if (energySamples == 0) return 0f
            
            // Simple energy ratio similarity
            val ratio = energy / meanEnergy
            return if (ratio > 1f) 1f / ratio else ratio
        }
    }
    
    /**
     * Diarization result with speaker identification
     */
    data class DiarizationResult(
        val speakerId: Int,
        val speakerLabel: String,
        val energy: Float,
        val confidence: Float,
        val timestamp: Long,
        val isManuallyAssigned: Boolean
    )
    
    /**
     * Process audio data for speaker diarization
     */
    fun processAudioData(audioData: AudioCapture.AudioData) {
        val timestamp = audioData.timestamp
        val energy = audioData.energyLevel
        val isVoiceActive = audioData.isVoiceActive
        
        // Update energy window
        updateEnergyWindow(energy)
        
        // Process speech activity
        if (isVoiceActive) {
            if (!isSpeaking) {
                // Speech start
                isSpeaking = true
                currentUtteranceStartTime = timestamp
                
                // Reset peak energy for this utterance
                peakEnergy = energy
            } else {
                // Continuing speech
                peakEnergy = max(peakEnergy, energy)
            }
            
            // Identify speaker if we have enough utterance duration
            if (timestamp - currentUtteranceStartTime >= minUtteranceDurationMs) {
                identifySpeaker(energy, timestamp)
            }
            
        } else {
            if (isSpeaking) {
                // Speech end
                isSpeaking = false
                lastSpeechEndTime = timestamp
                
                // Final speaker identification at the end of utterance
                if (timestamp - currentUtteranceStartTime >= minUtteranceDurationMs) {
                    identifySpeaker(peakEnergy, timestamp)
                }
            }
            
            // Long silence might reset speaker
            if (timestamp - lastSpeechEndTime > silenceThresholdMs * 3) {
                if (!manualRoleAssignment.get() && currentSpeaker.get() != SPEAKER_UNKNOWN) {
                    Timber.v("Long silence, resetting speaker identification")
                    currentSpeaker.set(SPEAKER_UNKNOWN)
                }
            }
        }
        
        // Update diarization evaluator with this sample
        diarizationEvaluator?.addDiarizationSample(
            speakerId = currentSpeaker.get(),
            energyLevel = energy,
            confidence = if (currentSpeaker.get() == SPEAKER_UNKNOWN) 0.5f else 0.8f,
            isManuallyAssigned = manualRoleAssignment.get()
        )
        
        // Check if we should use single-speaker fallback mode
        diarizationEvaluator?.let {
            isSingleSpeakerMode.set(it.isFallbackModeNeeded())
        }
    }
    
    /**
     * Manually set the current speaker (for one-tap role swap)
     */
    fun setCurrentSpeaker(speakerId: Int) {
        if (speakerId != currentSpeaker.get()) {
            val oldSpeaker = currentSpeaker.getAndSet(speakerId)
            manualRoleAssignment.set(true)
            lastSpeakerSwitchTime = System.currentTimeMillis()
            
            Timber.d("Manual speaker change: $oldSpeaker -> $speakerId")
            
            // Emit diarization result for manual change
            val result = DiarizationResult(
                speakerId = speakerId,
                speakerLabel = getSpeakerLabel(speakerId),
                energy = 0f,
                confidence = 1f,
                timestamp = System.currentTimeMillis(),
                isManuallyAssigned = true
            )
            
            diarizationResultChannel.trySend(result)
        }
    }
    
    /**
     * Swap doctor and patient roles (one-tap role swap)
     */
    fun swapSpeakerRoles() {
        val currentSpeakerId = currentSpeaker.get()
        val newSpeakerId = when (currentSpeakerId) {
            SPEAKER_DOCTOR -> SPEAKER_PATIENT
            SPEAKER_PATIENT -> SPEAKER_DOCTOR
            else -> SPEAKER_DOCTOR // Default to doctor if unknown
        }
        
        setCurrentSpeaker(newSpeakerId)
        
        // Also swap energy profiles
        val doctorProfile = doctorEnergyProfile.get()
        val patientProfile = patientEnergyProfile.get()
        
        doctorEnergyProfile.set(patientProfile)
        patientEnergyProfile.set(doctorProfile)
        
        // Record swap event in diarization evaluator
        diarizationEvaluator?.recordSpeakerSwap(
            fromSpeakerId = currentSpeakerId,
            toSpeakerId = newSpeakerId,
            isAutomatic = false
        )
        
        Timber.i("Speaker roles swapped: Doctor ↔ Patient")
    }
    
    /**
     * Reset diarization state
     */
    fun reset() {
        currentSpeaker.set(SPEAKER_UNKNOWN)
        manualRoleAssignment.set(false)
        doctorEnergyProfile.set(SpeakerEnergyProfile())
        patientEnergyProfile.set(SpeakerEnergyProfile())
        
        energyWindow.clear()
        baselineEnergy = 0f
        peakEnergy = 0f
        
        lastSpeakerSwitchTime = 0L
        lastSpeechEndTime = 0L
        currentUtteranceStartTime = 0L
        isSpeaking = false
        
        // Reset single-speaker mode
        isSingleSpeakerMode.set(false)
        
        // Reset diarization evaluator
        diarizationEvaluator?.reset()
        
        Timber.d("Speaker diarization reset")
    }
    
    /**
     * Get flow of diarization results
     */
    fun getDiarizationFlow(): Flow<DiarizationResult> = flow {
        while (true) {
            try {
                val result = diarizationResultChannel.receive()
                emit(result)
            } catch (e: Exception) {
                Timber.w(e, "Error in diarization flow")
                break
            }
        }
    }
    
    /**
     * Get current speaker ID
     */
    fun getCurrentSpeaker(): Int {
        return currentSpeaker.get()
    }
    
    /**
     * Get diarization quality metrics
     */
    suspend fun getDiarizationMetrics(): Map<String, Any> {
        return diarizationEvaluator?.getDiarizationMetrics() ?: mapOf(
            "der" to 0.0f,
            "swap_accuracy" to 1.0f,
            "quality" to "UNKNOWN",
            "fallback_mode" to false,
            "sample_count" to 0
        )
    }
    
    /**
     * Check if diarization is in single-speaker fallback mode
     */
    fun isInSingleSpeakerMode(): Boolean {
        return isSingleSpeakerMode.get()
    }
    
    /**
     * Get speaker label from ID
     */
    fun getSpeakerLabel(speakerId: Int): String {
        return when (speakerId) {
            SPEAKER_DOCTOR -> "Doctor"
            SPEAKER_PATIENT -> "Patient"
            else -> "Unknown"
        }
    }
    
    /**
     * Update energy window with new sample
     */
    private fun updateEnergyWindow(energy: Float) {
        energyWindow.add(energy)
        
        if (energyWindow.size > ENERGY_WINDOW_SIZE) {
            energyWindow.removeAt(0)
        }
        
        // Update baseline energy (average of window)
        if (energyWindow.size >= 5) {
            baselineEnergy = energyWindow.average().toFloat()
        }
    }
    
    /**
     * Identify speaker based on energy profile
     */
    private fun identifySpeaker(energy: Float, timestamp: Long) {
        // Don't change speaker if manual assignment is active or too soon after last switch
        // Also don't change if in single-speaker fallback mode
        if (manualRoleAssignment.get() || 
            timestamp - lastSpeakerSwitchTime < switchHysteresisMs ||
            isSingleSpeakerMode.get()) {
            return
        }
        
        val currentSpeakerId = currentSpeaker.get()
        
        // If we don't have speaker profiles yet, assign this as first speaker
        if (doctorEnergyProfile.get().energySamples == 0 && 
            patientEnergyProfile.get().energySamples == 0) {
            
            // First speaker is assumed to be the doctor
            val newProfile = SpeakerEnergyProfile().update(energy)
            doctorEnergyProfile.set(newProfile)
            currentSpeaker.set(SPEAKER_DOCTOR)
            
            emitDiarizationResult(SPEAKER_DOCTOR, energy, 0.7f, timestamp)
            return
        }
        
        // Compare energy with existing profiles
        val doctorProfile = doctorEnergyProfile.get()
        val patientProfile = patientEnergyProfile.get()
        
        val doctorSimilarity = doctorProfile.similarity(energy)
        val patientSimilarity = patientProfile.similarity(energy)
        
        // Determine if this is a new speaker
        val energyRatio = if (baselineEnergy > 0) energy / baselineEnergy else 1f
        val isSignificantChange = energyRatio > energyThresholdRatio || 
                                 energyRatio < 1f / energyThresholdRatio
        
        val speakerId = when {
            // Clear doctor match
            doctorSimilarity > patientSimilarity * 1.2f -> SPEAKER_DOCTOR
            
            // Clear patient match
            patientSimilarity > doctorSimilarity * 1.2f -> SPEAKER_PATIENT
            
            // Significant energy change might indicate speaker change
            isSignificantChange && currentSpeakerId != SPEAKER_UNKNOWN -> {
                if (currentSpeakerId == SPEAKER_DOCTOR) SPEAKER_PATIENT else SPEAKER_DOCTOR
            }
            
            // Default to current speaker if we can't determine
            else -> currentSpeakerId
        }
        
        // Update speaker profile
        if (speakerId != SPEAKER_UNKNOWN) {
            if (speakerId == SPEAKER_DOCTOR) {
                val newProfile = doctorProfile.update(energy)
                doctorEnergyProfile.set(newProfile)
            } else {
                val newProfile = patientProfile.update(energy)
                patientEnergyProfile.set(newProfile)
            }
        }
        
        // Only emit result if speaker changed or unknown
        if (speakerId != currentSpeakerId || currentSpeakerId == SPEAKER_UNKNOWN) {
            currentSpeaker.set(speakerId)
            lastSpeakerSwitchTime = timestamp
            
            // Calculate confidence based on similarity difference
            val confidence = abs(doctorSimilarity - patientSimilarity).coerceIn(0.5f, 0.95f)
            
            emitDiarizationResult(speakerId, energy, confidence, timestamp)
        }
    }
    
    /**
     * Emit diarization result
     */
    private fun emitDiarizationResult(
        speakerId: Int,
        energy: Float,
        confidence: Float,
        timestamp: Long
    ) {
        val result = DiarizationResult(
            speakerId = speakerId,
            speakerLabel = getSpeakerLabel(speakerId),
            energy = energy,
            confidence = confidence,
            timestamp = timestamp,
            isManuallyAssigned = manualRoleAssignment.get()
        )
        
        diarizationResultChannel.trySend(result)
        
        Timber.d("Speaker identified: ${result.speakerLabel} (ID: $speakerId), " +
                "confidence: ${confidence * 100}%, energy: $energy")
    }
}
