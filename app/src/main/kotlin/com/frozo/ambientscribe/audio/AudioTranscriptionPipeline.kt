package com.frozo.ambientscribe.audio

import android.content.Context
import android.util.Log
import com.frozo.ambientscribe.ai.ASRService
import com.frozo.ambientscribe.audio.AudioData
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages audio transcription pipeline with error handling and retries
 */
class AudioTranscriptionPipeline(
    private val context: Context,
    private val asrService: ASRService,
    private val audioCapture: AudioCapture,
    private val audioProcessingConfig: AudioProcessingConfig,
    private val metricsCollector: MetricsCollector? = null
) {

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    /**
     * Transcription result
     */
    sealed class TranscriptionResult {
        data class Success(
            val text: String,
            val confidence: Float,
            val duration: Long,
            val speakerId: Int
        ) : TranscriptionResult()

        data class Error(
            val message: String,
            val cause: Throwable? = null
        ) : TranscriptionResult()
    }

    /**
     * Start transcription pipeline
     */
    fun startTranscription(): Flow<TranscriptionResult> = flow {
        var retryCount = 0

        while (retryCount < MAX_RETRIES) {
            try {
                // Initialize audio capture
                if (!audioCapture.initialize()) {
                    throw IllegalStateException("Failed to initialize audio capture")
                }

                // Start audio capture
                audioCapture.getAudioFlow()
                    .collect { audioData ->
                        // Process audio data
                        val result = processAudioData(audioData)
                        emit(result)

                        // Log metrics
                        if (result is TranscriptionResult.Success) {
                            logTranscriptionMetrics(result)
                        }
                    }

                break // Success, exit retry loop

            } catch (e: Exception) {
                Timber.e(e, "Error in transcription pipeline")
                retryCount++

                if (retryCount >= MAX_RETRIES) {
                    emit(TranscriptionResult.Error("Max retries exceeded: ${e.message}"))
                } else {
                    // Wait before retry
                    kotlinx.coroutines.delay(RETRY_DELAY_MS)
                    Timber.w("Retrying transcription (attempt $retryCount)")
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Process audio data
     */
    private suspend fun processAudioData(audioData: AudioData): TranscriptionResult {
        return withContext(Dispatchers.Default) {
            try {
                // Save audio to temp file
                val tempFile = File.createTempFile("audio_", ".raw", context.cacheDir)
                tempFile.outputStream().use { output ->
                    for (sample in audioData.samples) {
                        output.write(sample.toByte().toInt())
                        output.write((sample.toInt() shr 8).toByte().toInt())
                    }
                }

                // Get audio config
                val config = audioProcessingConfig.createAudioConfig()

                // Transcribe audio
                val result = asrService.transcribeAudio(
                    audioFile = tempFile,
                    noiseSuppressionEnabled = config["noiseSuppression"] ?: true,
                    echoCancellationEnabled = config["echoCancellation"] ?: true,
                    automaticGainControlEnabled = config["automaticGainControl"] ?: true
                )

                // Clean up temp file
                tempFile.delete()

                TranscriptionResult.Success(
                    text = result.text,
                    confidence = result.confidence,
                    duration = result.duration,
                    speakerId = result.speakerId
                )

            } catch (e: Exception) {
                Timber.e(e, "Error processing audio data")
                TranscriptionResult.Error("Audio processing error: ${e.message}", e)
            }
        }
    }

    /**
     * Log transcription metrics
     */
    private fun logTranscriptionMetrics(result: TranscriptionResult.Success) {
        metricsCollector?.let {
            it.recordEvent(
                "transcription_success",
                mapOf(
                    "confidence" to result.confidence.toString(),
                    "duration" to result.duration.toString(),
                    "speaker_id" to result.speakerId.toString()
                )
            )
        }
    }

    /**
     * Stop transcription
     */
    suspend fun stopTranscription() {
        try {
            audioCapture.stopRecording()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping transcription")
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            audioCapture.cleanup()
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }
}
