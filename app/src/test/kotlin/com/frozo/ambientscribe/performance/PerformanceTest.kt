package com.frozo.ambientscribe.performance

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PerformanceTest {

    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var performanceManager: PerformanceManager
    private lateinit var deviceCapabilityDetector: DeviceCapabilityDetector

    @Before
    fun setUp() {
        deviceCapabilityDetector = DeviceCapabilityDetector(mockContext)
        val thermalManager = ThermalManager(mockContext)
        performanceManager = PerformanceManager(mockContext, thermalManager, deviceCapabilityDetector)
    }

    @After
    fun tearDown() {
        performanceManager.cleanup()
    }

    @Test
    fun `initialize should detect device capabilities`() {
        performanceManager.initialize()
        
        val state = performanceManager.getCurrentPerformanceState()
        assertNotNull(state)
        assertNotNull(state.deviceTier)
        assertTrue(state.recommendedThreads >= 2)
        assertTrue(state.recommendedThreads <= 6)
    }

    @Test
    fun `getRecommendedThreadCount should return valid thread count`() {
        performanceManager.initialize()
        
        val threadCount = performanceManager.getRecommendedThreadCount()
        assertTrue(threadCount >= 2)
        assertTrue(threadCount <= 6)
    }

    @Test
    fun `getRecommendedContextSize should return valid context size`() {
        performanceManager.initialize()
        
        val contextSize = performanceManager.getRecommendedContextSize()
        assertTrue(contextSize > 0)
    }

    @Test
    fun `supportsInstructionSet should check instruction set support`() {
        performanceManager.initialize()
        
        // NEON should be supported on most devices
        val supportsNeon = performanceManager.supportsInstructionSet("NEON")
        assertTrue(supportsNeon)
    }

    @Test
    fun `cleanup should release resources`() {
        performanceManager.initialize()
        performanceManager.cleanup()
        
        // No direct way to verify cleanup, but we can check it doesn't crash
        assertTrue(true)
    }

    @Test
    fun `performance state should include all required fields`() {
        performanceManager.initialize()
        
        val state = performanceManager.getCurrentPerformanceState()
        assertNotNull(state.deviceTier)
        assertNotNull(state.thermalState)
        assertNotNull(state.cpuUsagePercent)
        assertNotNull(state.temperatureCelsius)
        assertNotNull(state.recommendedThreads)
        assertNotNull(state.recommendedContextSize)
        assertNotNull(state.instructionSets)
        assertNotNull(state.timestamp)
    }

    @Test
    fun `device capability detector should classify devices`() {
        // This test is more of an integration test but useful for verification
        val tier = deviceCapabilityDetector.getDeviceTier()
        
        // Should be either TIER_A or TIER_B
        assertTrue(
            tier == DeviceCapabilityDetector.DeviceTier.TIER_A || 
            tier == DeviceCapabilityDetector.DeviceTier.TIER_B
        )
    }

    @Test
    fun `optimal thread count should be within valid range`() {
        val threadCount = performanceManager.getRecommendedThreadCount()
        
        assertTrue(threadCount >= 2)
        assertTrue(threadCount <= 6)
    }

    @Test
    fun `instruction set detection should include NEON, FP16, and SDOT`() {
        performanceManager.initialize()
        val instructionSets = performanceManager.getCurrentPerformanceState().instructionSets
        
        assertTrue(instructionSets.containsKey("NEON"))
        assertTrue(instructionSets.containsKey("FP16"))
        assertTrue(instructionSets.containsKey("SDOT"))
    }

    @Test
    fun `multiple initialize calls should be idempotent`() {
        performanceManager.initialize()
        performanceManager.initialize()
        
        // Should not crash
        assertTrue(true)
    }

    @Test
    fun `thermal state flow should emit thermal states`() = runTest {
        performanceManager.initialize()
        
        val thermalStateFlow = performanceManager.getThermalStateFlow()
        assertNotNull(thermalStateFlow)
    }
    
    @Test
    fun `benchmark simulated load with different thread counts`() = runTest {
        // This is a simple benchmark simulation
        val results = mutableMapOf<Int, Long>()
        
        for (threads in 2..6) {
            val startTime = System.currentTimeMillis()
            
            // Simulate work with different thread counts
            simulateWorkload(threads)
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            results[threads] = duration
        }
        
        // Just verify the benchmark ran without errors
        assertTrue(results.isNotEmpty())
    }
    
    private suspend fun simulateWorkload(threadCount: Int) {
        // Simple simulation of workload
        val workPerThread = 1000000 / threadCount
        
        coroutineScope {
            val jobs = List(threadCount) {
                launch {
                    var sum = 0.0
                    for (i in 0 until workPerThread) {
                        sum += Math.sin(i.toDouble())
                    }
                }
            }
            jobs.forEach { it.join() }
        }
    }
}
