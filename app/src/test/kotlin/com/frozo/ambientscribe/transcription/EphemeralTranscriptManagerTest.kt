package com.frozo.ambientscribe.transcription

import android.content.Context
import android.content.SharedPreferences
import com.frozo.ambientscribe.security.AuditLogger
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class EphemeralTranscriptManagerTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Mock
    private lateinit var mockMetricsCollector: MetricsCollector
    
    @Mock
    private lateinit var mockAuditLogger: AuditLogger
    
    private lateinit var ephemeralTranscriptManager: EphemeralTranscriptManager

    @Before
    fun setUp() {
        // Mock Timber
        val timberMockedStatic = mockStatic(Timber::class.java)
        timberMockedStatic.`when`<Unit> { Timber.d(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.i(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.w(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.e(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.e(any<Throwable>(), anyString()) }.thenAnswer { }
        
        // Mock SharedPreferences
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(anyString(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        whenever(mockEditor.putLong(anyString(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(anyString())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        
        // Mock AuditLogger
        val auditLoggerMockedConstructor = mockStatic(AuditLogger::class.java)
        auditLoggerMockedConstructor.`when`<AuditLogger> { 
            AuditLogger(mockContext) 
        }.thenReturn(mockAuditLogger)
        
        // Create manager with mocked dependencies
        ephemeralTranscriptManager = EphemeralTranscriptManager(mockContext, mockMetricsCollector)
    }

    @Test
    fun `startEphemeralSession should mark pending purge`() {
        // When
        val sessionId = ephemeralTranscriptManager.startEphemeralSession()
        
        // Then
        verify(mockEditor).putBoolean(eq("has_pending_purge"), eq(true))
        verify(mockEditor).putString(eq("pending_session_id"), eq(sessionId))
        verify(mockEditor).putLong(eq("crash_timestamp"), any())
        verify(mockEditor).apply()
        
        assertTrue(ephemeralTranscriptManager.isEphemeralModeActive())
    }

    @Test
    fun `addTranscriptSegment should store in memory only`() {
        // Given
        ephemeralTranscriptManager.startEphemeralSession()
        
        // When
        ephemeralTranscriptManager.addTranscriptSegment("Test transcript", 1, 1000L)
        ephemeralTranscriptManager.addTranscriptSegment("More text", 2, 2000L)
        
        // Then
        val completeTranscript = ephemeralTranscriptManager.getCompleteTranscript()
        assertEquals("Test transcript More text", completeTranscript)
        
        val metadata = ephemeralTranscriptManager.getTranscriptMetadata()
        assertEquals(2, metadata["segment_count"])
        assertEquals(true, metadata["is_ephemeral"])
    }

    @Test
    fun `endEphemeralSession should clear pending purge and buffers`() = runTest {
        // Given
        val sessionId = ephemeralTranscriptManager.startEphemeralSession()
        ephemeralTranscriptManager.addTranscriptSegment("Test transcript", 1, 1000L)
        
        // When
        val result = ephemeralTranscriptManager.endEphemeralSession()
        
        // Then
        assertTrue(result)
        verify(mockEditor).putBoolean(eq("has_pending_purge"), eq(false))
        verify(mockEditor).remove(eq("pending_session_id"))
        verify(mockEditor).remove(eq("crash_timestamp"))
        verify(mockAuditLogger).logEvent(eq("EPHEMERAL_SESSION_END"), mapOf("session_id" to sessionId))
        
        // Verify buffers cleared
        assertEquals("", ephemeralTranscriptManager.getCompleteTranscript())
        assertFalse(ephemeralTranscriptManager.isEphemeralModeActive())
    }

    @Test
    fun `checkForPendingPurge should detect and handle crashed sessions`() = runTest {
        // Given
        whenever(mockSharedPreferences.getBoolean(eq("has_pending_purge"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getString(eq("pending_session_id"), any())).thenReturn("crashed-session-id")
        whenever(mockSharedPreferences.getLong(eq("crash_timestamp"), any())).thenReturn(1000L)
        
        // When
        val manager = EphemeralTranscriptManager(mockContext, mockMetricsCollector)
        
        // Then
        verify(mockAuditLogger).logEvent(eq("ABANDON_PURGE"), mapOf("session_id" to "crashed-session-id"))
        verify(mockEditor).putBoolean(eq("has_pending_purge"), eq(false))
        verify(mockEditor).remove(eq("pending_session_id"))
        verify(mockEditor).remove(eq("crash_timestamp"))
    }

    @Test
    fun `forcePurge should clear everything immediately`() = runTest {
        // Given
        val sessionId = ephemeralTranscriptManager.startEphemeralSession()
        ephemeralTranscriptManager.addTranscriptSegment("Test transcript", 1, 1000L)
        
        // When
        ephemeralTranscriptManager.forcePurge()
        
        // Then
        verify(mockAuditLogger).logEvent(eq("FORCE_PURGE"), mapOf("reason" to "manual"))
        assertEquals("", ephemeralTranscriptManager.getCompleteTranscript())
        assertFalse(ephemeralTranscriptManager.isEphemeralModeActive())
    }
}
