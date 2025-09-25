package com.frozo.ambientscribe.performance

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Latency Measurer - ST-6.13, ST-6.14
 * Implements latency measurement for first-token and draft-ready scenarios
 * Provides comprehensive latency monitoring and validation
 */
class LatencyMeasurer(
    private val context: Context,
    private val deviceTierDetector: DeviceTierDetector
) {
    
    companion object {
        private const val TAG = "LatencyMeasurer"
        private const val TIER_A_FIRST_TOKEN_P50_MS = 800L
        private const val TIER_A_FIRST_TOKEN_P95_MS = 1200L
        private const val TIER_B_FIRST_TOKEN_P50_MS = 1200L
        private const val TIER_B_FIRST_TOKEN_P95_MS = 1800L
        private const val TIER_A_DRAFT_READY_P50_MS = 8000L
        private const val TIER_A_DRAFT_READY_P95_MS = 12000L
        private const val TIER_B_DRAFT_READY_P50_MS = 12000L
        private const val TIER_B_DRAFT_READY_P95_MS = 18000L
        private const val NOISE_PROFILES_COUNT = 3
        private const val MEASUREMENT_SAMPLES = 100
    }

    private val firstTokenMeasurements = mutableListOf<LatencyMeasurement>()
    private val draftReadyMeasurements = mutableListOf<LatencyMeasurement>()
    private val isMeasuring = AtomicReference(false)

    /**
     * Latency measurement data class
     */
    data class LatencyMeasurement(
        val type: MeasurementType,
        val latencyMs: Long,
        val tier: DeviceTierDetector.DeviceTier,
        val noiseProfile: Int,
        val timestamp: Long,
        val metadata: Map<String, Any>
    )

    /**
     * Measurement type enumeration
     */
    enum class MeasurementType {
        FIRST_TOKEN,
        DRAFT_READY
    }

    /**
     * Latency statistics data class
     */
    data class LatencyStatistics(
        val type: MeasurementType,
        val tier: DeviceTierDetector.DeviceTier,
        val p50Ms: Long,
        val p95Ms: Long,
        val averageMs: Long,
        val minMs: Long,
        val maxMs: Long,
        val sampleCount: Int,
        val targetP50Ms: Long,
        val targetP95Ms: Long,
        val p50Passed: Boolean,
        val p95Passed: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Noise profile data class
     */
    data class NoiseProfile(
        val id: Int,
        val name: String,
        val description: String,
        val noiseLevel: Float,
        val frequencyRange: Pair<Float, Float>
    )

    /**
     * Start latency measurement
     */
    suspend fun startMeasurement(type: MeasurementType): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting latency measurement: $type")
            
            if (isMeasuring.get()) {
                Log.w(TAG, "Measurement already in progress")
                return@withContext Result.failure(IllegalStateException("Measurement already in progress"))
            }

            isMeasuring.set(true)
            
            // Clear previous measurements
            when (type) {
                MeasurementType.FIRST_TOKEN -> firstTokenMeasurements.clear()
                MeasurementType.DRAFT_READY -> draftReadyMeasurements.clear()
            }
            
            Log.d(TAG, "Latency measurement started: $type")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start latency measurement", e)
            Result.failure(e)
        }
    }

    /**
     * Stop latency measurement
     */
    suspend fun stopMeasurement(type: MeasurementType): Result<LatencyStatistics> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Stopping latency measurement: $type")
            
            isMeasuring.set(false)
            
            val measurements = when (type) {
                MeasurementType.FIRST_TOKEN -> firstTokenMeasurements
                MeasurementType.DRAFT_READY -> draftReadyMeasurements
            }
            
            if (measurements.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No measurements available"))
            }
            
            val capabilities = deviceTierDetector.loadDeviceCapabilities()
                ?: return@withContext Result.failure(IllegalStateException("No device capabilities found"))
            
            val statistics = calculateLatencyStatistics(measurements, type, capabilities.tier)
            
            // Save measurement results
            saveMeasurementResults(statistics)
            
            Log.d(TAG, "Latency measurement stopped: $type")
            Result.success(statistics)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop latency measurement", e)
            Result.failure(e)
        }
    }

    /**
     * Measure first token latency
     */
    suspend fun measureFirstTokenLatency(noiseProfile: Int = 0): Result<Long> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Measuring first token latency with noise profile: $noiseProfile")
            
            val startTime = System.currentTimeMillis()
            
            // Simulate first token generation
            // In real implementation, this would measure actual first token generation
            val latency = simulateFirstTokenGeneration(noiseProfile)
            
            val endTime = System.currentTimeMillis()
            val actualLatency = endTime - startTime
            
            // Record measurement
            val capabilities = deviceTierDetector.loadDeviceCapabilities()
            if (capabilities != null) {
                val measurement = LatencyMeasurement(
                    type = MeasurementType.FIRST_TOKEN,
                    latencyMs = actualLatency,
                    tier = capabilities.tier,
                    noiseProfile = noiseProfile,
                    timestamp = System.currentTimeMillis(),
                    metadata = mapOf(
                        "noiseProfile" to noiseProfile,
                        "simulatedLatency" to latency
                    )
                )
                
                firstTokenMeasurements.add(measurement)
            }
            
            Log.d(TAG, "First token latency measured: ${actualLatency}ms")
            Result.success(actualLatency)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to measure first token latency", e)
            Result.failure(e)
        }
    }

    /**
     * Measure draft ready latency
     */
    suspend fun measureDraftReadyLatency(noiseProfile: Int = 0): Result<Long> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Measuring draft ready latency with noise profile: $noiseProfile")
            
            val startTime = System.currentTimeMillis()
            
            // Simulate draft ready generation
            // In real implementation, this would measure actual draft generation
            val latency = simulateDraftReadyGeneration(noiseProfile)
            
            val endTime = System.currentTimeMillis()
            val actualLatency = endTime - startTime
            
            // Record measurement
            val capabilities = deviceTierDetector.loadDeviceCapabilities()
            if (capabilities != null) {
                val measurement = LatencyMeasurement(
                    type = MeasurementType.DRAFT_READY,
                    latencyMs = actualLatency,
                    tier = capabilities.tier,
                    noiseProfile = noiseProfile,
                    timestamp = System.currentTimeMillis(),
                    metadata = mapOf(
                        "noiseProfile" to noiseProfile,
                        "simulatedLatency" to latency
                    )
                )
                
                draftReadyMeasurements.add(measurement)
            }
            
            Log.d(TAG, "Draft ready latency measured: ${actualLatency}ms")
            Result.success(actualLatency)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to measure draft ready latency", e)
            Result.failure(e)
        }
    }

    /**
     * Run comprehensive latency tests
     */
    suspend fun runComprehensiveLatencyTests(): Result<ComprehensiveTestResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Running comprehensive latency tests")
            
            val capabilities = deviceTierDetector.loadDeviceCapabilities()
                ?: return@withContext Result.failure(IllegalStateException("No device capabilities found"))
            
            val testResult = ComprehensiveTestResult(
                tier = capabilities.tier,
                firstTokenResults = mutableListOf(),
                draftReadyResults = mutableListOf(),
                overallPassed = true,
                timestamp = System.currentTimeMillis()
            )
            
            // Test first token latency across noise profiles
            for (noiseProfile in 0 until NOISE_PROFILES_COUNT) {
                val firstTokenResult = runFirstTokenTest(noiseProfile, capabilities.tier)
                testResult.firstTokenResults.add(firstTokenResult)
                
                if (!firstTokenResult.passed) {
                    testResult.overallPassed = false
                }
            }
            
            // Test draft ready latency across noise profiles
            for (noiseProfile in 0 until NOISE_PROFILES_COUNT) {
                val draftReadyResult = runDraftReadyTest(noiseProfile, capabilities.tier)
                testResult.draftReadyResults.add(draftReadyResult)
                
                if (!draftReadyResult.passed) {
                    testResult.overallPassed = false
                }
            }
            
            // Save comprehensive test results
            saveComprehensiveTestResults(testResult)
            
            Log.d(TAG, "Comprehensive latency tests completed. Overall passed: ${testResult.overallPassed}")
            Result.success(testResult)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to run comprehensive latency tests", e)
            Result.failure(e)
        }
    }

    /**
     * Run first token test
     */
    private suspend fun runFirstTokenTest(noiseProfile: Int, tier: DeviceTierDetector.DeviceTier): NoiseProfileTestResult {
        val measurements = mutableListOf<Long>()
        
        // Run multiple measurements
        for (i in 0 until MEASUREMENT_SAMPLES) {
            val result = measureFirstTokenLatency(noiseProfile)
            if (result.isSuccess) {
                measurements.add(result.getOrThrow())
            }
        }
        
        val p50 = calculatePercentile(measurements, 50)
        val p95 = calculatePercentile(measurements, 95)
        
        val targetP50 = if (tier == DeviceTierDetector.DeviceTier.TIER_A) TIER_A_FIRST_TOKEN_P50_MS else TIER_B_FIRST_TOKEN_P50_MS
        val targetP95 = if (tier == DeviceTierDetector.DeviceTier.TIER_A) TIER_A_FIRST_TOKEN_P95_MS else TIER_B_FIRST_TOKEN_P95_MS
        
        val p50Passed = p50 <= targetP50
        val p95Passed = p95 <= targetP95
        val passed = p50Passed && p95Passed
        
        return NoiseProfileTestResult(
            noiseProfile = noiseProfile,
            p50Ms = p50,
            p95Ms = p95,
            targetP50Ms = targetP50,
            targetP95Ms = targetP95,
            p50Passed = p50Passed,
            p95Passed = p95Passed,
            passed = passed,
            sampleCount = measurements.size
        )
    }

    /**
     * Run draft ready test
     */
    private suspend fun runDraftReadyTest(noiseProfile: Int, tier: DeviceTierDetector.DeviceTier): NoiseProfileTestResult {
        val measurements = mutableListOf<Long>()
        
        // Run multiple measurements
        for (i in 0 until MEASUREMENT_SAMPLES) {
            val result = measureDraftReadyLatency(noiseProfile)
            if (result.isSuccess) {
                measurements.add(result.getOrThrow())
            }
        }
        
        val p50 = calculatePercentile(measurements, 50)
        val p95 = calculatePercentile(measurements, 95)
        
        val targetP50 = if (tier == DeviceTierDetector.DeviceTier.TIER_A) TIER_A_DRAFT_READY_P50_MS else TIER_B_DRAFT_READY_P50_MS
        val targetP95 = if (tier == DeviceTierDetector.DeviceTier.TIER_A) TIER_A_DRAFT_READY_P95_MS else TIER_B_DRAFT_READY_P95_MS
        
        val p50Passed = p50 <= targetP50
        val p95Passed = p95 <= targetP95
        val passed = p50Passed && p95Passed
        
        return NoiseProfileTestResult(
            noiseProfile = noiseProfile,
            p50Ms = p50,
            p95Ms = p95,
            targetP50Ms = targetP50,
            targetP95Ms = targetP95,
            p50Passed = p50Passed,
            p95Passed = p95Passed,
            passed = passed,
            sampleCount = measurements.size
        )
    }

    /**
     * Calculate latency statistics
     */
    private fun calculateLatencyStatistics(
        measurements: List<LatencyMeasurement>,
        type: MeasurementType,
        tier: DeviceTierDetector.DeviceTier
    ): LatencyStatistics {
        val latencies = measurements.map { it.latencyMs }.sorted()
        
        val p50 = calculatePercentile(latencies, 50)
        val p95 = calculatePercentile(latencies, 95)
        val average = latencies.average().toLong()
        val min = latencies.minOrNull() ?: 0L
        val max = latencies.maxOrNull() ?: 0L
        
        val targetP50 = when (type) {
            MeasurementType.FIRST_TOKEN -> if (tier == DeviceTierDetector.DeviceTier.TIER_A) TIER_A_FIRST_TOKEN_P50_MS else TIER_B_FIRST_TOKEN_P50_MS
            MeasurementType.DRAFT_READY -> if (tier == DeviceTierDetector.DeviceTier.TIER_A) TIER_A_DRAFT_READY_P50_MS else TIER_B_DRAFT_READY_P50_MS
        }
        
        val targetP95 = when (type) {
            MeasurementType.FIRST_TOKEN -> if (tier == DeviceTierDetector.DeviceTier.TIER_A) TIER_A_FIRST_TOKEN_P95_MS else TIER_B_FIRST_TOKEN_P95_MS
            MeasurementType.DRAFT_READY -> if (tier == DeviceTierDetector.DeviceTier.TIER_A) TIER_A_DRAFT_READY_P95_MS else TIER_B_DRAFT_READY_P95_MS
        }
        
        val p50Passed = p50 <= targetP50
        val p95Passed = p95 <= targetP95
        
        return LatencyStatistics(
            type = type,
            tier = tier,
            p50Ms = p50,
            p95Ms = p95,
            averageMs = average,
            minMs = min,
            maxMs = max,
            sampleCount = measurements.size,
            targetP50Ms = targetP50,
            targetP95Ms = targetP95,
            p50Passed = p50Passed,
            p95Passed = p95Passed
        )
    }

    /**
     * Calculate percentile
     */
    private fun calculatePercentile(sortedValues: List<Long>, percentile: Int): Long {
        if (sortedValues.isEmpty()) return 0L
        
        val index = (percentile / 100.0 * (sortedValues.size - 1)).toInt()
        return sortedValues[index]
    }

    /**
     * Simulate first token generation
     */
    private suspend fun simulateFirstTokenGeneration(noiseProfile: Int): Long {
        // Simulate different latencies based on noise profile
        val baseLatency = when (noiseProfile) {
            0 -> 500L // Clean audio
            1 -> 800L // Moderate noise
            2 -> 1200L // High noise
            else -> 1000L
        }
        
        // Add some randomness
        val randomFactor = Random.nextDouble(0.8, 1.2)
        val latency = (baseLatency * randomFactor).toLong()
        
        // Simulate processing time
        kotlinx.coroutines.delay(latency)
        
        return latency
    }

    /**
     * Simulate draft ready generation
     */
    private suspend fun simulateDraftReadyGeneration(noiseProfile: Int): Long {
        // Simulate different latencies based on noise profile
        val baseLatency = when (noiseProfile) {
            0 -> 5000L // Clean audio
            1 -> 8000L // Moderate noise
            2 -> 12000L // High noise
            else -> 10000L
        }
        
        // Add some randomness
        val randomFactor = Random.nextDouble(0.8, 1.2)
        val latency = (baseLatency * randomFactor).toLong()
        
        // Simulate processing time
        kotlinx.coroutines.delay(latency)
        
        return latency
    }

    /**
     * Save measurement results
     */
    private fun saveMeasurementResults(statistics: LatencyStatistics) {
        try {
            val resultsDir = File(context.filesDir, "latency_measurements")
            resultsDir.mkdirs()
            
            val resultsFile = File(resultsDir, "latency_${statistics.type.name}_${statistics.timestamp}.json")
            val json = JSONObject().apply {
                put("type", statistics.type.name)
                put("tier", statistics.tier.name)
                put("p50Ms", statistics.p50Ms)
                put("p95Ms", statistics.p95Ms)
                put("averageMs", statistics.averageMs)
                put("minMs", statistics.minMs)
                put("maxMs", statistics.maxMs)
                put("sampleCount", statistics.sampleCount)
                put("targetP50Ms", statistics.targetP50Ms)
                put("targetP95Ms", statistics.targetP95Ms)
                put("p50Passed", statistics.p50Passed)
                put("p95Passed", statistics.p95Passed)
                put("timestamp", statistics.timestamp)
            }
            
            resultsFile.writeText(json.toString())
            Log.d(TAG, "Measurement results saved to: ${resultsFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save measurement results", e)
        }
    }

    /**
     * Save comprehensive test results
     */
    private fun saveComprehensiveTestResults(testResult: ComprehensiveTestResult) {
        try {
            val resultsDir = File(context.filesDir, "latency_tests")
            resultsDir.mkdirs()
            
            val resultsFile = File(resultsDir, "comprehensive_test_${testResult.timestamp}.json")
            val json = JSONObject().apply {
                put("tier", testResult.tier.name)
                put("overallPassed", testResult.overallPassed)
                put("timestamp", testResult.timestamp)
                put("firstTokenResults", testResult.firstTokenResults.map { result ->
                    JSONObject().apply {
                        put("noiseProfile", result.noiseProfile)
                        put("p50Ms", result.p50Ms)
                        put("p95Ms", result.p95Ms)
                        put("targetP50Ms", result.targetP50Ms)
                        put("targetP95Ms", result.targetP95Ms)
                        put("p50Passed", result.p50Passed)
                        put("p95Passed", result.p95Passed)
                        put("passed", result.passed)
                        put("sampleCount", result.sampleCount)
                    }
                })
                put("draftReadyResults", testResult.draftReadyResults.map { result ->
                    JSONObject().apply {
                        put("noiseProfile", result.noiseProfile)
                        put("p50Ms", result.p50Ms)
                        put("p95Ms", result.p95Ms)
                        put("targetP50Ms", result.targetP50Ms)
                        put("targetP95Ms", result.targetP95Ms)
                        put("p50Passed", result.p50Passed)
                        put("p95Passed", result.p95Passed)
                        put("passed", result.passed)
                        put("sampleCount", result.sampleCount)
                    }
                })
            }
            
            resultsFile.writeText(json.toString())
            Log.d(TAG, "Comprehensive test results saved to: ${resultsFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save comprehensive test results", e)
        }
    }

    /**
     * Comprehensive test result data class
     */
    data class ComprehensiveTestResult(
        val tier: DeviceTierDetector.DeviceTier,
        val firstTokenResults: MutableList<NoiseProfileTestResult>,
        val draftReadyResults: MutableList<NoiseProfileTestResult>,
        var overallPassed: Boolean,
        val timestamp: Long
    )

    /**
     * Noise profile test result data class
     */
    data class NoiseProfileTestResult(
        val noiseProfile: Int,
        val p50Ms: Long,
        val p95Ms: Long,
        val targetP50Ms: Long,
        val targetP95Ms: Long,
        val p50Passed: Boolean,
        val p95Passed: Boolean,
        val passed: Boolean,
        val sampleCount: Int
    )
}
