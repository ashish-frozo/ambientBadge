package com.frozo.ambientscribe.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber

/**
 * Manages Doze mode exclusion and battery optimization settings
 */
class DozeExclusionManager(private val context: Context) {

    /**
     * Check if the app is ignoring battery optimizations
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Request to be excluded from battery optimizations
     * Returns an intent that should be started with startActivityForResult
     */
    fun createBatteryOptimizationExclusionIntent(): Intent {
        return Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Start the DozeExclusionService
     */
    fun startDozeExclusionService() {
        if (DozeExclusionService.isServiceRunning()) {
            Timber.d("DozeExclusionService already running")
            return
        }
        
        val serviceIntent = Intent(context, DozeExclusionService::class.java).apply {
            action = DozeExclusionService.ACTION_START_SERVICE
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        Timber.i("Started DozeExclusionService")
    }

    /**
     * Stop the DozeExclusionService
     */
    fun stopDozeExclusionService() {
        if (!DozeExclusionService.isServiceRunning()) {
            Timber.d("DozeExclusionService not running")
            return
        }
        
        val serviceIntent = Intent(context, DozeExclusionService::class.java).apply {
            action = DozeExclusionService.ACTION_STOP_SERVICE
        }
        
        context.startService(serviceIntent)
        
        Timber.i("Stopped DozeExclusionService")
    }
}
