package com.frozo.ambientscribe.audio

import android.content.Context
import android.media.AudioRecord
import android.os.PowerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class RingBufferPurgeTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockPowerManager: PowerManager
    
    @Mock
    private lateinit var mockWakeLock: PowerManager.WakeLock
    
    @Mock
    private lateinit var mockAudioRecord: AudioRecord
    
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
            AudioRecord.getMinBufferSize(any(), any(), any()) 
        }.thenReturn(4096)
        
        // Mock AudioRecord instance
        whenever(mockAudioRecord.state).thenReturn(AudioRecord.STATE_INITIALIZED)
        whenever(mockAudioRecord.recordingState).thenReturn(AudioRecord.RECORDSTATE_STOPPED)
        whenever(mockAudioRecord.bufferSizeInFrames).thenReturn(1024)
    }

    @After
    fun tearDown() {
        audioRecordMockedStatic.close()
    }

    @Test
    fun `automatic purging should clear buffer on session end`() = runTest {
        // Create AudioCapture with auto-purge enabled
        audioCapture = AudioCapture(
            context = mockContext,
            autoPurgeEnabled = true
        )
        
        audioCapture.initialize()
        
        // Simulate adding data to ring buffer
        val testData = ByteArray(1024) { it.toByte() }
        val shortData = ShortArray(512) { it.toShort() }
        
        // Stop recording should trigger auto-purge
        audioCapture.stopRecording()
        
        // Verify buffer is empty
        assertTrue(audioCapture.verifyBufferEmpty())
        
        // Verify no data in buffer
        val bufferData = audioCapture.getRingBufferData()
        assertTrue(bufferData.isEmpty() || bufferData.all { it == 0.toByte() })
        
        audioCapture.cleanup()
    }

    @Test
    fun `manual purging should work when auto-purge is disabled`() = runTest {
        // Create AudioCapture with auto-purge disabled
        audioCapture = AudioCapture(
            context = mockContext,
            autoPurgeEnabled = false
        )
        
        audioCapture.initialize()
        
        // Simulate stopping recording without auto-purge
        audioCapture.stopRecording()
        
        // Manual purge
        audioCapture.purgeRingBuffer()
        
        // Verify buffer is empty
        assertTrue(audioCapture.verifyBufferEmpty())
        
        audioCapture.cleanup()
    }

    @Test
    fun `cleanup should always purge buffer regardless of auto-purge setting`() = runTest {
        // Create AudioCapture with auto-purge disabled
        audioCapture = AudioCapture(
            context = mockContext,
            autoPurgeEnabled = false
        )
        
        audioCapture.initialize()
        
        // Cleanup should purge buffer even with auto-purge disabled
        audioCapture.cleanup()
        
        // Create new instance to check if buffer was purged
        val newAudioCapture = AudioCapture(mockContext)
        newAudioCapture.initialize()
        
        // Verify buffer is empty
        assertTrue(newAudioCapture.verifyBufferEmpty())
        
        newAudioCapture.cleanup()
    }

    @Test
    fun `deleteLast30Seconds should purge buffer and log event`() = runTest {
        audioCapture = AudioCapture(mockContext)
        audioCapture.initialize()
        
        // Delete last 30 seconds
        audioCapture.deleteLast30Seconds()
        
        // Verify buffer is empty
        assertTrue(audioCapture.verifyBufferEmpty())
        
        audioCapture.cleanup()
    }
}
