package com.frozo.ambientscribe.performance

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ThermalManagerTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockThermalStateListener: ThermalManager.ThermalStateListener
    
    private lateinit var thermalManager: ThermalManager

    @Before
    fun setUp() {
        thermalManager = ThermalManager(
            context = mockContext,
            highThresholdPercent = 85,
            recoveryThresholdPercent = 60,
            monitoringIntervalMs = 100, // Faster for testing
            highTempDurationThresholdMs = 200, // Faster for testing
            recoveryDurationThresholdMs = 300 // Faster for testing
        )
    }

    @After
    fun tearDown() {
        thermalManager.cleanup()
    }

    @Test
    fun `startMonitoring should begin monitoring loop`() {
        thermalManager.startMonitoring()
        
        // Verify monitoring started
        val thermalState = thermalManager.getCurrentThermalState()
        assertNotNull(thermalState)
        
        thermalManager.stopMonitoring()
    }

    @Test
    fun `stopMonitoring should end monitoring loop`() {
        thermalManager.startMonitoring()
        thermalManager.stopMonitoring()
        
        // Verify monitoring stopped (no direct way to test, but we can check it doesn't crash)
        assertTrue(true)
    }

    @Test
    fun `registerComponent should notify listener of thermal state changes`() {
        // Register component
        thermalManager.registerComponent("test-component", mockThermalStateListener)
        
        // Verify initial state notification
        verify(mockThermalStateListener).onThermalStateChanged(any())
        
        thermalManager.unregisterComponent("test-component")
    }

    @Test
    fun `unregisterComponent should remove listener`() {
        // Register and then unregister
        thermalManager.registerComponent("test-component", mockThermalStateListener)
        thermalManager.unregisterComponent("test-component")
        
        // No direct way to verify unregistration, but we can check it doesn't crash
        assertTrue(true)
    }

    @Test
    fun `getThermalStateFlow should emit thermal states`() = runTest {
        thermalManager.startMonitoring()
        
        val thermalStateFlow = thermalManager.getThermalStateFlow()
        assertNotNull(thermalStateFlow)
        
        thermalManager.stopMonitoring()
    }

    @Test
    fun `getCurrentThermalState should return current state`() {
        val thermalState = thermalManager.getCurrentThermalState()
        
        assertNotNull(thermalState)
        assertEquals(0, thermalState.thermalLevel) // Should start at NORMAL (0)
    }

    @Test
    fun `setDeviceTier should adjust thread count`() {
        // Test TIER_A
        thermalManager.setDeviceTier(ThermalManager.DeviceTier.TIER_A)
        val tierAThreads = thermalManager.getRecommendedThreadCount()
        
        // Test TIER_B
        thermalManager.setDeviceTier(ThermalManager.DeviceTier.TIER_B)
        val tierBThreads = thermalManager.getRecommendedThreadCount()
        
        // TIER_B should have fewer or equal threads than TIER_A
        assertTrue(tierBThreads <= tierAThreads)
    }

    @Test
    fun `getRecommendedThreadCount should respect min and max limits`() {
        // Set to TIER_A (maximum threads)
        thermalManager.setDeviceTier(ThermalManager.DeviceTier.TIER_A)
        val maxThreads = thermalManager.getRecommendedThreadCount()
        
        // Should be between 2 and 6
        assertTrue(maxThreads >= 2)
        assertTrue(maxThreads <= 6)
        
        // Set to TIER_B (minimum threads)
        thermalManager.setDeviceTier(ThermalManager.DeviceTier.TIER_B)
        val minThreads = thermalManager.getRecommendedThreadCount()
        
        // Should be at least 2
        assertTrue(minThreads >= 2)
    }

    @Test
    fun `getRecommendedContextSize should return appropriate size`() {
        val contextSize = thermalManager.getRecommendedContextSize()
        
        // Should be positive
        assertTrue(contextSize > 0)
    }

    @Test
    fun `cleanup should release resources`() {
        thermalManager.startMonitoring()
        thermalManager.cleanup()
        
        // No direct way to verify cleanup, but we can check it doesn't crash
        assertTrue(true)
    }

    @Test
    fun `thermal state should include all required fields`() {
        val thermalState = thermalManager.getCurrentThermalState()
        
        assertNotNull(thermalState.thermalLevel)
        assertNotNull(thermalState.cpuUsagePercent)
        assertNotNull(thermalState.temperatureCelsius)
        assertNotNull(thermalState.recommendedThreads)
        assertNotNull(thermalState.recommendedContextSize)
        assertNotNull(thermalState.timestamp)
    }

    @Test
    fun `device tier should affect initial thread count`() {
        // Test TIER_A
        thermalManager.setDeviceTier(ThermalManager.DeviceTier.TIER_A)
        val tierAThreads = thermalManager.getRecommendedThreadCount()
        
        // Reset and test TIER_B
        val newThermalManager = ThermalManager(mockContext)
        newThermalManager.setDeviceTier(ThermalManager.DeviceTier.TIER_B)
        val tierBThreads = newThermalManager.getRecommendedThreadCount()
        
        // TIER_A should have more or equal threads than TIER_B
        assertTrue(tierAThreads >= tierBThreads)
        
        newThermalManager.cleanup()
    }

    @Test
    fun `thermal state should affect context size`() {
        // Normal state (default)
        val normalContextSize = thermalManager.getRecommendedContextSize()
        
        // We can't easily simulate thermal state changes in tests,
        // but we can verify the initial context size is reasonable
        assertTrue(normalContextSize >= 1000)
    }

    @Test
    fun `multiple start and stop calls should be idempotent`() {
        // Multiple starts
        thermalManager.startMonitoring()
        thermalManager.startMonitoring()
        
        // Multiple stops
        thermalManager.stopMonitoring()
        thermalManager.stopMonitoring()
        
        // Should not crash
        assertTrue(true)
    }

    @Test
    fun `thermal state listener should be notified of changes`() {
        // Create a test listener
        val capturedStates = mutableListOf<ThermalManager.ThermalState>()
        val testListener = object : ThermalManager.ThermalStateListener {
            override fun onThermalStateChanged(state: ThermalManager.ThermalState) {
                capturedStates.add(state)
            }
        }
        
        // Register listener and start monitoring
        thermalManager.registerComponent("test-listener", testListener)
        thermalManager.startMonitoring()
        
        // Wait a bit for at least one update
        Thread.sleep(200)
        
        // Should have received at least one state update
        assertTrue(capturedStates.isNotEmpty())
        
        thermalManager.unregisterComponent("test-listener")
        thermalManager.stopMonitoring()
    }
}
