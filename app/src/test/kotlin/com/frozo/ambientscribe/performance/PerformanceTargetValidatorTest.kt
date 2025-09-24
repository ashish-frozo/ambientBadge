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
 * Unit tests for PerformanceTargetValidator - PT-6.3, PT-6.8
 */
@RunWith(RobolectricTestRunner::class)
class PerformanceTargetValidatorTest {

    private lateinit var context: Context
    private lateinit var performanceValidator: PerformanceTargetValidator

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        performanceValidator = PerformanceTargetValidator(context)
    }

    @Test
    fun `test initialization`() = runTest {
        // When
        val result = performanceValidator.initialize()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test validate first model load time - Tier A success`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val loadTime = 6.0 // Within 8s limit
        
        // When
        val result = performanceValidator.validateFirstModelLoadTime(loadTime, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(loadTime, validation.actualValue)
        assertEquals(8.0, validation.targetValue)
        assertTrue(validation.recommendations.isEmpty())
    }

    @Test
    fun `test validate first model load time - Tier A failure`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val loadTime = 10.0 // Exceeds 8s limit
        
        // When
        val result = performanceValidator.validateFirstModelLoadTime(loadTime, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(loadTime, validation.actualValue)
        assertEquals(8.0, validation.targetValue)
        assertTrue(validation.recommendations.isNotEmpty())
        assertTrue(validation.recommendations.any { it.contains("exceeds target") })
    }

    @Test
    fun `test validate first model load time - Tier B success`() = runTest {
        // Given
        val tier = DeviceTier.TIER_B
        val loadTime = 10.0 // Within 12s limit
        
        // When
        val result = performanceValidator.validateFirstModelLoadTime(loadTime, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(loadTime, validation.actualValue)
        assertEquals(12.0, validation.targetValue)
    }

    @Test
    fun `test validate first token latency - Tier A success`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val latency = 0.6 // Within 0.8s limit
        
        // When
        val result = performanceValidator.validateFirstTokenLatency(latency, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(latency, validation.actualValue)
        assertEquals(0.8, validation.targetValue)
    }

    @Test
    fun `test validate first token latency - Tier A failure`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val latency = 1.0 // Exceeds 0.8s limit
        
        // When
        val result = performanceValidator.validateFirstTokenLatency(latency, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(latency, validation.actualValue)
        assertEquals(0.8, validation.targetValue)
        assertTrue(validation.recommendations.isNotEmpty())
    }

    @Test
    fun `test validate draft ready latency - Tier A success`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val latency = 6.0 // Within 8s limit
        
        // When
        val result = performanceValidator.validateDraftReadyLatency(latency, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(latency, validation.actualValue)
        assertEquals(8.0, validation.targetValue)
    }

    @Test
    fun `test validate draft ready latency - Tier B success`() = runTest {
        // Given
        val tier = DeviceTier.TIER_B
        val latency = 10.0 // Within 12s limit
        
        // When
        val result = performanceValidator.validateDraftReadyLatency(latency, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(latency, validation.actualValue)
        assertEquals(12.0, validation.targetValue)
    }

    @Test
    fun `test validate battery consumption - Tier A success`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val consumption = 4.0 // Within 6% limit
        
        // When
        val result = performanceValidator.validateBatteryConsumption(consumption, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(consumption, validation.actualValue)
        assertEquals(6.0, validation.targetValue)
    }

    @Test
    fun `test validate battery consumption - Tier A failure`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val consumption = 8.0 // Exceeds 6% limit
        
        // When
        val result = performanceValidator.validateBatteryConsumption(consumption, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(consumption, validation.actualValue)
        assertEquals(6.0, validation.targetValue)
        assertTrue(validation.recommendations.isNotEmpty())
    }

    @Test
    fun `test validate battery consumption - Tier B success`() = runTest {
        // Given
        val tier = DeviceTier.TIER_B
        val consumption = 6.0 // Within 8% limit
        
        // When
        val result = performanceValidator.validateBatteryConsumption(consumption, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(consumption, validation.actualValue)
        assertEquals(8.0, validation.targetValue)
    }

    @Test
    fun `test validate memory usage - success`() = runTest {
        // Given
        val memoryUsage = 512.0 // 512MB
        
        // When
        val result = performanceValidator.validateMemoryUsage(memoryUsage)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(memoryUsage, validation.actualValue)
        assertTrue(validation.targetValue > 0)
    }

    @Test
    fun `test validate memory usage - failure`() = runTest {
        // Given
        val memoryUsage = 2048.0 // 2GB - too high
        
        // When
        val result = performanceValidator.validateMemoryUsage(memoryUsage)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(memoryUsage, validation.actualValue)
        assertTrue(validation.recommendations.isNotEmpty())
    }

    @Test
    fun `test validate CPU usage - success`() = runTest {
        // Given
        val cpuUsage = 60.0 // 60%
        
        // When
        val result = performanceValidator.validateCPUUsage(cpuUsage)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(cpuUsage, validation.actualValue)
        assertTrue(validation.targetValue > 0)
    }

    @Test
    fun `test validate CPU usage - failure`() = runTest {
        // Given
        val cpuUsage = 95.0 // 95% - too high
        
        // When
        val result = performanceValidator.validateCPUUsage(cpuUsage)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(cpuUsage, validation.actualValue)
        assertTrue(validation.recommendations.isNotEmpty())
    }

    @Test
    fun `test validate all performance metrics`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val metrics = PerformanceMetrics(
            firstModelLoadTime = 6.0,
            firstTokenLatency = 0.6,
            draftReadyLatency = 6.0,
            batteryConsumption = 4.0,
            memoryUsage = 512.0,
            cpuUsage = 60.0
        )
        
        // When
        val result = performanceValidator.validateAllMetrics(metrics, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(6, validation.validations.size)
        assertTrue(validation.validations.all { it.isValid })
    }

    @Test
    fun `test validate all performance metrics - some failures`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val metrics = PerformanceMetrics(
            firstModelLoadTime = 10.0, // Exceeds limit
            firstTokenLatency = 1.0, // Exceeds limit
            draftReadyLatency = 6.0,
            batteryConsumption = 8.0, // Exceeds limit
            memoryUsage = 512.0,
            cpuUsage = 60.0
        )
        
        // When
        val result = performanceValidator.validateAllMetrics(metrics, tier)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(6, validation.validations.size)
        assertTrue(validation.validations.count { it.isValid } < 6)
        assertTrue(validation.recommendations.isNotEmpty())
    }

    @Test
    fun `test get performance targets for tier`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        
        // When
        val targets = performanceValidator.getPerformanceTargets(tier)
        
        // Then
        assertNotNull(targets)
        assertEquals(8.0, targets.maxModelLoadTime)
        assertEquals(0.8, targets.maxFirstTokenLatency)
        assertEquals(8.0, targets.maxDraftReadyLatency)
        assertEquals(6.0, targets.maxBatteryConsumption)
        assertTrue(targets.maxMemoryUsage > 0)
        assertTrue(targets.maxCPUUsage > 0)
    }

    @Test
    fun `test get performance recommendations`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        val metrics = PerformanceMetrics(
            firstModelLoadTime = 10.0, // Exceeds limit
            firstTokenLatency = 0.6,
            draftReadyLatency = 6.0,
            batteryConsumption = 4.0,
            memoryUsage = 512.0,
            cpuUsage = 60.0
        )
        
        // When
        val recommendations = performanceValidator.getPerformanceRecommendations(metrics, tier)
        
        // Then
        assertNotNull(recommendations)
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.any { it.contains("model load time") })
    }

    @Test
    fun `test performance validation result properties`() {
        // Given
        val validation = PerformanceValidationResult(
            isValid = true,
            actualValue = 6.0,
            targetValue = 8.0,
            recommendations = listOf("Good performance")
        )
        
        // Then
        assertTrue(validation.isValid)
        assertEquals(6.0, validation.actualValue)
        assertEquals(8.0, validation.targetValue)
        assertEquals(1, validation.recommendations.size)
    }

    @Test
    fun `test comprehensive validation result properties`() {
        // Given
        val validations = listOf(
            PerformanceValidationResult(true, 6.0, 8.0, emptyList()),
            PerformanceValidationResult(true, 0.6, 0.8, emptyList())
        )
        
        val result = ComprehensiveValidationResult(
            isValid = true,
            validations = validations,
            recommendations = emptyList()
        )
        
        // Then
        assertTrue(result.isValid)
        assertEquals(2, result.validations.size)
        assertTrue(result.validations.all { it.isValid })
        assertTrue(result.recommendations.isEmpty())
    }
}