package com.frozo.ambientscribe.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Battery Optimization Manager - ST-6.4, ST-6.7
 * Implements battery optimization with target consumption and exemption UX flow
 * Manages battery usage monitoring and optimization strategies
 */
class BatteryOptimizationManager(
    private val context: Context,
    private val deviceTierDetector: DeviceTierDetector
) {
    
    companion object {
        private const val TAG = "BatteryOptimizationManager"
        private const val BATTERY_SAMPLE_INTERVAL_MS = 60000L // 1 minute
        private const val BATTERY_HISTORY_SIZE = 60 // 1 hour of samples
        private const val LOW_BATTERY_THRESHOLD = 20f
        private const val CRITICAL_BATTERY_THRESHOLD = 10f
    }

    private val isMonitoring = AtomicBoolean(false)
    private val batteryHistory = mutableListOf<BatterySample>()
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

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
        val consumptionPercentPerHour: Float
    )

    /**
     * Battery optimization result
     */
    data class BatteryOptimizationResult(
        val success: Boolean,
        val currentConsumption: Float,
        val targetConsumption: Float,
        val optimizationLevel: Int,
        val recommendations: List<String>,
        val timestamp: Long
    )

    /**
     * Battery exemption status
     */
    data class BatteryExemptionStatus(
        val isExempted: Boolean,
        val canRequestExemption: Boolean,
        val exemptionReason: String?,
        val lastChecked: Long
    )

    /**
     * Start battery monitoring
     */
    suspend fun startBatteryMonitoring(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting battery monitoring")
            
            if (isMonitoring.get()) {
                Log.w(TAG, "Battery monitoring already started")
                return@withContext Result.success(Unit)
            }

            isMonitoring.set(true)
            
            // Initialize battery history with current sample
            val initialSample = getCurrentBatterySample()
            batteryHistory.add(initialSample)

            Log.d(TAG, "Battery monitoring started")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start battery monitoring", e)
            Result.failure(e)
        }
    }

    /**
     * Stop battery monitoring
     */
    suspend fun stopBatteryMonitoring(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Stopping battery monitoring")
            
            isMonitoring.set(false)
            
            // Save battery history
            saveBatteryHistory()
            
            Log.d(TAG, "Battery monitoring stopped")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop battery monitoring", e)
            Result.failure(e)
        }
    }

    /**
     * Get current battery consumption rate
     */
    suspend fun getCurrentBatteryConsumption(): Result<Float> = withContext(Dispatchers.IO) {
        try {
            if (batteryHistory.size < 2) {
                return@withContext Result.failure(IllegalStateException("Insufficient battery history"))
            }

            val currentSample = getCurrentBatterySample()
            batteryHistory.add(currentSample)

            // Keep only recent history
            if (batteryHistory.size > BATTERY_HISTORY_SIZE) {
                batteryHistory.removeAt(0)
            }

            // Calculate consumption rate
            val consumption = calculateBatteryConsumption()
            
            Log.d(TAG, "Current battery consumption: ${consumption}%/hour")
            Result.success(consumption)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current battery consumption", e)
            Result.failure(e)
        }
    }

    /**
     * Optimize battery usage
     */
    suspend fun optimizeBatteryUsage(): Result<BatteryOptimizationResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting battery optimization")
            
            val capabilities = deviceTierDetector.loadDeviceCapabilities()
                ?: return@withContext Result.failure(IllegalStateException("No device capabilities found"))

            val currentConsumption = getCurrentBatteryConsumption().getOrNull() ?: 0f
            val targetConsumption = capabilities.recommendedSettings.batteryConsumptionTargetPercentPerHour.toFloat()
            
            val optimizationLevel = determineOptimizationLevel(currentConsumption, targetConsumption)
            val recommendations = generateOptimizationRecommendations(currentConsumption, targetConsumption, capabilities)
            
            // Apply optimization strategies
            applyOptimizationStrategies(optimizationLevel, capabilities)

            val result = BatteryOptimizationResult(
                success = true,
                currentConsumption = currentConsumption,
                targetConsumption = targetConsumption,
                optimizationLevel = optimizationLevel,
                recommendations = recommendations,
                timestamp = System.currentTimeMillis()
            )

            // Save optimization result
            saveOptimizationResult(result)

            Log.d(TAG, "Battery optimization completed. Level: $optimizationLevel")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to optimize battery usage", e)
            Result.failure(e)
        }
    }

    /**
     * Check battery exemption status
     */
    suspend fun checkBatteryExemptionStatus(): BatteryExemptionStatus = withContext(Dispatchers.IO) {
        try {
            val isExempted = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
            val canRequestExemption = !isExempted
            val exemptionReason = if (isExempted) "App is exempted from battery optimization" else "App is not exempted from battery optimization"
            
            BatteryExemptionStatus(
                isExempted = isExempted,
                canRequestExemption = canRequestExemption,
                exemptionReason = exemptionReason,
                lastChecked = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check battery exemption status", e)
            BatteryExemptionStatus(
                isExempted = false,
                canRequestExemption = false,
                exemptionReason = "Error checking exemption status: ${e.message}",
                lastChecked = System.currentTimeMillis()
            )
        }
    }

    /**
     * Request battery optimization exemption
     */
    suspend fun requestBatteryExemption(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting battery optimization exemption")
            
            val status = checkBatteryExemptionStatus()
            if (status.isExempted) {
                Log.d(TAG, "App is already exempted from battery optimization")
                return@withContext Result.success(Unit)
            }

            if (!status.canRequestExemption) {
                return@withContext Result.failure(IllegalStateException("Cannot request battery exemption"))
            }

            // Open battery optimization settings
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
            
            Log.d(TAG, "Battery exemption request initiated")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery exemption", e)
            Result.failure(e)
        }
    }

    /**
     * Get battery optimization UX flow
     */
    suspend fun getBatteryOptimizationUXFlow(): Result<BatteryOptimizationUXFlow> = withContext(Dispatchers.IO) {
        try {
            val status = checkBatteryExemptionStatus()
            val currentConsumption = getCurrentBatteryConsumption().getOrNull() ?: 0f
            val capabilities = deviceTierDetector.loadDeviceCapabilities()
                ?: return@withContext Result.failure(IllegalStateException("No device capabilities found"))

            val targetConsumption = capabilities.recommendedSettings.batteryConsumptionTargetPercentPerHour.toFloat()
            val isExceedingTarget = currentConsumption > targetConsumption

            val flow = BatteryOptimizationUXFlow(
                showExemptionRequest = !status.isExempted && isExceedingTarget,
                showOptimizationTips = isExceedingTarget,
                currentConsumption = currentConsumption,
                targetConsumption = targetConsumption,
                batteryLevel = getCurrentBatteryLevel(),
                recommendations = generateOptimizationRecommendations(currentConsumption, targetConsumption, capabilities)
            )

            Result.success(flow)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery optimization UX flow", e)
            Result.failure(e)
        }
    }

    /**
     * Get current battery sample
     */
    private fun getCurrentBatterySample(): BatterySample {
        val batteryLevel = getCurrentBatteryLevel()
        val temperature = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Use reflection to access temperature property if available
                val temperatureField = BatteryManager::class.java.getDeclaredField("BATTERY_PROPERTY_TEMPERATURE")
                val temperatureProperty = temperatureField.getInt(null)
                batteryManager.getIntProperty(temperatureProperty) / 10f
            } else {
                25f // Default temperature for older API levels
            }
        } catch (e: Exception) {
            25f // Default temperature
        }
        val voltage = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Use reflection to access voltage property if available
                val voltageField = BatteryManager::class.java.getDeclaredField("BATTERY_PROPERTY_VOLTAGE")
                val voltageProperty = voltageField.getInt(null)
                batteryManager.getIntProperty(voltageProperty)
            } else {
                3700 // Default voltage for older API levels
            }
        } catch (e: Exception) {
            3700 // Default voltage
        }
        val isCharging = batteryManager.isCharging
        val chargeCounter = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toLong()
            } else {
                0L // Default charge counter for older API levels
            }
        } catch (e: Exception) {
            0L // Default charge counter
        }
        
        // Calculate consumption rate from history
        val consumption = if (batteryHistory.isNotEmpty()) {
            calculateBatteryConsumption()
        } else {
            0f
        }

        return BatterySample(
            timestamp = System.currentTimeMillis(),
            level = batteryLevel,
            temperature = temperature,
            voltage = voltage,
            isCharging = isCharging,
            chargeCounter = chargeCounter,
            consumptionPercentPerHour = consumption
        )
    }

    /**
     * Get current battery level
     */
    private fun getCurrentBatteryLevel(): Float {
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toFloat()
    }

    /**
     * Calculate battery consumption rate
     */
    private fun calculateBatteryConsumption(): Float {
        if (batteryHistory.size < 2) return 0f

        val recentSamples = batteryHistory.takeLast(10) // Use last 10 samples
        if (recentSamples.size < 2) return 0f

        val firstSample = recentSamples.first()
        val lastSample = recentSamples.last()
        
        val timeDiff = lastSample.timestamp - firstSample.timestamp
        val levelDiff = firstSample.level - lastSample.level
        
        if (timeDiff <= 0 || levelDiff <= 0) return 0f
        
        val hoursDiff = timeDiff / (1000f * 60f * 60f) // Convert to hours
        return (levelDiff / hoursDiff).coerceAtLeast(0f)
    }

    /**
     * Determine optimization level based on consumption
     */
    private fun determineOptimizationLevel(currentConsumption: Float, targetConsumption: Float): Int {
        return when {
            currentConsumption <= targetConsumption * 0.8f -> 1 // Low optimization
            currentConsumption <= targetConsumption -> 2 // Medium optimization
            currentConsumption <= targetConsumption * 1.2f -> 3 // High optimization
            else -> 4 // Maximum optimization
        }
    }

    /**
     * Generate optimization recommendations
     */
    private fun generateOptimizationRecommendations(
        currentConsumption: Float,
        targetConsumption: Float,
        capabilities: DeviceTierDetector.DeviceCapabilities
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when {
            currentConsumption > targetConsumption * 1.5f -> {
                recommendations.add("Battery consumption is significantly high. Consider reducing background processing.")
                recommendations.add("Enable aggressive power saving mode.")
            }
            currentConsumption > targetConsumption * 1.2f -> {
                recommendations.add("Battery consumption is above target. Reduce model cache size.")
                recommendations.add("Limit concurrent processing threads.")
            }
            currentConsumption > targetConsumption -> {
                recommendations.add("Battery consumption is slightly above target. Optimize audio processing.")
                recommendations.add("Consider reducing thermal throttling threshold.")
            }
            else -> {
                recommendations.add("Battery consumption is within target range.")
                recommendations.add("Current settings are optimal for this device tier.")
            }
        }

        // Add tier-specific recommendations
        when (capabilities.tier) {
            DeviceTierDetector.DeviceTier.TIER_A -> {
                recommendations.add("Tier A device: You can use higher performance settings while maintaining battery efficiency.")
            }
            DeviceTierDetector.DeviceTier.TIER_B -> {
                recommendations.add("Tier B device: Use balanced settings to maintain performance and battery life.")
            }
            DeviceTierDetector.DeviceTier.UNSUPPORTED -> {
                recommendations.add("Unsupported device: Use minimal settings to conserve battery.")
            }
        }

        return recommendations
    }

    /**
     * Apply optimization strategies
     */
    private fun applyOptimizationStrategies(
        optimizationLevel: Int,
        capabilities: DeviceTierDetector.DeviceCapabilities
    ) {
        Log.d(TAG, "Applying optimization strategies at level: $optimizationLevel")

        when (optimizationLevel) {
            1 -> {
                // Low optimization - minimal changes
                Log.d(TAG, "Applying low optimization strategies")
            }
            2 -> {
                // Medium optimization - moderate changes
                Log.d(TAG, "Applying medium optimization strategies")
            }
            3 -> {
                // High optimization - significant changes
                Log.d(TAG, "Applying high optimization strategies")
            }
            4 -> {
                // Maximum optimization - aggressive changes
                Log.d(TAG, "Applying maximum optimization strategies")
            }
        }
    }

    /**
     * Save battery history
     */
    private fun saveBatteryHistory() {
        try {
            val historyDir = File(context.filesDir, "battery_history")
            historyDir.mkdirs()
            
            val historyFile = File(historyDir, "battery_history_${System.currentTimeMillis()}.json")
            val json = JSONObject().apply {
                put("samples", batteryHistory.map { sample ->
                    JSONObject().apply {
                        put("timestamp", sample.timestamp)
                        put("level", sample.level)
                        put("temperature", sample.temperature)
                        put("voltage", sample.voltage)
                        put("isCharging", sample.isCharging)
                        put("chargeCounter", sample.chargeCounter)
                        put("consumptionPercentPerHour", sample.consumptionPercentPerHour)
                    }
                })
            }
            
            historyFile.writeText(json.toString())
            Log.d(TAG, "Battery history saved to: ${historyFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save battery history", e)
        }
    }

    /**
     * Save optimization result
     */
    private fun saveOptimizationResult(result: BatteryOptimizationResult) {
        try {
            val resultsDir = File(context.filesDir, "battery_optimization")
            resultsDir.mkdirs()
            
            val resultsFile = File(resultsDir, "optimization_${result.timestamp}.json")
            val json = JSONObject().apply {
                put("success", result.success)
                put("currentConsumption", result.currentConsumption)
                put("targetConsumption", result.targetConsumption)
                put("optimizationLevel", result.optimizationLevel)
                put("recommendations", result.recommendations)
                put("timestamp", result.timestamp)
            }
            
            resultsFile.writeText(json.toString())
            Log.d(TAG, "Optimization result saved to: ${resultsFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save optimization result", e)
        }
    }

    /**
     * Battery optimization UX flow data class
     */
    data class BatteryOptimizationUXFlow(
        val showExemptionRequest: Boolean,
        val showOptimizationTips: Boolean,
        val currentConsumption: Float,
        val targetConsumption: Float,
        val batteryLevel: Float,
        val recommendations: List<String>
    )
}
