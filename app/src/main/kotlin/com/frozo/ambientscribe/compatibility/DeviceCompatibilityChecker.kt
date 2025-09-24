package com.frozo.ambientscribe.compatibility

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Device Compatibility Checker - ST-6.2
 * Implements install-time device compatibility blocking via Play Store
 * Provides device compatibility validation and blocking mechanisms
 */
class DeviceCompatibilityChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceCompatibilityChecker"
        private const val MIN_ANDROID_API = 26
        private const val MIN_RAM_MB = 3072
        private const val MIN_STORAGE_GB = 16
        private const val MIN_CPU_CORES = 4
        private const val MIN_CPU_FREQ_MHZ = 1500
        private const val REQUIRED_FEATURES = arrayOf(
            PackageManager.FEATURE_MICROPHONE,
            PackageManager.FEATURE_AUDIO_OUTPUT,
            PackageManager.FEATURE_WIFI,
            PackageManager.FEATURE_BLUETOOTH
        )
    }

    /**
     * Device compatibility result
     */
    data class CompatibilityResult(
        val isCompatible: Boolean,
        val compatibilityScore: Float,
        val blockingReasons: List<String>,
        val warnings: List<String>,
        val recommendations: List<String>,
        val deviceInfo: DeviceInfo,
        val timestamp: Long
    )

    /**
     * Device information
     */
    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val apiLevel: Int,
        val ramTotalMB: Long,
        val storageTotalGB: Long,
        val cpuCores: Int,
        val cpuMaxFreqMHz: Long,
        val architecture: String,
        val is64Bit: Boolean,
        val hasNeon: Boolean,
        val hasVulkan: Boolean,
        val features: List<String>
    )

    /**
     * Check device compatibility
     */
    suspend fun checkDeviceCompatibility(): CompatibilityResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting device compatibility check")

            val deviceInfo = getDeviceInfo()
            val blockingReasons = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val recommendations = mutableListOf<String>()

            // Check Android API level
            if (deviceInfo.apiLevel < MIN_ANDROID_API) {
                blockingReasons.add("Android API level ${deviceInfo.apiLevel} is below minimum required ${MIN_ANDROID_API}")
            }

            // Check RAM
            if (deviceInfo.ramTotalMB < MIN_RAM_MB) {
                blockingReasons.add("RAM ${deviceInfo.ramTotalMB}MB is below minimum required ${MIN_RAM_MB}MB")
            } else if (deviceInfo.ramTotalMB < 4096) {
                warnings.add("RAM ${deviceInfo.ramTotalMB}MB is below recommended 4GB")
                recommendations.add("Consider upgrading to a device with more RAM for better performance")
            }

            // Check storage
            if (deviceInfo.storageTotalGB < MIN_STORAGE_GB) {
                blockingReasons.add("Storage ${deviceInfo.storageTotalGB}GB is below minimum required ${MIN_STORAGE_GB}GB")
            } else if (deviceInfo.storageTotalGB < 32) {
                warnings.add("Storage ${deviceInfo.storageTotalGB}GB is below recommended 32GB")
                recommendations.add("Consider upgrading to a device with more storage")
            }

            // Check CPU cores
            if (deviceInfo.cpuCores < MIN_CPU_CORES) {
                blockingReasons.add("CPU cores ${deviceInfo.cpuCores} is below minimum required ${MIN_CPU_CORES}")
            } else if (deviceInfo.cpuCores < 6) {
                warnings.add("CPU cores ${deviceInfo.cpuCores} is below recommended 6")
                recommendations.add("Consider upgrading to a device with more CPU cores")
            }

            // Check CPU frequency
            if (deviceInfo.cpuMaxFreqMHz < MIN_CPU_FREQ_MHZ) {
                blockingReasons.add("CPU frequency ${deviceInfo.cpuMaxFreqMHz}MHz is below minimum required ${MIN_CPU_FREQ_MHZ}MHz")
            } else if (deviceInfo.cpuMaxFreqMHz < 2000) {
                warnings.add("CPU frequency ${deviceInfo.cpuMaxFreqMHz}MHz is below recommended 2GHz")
                recommendations.add("Consider upgrading to a device with higher CPU frequency")
            }

            // Check required features
            val missingFeatures = checkRequiredFeatures()
            if (missingFeatures.isNotEmpty()) {
                blockingReasons.add("Missing required features: ${missingFeatures.joinToString(", ")}")
            }

            // Check architecture
            if (!deviceInfo.is64Bit) {
                warnings.add("Device is not 64-bit, performance may be limited")
                recommendations.add("Consider upgrading to a 64-bit device for better performance")
            }

            // Check NEON support
            if (!deviceInfo.hasNeon) {
                warnings.add("Device does not support NEON instructions, performance may be limited")
                recommendations.add("Consider upgrading to a device with NEON support")
            }

            // Check Vulkan support
            if (!deviceInfo.hasVulkan) {
                warnings.add("Device does not support Vulkan, some features may be limited")
                recommendations.add("Consider upgrading to a device with Vulkan support")
            }

            // Calculate compatibility score
            val compatibilityScore = calculateCompatibilityScore(deviceInfo, blockingReasons, warnings)

            val result = CompatibilityResult(
                isCompatible = blockingReasons.isEmpty(),
                compatibilityScore = compatibilityScore,
                blockingReasons = blockingReasons,
                warnings = warnings,
                recommendations = recommendations,
                deviceInfo = deviceInfo,
                timestamp = System.currentTimeMillis()
            )

            // Save compatibility result
            saveCompatibilityResult(result)

            Log.d(TAG, "Device compatibility check completed. Compatible: ${result.isCompatible}")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Device compatibility check failed", e)
            createErrorResult(e)
        }
    }

    /**
     * Get device information
     */
    private fun getDeviceInfo(): DeviceInfo {
        val packageManager = context.packageManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val ramTotalMB = memInfo.totalMem / (1024 * 1024)
        val storageTotalGB = context.filesDir.totalSpace / (1024 * 1024 * 1024)
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuMaxFreqMHz = getCpuMaxFrequency()
        val architecture = getCpuArchitecture()
        val is64Bit = is64BitArchitecture()
        val hasNeon = checkNeonSupport()
        val hasVulkan = checkVulkanSupport()
        val features = getAvailableFeatures()

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            ramTotalMB = ramTotalMB,
            storageTotalGB = storageTotalGB,
            cpuCores = cpuCores,
            cpuMaxFreqMHz = cpuMaxFreqMHz,
            architecture = architecture,
            is64Bit = is64Bit,
            hasNeon = hasNeon,
            hasVulkan = hasVulkan,
            features = features
        )
    }

    /**
     * Check required features
     */
    private fun checkRequiredFeatures(): List<String> {
        val packageManager = context.packageManager
        val missingFeatures = mutableListOf<String>()

        REQUIRED_FEATURES.forEach { feature ->
            if (!packageManager.hasSystemFeature(feature)) {
                missingFeatures.add(feature)
            }
        }

        return missingFeatures
    }

    /**
     * Get available features
     */
    private fun getAvailableFeatures(): List<String> {
        val packageManager = context.packageManager
        val features = mutableListOf<String>()

        REQUIRED_FEATURES.forEach { feature ->
            if (packageManager.hasSystemFeature(feature)) {
                features.add(feature)
            }
        }

        return features
    }

    /**
     * Calculate compatibility score
     */
    private fun calculateCompatibilityScore(
        deviceInfo: DeviceInfo,
        blockingReasons: List<String>,
        warnings: List<String>
    ): Float {
        if (blockingReasons.isNotEmpty()) {
            return 0f
        }

        var score = 1.0f

        // Deduct points for warnings
        score -= warnings.size * 0.1f

        // Bonus points for high-end features
        if (deviceInfo.ramTotalMB >= 8192) score += 0.1f
        if (deviceInfo.cpuCores >= 8) score += 0.1f
        if (deviceInfo.cpuMaxFreqMHz >= 2500) score += 0.1f
        if (deviceInfo.storageTotalGB >= 128) score += 0.1f
        if (deviceInfo.hasVulkan) score += 0.1f

        return score.coerceIn(0f, 1f)
    }

    /**
     * Get CPU max frequency
     */
    private fun getCpuMaxFrequency(): Long {
        return try {
            val cpuInfoFile = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (cpuInfoFile.exists()) {
                cpuInfoFile.readText().trim().toLong() / 1000 // Convert to MHz
            } else {
                2000L // Default fallback
            }
        } catch (e: Exception) {
            2000L
        }
    }

    /**
     * Get CPU architecture
     */
    private fun getCpuArchitecture(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    /**
     * Check if 64-bit architecture
     */
    private fun is64BitArchitecture(): Boolean {
        return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }

    /**
     * Check NEON support
     */
    private fun checkNeonSupport(): Boolean {
        return Build.SUPPORTED_ABIS.contains("arm64-v8a") || Build.SUPPORTED_ABIS.contains("armeabi-v7a")
    }

    /**
     * Check Vulkan support
     */
    private fun checkVulkanSupport(): Boolean {
        return Build.VERSION.SDK_INT >= 24
    }

    /**
     * Save compatibility result
     */
    private fun saveCompatibilityResult(result: CompatibilityResult) {
        try {
            val compatibilityDir = File(context.filesDir, "compatibility")
            compatibilityDir.mkdirs()
            
            val resultFile = File(compatibilityDir, "compatibility_${result.timestamp}.json")
            val json = JSONObject().apply {
                put("isCompatible", result.isCompatible)
                put("compatibilityScore", result.compatibilityScore)
                put("blockingReasons", result.blockingReasons)
                put("warnings", result.warnings)
                put("recommendations", result.recommendations)
                put("timestamp", result.timestamp)
                
                // Device info
                put("manufacturer", result.deviceInfo.manufacturer)
                put("model", result.deviceInfo.model)
                put("androidVersion", result.deviceInfo.androidVersion)
                put("apiLevel", result.deviceInfo.apiLevel)
                put("ramTotalMB", result.deviceInfo.ramTotalMB)
                put("storageTotalGB", result.deviceInfo.storageTotalGB)
                put("cpuCores", result.deviceInfo.cpuCores)
                put("cpuMaxFreqMHz", result.deviceInfo.cpuMaxFreqMHz)
                put("architecture", result.deviceInfo.architecture)
                put("is64Bit", result.deviceInfo.is64Bit)
                put("hasNeon", result.deviceInfo.hasNeon)
                put("hasVulkan", result.deviceInfo.hasVulkan)
                put("features", result.deviceInfo.features)
            }
            
            resultFile.writeText(json.toString())
            Log.d(TAG, "Compatibility result saved to: ${resultFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save compatibility result", e)
        }
    }

    /**
     * Create error result
     */
    private fun createErrorResult(exception: Exception): CompatibilityResult {
        return CompatibilityResult(
            isCompatible = false,
            compatibilityScore = 0f,
            blockingReasons = listOf("Compatibility check failed: ${exception.message}"),
            warnings = emptyList(),
            recommendations = listOf("Please try again or contact support"),
            deviceInfo = DeviceInfo(
                manufacturer = "Unknown",
                model = "Unknown",
                androidVersion = "Unknown",
                apiLevel = 0,
                ramTotalMB = 0,
                storageTotalGB = 0,
                cpuCores = 0,
                cpuMaxFreqMHz = 0,
                architecture = "unknown",
                is64Bit = false,
                hasNeon = false,
                hasVulkan = false,
                features = emptyList()
            ),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Check if device should be blocked from installation
     */
    suspend fun shouldBlockInstallation(): Boolean = withContext(Dispatchers.IO) {
        val result = checkDeviceCompatibility()
        !result.isCompatible
    }

    /**
     * Get compatibility report for Play Store
     */
    suspend fun getCompatibilityReport(): String = withContext(Dispatchers.IO) {
        val result = checkDeviceCompatibility()
        
        val report = StringBuilder()
        report.appendLine("Device Compatibility Report")
        report.appendLine("==========================")
        report.appendLine("Device: ${result.deviceInfo.manufacturer} ${result.deviceInfo.model}")
        report.appendLine("Android: ${result.deviceInfo.androidVersion} (API ${result.deviceInfo.apiLevel})")
        report.appendLine("Compatibility Score: ${(result.compatibilityScore * 100).toInt()}%")
        report.appendLine("Compatible: ${if (result.isCompatible) "Yes" else "No"}")
        report.appendLine()
        
        if (result.blockingReasons.isNotEmpty()) {
            report.appendLine("Blocking Reasons:")
            result.blockingReasons.forEach { reason ->
                report.appendLine("- $reason")
            }
            report.appendLine()
        }
        
        if (result.warnings.isNotEmpty()) {
            report.appendLine("Warnings:")
            result.warnings.forEach { warning ->
                report.appendLine("- $warning")
            }
            report.appendLine()
        }
        
        if (result.recommendations.isNotEmpty()) {
            report.appendLine("Recommendations:")
            result.recommendations.forEach { recommendation ->
                report.appendLine("- $recommendation")
            }
        }
        
        report.toString()
    }
}
