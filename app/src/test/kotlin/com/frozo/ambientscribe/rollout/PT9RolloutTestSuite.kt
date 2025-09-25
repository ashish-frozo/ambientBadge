package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest

/**
 * Comprehensive test suite for PT-9 Rollout and Guardrails
 * 
 * Tests all rollout components:
 * - Feature flags
 * - Kill switches
 * - Fallback mechanisms
 * - Device allowlists
 * - Model swapping
 * - Ramp plans
 * - OEM permission playbooks
 * - Notification permissions
 * - Release gates
 * - Remote config
 * - Upload policies
 */
class PT9RolloutTestSuite {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    
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
    }
    
    @Nested
    @DisplayName("Feature Flag Manager Tests")
    inner class FeatureFlagManagerTests {
        
        @Test
        @DisplayName("Test feature flag initialization")
        fun testFeatureFlagInitialization() {
            // Given
            every { mockPrefs.getBoolean("ambient_scribe_enabled", true) } returns true
            every { mockPrefs.getBoolean("llm_processing_enabled", true) } returns true
            every { mockPrefs.getBoolean("te_language_enabled", false) } returns false
            
            // When
            val featureFlagManager = FeatureFlagManager.getInstance(mockContext)
            
            // Then
            assertTrue(featureFlagManager.isAmbientScribeEnabled())
            assertTrue(featureFlagManager.isLLMProcessingEnabled())
            assertFalse(featureFlagManager.isTeLanguageEnabled())
        }
        
        @Test
        @DisplayName("Test feature flag state changes")
        fun testFeatureFlagStateChanges() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns false
            val featureFlagManager = FeatureFlagManager.getInstance(mockContext)
            
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
        @DisplayName("Test A/B test group assignment")
        fun testABTestGroupAssignment() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns true
            every { mockPrefs.getString(any(), any()) } returns "{}"
            val featureFlagManager = FeatureFlagManager.getInstance(mockContext)
            
            // When
            featureFlagManager.setABTestGroup("test_feature", "test_a")
            
            // Then
            assertEquals("test_a", featureFlagManager.getABTestGroup("test_feature"))
        }
    }
    
    @Nested
    @DisplayName("Kill Switch Manager Tests")
    inner class KillSwitchManagerTests {
        
        @Test
        @DisplayName("Test kill switch initialization")
        fun testKillSwitchInitialization() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns false
            
            // When
            val killSwitchManager = KillSwitchManager.getInstance(mockContext)
            
            // Then
            assertFalse(killSwitchManager.isAudioCaptureKilled())
            assertFalse(killSwitchManager.isLLMProcessingKilled())
            assertFalse(killSwitchManager.isAppKilled())
            assertFalse(killSwitchManager.isEmergencyKilled())
        }
        
        @Test
        @DisplayName("Test kill switch activation")
        fun testKillSwitchActivation() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns false
            val killSwitchManager = KillSwitchManager.getInstance(mockContext)
            
            // When
            killSwitchManager.killAudioCapture("test reason", "test_user")
            killSwitchManager.killLLMProcessing("test reason", "test_user")
            killSwitchManager.killApp("test reason", "test_user")
            killSwitchManager.activateEmergencyKillSwitch("test reason", "test_user")
            
            // Then
            assertTrue(killSwitchManager.isAudioCaptureKilled())
            assertTrue(killSwitchManager.isLLMProcessingKilled())
            assertTrue(killSwitchManager.isAppKilled())
            assertTrue(killSwitchManager.isEmergencyKilled())
        }
        
        @Test
        @DisplayName("Test kill switch restoration")
        fun testKillSwitchRestoration() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns true
            val killSwitchManager = KillSwitchManager.getInstance(mockContext)
            
            // When
            killSwitchManager.restoreAudioCapture("test_user")
            killSwitchManager.restoreLLMProcessing("test_user")
            killSwitchManager.restoreApp("test_user")
            killSwitchManager.deactivateEmergencyKillSwitch("test_user")
            
            // Then
            assertFalse(killSwitchManager.isAudioCaptureKilled())
            assertFalse(killSwitchManager.isLLMProcessingKilled())
            assertFalse(killSwitchManager.isAppKilled())
            assertFalse(killSwitchManager.isEmergencyKilled())
        }
    }
    
    @Nested
    @DisplayName("Fallback Manager Tests")
    inner class FallbackManagerTests {
        
        @Test
        @DisplayName("Test fallback mode detection")
        fun testFallbackModeDetection() {
            // Given
            val fallbackManager = FallbackManager.getInstance(mockContext)
            val mockFeatureFlagManager = mockk<FeatureFlagManager>(relaxed = true)
            val mockKillSwitchManager = mockk<KillSwitchManager>(relaxed = true)
            
            every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns false
            every { mockKillSwitchManager.isEmergencyKilled() } returns false
            every { mockKillSwitchManager.isAppKilled() } returns false
            every { mockKillSwitchManager.isAudioCaptureKilled() } returns false
            every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
            
            fallbackManager.initialize(mockFeatureFlagManager, mockKillSwitchManager)
            
            // When
            val fallbackType = fallbackManager.checkFallbackMode()
            
            // Then
            assertEquals(FallbackManager.FallbackType.FEATURE_DISABLED, fallbackType)
            assertTrue(fallbackManager.isInFallbackMode.value)
        }
        
        @Test
        @DisplayName("Test fallback message generation")
        fun testFallbackMessageGeneration() {
            // Given
            val fallbackManager = FallbackManager.getInstance(mockContext)
            val mockFeatureFlagManager = mockk<FeatureFlagManager>(relaxed = true)
            val mockKillSwitchManager = mockk<KillSwitchManager>(relaxed = true)
            
            every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
            every { mockKillSwitchManager.isEmergencyKilled() } returns false
            every { mockKillSwitchManager.isAppKilled() } returns false
            every { mockKillSwitchManager.isAudioCaptureKilled() } returns true
            every { mockKillSwitchManager.isLLMProcessingKilled() } returns false
            
            fallbackManager.initialize(mockFeatureFlagManager, mockKillSwitchManager)
            fallbackManager.checkFallbackMode()
            
            // When
            val message = fallbackManager.getFallbackMessage()
            
            // Then
            assertTrue(message.contains("Audio disabled"))
        }
    }
    
    @Nested
    @DisplayName("Device Allowlist Manager Tests")
    inner class DeviceAllowlistManagerTests {
        
        @Test
        @DisplayName("Test device allowlist initialization")
        fun testDeviceAllowlistInitialization() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "internal"
            every { mockPrefs.getInt(any(), any()) } returns 0
            every { mockPrefs.getLong(any(), any()) } returns 0L
            
            // When
            val allowlistManager = DeviceAllowlistManager.getInstance(mockContext)
            
            // Then
            assertEquals("internal", allowlistManager.getCurrentPilotPhase())
            assertFalse(allowlistManager.isDeviceAllowed())
        }
        
        @Test
        @DisplayName("Test pilot phase advancement")
        fun testPilotPhaseAdvancement() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "internal"
            every { mockPrefs.getInt(any(), any()) } returns 0
            every { mockPrefs.getLong(any(), any()) } returns 0L
            val allowlistManager = DeviceAllowlistManager.getInstance(mockContext)
            
            // When
            val result = allowlistManager.advanceToNextPhase()
            
            // Then
            assertTrue(result is RampPlanManager.PhaseAdvanceResult.SUCCESS)
            assertEquals("pilot_1", (result as RampPlanManager.PhaseAdvanceResult.SUCCESS).newPhase)
        }
        
        @Test
        @DisplayName("Test device requirements checking")
        fun testDeviceRequirementsChecking() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "internal"
            every { mockPrefs.getInt(any(), any()) } returns 0
            every { mockPrefs.getLong(any(), any()) } returns 0L
            val allowlistManager = DeviceAllowlistManager.getInstance(mockContext)
            
            // When
            val requirements = allowlistManager.checkDeviceRequirements()
            
            // Then
            assertNotNull(requirements)
            assertTrue(requirements.meetsRequirements || !requirements.requirements.isEmpty())
        }
    }
    
    @Nested
    @DisplayName("Model Swap Manager Tests")
    inner class ModelSwapManagerTests {
        
        @Test
        @DisplayName("Test model swap initialization")
        fun testModelSwapInitialization() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "default"
            every { mockPrefs.getInt(any(), any()) } returns 14
            every { mockPrefs.getLong(any(), any()) } returns 0L
            
            // When
            val modelSwapManager = ModelSwapManager.getInstance(mockContext)
            
            // Then
            assertEquals("default", modelSwapManager.currentModel.value)
            assertEquals("1.0.0", modelSwapManager.modelVersion.value)
        }
        
        @Test
        @DisplayName("Test model validation")
        fun testModelValidation() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "default"
            every { mockPrefs.getInt(any(), any()) } returns 14
            every { mockPrefs.getLong(any(), any()) } returns 0L
            val modelSwapManager = ModelSwapManager.getInstance(mockContext)
            
            val modelFiles = mapOf(
                "model.bin" to "test model data".toByteArray(),
                "config.json" to "{\"version\": \"1.0.0\"}".toByteArray(),
                "vocab.txt" to "test vocab".toByteArray()
            )
            
            // When
            val validationResult = modelSwapManager.validateModel(modelFiles)
            
            // Then
            assertTrue(validationResult.isValid)
            assertTrue(validationResult.errors.isEmpty())
        }
    }
    
    @Nested
    @DisplayName("Ramp Plan Manager Tests")
    inner class RampPlanManagerTests {
        
        @Test
        @DisplayName("Test ramp plan initialization")
        fun testRampPlanInitialization() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "internal"
            every { mockPrefs.getInt(any(), any()) } returns 0
            every { mockPrefs.getLong(any(), any()) } returns 0L
            
            // When
            val rampPlanManager = RampPlanManager.getInstance(mockContext)
            
            // Then
            assertEquals("internal", rampPlanManager.getCurrentPilotPhase())
            assertFalse(rampPlanManager.hasFeatureAccess())
        }
        
        @Test
        @DisplayName("Test phase advancement")
        fun testPhaseAdvancement() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "internal"
            every { mockPrefs.getInt(any(), any()) } returns 0
            every { mockPrefs.getLong(any(), any()) } returns 0L
            val rampPlanManager = RampPlanManager.getInstance(mockContext)
            
            // When
            val result = rampPlanManager.advanceToNextPhase()
            
            // Then
            assertTrue(result is RampPlanManager.PhaseAdvanceResult.SUCCESS)
            assertEquals("pilot_1", (result as RampPlanManager.PhaseAdvanceResult.SUCCESS).newPhase)
        }
        
        @Test
        @DisplayName("Test phase rollback")
        fun testPhaseRollback() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "pilot_1"
            every { mockPrefs.getInt(any(), any()) } returns 5
            every { mockPrefs.getLong(any(), any()) } returns 0L
            val rampPlanManager = RampPlanManager.getInstance(mockContext)
            
            // When
            val result = rampPlanManager.rollbackToPreviousPhase()
            
            // Then
            assertTrue(result is RampPlanManager.PhaseRollbackResult.SUCCESS)
            assertEquals("internal", (result as RampPlanManager.PhaseRollbackResult.SUCCESS).newPhase)
        }
    }
    
    @Nested
    @DisplayName("OEM Permission Playbook Tests")
    inner class OEMPermissionPlaybookTests {
        
        @Test
        @DisplayName("Test OEM detection")
        fun testOEMDetection() {
            // Given
            val playbook = OEMPermissionPlaybook.getInstance(mockContext)
            
            // When
            val oemInfo = playbook.getOEMInfo()
            
            // Then
            assertNotNull(oemInfo["oem"])
            assertNotNull(oemInfo["manufacturer"])
            assertNotNull(oemInfo["brand"])
            assertNotNull(oemInfo["model"])
        }
        
        @Test
        @DisplayName("Test permission guidance generation")
        fun testPermissionGuidanceGeneration() {
            // Given
            val playbook = OEMPermissionPlaybook.getInstance(mockContext)
            
            // When
            val guidance = playbook.getPermissionGuidance("RECORD_AUDIO")
            
            // Then
            assertNotNull(guidance.title)
            assertTrue(guidance.steps.isNotEmpty())
            assertNotNull(guidance.settingsIntent)
            assertNotNull(guidance.helpUrl)
        }
    }
    
    @Nested
    @DisplayName("Notification Permission Manager Tests")
    inner class NotificationPermissionManagerTests {
        
        @Test
        @DisplayName("Test notification permission checking")
        fun testNotificationPermissionChecking() {
            // Given
            val permissionManager = NotificationPermissionManager.getInstance(mockContext)
            
            // When
            val hasPermission = permissionManager.hasNotificationPermission()
            val isRequired = permissionManager.isNotificationPermissionRequired()
            
            // Then
            assertNotNull(hasPermission)
            assertNotNull(isRequired)
        }
        
        @Test
        @DisplayName("Test permission request rationale")
        fun testPermissionRequestRationale() {
            // Given
            every { mockPrefs.getInt(any(), any()) } returns 0
            val permissionManager = NotificationPermissionManager.getInstance(mockContext)
            
            // When
            val rationale = permissionManager.getPermissionRequestRationale()
            
            // Then
            assertNotNull(rationale.title)
            assertNotNull(rationale.message)
            assertTrue(rationale.canRequest)
        }
    }
    
    @Nested
    @DisplayName("Release Gate Manager Tests")
    inner class ReleaseGateManagerTests {
        
        @Test
        @DisplayName("Test release gate initialization")
        fun testReleaseGateInitialization() {
            // Given
            every { mockPrefs.getInt(any(), any()) } returns 5
            every { mockPrefs.getString(any(), any()) } returns "canary"
            every { mockPrefs.getBoolean(any(), any()) } returns false
            every { mockPrefs.getLong(any(), any()) } returns 0L
            
            // When
            val releaseGateManager = ReleaseGateManager.getInstance(mockContext)
            
            // Then
            assertEquals(5, releaseGateManager.canaryPercentage.value)
            assertEquals("canary", releaseGateManager.rolloutPhase.value)
            assertFalse(releaseGateManager.qualityGatesPassed.value)
        }
        
        @Test
        @DisplayName("Test canary percentage setting")
        fun testCanaryPercentageSetting() {
            // Given
            every { mockPrefs.getInt(any(), any()) } returns 5
            every { mockPrefs.getString(any(), any()) } returns "canary"
            every { mockPrefs.getBoolean(any(), any()) } returns false
            every { mockPrefs.getLong(any(), any()) } returns 0L
            val releaseGateManager = ReleaseGateManager.getInstance(mockContext)
            
            // When
            val result = releaseGateManager.setCanaryPercentage(10)
            
            // Then
            assertTrue(result)
            assertEquals(10, releaseGateManager.canaryPercentage.value)
        }
    }
    
    @Nested
    @DisplayName("Remote Config Manager Tests")
    inner class RemoteConfigManagerTests {
        
        @Test
        @DisplayName("Test remote config initialization")
        fun testRemoteConfigInitialization() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "{}"
            every { mockPrefs.getLong(any(), any()) } returns 0L
            
            // When
            val configManager = RemoteConfigManager.getInstance(mockContext)
            
            // Then
            assertFalse(configManager.isConfigurationValid())
            assertEquals("1.0.0", configManager.configVersion.value)
        }
        
        @Test
        @DisplayName("Test config value retrieval")
        fun testConfigValueRetrieval() {
            // Given
            every { mockPrefs.getString(any(), any()) } returns "{}"
            every { mockPrefs.getLong(any(), any()) } returns 0L
            val configManager = RemoteConfigManager.getInstance(mockContext)
            
            // When
            val stringValue = configManager.getStringConfig("test_key", "default")
            val intValue = configManager.getIntConfig("test_key", 42)
            val boolValue = configManager.getBooleanConfig("test_key", true)
            
            // Then
            assertEquals("default", stringValue)
            assertEquals(42, intValue)
            assertTrue(boolValue)
        }
    }
    
    @Nested
    @DisplayName("Upload Policy Manager Tests")
    inner class UploadPolicyManagerTests {
        
        @Test
        @DisplayName("Test upload policy initialization")
        fun testUploadPolicyInitialization() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns true
            every { mockPrefs.getString(any(), any()) } returns "test_clinic"
            every { mockPrefs.getLong(any(), any()) } returns 0L
            
            // When
            val policyManager = UploadPolicyManager.getInstance(mockContext)
            
            // Then
            assertTrue(policyManager.wifiOnly.value)
            assertFalse(policyManager.meteredOk.value)
            assertEquals("test_clinic", policyManager.clinicId.value)
        }
        
        @Test
        @DisplayName("Test upload policy setting")
        fun testUploadPolicySetting() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns false
            every { mockPrefs.getString(any(), any()) } returns ""
            every { mockPrefs.getLong(any(), any()) } returns 0L
            val policyManager = UploadPolicyManager.getInstance(mockContext)
            
            // When
            policyManager.setUploadPolicy("test_clinic", false, true, "2.0.0")
            
            // Then
            assertFalse(policyManager.wifiOnly.value)
            assertTrue(policyManager.meteredOk.value)
            assertEquals("test_clinic", policyManager.clinicId.value)
            assertEquals("2.0.0", policyManager.policyVersion.value)
        }
    }
    
    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        
        @Test
        @DisplayName("Test complete rollout workflow")
        fun testCompleteRolloutWorkflow() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns true
            every { mockPrefs.getString(any(), any()) } returns "internal"
            every { mockPrefs.getInt(any(), any()) } returns 0
            every { mockPrefs.getLong(any(), any()) } returns 0L
            
            val featureFlagManager = FeatureFlagManager.getInstance(mockContext)
            val killSwitchManager = KillSwitchManager.getInstance(mockContext)
            val fallbackManager = FallbackManager.getInstance(mockContext)
            val allowlistManager = DeviceAllowlistManager.getInstance(mockContext)
            val rampPlanManager = RampPlanManager.getInstance(mockContext)
            
            // When
            fallbackManager.initialize(featureFlagManager, killSwitchManager)
            val fallbackType = fallbackManager.checkFallbackMode()
            val isAllowed = allowlistManager.isDeviceAllowed()
            val hasAccess = rampPlanManager.hasFeatureAccess()
            
            // Then
            assertNotNull(fallbackType)
            assertNotNull(isAllowed)
            assertNotNull(hasAccess)
        }
        
        @Test
        @DisplayName("Test error handling and graceful degradation")
        fun testErrorHandlingAndGracefulDegradation() {
            // Given
            every { mockPrefs.getBoolean(any(), any()) } returns true
            every { mockPrefs.getString(any(), any()) } returns "internal"
            every { mockPrefs.getInt(any(), any()) } returns 0
            every { mockPrefs.getLong(any(), any()) } returns 0L
            
            val featureFlagManager = FeatureFlagManager.getInstance(mockContext)
            val killSwitchManager = KillSwitchManager.getInstance(mockContext)
            val fallbackManager = FallbackManager.getInstance(mockContext)
            
            // When
            fallbackManager.initialize(featureFlagManager, killSwitchManager)
            
            // Simulate error conditions
            every { featureFlagManager.isAmbientScribeEnabled() } throws RuntimeException("Test error")
            
            val fallbackType = fallbackManager.checkFallbackMode()
            
            // Then
            assertEquals(FallbackManager.FallbackType.ERROR, fallbackType)
            assertTrue(fallbackManager.isInFallbackMode.value)
        }
    }
}
