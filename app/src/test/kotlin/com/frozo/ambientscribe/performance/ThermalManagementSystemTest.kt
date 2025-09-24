package com.frozo.ambientscribe.performance

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for ThermalManagementSystem - PT-6.5, PT-6.11
 */
@RunWith(RobolectricTestRunner::class)
class ThermalManagementSystemTest {

    private lateinit var context: Context
    private lateinit var thermalManagementSystem: ThermalManagementSystem

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        thermalManagementSystem = ThermalManagementSystem(context)
    }

    @Test
    fun `test initialization`() = runTest {
        // When
        val result = thermalManagementSystem.initialize()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test start thermal monitoring`() = runTest {
        // When
        val result = thermalManagementSystem.startThermalMonitoring()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test stop thermal monitoring`() = runTest {
        // Given
        thermalManagementSystem.startThermalMonitoring()
        
        // When
        val result = thermalManagementSystem.stopThermalMonitoring()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test get current CPU usage`() = runTest {
        // When
        val cpuUsage = thermalManagementSystem.getCurrentCPUUsage()
        
        // Then
        assertNotNull(cpuUsage)
        assertTrue(cpuUsage.percentage >= 0.0)
        assertTrue(cpuUsage.percentage <= 100.0)
        assertTrue(cpuUsage.timestamp > 0)
    }

    @Test
    fun `test get current thermal state`() = runTest {
        // When
        val thermalState = thermalManagementSystem.getCurrentThermalState()
        
        // Then
        assertNotNull(thermalState)
        assertTrue(thermalState in ThermalState.values())
    }

    @Test
    fun `test check thermal threshold - normal state`() = runTest {
        // Given
        val cpuUsage = 60.0 // Below 85% threshold
        
        // When
        val result = thermalManagementSystem.checkThermalThreshold(cpuUsage)
        
        // Then
        assertTrue(result.isSuccess)
        val threshold = result.getOrThrow()
        assertFalse(threshold.isExceeded)
        assertEquals(cpuUsage, threshold.currentValue)
        assertEquals(85.0, threshold.thresholdValue)
    }

    @Test
    fun `test check thermal threshold - exceeded`() = runTest {
        // Given
        val cpuUsage = 90.0 // Above 85% threshold
        
        // When
        val result = thermalManagementSystem.checkThermalThreshold(cpuUsage)
        
        // Then
        assertTrue(result.isSuccess)
        val threshold = result.getOrThrow()
        assertTrue(threshold.isExceeded)
        assertEquals(cpuUsage, threshold.currentValue)
        assertEquals(85.0, threshold.thresholdValue)
    }

    @Test
    fun `test apply thermal throttling`() = runTest {
        // Given
        val cpuUsage = 90.0
        
        // When
        val result = thermalManagementSystem.applyThermalThrottling(cpuUsage)
        
        // Then
        assertTrue(result.isSuccess)
        val throttling = result.getOrThrow()
        assertTrue(throttling.isApplied)
        assertTrue(throttling.newThreadCount < 4) // Reduced from default
    }

    @Test
    fun `test remove thermal throttling`() = runTest {
        // Given
        thermalManagementSystem.applyThermalThrottling(90.0)
        
        // When
        val result = thermalManagementSystem.removeThermalThrottling()
        
        // Then
        assertTrue(result.isSuccess)
        val throttling = result.getOrThrow()
        assertFalse(throttling.isApplied)
        assertEquals(4, throttling.newThreadCount) // Restored to default
    }

    @Test
    fun `test get thermal recovery status`() = runTest {
        // When
        val status = thermalManagementSystem.getThermalRecoveryStatus()
        
        // Then
        assertNotNull(status)
        assertTrue(status.isRecovering)
        assertTrue(status.recoveryProgress >= 0.0)
        assertTrue(status.recoveryProgress <= 100.0)
    }

    @Test
    fun `test send thermal notification`() = runTest {
        // Given
        val thermalState = ThermalState.SEVERE
        
        // When
        val result = thermalManagementSystem.sendThermalNotification(thermalState)
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test get thermal statistics`() = runTest {
        // When
        val stats = thermalManagementSystem.getThermalStatistics()
        
        // Then
        assertNotNull(stats)
        assertTrue(stats.averageCPUUsage >= 0.0)
        assertTrue(stats.peakCPUUsage >= 0.0)
        assertTrue(stats.throttlingEvents >= 0)
        assertTrue(stats.totalSamples > 0)
    }

    @Test
    fun `test get thermal alerts`() = runTest {
        // When
        val alerts = thermalManagementSystem.getThermalAlerts()
        
        // Then
        assertNotNull(alerts)
        assertTrue(alerts.isNotEmpty())
    }

    @Test
    fun `test clear thermal alerts`() = runTest {
        // When
        val result = thermalManagementSystem.clearThermalAlerts()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test get thermal management status`() = runTest {
        // When
        val status = thermalManagementSystem.getThermalManagementStatus()
        
        // Then
        assertNotNull(status)
        assertTrue(status.isMonitoring)
        assertTrue(status.currentCPUUsage >= 0.0)
        assertTrue(status.currentThermalState in ThermalState.values())
        assertTrue(status.isThrottling)
    }

    @Test
    fun `test thermal threshold properties`() {
        // Given
        val threshold = ThermalThreshold(
            isExceeded = true,
            currentValue = 90.0,
            thresholdValue = 85.0,
            duration = 10.0
        )
        
        // Then
        assertTrue(threshold.isExceeded)
        assertEquals(90.0, threshold.currentValue)
        assertEquals(85.0, threshold.thresholdValue)
        assertEquals(10.0, threshold.duration)
    }

    @Test
    fun `test thermal throttling properties`() {
        // Given
        val throttling = ThermalThrottling(
            isApplied = true,
            newThreadCount = 2,
            oldThreadCount = 4,
            reason = "High CPU usage"
        )
        
        // Then
        assertTrue(throttling.isApplied)
        assertEquals(2, throttling.newThreadCount)
        assertEquals(4, throttling.oldThreadCount)
        assertEquals("High CPU usage", throttling.reason)
    }

    @Test
    fun `test thermal recovery status properties`() {
        // Given
        val status = ThermalRecoveryStatus(
            isRecovering = true,
            recoveryProgress = 75.0,
            estimatedTimeRemaining = 30.0
        )
        
        // Then
        assertTrue(status.isRecovering)
        assertEquals(75.0, status.recoveryProgress)
        assertEquals(30.0, status.estimatedTimeRemaining)
    }

    @Test
    fun `test thermal statistics properties`() {
        // Given
        val stats = ThermalStatistics(
            averageCPUUsage = 65.0,
            peakCPUUsage = 95.0,
            throttlingEvents = 5,
            totalSamples = 100,
            lastUpdated = System.currentTimeMillis()
        )
        
        // Then
        assertEquals(65.0, stats.averageCPUUsage)
        assertEquals(95.0, stats.peakCPUUsage)
        assertEquals(5, stats.throttlingEvents)
        assertEquals(100, stats.totalSamples)
        assertTrue(stats.lastUpdated > 0)
    }

    @Test
    fun `test thermal alert properties`() {
        // Given
        val alert = ThermalAlert(
            type = "High CPU Usage",
            message = "CPU usage is above threshold",
            severity = "Warning",
            timestamp = System.currentTimeMillis()
        )
        
        // Then
        assertEquals("High CPU Usage", alert.type)
        assertEquals("CPU usage is above threshold", alert.message)
        assertEquals("Warning", alert.severity)
        assertTrue(alert.timestamp > 0)
    }

    @Test
    fun `test thermal management status properties`() {
        // Given
        val status = ThermalManagementStatus(
            isMonitoring = true,
            currentCPUUsage = 70.0,
            currentThermalState = ThermalState.NORMAL,
            isThrottling = false,
            lastUpdated = System.currentTimeMillis()
        )
        
        // Then
        assertTrue(status.isMonitoring)
        assertEquals(70.0, status.currentCPUUsage)
        assertEquals(ThermalState.NORMAL, status.currentThermalState)
        assertFalse(status.isThrottling)
        assertTrue(status.lastUpdated > 0)
    }

    @Test
    fun `test thermal state enum values`() {
        // Then
        assertTrue(ThermalState.values().contains(ThermalState.NORMAL))
        assertTrue(ThermalState.values().contains(ThermalState.WARNING))
        assertTrue(ThermalState.values().contains(ThermalState.SEVERE))
        assertTrue(ThermalState.values().contains(ThermalState.CRITICAL))
    }

    @Test
    fun `test CPU usage data properties`() {
        // Given
        val cpuUsage = CPUUsageData(
            percentage = 75.0,
            timestamp = System.currentTimeMillis()
        )
        
        // Then
        assertEquals(75.0, cpuUsage.percentage)
        assertTrue(cpuUsage.timestamp > 0)
    }
}
