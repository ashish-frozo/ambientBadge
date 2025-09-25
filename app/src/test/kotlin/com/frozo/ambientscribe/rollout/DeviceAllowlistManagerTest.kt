package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for DeviceAllowlistManager
 * 
 * Tests device allowlist functionality including:
 * - Device fingerprinting and identification
 * - Pilot phase management
 * - Allowlist validation
 * - Device requirements checking
 */
class DeviceAllowlistManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var allowlistManager: DeviceAllowlistManager
    
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
        
        // Mock Build constants
        mockkStatic(Build::class)
        every { Build.MANUFACTURER } returns "TestManufacturer"
        every { Build.MODEL } returns "TestModel"
        every { Build.VERSION.RELEASE } returns "13"
        every { Build.FINGERPRINT } returns "test/fingerprint/123"
        
        // Reset singleton instance
        DeviceAllowlistManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        allowlistManager = DeviceAllowlistManager.getInstance(mockContext)
    }
    
    @Test
    @DisplayName("Test device allowlist initialization")
    fun testDeviceAllowlistInitialization() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = DeviceAllowlistManager.getInstance(mockContext)
        
        // Then
        assertEquals("internal", manager.getCurrentPilotPhase())
        assertFalse(manager.isDeviceAllowed())
    }
    
    @Test
    @DisplayName("Test device ID generation")
    fun testDeviceIdGeneration() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns null
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val deviceInfo = allowlistManager.getDeviceInfo()
        
        // Then
        assertTrue(deviceInfo.containsKey("device_id"))
        assertTrue(deviceInfo.containsKey("device_fingerprint"))
        assertTrue(deviceInfo.containsKey("manufacturer"))
        assertTrue(deviceInfo.containsKey("model"))
        assertTrue(deviceInfo.containsKey("version"))
        assertTrue(deviceInfo.containsKey("sdk_version"))
    }
    
    @Test
    @DisplayName("Test device allowlist for internal phase")
    fun testDeviceAllowlistForInternalPhase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val isAllowed = allowlistManager.isDeviceAllowed()
        
        // Then
        assertFalse(isAllowed) // Internal phase requires explicit allowlist
    }
    
    @Test
    @DisplayName("Test device allowlist for production phase")
    fun testDeviceAllowlistForProductionPhase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "production"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val isAllowed = allowlistManager.isDeviceAllowed()
        
        // Then
        assertTrue(isAllowed) // Production phase allows all devices
    }
    
    @Test
    @DisplayName("Test pilot phase advancement")
    fun testPilotPhaseAdvancement() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = allowlistManager.advanceToNextPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.SUCCESS)
        assertEquals("pilot_1", (result as RampPlanManager.PhaseAdvanceResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test pilot phase rollback")
    fun testPilotPhaseRollback() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = allowlistManager.rollbackToPreviousPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseRollbackResult.SUCCESS)
        assertEquals("internal", (result as RampPlanManager.PhaseRollbackResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test device requirements checking")
    fun testDeviceRequirementsChecking() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val requirements = allowlistManager.checkDeviceRequirements()
        
        // Then
        assertNotNull(requirements)
        assertTrue(requirements.meetsRequirements || !requirements.requirements.isEmpty())
        assertTrue(requirements.requirements.isNotEmpty() || requirements.warnings.isNotEmpty())
    }
    
    @Test
    @DisplayName("Test allowlist update from remote source")
    fun testAllowlistUpdateFromRemoteSource() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val allowlistData = mapOf(
            "internal" to listOf("device1", "device2"),
            "pilot_1" to listOf("device3", "device4"),
            "pilot_2" to listOf("device5", "device6")
        )
        
        // When
        allowlistManager.updateAllowlists(allowlistData)
        
        // Then
        // Should not throw exception
        assertNotNull(allowlistManager.getAllowlistStatus())
    }
    
    @Test
    @DisplayName("Test device addition to allowlist")
    fun testDeviceAdditionToAllowlist() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        allowlistManager.addDeviceToAllowlist("internal", "test_device")
        
        // Then
        // Should not throw exception
        assertNotNull(allowlistManager.getAllowlistStatus())
    }
    
    @Test
    @DisplayName("Test device removal from allowlist")
    fun testDeviceRemovalFromAllowlist() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        allowlistManager.removeDeviceFromAllowlist("internal", "test_device")
        
        // Then
        // Should not throw exception
        assertNotNull(allowlistManager.getAllowlistStatus())
    }
    
    @Test
    @DisplayName("Test allowlist status retrieval")
    fun testAllowlistStatusRetrieval() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val status = allowlistManager.getAllowlistStatus()
        
        // Then
        assertTrue(status.containsKey("current_phase"))
        assertTrue(status.containsKey("is_allowed"))
        assertTrue(status.containsKey("allowlists"))
        assertTrue(status.containsKey("last_update"))
        assertTrue(status.containsKey("allowlist_version"))
    }
    
    @Test
    @DisplayName("Test device info retrieval")
    fun testDeviceInfoRetrieval() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val deviceInfo = allowlistManager.getDeviceInfo()
        
        // Then
        assertTrue(deviceInfo.containsKey("device_id"))
        assertTrue(deviceInfo.containsKey("device_fingerprint"))
        assertTrue(deviceInfo.containsKey("manufacturer"))
        assertTrue(deviceInfo.containsKey("model"))
        assertTrue(deviceInfo.containsKey("version"))
        assertTrue(deviceInfo.containsKey("sdk_version"))
        assertTrue(deviceInfo.containsKey("current_pilot_phase"))
        assertTrue(deviceInfo.containsKey("is_allowed"))
        assertTrue(deviceInfo.containsKey("last_update"))
        assertTrue(deviceInfo.containsKey("allowlist_version"))
    }
    
    @Test
    @DisplayName("Test error handling in device allowlist checking")
    fun testErrorHandlingInDeviceAllowlistChecking() {
        // Given
        every { mockPrefs.getString(any(), any()) } throws RuntimeException("Test error")
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val isAllowed = allowlistManager.isDeviceAllowed()
        
        // Then
        assertFalse(isAllowed) // Should return false on error (fail-safe)
    }
    
    @Test
    @DisplayName("Test error handling in pilot phase advancement")
    fun testErrorHandlingInPilotPhaseAdvancement() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefsEditor.putString(any(), any()) } throws RuntimeException("Test error")
        
        // When
        val result = allowlistManager.advanceToNextPhase()
        
        // Then
        // Should not throw exception, should handle error gracefully
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.ERROR)
    }
    
    @Test
    @DisplayName("Test error handling in device requirements checking")
    fun testErrorHandlingInDeviceRequirementsChecking() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock Runtime.getRuntime() to throw exception
        mockkStatic(Runtime::class)
        val mockRuntime = mockk<Runtime>()
        every { Runtime.getRuntime() } returns mockRuntime
        every { mockRuntime.maxMemory() } throws RuntimeException("Test error")
        
        // When
        val requirements = allowlistManager.checkDeviceRequirements()
        
        // Then
        assertFalse(requirements.meetsRequirements)
        assertTrue(requirements.requirements.contains("Error checking requirements"))
    }
    
    @Test
    @DisplayName("Test concurrent allowlist operations")
    fun testConcurrentAllowlistOperations() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) {
                    when (threadId % 3) {
                        0 -> allowlistManager.advanceToNextPhase()
                        1 -> allowlistManager.addDeviceToAllowlist("internal", "device_$threadId")
                        2 -> allowlistManager.isDeviceAllowed()
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertNotNull(allowlistManager.getAllowlistStatus())
    }
    
    @Test
    @DisplayName("Test pilot phase advancement from production")
    fun testPilotPhaseAdvancementFromProduction() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "production"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = allowlistManager.advanceToNextPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.ALREADY_AT_MAX_PHASE)
    }
    
    @Test
    @DisplayName("Test pilot phase rollback from internal")
    fun testPilotPhaseRollbackFromInternal() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = allowlistManager.rollbackToPreviousPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseRollbackResult.ALREADY_AT_MIN_PHASE)
    }
    
    @Test
    @DisplayName("Test device fingerprinting consistency")
    fun testDeviceFingerprintingConsistency() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val deviceInfo1 = allowlistManager.getDeviceInfo()
        val deviceInfo2 = allowlistManager.getDeviceInfo()
        
        // Then
        assertEquals(deviceInfo1["device_id"], deviceInfo2["device_id"])
        assertEquals(deviceInfo1["device_fingerprint"], deviceInfo2["device_fingerprint"])
    }
    
    @Test
    @DisplayName("Test allowlist status with different phases")
    fun testAllowlistStatusWithDifferentPhases() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_2"
        every { mockPrefs.getInt(any(), any()) } returns 25
        every { mockPrefs.getLong(any(), any()) } returns 1234567890L
        
        // When
        val status = allowlistManager.getAllowlistStatus()
        
        // Then
        assertEquals("pilot_2", status["current_phase"])
        assertTrue(status.containsKey("is_allowed"))
        assertTrue(status.containsKey("allowlists"))
        assertTrue(status.containsKey("last_update"))
        assertTrue(status.containsKey("allowlist_version"))
    }
}
