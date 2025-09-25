package com.frozo.ambientscribe.rollout

import android.content.Context
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for FallbackManager
 * 
 * Tests fallback functionality including:
 * - Fallback mode detection
 * - User guidance generation
 * - Error handling and graceful degradation
 * - Integration with feature flags and kill switches
 */
class FallbackManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockFeatureFlagManager: FeatureFlagManager
    private lateinit var mockKillSwitchManager: KillSwitchManager
    private lateinit var fallbackManager: FallbackManager
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockFeatureFlagManager = mockk<FeatureFlagManager>(relaxed = true)
        mockKillSwitchManager = mockk<KillSwitchManager>(relaxed = true)
        
        // Reset singleton instance
        FallbackManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        fallbackManager = FallbackManager.getInstance(mockContext)
        fallbackManager.initialize(mockFeatureFlagManager, mockKillSwitchManager)
    }
    
    @Test
    @DisplayName("Test fallback mode detection when ambient scribe is disabled")
    fun testFallbackModeDetectionWhenAmbientScribeDisabled() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns false
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.FEATURE_DISABLED, fallbackType)
        assertTrue(fallbackManager.isInFallbackMode.value)
        assertEquals("Ambient Scribe feature disabled", fallbackManager.fallbackReason.value)
    }
    
    @Test
    @DisplayName("Test fallback mode detection when emergency kill switch is active")
    fun testFallbackModeDetectionWhenEmergencyKillSwitchActive() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns true
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.EMERGENCY, fallbackType)
        assertTrue(fallbackManager.isInFallbackMode.value)
        assertEquals("Emergency kill switch activated", fallbackManager.fallbackReason.value)
    }
    
    @Test
    @DisplayName("Test fallback mode detection when app is killed")
    fun testFallbackModeDetectionWhenAppKilled() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns true
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.APP_DISABLED, fallbackType)
        assertTrue(fallbackManager.isInFallbackMode.value)
        assertEquals("App functionality disabled", fallbackManager.fallbackReason.value)
    }
    
    @Test
    @DisplayName("Test fallback mode detection when audio capture is killed")
    fun testFallbackModeDetectionWhenAudioCaptureKilled() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns true
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.AUDIO_DISABLED, fallbackType)
        assertTrue(fallbackManager.isInFallbackMode.value)
        assertEquals("Audio capture disabled", fallbackManager.fallbackReason.value)
    }
    
    @Test
    @DisplayName("Test fallback mode detection when LLM processing is killed")
    fun testFallbackModeDetectionWhenLLMProcessingKilled() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns true
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.LLM_DISABLED, fallbackType)
        assertTrue(fallbackManager.isInFallbackMode.value)
        assertEquals("LLM processing disabled", fallbackManager.fallbackReason.value)
    }
    
    @Test
    @DisplayName("Test no fallback mode when everything is working")
    fun testNoFallbackModeWhenEverythingWorking() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.NONE, fallbackType)
        assertFalse(fallbackManager.isInFallbackMode.value)
        assertEquals("", fallbackManager.fallbackReason.value)
    }
    
    @Test
    @DisplayName("Test fallback message generation for emergency mode")
    fun testFallbackMessageGenerationForEmergencyMode() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns true
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        fallbackManager.checkFallbackMode()
        
        // When
        val message = fallbackManager.getFallbackMessage()
        
        // Then
        assertTrue(message.contains("Emergency mode"))
        assertTrue(message.contains("temporarily disabled"))
    }
    
    @Test
    @DisplayName("Test fallback message generation for audio disabled mode")
    fun testFallbackMessageGenerationForAudioDisabledMode() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns true
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        fallbackManager.checkFallbackMode()
        
        // When
        val message = fallbackManager.getFallbackMessage()
        
        // Then
        assertTrue(message.contains("Audio disabled"))
        assertTrue(message.contains("Manual note entry available"))
    }
    
    @Test
    @DisplayName("Test fallback action for audio disabled mode")
    fun testFallbackActionForAudioDisabledMode() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns true
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        fallbackManager.checkFallbackMode()
        
        // When
        val action = fallbackManager.getFallbackAction()
        
        // Then
        assertEquals(FallbackManager.FallbackAction.MANUAL_ENTRY, action)
    }
    
    @Test
    @DisplayName("Test fallback action for LLM disabled mode")
    fun testFallbackActionForLLMDisabledMode() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns true
        
        fallbackManager.checkFallbackMode()
        
        // When
        val action = fallbackManager.getFallbackAction()
        
        // Then
        assertEquals(FallbackManager.FallbackAction.BASIC_GENERATION, action)
    }
    
    @Test
    @DisplayName("Test manual note entry availability")
    fun testManualNoteEntryAvailability() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns true
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        fallbackManager.checkFallbackMode()
        
        // When
        val isAvailable = fallbackManager.isManualNoteEntryAvailable()
        
        // Then
        assertTrue(isAvailable)
    }
    
    @Test
    @DisplayName("Test basic generation availability")
    fun testBasicGenerationAvailability() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns true
        
        fallbackManager.checkFallbackMode()
        
        // When
        val isAvailable = fallbackManager.isBasicGenerationAvailable()
        
        // Then
        assertTrue(isAvailable)
    }
    
    @Test
    @DisplayName("Test limited functionality availability")
    fun testLimitedFunctionalityAvailability() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns false
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        fallbackManager.checkFallbackMode()
        
        // When
        val isAvailable = fallbackManager.isLimitedFunctionalityAvailable()
        
        // Then
        assertTrue(isAvailable)
    }
    
    @Test
    @DisplayName("Test fallback config retrieval")
    fun testFallbackConfigRetrieval() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns true
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        fallbackManager.checkFallbackMode()
        
        // When
        val config = fallbackManager.getFallbackConfig()
        
        // Then
        assertTrue(config.containsKey("is_in_fallback_mode"))
        assertTrue(config.containsKey("fallback_type"))
        assertTrue(config.containsKey("fallback_reason"))
        assertTrue(config.containsKey("manual_note_entry_available"))
        assertTrue(config.containsKey("basic_generation_available"))
        assertTrue(config.containsKey("limited_functionality_available"))
    }
    
    @Test
    @DisplayName("Test error handling in fallback mode detection")
    fun testErrorHandlingInFallbackModeDetection() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } throws RuntimeException("Test error")
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.ERROR, fallbackType)
        assertTrue(fallbackManager.isInFallbackMode.value)
        assertTrue(fallbackManager.fallbackReason.value.contains("Error checking fallback mode"))
    }
    
    @Test
    @DisplayName("Test error handling in fallback action")
    fun testErrorHandlingInFallbackAction() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        fallbackManager.checkFallbackMode()
        
        // When
        val action = fallbackManager.getFallbackAction()
        
        // Then
        assertEquals(FallbackManager.FallbackAction.NONE, action)
    }
    
    @Test
    @DisplayName("Test manual note entry launch")
    fun testManualNoteEntryLaunch() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns true
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        fallbackManager.checkFallbackMode()
        
        // When
        fallbackManager.launchManualNoteEntry()
        
        // Then
        // Should not throw exception
        assertTrue(fallbackManager.isManualNoteEntryAvailable())
    }
    
    @Test
    @DisplayName("Test fallback mode priority order")
    fun testFallbackModePriorityOrder() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns true
        every { mockKillSwitchManager.isAppKilled() } returns true
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns true
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns true
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.EMERGENCY, fallbackType) // Emergency should take priority
    }
    
    @Test
    @DisplayName("Test fallback mode deactivation")
    fun testFallbackModeDeactivation() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns false
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        // First activate fallback mode
        fallbackManager.checkFallbackMode()
        assertTrue(fallbackManager.isInFallbackMode.value)
        
        // Then fix the issue
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        
        // When
        val fallbackType = fallbackManager.checkFallbackMode()
        
        // Then
        assertEquals(FallbackManager.FallbackType.NONE, fallbackType)
        assertFalse(fallbackManager.isInFallbackMode.value)
        assertEquals("", fallbackManager.fallbackReason.value)
    }
    
    @Test
    @DisplayName("Test concurrent fallback mode checking")
    fun testConcurrentFallbackModeChecking() {
        // Given
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        every { mockKillSwitchManager.isAppKilled() } returns false
        every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
        every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) {
                    fallbackManager.checkFallbackMode()
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertFalse(fallbackManager.isInFallbackMode.value)
    }
}
