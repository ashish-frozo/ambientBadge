package com.frozo.ambientscribe.performance

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Battery Stats Validator - ST-6.15
 * Implements BatteryStats validation on Tier A/B; assert ≤6%/8% per hour
 * Provides comprehensive battery consumption validation
 */
class BatteryStatsValidator(
    private val context: Context,
    private val deviceTierDetector: DeviceTierDetector
) {
    
    companion object {
        private const val TAG = "BatteryStatsValidator"
        private const val VALIDATION_DURATION_MS = 3600000L // 1 hour
        private const val SAMPLE_INTERVAL_MS = 60000L // 1 minute
        private const val TIER_A_MAX_CONSUMPTION_PERCENT = 6f
        private const val TIER_B_MAX_CONSUMPTION_PERCENT = 8f
        private const val MIN_VALIDATION_SAMPLES = 10
    }

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val isValidationRunning = AtomicBoolean(false)
    private val validationStartTime = AtomicLong(0)
    private val batterySamples = mutableListOf<BatterySample>()

    /**
     * Battery sample data class
     */
    data class BatterySample(
        val timestamp: Long,
        val level: Float,
        val temperature: Float,
        val voltage: Int,
        val isCharging: Boolean,
        val chargeCounter: Long,
        val health: Int,
        val status: Int
    )

    /**
     * Battery validation result
     */
    data class BatteryValidationResult(
        val tier: DeviceTierDetector.DeviceTier,
        val validationPassed: Boolean,
        val consumptionPercentPerHour: Float,
        val maxAllowedPercentPerHour: Float,
        val averageConsumption: Float,
        val peakConsumption: Float,
        val sampleCount: Int,
        val validationDurationMs: Long,
        val recommendations: List<String>,
        val timestamp: Long
    )

    /**
     * Start battery validation
     */
    suspend fun startBatteryValidation(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting battery validation")
            
            if (isValidationRunning.get()) {
                Log.w(TAG, "Battery validation already running")
                return Result.success(Unit)
            }

            val capabilities = deviceTierDetector.loadDeviceCapabilities()
                ?: return Result.failure(IllegalStateException("No device capabilities found"))

            isValidationRunning.set(true)
            validationStartTime.set(System.currentTimeMillis())
            batterySamples.clear()

            // Take initial battery sample
            val initialSample = getCurrentBatterySample()
            batterySamples.add(initialSample)

            Log.d(TAG, "Battery validation started for tier: ${capabilities.tier}")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start battery validation", e)
            Result.failure(e)
        }
    }

    /**
     * Stop battery validation
     */
    suspend fun stopBatteryValidation(): Result<BatteryValidationResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Stopping battery validation")
            
            if (!isValidationRunning.get()) {
                return Result.failure(IllegalStateException("Battery validation not running"))
            }

            isValidationRunning.set(false)

            // Take final battery sample
            val finalSample = getCurrentBatterySample()
            batterySamples.add(finalSample)

            val capabilities = deviceTierDetector.loadDeviceCapabilities()
                ?: return Result.failure(IllegalStateException("No device capabilities found"))

            val validationResult = calculateBatteryValidationResult(capabilities.tier)
            
            // Save validation results
            saveBatteryValidationResult(validationResult)

            Log.d(TAG, "Battery validation stopped. Passed: ${validationResult.validationPassed}")
            Result.success(validationResult)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop battery validation", e)
            Result.failure(e)
        }
    }

    /**
     * Add battery sample during validation
     */
    suspend fun addBatterySample(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isValidationRunning.get()) {
                return Result.failure(IllegalStateException("Battery validation not running"))
            }

            val sample = getCurrentBatterySample()
            batterySamples.add(sample)

            Log.d(TAG, "Battery sample added: ${sample.level}%")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add battery sample", e)
            Result.failure(e)
        }
    }

    /**
     * Get current battery sample
     */
    private fun getCurrentBatterySample(): BatterySample {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toFloat()
        val temperature = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10f
        val voltage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_VOLTAGE)
        val isCharging = batteryManager.isCharging
        val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toLong()
        val health = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)

        return BatterySample(
            timestamp = System.currentTimeMillis(),
            level = level,
            temperature = temperature,
            voltage = voltage,
            isCharging = isCharging,
            chargeCounter = chargeCounter,
            health = health,
            status = status
        )
    }

    /**
     * Calculate battery validation result
     */
    private fun calculateBatteryValidationResult(tier: DeviceTierDetector.DeviceTier): BatteryValidationResult {
        val maxAllowedPercentPerHour = when (tier) {
            DeviceTierDetector.DeviceTier.TIER_A -> TIER_A_MAX_CONSUMPTION_PERCENT
            DeviceTierDetector.DeviceTier.TIER_B -> TIER_B_MAX_CONSUMPTION_PERCENT
            DeviceTierDetector.DeviceTier.UNSUPPORTED -> 10f // Higher threshold for unsupported devices
        }

        val consumptionPercentPerHour = calculateConsumptionPercentPerHour()
        val averageConsumption = calculateAverageConsumption()
        val peakConsumption = calculatePeakConsumption()
        val validationDurationMs = System.currentTimeMillis() - validationStartTime.get()
        
        val validationPassed = consumptionPercentPerHour <= maxAllowedPercentPerHour && 
                              batterySamples.size >= MIN_VALIDATION_SAMPLES

        val recommendations = generateBatteryRecommendations(
            tier, consumptionPercentPerHour, maxAllowedPercentPerHour, validationPassed
        )

        return BatteryValidationResult(
            tier = tier,
            validationPassed = validationPassed,
            consumptionPercentPerHour = consumptionPercentPerHour,
            maxAllowedPercentPerHour = maxAllowedPercentPerHour,
            averageConsumption = averageConsumption,
            peakConsumption = peakConsumption,
            sampleCount = batterySamples.size,
            validationDurationMs = validationDurationMs,
            recommendations = recommendations,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Calculate consumption percentage per hour
     */
    private fun calculateConsumptionPercentPerHour(): Float {
        if (batterySamples.size < 2) return 0f

        val firstSample = batterySamples.first()
        val lastSample = batterySamples.last()
        
        val timeDiff = lastSample.timestamp - firstSample.timestamp
        val levelDiff = firstSample.level - lastSample.level
        
        if (timeDiff <= 0 || levelDiff <= 0) return 0f
        
        val hoursDiff = timeDiff / (1000f * 60f * 60f) // Convert to hours
        return (levelDiff / hoursDiff).coerceAtLeast(0f)
    }

    /**
     * Calculate average consumption
     */
    private fun calculateAverageConsumption(): Float {
        if (batterySamples.size < 2) return 0f

        val consumptionRates = mutableListOf<Float>()
        
        for (i in 1 until batterySamples.size) {
            val prevSample = batterySamples[i - 1]
            val currSample = batterySamples[i]
            
            val timeDiff = currSample.timestamp - prevSample.timestamp
            val levelDiff = prevSample.level - currSample.level
            
            if (timeDiff > 0 && levelDiff > 0) {
                val hoursDiff = timeDiff / (1000f * 60f * 60f)
                val consumptionRate = levelDiff / hoursDiff
                consumptionRates.add(consumptionRate)
            }
        }
        
        return if (consumptionRates.isNotEmpty()) {
            consumptionRates.average().toFloat()
        } else {
            0f
        }
    }

    /**
     * Calculate peak consumption
     */
    private fun calculatePeakConsumption(): Float {
        if (batterySamples.size < 2) return 0f

        val consumptionRates = mutableListOf<Float>()
        
        for (i in 1 until batterySamples.size) {
            val prevSample = batterySamples[i - 1]
            val currSample = batterySamples[i]
            
            val timeDiff = currSample.timestamp - prevSample.timestamp
            val levelDiff = prevSample.level - currSample.level
            
            if (timeDiff > 0 && levelDiff > 0) {
                val hoursDiff = timeDiff / (1000f * 60f * 60f)
                val consumptionRate = levelDiff / hoursDiff
                consumptionRates.add(consumptionRate)
            }
        }
        
        return if (consumptionRates.isNotEmpty()) {
            consumptionRates.maxOrNull() ?: 0f
        } else {
            0f
        }
    }

    /**
     * Generate battery recommendations
     */
    private fun generateBatteryRecommendations(
        tier: DeviceTierDetector.DeviceTier,
        consumptionPercentPerHour: Float,
        maxAllowedPercentPerHour: Float,
        validationPassed: Boolean
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when {
            validationPassed -> {
                recommendations.add("Battery consumption is within acceptable limits for ${tier.name}")
                recommendations.add("Current consumption: ${consumptionPercentPerHour}%/hour (max allowed: ${maxAllowedPercentPerHour}%/hour)")
            }
            consumptionPercentPerHour > maxAllowedPercentPerHour * 1.5f -> {
                recommendations.add("Battery consumption is significantly high (${consumptionPercentPerHour}%/hour)")
                recommendations.add("Consider reducing background processing and optimizing algorithms")
                recommendations.add("Check for memory leaks or inefficient operations")
            }
            consumptionPercentPerHour > maxAllowedPercentPerHour -> {
                recommendations.add("Battery consumption exceeds target (${consumptionPercentPerHour}%/hour)")
                recommendations.add("Consider optimizing performance settings")
                recommendations.add("Reduce model cache size or processing frequency")
            }
        }

        // Add tier-specific recommendations
        when (tier) {
            DeviceTierDetector.DeviceTier.TIER_A -> {
                recommendations.add("Tier A device: You can use high-performance features while maintaining battery efficiency")
            }
            DeviceTierDetector.DeviceTier.TIER_B -> {
                recommendations.add("Tier B device: Use balanced settings to maintain performance and battery life")
            }
            DeviceTierDetector.DeviceTier.UNSUPPORTED -> {
                recommendations.add("Unsupported device: Use minimal settings to conserve battery")
            }
        }

        return recommendations
    }

    /**
     * Save battery validation result
     */
    private fun saveBatteryValidationResult(result: BatteryValidationResult) {
        try {
            val validationDir = File(context.filesDir, "battery_validation")
            validationDir.mkdirs()
            
            val resultFile = File(validationDir, "battery_validation_${result.timestamp}.json")
            val json = JSONObject().apply {
                put("tier", result.tier.name)
                put("validationPassed", result.validationPassed)
                put("consumptionPercentPerHour", result.consumptionPercentPerHour)
                put("maxAllowedPercentPerHour", result.maxAllowedPercentPerHour)
                put("averageConsumption", result.averageConsumption)
                put("peakConsumption", result.peakConsumption)
                put("sampleCount", result.sampleCount)
                put("validationDurationMs", result.validationDurationMs)
                put("recommendations", result.recommendations)
                put("timestamp", result.timestamp)
                
                // Battery samples
                put("batterySamples", result.sampleCount)
            }
            
            resultFile.writeText(json.toString())
            Log.d(TAG, "Battery validation result saved to: ${resultFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save battery validation result", e)
        }
    }

    /**
     * Get battery validation statistics
     */
    suspend fun getBatteryValidationStatistics(): BatteryValidationStatistics = withContext(Dispatchers.IO) {
        try {
            val validationDir = File(context.filesDir, "battery_validation")
            val validationFiles = validationDir.listFiles { file ->
                file.name.startsWith("battery_validation_") && file.name.endsWith(".json")
            } ?: emptyArray()

            val totalValidations = validationFiles.size
            val passedValidations = validationFiles.count { file ->
                try {
                    val json = JSONObject(file.readText())
                    json.getBoolean("validationPassed")
                } catch (e: Exception) {
                    false
                }
            }

            val averageConsumption = if (validationFiles.isNotEmpty()) {
                validationFiles.mapNotNull { file ->
                    try {
                        val json = JSONObject(file.readText())
                        json.getDouble("consumptionPercentPerHour").toFloat()
                    } catch (e: Exception) {
                        null
                    }
                }.average().toFloat()
            } else {
                0f
            }

            BatteryValidationStatistics(
                totalValidations = totalValidations,
                passedValidations = passedValidations,
                averageConsumption = averageConsumption,
                isValidationRunning = isValidationRunning.get()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery validation statistics", e)
            BatteryValidationStatistics(
                totalValidations = 0,
                passedValidations = 0,
                averageConsumption = 0f,
                isValidationRunning = false
            )
        }
    }

    /**
     * Battery validation statistics data class
     */
    data class BatteryValidationStatistics(
        val totalValidations: Int,
        val passedValidations: Int,
        val averageConsumption: Float,
        val isValidationRunning: Boolean
    )
}
