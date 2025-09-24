package com.frozo.ambientscribe.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.frozo.ambientscribe.telemetry.MetricsCollector
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class BufferAutotuneTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockAudioRecord: AudioRecord
    
    @Mock
    private lateinit var mockMetricsCollector: MetricsCollector
    
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
        
        // Mock MetricsCollector
        runTest {
            whenever(mockMetricsCollector.recordEvent(anyString(), any())).thenReturn(Unit)
        }
    }
    
    @Test
    fun testBufferUnderrunHandling() = runTest {
        // Create AudioCapture with mocked dependencies
        val audioCapture = AudioCapture(mockContext)
        
        // Initialize audio capture
        val result = audioCapture.initialize()
        assertTrue(result)
        
        // Simulate buffer underrun by reading too slowly
        whenever(mockAudioRecord.read(any<ByteArray>(), anyInt(), anyInt())).thenReturn(AudioRecord.ERROR_INVALID_OPERATION)
        
        // Start recording
        audioCapture.startRecording()
        
        // Get audio flow to trigger buffer processing
        val audioFlow = audioCapture.getAudioFlow()
        assertNotNull(audioFlow)
        
        // Stop recording
        audioCapture.stopRecording()
        
        // Cleanup
        audioCapture.cleanup()
    }
    
    @Test
    fun testBufferOverrunHandling() = runTest {
        // Create AudioCapture with mocked dependencies
        val audioCapture = AudioCapture(mockContext)
        
        // Initialize audio capture
        val result = audioCapture.initialize()
        assertTrue(result)
        
        // Simulate buffer overrun by reading too quickly
        whenever(mockAudioRecord.read(any<ByteArray>(), anyInt(), anyInt())).thenReturn(-2) // ERROR_WOULD_BLOCK
        
        // Start recording
        audioCapture.startRecording()
        
        // Get audio flow to trigger buffer processing
        val audioFlow = audioCapture.getAudioFlow()
        assertNotNull(audioFlow)
        
        // Stop recording
        audioCapture.stopRecording()
        
        // Cleanup
        audioCapture.cleanup()
    }
    
    @Test
    fun testBufferSizeAdjustment() = runTest {
        // Create AudioCapture with mocked dependencies
        val audioCapture = AudioCapture(mockContext)
        
        // Initialize audio capture
        val result = audioCapture.initialize()
        assertTrue(result)
        
        // Simulate successful audio reading
        whenever(mockAudioRecord.read(any<ByteArray>(), anyInt(), anyInt())).thenAnswer { invocation ->
            val buffer = invocation.arguments[0] as ByteArray
            val offset = invocation.arguments[1] as Int
            val length = invocation.arguments[2] as Int
            
            // Fill buffer with test data
            for (i in offset until offset + length) {
                if (i < buffer.size) {
                    buffer[i] = (i % 256).toByte()
                }
            }
            
            length // Return number of bytes read
        }
        
        // Start recording
        audioCapture.startRecording()
        
        // Get audio flow to trigger buffer processing
        val audioFlow = audioCapture.getAudioFlow()
        assertNotNull(audioFlow)
        
        // Stop recording
        audioCapture.stopRecording()
        
        // Cleanup
        audioCapture.cleanup()
    }
}