package com.frozo.ambientscribe.transcription

import android.content.Context
import android.content.res.AssetManager
import com.frozo.ambientscribe.performance.PerformanceManager
import com.frozo.ambientscribe.performance.ThermalManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.log10
import kotlin.math.max

/**
 * Automatic Speech Recognition service using CTranslate2 Whisper tiny int8 model.
 * Provides real-time audio-to-text transcription with confidence scoring.
 */
class ASRService(
    private val context: Context,
    private val modelName: String = "whisper-tiny-int8",
    private val confidenceThreshold: Float = 0.6f,
    private val performanceManager: PerformanceManager? = null
) : ThermalManager.ThermalStateListener {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_DURATION_MS = 3000L // 3 second chunks
        private const val CHUNK_SIZE_SAMPLES = (SAMPLE_RATE * CHUNK_DURATION_MS / 1000).toInt()
        private const val OVERLAP_MS = 500L // 0.5 second overlap
        private const val OVERLAP_SAMPLES = (SAMPLE_RATE * OVERLAP_MS / 1000).toInt()
        
        // Confidence score thresholds
        private const val HIGH_CONFIDENCE = 0.8f
        private const val MEDIUM_CONFIDENCE = 0.6f
        
        // Model file names
        private const val ENCODER_MODEL = "whisper-tiny-encoder-int8.ct2"
        private const val DECODER_MODEL = "whisper-tiny-decoder-int8.ct2"
        private const val TOKENIZER_CONFIG = "tokenizer.json"
        private const val VOCAB_FILE = "vocab.txt"
        
        // Native library loading
        init {
            try {
                System.loadLibrary("ctranslate2")
                System.loadLibrary("whisper_android")
                Timber.d("CTranslate2 native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load CTranslate2 native libraries")
            }
        }
    }

    private val isInitialized = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var modelPath: String? = null
    private var nativeHandle: Long = 0L
    
    // Adaptive threading parameters
    private var threadCount = 4
    private var contextSize = 3000
    
    // Audio buffer for chunked processing
    private val audioBuffer = mutableListOf<Short>()
    private val bufferLock = Any()
    
    // Transcription results
    private val transcriptionChannel = Channel<TranscriptionResult>(Channel.UNLIMITED)
    private val currentTranscription = AtomicReference("")
    
    // Error handling
    private val _errorFlow = MutableSharedFlow<ASRError>(extraBufferCapacity = 10)
    val errorFlow = _errorFlow.asSharedFlow()
    private var lastError: ASRError? = null
    
    /**
     * Data class representing transcription results with confidence scoring
     */
    data class TranscriptionResult(
        val text: String,
        val confidence: Float,
        val confidenceLevel: ConfidenceLevel,
        val timestamp: Long,
        val isPartial: Boolean = false,
        val wordTimestamps: List<WordTimestamp> = emptyList()
    )
    
    /**
     * Word-level timestamp information
     */
    data class WordTimestamp(
        val word: String,
        val startTime: Float,
        val endTime: Float,
        val confidence: Float
    )
    
    /**
     * Confidence level classification
     */
    enum class ConfidenceLevel {
        HIGH,    // ≥ 0.8
        MEDIUM,  // 0.6 - 0.79
        LOW      // < 0.6
    }
    
    /**
     * Initialize the ASR service and load the Whisper model
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized.get()) {
                return@withContext Result.success(Unit)
            }
            
            Timber.d("Initializing ASRService with model: $modelName")
            
            // Initialize performance management
            performanceManager?.let { pm ->
                pm.initialize()
                
                // Register for thermal updates
                pm.getThermalStateFlow().collect {
                    // This will be collected in a separate coroutine
                }
                
                // Get initial performance recommendations
                threadCount = pm.getRecommendedThreadCount()
                contextSize = pm.getRecommendedContextSize()
                
                Timber.d("Initial performance settings - threads: $threadCount, context size: $contextSize")
            }
            
            // Extract model files from assets
            val modelDir = extractModelFromAssets()
            modelPath = modelDir
            
            // Initialize native Whisper model with adaptive threading
            nativeHandle = initializeNativeModel(modelDir, threadCount, contextSize)
            
            if (nativeHandle == 0L) {
                throw RuntimeException("Failed to initialize native Whisper model")
            }
            
            isInitialized.set(true)
            Timber.i("ASRService initialized successfully with $threadCount threads")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize ASRService")
            val error = ASRError.InitializationError(
                message = "Failed to initialize ASR service: ${e.message}",
                cause = e
            )
            emitError(error)
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Process audio data and generate transcriptions
     */
    suspend fun processAudio(audioData: ShortArray): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized.get()) {
                return@withContext Result.failure(IllegalStateException("ASRService not initialized"))
            }
            
            synchronized(bufferLock) {
                audioBuffer.addAll(audioData.toList())
            }
            
            // Process chunks when we have enough data
            if (audioBuffer.size >= CHUNK_SIZE_SAMPLES) {
                processAudioChunk()
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Error processing audio data")
            val error = ASRError.AudioInputError(
                message = "Failed to process audio data: ${e.message}",
                cause = e
            )
            emitError(error)
            Result.failure(e)
        }
    }
    
    /**
     * Get flow of transcription results
     */
    fun getTranscriptionFlow(): Flow<TranscriptionResult> = flow {
        while (isInitialized.get()) {
            try {
                val result = transcriptionChannel.receive()
                emit(result)
            } catch (e: Exception) {
                Timber.w(e, "Error in transcription flow")
                break
            }
        }
    }
    
    /**
     * Get the current accumulated transcription
     */
    fun getCurrentTranscription(): String {
        return currentTranscription.get()
    }
    
    /**
     * Clear the current transcription buffer
     */
    fun clearTranscription() {
        currentTranscription.set("")
        synchronized(bufferLock) {
            audioBuffer.clear()
        }
        Timber.d("Transcription buffer cleared")
    }
    
    /**
     * Process a chunk of audio data through the Whisper model
     */
    private suspend fun processAudioChunk() = withContext(Dispatchers.Default) {
        if (isProcessing.get()) {
            return@withContext
        }
        
        isProcessing.set(true)
        
        try {
            val chunk: ShortArray
            synchronized(bufferLock) {
                if (audioBuffer.size < CHUNK_SIZE_SAMPLES) {
                    isProcessing.set(false)
                    return@withContext
                }
                
                // Extract chunk with overlap
                val chunkSize = minOf(CHUNK_SIZE_SAMPLES, audioBuffer.size)
                chunk = audioBuffer.take(chunkSize).toShortArray()
                
                // Remove processed samples, keeping overlap
                val removeCount = maxOf(0, chunkSize - OVERLAP_SAMPLES)
                repeat(removeCount) {
                    if (audioBuffer.isNotEmpty()) {
                        audioBuffer.removeAt(0)
                    }
                }
            }
            
            // Convert to float array for model input
            val floatArray = chunk.map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()
            
            // Run inference
            val result = runInference(floatArray)
            
            if (result.text.isNotBlank()) {
                // Update current transcription
                val current = currentTranscription.get()
                val updated = if (current.isEmpty()) {
                    result.text
                } else {
                    "$current ${result.text}"
                }
                currentTranscription.set(updated)
                
                // Send result
                transcriptionChannel.trySend(result)
                
                Timber.d("Transcription: '${result.text}' (confidence: ${result.confidence})")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error processing audio chunk")
            
            // Determine error type based on exception
            val error = when {
                e.message?.contains("thermal", ignoreCase = true) == true -> {
                    ASRError.ThermalError(cause = e)
                }
                e.message?.contains("network", ignoreCase = true) == true -> {
                    ASRError.NetworkError(cause = e)
                }
                e.message?.contains("decoder", ignoreCase = true) == true ||
                e.message?.contains("model", ignoreCase = true) == true -> {
                    ASRError.DecoderError(cause = e)
                }
                else -> {
                    ASRError.fromException(e)
                }
            }
            
            emitError(error)
        } finally {
            isProcessing.set(false)
        }
    }
    
    /**
     * Run Whisper inference on audio data
     */
    private suspend fun runInference(audioData: FloatArray): TranscriptionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Apply current performance settings
            val currentThreads = threadCount
            val currentCtxSize = contextSize
            
            // Call native inference method with adaptive parameters
            val nativeResult = nativeInference(nativeHandle, audioData, currentThreads, currentCtxSize)
            
            val text = nativeResult.text.trim()
            val confidence = calculateConfidence(nativeResult.logProbs)
            val confidenceLevel = when {
                confidence >= HIGH_CONFIDENCE -> ConfidenceLevel.HIGH
                confidence >= MEDIUM_CONFIDENCE -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }
            
            // Extract word timestamps if available
            val wordTimestamps = extractWordTimestamps(nativeResult.alignments)
            
            // Log inference performance
            val duration = System.currentTimeMillis() - startTime
            Timber.v("Inference completed in ${duration}ms with $currentThreads threads, " +
                    "ctx size: $currentCtxSize, confidence: $confidence")
            
            TranscriptionResult(
                text = text,
                confidence = confidence,
                confidenceLevel = confidenceLevel,
                timestamp = startTime,
                wordTimestamps = wordTimestamps
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Native inference failed")
            
            // Create appropriate error based on exception
            val error = when {
                e.message?.contains("thermal", ignoreCase = true) == true -> {
                    ASRError.ThermalError(
                        message = "Device overheating, reducing transcription quality",
                        cause = e
                    )
                }
                else -> {
                    ASRError.DecoderError(
                        message = "Transcription engine error: ${e.message}",
                        cause = e
                    )
                }
            }
            
            emitError(error)
            
            TranscriptionResult(
                text = "",
                confidence = 0f,
                confidenceLevel = ConfidenceLevel.LOW,
                timestamp = startTime
            )
        }
    }
    
    /**
     * Calculate confidence score from log probabilities
     */
    private fun calculateConfidence(logProbs: FloatArray): Float {
        if (logProbs.isEmpty()) return 0f
        
        // Convert log probabilities to confidence score
        val avgLogProb = logProbs.average().toFloat()
        val confidence = kotlin.math.exp(avgLogProb.toDouble()).toFloat()
        
        // Normalize to 0-1 range with sigmoid-like function
        return 1f / (1f + kotlin.math.exp((-confidence * 10f + 5f).toDouble())).toFloat()
    }
    
    /**
     * Extract word-level timestamps from alignment data
     */
    private fun extractWordTimestamps(alignments: Array<NativeAlignment>): List<WordTimestamp> {
        return alignments.map { alignment ->
            WordTimestamp(
                word = alignment.word,
                startTime = alignment.startTime,
                endTime = alignment.endTime,
                confidence = alignment.confidence
            )
        }
    }
    
    /**
     * Extract model files from assets to internal storage
     */
    private suspend fun extractModelFromAssets(): String = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models/$modelName")
        
        if (modelDir.exists() && isModelComplete(modelDir)) {
            Timber.d("Model already extracted: ${modelDir.absolutePath}")
            return@withContext modelDir.absolutePath
        }
        
        modelDir.mkdirs()
        
        val modelFiles = listOf(ENCODER_MODEL, DECODER_MODEL, TOKENIZER_CONFIG, VOCAB_FILE)
        val assetManager = context.assets
        
        try {
            for (fileName in modelFiles) {
                val assetPath = "models/$modelName/$fileName"
                val outputFile = File(modelDir, fileName)
                
                Timber.d("Extracting $assetPath to ${outputFile.absolutePath}")
                
                assetManager.open(assetPath).use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            Timber.i("Model extracted successfully to: ${modelDir.absolutePath}")
            modelDir.absolutePath
            
        } catch (e: IOException) {
            Timber.e(e, "Failed to extract model from assets")
            val error = ASRError.InitializationError(
                message = "Failed to extract model files: ${e.message}",
                cause = e
            )
            emitError(error)
            throw e
        }
    }
    
    /**
     * Check if all required model files are present
     */
    private fun isModelComplete(modelDir: File): Boolean {
        val requiredFiles = listOf(ENCODER_MODEL, DECODER_MODEL, TOKENIZER_CONFIG, VOCAB_FILE)
        return requiredFiles.all { fileName ->
            val file = File(modelDir, fileName)
            file.exists() && file.length() > 0
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        if (nativeHandle != 0L) {
            releaseNativeModel(nativeHandle)
            nativeHandle = 0L
        }
        
        synchronized(bufferLock) {
            audioBuffer.clear()
        }
        
        transcriptionChannel.close()
        coroutineScope.cancel()
        isInitialized.set(false)
        
        Timber.d("ASRService cleanup completed")
    }
    
    /**
     * Handle thermal state changes
     */
    override fun onThermalStateChanged(state: ThermalManager.ThermalState) {
        // Update thread count and context size based on thermal state
        val newThreadCount = state.recommendedThreads
        val newContextSize = state.recommendedContextSize
        
        // Only log if values changed
        if (threadCount != newThreadCount || contextSize != newContextSize) {
            Timber.d("Thermal state changed: level=${state.thermalLevel}, " +
                    "threads: $threadCount -> $newThreadCount, " +
                    "context: $contextSize -> $newContextSize")
            
            threadCount = newThreadCount
            contextSize = newContextSize
            
            // In a real implementation, we would update the native model parameters
            if (nativeHandle != 0L && isInitialized.get()) {
                // updateNativeModelParameters(nativeHandle, threadCount, contextSize)
            }
            
            // Emit thermal error for severe thermal state
            if (state.thermalLevel == 2) { // SEVERE
                val error = ASRError.ThermalError(
                    message = "Device overheating, transcription quality reduced",
                    errorCode = ASRError.ERROR_CPU_THERMAL
                )
                emitError(error)
            }
        }
    }
    
    /**
     * Emit an error to the error flow
     */
    private fun emitError(error: ASRError) {
        lastError = error
        coroutineScope.launch {
            _errorFlow.emit(error)
        }
        Timber.w("ASR error: ${error.message} [${error.errorCode}]")
    }
    
    // Native method declarations (would be implemented in C++)
    private external fun initializeNativeModel(modelPath: String, threadCount: Int = 4, contextSize: Int = 3000): Long
    private external fun nativeInference(handle: Long, audioData: FloatArray, threadCount: Int = 4, contextSize: Int = 3000): NativeInferenceResult
    private external fun releaseNativeModel(handle: Long)
    // private external fun updateNativeModelParameters(handle: Long, threadCount: Int, contextSize: Int): Boolean
    
    /**
     * Native inference result structure
     */
    private data class NativeInferenceResult(
        val text: String,
        val logProbs: FloatArray,
        val alignments: Array<NativeAlignment>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NativeInferenceResult

            if (text != other.text) return false
            if (!logProbs.contentEquals(other.logProbs)) return false
            if (!alignments.contentEquals(other.alignments)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + logProbs.contentHashCode()
            result = 31 * result + alignments.contentHashCode()
            return result
        }
    }
    
    /**
     * Native alignment structure for word timestamps
     */
    private data class NativeAlignment(
        val word: String,
        val startTime: Float,
        val endTime: Float,
        val confidence: Float
    )
}
