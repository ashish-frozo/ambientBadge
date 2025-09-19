package com.frozo.ambientscribe.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

/**
 * Detects device capabilities to determine optimal performance settings.
 * Used to classify devices into tiers and determine appropriate resource usage.
 */
class DeviceCapabilityDetector(private val context: Context) {

    companion object {
        // Memory thresholds
        private const val TIER_A_MIN_RAM_MB = 4096 // 4GB
        private const val TIER_B_MIN_RAM_MB = 2048 // 2GB
        
        // Storage thresholds
        private const val TIER_A_MIN_STORAGE_GB = 32
        private const val TIER_B_MIN_STORAGE_GB = 16
        
        // CPU core thresholds
        private const val TIER_A_MIN_CORES = 6
        private const val TIER_B_MIN_CORES = 4
        
        // CPU frequency thresholds
        private const val TIER_A_MIN_FREQ_MHZ = 2000
        private const val TIER_B_MIN_FREQ_MHZ = 1400
        
        // Tier A device models (partial list)
        private val TIER_A_MODELS = setOf(
            // Samsung
            "SM-G99", "SM-S9", "SM-N9", // Galaxy S, Note series
            "SM-F9", // Fold series
            // Google
            "Pixel 6", "Pixel 7", "Pixel 8",
            // OnePlus
            "OnePlus 9", "OnePlus 10", "OnePlus 11",
            // Xiaomi
            "M2007", "M2102", "M2104", // Mi 11, 12 series
            "22021", "22081", // 12S, 12T series
            // Other high-end
            "ROG Phone", "Legion"
        )
    }

    /**
     * Detect device tier based on hardware capabilities
     */
    fun detectDeviceTier(): ThermalManager.DeviceTier {
        Timber.d("Detecting device tier...")
        
        // Check if device model is known high-end
        if (isKnownTierADevice()) {
            Timber.i("Device recognized as Tier A from model: ${Build.MODEL}")
            return ThermalManager.DeviceTier.TIER_A
        }
        
        // Check RAM
        val ramMb = getTotalRamMb()
        val cpuCores = getAvailableCpuCores()
        val maxFreqMhz = getMaxCpuFrequencyMhz()
        val storageFreeGb = getAvailableStorageGb()
        
        Timber.d("Device specs - RAM: $ramMb MB, CPU cores: $cpuCores, " +
                "Max freq: $maxFreqMhz MHz, Free storage: $storageFreeGb GB")
        
        // Determine tier based on combined factors
        val isTierA = ramMb >= TIER_A_MIN_RAM_MB &&
                cpuCores >= TIER_A_MIN_CORES &&
                maxFreqMhz >= TIER_A_MIN_FREQ_MHZ &&
                storageFreeGb >= TIER_A_MIN_STORAGE_GB
        
        val isTierB = ramMb >= TIER_B_MIN_RAM_MB &&
                cpuCores >= TIER_B_MIN_CORES &&
                maxFreqMhz >= TIER_B_MIN_FREQ_MHZ &&
                storageFreeGb >= TIER_B_MIN_STORAGE_GB
        
        return when {
            isTierA -> {
                Timber.i("Device classified as Tier A based on specs")
                ThermalManager.DeviceTier.TIER_A
            }
            isTierB -> {
                Timber.i("Device classified as Tier B based on specs")
                ThermalManager.DeviceTier.TIER_B
            }
            else -> {
                Timber.w("Device below minimum specs, defaulting to Tier B")
                ThermalManager.DeviceTier.TIER_B
            }
        }
    }
    
    /**
     * Detect optimal thread count based on device capabilities
     */
    fun detectOptimalThreadCount(): Int {
        val cpuCores = getAvailableCpuCores()
        val ramMb = getTotalRamMb()
        
        return when {
            cpuCores >= 8 && ramMb >= TIER_A_MIN_RAM_MB -> 6
            cpuCores >= 6 && ramMb >= TIER_A_MIN_RAM_MB -> 4
            cpuCores >= 4 -> 3
            else -> 2
        }
    }
    
    /**
     * Detect if device has NEON/FP16/SDOT capabilities
     */
    fun detectAdvancedInstructionSets(): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        
        // Check for NEON support (most ARM devices have this)
        result["NEON"] = Build.SUPPORTED_ABIS.any { it.startsWith("arm") }
        
        // Check for FP16 support (ARMv8.2+)
        result["FP16"] = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
                isArmv82OrHigher()
        
        // Check for SDOT support (ARMv8.4+)
        result["SDOT"] = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                isArmv84OrHigher()
        
        Timber.d("Instruction set detection: NEON=${result["NEON"]}, " +
                "FP16=${result["FP16"]}, SDOT=${result["SDOT"]}")
        
        return result
    }
    
    /**
     * Check if device is known to be Tier A from model number
     */
    private fun isKnownTierADevice(): Boolean {
        val model = Build.MODEL
        return TIER_A_MODELS.any { model.contains(it, ignoreCase = true) }
    }
    
    /**
     * Get total RAM in megabytes
     */
    private fun getTotalRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        return (memoryInfo.totalMem / (1024 * 1024)).toInt()
    }
    
    /**
     * Get available CPU cores
     */
    private fun getAvailableCpuCores(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            Timber.w(e, "Error getting CPU cores")
            4 // Default to 4 cores
        }
    }
    
    /**
     * Get maximum CPU frequency in MHz
     */
    private fun getMaxCpuFrequencyMhz(): Int {
        try {
            val cpuCount = getAvailableCpuCores()
            var maxFreq = 0
            
            for (i in 0 until cpuCount) {
                val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (freqFile.exists()) {
                    val freq = BufferedReader(FileReader(freqFile)).use { it.readLine() }.toInt() / 1000
                    maxFreq = maxOf(maxFreq, freq)
                }
            }
            
            return maxFreq
            
        } catch (e: Exception) {
            Timber.w(e, "Error getting CPU frequency")
            return 0
        }
    }
    
    /**
     * Get available storage in gigabytes
     */
    private fun getAvailableStorageGb(): Int {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
            return (bytesAvailable / (1024 * 1024 * 1024)).toInt()
            
        } catch (e: Exception) {
            Timber.w(e, "Error getting storage space")
            return 0
        }
    }
    
    /**
     * Check if device has ARMv8.2 or higher (for FP16 support)
     */
    private fun isArmv82OrHigher(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getprop ro.product.cpu.abi")
            val abi = BufferedReader(process.inputStream.reader()).use { it.readLine() }
            
            // ARMv8.2 is typically in newer devices with arm64-v8a
            abi.contains("arm64-v8a")
            
        } catch (e: IOException) {
            Timber.w(e, "Error checking ARM version")
            false
        }
    }
    
    /**
     * Check if device has ARMv8.4 or higher (for SDOT support)
     */
    private fun isArmv84OrHigher(): Boolean {
        // This is a simplification - ARMv8.4 is typically only in very new devices
        // and there's no reliable way to detect it from user space
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}
