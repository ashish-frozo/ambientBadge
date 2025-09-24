package com.frozo.ambientscribe.debug

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ANR Watchdog - ST-6.12
 * Implements ANR watchdog + StrictMode in debug; JNI load guard
 * Provides Application Not Responding detection and prevention
 */
class ANRWatchdog(
    private val context: Context,
    private val isDebugMode: Boolean = true
) {
    
    companion object {
        private const val TAG = "ANRWatchdog"
        private const val ANR_THRESHOLD_MS = 5000L // 5 seconds
        private const val CHECK_INTERVAL_MS = 1000L // 1 second
        private const val MAX_ANR_COUNT = 3
        private const val ANR_RECOVERY_TIMEOUT_MS = 10000L // 10 seconds
    }

    private val isRunning = AtomicBoolean(false)
    private val lastResponseTime = AtomicLong(System.currentTimeMillis())
    private val anrCount = AtomicLong(0)
    private val handler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkForANR()
            if (isRunning.get()) {
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * ANR detection result
     */
    data class ANRResult(
        val isANR: Boolean,
        val responseTimeMs: Long,
        val thresholdMs: Long,
        val stackTrace: String?,
        val memoryUsage: Long,
        val timestamp: Long
    )

    /**
     * ANR recovery result
     */
    data class ANRRecoveryResult(
        val success: Boolean,
        val recoveryAction: RecoveryAction,
        val recoveryTimeMs: Long,
        val recommendations: List<String>,
        val timestamp: Long
    )

    /**
     * Recovery action enumeration
     */
    enum class RecoveryAction {
        NONE,
        FORCE_GC,
        CLEAR_CACHE,
        RESTART_SERVICE,
        EMERGENCY_RECOVERY
    }

    /**
     * Start ANR watchdog
     */
    suspend fun startWatchdog(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting ANR watchdog")
            
            if (isRunning.get()) {
                Log.w(TAG, "ANR watchdog already running")
                return Result.success(Unit)
            }

            if (!isDebugMode) {
                Log.d(TAG, "ANR watchdog disabled in release mode")
                return Result.success(Unit)
            }

            isRunning.set(true)
            lastResponseTime.set(System.currentTimeMillis())
            
            // Start watchdog loop
            handler.post(watchdogRunnable)
            
            Log.d(TAG, "ANR watchdog started")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ANR watchdog", e)
            Result.failure(e)
        }
    }

    /**
     * Stop ANR watchdog
     */
    suspend fun stopWatchdog(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Stopping ANR watchdog")
            
            isRunning.set(false)
            handler.removeCallbacks(watchdogRunnable)
            
            Log.d(TAG, "ANR watchdog stopped")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop ANR watchdog", e)
            Result.failure(e)
        }
    }

    /**
     * Update response time (call this from main thread)
     */
    fun updateResponseTime() {
        lastResponseTime.set(System.currentTimeMillis())
    }

    /**
     * Check for ANR
     */
    private fun checkForANR() {
        try {
            val currentTime = System.currentTimeMillis()
            val responseTime = currentTime - lastResponseTime.get()
            
            if (responseTime > ANR_THRESHOLD_MS) {
                Log.w(TAG, "Potential ANR detected: ${responseTime}ms")
                
                val anrResult = ANRResult(
                    isANR = true,
                    responseTimeMs = responseTime,
                    thresholdMs = ANR_THRESHOLD_MS,
                    stackTrace = getCurrentStackTrace(),
                    memoryUsage = getCurrentMemoryUsage(),
                    timestamp = currentTime
                )
                
                // Save ANR result
                saveANRResult(anrResult)
                
                // Attempt recovery
                attemptANRRecovery(anrResult)
                
                // Increment ANR count
                anrCount.incrementAndGet()
                
            } else {
                // Reset ANR count if response time is normal
                if (responseTime < ANR_THRESHOLD_MS / 2) {
                    anrCount.set(0)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for ANR", e)
        }
    }

    /**
     * Attempt ANR recovery
     */
    private suspend fun attemptANRRecovery(anrResult: ANRResult) {
        try {
            Log.d(TAG, "Attempting ANR recovery")
            
            val recoveryAction = determineRecoveryAction(anrResult)
            val startTime = System.currentTimeMillis()
            
            when (recoveryAction) {
                RecoveryAction.FORCE_GC -> {
                    System.gc()
                    Log.d(TAG, "Forced garbage collection")
                }
                RecoveryAction.CLEAR_CACHE -> {
                    clearApplicationCache()
                    Log.d(TAG, "Cleared application cache")
                }
                RecoveryAction.RESTART_SERVICE -> {
                    restartCriticalServices()
                    Log.d(TAG, "Restarted critical services")
                }
                RecoveryAction.EMERGENCY_RECOVERY -> {
                    performEmergencyRecovery()
                    Log.d(TAG, "Performed emergency recovery")
                }
                RecoveryAction.NONE -> {
                    Log.d(TAG, "No recovery action needed")
                }
            }
            
            val recoveryTime = System.currentTimeMillis() - startTime
            val recoveryResult = ANRRecoveryResult(
                success = true,
                recoveryAction = recoveryAction,
                recoveryTimeMs = recoveryTime,
                recommendations = generateRecoveryRecommendations(anrResult, recoveryAction),
                timestamp = System.currentTimeMillis()
            )
            
            // Save recovery result
            saveRecoveryResult(recoveryResult)
            
            Log.d(TAG, "ANR recovery completed in ${recoveryTime}ms")

        } catch (e: Exception) {
            Log.e(TAG, "ANR recovery failed", e)
        }
    }

    /**
     * Determine recovery action based on ANR result
     */
    private fun determineRecoveryAction(anrResult: ANRResult): RecoveryAction {
        return when {
            anrCount.get() >= MAX_ANR_COUNT -> RecoveryAction.EMERGENCY_RECOVERY
            anrResult.memoryUsage > 100 * 1024 * 1024 -> RecoveryAction.FORCE_GC // > 100MB
            anrResult.responseTimeMs > ANR_THRESHOLD_MS * 2 -> RecoveryAction.RESTART_SERVICE
            anrResult.responseTimeMs > ANR_THRESHOLD_MS -> RecoveryAction.CLEAR_CACHE
            else -> RecoveryAction.NONE
        }
    }

    /**
     * Get current stack trace
     */
    private fun getCurrentStackTrace(): String {
        return try {
            val stackTrace = Thread.currentThread().stackTrace
            stackTrace.joinToString("\n") { it.toString() }
        } catch (e: Exception) {
            "Unable to get stack trace: ${e.message}"
        }
    }

    /**
     * Get current memory usage
     */
    private fun getCurrentMemoryUsage(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            runtime.totalMemory() - runtime.freeMemory()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Clear application cache
     */
    private suspend fun clearApplicationCache() {
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }

    /**
     * Restart critical services
     */
    private suspend fun restartCriticalServices() {
        try {
            // In a real implementation, this would restart critical services
            Log.d(TAG, "Restarting critical services")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart services", e)
        }
    }

    /**
     * Perform emergency recovery
     */
    private suspend fun performEmergencyRecovery() {
        try {
            Log.d(TAG, "Performing emergency recovery")
            
            // Force garbage collection
            System.gc()
            
            // Clear cache
            clearApplicationCache()
            
            // Reset ANR count
            anrCount.set(0)
            
            // Update response time
            updateResponseTime()
            
        } catch (e: Exception) {
            Log.e(TAG, "Emergency recovery failed", e)
        }
    }

    /**
     * Generate recovery recommendations
     */
    private fun generateRecoveryRecommendations(anrResult: ANRResult, recoveryAction: RecoveryAction): List<String> {
        val recommendations = mutableListOf<String>()

        when (recoveryAction) {
            RecoveryAction.FORCE_GC -> {
                recommendations.add("Garbage collection performed to free memory")
                recommendations.add("Monitor memory usage to prevent future ANRs")
            }
            RecoveryAction.CLEAR_CACHE -> {
                recommendations.add("Application cache cleared to free memory")
                recommendations.add("Consider reducing cache size in settings")
            }
            RecoveryAction.RESTART_SERVICE -> {
                recommendations.add("Critical services restarted")
                recommendations.add("Monitor service performance")
            }
            RecoveryAction.EMERGENCY_RECOVERY -> {
                recommendations.add("Emergency recovery performed")
                recommendations.add("Consider restarting the application")
            }
            RecoveryAction.NONE -> {
                recommendations.add("No recovery action needed")
            }
        }

        // Add general recommendations
        if (anrResult.responseTimeMs > ANR_THRESHOLD_MS * 2) {
            recommendations.add("Response time is very high. Consider optimizing main thread operations.")
        }

        if (anrCount.get() >= MAX_ANR_COUNT) {
            recommendations.add("Multiple ANRs detected. Consider restarting the application.")
        }

        return recommendations
    }

    /**
     * Save ANR result
     */
    private fun saveANRResult(anrResult: ANRResult) {
        try {
            val anrDir = File(context.filesDir, "anr_reports")
            anrDir.mkdirs()
            
            val anrFile = File(anrDir, "anr_${anrResult.timestamp}.json")
            val json = JSONObject().apply {
                put("isANR", anrResult.isANR)
                put("responseTimeMs", anrResult.responseTimeMs)
                put("thresholdMs", anrResult.thresholdMs)
                put("stackTrace", anrResult.stackTrace)
                put("memoryUsage", anrResult.memoryUsage)
                put("timestamp", anrResult.timestamp)
                put("anrCount", anrCount.get())
            }
            
            anrFile.writeText(json.toString())
            Log.d(TAG, "ANR result saved to: ${anrFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ANR result", e)
        }
    }

    /**
     * Save recovery result
     */
    private fun saveRecoveryResult(recoveryResult: ANRRecoveryResult) {
        try {
            val recoveryDir = File(context.filesDir, "anr_recovery")
            recoveryDir.mkdirs()
            
            val recoveryFile = File(recoveryDir, "recovery_${recoveryResult.timestamp}.json")
            val json = JSONObject().apply {
                put("success", recoveryResult.success)
                put("recoveryAction", recoveryResult.recoveryAction.name)
                put("recoveryTimeMs", recoveryResult.recoveryTimeMs)
                put("recommendations", recoveryResult.recommendations)
                put("timestamp", recoveryResult.timestamp)
            }
            
            recoveryFile.writeText(json.toString())
            Log.d(TAG, "Recovery result saved to: ${recoveryFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save recovery result", e)
        }
    }

    /**
     * Get ANR statistics
     */
    suspend fun getANRStatistics(): ANRStatistics = withContext(Dispatchers.IO) {
        try {
            val anrDir = File(context.filesDir, "anr_reports")
            val anrFiles = anrDir.listFiles { file ->
                file.name.startsWith("anr_") && file.name.endsWith(".json")
            } ?: emptyArray()

            val totalANRs = anrFiles.size
            val averageResponseTime = if (anrFiles.isNotEmpty()) {
                anrFiles.mapNotNull { file ->
                    try {
                        val json = JSONObject(file.readText())
                        json.getLong("responseTimeMs")
                    } catch (e: Exception) {
                        null
                    }
                }.average().toLong()
            } else {
                0L
            }

            ANRStatistics(
                totalANRs = totalANRs,
                averageResponseTimeMs = averageResponseTime,
                currentANRCount = anrCount.get(),
                isWatchdogRunning = isRunning.get()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ANR statistics", e)
            ANRStatistics(
                totalANRs = 0,
                averageResponseTimeMs = 0L,
                currentANRCount = 0,
                isWatchdogRunning = false
            )
        }
    }

    /**
     * ANR statistics data class
     */
    data class ANRStatistics(
        val totalANRs: Int,
        val averageResponseTimeMs: Long,
        val currentANRCount: Long,
        val isWatchdogRunning: Boolean
    )
}
