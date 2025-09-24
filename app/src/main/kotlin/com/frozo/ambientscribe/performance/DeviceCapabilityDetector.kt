package com.frozo.ambientscribe.performance

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Device capability detector for performance optimization
 */
class DeviceCapabilityDetector(private val context: Context) {
    
    enum class DeviceTier {
        TIER_A,  // High-end devices (8GB+ RAM, flagship SoCs)
        TIER_B,  // Mid-range devices (4-8GB RAM, mid-range SoCs)  
        TIER_C   // Low-end devices (<4GB RAM, entry-level SoCs)
    }
    
    fun getDeviceTier(): DeviceTier {
        val ramMB = getAvailableMemoryMB()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        
        return when {
            ramMB >= 8192 && cpuCores >= 8 -> DeviceTier.TIER_A
            ramMB >= 4096 && cpuCores >= 6 -> DeviceTier.TIER_B
            else -> DeviceTier.TIER_C
        }
    }
    
    fun getAvailableMemoryMB(): Long {
        return try {
            val memInfo = File("/proc/meminfo")
            if (memInfo.exists()) {
                val memTotal = memInfo.readText()
                    .lines()
                    .find { it.startsWith("MemTotal:") }
                    ?.split("\\s+".toRegex())
                    ?.get(1)
                    ?.toLongOrNull() ?: 4194304L // Default 4GB in KB
                
                memTotal / 1024 // Convert KB to MB
            } else {
                4096L // Default 4GB
            }
        } catch (e: Exception) {
            4096L // Default 4GB
        }
    }
    
    fun detectDeviceTier(): DeviceTier {
        return getDeviceTier()
    }
    
    fun getAvailableCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }
    
    fun getMaxFrequencyMHz(): Int {
        return try {
            val cpuInfo = File("/proc/cpuinfo")
            if (cpuInfo.exists()) {
                val freq = cpuInfo.readText()
                    .lines()
                    .find { it.startsWith("cpu MHz") }
                    ?.split("\\s+".toRegex())
                    ?.get(3)
                    ?.toIntOrNull() ?: 2000
                freq
            } else {
                2000 // Default 2GHz
            }
        } catch (e: Exception) {
            2000 // Default 2GHz
        }
    }
    
    fun getTotalRamGB(): Float {
        return getAvailableMemoryMB() / 1024.0f
    }
    
    fun hasNeonSupport(): Boolean {
        return Build.CPU_ABI.contains("arm64") || Build.CPU_ABI.contains("armeabi")
    }
    
    fun hasFp16Support(): Boolean {
        return Build.CPU_ABI.contains("arm64")
    }
    
    fun hasSdotSupport(): Boolean {
        return Build.CPU_ABI.contains("arm64")
    }

    /**
     * Initialize device capability detection
     */
    suspend fun initialize() {
        // No initialization needed for now
    }

    /**
     * Get device capabilities
     */
    fun getCapabilities(): Map<String, Any> {
        return mapOf(
            "device_tier" to getDeviceTier(),
            "ram_gb" to getTotalRamGB(),
            "cpu_cores" to getAvailableCores(),
            "cpu_freq_mhz" to getMaxFrequencyMHz(),
            "has_neon" to hasNeonSupport(),
            "has_fp16" to hasFp16Support(),
            "has_sdot" to hasSdotSupport()
        )
    }
}