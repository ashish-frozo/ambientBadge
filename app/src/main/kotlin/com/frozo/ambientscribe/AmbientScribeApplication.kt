package com.frozo.ambientscribe

import android.app.Application
import com.frozo.ambientscribe.services.OEMKillerWatchdog
import com.frozo.ambientscribe.telemetry.MetricsCollector
import timber.log.Timber

/**
 * Application class for Ambient Scribe.
 * Handles initialization of global components.
 */
class AmbientScribeApplication : Application() {

    lateinit var oemKillerWatchdog: OEMKillerWatchdog
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Initialize metrics collector
        val metricsCollector = MetricsCollector(this)
        
        // Initialize OEM killer watchdog
        oemKillerWatchdog = OEMKillerWatchdog(this, metricsCollector)
        
        // Check if we need to auto-restart after abnormal termination
        if (oemKillerWatchdog.wasAbnormallyTerminated()) {
            Timber.w("Detected previous abnormal termination, auto-restarting")
            oemKillerWatchdog.autoRestart()
        }
        
        Timber.i("AmbientScribeApplication initialized")
    }
}