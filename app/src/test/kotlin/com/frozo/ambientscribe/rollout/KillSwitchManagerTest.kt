package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for KillSwitchManager
 * 
 * Tests kill switch functionality including:
 * - Kill switch activation and deactivation
 * - Emergency kill switch behavior
 * - Error handling and graceful degradation
 * - State persistence and recovery
 */
class KillSwitchManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var killSwitchManager: KillSwitchManager
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockPrefs = mockk<SharedPreferences>(relaxed = true)
        mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putString(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        
        // Reset singleton instance
        KillSwitchManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        killSwitchManager = KillSwitchManager.getInstance(mockContext)
    }
    
    @Test
    @DisplayName("Test kill switch initialization with default values")
    fun testKillSwitchInitialization() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = KillSwitchManager.getInstance(mockContext)
        
        // Then
        assertFalse(manager.isAudioCaptureKilled())
        assertFalse(manager.isLLMProcessingKilled())
        assertFalse(manager.isAppKilled())
        assertFalse(manager.isEmergencyKilled())
    }
    
    @Test
    @DisplayName("Test audio capture kill switch activation")
    fun testAudioCaptureKillSwitchActivation() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.killAudioCapture("Test reason", "test_user")
        
        // Then
        assertTrue(killSwitchManager.isAudioCaptureKilled())
        assertFalse(killSwitchManager.isLLMProcessingKilled())
        assertFalse(killSwitchManager.isAppKilled())
        assertFalse(killSwitchManager.isEmergencyKilled())
    }
    
    @Test
    @DisplayName("Test LLM processing kill switch activation")
    fun testLLMProcessingKillSwitchActivation() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.killLLMProcessing("Test reason", "test_user")
        
        // Then
        assertFalse(killSwitchManager.isAudioCaptureKilled())
        assertTrue(killSwitchManager.isLLMProcessingKilled())
        assertFalse(killSwitchManager.isAppKilled())
        assertFalse(killSwitchManager.isEmergencyKilled())
    }
    
    @Test
    @DisplayName("Test app kill switch activation")
    fun testAppKillSwitchActivation() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.killApp("Test reason", "test_user")
        
        // Then
        assertFalse(killSwitchManager.isAudioCaptureKilled())
        assertFalse(killSwitchManager.isLLMProcessingKilled())
        assertTrue(killSwitchManager.isAppKilled())
        assertFalse(killSwitchManager.isEmergencyKilled())
    }
    
    @Test
    @DisplayName("Test emergency kill switch activation")
    fun testEmergencyKillSwitchActivation() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.activateEmergencyKillSwitch("Emergency reason", "test_user")
        
        // Then
        assertTrue(killSwitchManager.isAudioCaptureKilled()) // Should be killed by emergency
        assertTrue(killSwitchManager.isLLMProcessingKilled()) // Should be killed by emergency
        assertTrue(killSwitchManager.isAppKilled()) // Should be killed by emergency
        assertTrue(killSwitchManager.isEmergencyKilled())
    }
    
    @Test
    @DisplayName("Test audio capture restoration")
    fun testAudioCaptureRestoration() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.restoreAudioCapture("test_user")
        
        // Then
        assertFalse(killSwitchManager.isAudioCaptureKilled())
    }
    
    @Test
    @DisplayName("Test LLM processing restoration")
    fun testLLMProcessingRestoration() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.restoreLLMProcessing("test_user")
        
        // Then
        assertFalse(killSwitchManager.isLLMProcessingKilled())
    }
    
    @Test
    @DisplayName("Test app restoration")
    fun testAppRestoration() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.restoreApp("test_user")
        
        // Then
        assertFalse(killSwitchManager.isAppKilled())
    }
    
    @Test
    @DisplayName("Test emergency kill switch deactivation")
    fun testEmergencyKillSwitchDeactivation() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.deactivateEmergencyKillSwitch("test_user")
        
        // Then
        assertFalse(killSwitchManager.isEmergencyKilled())
    }
    
    @Test
    @DisplayName("Test kill switch status retrieval")
    fun testKillSwitchStatusRetrieval() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "Test reason"
        every { mockPrefs.getLong(any(), any()) } returns 1234567890L
        
        // When
        val status = killSwitchManager.getKillSwitchStatus()
        
        // Then
        assertTrue(status.containsKey("audio_capture_disabled"))
        assertTrue(status.containsKey("llm_processing_disabled"))
        assertTrue(status.containsKey("app_disabled"))
        assertTrue(status.containsKey("emergency_disabled"))
        assertTrue(status.containsKey("last_update"))
        assertTrue(status.containsKey("reason"))
        assertTrue(status.containsKey("activated_by"))
    }
    
    @Test
    @DisplayName("Test emergency kill switch overrides all others")
    fun testEmergencyKillSwitchOverridesAllOthers() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.activateEmergencyKillSwitch("Emergency", "test_user")
        
        // Then
        assertTrue(killSwitchManager.isAudioCaptureKilled())
        assertTrue(killSwitchManager.isLLMProcessingKilled())
        assertTrue(killSwitchManager.isAppKilled())
        assertTrue(killSwitchManager.isEmergencyKilled())
    }
    
    @Test
    @DisplayName("Test error handling in kill switch checking")
    fun testErrorHandlingInKillSwitchChecking() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } throws RuntimeException("Test error")
        
        // When
        val audioCaptureKilled = killSwitchManager.isAudioCaptureKilled()
        val llmProcessingKilled = killSwitchManager.isLLMProcessingKilled()
        val appKilled = killSwitchManager.isAppKilled()
        val emergencyKilled = killSwitchManager.isEmergencyKilled()
        
        // Then
        assertTrue(audioCaptureKilled) // Should return true on error (fail-safe)
        assertTrue(llmProcessingKilled) // Should return true on error (fail-safe)
        assertTrue(appKilled) // Should return true on error (fail-safe)
        assertTrue(emergencyKilled) // Should return true on error (fail-safe)
    }
    
    @Test
    @DisplayName("Test error handling in kill switch activation")
    fun testErrorHandlingInKillSwitchActivation() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefsEditor.putBoolean(any(), any()) } throws RuntimeException("Test error")
        
        // When
        killSwitchManager.killAudioCapture("Test reason", "test_user")
        
        // Then
        // Should not throw exception, should handle error gracefully
        assertFalse(killSwitchManager.isAudioCaptureKilled()) // Should remain unchanged on error
    }
    
    @Test
    @DisplayName("Test error handling in kill switch restoration")
    fun testErrorHandlingInKillSwitchRestoration() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefsEditor.putBoolean(any(), any()) } throws RuntimeException("Test error")
        
        // When
        killSwitchManager.restoreAudioCapture("test_user")
        
        // Then
        // Should not throw exception, should handle error gracefully
        assertTrue(killSwitchManager.isAudioCaptureKilled()) // Should remain unchanged on error
    }
    
    @Test
    @DisplayName("Test concurrent kill switch operations")
    fun testConcurrentKillSwitchOperations() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) {
                    when (threadId % 4) {
                        0 -> killSwitchManager.killAudioCapture("Thread $threadId", "user_$threadId")
                        1 -> killSwitchManager.killLLMProcessing("Thread $threadId", "user_$threadId")
                        2 -> killSwitchManager.killApp("Thread $threadId", "user_$threadId")
                        3 -> killSwitchManager.activateEmergencyKillSwitch("Thread $threadId", "user_$threadId")
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertNotNull(killSwitchManager.getKillSwitchStatus())
    }
    
    @Test
    @DisplayName("Test kill switch state persistence")
    fun testKillSwitchStatePersistence() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.killAudioCapture("Test reason", "test_user")
        
        // Then
        verify { mockPrefsEditor.putBoolean("audio_capture_disabled", true) }
        verify { mockPrefsEditor.putString("reason", "Test reason") }
        verify { mockPrefsEditor.putString("activated_by", "test_user") }
        verify { mockPrefsEditor.apply() }
    }
    
    @Test
    @DisplayName("Test kill switch metadata update")
    fun testKillSwitchMetadataUpdate() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.killAudioCapture("Test reason", "test_user")
        
        // Then
        verify { mockPrefsEditor.putString("reason", "Test reason") }
        verify { mockPrefsEditor.putString("activated_by", "test_user") }
        verify { mockPrefsEditor.putLong("last_update", any()) }
    }
    
    @Test
    @DisplayName("Test multiple kill switch activations")
    fun testMultipleKillSwitchActivations() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.killAudioCapture("Reason 1", "user1")
        killSwitchManager.killLLMProcessing("Reason 2", "user2")
        killSwitchManager.killApp("Reason 3", "user3")
        
        // Then
        assertTrue(killSwitchManager.isAudioCaptureKilled())
        assertTrue(killSwitchManager.isLLMProcessingKilled())
        assertTrue(killSwitchManager.isAppKilled())
        assertFalse(killSwitchManager.isEmergencyKilled())
    }
    
    @Test
    @DisplayName("Test partial restoration after multiple activations")
    fun testPartialRestorationAfterMultipleActivations() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        killSwitchManager.restoreAudioCapture("user1")
        killSwitchManager.restoreLLMProcessing("user2")
        // App remains killed
        
        // Then
        assertFalse(killSwitchManager.isAudioCaptureKilled())
        assertFalse(killSwitchManager.isLLMProcessingKilled())
        assertTrue(killSwitchManager.isAppKilled()) // Still killed
        assertFalse(killSwitchManager.isEmergencyKilled())
    }
}
