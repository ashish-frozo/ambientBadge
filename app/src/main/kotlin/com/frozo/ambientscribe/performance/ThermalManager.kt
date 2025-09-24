package com.frozo.ambientscribe.performance

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Thermal management for preventing overheating during intensive AI processing
 */
class ThermalManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ThermalManager"
    }
    
    enum class ThermalState {
        NORMAL,     // Normal operating temperature
        WARM,       // Elevated temperature, reduce performance
        HOT,        // High temperature, significant throttling
        CRITICAL    // Critical temperature, stop processing
    }
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    fun getCurrentThermalState(): ThermalState {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                when (powerManager.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE,
                    PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.NORMAL
                    PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.WARM
                    PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.HOT
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
                    else -> ThermalState.NORMAL
                }
            } else {
                // For older Android versions, assume normal state
                ThermalState.NORMAL
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get thermal state", e)
            ThermalState.NORMAL
        }
    }

    /**
     * Get thermal throttling level (0-3)
     */
    fun getThermalThrottling(): Int {
        return when (getCurrentThermalState()) {
            ThermalState.NORMAL -> 0
            ThermalState.WARM -> 1
            ThermalState.HOT -> 2
            ThermalState.CRITICAL -> 3
        }
    }

    /**
     * Start thermal monitoring
     */
    fun startMonitoring() {
        // No active monitoring needed for now, we check on demand
        Log.d(TAG, "Thermal monitoring started")
    }

    /**
     * Stop thermal monitoring
     */
    fun stopMonitoring() {
        // No active monitoring to stop
        Log.d(TAG, "Thermal monitoring stopped")
    }
}