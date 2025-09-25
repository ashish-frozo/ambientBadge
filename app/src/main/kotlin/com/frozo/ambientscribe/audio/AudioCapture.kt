package com.frozo.ambientscribe.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles audio capture and processing
 */
class AudioCapture(private val context: Context) {

    companion object {
        private const val TAG = "AudioCapture"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        private const val RING_BUFFER_DURATION = 30 // seconds
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var ringBuffer: ByteBuffer? = null
    private var ringBufferSize = 0
    private var ringBufferPosition = 0
    private var bytesBuffered = 0

    /**
     * Initialize audio capture
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // Calculate buffer sizes
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

            // Create ring buffer
            ringBufferSize = SAMPLE_RATE * 2 * RING_BUFFER_DURATION // 16-bit = 2 bytes per sample
            ringBuffer = ByteBuffer.allocateDirect(ringBufferSize).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }
            ringBufferPosition = 0
            bytesBuffered = 0

            // Create AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            // Set thread priority
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio capture: ${e.message}", e)
            false
        }
    }
    
    /**
     * Start recording
     */
    suspend fun startRecording(): Boolean {
        val record = audioRecord ?: return false
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            return false
        }
        if (isRecording) {
            return false
        }
        isRecording = true
        return true
    }

    fun getAudioFlow(): Flow<AudioData> = flow {
        try {
            if (!isRecording) {
                return@flow
            }

            val record = audioRecord ?: throw IllegalStateException("AudioRecord not initialized")
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord not initialized")
            }

            isRecording = true
            record.startRecording()

            val buffer = ByteArray(record.bufferSizeInFrames * 2) // 16-bit = 2 bytes per frame
            while (isRecording) {
                val bytesRead = record.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    // Update ring buffer
                    updateRingBuffer(buffer, bytesRead)
                    // Emit buffer
                    val shortBuffer = ShortArray(bytesRead / 2)
                    for (i in 0 until bytesRead step 2) {
                        shortBuffer[i / 2] = ((buffer[i + 1].toInt() and 0xFF) shl 8 or
                                (buffer[i].toInt() and 0xFF)).toShort()
                    }
                    val energyLevel = calculateEnergyLevel(shortBuffer)
                    val isVoiceActive = energyLevel > 0.1f // Simple threshold-based VAD
                    emit(AudioData(
                        samples = shortBuffer,
                        energyLevel = energyLevel,
                        isVoiceActive = isVoiceActive,
                        timestamp = System.currentTimeMillis()
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during recording: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Stop recording
     */
    suspend fun stopRecording() = withContext(Dispatchers.IO) {
        try {
            isRecording = false
            audioRecord?.stop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            false
        }
    }
    
    /**
     * Save last 30 seconds
     */
    suspend fun saveLast30Seconds(outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = getRingBufferData()
            if (data.isEmpty()) {
                return@withContext false
            }

            FileOutputStream(outputFile).use { output ->
                // Write WAV header sized to actual captured data
                writeWavHeader(output, data.size)
                output.write(data)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last 30 seconds: ${e.message}", e)
            false
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            audioRecord?.release()
            audioRecord = null
            ringBuffer = null
            ringBufferPosition = 0
            bytesBuffered = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }
    
    /**
     * Clear ring buffer
     */
    fun clearRingBuffer() {
        ringBuffer?.let { buffer ->
            buffer.clear()
            buffer.limit(buffer.capacity())
        }
        ringBufferPosition = 0
        bytesBuffered = 0
    }

    /**
     * Delete last 30 seconds
     */
    fun deleteLast30Seconds() {
        clearRingBuffer()
    }

    /**
     * Verify buffer is empty
     */
    fun verifyBufferEmpty(): Boolean = bytesBuffered == 0

    /**
     * Get ring buffer data
     */
    fun getRingBufferData(): ByteArray {
        val buffer = ringBuffer ?: return ByteArray(0)
        if (bytesBuffered == 0) {
            return ByteArray(0)
        }

        val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val result = ByteArray(bytesBuffered)

        if (bytesBuffered < ringBufferSize) {
            duplicate.clear()
            duplicate.limit(bytesBuffered)
            duplicate.get(result)
        } else {
            val tailBytes = ringBufferSize - ringBufferPosition
            if (tailBytes > 0) {
                duplicate.clear()
                duplicate.position(ringBufferPosition)
                duplicate.limit(ringBufferPosition + tailBytes)
                duplicate.get(result, 0, tailBytes)
            }
            if (ringBufferPosition > 0) {
                duplicate.clear()
                duplicate.limit(ringBufferPosition)
                duplicate.get(result, tailBytes, ringBufferPosition)
            }
        }

        return result
    }

    private fun updateRingBuffer(buffer: ByteArray, size: Int) {
        val ring = ringBuffer ?: return
        if (ringBufferSize == 0) {
            return
        }

        if (size >= ringBufferSize) {
            val offset = size - ringBufferSize
            ring.clear()
            ring.put(buffer, offset, ringBufferSize)
            ringBufferPosition = 0
            bytesBuffered = ringBufferSize
            return
        }

        ring.limit(ring.capacity())
        ring.position(ringBufferPosition)

        val remaining = ring.remaining()
        if (size <= remaining) {
            ring.put(buffer, 0, size)
        } else {
            ring.put(buffer, 0, remaining)
            ring.position(0)
            ring.put(buffer, remaining, size - remaining)
        }

        ringBufferPosition = (ringBufferPosition + size) % ringBufferSize
        bytesBuffered = minOf(ringBufferSize, bytesBuffered + size)
    }

    private fun calculateEnergyLevel(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0.0f
        
        var sum = 0.0
        for (sample in samples) {
            val normalized = sample / 32768.0 // Normalize to [-1.0, 1.0]
            sum += normalized * normalized
        }
        return (sum / samples.size).toFloat()
    }

    private fun writeWavHeader(output: FileOutputStream, dataSize: Int) {
        // RIFF header
        output.write("RIFF".toByteArray()) // ChunkID
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(36 + dataSize).array()) // ChunkSize
        output.write("WAVE".toByteArray()) // Format

        // fmt subchunk
        output.write("fmt ".toByteArray()) // Subchunk1ID
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(16).array()) // Subchunk1Size
        output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1).array()) // AudioFormat (PCM)
        output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1).array()) // NumChannels (Mono)
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(SAMPLE_RATE).array()) // SampleRate
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(SAMPLE_RATE * 2).array()) // ByteRate
        output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(2).array()) // BlockAlign
        output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(16).array()) // BitsPerSample

        // data subchunk
        output.write("data".toByteArray()) // Subchunk2ID
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(dataSize).array()) // Subchunk2Size
    }
}
