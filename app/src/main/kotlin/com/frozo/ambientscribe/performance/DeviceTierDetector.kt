package com.frozo.ambientscribe.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * Device Tier Detector - ST-6.1
 * Implements device tier detection (A vs B) based on RAM/CPU capabilities
 * Provides performance classification for optimization strategies
 */
class DeviceTierDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceTierDetector"
        private const val TIER_A_MIN_RAM_MB = 6144 // 6GB
        private const val TIER_A_MIN_CPU_CORES = 8
        private const val TIER_A_MIN_CPU_FREQ_MHZ = 2000 // 2GHz
        private const val TIER_A_MIN_STORAGE_GB = 64
        private const val TIER_A_MIN_ANDROID_API = 29
        
        // Performance thresholds
        private const val TIER_A_FIRST_MODEL_LOAD_MS = 8000 // 8 seconds
        private const val TIER_B_FIRST_MODEL_LOAD_MS = 12000 // 12 seconds
        private const val TIER_A_BATTERY_CONSUMPTION_PERCENT_PER_HOUR = 6
        private const val TIER_B_BATTERY_CONSUMPTION_PERCENT_PER_HOUR = 8
    }

    /**
     * Device tier enumeration
     */
    enum class DeviceTier {
        TIER_A,  // High-performance devices
        TIER_B,  // Standard devices
        UNSUPPORTED  // Below minimum requirements
    }

    /**
     * Device capability data class
     */
    data class DeviceCapabilities(
        val tier: DeviceTier,
        val ramTotalMB: Long,
        val ramAvailableMB: Long,
        val cpuCores: Int,
        val cpuMaxFreqMHz: Long,
        val storageTotalGB: Long,
        val storageAvailableGB: Long,
        val androidApiLevel: Int,
        val deviceModel: String,
        val manufacturer: String,
        val gpuRenderer: String?,
        val is64Bit: Boolean,
        val hasNeon: Boolean,
        val hasVulkan: Boolean,
        val performanceScore: Float,
        val recommendedSettings: PerformanceSettings
    )

    /**
     * Performance settings data class
     */
    data class PerformanceSettings(
        val maxConcurrentThreads: Int,
        val modelCacheSizeMB: Int,
        val audioBufferSizeMs: Int,
        val thermalThrottleThreshold: Float,
        val batteryOptimizationLevel: Int,
        val memoryPressureThreshold: Float,
        val firstModelLoadTimeoutMs: Int,
        val batteryConsumptionTargetPercentPerHour: Int
    )

    /**
     * Performance metrics data class
     */
    data class PerformanceMetrics(
        val firstModelLoadTimeMs: Long,
        val batteryConsumptionPercentPerHour: Float,
        val memoryUsageMB: Long,
        val cpuUsagePercent: Float,
        val thermalState: Int,
        val frameRate: Float,
        val audioLatencyMs: Int,
        val modelInferenceTimeMs: Long
    )

    /**
     * Detect device tier based on hardware capabilities
     */
    suspend fun detectDeviceTier(): DeviceCapabilities = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting device tier detection")

            val ramInfo = getRamInfo()
            val cpuInfo = getCpuInfo()
            val storageInfo = getStorageInfo()
            val gpuInfo = getGpuInfo()
            val systemInfo = getSystemInfo()

            val performanceScore = calculatePerformanceScore(
                ramInfo, cpuInfo, storageInfo, gpuInfo, systemInfo
            )

            val tier = determineDeviceTier(ramInfo, cpuInfo, storageInfo, systemInfo, performanceScore)
            val recommendedSettings = generatePerformanceSettings(tier, performanceScore)

            val capabilities = DeviceCapabilities(
                tier = tier,
                ramTotalMB = ramInfo.totalMB,
                ramAvailableMB = ramInfo.availableMB,
                cpuCores = cpuInfo.cores,
                cpuMaxFreqMHz = cpuInfo.maxFreqMHz,
                storageTotalGB = storageInfo.totalGB,
                storageAvailableGB = storageInfo.availableGB,
                androidApiLevel = systemInfo.apiLevel,
                deviceModel = systemInfo.model,
                manufacturer = systemInfo.manufacturer,
                gpuRenderer = gpuInfo.renderer,
                is64Bit = systemInfo.is64Bit,
                hasNeon = systemInfo.hasNeon,
                hasVulkan = systemInfo.hasVulkan,
                performanceScore = performanceScore,
                recommendedSettings = recommendedSettings
            )

            // Save device capabilities for future reference
            saveDeviceCapabilities(capabilities)

            Log.d(TAG, "Device tier detection completed: ${tier.name}")
            capabilities

        } catch (e: Exception) {
            Log.e(TAG, "Device tier detection failed", e)
            // Return default capabilities for unsupported devices
            createDefaultCapabilities()
        }
    }

    /**
     * Get RAM information
     */
    private fun getRamInfo(): RamInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMB = memInfo.totalMem / (1024 * 1024)
        val availableMB = memInfo.availMem / (1024 * 1024)

        return RamInfo(
            totalMB = totalMB,
            availableMB = availableMB,
            lowMemoryThreshold = memInfo.threshold / (1024 * 1024)
        )
    }

    /**
     * Get CPU information
     */
    private fun getCpuInfo(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxFreqMHz = getCpuMaxFrequency()
        val architecture = getCpuArchitecture()

        return CpuInfo(
            cores = cores,
            maxFreqMHz = maxFreqMHz,
            architecture = architecture,
            hasNeon = checkNeonSupport(),
            hasVulkan = checkVulkanSupport()
        )
    }

    /**
     * Get storage information
     */
    private fun getStorageInfo(): StorageInfo {
        val internalStorage = context.filesDir
        val totalSpace = internalStorage.totalSpace
        val freeSpace = internalStorage.freeSpace

        return StorageInfo(
            totalGB = totalSpace / (1024 * 1024 * 1024),
            availableGB = freeSpace / (1024 * 1024 * 1024),
            isExternalStorageAvailable = isExternalStorageAvailable()
        )
    }

    /**
     * Get GPU information
     */
    private fun getGpuInfo(): GpuInfo {
        val renderer = getGpuRenderer()
        val version = getGpuVersion()
        val vendor = getGpuVendor()

        return GpuInfo(
            renderer = renderer,
            version = version,
            vendor = vendor,
            supportsOpenGLES = checkOpenGLESSupport(),
            supportsVulkan = checkVulkanSupport()
        )
    }

    /**
     * Get system information
     */
    private fun getSystemInfo(): SystemInfo {
        return SystemInfo(
            apiLevel = Build.VERSION.SDK_INT,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            is64Bit = is64BitArchitecture(),
            hasNeon = checkNeonSupport(),
            hasVulkan = checkVulkanSupport()
        )
    }

    /**
     * Calculate performance score based on hardware capabilities
     */
    private fun calculatePerformanceScore(
        ramInfo: RamInfo,
        cpuInfo: CpuInfo,
        storageInfo: StorageInfo,
        gpuInfo: GpuInfo,
        systemInfo: SystemInfo
    ): Float {
        var score = 0f

        // RAM score (40% weight)
        val ramScore = when {
            ramInfo.totalMB >= 12288 -> 1.0f // 12GB+
            ramInfo.totalMB >= 8192 -> 0.9f  // 8GB
            ramInfo.totalMB >= 6144 -> 0.8f  // 6GB
            ramInfo.totalMB >= 4096 -> 0.6f  // 4GB
            ramInfo.totalMB >= 3072 -> 0.4f  // 3GB
            else -> 0.2f
        }
        score += ramScore * 0.4f

        // CPU score (30% weight)
        val cpuScore = when {
            cpuInfo.cores >= 8 && cpuInfo.maxFreqMHz >= 3000 -> 1.0f
            cpuInfo.cores >= 8 && cpuInfo.maxFreqMHz >= 2500 -> 0.9f
            cpuInfo.cores >= 6 && cpuInfo.maxFreqMHz >= 2500 -> 0.8f
            cpuInfo.cores >= 6 && cpuInfo.maxFreqMHz >= 2000 -> 0.7f
            cpuInfo.cores >= 4 && cpuInfo.maxFreqMHz >= 2000 -> 0.6f
            cpuInfo.cores >= 4 && cpuInfo.maxFreqMHz >= 1500 -> 0.5f
            else -> 0.3f
        }
        score += cpuScore * 0.3f

        // Storage score (15% weight)
        val storageScore = when {
            storageInfo.totalGB >= 128 -> 1.0f
            storageInfo.totalGB >= 64 -> 0.8f
            storageInfo.totalGB >= 32 -> 0.6f
            storageInfo.totalGB >= 16 -> 0.4f
            else -> 0.2f
        }
        score += storageScore * 0.15f

        // GPU score (10% weight)
        val gpuScore = when {
            gpuInfo.supportsVulkan -> 1.0f
            gpuInfo.supportsOpenGLES -> 0.8f
            else -> 0.5f
        }
        score += gpuScore * 0.1f

        // System score (5% weight)
        val systemScore = when {
            systemInfo.apiLevel >= 33 -> 1.0f
            systemInfo.apiLevel >= 31 -> 0.9f
            systemInfo.apiLevel >= 29 -> 0.8f
            systemInfo.apiLevel >= 26 -> 0.6f
            else -> 0.4f
        }
        score += systemScore * 0.05f

        return score.coerceIn(0f, 1f)
    }

    /**
     * Determine device tier based on capabilities
     */
    private fun determineDeviceTier(
        ramInfo: RamInfo,
        cpuInfo: CpuInfo,
        storageInfo: StorageInfo,
        systemInfo: SystemInfo,
        performanceScore: Float
    ): DeviceTier {
        // Check minimum requirements
        if (systemInfo.apiLevel < 26 || ramInfo.totalMB < 3072 || storageInfo.totalGB < 16) {
            return DeviceTier.UNSUPPORTED
        }

        // Determine tier based on performance score and key metrics
        return when {
            performanceScore >= 0.8f && 
            ramInfo.totalMB >= TIER_A_MIN_RAM_MB && 
            cpuInfo.cores >= TIER_A_MIN_CPU_CORES && 
            cpuInfo.maxFreqMHz >= TIER_A_MIN_CPU_FREQ_MHZ &&
            storageInfo.totalGB >= TIER_A_MIN_STORAGE_GB &&
            systemInfo.apiLevel >= TIER_A_MIN_ANDROID_API -> DeviceTier.TIER_A
            
            performanceScore >= 0.5f -> DeviceTier.TIER_B
            
            else -> DeviceTier.UNSUPPORTED
        }
    }

    /**
     * Generate performance settings based on device tier
     */
    private fun generatePerformanceSettings(tier: DeviceTier, performanceScore: Float): PerformanceSettings {
        return when (tier) {
            DeviceTier.TIER_A -> PerformanceSettings(
                maxConcurrentThreads = 8,
                modelCacheSizeMB = 512,
                audioBufferSizeMs = 30,
                thermalThrottleThreshold = 0.85f,
                batteryOptimizationLevel = 1,
                memoryPressureThreshold = 0.8f,
                firstModelLoadTimeoutMs = TIER_A_FIRST_MODEL_LOAD_MS,
                batteryConsumptionTargetPercentPerHour = TIER_A_BATTERY_CONSUMPTION_PERCENT_PER_HOUR
            )
            DeviceTier.TIER_B -> PerformanceSettings(
                maxConcurrentThreads = 4,
                modelCacheSizeMB = 256,
                audioBufferSizeMs = 50,
                thermalThrottleThreshold = 0.75f,
                batteryOptimizationLevel = 2,
                memoryPressureThreshold = 0.7f,
                firstModelLoadTimeoutMs = TIER_B_FIRST_MODEL_LOAD_MS,
                batteryConsumptionTargetPercentPerHour = TIER_B_BATTERY_CONSUMPTION_PERCENT_PER_HOUR
            )
            DeviceTier.UNSUPPORTED -> PerformanceSettings(
                maxConcurrentThreads = 2,
                modelCacheSizeMB = 128,
                audioBufferSizeMs = 100,
                thermalThrottleThreshold = 0.6f,
                batteryOptimizationLevel = 3,
                memoryPressureThreshold = 0.6f,
                firstModelLoadTimeoutMs = 15000,
                batteryConsumptionTargetPercentPerHour = 10
            )
        }
    }

    /**
     * Save device capabilities to file
     */
    private fun saveDeviceCapabilities(capabilities: DeviceCapabilities) {
        try {
            val capabilitiesDir = File(context.filesDir, "device_capabilities")
            capabilitiesDir.mkdirs()
            
            val capabilitiesFile = File(capabilitiesDir, "device_capabilities.json")
            val json = JSONObject().apply {
                put("tier", capabilities.tier.name)
                put("ramTotalMB", capabilities.ramTotalMB)
                put("ramAvailableMB", capabilities.ramAvailableMB)
                put("cpuCores", capabilities.cpuCores)
                put("cpuMaxFreqMHz", capabilities.cpuMaxFreqMHz)
                put("storageTotalGB", capabilities.storageTotalGB)
                put("storageAvailableGB", capabilities.storageAvailableGB)
                put("androidApiLevel", capabilities.androidApiLevel)
                put("deviceModel", capabilities.deviceModel)
                put("manufacturer", capabilities.manufacturer)
                put("gpuRenderer", capabilities.gpuRenderer)
                put("is64Bit", capabilities.is64Bit)
                put("hasNeon", capabilities.hasNeon)
                put("hasVulkan", capabilities.hasVulkan)
                put("performanceScore", capabilities.performanceScore)
                put("detectedAt", System.currentTimeMillis())
                
                // Performance settings
                put("maxConcurrentThreads", capabilities.recommendedSettings.maxConcurrentThreads)
                put("modelCacheSizeMB", capabilities.recommendedSettings.modelCacheSizeMB)
                put("audioBufferSizeMs", capabilities.recommendedSettings.audioBufferSizeMs)
                put("thermalThrottleThreshold", capabilities.recommendedSettings.thermalThrottleThreshold)
                put("batteryOptimizationLevel", capabilities.recommendedSettings.batteryOptimizationLevel)
                put("memoryPressureThreshold", capabilities.recommendedSettings.memoryPressureThreshold)
                put("firstModelLoadTimeoutMs", capabilities.recommendedSettings.firstModelLoadTimeoutMs)
                put("batteryConsumptionTargetPercentPerHour", capabilities.recommendedSettings.batteryConsumptionTargetPercentPerHour)
            }
            
            capabilitiesFile.writeText(json.toString())
            Log.d(TAG, "Device capabilities saved to: ${capabilitiesFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save device capabilities", e)
        }
    }

    /**
     * Load saved device capabilities
     */
    suspend fun loadDeviceCapabilities(): DeviceCapabilities? = withContext(Dispatchers.IO) {
        try {
            val capabilitiesFile = File(context.filesDir, "device_capabilities/device_capabilities.json")
            if (!capabilitiesFile.exists()) return@withContext null
            
            val json = JSONObject(capabilitiesFile.readText())
            
            DeviceCapabilities(
                tier = DeviceTier.valueOf(json.getString("tier")),
                ramTotalMB = json.getLong("ramTotalMB"),
                ramAvailableMB = json.getLong("ramAvailableMB"),
                cpuCores = json.getInt("cpuCores"),
                cpuMaxFreqMHz = json.getLong("cpuMaxFreqMHz"),
                storageTotalGB = json.getLong("storageTotalGB"),
                storageAvailableGB = json.getLong("storageAvailableGB"),
                androidApiLevel = json.getInt("androidApiLevel"),
                deviceModel = json.getString("deviceModel"),
                manufacturer = json.getString("manufacturer"),
                gpuRenderer = if (json.isNull("gpuRenderer")) null else json.getString("gpuRenderer"),
                is64Bit = json.getBoolean("is64Bit"),
                hasNeon = json.getBoolean("hasNeon"),
                hasVulkan = json.getBoolean("hasVulkan"),
                performanceScore = json.getDouble("performanceScore").toFloat(),
                recommendedSettings = PerformanceSettings(
                    maxConcurrentThreads = json.getInt("maxConcurrentThreads"),
                    modelCacheSizeMB = json.getInt("modelCacheSizeMB"),
                    audioBufferSizeMs = json.getInt("audioBufferSizeMs"),
                    thermalThrottleThreshold = json.getDouble("thermalThrottleThreshold").toFloat(),
                    batteryOptimizationLevel = json.getInt("batteryOptimizationLevel"),
                    memoryPressureThreshold = json.getDouble("memoryPressureThreshold").toFloat(),
                    firstModelLoadTimeoutMs = json.getInt("firstModelLoadTimeoutMs"),
                    batteryConsumptionTargetPercentPerHour = json.getInt("batteryConsumptionTargetPercentPerHour")
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load device capabilities", e)
            null
        }
    }

    // Helper methods for hardware detection
    private fun getCpuMaxFrequency(): Long {
        return try {
            val cpuInfoFile = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (cpuInfoFile.exists()) {
                cpuInfoFile.readText().trim().toLong() / 1000 // Convert to MHz
            } else {
                2000L // Default fallback
            }
        } catch (e: Exception) {
            2000L
        }
    }

    private fun getCpuArchitecture(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    private fun checkNeonSupport(): Boolean {
        return Build.SUPPORTED_ABIS.contains("arm64-v8a") || Build.SUPPORTED_ABIS.contains("armeabi-v7a")
    }

    private fun checkVulkanSupport(): Boolean {
        return Build.VERSION.SDK_INT >= 24
    }

    private fun checkOpenGLESSupport(): Boolean {
        return true // All Android devices support OpenGL ES
    }

    private fun getGpuRenderer(): String? {
        return try {
            // This would require OpenGL context in real implementation
            "Unknown GPU"
        } catch (e: Exception) {
            null
        }
    }

    private fun getGpuVersion(): String? {
        return try {
            "OpenGL ES 3.0"
        } catch (e: Exception) {
            null
        }
    }

    private fun getGpuVendor(): String? {
        return try {
            "Unknown"
        } catch (e: Exception) {
            null
        }
    }

    private fun isExternalStorageAvailable(): Boolean {
        return context.getExternalFilesDir(null) != null
    }

    private fun is64BitArchitecture(): Boolean {
        return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }

    private fun createDefaultCapabilities(): DeviceCapabilities {
        return DeviceCapabilities(
            tier = DeviceTier.UNSUPPORTED,
            ramTotalMB = 0,
            ramAvailableMB = 0,
            cpuCores = 0,
            cpuMaxFreqMHz = 0,
            storageTotalGB = 0,
            storageAvailableGB = 0,
            androidApiLevel = 0,
            deviceModel = "Unknown",
            manufacturer = "Unknown",
            gpuRenderer = null,
            is64Bit = false,
            hasNeon = false,
            hasVulkan = false,
            performanceScore = 0f,
            recommendedSettings = generatePerformanceSettings(DeviceTier.UNSUPPORTED, 0f)
        )
    }

    // Data classes for internal use
    private data class RamInfo(
        val totalMB: Long,
        val availableMB: Long,
        val lowMemoryThreshold: Long
    )

    private data class CpuInfo(
        val cores: Int,
        val maxFreqMHz: Long,
        val architecture: String,
        val hasNeon: Boolean,
        val hasVulkan: Boolean
    )

    private data class StorageInfo(
        val totalGB: Long,
        val availableGB: Long,
        val isExternalStorageAvailable: Boolean
    )

    private data class GpuInfo(
        val renderer: String?,
        val version: String?,
        val vendor: String?,
        val supportsOpenGLES: Boolean,
        val supportsVulkan: Boolean
    )

    private data class SystemInfo(
        val apiLevel: Int,
        val model: String,
        val manufacturer: String,
        val brand: String,
        val is64Bit: Boolean,
        val hasNeon: Boolean,
        val hasVulkan: Boolean
    )
}
