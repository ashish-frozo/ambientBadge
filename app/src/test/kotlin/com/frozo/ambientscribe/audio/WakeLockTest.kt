package com.frozo.ambientscribe.audio

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class WakeLockTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockPowerManager: PowerManager
    
    @Mock
    private lateinit var mockWakeLock: PowerManager.WakeLock
    
    @Captor
    private lateinit var wakeLockCaptor: ArgumentCaptor<Int>
    
    @Captor
    private lateinit var wakeLockTagCaptor: ArgumentCaptor<String>
    
    private lateinit var audioCapture: AudioCapture

    @Before
    fun setUp() {
        // Mock PowerManager
        whenever(mockContext.getSystemService(Context.POWER_SERVICE))
            .thenReturn(mockPowerManager)
        
        // Capture WakeLock parameters
        whenever(mockPowerManager.newWakeLock(wakeLockCaptor.capture(), wakeLockTagCaptor.capture()))
            .thenReturn(mockWakeLock)
        
        // Mock AudioRecord static methods
        val audioRecordMockedStatic = mockStatic(android.media.AudioRecord::class.java)
        audioRecordMockedStatic.`when`<Int> { 
            android.media.AudioRecord.getMinBufferSize(anyInt(), anyInt(), anyInt()) 
        }.thenReturn(4096)
        
        // Create AudioCapture with mocked dependencies
        audioCapture = AudioCapture(mockContext)
    }

    @Test
    fun `initialize should not acquire WakeLock`() = runTest {
        // Initialize AudioCapture
        audioCapture.initialize()
        
        // Verify WakeLock was created but not acquired
        verify(mockPowerManager).newWakeLock(anyInt(), anyString())
        verify(mockWakeLock, never()).acquire()
    }

    @Test
    fun `startRecording should acquire WakeLock`() = runTest {
        // Initialize and start recording
        audioCapture.initialize()
        
        // Mock AudioRecord
        val mockAudioRecord = mock(android.media.AudioRecord::class.java)
        whenever(mockAudioRecord.state).thenReturn(android.media.AudioRecord.STATE_INITIALIZED)
        whenever(mockAudioRecord.recordingState).thenReturn(android.media.AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        val audioRecordMockedStatic = mockStatic(android.media.AudioRecord::class.java)
        audioRecordMockedStatic.`when`<android.media.AudioRecord> {
            android.media.AudioRecord(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Start recording
        audioCapture.startRecording()
        
        // Verify WakeLock was acquired
        verify(mockWakeLock).acquire()
        
        // Verify WakeLock type is PARTIAL_WAKE_LOCK
        assertEquals(PowerManager.PARTIAL_WAKE_LOCK, wakeLockCaptor.value)
        
        // Verify WakeLock tag contains "AmbientScribe"
        assertTrue(wakeLockTagCaptor.value.contains("AmbientScribe"))
        
        audioRecordMockedStatic.close()
    }

    @Test
    fun `stopRecording should release WakeLock if held`() = runTest {
        // Initialize and start recording
        audioCapture.initialize()
        
        // Mock AudioRecord
        val mockAudioRecord = mock(android.media.AudioRecord::class.java)
        whenever(mockAudioRecord.state).thenReturn(android.media.AudioRecord.STATE_INITIALIZED)
        whenever(mockAudioRecord.recordingState).thenReturn(android.media.AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        val audioRecordMockedStatic = mockStatic(android.media.AudioRecord::class.java)
        audioRecordMockedStatic.`when`<android.media.AudioRecord> {
            android.media.AudioRecord(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Mock WakeLock.isHeld
        whenever(mockWakeLock.isHeld).thenReturn(true)
        
        // Start and stop recording
        audioCapture.startRecording()
        audioCapture.stopRecording()
        
        // Verify WakeLock was released
        verify(mockWakeLock).release()
        
        audioRecordMockedStatic.close()
    }

    @Test
    fun `stopRecording should not release WakeLock if not held`() = runTest {
        // Initialize and start recording
        audioCapture.initialize()
        
        // Mock AudioRecord
        val mockAudioRecord = mock(android.media.AudioRecord::class.java)
        whenever(mockAudioRecord.state).thenReturn(android.media.AudioRecord.STATE_INITIALIZED)
        whenever(mockAudioRecord.recordingState).thenReturn(android.media.AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        val audioRecordMockedStatic = mockStatic(android.media.AudioRecord::class.java)
        audioRecordMockedStatic.`when`<android.media.AudioRecord> {
            android.media.AudioRecord(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Mock WakeLock.isHeld
        whenever(mockWakeLock.isHeld).thenReturn(false)
        
        // Start and stop recording
        audioCapture.startRecording()
        audioCapture.stopRecording()
        
        // Verify WakeLock was not released
        verify(mockWakeLock, never()).release()
        
        audioRecordMockedStatic.close()
    }

    @Test
    fun `cleanup should release WakeLock if held`() = runTest {
        // Initialize AudioCapture
        audioCapture.initialize()
        
        // Mock WakeLock.isHeld
        whenever(mockWakeLock.isHeld).thenReturn(true)
        
        // Cleanup
        audioCapture.cleanup()
        
        // Verify WakeLock was released
        verify(mockWakeLock).release()
    }

    @Test
    fun `WakeLock should have timeout of 0 (indefinite)`() = runTest {
        // Initialize AudioCapture
        audioCapture.initialize()
        
        // Verify WakeLock was created with no timeout
        verify(mockWakeLock, never()).acquire(anyLong())
        
        // Start recording to acquire WakeLock
        val mockAudioRecord = mock(android.media.AudioRecord::class.java)
        whenever(mockAudioRecord.state).thenReturn(android.media.AudioRecord.STATE_INITIALIZED)
        whenever(mockAudioRecord.recordingState).thenReturn(android.media.AudioRecord.RECORDSTATE_RECORDING)
        
        // Mock AudioRecord creation
        val audioRecordMockedStatic = mockStatic(android.media.AudioRecord::class.java)
        audioRecordMockedStatic.`when`<android.media.AudioRecord> {
            android.media.AudioRecord(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt()
            )
        }.thenReturn(mockAudioRecord)
        
        // Start recording
        audioCapture.startRecording()
        
        // Verify WakeLock was acquired with no timeout
        verify(mockWakeLock).acquire()
        verify(mockWakeLock, never()).acquire(anyLong())
        
        audioRecordMockedStatic.close()
    }
}
