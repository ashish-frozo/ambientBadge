package com.frozo.ambientscribe.ai

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import com.frozo.ambientscribe.performance.DeviceCapabilityDetector
import com.frozo.ambientscribe.performance.ThermalManager
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages AI resources and throttling
 */
class AIResourceManager(
    private val context: Context,
    private val deviceCapabilityDetector: DeviceCapabilityDetector,
    private val thermalManager: ThermalManager,
    private val metricsCollector: MetricsCollector
) {

    companion object {
        private const val TAG = "AIResourceManager"
        private const val MIN_BATTERY_LEVEL = 0.15f
        private const val MIN_MEMORY_AVAILABLE = 200 * 1024 * 1024L // 200MB
        private const val MAX_THERMAL_THROTTLING = 2
        private const val THROTTLE_DURATION = 60 * 1000L // 1 minute
    }

    private var lastThrottleTime = 0L
    private var throttleCount = 0

    /**
     * Load AI resources
     */
    suspend fun loadResources() = withContext(Dispatchers.IO) {
        try {
            // Load models and resources
            loadModels()
            loadVocabulary()
            loadConfigs()

            // Initialize device capabilities
            deviceCapabilityDetector.initialize()

            // Start thermal monitoring
            thermalManager.startMonitoring()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading resources: ${e.message}", e)
            false
        }
    }

    /**
     * Check if AI should be throttled
     */
    fun shouldThrottleAI(): Boolean {
        // Check battery level
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) / 100f
        if (batteryLevel < MIN_BATTERY_LEVEL) {
            Log.w(TAG, "Throttling AI due to low battery: $batteryLevel")
            return true
        }

        // Check available memory
        val runtime = Runtime.getRuntime()
        val availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        if (availableMemory < MIN_MEMORY_AVAILABLE) {
            Log.w(TAG, "Throttling AI due to low memory: $availableMemory")
            return true
        }

        // Check thermal state
        if (thermalManager.getThermalThrottling() >= MAX_THERMAL_THROTTLING) {
            Log.w(TAG, "Throttling AI due to thermal throttling: ${thermalManager.getThermalThrottling()}")
            return true
        }

        // Check throttle count
        val now = System.currentTimeMillis()
        if (now - lastThrottleTime < THROTTLE_DURATION) {
            if (throttleCount >= 3) {
                Log.w(TAG, "Throttling AI due to high throttle count: $throttleCount")
                return true
            }
        } else {
            // Reset throttle count after duration
            throttleCount = 0
            lastThrottleTime = now
        }

        return false
    }

    /**
     * Load models
     */
    private suspend fun loadModels() = withContext(Dispatchers.IO) {
        try {
            // Load ASR model
            val asrModelFile = File(context.filesDir, "models/whisper_tiny_int8.bin")
            if (!asrModelFile.exists()) {
                copyAsset("models/whisper_tiny_int8.bin", asrModelFile)
            }

            // Load LLM model
            val llmModelFile = File(context.filesDir, "models/llama_1.1b_q4.bin")
            if (!llmModelFile.exists()) {
                copyAsset("models/llama_1.1b_q4.bin", llmModelFile)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading models: ${e.message}", e)
            false
        }
    }

    /**
     * Load vocabulary
     */
    private suspend fun loadVocabulary() = withContext(Dispatchers.IO) {
        try {
            // Load ASR vocabulary
            val asrVocabFile = File(context.filesDir, "vocab/asr_vocab.txt")
            if (!asrVocabFile.exists()) {
                copyAsset("vocab/asr_vocab.txt", asrVocabFile)
            }

            // Load LLM vocabulary
            val llmVocabFile = File(context.filesDir, "vocab/llm_vocab.json")
            if (!llmVocabFile.exists()) {
                copyAsset("vocab/llm_vocab.json", llmVocabFile)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocabulary: ${e.message}", e)
            false
        }
    }

    /**
     * Load configs
     */
    private suspend fun loadConfigs() = withContext(Dispatchers.IO) {
        try {
            // Load ASR config
            val asrConfigFile = File(context.filesDir, "config/asr_config.json")
            if (!asrConfigFile.exists()) {
                copyAsset("config/asr_config.json", asrConfigFile)
            }

            // Load LLM config
            val llmConfigFile = File(context.filesDir, "config/llm_config.json")
            if (!llmConfigFile.exists()) {
                copyAsset("config/llm_config.json", llmConfigFile)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading configs: ${e.message}", e)
            false
        }
    }

    /**
     * Copy asset to file
     */
    private fun copyAsset(assetPath: String, outputFile: File) {
        try {
            outputFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset $assetPath: ${e.message}", e)
            throw e
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        thermalManager.stopMonitoring()
    }

    /**
     * Get current device capabilities
     */
    fun getDeviceCapabilities(): Map<String, Any> {
        return deviceCapabilityDetector.getCapabilities()
    }

    /**
     * Get current thermal state
     */
    fun getThermalState(): Int {
        return thermalManager.getThermalThrottling()
    }

    /**
     * Get current resource metrics
     */
    suspend fun getResourceMetrics(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val availableMemory = maxMemory - usedMemory

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) / 100f

        return mapOf(
            "max_memory" to maxMemory,
            "total_memory" to totalMemory,
            "free_memory" to freeMemory,
            "used_memory" to usedMemory,
            "available_memory" to availableMemory,
            "battery_level" to batteryLevel,
            "thermal_throttling" to thermalManager.getThermalThrottling(),
            "throttle_count" to throttleCount,
            "last_throttle_time" to lastThrottleTime
        )
    }
}