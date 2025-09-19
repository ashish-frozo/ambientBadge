package com.frozo.ambientscribe.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Probes available audio input formats and determines the best format to use
 * with fallback options and resampling when necessary.
 */
class AudioFormatProbe {
    companion object {
        // Preferred audio format
        const val PREFERRED_SAMPLE_RATE = 16000 // 16 kHz
        const val PREFERRED_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val PREFERRED_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Fallback audio formats (in order of preference)
        val FALLBACK_SAMPLE_RATES = arrayOf(
            48000, // 48 kHz
            44100, // 44.1 kHz
            32000, // 32 kHz
            22050, // 22.05 kHz
            8000   // 8 kHz
        )
        
        // Buffer size multiplier for stable recording
        const val BUFFER_SIZE_FACTOR = 4
    }
    
    /**
     * Audio format configuration result
     */
    data class AudioFormatResult(
        val sampleRate: Int,
        val channelConfig: Int,
        val audioFormat: Int,
        val bufferSize: Int,
        val needsResampling: Boolean,
        val targetSampleRate: Int = PREFERRED_SAMPLE_RATE
    )
    
    /**
     * Probe available audio formats and find the best match
     */
    suspend fun probeAudioFormat(): Result<AudioFormatResult> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Probing audio formats")
            
            // Try preferred format first
            val preferredResult = tryAudioFormat(PREFERRED_SAMPLE_RATE)
            if (preferredResult != null) {
                Timber.i("Using preferred audio format: $PREFERRED_SAMPLE_RATE Hz")
                return@withContext Result.success(preferredResult)
            }
            
            // Try fallback formats
            for (sampleRate in FALLBACK_SAMPLE_RATES) {
                val result = tryAudioFormat(sampleRate)
                if (result != null) {
                    Timber.w("Using fallback audio format: $sampleRate Hz (will resample to ${PREFERRED_SAMPLE_RATE} Hz)")
                    return@withContext Result.success(result)
                }
            }
            
            // No compatible format found
            Timber.e("No compatible audio format found")
            return@withContext Result.failure(IllegalStateException("No compatible audio format found"))
            
        } catch (e: Exception) {
            Timber.e(e, "Error probing audio formats")
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Try to initialize AudioRecord with a specific sample rate
     */
    private fun tryAudioFormat(sampleRate: Int): AudioFormatResult? {
        try {
            // Get minimum buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                PREFERRED_CHANNEL_CONFIG,
                PREFERRED_AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || 
                minBufferSize == AudioRecord.ERROR) {
                Timber.d("Unsupported audio format: $sampleRate Hz")
                return null
            }
            
            // Calculate buffer size
            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR
            
            // Try to create AudioRecord
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                PREFERRED_CHANNEL_CONFIG,
                PREFERRED_AUDIO_FORMAT,
                bufferSize
            )
            
            // Check if initialization succeeded
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                Timber.d("Failed to initialize AudioRecord with $sampleRate Hz")
                return null
            }
            
            // Release the test AudioRecord
            audioRecord.release()
            
            // Return successful result
            return AudioFormatResult(
                sampleRate = sampleRate,
                channelConfig = PREFERRED_CHANNEL_CONFIG,
                audioFormat = PREFERRED_AUDIO_FORMAT,
                bufferSize = bufferSize,
                needsResampling = sampleRate != PREFERRED_SAMPLE_RATE
            )
            
        } catch (e: Exception) {
            Timber.d("Error testing audio format $sampleRate Hz: ${e.message}")
            return null
        }
    }
}
