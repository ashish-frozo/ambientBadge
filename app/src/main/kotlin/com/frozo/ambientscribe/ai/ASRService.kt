package com.frozo.ambientscribe.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Service for automatic speech recognition using CTranslate2 Whisper
 */
class ASRService(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "models/whisper_tiny_int8.bin"
        private const val VOCAB_FILE = "vocab/whisper_vocab.json"
        private const val CONFIG_FILE = "config/whisper_config.json"
    }

    private var isInitialized = false
    private var nativeHandle: Long = 0

    /**
     * Transcription result
     */
    data class TranscriptionResult(
        val text: String,
        val confidence: Float,
        val duration: Long,
        val speakerId: Int
    )

    /**
     * Initialize ASR service
     */
    suspend fun initialize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                return@withContext Result.success(true)
            }

            // Load model
            val modelFile = File(context.filesDir, MODEL_FILE)
            if (!modelFile.exists()) {
                context.assets.open(MODEL_FILE).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Load vocabulary
            val vocabFile = File(context.filesDir, VOCAB_FILE)
            if (!vocabFile.exists()) {
                context.assets.open(VOCAB_FILE).use { input ->
                    vocabFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Load config
            val configFile = File(context.filesDir, CONFIG_FILE)
            if (!configFile.exists()) {
                context.assets.open(CONFIG_FILE).use { input ->
                    configFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Initialize native model
            nativeHandle = initializeNative(
                modelFile.absolutePath,
                vocabFile.absolutePath,
                configFile.absolutePath
            )
            isInitialized = true

            Result.success(true)
        } catch (e: Exception) {
            Timber.e(e, "Error initializing ASR service")
            Result.failure(e)
        }
    }

    /**
     * Transcribe audio file
     */
    suspend fun transcribeAudio(
        audioFile: File,
        noiseSuppressionEnabled: Boolean = true,
        echoCancellationEnabled: Boolean = true,
        automaticGainControlEnabled: Boolean = true
    ): TranscriptionResult = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized) {
                throw IllegalStateException("ASR service not initialized")
            }

            // Transcribe using native model
            val result = transcribeNative(
                nativeHandle,
                audioFile.absolutePath,
                noiseSuppressionEnabled,
                echoCancellationEnabled,
                automaticGainControlEnabled
            )

            // Parse result
            val parts = result.split("|")
            if (parts.size != 4) {
                throw IllegalStateException("Invalid transcription result format")
            }

            TranscriptionResult(
                text = parts[0],
                confidence = parts[1].toFloat(),
                duration = parts[2].toLong(),
                speakerId = parts[3].toInt()
            )

        } catch (e: Exception) {
            Timber.e(e, "Error transcribing audio")
            throw e
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        if (isInitialized) {
            cleanupNative(nativeHandle)
            isInitialized = false
            nativeHandle = 0
        }
    }

    private external fun initializeNative(
        modelPath: String,
        vocabPath: String,
        configPath: String
    ): Long

    private external fun transcribeNative(
        handle: Long,
        audioPath: String,
        noiseSuppressionEnabled: Boolean,
        echoCancellationEnabled: Boolean,
        automaticGainControlEnabled: Boolean
    ): String

    private external fun cleanupNative(handle: Long)

    init {
        System.loadLibrary("whisper")
    }
}
