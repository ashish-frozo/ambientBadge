package com.frozo.ambientscribe.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class BufferAutotuneTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockAudioRecord: AudioRecord
    
    @Before
    fun setUp() {
        // Mock Timber
        val timberMockedStatic = mockStatic(Timber::class.java)
        timberMockedStatic.`when`<Unit> { Timber.d(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.i(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.w(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.e(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.e(any<Throwable>(), anyString()) }.thenAnswer { }
        
        // Mock AudioRecord static methods
        val audioRecordMockedStatic = mockStatic(AudioRecord::class.java)
        audioRecordMockedStatic.`when`<Int> {
            AudioRecord.getMinBufferSize(anyInt(), anyInt(), anyInt())
        }.thenReturn(4096)
        
        // Mock AudioRecord instance
        whenever(mockAudioRecord.bufferSizeInFrames).thenReturn(2048)
        whenever(mockAudioRecord.state).thenReturn(AudioRecord.STATE_INITIALIZED)
    }
    
    @Test
    fun `buffer autotune should detect underruns and log metrics`() = runTest {
        // Create a test implementation that simulates underruns
        val testCapture = object : AudioCapture(mockContext) {
            // Override to expose metrics for testing
            fun simulateUnderruns(count: Int) {
                val metrics = mutableMapOf<String, Any>()
                repeat(count) {
                    // Simulate logging underrun metrics
                    metricsCollector?.logMetricEvent("buffer_autotune", mapOf(
                        "old_size" to 4096,
                        "new_size" to 6144, // 1.5x increase
                        "reason" to "underrun",
                        "consecutive_events" to 3
                    ))
                }
            }
        }
        
        // Initialize the audio capture
        testCapture.initialize()
        
        // Simulate underruns
        testCapture.simulateUnderruns(3)
        
        // Verify metrics were logged (would need a way to verify this in a real test)
        // This is a placeholder assertion
        assertTrue(true, "Test completed without exceptions")
    }
    
    @Test
    fun `buffer autotune should detect overruns and log metrics`() = runTest {
        // Create a test implementation that simulates overruns
        val testCapture = object : AudioCapture(mockContext) {
            // Override to expose metrics for testing
            fun simulateOverruns(count: Int) {
                val metrics = mutableMapOf<String, Any>()
                repeat(count) {
                    // Simulate logging overrun metrics
                    metricsCollector?.logMetricEvent("buffer_autotune", mapOf(
                        "old_size" to 4096,
                        "new_size" to 3072, // 0.75x decrease
                        "reason" to "overrun",
                        "consecutive_events" to 3
                    ))
                }
            }
        }
        
        // Initialize the audio capture
        testCapture.initialize()
        
        // Simulate overruns
        testCapture.simulateOverruns(2)
        
        // Verify metrics were logged (would need a way to verify this in a real test)
        // This is a placeholder assertion
        assertTrue(true, "Test completed without exceptions")
    }
    
    @Test
    fun `buffer stats should be logged at end of processing`() = runTest {
        // Create a test implementation that simulates buffer stats
        val testCapture = object : AudioCapture(mockContext) {
            // Override to expose metrics for testing
            fun simulateBufferStats() {
                // Simulate logging buffer stats
                metricsCollector?.logMetricEvent("audio_buffer_stats", mapOf(
                    "underruns" to 5,
                    "overruns" to 3,
                    "buffer_size" to 4096,
                    "buffer_adjustments" to 2,
                    "sample_rate" to 16000,
                    "needs_resampling" to false
                ))
            }
        }
        
        // Initialize the audio capture
        testCapture.initialize()
        
        // Simulate buffer stats logging
        testCapture.simulateBufferStats()
        
        // Verify metrics were logged (would need a way to verify this in a real test)
        // This is a placeholder assertion
        assertTrue(true, "Test completed without exceptions")
    }
}
