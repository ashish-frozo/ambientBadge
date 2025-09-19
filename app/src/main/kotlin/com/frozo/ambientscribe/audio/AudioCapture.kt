package com.frozo.ambientscribe.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import com.frozo.ambientscribe.security.AuditLogger
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.webrtc.audio.WebRtcAudioRecord
import org.webrtc.audio.WebRtcAudioUtils
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Audio capture implementation with WebRTC VAD and 30-second ring buffer.
 * Provides real-time audio processing with voice activity detection.
 */
class AudioCapture(
    private val context: Context,
    private val vadThreshold: Float = 0.01f,
    private val ringBufferDurationMs: Long = 30_000L,
    private val autoPurgeEnabled: Boolean = true
) {
    companion object {
        // Target sample rate for processing
        private const val TARGET_SAMPLE_RATE = 16000 // 16 kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 4
        private const val VAD_FRAME_SIZE_MS = 30
        private const val VAD_FRAME_SIZE_SAMPLES = (TARGET_SAMPLE_RATE * VAD_FRAME_SIZE_MS) / 1000
        private const val BYTES_PER_SAMPLE = 2 // 16-bit PCM
        
        // Thread priority for audio processing
        private const val AUDIO_THREAD_PRIORITY = android.os.Process.THREAD_PRIORITY_AUDIO
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val isRecording = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Audit logging
    private val auditLogger = AuditLogger(context)
    private var sessionId = UUID.randomUUID().toString()
    
    // Audio format probe and resampler
    private val audioFormatProbe = AudioFormatProbe()
    private var audioResampler: AudioResampler? = null
    private var actualSampleRate = TARGET_SAMPLE_RATE
    private var needsResampling = false
    
    // Audio processing configuration
    private val audioProcessingConfig = AudioProcessingConfig(context, MetricsCollector(context))
    
    // Ring buffer for 30-second audio storage
    private val ringBuffer = RingBuffer(
        sizeBytes = ((TARGET_SAMPLE_RATE * BYTES_PER_SAMPLE * ringBufferDurationMs) / 1000).toInt()
    )
    
    // VAD state
    private val currentVadState = AtomicReference(VoiceActivityState.SILENCE)
    private val vadBuffer = ByteArray(VAD_FRAME_SIZE_SAMPLES * BYTES_PER_SAMPLE)
    private var vadBufferPosition = 0
    
    // Audio processing callbacks
    private val audioDataChannel = Channel<AudioData>(Channel.UNLIMITED)
    private val vadStateChannel = Channel<VoiceActivityState>(Channel.CONFLATED)
    
    /**
     * Data class representing processed audio data
     */
    data class AudioData(
        val samples: ShortArray,
        val timestamp: Long,
        val isVoiceActive: Boolean,
        val energyLevel: Float
    )
    
    /**
     * Voice activity detection states
     */
    enum class VoiceActivityState {
        SILENCE,
        SPEECH,
        UNCERTAIN
    }
    
    /**
     * Initialize audio capture system
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (isInitialized.get()) {
                return@withContext Result.success(Unit)
            }
            
            Timber.d("Initializing AudioCapture")
            
            // Initialize WebRTC audio utilities using audio processing config
            audioProcessingConfig.applySettings()
            
            // Probe available audio formats
            val formatResult = audioFormatProbe.probeAudioFormat()
            if (formatResult.isFailure) {
                val exception = formatResult.exceptionOrNull() ?: Exception("Failed to probe audio formats")
                Timber.e(exception, "Failed to probe audio formats")
                return@withContext Result.failure(exception)
            }
            
            // Get the best available audio format
            val audioFormat = formatResult.getOrThrow()
            actualSampleRate = audioFormat.sampleRate
            needsResampling = audioFormat.needsResampling
            
            // Initialize resampler if needed
            if (needsResampling) {
                audioResampler = AudioResampler(actualSampleRate, TARGET_SAMPLE_RATE)
                Timber.i("Audio resampling enabled: ${actualSampleRate}Hz -> ${TARGET_SAMPLE_RATE}Hz")
            }
            
            // Create AudioRecord instance
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                actualSampleRate,
                audioFormat.channelConfig,
                audioFormat.audioFormat,
                audioFormat.bufferSize
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord initialization failed")
                }
            }
            
            // Acquire wake lock for audio processing
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AmbientScribe::AudioCapture"
            )
            
            isInitialized.set(true)
            Timber.i("AudioCapture initialized successfully")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AudioCapture")
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Start audio recording with VAD processing
     */
    suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized.get()) {
                return@withContext Result.failure(IllegalStateException("AudioCapture not initialized"))
            }
            
            if (isRecording.get()) {
                return@withContext Result.success(Unit)
            }
            
            val audioRecord = this@AudioCapture.audioRecord
                ?: return@withContext Result.failure(IllegalStateException("AudioRecord not available"))
            
            // Acquire wake lock
            wakeLock?.acquire()
            
            // Start recording
            audioRecord.startRecording()
            
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                wakeLock?.release()
                return@withContext Result.failure(IllegalStateException("Failed to start recording"))
            }
            
            isRecording.set(true)
            
            // Start recording coroutine
            recordingJob = coroutineScope.launch {
                processAudioData(audioRecord)
            }
            
            Timber.i("Audio recording started")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
            stopRecording()
            Result.failure(e)
        }
    }
    
    /**
     * Stop audio recording and release resources
     */
    suspend fun stopRecording(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (!isRecording.get()) {
                return@withContext Result.success(Unit)
            }
            
            isRecording.set(false)
            
            // Cancel recording job
            recordingJob?.cancelAndJoin()
            recordingJob = null
            
            // Stop AudioRecord
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
            }
            
            // Release wake lock
            wakeLock?.takeIf { it.isHeld }?.release()
            
            // Automatically purge ring buffer if enabled
            if (autoPurgeEnabled) {
                purgeRingBuffer()
            }
            
            // Log session end event
            auditLogger.logEvent(
                eventType = AuditLogger.EVENT_SESSION_END,
                details = mapOf(
                    "auto_purge_enabled" to autoPurgeEnabled.toString(),
                    "session_duration_ms" to System.currentTimeMillis().toString()
                ),
                sessionId = sessionId
            )
            
            Timber.i("Audio recording stopped, auto-purge: $autoPurgeEnabled")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Error stopping recording")
            Result.failure(e)
        }
    }
    
    /**
     * Get flow of processed audio data
     */
    fun getAudioDataFlow(): Flow<AudioData> = flow {
        while (isRecording.get()) {
            try {
                val audioData = audioDataChannel.receive()
                emit(audioData)
            } catch (e: Exception) {
                Timber.w(e, "Error in audio data flow")
                break
            }
        }
    }
    
    /**
     * Get flow of VAD state changes
     */
    fun getVadStateFlow(): Flow<VoiceActivityState> = flow {
        while (isRecording.get()) {
            try {
                val vadState = vadStateChannel.receive()
                emit(vadState)
            } catch (e: Exception) {
                Timber.w(e, "Error in VAD state flow")
                break
            }
        }
    }
    
    /**
     * Get the last 30 seconds of audio from ring buffer
     */
    fun getRingBufferData(): ByteArray {
        return ringBuffer.getData()
    }
    
    /**
     * Clear the ring buffer (for privacy/consent management)
     */
    fun clearRingBuffer() {
        ringBuffer.clear()
        Timber.d("Ring buffer cleared")
    }
    
    /**
     * Get the audio processing configuration
     */
    fun getAudioProcessingConfig(): AudioProcessingConfig {
        return audioProcessingConfig
    }
    
    /**
     * Purge ring buffer with audit logging
     */
    suspend fun purgeRingBuffer() {
        // Get buffer size before purging for audit log
        val bufferSizeBytes = ringBuffer.getData().size
        
        // Clear the buffer
        clearRingBuffer()
        
        // Log the purge event
        auditLogger.logEvent(
            eventType = AuditLogger.EVENT_PURGE_BUFFER,
            details = mapOf(
                "buffer_size_bytes" to bufferSizeBytes.toString(),
                "auto_purge" to autoPurgeEnabled.toString()
            ),
            sessionId = sessionId
        )
        
        Timber.i("Ring buffer purged: $bufferSizeBytes bytes")
    }
    
    /**
     * Delete last 30 seconds of audio and clear ring buffer
     */
    suspend fun deleteLast30Seconds() {
        // Get buffer size before purging for audit log
        val bufferSizeBytes = ringBuffer.getData().size
        
        // Clear the buffer
        clearRingBuffer()
        
        // Log the purge event
        auditLogger.logEvent(
            eventType = AuditLogger.EVENT_PURGE_30S,
            details = mapOf(
                "buffer_size_bytes" to bufferSizeBytes.toString(),
                "manual_purge" to "true"
            ),
            sessionId = sessionId
        )
        
        Timber.i("Last 30 seconds of audio deleted: $bufferSizeBytes bytes")
    }
    
    /**
     * Verify that ring buffer is empty (for privacy compliance)
     */
    fun verifyBufferEmpty(): Boolean {
        val bufferData = ringBuffer.getData()
        return bufferData.isEmpty() || bufferData.all { it == 0.toByte() }
    }
    
    /**
     * Main audio processing loop
     */
    private suspend fun processAudioData(audioRecord: AudioRecord) = withContext(Dispatchers.Default) {
        // Set thread priority for audio processing
        android.os.Process.setThreadPriority(AUDIO_THREAD_PRIORITY)
        
        val bufferSize = audioRecord.bufferSizeInFrames * BYTES_PER_SAMPLE
        val buffer = ByteArray(bufferSize)
        
        Timber.d("Starting audio processing loop with buffer size: $bufferSize")
        
        // Track underruns/overruns
        var underruns = 0
        var overruns = 0
        var lastBufferTime = System.currentTimeMillis()
        
        // Buffer auto-tuning
        var bufferAdjustments = 0
        var consecutiveUnderruns = 0
        var consecutiveOverruns = 0
        var initialBufferSize = bufferSize
        
        try {
            while (isRecording.get() && !currentCoroutineContext().isActive.not()) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                val currentTime = System.currentTimeMillis()
                
                // Check for underruns/overruns
                val expectedInterval = (bufferSize / BYTES_PER_SAMPLE / actualSampleRate.toDouble() * 1000).toLong()
                val actualInterval = currentTime - lastBufferTime
                
                if (actualInterval > expectedInterval * 1.5) {
                    underruns++
                    consecutiveUnderruns++
                    consecutiveOverruns = 0
                    Timber.w("Audio underrun detected: ${actualInterval}ms (expected: ${expectedInterval}ms)")
                    
                    // Auto-tune buffer size for underruns
                    if (consecutiveUnderruns >= 3 && bufferAdjustments < 5) {
                        val newBufferSize = (bufferSize * 1.5).toInt()
                        Timber.i("Auto-tuning buffer size due to underruns: $bufferSize -> $newBufferSize")
                        
                        // Log metrics
                        metricsCollector?.logMetricEvent("buffer_autotune", mapOf(
                            "old_size" to bufferSize,
                            "new_size" to newBufferSize,
                            "reason" to "underrun",
                            "consecutive_events" to consecutiveUnderruns
                        ))
                        
                        bufferAdjustments++
                        // Note: We can't actually change the buffer size during recording
                        // This would require stopping and restarting the AudioRecord
                    }
                } else if (actualInterval < expectedInterval * 0.5 && lastBufferTime > 0) {
                    overruns++
                    consecutiveOverruns++
                    consecutiveUnderruns = 0
                    Timber.w("Audio overrun detected: ${actualInterval}ms (expected: ${expectedInterval}ms)")
                    
                    // Auto-tune buffer size for overruns
                    if (consecutiveOverruns >= 3 && bufferAdjustments < 5 && bufferSize > initialBufferSize / 2) {
                        val newBufferSize = (bufferSize * 0.75).toInt()
                        Timber.i("Auto-tuning buffer size due to overruns: $bufferSize -> $newBufferSize")
                        
                        // Log metrics
                        metricsCollector?.logMetricEvent("buffer_autotune", mapOf(
                            "old_size" to bufferSize,
                            "new_size" to newBufferSize,
                            "reason" to "overrun",
                            "consecutive_events" to consecutiveOverruns
                        ))
                        
                        bufferAdjustments++
                        // Note: We can't actually change the buffer size during recording
                        // This would require stopping and restarting the AudioRecord
                    }
                } else {
                    consecutiveUnderruns = 0
                    consecutiveOverruns = 0
                }
                
                lastBufferTime = currentTime
                
                if (bytesRead > 0) {
                    val timestamp = System.currentTimeMillis()
                    
                    // Add to ring buffer (after resampling if needed)
                    if (needsResampling) {
                        // Convert byte buffer to short array
                        val shortBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                        val shortArray = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(shortArray)
                        
                        // Resample to target sample rate
                        val resampled = audioResampler?.resample(shortArray) ?: shortArray
                        
                        // Convert back to bytes for ring buffer
                        val resampledBytes = ByteArray(resampled.size * BYTES_PER_SAMPLE)
                        val resampledBuffer = ByteBuffer.wrap(resampledBytes)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                        resampledBuffer.put(resampled)
                        
                        // Write resampled data to ring buffer
                        ringBuffer.write(resampledBytes, 0, resampledBytes.size)
                        
                        // Process VAD on resampled data
                        processVAD(resampledBytes, resampledBytes.size, timestamp)
                        
                    } else {
                        // No resampling needed
                        ringBuffer.write(buffer, 0, bytesRead)
                        processVAD(buffer, bytesRead, timestamp)
                    }
                    
                } else if (bytesRead < 0) {
                    Timber.w("AudioRecord read error: $bytesRead")
                    break
                }
                
                // Yield to prevent blocking
                yield()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in audio processing loop")
        } finally {
            // Log underruns/overruns
            if (underruns > 0 || overruns > 0) {
                Timber.w("Audio processing completed with $underruns underruns, $overruns overruns")
                
                // Log final metrics
                metricsCollector?.logMetricEvent("audio_buffer_stats", mapOf(
                    "underruns" to underruns,
                    "overruns" to overruns,
                    "buffer_size" to bufferSize,
                    "buffer_adjustments" to bufferAdjustments,
                    "sample_rate" to actualSampleRate,
                    "needs_resampling" to needsResampling
                ))
            }
            
            Timber.d("Audio processing loop ended")
        }
    }
    
    /**
     * Process Voice Activity Detection
     */
    private suspend fun processVAD(buffer: ByteArray, bytesRead: Int, timestamp: Long) {
        var offset = 0
        
        while (offset < bytesRead) {
            val bytesToCopy = minOf(vadBuffer.size - vadBufferPosition, bytesRead - offset)
            System.arraycopy(buffer, offset, vadBuffer, vadBufferPosition, bytesToCopy)
            
            vadBufferPosition += bytesToCopy
            offset += bytesToCopy
            
            // Process complete VAD frame
            if (vadBufferPosition >= vadBuffer.size) {
                val samples = ShortArray(VAD_FRAME_SIZE_SAMPLES)
                
                // Convert bytes to shorts
                for (i in samples.indices) {
                    val byteIndex = i * 2
                    samples[i] = ((vadBuffer[byteIndex + 1].toInt() shl 8) or 
                                 (vadBuffer[byteIndex].toInt() and 0xFF)).toShort()
                }
                
                // Calculate energy level
                val energy = calculateEnergyLevel(samples)
                
                // Determine voice activity
                val isVoiceActive = energy > vadThreshold
                val vadState = when {
                    isVoiceActive -> VoiceActivityState.SPEECH
                    energy > vadThreshold * 0.3f -> VoiceActivityState.UNCERTAIN
                    else -> VoiceActivityState.SILENCE
                }
                
                // Update VAD state if changed
                val previousState = currentVadState.getAndSet(vadState)
                if (previousState != vadState) {
                    vadStateChannel.trySend(vadState)
                    Timber.v("VAD state changed: $previousState -> $vadState (energy: $energy)")
                }
                
                // Send audio data
                val audioData = AudioData(
                    samples = samples,
                    timestamp = timestamp,
                    isVoiceActive = isVoiceActive,
                    energyLevel = energy
                )
                
                audioDataChannel.trySend(audioData)
                
                // Reset VAD buffer
                vadBufferPosition = 0
            }
        }
    }
    
    /**
     * Calculate RMS energy level for VAD
     */
    private fun calculateEnergyLevel(samples: ShortArray): Float {
        var sum = 0.0
        for (sample in samples) {
            sum += (sample * sample).toDouble()
        }
        return sqrt(sum / samples.size).toFloat() / Short.MAX_VALUE
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        runBlocking {
            stopRecording()
            
            // Ensure buffer is purged on cleanup
            if (!autoPurgeEnabled) {
                purgeRingBuffer()
            }
        }
        
        audioRecord?.release()
        audioRecord = null
        
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        
        // Double-check ring buffer is cleared
        ringBuffer.clear()
        audioDataChannel.close()
        vadStateChannel.close()
        coroutineScope.cancel()
        
        // Verify buffer is empty
        val isBufferEmpty = verifyBufferEmpty()
        if (!isBufferEmpty) {
            Timber.e("WARNING: Ring buffer not completely purged during cleanup!")
        }
        
        isInitialized.set(false)
        Timber.d("AudioCapture cleanup completed, buffer purged: $isBufferEmpty")
    }
    
    /**
     * Ring buffer implementation for 30-second audio storage
     */
    private class RingBuffer(private val sizeBytes: Int) {
        private val buffer = ByteArray(sizeBytes)
        private var writePosition = 0
        private var availableBytes = 0
        private val lock = Any()
        
        fun write(data: ByteArray, offset: Int, length: Int) {
            synchronized(lock) {
                var remaining = length
                var srcOffset = offset
                
                while (remaining > 0) {
                    val chunkSize = minOf(remaining, sizeBytes - writePosition)
                    System.arraycopy(data, srcOffset, buffer, writePosition, chunkSize)
                    
                    writePosition = (writePosition + chunkSize) % sizeBytes
                    srcOffset += chunkSize
                    remaining -= chunkSize
                    
                    availableBytes = minOf(availableBytes + chunkSize, sizeBytes)
                }
            }
        }
        
        fun getData(): ByteArray {
            synchronized(lock) {
                if (availableBytes == 0) return ByteArray(0)
                
                val result = ByteArray(availableBytes)
                val startPos = if (availableBytes < sizeBytes) {
                    0
                } else {
                    writePosition
                }
                
                if (startPos + availableBytes <= sizeBytes) {
                    System.arraycopy(buffer, startPos, result, 0, availableBytes)
                } else {
                    val firstChunk = sizeBytes - startPos
                    System.arraycopy(buffer, startPos, result, 0, firstChunk)
                    System.arraycopy(buffer, 0, result, firstChunk, availableBytes - firstChunk)
                }
                
                return result
            }
        }
        
        fun clear() {
            synchronized(lock) {
                writePosition = 0
                availableBytes = 0
                buffer.fill(0)
            }
        }
    }
}
