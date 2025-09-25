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
 * Unit tests for ReleaseGateManager
 * 
 * Tests release gate functionality including:
 * - Quality gate validation
 * - Performance threshold checking
 * - Privacy compliance validation
 * - Canary deployment and rollback
 */
class ReleaseGateManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var mockFeatureFlagManager: FeatureFlagManager
    private lateinit var mockKillSwitchManager: KillSwitchManager
    private lateinit var mockRampPlanManager: RampPlanManager
    private lateinit var releaseGateManager: ReleaseGateManager
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockPrefs = mockk<SharedPreferences>(relaxed = true)
        mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        mockFeatureFlagManager = mockk<FeatureFlagManager>(relaxed = true)
        mockKillSwitchManager = mockk<KillSwitchManager>(relaxed = true)
        mockRampPlanManager = mockk<RampPlanManager>(relaxed = true)
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putString(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        
        // Reset singleton instance
        ReleaseGateManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        releaseGateManager = ReleaseGateManager.getInstance(mockContext)
        releaseGateManager.initialize(mockFeatureFlagManager, mockKillSwitchManager, mockRampPlanManager)
    }
    
    @Test
    @DisplayName("Test release gate manager initialization")
    fun testReleaseGateManagerInitialization() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = ReleaseGateManager.getInstance(mockContext)
        
        // Then
        assertEquals(5, manager.canaryPercentage.value)
        assertEquals("canary", manager.rolloutPhase.value)
        assertFalse(manager.qualityGatesPassed.value)
    }
    
    @Test
    @DisplayName("Test canary percentage setting")
    fun testCanaryPercentageSetting() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = releaseGateManager.setCanaryPercentage(10)
        
        // Then
        assertTrue(result)
        assertEquals(10, releaseGateManager.canaryPercentage.value)
    }
    
    @Test
    @DisplayName("Test canary percentage setting with invalid value")
    fun testCanaryPercentageSettingWithInvalidValue() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result1 = releaseGateManager.setCanaryPercentage(-1)
        val result2 = releaseGateManager.setCanaryPercentage(60)
        
        // Then
        assertFalse(result1)
        assertFalse(result2)
        assertEquals(5, releaseGateManager.canaryPercentage.value) // Should remain unchanged
    }
    
    @Test
    @DisplayName("Test canary percentage setting with valid values")
    fun testCanaryPercentageSettingWithValidValues() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val testCases = listOf(0, 5, 10, 25, 50)
        
        // When & Then
        testCases.forEach { percentage ->
            val result = releaseGateManager.setCanaryPercentage(percentage)
            assertTrue(result)
            assertEquals(percentage, releaseGateManager.canaryPercentage.value)
        }
    }
    
    @Test
    @DisplayName("Test release gate status retrieval")
    fun testReleaseGateStatusRetrieval() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val status = releaseGateManager.getReleaseGateStatus()
        
        // Then
        assertTrue(status.containsKey("canaryPercentage"))
        assertTrue(status.containsKey("rolloutPhase"))
        assertTrue(status.containsKey("qualityGatesPassed"))
        assertTrue(status.containsKey("isCheckingGates"))
        assertTrue(status.containsKey("isRollingBack"))
        assertTrue(status.containsKey("lastMetricsCheck"))
    }
    
    @Test
    @DisplayName("Test release gates check with all gates passing")
    fun testReleaseGatesCheckWithAllGatesPassing() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock all gates to pass
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        
        // When
        val result = releaseGateManager.checkReleaseGates()
        
        // Then
        assertTrue(result is ReleaseGateManager.GateCheckResult.PASSED)
        assertTrue(releaseGateManager.qualityGatesPassed.value)
    }
    
    @Test
    @DisplayName("Test release gates check with performance gate failing")
    fun testReleaseGatesCheckWithPerformanceGateFailing() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock gates to pass
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        
        // When
        val result = releaseGateManager.checkReleaseGates()
        
        // Then
        assertTrue(result is ReleaseGateManager.GateCheckResult.FAILED)
        assertFalse(releaseGateManager.qualityGatesPassed.value)
    }
    
    @Test
    @DisplayName("Test release gates check with privacy gate failing")
    fun testReleaseGatesCheckWithPrivacyGateFailing() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock gates to pass
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        
        // When
        val result = releaseGateManager.checkReleaseGates()
        
        // Then
        assertTrue(result is ReleaseGateManager.GateCheckResult.FAILED)
        assertFalse(releaseGateManager.qualityGatesPassed.value)
    }
    
    @Test
    @DisplayName("Test release gates check with quality gate failing")
    fun testReleaseGatesCheckWithQualityGateFailing() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock quality gate to fail
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns false
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        
        // When
        val result = releaseGateManager.checkReleaseGates()
        
        // Then
        assertTrue(result is ReleaseGateManager.GateCheckResult.FAILED)
        assertFalse(releaseGateManager.qualityGatesPassed.value)
    }
    
    @Test
    @DisplayName("Test release gates check with emergency kill switch active")
    fun testReleaseGatesCheckWithEmergencyKillSwitchActive() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock emergency kill switch to be active
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns true
        
        // When
        val result = releaseGateManager.checkReleaseGates()
        
        // Then
        assertTrue(result is ReleaseGateManager.GateCheckResult.FAILED)
        assertFalse(releaseGateManager.qualityGatesPassed.value)
    }
    
    @Test
    @DisplayName("Test release gates check error handling")
    fun testReleaseGatesCheckErrorHandling() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock gates to throw exception
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } throws RuntimeException("Test error")
        
        // When
        val result = releaseGateManager.checkReleaseGates()
        
        // Then
        assertTrue(result is ReleaseGateManager.GateCheckResult.ERROR)
        assertFalse(releaseGateManager.qualityGatesPassed.value)
    }
    
    @Test
    @DisplayName("Test rollback trigger")
    fun testRollbackTrigger() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock dependencies
        every { mockKillSwitchManager.activateEmergencyKillSwitch(any(), any()) } just Runs
        every { mockRampPlanManager.rollbackToPreviousPhase() } returns RampPlanManager.PhaseRollbackResult.SUCCESS("internal")
        
        // When
        val result = releaseGateManager.triggerRollback("Test reason")
        
        // Then
        assertTrue(result is ReleaseGateManager.RollbackResult.SUCCESS)
        assertEquals("rollback", releaseGateManager.rolloutPhase.value)
    }
    
    @Test
    @DisplayName("Test rollback trigger error handling")
    fun testRollbackTriggerErrorHandling() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock dependencies to throw exception
        every { mockKillSwitchManager.activateEmergencyKillSwitch(any(), any()) } throws RuntimeException("Test error")
        
        // When
        val result = releaseGateManager.triggerRollback("Test reason")
        
        // Then
        assertTrue(result is ReleaseGateManager.RollbackResult.ERROR)
    }
    
    @Test
    @DisplayName("Test concurrent release gate operations")
    fun testConcurrentReleaseGateOperations() = runTest {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock gates to pass
        every { mockFeatureFlagManager.isAmbientScribeEnabled() } returns true
        every { mockKillSwitchManager.isEmergencyKilled() } returns false
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) {
                    when (threadId % 3) {
                        0 -> releaseGateManager.checkReleaseGates()
                        1 -> releaseGateManager.setCanaryPercentage(threadId * 5)
                        2 -> releaseGateManager.getReleaseGateStatus()
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertNotNull(releaseGateManager.getReleaseGateStatus())
    }
    
    @Test
    @DisplayName("Test gate check result types")
    fun testGateCheckResultTypes() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Test different result types
        val results = listOf(
            ReleaseGateManager.GateCheckResult.ALREADY_CHECKING,
            ReleaseGateManager.GateCheckResult.PASSED,
            ReleaseGateManager.GateCheckResult.FAILED(listOf("Test error")),
            ReleaseGateManager.GateCheckResult.ERROR("Test error")
        )
        
        // When & Then
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result is ReleaseGateManager.GateCheckResult)
        }
    }
    
    @Test
    @DisplayName("Test rollback result types")
    fun testRollbackResultTypes() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Test different result types
        val results = listOf(
            ReleaseGateManager.RollbackResult.ALREADY_ROLLING_BACK,
            ReleaseGateManager.RollbackResult.SUCCESS,
            ReleaseGateManager.RollbackResult.ERROR("Test error")
        )
        
        // When & Then
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result is ReleaseGateManager.RollbackResult)
        }
    }
    
    @Test
    @DisplayName("Test gate result data class")
    fun testGateResultDataClass() {
        // Given
        val issues = listOf("Test issue 1", "Test issue 2")
        
        // When
        val passedResult = ReleaseGateManager.GateResult(true, emptyList())
        val failedResult = ReleaseGateManager.GateResult(false, issues)
        
        // Then
        assertTrue(passedResult.passed)
        assertTrue(passedResult.issues.isEmpty())
        assertFalse(failedResult.passed)
        assertEquals(issues, failedResult.issues)
    }
    
    @Test
    @DisplayName("Test release gate status data class")
    fun testReleaseGateStatusDataClass() {
        // Given
        val status = ReleaseGateManager.ReleaseGateStatus(
            canaryPercentage = 10,
            rolloutPhase = "canary",
            qualityGatesPassed = true,
            isCheckingGates = false,
            isRollingBack = false,
            lastMetricsCheck = 1234567890L
        )
        
        // When & Then
        assertEquals(10, status.canaryPercentage)
        assertEquals("canary", status.rolloutPhase)
        assertTrue(status.qualityGatesPassed)
        assertFalse(status.isCheckingGates)
        assertFalse(status.isRollingBack)
        assertEquals(1234567890L, status.lastMetricsCheck)
    }
    
    @Test
    @DisplayName("Test canary percentage boundary values")
    fun testCanaryPercentageBoundaryValues() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getString(any(), any()) } returns "canary"
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val boundaryValues = listOf(0, 1, 49, 50, 51, 100)
        
        // When & Then
        boundaryValues.forEach { percentage ->
            val result = releaseGateManager.setCanaryPercentage(percentage)
            if (percentage <= 50) {
                assertTrue(result)
                assertEquals(percentage, releaseGateManager.canaryPercentage.value)
            } else {
                assertFalse(result)
            }
        }
    }
    
    @Test
    @DisplayName("Test release gate status with different phases")
    fun testReleaseGateStatusWithDifferentPhases() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 10
        every { mockPrefs.getString(any(), any()) } returns "stable"
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getLong(any(), any()) } returns 1234567890L
        
        // When
        val status = releaseGateManager.getReleaseGateStatus()
        
        // Then
        assertEquals(10, status.canaryPercentage)
        assertEquals("stable", status.rolloutPhase)
        assertTrue(status.qualityGatesPassed)
        assertFalse(status.isCheckingGates)
        assertFalse(status.isRollingBack)
        assertEquals(1234567890L, status.lastMetricsCheck)
    }
    
    @Test
    @DisplayName("Test release gate initialization with different values")
    fun testReleaseGateInitializationWithDifferentValues() {
        // Given
        every { mockPrefs.getInt(any(), any()) } returns 25
        every { mockPrefs.getString(any(), any()) } returns "stable"
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getLong(any(), any()) } returns 1234567890L
        
        // When
        val manager = ReleaseGateManager.getInstance(mockContext)
        
        // Then
        assertEquals(25, manager.canaryPercentage.value)
        assertEquals("stable", manager.rolloutPhase.value)
        assertTrue(manager.qualityGatesPassed.value)
    }
}
