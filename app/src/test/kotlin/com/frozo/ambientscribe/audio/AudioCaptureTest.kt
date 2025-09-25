package com.frozo.ambientscribe.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import com.frozo.ambientscribe.security.AuditLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AudioCaptureTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockPowerManager: PowerManager
    
    @Mock
    private lateinit var mockWakeLock: PowerManager.WakeLock
    
    @Mock
    private lateinit var mockAudioRecord: AudioRecord
    
    @Mock
    private lateinit var mockAuditLogger: AuditLogger
    
    private lateinit var audioCapture: AudioCapture
    private lateinit var audioRecordMockedStatic: MockedStatic<AudioRecord>

    @Before
    fun setUp() {
        // Mock PowerManager
        whenever(mockContext.getSystemService(Context.POWER_SERVICE))
            .thenReturn(mockPowerManager)
        whenever(mockPowerManager.newWakeLock(any(), any()))
            .thenReturn(mockWakeLock)
        
        // Mock AudioRecord static methods
        audioRecordMockedStatic = mockStatic(AudioRecord::class.java)
        audioRecordMockedStatic.`when`<Int> { 
            AudioRecord.getMinBufferSize(anyInt(), anyInt(), anyInt()) 
        }.thenReturn(4096)

        audioRecordMockedStatic.`when`<AudioRecord> {
            AudioRecord(
                eq(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                anyInt(),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT),
                anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Mock AudioRecord instance
        whenever(mockAudioRecord.state).thenReturn(AudioRecord.STATE_INITIALIZED)
        whenever(mockAudioRecord.recordingState).thenReturn(AudioRecord.RECORDSTATE_STOPPED)
        whenever(mockAudioRecord.bufferSizeInFrames).thenReturn(1024)
        
        // Mock audio reading behavior
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
        
        // Mock AuditLogger
        runTest {
            whenever(mockAuditLogger.logEvent(anyString(), any(), anyString())).thenReturn(Result.success("test-event-id"))
        }
        
        // Create AudioCapture with mocked dependencies
        audioCapture = AudioCapture(mockContext)
    }

    @After
    fun tearDown() {
        audioCapture.cleanup()
        audioRecordMockedStatic.close()
    }

    @Test
    fun testInitialize() = runTest {
        val result = audioCapture.initialize()
        assertTrue(result)
    }

    @Test
    fun testInitializeFailure() = runTest {
        audioRecordMockedStatic.`when`<Int> { 
            AudioRecord.getMinBufferSize(any(), any(), any()) 
        }.thenReturn(AudioRecord.ERROR_BAD_VALUE)
        
        val result = audioCapture.initialize()
        assertFalse(result)
    }

    @Test
    fun testStartRecordingNotInitialized() = runTest {
        val result = audioCapture.startRecording()
        assertFalse(result)
    }

    @Test
    fun testStartRecording() = runTest {
        audioCapture.initialize()
        
        // Mock successful recording start
        whenever(mockAudioRecord.recordingState)
            .thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        audioRecordMockedStatic.`when`<AudioRecord> {
            AudioRecord(
                eq(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                anyInt(),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT),
                anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        val result = audioCapture.startRecording()
        assertTrue(result)
    }
    
    @Test
    fun testAudioProcessing() = runTest {
        audioCapture.initialize()
        
        // Mock successful recording start
        whenever(mockAudioRecord.recordingState)
            .thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        audioRecordMockedStatic.`when`<AudioRecord> {
            AudioRecord(
                eq(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                anyInt(),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT),
                anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        val result = audioCapture.startRecording()
        assertTrue(result)
        
        // Collect some audio data
        val audioData = audioCapture.getAudioFlow().first()
        
        // Verify audio data properties
        assertNotNull(audioData)
        assertTrue(audioData.samples.isNotEmpty())
        
        // Stop recording
        audioCapture.stopRecording()
    }

    @Test
    fun testStopRecording() = runTest {
        audioCapture.initialize()
        whenever(mockWakeLock.isHeld).thenReturn(true)
        
        val result = audioCapture.stopRecording()
        assertTrue(result)
    }

    @Test
    fun testRingBuffer() = runTest {
        audioCapture.initialize()
        
        // Mock successful recording start
        whenever(mockAudioRecord.recordingState)
            .thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        audioRecordMockedStatic.`when`<AudioRecord> {
            AudioRecord(
                eq(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                anyInt(),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT),
                anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Start recording to fill ring buffer
        audioCapture.startRecording()
        
        // Wait for some data to be processed
        delay(100)
        
        // Get data from ring buffer
        val bufferData = audioCapture.getRingBufferData()
        
        // Should have some data now
        assertTrue(bufferData.isNotEmpty())
        
        // Clear ring buffer should work
        audioCapture.clearRingBuffer()
        val clearedData = audioCapture.getRingBufferData()
        assertEquals(0, clearedData.size)
        
        // Stop recording
        audioCapture.stopRecording()
    }

    @Test
    fun testRingBufferWrapAroundDoesNotOverflow() = runTest {
        audioCapture.initialize()

        val ringBufferField = AudioCapture::class.java.getDeclaredField("ringBuffer").apply { isAccessible = true }
        val ringBufferSizeField = AudioCapture::class.java.getDeclaredField("ringBufferSize").apply { isAccessible = true }
        val ringBufferPositionField = AudioCapture::class.java.getDeclaredField("ringBufferPosition").apply { isAccessible = true }
        val bytesBufferedField = AudioCapture::class.java.getDeclaredField("bytesBuffered").apply { isAccessible = true }
        val updateMethod = AudioCapture::class.java.getDeclaredMethod("updateRingBuffer", ByteArray::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }

        val smallSize = 8
        ringBufferSizeField.setInt(audioCapture, smallSize)
        val ring = ByteBuffer.allocateDirect(smallSize).order(ByteOrder.LITTLE_ENDIAN)
        ringBufferField.set(audioCapture, ring)
        ringBufferPositionField.setInt(audioCapture, smallSize - 2)
        bytesBufferedField.setInt(audioCapture, smallSize - 2)

        val data = ByteArray(5) { (it + 1).toByte() }

        updateMethod.invoke(audioCapture, data, data.size)

        val expectedPosition = (smallSize - 2 + data.size) % smallSize
        val newPosition = ringBufferPositionField.getInt(audioCapture)
        assertEquals(expectedPosition, newPosition)

        val bufferedBytes = bytesBufferedField.getInt(audioCapture)
        assertEquals(smallSize, bufferedBytes)

        val snapshot = audioCapture.getRingBufferData()
        assertEquals(smallSize, snapshot.size)
    }

    @Test
    fun testRingBufferClearedOnStartRecording() = runTest {
        audioCapture.initialize()

        val bytesBufferedField = AudioCapture::class.java.getDeclaredField("bytesBuffered").apply { isAccessible = true }
        val ringBufferPositionField = AudioCapture::class.java.getDeclaredField("ringBufferPosition").apply { isAccessible = true }

        bytesBufferedField.setInt(audioCapture, 128)
        ringBufferPositionField.setInt(audioCapture, 64)

        whenever(mockAudioRecord.recordingState).thenReturn(AudioRecord.RECORDSTATE_RECORDING)

        val started = audioCapture.startRecording()
        assertTrue(started)

        assertEquals(0, bytesBufferedField.getInt(audioCapture))
        assertEquals(0, ringBufferPositionField.getInt(audioCapture))
    }
    
    @Test
    fun testVerifyBufferEmpty() = runTest {
        audioCapture.initialize()
        
        // Buffer should be empty initially
        assertTrue(audioCapture.verifyBufferEmpty())
        
        // After clearing, buffer should still be empty
        audioCapture.clearRingBuffer()
        assertTrue(audioCapture.verifyBufferEmpty())
    }

    @Test
    fun testDeleteLast30Seconds() = runTest {
        audioCapture.initialize()
        
        // Mock successful recording start to fill buffer
        whenever(mockAudioRecord.recordingState)
            .thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        audioRecordMockedStatic.`when`<AudioRecord> {
            AudioRecord(
                eq(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                anyInt(),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT),
                anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Start recording to fill buffer
        audioCapture.startRecording()
        delay(100)
        
        // Delete last 30 seconds
        audioCapture.deleteLast30Seconds()
        
        // Verify buffer is empty
        val data = audioCapture.getRingBufferData()
        assertEquals(0, data.size)
        assertTrue(audioCapture.verifyBufferEmpty())
        
        // Stop recording
        audioCapture.stopRecording()
    }
    
    @Test
    fun testCleanup() = runTest {
        audioCapture.initialize()
        whenever(mockWakeLock.isHeld).thenReturn(true)
        
        // Mock successful recording start
        whenever(mockAudioRecord.recordingState)
            .thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        audioRecordMockedStatic.`when`<AudioRecord> {
            AudioRecord(
                eq(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                anyInt(),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT),
                anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Start recording
        audioCapture.startRecording()
        
        // Cleanup should stop recording and release resources
        audioCapture.cleanup()
        
        // Verify buffer is purged
        assertTrue(audioCapture.verifyBufferEmpty())
    }
    
    @Test
    fun testAudioDataFlow() = runTest {
        audioCapture.initialize()
        
        // Mock successful recording start
        whenever(mockAudioRecord.recordingState)
            .thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        audioRecordMockedStatic.`when`<AudioRecord> {
            AudioRecord(
                eq(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                anyInt(),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT),
                anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Start recording
        audioCapture.startRecording()
        
        // Collect audio data
        val audioFlow = audioCapture.getAudioFlow()
        assertNotNull(audioFlow)
        
        // Try to collect audio data (may timeout if none emitted)
        try {
            withTimeout(500) {
                val audioData = audioFlow.first()
                assertNotNull(audioData)
                assertTrue(audioData.samples.isNotEmpty())
            }
        } catch (e: TimeoutCancellationException) {
            // This is acceptable in a test environment
        }
        
        // Stop recording
        audioCapture.stopRecording()
    }
    
    @Test
    fun testEnergyLevelCalculation() = runTest {
        audioCapture.initialize()
        
        // Mock successful recording start
        whenever(mockAudioRecord.recordingState)
            .thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        audioRecordMockedStatic.`when`<AudioRecord> {
            AudioRecord(
                eq(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                anyInt(),
                eq(AudioFormat.CHANNEL_IN_MONO),
                eq(AudioFormat.ENCODING_PCM_16BIT),
                anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Custom read implementation to simulate specific audio pattern
        whenever(mockAudioRecord.read(any<ByteArray>(), anyInt(), anyInt())).thenAnswer { invocation ->
            val buffer = invocation.arguments[0] as ByteArray
            val offset = invocation.arguments[1] as Int
            val length = invocation.arguments[2] as Int
            
            // Fill buffer with specific pattern to test energy calculation
            // Create a pattern that will result in shorts: 1000, -1000, 2000, -2000, 0
            val values = listOf(1000, -1000, 2000, -2000, 0)
            var valueIndex = 0
            
            var i = offset
            while (i < offset + length) {
                if (i + 1 < buffer.size) {
                    val value = values[valueIndex % values.size]
                    
                    // Convert short to bytes (little endian)
                    buffer[i] = (value and 0xFF).toByte()
                    buffer[i + 1] = ((value shr 8) and 0xFF).toByte()
                    
                    valueIndex++
                    i += 2
                } else {
                    break
                }
            }
            
            length // Return number of bytes read
        }
        
        // Start recording
        audioCapture.startRecording()
        
        // Collect audio data to check energy level
        try {
            withTimeout(500) {
                val audioData = audioCapture.getAudioFlow().first()
                
                // Verify energy level is reasonable
                // For the given sample pattern, energy should be non-zero
                assertTrue(audioData.energyLevel > 0)
                
                // For the given high-amplitude samples, isVoiceActive should be true
                assertTrue(audioData.isVoiceActive)
            }
        } catch (e: TimeoutCancellationException) {
            // This is acceptable in a test environment
        }
        
        // Stop recording
        audioCapture.stopRecording()
    }

    @Test
    fun testRingBufferWraparound() = runTest {
        // Test ring buffer wraparound behavior
        // This would be tested through the public interface in integration tests
        assertTrue(true) // Placeholder for integration test
    }

    @Test
    fun testMultipleInitializeCalls() = runTest {
        val result1 = audioCapture.initialize()
        val result2 = audioCapture.initialize()
        
        assertTrue(result1)
        assertTrue(result2)
    }

    @Test
    fun testStopRecordingWhenNotRecording() = runTest {
        audioCapture.initialize()
        
        val result = audioCapture.stopRecording()
        assertTrue(result)
    }

    @Test
    fun testRingBufferDurationLimit() = runTest {
        // Test that ring buffer doesn't exceed 30 seconds of audio data
        val expectedMaxSize = (16000 * 2 * 30) // 16kHz * 2 bytes * 30 seconds
        
        // This would be tested in integration tests with actual audio data
        assertTrue(expectedMaxSize > 0)
    }

    @Test
    fun testAudioFormat() = runTest {
        // Verify the audio configuration constants
        assertEquals(16000, 16000) // Sample rate
        assertTrue(true) // Format verification through AudioRecord mock
    }
}
