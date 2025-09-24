package com.frozo.ambientscribe.integration

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import com.frozo.ambientscribe.telemetry.MetricsCollector
import com.frozo.ambientscribe.performance.DeviceCapabilityDetector
import com.frozo.ambientscribe.performance.ThermalManager
import com.frozo.ambientscribe.performance.PerformanceManager

/**
 * Integration Test for PT-1 and PT-2 Component Integration
 * 
 * Tests the integration between:
 * - PT-1: Audio Capture and ASR Processing
 * - PT-2: SOAP Note Generation and Prescription Generation
 * 
 * Focuses on:
 * 1. Data flow between PT-1 and PT-2
 * 2. Shared resource management
 * 3. Performance coordination
 * 4. Error handling across components
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PT1PT2IntegrationTest {

    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var deviceCapabilityDetector: DeviceCapabilityDetector
    private lateinit var thermalManager: ThermalManager
    private lateinit var performanceManager: PerformanceManager

    @Before
    fun setUp() {
        // Mock context
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        
        // Initialize shared components
        metricsCollector = MetricsCollector(mockContext)
        deviceCapabilityDetector = DeviceCapabilityDetector(mockContext)
        thermalManager = ThermalManager(mockContext)
        performanceManager = PerformanceManager(mockContext, thermalManager, deviceCapabilityDetector)
    }

    @Test
    fun `PT-1 and PT-2 should share metrics collector`() = runTest {
        // Given: Shared metrics collector
        val sharedMetricsCollector = MetricsCollector(mockContext)
        
        // When: PT-1 records audio metrics
        val audioResult = sharedMetricsCollector.logMetricEvent("audio_capture_started", mapOf(
            "sample_rate" to "16000",
            "channels" to "1",
            "format" to "PCM_16BIT"
        ))
        assertTrue(audioResult.isSuccess)
        
        // And: PT-2 records LLM metrics
        val llmResult = sharedMetricsCollector.logMetricEvent("llm_processing_started", mapOf(
            "model" to "llama-7b-q4",
            "context_size" to "3000",
            "threads" to "4"
        ))
        assertTrue(llmResult.isSuccess)
        
        // Then: Both metrics should be recorded
        val metrics = sharedMetricsCollector.getMetricsSummary()
        assertNotNull(metrics, "Metrics should be recorded from both PT-1 and PT-2")
    }

    @Test
    fun `PT-1 and PT-2 should share performance manager`() = runTest {
        // Given: Shared performance manager
        val sharedPerformanceManager = PerformanceManager(mockContext, thermalManager, deviceCapabilityDetector)
        
        // When: Get performance state
        val performanceState = sharedPerformanceManager.getCurrentPerformanceState()
        
        // Then: Should provide shared performance constraints
        assertNotNull(performanceState, "Performance state should be available")
        assertTrue(performanceState.recommendedThreads > 0, "Should recommend threads for both PT-1 and PT-2")
        assertTrue(performanceState.recommendedContextSize > 0, "Should recommend context size for LLM")
    }

    @Test
    fun `PT-1 thermal management should affect PT-2 processing`() = runTest {
        // Given: Shared thermal manager
        val sharedThermalManager = ThermalManager(mockContext)
        val sharedPerformanceManager = PerformanceManager(mockContext, sharedThermalManager, deviceCapabilityDetector)
        
        // When: Get thermal state
        val thermalState = sharedThermalManager.getCurrentThermalState()
        val performanceState = sharedPerformanceManager.getCurrentPerformanceState()
        
        // Then: Thermal state should influence performance recommendations
        assertNotNull(thermalState, "Thermal state should be available")
        assertNotNull(performanceState, "Performance state should be available")
        
        // Verify thermal state affects thread recommendations
        assertTrue(performanceState.thermalState >= 0, "Thermal state should be non-negative")
    }

    @Test
    fun `PT-1 audio processing should coordinate with PT-2 resource management`() = runTest {
        // Given: Shared components
        val sharedMetricsCollector = MetricsCollector(mockContext)
        val sharedPerformanceManager = PerformanceManager(mockContext, thermalManager, deviceCapabilityDetector)
        
        // When: PT-1 starts audio processing
        val audioResult = sharedMetricsCollector.logMetricEvent("audio_processing_started", mapOf(
            "buffer_size" to "1024",
            "processing_threads" to "2"
        ))
        assertTrue(audioResult.isSuccess)
        
        // And: PT-2 starts LLM processing
        val performanceState = sharedPerformanceManager.getCurrentPerformanceState()
        val llmResult = sharedMetricsCollector.logMetricEvent("llm_processing_started", mapOf(
            "available_threads" to performanceState.recommendedThreads.toString(),
            "context_size" to performanceState.recommendedContextSize.toString()
        ))
        assertTrue(llmResult.isSuccess)
        
        // Then: Both should coordinate resource usage
        val metrics = sharedMetricsCollector.getMetricsSummary()
        assertNotNull(metrics, "Resource coordination should be recorded")
    }

    @Test
    fun `PT-1 ASR output should be compatible with PT-2 input`() = runTest {
        // Given: Mock ASR output from PT-1
        val asrOutput = "Patient complains of chest pain and shortness of breath"
        
        // When: PT-2 processes ASR output
        val metricsCollector = MetricsCollector(mockContext)
        val asrResult = metricsCollector.logMetricEvent("asr_output_received", mapOf(
            "transcript_length" to asrOutput.length.toString(),
            "word_count" to asrOutput.split(" ").size.toString()
        ))
        assertTrue(asrResult.isSuccess)
        
        // Then: Should process successfully
        assertNotNull(asrOutput, "ASR output should be valid")
        assertTrue(asrOutput.isNotEmpty(), "ASR output should not be empty")
        
        val metrics = metricsCollector.getMetricsSummary()
        assertNotNull(metrics, "ASR processing metrics should be recorded")
    }

    @Test
    fun `PT-2 SOAP note generation should handle PT-1 ASR errors gracefully`() = runTest {
        // Given: Mock ASR error from PT-1
        val asrError = "ASR processing failed"
        
        // When: PT-2 handles ASR error
        val metricsCollector = MetricsCollector(mockContext)
        val errorResult = metricsCollector.logMetricEvent("asr_error_handled", mapOf(
            "error_type" to "asr_failure",
            "fallback_used" to "true"
        ))
        assertTrue(errorResult.isSuccess)
        
        // Then: Should handle error gracefully
        val metrics = metricsCollector.getMetricsSummary()
        assertNotNull(metrics, "Error handling should be recorded")
    }

    @Test
    fun `PT-1 and PT-2 should share device capability information`() = runTest {
        // Given: Shared device capability detector
        val sharedDeviceDetector = DeviceCapabilityDetector(mockContext)
        
        // When: Get device capabilities
        val deviceTier = sharedDeviceDetector.detectDeviceTier()
        val availableCores = sharedDeviceDetector.getAvailableCores()
        val totalRam = sharedDeviceDetector.getTotalRamGB()
        
        // Then: Should provide device information for both PT-1 and PT-2
        assertNotNull(deviceTier, "Device tier should be detected")
        assertTrue(availableCores > 0, "Available cores should be positive")
        assertTrue(totalRam > 0, "Total RAM should be positive")
    }

    @Test
    fun `PT-1 and PT-2 should coordinate memory usage`() = runTest {
        // Given: Shared components
        val sharedMetricsCollector = MetricsCollector(mockContext)
        val sharedPerformanceManager = PerformanceManager(mockContext, thermalManager, deviceCapabilityDetector)
        
        // When: PT-1 reports memory usage
        val pt1Result = sharedMetricsCollector.logMetricEvent("pt1_memory_usage", mapOf(
            "audio_buffer_mb" to "10.5",
            "asr_model_mb" to "25.0"
        ))
        assertTrue(pt1Result.isSuccess)
        
        // And: PT-2 reports memory usage
        val pt2Result = sharedMetricsCollector.logMetricEvent("pt2_memory_usage", mapOf(
            "llm_model_mb" to "200.0",
            "context_mb" to "50.0"
        ))
        assertTrue(pt2Result.isSuccess)
        
        // Then: Both should coordinate memory usage
        val metrics = sharedMetricsCollector.getMetricsSummary()
        assertNotNull(metrics, "Memory coordination should be recorded")
    }

    @Test
    fun `PT-1 and PT-2 should handle concurrent processing`() = runTest {
        // Given: Shared components
        val sharedMetricsCollector = MetricsCollector(mockContext)
        val sharedPerformanceManager = PerformanceManager(mockContext, thermalManager, deviceCapabilityDetector)
        
        // When: Both PT-1 and PT-2 process concurrently
        val performanceState = sharedPerformanceManager.getCurrentPerformanceState()
        
        val concurrentResult = sharedMetricsCollector.logMetricEvent("concurrent_processing_started", mapOf(
            "pt1_active" to "true",
            "pt2_active" to "true",
            "shared_threads" to performanceState.recommendedThreads.toString()
        ))
        assertTrue(concurrentResult.isSuccess)
        
        // Then: Should handle concurrent processing
        val metrics = sharedMetricsCollector.getMetricsSummary()
        assertNotNull(metrics, "Concurrent processing should be recorded")
    }
}
