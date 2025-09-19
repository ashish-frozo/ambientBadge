package com.frozo.ambientscribe.services

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.frozo.ambientscribe.MainActivity
import com.frozo.ambientscribe.telemetry.MetricsCollector
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watchdog to detect and handle OEM app killers (like MIUI, EMUI, ColorOS, etc.)
 * Detects foreground termination, logs the cause, and provides auto-restart capability.
 */
class OEMKillerWatchdog(
    private val application: Application,
    private val metricsCollector: MetricsCollector? = null
) : DefaultLifecycleObserver {

    companion object {
        private const val PREFS_NAME = "oem_killer_watchdog_prefs"
        private const val KEY_LAST_HEARTBEAT = "last_heartbeat"
        private const val KEY_WAS_RUNNING = "was_running"
        private const val KEY_ABNORMAL_TERMINATION = "abnormal_termination"
        private const val KEY_TERMINATION_TIME = "termination_time"
        private const val KEY_OEM_TYPE = "oem_type"
        
        // Heartbeat interval
        private const val HEARTBEAT_INTERVAL_MS = 60_000L // 1 minute
        
        // Known OEM types
        private val OEM_TYPES = mapOf(
            "xiaomi" to "MIUI",
            "huawei" to "EMUI",
            "oppo" to "ColorOS",
            "vivo" to "FuntouchOS",
            "samsung" to "OneUI",
            "oneplus" to "OxygenOS",
            "realme" to "RealmeUI"
        )
    }

    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isRunning = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            recordHeartbeat()
            if (isRunning.get()) {
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    init {
        // Register with ProcessLifecycleOwner to detect app foreground/background state
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // Check for abnormal termination on startup
        checkForAbnormalTermination()
    }

    /**
     * Start the watchdog
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            return
        }
        
        Timber.i("OEM Killer Watchdog started")
        
        // Record that the app is running
        prefs.edit().putBoolean(KEY_WAS_RUNNING, true).apply()
        
        // Start heartbeat
        recordHeartbeat()
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    /**
     * Stop the watchdog
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            return
        }
        
        Timber.i("OEM Killer Watchdog stopped")
        
        // Record that the app was gracefully stopped
        prefs.edit().putBoolean(KEY_WAS_RUNNING, false).apply()
        
        // Stop heartbeat
        handler.removeCallbacks(heartbeatRunnable)
    }

    /**
     * Record a heartbeat to indicate the app is still alive
     */
    private fun recordHeartbeat() {
        prefs.edit().putLong(KEY_LAST_HEARTBEAT, SystemClock.elapsedRealtime()).apply()
    }

    /**
     * Check if the app was abnormally terminated
     */
    private fun checkForAbnormalTermination() {
        val wasRunning = prefs.getBoolean(KEY_WAS_RUNNING, false)
        if (wasRunning) {
            // App was running but didn't gracefully stop
            val lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
            val timeSinceHeartbeat = SystemClock.elapsedRealtime() - lastHeartbeat
            
            if (timeSinceHeartbeat < HEARTBEAT_INTERVAL_MS * 3) {
                // Recent abnormal termination detected
                val oemType = detectOEMType()
                
                Timber.w("Detected abnormal termination by OEM killer (likely $oemType)")
                
                // Record the termination
                prefs.edit()
                    .putBoolean(KEY_ABNORMAL_TERMINATION, true)
                    .putLong(KEY_TERMINATION_TIME, System.currentTimeMillis())
                    .putString(KEY_OEM_TYPE, oemType)
                    .apply()
                
                // Log the event
                metricsCollector?.logMetricEvent("oem_killer_detected", mapOf(
                    "oem_type" to oemType,
                    "time_since_heartbeat_ms" to timeSinceHeartbeat,
                    "device_manufacturer" to Build.MANUFACTURER.lowercase(),
                    "device_model" to Build.MODEL
                ))
            }
        }
        
        // Reset the running state
        prefs.edit().putBoolean(KEY_WAS_RUNNING, false).apply()
    }

    /**
     * Detect the OEM type based on device manufacturer
     */
    private fun detectOEMType(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return OEM_TYPES.entries.firstOrNull { (key, _) -> 
            manufacturer.contains(key) 
        }?.value ?: "Unknown"
    }

    /**
     * Check if an abnormal termination was detected
     */
    fun wasAbnormallyTerminated(): Boolean {
        return prefs.getBoolean(KEY_ABNORMAL_TERMINATION, false)
    }

    /**
     * Get details about the abnormal termination
     */
    fun getAbnormalTerminationDetails(): Map<String, Any> {
        return if (wasAbnormallyTerminated()) {
            mapOf(
                "termination_time" to prefs.getLong(KEY_TERMINATION_TIME, 0L),
                "oem_type" to (prefs.getString(KEY_OEM_TYPE, "Unknown") ?: "Unknown")
            )
        } else {
            emptyMap()
        }
    }

    /**
     * Clear the abnormal termination flag
     */
    fun clearAbnormalTermination() {
        prefs.edit().putBoolean(KEY_ABNORMAL_TERMINATION, false).apply()
    }

    /**
     * Auto-restart the app after abnormal termination
     */
    fun autoRestart() {
        if (wasAbnormallyTerminated()) {
            Timber.i("Auto-restarting app after abnormal termination")
            
            // Clear the flag
            clearAbnormalTermination()
            
            // Log the restart
            metricsCollector?.logMetricEvent("oem_killer_auto_restart", mapOf(
                "oem_type" to (prefs.getString(KEY_OEM_TYPE, "Unknown") ?: "Unknown"),
                "termination_time" to prefs.getLong(KEY_TERMINATION_TIME, 0L)
            ))
            
            // Launch the main activity
            val intent = Intent(application, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            application.startActivity(intent)
        }
    }

    /**
     * Get OEM-specific battery optimization guidance
     */
    fun getOEMOptimizationGuidance(): String {
        val oemType = detectOEMType()
        
        return when (oemType) {
            "MIUI" -> "To prevent app termination, go to Settings > Battery & Performance > App Battery Saver > Ambient Scribe > No restrictions"
            "EMUI" -> "To prevent app termination, go to Settings > Battery > App Launch > Ambient Scribe > Manage Manually > Allow"
            "ColorOS" -> "To prevent app termination, go to Settings > Battery > Power Optimization > Ambient Scribe > Don't Optimize"
            "FuntouchOS" -> "To prevent app termination, go to Settings > Battery > Background Power Management > Ambient Scribe > Allow Background Running"
            "OneUI" -> "To prevent app termination, go to Settings > Device Care > Battery > App Power Management > Ambient Scribe > Don't Optimize"
            "OxygenOS" -> "To prevent app termination, go to Settings > Battery > Battery Optimization > Ambient Scribe > Don't Optimize"
            "RealmeUI" -> "To prevent app termination, go to Settings > Battery > App Battery Management > Ambient Scribe > Don't Restrict"
            else -> "To prevent app termination, please add Ambient Scribe to your device's battery optimization exceptions."
        }
    }

    // Lifecycle events
    override fun onStart(owner: LifecycleOwner) {
        // App is in foreground
        start()
    }

    override fun onStop(owner: LifecycleOwner) {
        // App is in background
        stop()
    }
}
