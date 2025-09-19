package com.frozo.ambientscribe.performance

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ThermalScenarioTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockDeviceCapabilityDetector: DeviceCapabilityDetector
    
    private lateinit var thermalManager: ThermalManager
    private lateinit var performanceManager: PerformanceManager

    @Before
    fun setUp() {
        // Mock device capabilities
        whenever(mockDeviceCapabilityDetector.detectDeviceTier()).thenReturn(DeviceTier.TIER_A)
        whenever(mockDeviceCapabilityDetector.getAvailableCores()).thenReturn(8)
        whenever(mockDeviceCapabilityDetector.getMaxFrequencyMHz()).thenReturn(2400)
        whenever(mockDeviceCapabilityDetector.getTotalRamGB()).thenReturn(6.0f)
        whenever(mockDeviceCapabilityDetector.hasNeonSupport()).thenReturn(true)
        whenever(mockDeviceCapabilityDetector.hasFp16Support()).thenReturn(true)
        whenever(mockDeviceCapabilityDetector.hasSdotSupport()).thenReturn(true)
        
        // Create managers with mocked dependencies
        thermalManager = ThermalManager(mockContext)
        performanceManager = PerformanceManager(
            context = mockContext,
            thermalManager = thermalManager,
            deviceCapabilityDetector = mockDeviceCapabilityDetector
        )
    }

    @Test
    fun `normal thermal state should use optimal performance settings`() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Set normal thermal state
        val normalState = ThermalManager.ThermalState(
            thermalLevel = 0, // NORMAL
            cpuUsagePercent = 30,
            temperatureCelsius = 35.0f,
            recommendedThreads = 6,
            recommendedContextSize = 3000,
            timestamp = System.currentTimeMillis()
        )
        thermalManager.simulateThermalState(normalState)
        
        // Get current performance state
        val performanceState = performanceManager.performanceState.first()
        
        // Verify optimal settings for TIER_A device in NORMAL state
        assertTrue(performanceState.recommendedThreads >= 4)
        assertEquals(3000, performanceState.recommendedContextSize)
        assertTrue(performanceState.cpuUsagePercent < 80)
        assertEquals(0, performanceState.thermalState)
    }

    @Test
    fun `moderate thermal state should reduce performance settings`() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Set moderate thermal state
        val moderateState = ThermalManager.ThermalState(
            thermalLevel = 1, // MODERATE
            cpuUsagePercent = 70,
            temperatureCelsius = 40.0f,
            recommendedThreads = 4,
            recommendedContextSize = 2000,
            timestamp = System.currentTimeMillis()
        )
        thermalManager.simulateThermalState(moderateState)
        
        // Get current performance state
        val performanceState = performanceManager.performanceState.first()
        
        // Verify reduced settings for TIER_A device in MODERATE state
        assertTrue(performanceState.recommendedThreads <= 4)
        assertEquals(2000, performanceState.recommendedContextSize)
        assertTrue(performanceState.cpuUsagePercent < 80)
        assertEquals(1, performanceState.thermalState)
    }

    @Test
    fun `severe thermal state should use minimal performance settings`() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Set severe thermal state
        val severeState = ThermalManager.ThermalState(
            thermalLevel = 2, // SEVERE
            cpuUsagePercent = 90,
            temperatureCelsius = 45.0f,
            recommendedThreads = 2,
            recommendedContextSize = 1000,
            timestamp = System.currentTimeMillis()
        )
        thermalManager.simulateThermalState(severeState)
        
        // Get current performance state
        val performanceState = performanceManager.performanceState.first()
        
        // Verify minimal settings for TIER_A device in SEVERE state
        assertEquals(2, performanceState.recommendedThreads)
        assertEquals(1000, performanceState.recommendedContextSize)
        assertTrue(performanceState.cpuUsagePercent > 0)
        assertEquals(2, performanceState.thermalState)
    }

    @Test
    fun `thermal state transitions should update performance settings`() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Collect performance states during transitions
        val states = mutableListOf<PerformanceState>()
        
        // Start collection job
        val collectJob = launch {
            performanceManager.performanceState.take(3).toList(states)
        }
        
        // Simulate thermal state transitions
        thermalManager.simulateThermalState(ThermalManager.ThermalState(
            thermalLevel = 0, // NORMAL
            cpuUsagePercent = 30,
            temperatureCelsius = 35.0f,
            recommendedThreads = 6,
            recommendedContextSize = 3000,
            timestamp = System.currentTimeMillis()
        ))
        
        delay(100)
        
        thermalManager.simulateThermalState(ThermalManager.ThermalState(
            thermalLevel = 1, // MODERATE
            cpuUsagePercent = 70,
            temperatureCelsius = 40.0f,
            recommendedThreads = 4,
            recommendedContextSize = 2000,
            timestamp = System.currentTimeMillis()
        ))
        
        delay(100)
        
        thermalManager.simulateThermalState(ThermalManager.ThermalState(
            thermalLevel = 2, // SEVERE
            cpuUsagePercent = 90,
            temperatureCelsius = 45.0f,
            recommendedThreads = 2,
            recommendedContextSize = 1000,
            timestamp = System.currentTimeMillis()
        ))
        
        // Wait for collection to complete
        collectJob.join()
        
        // Verify state transitions
        assertEquals(3, states.size)
        assertEquals(0, states[0].thermalState)
        assertEquals(1, states[1].thermalState)
        assertEquals(2, states[2].thermalState)
        
        // Verify performance degradation
        assertTrue(states[0].recommendedThreads > states[1].recommendedThreads)
        assertTrue(states[1].recommendedThreads > states[2].recommendedThreads)
        assertTrue(states[0].recommendedContextSize > states[1].recommendedContextSize)
        assertTrue(states[1].recommendedContextSize > states[2].recommendedContextSize)
    }

    @Test
    fun `tier B devices should have lower base performance settings`() = runTest {
        // Mock TIER_B device
        whenever(mockDeviceCapabilityDetector.detectDeviceTier()).thenReturn(DeviceTier.TIER_B)
        whenever(mockDeviceCapabilityDetector.getAvailableCores()).thenReturn(4)
        whenever(mockDeviceCapabilityDetector.getMaxFrequencyMHz()).thenReturn(1800)
        whenever(mockDeviceCapabilityDetector.getTotalRamGB()).thenReturn(3.0f)
        
        // Create new performance manager with TIER_B device
        val tierBPerformanceManager = PerformanceManager(
            context = mockContext,
            thermalManager = thermalManager,
            deviceCapabilityDetector = mockDeviceCapabilityDetector
        )
        
        // Initialize manager
        tierBPerformanceManager.initialize()
        
        // Set normal thermal state
        thermalManager.simulateThermalState(ThermalManager.ThermalState(
            thermalLevel = 0, // NORMAL
            cpuUsagePercent = 30,
            temperatureCelsius = 35.0f,
            recommendedThreads = 4,
            recommendedContextSize = 2000,
            timestamp = System.currentTimeMillis()
        ))
        
        // Get current performance state
        val performanceState = tierBPerformanceManager.performanceState.first()
        
        // Verify lower base settings for TIER_B device
        assertTrue(performanceState.recommendedThreads <= 4)
        assertTrue(performanceState.recommendedContextSize <= 3000)
    }

    @Test
    fun `devices without advanced instruction sets should have lower performance settings`() = runTest {
        // Mock device without advanced instruction sets
        whenever(mockDeviceCapabilityDetector.hasFp16Support()).thenReturn(false)
        whenever(mockDeviceCapabilityDetector.hasSdotSupport()).thenReturn(false)
        
        // Create new performance manager
        val limitedDeviceManager = PerformanceManager(
            context = mockContext,
            thermalManager = thermalManager,
            deviceCapabilityDetector = mockDeviceCapabilityDetector
        )
        
        // Initialize manager
        limitedDeviceManager.initialize()
        
        // Set normal thermal state
        thermalManager.simulateThermalState(ThermalManager.ThermalState(
            thermalLevel = 0, // NORMAL
            cpuUsagePercent = 30,
            temperatureCelsius = 35.0f,
            recommendedThreads = 6,
            recommendedContextSize = 3000,
            timestamp = System.currentTimeMillis()
        ))
        
        // Get current performance state
        val performanceState = limitedDeviceManager.performanceState.first()
        
        // Verify performance settings are adjusted for limited device
        assertTrue(performanceState.recommendedThreads <= 6)
    }
    
    @Test
    fun `performance manager should handle rapid thermal state changes`() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Simulate rapid thermal state changes
        repeat(5) {
            // Normal state
            thermalManager.simulateThermalState(ThermalManager.ThermalState(
                thermalLevel = 0, // NORMAL
                cpuUsagePercent = 30,
                temperatureCelsius = 35.0f,
                recommendedThreads = 6,
                recommendedContextSize = 3000,
                timestamp = System.currentTimeMillis()
            ))
            
            delay(50)
            
            // Moderate state
            thermalManager.simulateThermalState(ThermalManager.ThermalState(
                thermalLevel = 1, // MODERATE
                cpuUsagePercent = 70,
                temperatureCelsius = 40.0f,
                recommendedThreads = 4,
                recommendedContextSize = 2000,
                timestamp = System.currentTimeMillis()
            ))
            
            delay(50)
        }
        
        // Get final performance state
        val finalState = performanceManager.performanceState.first()
        
        // Verify performance manager is still functioning
        assertTrue(finalState.recommendedThreads > 0)
        assertTrue(finalState.recommendedContextSize > 0)
    }
}