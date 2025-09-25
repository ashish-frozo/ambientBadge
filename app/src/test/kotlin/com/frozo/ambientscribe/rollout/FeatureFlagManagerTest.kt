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
 * Unit tests for FeatureFlagManager
 * 
 * Tests feature flag functionality including:
 * - Flag initialization and state management
 * - A/B testing group assignment
 * - Remote configuration updates
 * - Error handling and graceful degradation
 */
class FeatureFlagManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var featureFlagManager: FeatureFlagManager
    
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
        FeatureFlagManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        featureFlagManager = FeatureFlagManager.getInstance(mockContext)
    }
    
    @Test
    @DisplayName("Test feature flag initialization with default values")
    fun testFeatureFlagInitialization() {
        // Given
        every { mockPrefs.getBoolean("ambient_scribe_enabled", true) } returns true
        every { mockPrefs.getBoolean("llm_processing_enabled", true) } returns true
        every { mockPrefs.getBoolean("te_language_enabled", false) } returns false
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = FeatureFlagManager.getInstance(mockContext)
        
        // Then
        assertTrue(manager.isAmbientScribeEnabled())
        assertTrue(manager.isLLMProcessingEnabled())
        assertFalse(manager.isTeLanguageEnabled())
    }
    
    @Test
    @DisplayName("Test feature flag state changes")
    fun testFeatureFlagStateChanges() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        featureFlagManager.setAmbientScribeEnabled(false, "test")
        featureFlagManager.setLLMProcessingEnabled(false, "test")
        featureFlagManager.setTeLanguageEnabled(true, "test")
        
        // Then
        assertFalse(featureFlagManager.isAmbientScribeEnabled())
        assertFalse(featureFlagManager.isLLMProcessingEnabled())
        assertTrue(featureFlagManager.isTeLanguageEnabled())
    }
    
    @Test
    @DisplayName("Test LLM processing disabled when ambient scribe is disabled")
    fun testLLMProcessingDisabledWhenAmbientScribeDisabled() {
        // Given
        every { mockPrefs.getBoolean("ambient_scribe_enabled", true) } returns false
        every { mockPrefs.getBoolean("llm_processing_enabled", true) } returns true
        every { mockPrefs.getBoolean("te_language_enabled", false) } returns false
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = FeatureFlagManager.getInstance(mockContext)
        
        // Then
        assertFalse(manager.isAmbientScribeEnabled())
        assertFalse(manager.isLLMProcessingEnabled()) // Should be false when ambient scribe is disabled
        assertFalse(manager.isTeLanguageEnabled()) // Should be false when ambient scribe is disabled
    }
    
    @Test
    @DisplayName("Test A/B test group assignment")
    fun testABTestGroupAssignment() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        featureFlagManager.setABTestGroup("test_feature", "test_a")
        
        // Then
        assertEquals("test_a", featureFlagManager.getABTestGroup("test_feature"))
        assertEquals("control", featureFlagManager.getABTestGroup("unknown_feature"))
    }
    
    @Test
    @DisplayName("Test remote configuration update")
    fun testRemoteConfigurationUpdate() = runTest {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val config = mapOf(
            "ambient_scribe_enabled" to false,
            "llm_processing_enabled" to false,
            "te_language_enabled" to true,
            "ab_test_groups" to mapOf("test_feature" to "test_b")
        )
        
        // When
        featureFlagManager.updateFromRemoteConfig(config)
        
        // Then
        assertFalse(featureFlagManager.isAmbientScribeEnabled())
        assertFalse(featureFlagManager.isLLMProcessingEnabled())
        assertTrue(featureFlagManager.isTeLanguageEnabled())
        assertEquals("test_b", featureFlagManager.getABTestGroup("test_feature"))
    }
    
    @Test
    @DisplayName("Test get all flags")
    fun testGetAllFlags() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val allFlags = featureFlagManager.getAllFlags()
        
        // Then
        assertTrue(allFlags.containsKey("ambient_scribe_enabled"))
        assertTrue(allFlags.containsKey("llm_processing_enabled"))
        assertTrue(allFlags.containsKey("te_language_enabled"))
        assertTrue(allFlags.containsKey("ab_test_groups"))
        assertTrue(allFlags.containsKey("last_update"))
    }
    
    @Test
    @DisplayName("Test reset to defaults")
    fun testResetToDefaults() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        featureFlagManager.resetToDefaults()
        
        // Then
        assertTrue(featureFlagManager.isAmbientScribeEnabled())
        assertTrue(featureFlagManager.isLLMProcessingEnabled())
        assertFalse(featureFlagManager.isTeLanguageEnabled())
    }
    
    @Test
    @DisplayName("Test remote config availability")
    fun testRemoteConfigAvailability() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "https://config.example.com"
        every { mockPrefs.getLong(any(), any()) } returns System.currentTimeMillis()
        
        // When
        val isAvailable = featureFlagManager.isRemoteConfigAvailable()
        
        // Then
        assertTrue(isAvailable)
    }
    
    @Test
    @DisplayName("Test remote config not available when URL is null")
    fun testRemoteConfigNotAvailableWhenUrlIsNull() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns null
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val isAvailable = featureFlagManager.isRemoteConfigAvailable()
        
        // Then
        assertFalse(isAvailable)
    }
    
    @Test
    @DisplayName("Test remote config not available when expired")
    fun testRemoteConfigNotAvailableWhenExpired() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "https://config.example.com"
        every { mockPrefs.getLong(any(), any()) } returns System.currentTimeMillis() - (25 * 60 * 60 * 1000L) // 25 hours ago
        
        // When
        val isAvailable = featureFlagManager.isRemoteConfigAvailable()
        
        // Then
        assertFalse(isAvailable)
    }
    
    @Test
    @DisplayName("Test error handling in flag checking")
    fun testErrorHandlingInFlagChecking() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } throws RuntimeException("Test error")
        
        // When
        val ambientScribeEnabled = featureFlagManager.isAmbientScribeEnabled()
        val llmProcessingEnabled = featureFlagManager.isLLMProcessingEnabled()
        val teLanguageEnabled = featureFlagManager.isTeLanguageEnabled()
        
        // Then
        assertFalse(ambientScribeEnabled) // Should return false on error
        assertFalse(llmProcessingEnabled) // Should return false on error
        assertFalse(teLanguageEnabled) // Should return false on error
    }
    
    @Test
    @DisplayName("Test error handling in flag setting")
    fun testErrorHandlingInFlagSetting() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefsEditor.putBoolean(any(), any()) } throws RuntimeException("Test error")
        
        // When
        featureFlagManager.setAmbientScribeEnabled(false, "test")
        
        // Then
        // Should not throw exception, should handle error gracefully
        assertTrue(featureFlagManager.isAmbientScribeEnabled()) // Should remain unchanged on error
    }
    
    @Test
    @DisplayName("Test error handling in remote config update")
    fun testErrorHandlingInRemoteConfigUpdate() = runTest {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefsEditor.putBoolean(any(), any()) } throws RuntimeException("Test error")
        
        val config = mapOf(
            "ambient_scribe_enabled" to false
        )
        
        // When
        featureFlagManager.updateFromRemoteConfig(config)
        
        // Then
        // Should not throw exception, should handle error gracefully
        assertTrue(featureFlagManager.isAmbientScribeEnabled()) // Should remain unchanged on error
    }
    
    @Test
    @DisplayName("Test A/B test group persistence")
    fun testABTestGroupPersistence() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        featureFlagManager.setABTestGroup("feature1", "group_a")
        featureFlagManager.setABTestGroup("feature2", "group_b")
        
        // Then
        assertEquals("group_a", featureFlagManager.getABTestGroup("feature1"))
        assertEquals("group_b", featureFlagManager.getABTestGroup("feature2"))
        assertEquals("control", featureFlagManager.getABTestGroup("unknown_feature"))
    }
    
    @Test
    @DisplayName("Test concurrent access safety")
    fun testConcurrentAccessSafety() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) {
                    featureFlagManager.setAmbientScribeEnabled(threadId % 2 == 0, "thread_$threadId")
                    featureFlagManager.isAmbientScribeEnabled()
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertTrue(featureFlagManager.isAmbientScribeEnabled())
    }
}
