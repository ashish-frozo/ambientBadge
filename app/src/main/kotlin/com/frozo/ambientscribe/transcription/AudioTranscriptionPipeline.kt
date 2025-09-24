package com.frozo.ambientscribe.transcription

import android.content.Context
import com.frozo.ambientscribe.audio.AudioCapture
import com.frozo.ambientscribe.audio.AudioProcessingConfig
import com.frozo.ambientscribe.audio.SpeakerDiarization
import com.frozo.ambientscribe.transcription.EphemeralTranscriptManager
import com.frozo.ambientscribe.performance.PerformanceManager
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Integration pipeline connecting AudioCapture with ASRService for real-time transcription.
 * Handles the complete audio-to-text workflow with confidence scoring.
 */
class AudioTranscriptionPipeline(
    private val context: Context,
    private val vadThreshold: Float = 0.01f,
    private val confidenceThreshold: Float = 0.6f,
    private val pilotMode: Boolean = false
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val metricsCollector = MetricsCollector(context)
    private val performanceManager = PerformanceManager(context)
    private val audioProcessingConfig = AudioProcessingConfig(context, metricsCollector)
    private val audioCapture = AudioCapture(context)
    private val asrService = ASRService(
        context, 
        confidenceThreshold = confidenceThreshold,
        performanceManager = performanceManager
    )
    private val speakerDiarization = SpeakerDiarization(context)
    private val accuracyEvaluator = ASRAccuracyEvaluator(context, pilotMode)
    private val ephemeralTranscriptManager = EphemeralTranscriptManager(context, metricsCollector)
    
    private val isRunning = AtomicBoolean(false)
    private var processingJob: Job? = null
    private val sessionId = UUID.randomUUID().toString()
    
    private val transcriptionResultChannel = Channel<TranscriptionPipelineResult>(Channel.UNLIMITED)
    private val diarizationResultChannel = Channel<DiarizationPipelineResult>(Channel.UNLIMITED)
    private val errorChannel = Channel<ASRError>(Channel.CONFLATED)
    
    /**
     * Combined result from the audio transcription pipeline
     */
    enum class VoiceActivityState {
        SILENCE, SPEECH
    }

    data class TranscriptionPipelineResult(
        val transcription: ASRService.TranscriptionResult,
        val vadState: VoiceActivityState,
        val audioEnergyLevel: Float,
        val speakerId: Int
    )
    
    /**
     * Diarization result from the pipeline
     */
    data class DiarizationPipelineResult(
        val speakerId: Int,
        val speakerLabel: String,
        val confidence: Float,
        val timestamp: Long,
        val isManuallyAssigned: Boolean
    )
    
    /**
     * Initialize the complete pipeline
     */
    suspend fun initialize(): Result<Unit> {
        try {
            Timber.d("Initializing AudioTranscriptionPipeline")
            
            // Initialize performance manager
            performanceManager.initialize()
            
            // Initialize audio capture
            val audioResult = audioCapture.initialize()
            if (audioResult == false) {
                val exception = Exception("Audio initialization failed")
                val error = ASRError.InitializationError(
                    message = "Failed to initialize audio capture: ${exception.message}",
                    cause = exception
                )
                errorChannel.send(error)
                return Result.failure(exception)
            }
            
            // Initialize ASR service
            val asrResult = asrService.initialize()
            if (asrResult.isFailure) {
                val exception = Exception("ASR initialization failed")
                val error = ASRError.InitializationError(
                    message = "Failed to initialize ASR service: ${exception.message}",
                    cause = exception
                )
                errorChannel.send(error)
                return Result.failure(exception)
            }
            
            // Set up pilot mode
            if (pilotMode) {
                accuracyEvaluator.setPilotMode(true)
                metricsCollector.setPilotModeEnabled(true)
                Timber.i("Pilot mode enabled for metrics collection")
            }
            
            // Monitor ASR errors
            coroutineScope.launch {
                monitorASRErrors()
            }
            
            Timber.i("AudioTranscriptionPipeline initialized successfully")
            return Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AudioTranscriptionPipeline")
            val error = ASRError.InitializationError(
                message = "Failed to initialize transcription pipeline: ${e.message}",
                cause = e
            )
            errorChannel.send(error)
            cleanup()
            return Result.failure(e)
        }
    }
    
    /**
     * Start the real-time transcription pipeline
     * 
     * @param ephemeralMode If true, transcripts will only be stored in RAM and purged on app termination
     */
    suspend fun startTranscription(ephemeralMode: Boolean = false): Result<Unit> {
        try {
            if (isRunning.get()) {
                return Result.success(Unit)
            }
            
            Timber.d("Starting transcription pipeline")
            
            // Start ephemeral mode if requested
            if (ephemeralMode) {
                ephemeralTranscriptManager.startEphemeralSession()
                Timber.i("Started transcription in ephemeral mode")
            }
            
            // Start audio capture
            val result = audioCapture.startRecording()
            if (result == false) {
                val exception = Exception("Failed to start recording")
                val error = ASRError.AudioInputError(
                    message = "Failed to start audio recording: ${exception.message}",
                    cause = exception
                )
                errorChannel.send(error)
                return Result.failure(exception)
            }
            
            // Start processing job
            processingJob = coroutineScope.launch {
                processAudioStream()
            }
            
            isRunning.set(true)
            Timber.i("Transcription pipeline started")
            
            return Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start transcription pipeline")
            val error = ASRError.fromException(e)
            errorChannel.send(error)
            return Result.failure(e)
        }
    }
    
    /**
     * Stop the transcription pipeline
     */
    suspend fun stopTranscription(): Result<Unit> {
        try {
            if (!isRunning.get()) {
                return Result.success(Unit)
            }
            
            Timber.d("Stopping transcription pipeline")
            
            isRunning.set(false)
            
            // Cancel processing job
            processingJob?.cancelAndJoin()
            processingJob = null
            
            // Stop audio capture
            val result = audioCapture.stopRecording()
            if (result == false) {
                val exception = Exception("Failed to stop recording")
                return Result.failure(exception)
            }
            
            // End ephemeral session if active
            if (ephemeralTranscriptManager.isEphemeralModeActive()) {
                ephemeralTranscriptManager.endEphemeralSession()
                Timber.i("Ended ephemeral transcript session")
            }
            
            Timber.i("Transcription pipeline stopped")
            return Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Error stopping transcription pipeline")
            return Result.failure(e)
        }
    }
    
    /**
     * Get flow of transcription results
     */
    fun getTranscriptionFlow(): Flow<TranscriptionPipelineResult> = flow {
        while (isRunning.get()) {
            try {
                val result = transcriptionResultChannel.receive()
                emit(result)
            } catch (e: Exception) {
                Timber.w(e, "Error in transcription flow")
                break
            }
        }
    }
    
    /**
     * Get flow of diarization results
     */
    fun getDiarizationFlow(): Flow<DiarizationPipelineResult> = flow {
        while (isRunning.get()) {
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
     * Get the audio processing configuration
     */
    fun getAudioProcessingConfig(): AudioProcessingConfig {
        return audioProcessingConfig
    }
    
    /**
     * Get the ephemeral transcript manager
     */
    fun getEphemeralTranscriptManager(): EphemeralTranscriptManager {
        return ephemeralTranscriptManager
    }
    
    /**
     * Get flow of ASR errors
     */
    fun getErrorFlow(): Flow<ASRError> = flow {
        while (true) {
            try {
                val error = errorChannel.receive()
                emit(error)
            } catch (e: Exception) {
                Timber.w(e, "Error in error flow")
                break
            }
        }
    }
    
    /**
     * Monitor ASR errors from service
     */
    private suspend fun monitorASRErrors() {
        try {
            asrService.errorFlow.collect { error ->
                errorChannel.send(error)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error monitoring ASR errors")
        }
    }
    
    /**
     * Swap speaker roles
     */
    fun swapSpeakerRoles() {
        speakerDiarization.swapSpeakerRoles()
    }
    
    /**
     * Get current speaker ID
     */
    fun getCurrentSpeaker(): Int {
        return speakerDiarization.getCurrentSpeaker()
    }
    
    /**
     * Get speaker label for speaker ID
     */
    fun getSpeakerLabel(speakerId: Int): String {
        return speakerDiarization.getSpeakerLabel(speakerId)
    }
    
    /**
     * Clear transcription and audio buffers
     */
    fun clearBuffers() {
        asrService.clearTranscription()
        audioCapture.clearRingBuffer()
        Timber.d("Transcription and audio buffers cleared")
    }
    
    /**
     * Delete last 30 seconds of audio and transcription
     */
    suspend fun deleteLast30Seconds() {
        audioCapture.deleteLast30Seconds()
        // In a more sophisticated implementation, we might also trim the transcription
        Timber.i("Last 30 seconds deleted from pipeline")
    }
    
    /**
     * Verify that audio buffer is empty (for privacy compliance)
     */
    fun verifyAudioBufferEmpty(): Boolean {
        return audioCapture.verifyBufferEmpty()
    }
    
    /**
     * Get the last 30 seconds of audio data
     */
    fun getRingBufferData(): ByteArray {
        return audioCapture.getRingBufferData()
    }
    
    /**
     * Clean up all resources
     */
    fun cleanup() {
        runBlocking {
            stopTranscription()
        }
        
        audioCapture.cleanup()
        asrService.cleanup()
        performanceManager.cleanup()
        
        // Force purge any ephemeral transcripts
        if (ephemeralTranscriptManager.isEphemeralModeActive()) {
            ephemeralTranscriptManager.forcePurge()
        }
        transcriptionResultChannel.close()
        diarizationResultChannel.close()
        errorChannel.close()
        coroutineScope.cancel()
        
        Timber.d("AudioTranscriptionPipeline cleanup completed")
    }
    
    /**
     * Set user consent for metrics collection
     */
    fun setUserConsent(granted: Boolean) {
        accuracyEvaluator.setUserConsent(granted)
        metricsCollector.setUserConsent(granted)
    }
    
    /**
     * Evaluate ASR accuracy with reference text (for pilot mode)
     */
    suspend fun evaluateAccuracy(reference: String, hypothesis: String): Result<Map<String, Float>> {
        if (!pilotMode) {
            return Result.failure(IllegalStateException("Accuracy evaluation only available in pilot mode"))
        }
        
        return accuracyEvaluator.evaluateAndLogAccuracy(
            reference = reference,
            hypothesis = hypothesis,
            sampleId = sessionId,
            durationMs = System.currentTimeMillis()
        )
    }
    
    /**
     * Get accuracy metrics summary
     */
    suspend fun getAccuracySummary(days: Int = 7): Result<Map<String, Any>> {
        if (!pilotMode) {
            return Result.failure(IllegalStateException("Accuracy metrics only available in pilot mode"))
        }
        
        return accuracyEvaluator.getAccuracySummary(days)
    }
    
    /**
     * Log performance metrics
     */
    suspend fun logPerformanceMetrics(processingTimeMs: Long) {
        if (!pilotMode) return
        
        val performanceState = performanceManager.performanceState.value
        
        metricsCollector.logPerformanceMetrics(
            processingTimeMs = processingTimeMs,
            memoryUsageMb = 0f, // Would need to be measured
            cpuUsagePercent = performanceState.cpuUsagePercent.toFloat(),
            thermalLevel = performanceState.thermalState,
            threadCount = performanceState.recommendedThreads,
            contextSize = performanceState.recommendedContextSize
        )
    }

    /**
     * Calculate RMS energy level from audio samples
     */
    private fun calculateEnergyLevel(samples: ShortArray): Float {
        var sum = 0.0
        for (sample in samples) {
            val normalized = sample / 32768.0 // Normalize to [-1, 1]
            sum += normalized * normalized
        }
        return kotlin.math.sqrt(sum / samples.size).toFloat()
    }
    
    /**
     * Main audio processing loop
     */
    private suspend fun processAudioStream() {
        Timber.d("Starting audio stream processing")
        
        try {
            // Collect audio data and VAD state in parallel
            val audioJob = coroutineScope.launch {
                audioCapture.getAudioFlow().collect { samples ->
                    // Process audio through ASR
                    val asrResult = asrService.processAudio(samples.samples)
                    if (asrResult.isFailure) {
                        val exception = Exception("ASR processing failed")
                        Timber.w("ASR processing failed: $exception")
                        val error = ASRError.fromException(exception)
                        errorChannel.send(error)
                    }
                    
                    // Calculate energy level and VAD state
                    val energyLevel = calculateEnergyLevel(samples.samples)
                    val isVoiceActive = energyLevel > vadThreshold
                    
                    // Create audio data for diarization
                    val audioData = SpeakerDiarization.AudioData(
                        timestamp = System.currentTimeMillis(),
                        energyLevel = energyLevel,
                        isVoiceActive = isVoiceActive
                    )
                    
                    // Process audio through speaker diarization
                    speakerDiarization.processAudioData(audioData)
                }
            }
            
            val vadJob = coroutineScope.launch {
                var currentVadState = VoiceActivityState.SILENCE
                var currentEnergyLevel = 0f
                
                audioCapture.getAudioFlow().collect { samples ->
                    val energyLevel = calculateEnergyLevel(samples.samples)
                    val isVoiceActive = energyLevel > vadThreshold
                    currentVadState = if (isVoiceActive) VoiceActivityState.SPEECH else VoiceActivityState.SILENCE
                    currentEnergyLevel = energyLevel
                    Timber.v("VAD state changed to: $currentVadState")
                }
            }
            
            val transcriptionJob = coroutineScope.launch {
                asrService.getTranscriptionFlow().collect { transcriptionResult ->
                    // Get current speaker ID
                    val currentSpeakerId = speakerDiarization.getCurrentSpeaker()
                    
                    // Create pipeline result
                    val pipelineResult = TranscriptionPipelineResult(
                        transcription = transcriptionResult,
                        vadState = VoiceActivityState.SPEECH, // Simplified
                        audioEnergyLevel = 0.5f, // Simplified
                        speakerId = currentSpeakerId
                    )
                    
                    // Add to ephemeral transcript if active
                    if (ephemeralTranscriptManager.isEphemeralModeActive()) {
                        ephemeralTranscriptManager.addTranscriptSegment(
                            text = transcriptionResult.text,
                            speakerId = currentSpeakerId,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    
                    transcriptionResultChannel.trySend(pipelineResult)
                    
                    Timber.d("Pipeline result: '${transcriptionResult.text}' " +
                            "(confidence: ${transcriptionResult.confidence}, " +
                            "level: ${transcriptionResult.confidenceLevel}, " +
                            "speaker: ${speakerDiarization.getSpeakerLabel(currentSpeakerId)})")
                }
            }
            
            val diarizationJob = coroutineScope.launch {
                speakerDiarization.getDiarizationFlow().collect { diarizationResult ->
                    val pipelineResult = DiarizationPipelineResult(
                        speakerId = diarizationResult.speakerId,
                        speakerLabel = diarizationResult.speakerLabel,
                        confidence = diarizationResult.confidence,
                        timestamp = diarizationResult.timestamp,
                        isManuallyAssigned = diarizationResult.isManuallyAssigned
                    )
                    
                    diarizationResultChannel.trySend(pipelineResult)
                    
                    Timber.d("Diarization result: Speaker ${diarizationResult.speakerLabel} " +
                            "(confidence: ${diarizationResult.confidence}, " +
                            "manual: ${diarizationResult.isManuallyAssigned})")
                }
            }
            
            // Wait for all jobs to complete
            joinAll(audioJob, vadJob, transcriptionJob, diarizationJob)
            
        } catch (e: Exception) {
            Timber.e(e, "Error in audio stream processing")
            val error = ASRError.fromException(e)
            errorChannel.send(error)
        } finally {
            Timber.d("Audio stream processing ended")
        }
    }
}