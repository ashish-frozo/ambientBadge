package com.frozo.ambientscribe.audio

import android.media.AudioFormat
import android.media.AudioRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AudioFormatTest {

    @Test
    fun `AudioResampler should correctly resample audio data`() {
        // Create resampler for 48kHz to 16kHz
        val resampler = AudioResampler(48000, 16000)
        
        // Create test data (1 second of 48kHz audio)
        val inputData = ShortArray(48000) { 
            // Simple sine wave
            (Short.MAX_VALUE * kotlin.math.sin(it * 2 * kotlin.math.PI / 48.0)).toShort()
        }
        
        // Resample to 16kHz
        val outputData = resampler.resample(inputData)
        
        // Output should be ~1/3 the size of input
        assertEquals(16000, outputData.size)
        
        // Check resampling stats
        val stats = resampler.getStats()
        assertEquals(48000, stats["input_sample_rate"])
        assertEquals(16000, stats["output_sample_rate"])
        assertEquals(48000L, stats["total_input_samples"])
        assertEquals(16000L, stats["total_output_samples"])
        assertEquals(0, stats["underruns"])
        assertEquals(0, stats["overruns"])
    }
    
    @Test
    fun `AudioResampler should handle edge cases`() {
        // Create resampler
        val resampler = AudioResampler(44100, 16000)
        
        // Empty input
        val emptyInput = ShortArray(0)
        val emptyOutput = resampler.resample(emptyInput)
        assertEquals(0, emptyOutput.size)
        
        // Single sample input
        val singleInput = shortArrayOf(16000)
        val singleOutput = resampler.resample(singleInput)
        assertTrue(singleOutput.size > 0)
        
        // Reset stats
        resampler.resetStats()
        val stats = resampler.getStats()
        assertEquals(0L, stats["total_input_samples"])
    }
    
    @Test
    fun `AudioFormatProbe should prefer 16kHz when available`() = runTest {
        // Mock AudioRecord.getMinBufferSize to simulate 16kHz support
        val audioRecordMockedStatic = mockStatic(AudioRecord::class.java)
        audioRecordMockedStatic.`when`<Int> { 
            AudioRecord.getMinBufferSize(
                eq(16000),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT)
            )
        }.thenReturn(4096)
        
        // Create probe
        val probe = AudioFormatProbe()
        
        // Test probe
        val result = probe.probeAudioFormat()
        
        // Should succeed with 16kHz
        assertTrue(result.isSuccess)
        val format = result.getOrNull()!!
        assertEquals(16000, format.sampleRate)
        assertFalse(format.needsResampling)
        
        audioRecordMockedStatic.close()
    }
    
    @Test
    fun `AudioFormatProbe should fall back to 48kHz when 16kHz not available`() = runTest {
        // Mock AudioRecord.getMinBufferSize to simulate 16kHz not supported but 48kHz supported
        val audioRecordMockedStatic = mockStatic(AudioRecord::class.java)
        
        // 16kHz not supported
        audioRecordMockedStatic.`when`<Int> { 
            AudioRecord.getMinBufferSize(
                eq(16000),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT)
            )
        }.thenReturn(AudioRecord.ERROR_BAD_VALUE)
        
        // 48kHz supported
        audioRecordMockedStatic.`when`<Int> { 
            AudioRecord.getMinBufferSize(
                eq(48000),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT)
            )
        }.thenReturn(8192)
        
        // Create probe
        val probe = AudioFormatProbe()
        
        // Test probe
        val result = probe.probeAudioFormat()
        
        // Should succeed with 48kHz and need resampling
        assertTrue(result.isSuccess)
        val format = result.getOrNull()!!
        assertEquals(48000, format.sampleRate)
        assertTrue(format.needsResampling)
        assertEquals(16000, format.targetSampleRate)
        
        audioRecordMockedStatic.close()
    }
    
    @Test
    fun `AudioFormatProbe should try all fallback rates in order`() = runTest {
        // Mock AudioRecord.getMinBufferSize to simulate only 8kHz supported
        val audioRecordMockedStatic = mockStatic(AudioRecord::class.java)
        
        // All rates not supported except 8kHz
        for (rate in listOf(16000, 48000, 44100, 32000, 22050)) {
            audioRecordMockedStatic.`when`<Int> { 
                AudioRecord.getMinBufferSize(
                    eq(rate),
                    eq(AudioFormat.CHANNEL_IN_MONO),
                    eq(AudioFormat.ENCODING_PCM_16BIT)
                )
            }.thenReturn(AudioRecord.ERROR_BAD_VALUE)
        }
        
        // 8kHz supported
        audioRecordMockedStatic.`when`<Int> { 
            AudioRecord.getMinBufferSize(
                eq(8000),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT)
            )
        }.thenReturn(2048)
        
        // Create probe
        val probe = AudioFormatProbe()
        
        // Test probe
        val result = probe.probeAudioFormat()
        
        // Should succeed with 8kHz and need resampling
        assertTrue(result.isSuccess)
        val format = result.getOrNull()!!
        assertEquals(8000, format.sampleRate)
        assertTrue(format.needsResampling)
        
        audioRecordMockedStatic.close()
    }
}
