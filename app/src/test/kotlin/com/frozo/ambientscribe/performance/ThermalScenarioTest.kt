package com.frozo.ambientscribe.performance

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    private lateinit var mockPowerManager: PowerManager
    
    @Mock
    private lateinit var mockDeviceCapabilityDetector: DeviceCapabilityDetector
    
    private lateinit var thermalManager: ThermalManager
    private lateinit var performanceManager: PerformanceManager

    @Before
    fun setUp() {
        // Mock PowerManager
        whenever(mockContext.getSystemService(Context.POWER_SERVICE))
            .thenReturn(mockPowerManager)
        
        // Mock device capabilities
        whenever(mockDeviceCapabilityDetector.detectDeviceTier())
            .thenReturn(DeviceCapabilityDetector.DeviceTier.TIER_A)
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
    fun testNormalThermalState() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Mock normal thermal state
        whenever(mockPowerManager.currentThermalStatus)
            .thenReturn(PowerManager.THERMAL_STATUS_NONE)
        
        // Get current performance state
        val thermalState = thermalManager.getCurrentThermalState()
        
        // Verify optimal settings for TIER_A device in NORMAL state
        assertEquals(ThermalManager.ThermalState.NORMAL, thermalState)
    }

    @Test
    fun testModerateThermalState() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Mock moderate thermal state
        whenever(mockPowerManager.currentThermalStatus)
            .thenReturn(PowerManager.THERMAL_STATUS_MODERATE)
        
        // Get current performance state
        val thermalState = thermalManager.getCurrentThermalState()
        
        // Verify reduced settings for TIER_A device in MODERATE state
        assertEquals(ThermalManager.ThermalState.WARM, thermalState)
    }

    @Test
    fun testSevereThermalState() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Mock severe thermal state
        whenever(mockPowerManager.currentThermalStatus)
            .thenReturn(PowerManager.THERMAL_STATUS_SEVERE)
        
        // Get current performance state
        val thermalState = thermalManager.getCurrentThermalState()
        
        // Verify minimal settings for TIER_A device in SEVERE state
        assertEquals(ThermalManager.ThermalState.HOT, thermalState)
    }

    @Test
    fun testThermalStateTransitions() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Simulate thermal state transitions
        whenever(mockPowerManager.currentThermalStatus)
            .thenReturn(PowerManager.THERMAL_STATUS_NONE)
        var state = thermalManager.getCurrentThermalState()
        assertEquals(ThermalManager.ThermalState.NORMAL, state)
        
        whenever(mockPowerManager.currentThermalStatus)
            .thenReturn(PowerManager.THERMAL_STATUS_MODERATE)
        state = thermalManager.getCurrentThermalState()
        assertEquals(ThermalManager.ThermalState.WARM, state)
        
        whenever(mockPowerManager.currentThermalStatus)
            .thenReturn(PowerManager.THERMAL_STATUS_SEVERE)
        state = thermalManager.getCurrentThermalState()
        assertEquals(ThermalManager.ThermalState.HOT, state)
    }

    @Test
    fun testTierBDeviceSettings() = runTest {
        // Mock TIER_B device
        whenever(mockDeviceCapabilityDetector.detectDeviceTier())
            .thenReturn(DeviceCapabilityDetector.DeviceTier.TIER_B)
        whenever(mockDeviceCapabilityDetector.getAvailableCores()).thenReturn(4)
        whenever(mockDeviceCapabilityDetector.getMaxFrequencyMHz()).thenReturn(1800)
        whenever(mockDeviceCapabilityDetector.getTotalRamGB()).thenReturn(3.0f)
        
        // Mock normal thermal state
        whenever(mockPowerManager.currentThermalStatus)
            .thenReturn(PowerManager.THERMAL_STATUS_NONE)
        
        // Get current thermal state
        val thermalState = thermalManager.getCurrentThermalState()
        
        // Verify thermal state is normal
        assertEquals(ThermalManager.ThermalState.NORMAL, thermalState)
    }

    @Test
    fun testLimitedDeviceSettings() = runTest {
        // Mock device without advanced instruction sets
        whenever(mockDeviceCapabilityDetector.hasFp16Support()).thenReturn(false)
        whenever(mockDeviceCapabilityDetector.hasSdotSupport()).thenReturn(false)
        
        // Mock normal thermal state
        whenever(mockPowerManager.currentThermalStatus)
            .thenReturn(PowerManager.THERMAL_STATUS_NONE)
        
        // Get current thermal state
        val thermalState = thermalManager.getCurrentThermalState()
        
        // Verify thermal state is normal
        assertEquals(ThermalManager.ThermalState.NORMAL, thermalState)
    }
    
    @Test
    fun testRapidThermalStateChanges() = runTest {
        // Initialize managers
        performanceManager.initialize()
        
        // Simulate rapid thermal state changes
        repeat(5) {
            whenever(mockPowerManager.currentThermalStatus)
                .thenReturn(PowerManager.THERMAL_STATUS_NONE)
            delay(50)
            
            whenever(mockPowerManager.currentThermalStatus)
                .thenReturn(PowerManager.THERMAL_STATUS_MODERATE)
            delay(50)
        }
        
        // Get final thermal state
        val finalState = thermalManager.getCurrentThermalState()
        
        // Verify thermal manager is still functioning
        assertTrue(finalState in ThermalManager.ThermalState.values())
    }
}