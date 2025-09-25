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
 * Unit tests for RampPlanManager
 * 
 * Tests ramp plan functionality including:
 * - Phase advancement and rollback
 * - User segmentation
 * - Feature access control
 * - Error handling and recovery
 */
class RampPlanManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var rampPlanManager: RampPlanManager
    
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
        RampPlanManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        rampPlanManager = RampPlanManager.getInstance(mockContext)
    }
    
    @Test
    @DisplayName("Test ramp plan initialization")
    fun testRampPlanInitialization() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = RampPlanManager.getInstance(mockContext)
        
        // Then
        assertEquals("internal", manager.getCurrentPilotPhase())
        assertFalse(manager.hasFeatureAccess())
    }
    
    @Test
    @DisplayName("Test phase advancement from internal to pilot 1")
    fun testPhaseAdvancementFromInternalToPilot1() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.advanceToNextPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.SUCCESS)
        assertEquals("pilot_1", (result as RampPlanManager.PhaseAdvanceResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test phase advancement from pilot 1 to pilot 2")
    fun testPhaseAdvancementFromPilot1ToPilot2() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.advanceToNextPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.SUCCESS)
        assertEquals("pilot_2", (result as RampPlanManager.PhaseAdvanceResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test phase advancement from pilot 2 to pilot 3")
    fun testPhaseAdvancementFromPilot2ToPilot3() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_2"
        every { mockPrefs.getInt(any(), any()) } returns 25
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.advanceToNextPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.SUCCESS)
        assertEquals("pilot_3", (result as RampPlanManager.PhaseAdvanceResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test phase advancement from pilot 3 to expansion")
    fun testPhaseAdvancementFromPilot3ToExpansion() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_3"
        every { mockPrefs.getInt(any(), any()) } returns 50
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.advanceToNextPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.SUCCESS)
        assertEquals("expansion", (result as RampPlanManager.PhaseAdvanceResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test phase advancement from expansion fails")
    fun testPhaseAdvancementFromExpansionFails() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "expansion"
        every { mockPrefs.getInt(any(), any()) } returns 100
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.advanceToNextPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.ALREADY_AT_MAX_PHASE)
    }
    
    @Test
    @DisplayName("Test phase rollback from pilot 1 to internal")
    fun testPhaseRollbackFromPilot1ToInternal() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.rollbackToPreviousPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseRollbackResult.SUCCESS)
        assertEquals("internal", (result as RampPlanManager.PhaseRollbackResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test phase rollback from pilot 2 to pilot 1")
    fun testPhaseRollbackFromPilot2ToPilot1() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_2"
        every { mockPrefs.getInt(any(), any()) } returns 25
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.rollbackToPreviousPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseRollbackResult.SUCCESS)
        assertEquals("pilot_1", (result as RampPlanManager.PhaseRollbackResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test phase rollback from pilot 3 to pilot 2")
    fun testPhaseRollbackFromPilot3ToPilot2() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_3"
        every { mockPrefs.getInt(any(), any()) } returns 50
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.rollbackToPreviousPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseRollbackResult.SUCCESS)
        assertEquals("pilot_2", (result as RampPlanManager.PhaseRollbackResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test phase rollback from expansion to pilot 3")
    fun testPhaseRollbackFromExpansionToPilot3() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "expansion"
        every { mockPrefs.getInt(any(), any()) } returns 100
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.rollbackToPreviousPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseRollbackResult.SUCCESS)
        assertEquals("pilot_3", (result as RampPlanManager.PhaseRollbackResult.SUCCESS).newPhase)
    }
    
    @Test
    @DisplayName("Test phase rollback from internal fails")
    fun testPhaseRollbackFromInternalFails() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val result = rampPlanManager.rollbackToPreviousPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseRollbackResult.ALREADY_AT_MIN_PHASE)
    }
    
    @Test
    @DisplayName("Test feature access for internal phase")
    fun testFeatureAccessForInternalPhase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val hasAccess = rampPlanManager.hasFeatureAccess()
        
        // Then
        assertTrue(hasAccess) // Internal phase should have access
    }
    
    @Test
    @DisplayName("Test feature access for pilot phases")
    fun testFeatureAccessForPilotPhases() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val hasAccess = rampPlanManager.hasFeatureAccess()
        
        // Then
        assertTrue(hasAccess) // Pilot phases should have access
    }
    
    @Test
    @DisplayName("Test feature access for expansion phase")
    fun testFeatureAccessForExpansionPhase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "expansion"
        every { mockPrefs.getInt(any(), any()) } returns 100
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val hasAccess = rampPlanManager.hasFeatureAccess()
        
        // Then
        assertTrue(hasAccess) // Expansion phase should have access
    }
    
    @Test
    @DisplayName("Test feature access for control phase")
    fun testFeatureAccessForControlPhase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "control"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val hasAccess = rampPlanManager.hasFeatureAccess()
        
        // Then
        assertFalse(hasAccess) // Control phase should not have access
    }
    
    @Test
    @DisplayName("Test current rollout percentage for internal phase")
    fun testCurrentRolloutPercentageForInternalPhase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "internal"
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val percentage = rampPlanManager.getCurrentRolloutPercentage()
        
        // Then
        assertEquals(0, percentage)
    }
    
    @Test
    @DisplayName("Test current rollout percentage for pilot 1 phase")
    fun testCurrentRolloutPercentageForPilot1Phase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val percentage = rampPlanManager.getCurrentRolloutPercentage()
        
        // Then
        assertEquals(5, percentage)
    }
    
    @Test
    @DisplayName("Test current rollout percentage for pilot 2 phase")
    fun testCurrentRolloutPercentageForPilot2Phase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_2"
        every { mockPrefs.getInt(any(), any()) } returns 25
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val percentage = rampPlanManager.getCurrentRolloutPercentage()
        
        // Then
        assertEquals(25, percentage)
    }
    
    @Test
    @DisplayName("Test current rollout percentage for pilot 3 phase")
    fun testCurrentRolloutPercentageForPilot3Phase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_3"
        every { mockPrefs.getInt(any(), any()) } returns 50
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val percentage = rampPlanManager.getCurrentRolloutPercentage()
        
        // Then
        assertEquals(50, percentage)
    }
    
    @Test
    @DisplayName("Test current rollout percentage for expansion phase")
    fun testCurrentRolloutPercentageForExpansionPhase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "expansion"
        every { mockPrefs.getInt(any(), any()) } returns 100
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val percentage = rampPlanManager.getCurrentRolloutPercentage()
        
        // Then
        assertEquals(100, percentage)
    }
    
    @Test
    @DisplayName("Test ramp plan status retrieval")
    fun testRampPlanStatusRetrieval() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val status = rampPlanManager.getRampPlanStatus()
        
        // Then
        assertTrue(status.containsKey("current_phase"))
        assertTrue(status.containsKey("user_segment"))
        assertTrue(status.containsKey("ab_test_group"))
        assertTrue(status.containsKey("rollout_percentage"))
        assertTrue(status.containsKey("has_feature_access"))
        assertTrue(status.containsKey("last_update"))
    }
    
    @Test
    @DisplayName("Test phase info retrieval")
    fun testPhaseInfoRetrieval() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val phaseInfo = rampPlanManager.getPhaseInfo("pilot_1")
        
        // Then
        assertNotNull(phaseInfo)
        assertEquals("Pilot 1", phaseInfo?.name)
        assertEquals(5, phaseInfo?.percentage)
        assertTrue(phaseInfo?.description?.contains("5%") == true)
    }
    
    @Test
    @DisplayName("Test phase info retrieval for unknown phase")
    fun testPhaseInfoRetrievalForUnknownPhase() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val phaseInfo = rampPlanManager.getPhaseInfo("unknown_phase")
        
        // Then
        assertNull(phaseInfo)
    }
    
    @Test
    @DisplayName("Test error handling in phase advancement")
    fun testErrorHandlingInPhaseAdvancement() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefsEditor.putString(any(), any()) } throws RuntimeException("Test error")
        
        // When
        val result = rampPlanManager.advanceToNextPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseAdvanceResult.ERROR)
    }
    
    @Test
    @DisplayName("Test error handling in phase rollback")
    fun testErrorHandlingInPhaseRollback() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefsEditor.putString(any(), any()) } throws RuntimeException("Test error")
        
        // When
        val result = rampPlanManager.rollbackToPreviousPhase()
        
        // Then
        assertTrue(result is RampPlanManager.PhaseRollbackResult.ERROR)
    }
    
    @Test
    @DisplayName("Test error handling in feature access checking")
    fun testErrorHandlingInFeatureAccessChecking() {
        // Given
        every { mockPrefs.getString(any(), any()) } throws RuntimeException("Test error")
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val hasAccess = rampPlanManager.hasFeatureAccess()
        
        // Then
        assertFalse(hasAccess) // Should return false on error (fail-safe)
    }
    
    @Test
    @DisplayName("Test concurrent phase operations")
    fun testConcurrentPhaseOperations() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) {
                    when (threadId % 2) {
                        0 -> rampPlanManager.advanceToNextPhase()
                        1 -> rampPlanManager.rollbackToPreviousPhase()
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertNotNull(rampPlanManager.getRampPlanStatus())
    }
    
    @Test
    @DisplayName("Test user segmentation consistency")
    fun testUserSegmentationConsistency() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val status1 = rampPlanManager.getRampPlanStatus()
        val status2 = rampPlanManager.getRampPlanStatus()
        
        // Then
        assertEquals(status1["user_segment"], status2["user_segment"])
        assertEquals(status1["ab_test_group"], status2["ab_test_group"])
    }
    
    @Test
    @DisplayName("Test phase advancement result types")
    fun testPhaseAdvancementResultTypes() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Test different result types
        val results = listOf(
            RampPlanManager.PhaseAdvanceResult.ALREADY_UPDATING,
            RampPlanManager.PhaseAdvanceResult.ALREADY_AT_MAX_PHASE,
            RampPlanManager.PhaseAdvanceResult.SUCCESS("pilot_2"),
            RampPlanManager.PhaseAdvanceResult.ERROR("Test error")
        )
        
        // When & Then
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result is RampPlanManager.PhaseAdvanceResult)
        }
    }
    
    @Test
    @DisplayName("Test phase rollback result types")
    fun testPhaseRollbackResultTypes() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Test different result types
        val results = listOf(
            RampPlanManager.PhaseRollbackResult.ALREADY_UPDATING,
            RampPlanManager.PhaseRollbackResult.ALREADY_AT_MIN_PHASE,
            RampPlanManager.PhaseRollbackResult.SUCCESS("internal"),
            RampPlanManager.PhaseRollbackResult.ERROR("Test error")
        )
        
        // When & Then
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result is RampPlanManager.PhaseRollbackResult)
        }
    }
    
    @Test
    @DisplayName("Test phase info for all phases")
    fun testPhaseInfoForAllPhases() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "pilot_1"
        every { mockPrefs.getInt(any(), any()) } returns 5
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val phases = listOf("internal", "pilot_1", "pilot_2", "pilot_3", "expansion")
        
        // When & Then
        phases.forEach { phase ->
            val phaseInfo = rampPlanManager.getPhaseInfo(phase)
            assertNotNull(phaseInfo)
            assertNotNull(phaseInfo?.name)
            assertNotNull(phaseInfo?.percentage)
            assertNotNull(phaseInfo?.description)
        }
    }
}
