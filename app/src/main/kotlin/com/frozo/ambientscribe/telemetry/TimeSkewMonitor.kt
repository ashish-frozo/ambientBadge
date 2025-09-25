package com.frozo.ambientscribe.telemetry

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Time Skew Monitor for PT-8 implementation
 * Monitors device-server time difference and warns if >120s (ST-8.15, ST-8.16, ST-8.17)
 */
class TimeSkewMonitor private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var INSTANCE: TimeSkewMonitor? = null
        
        fun getInstance(context: Context): TimeSkewMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimeSkewMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val PREFS_NAME = "time_skew_prefs"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_DEVICE_OFFSET = "device_offset"
        private const val KEY_TIME_SOURCE = "time_source"
        private const val KEY_SKEW_WARNINGS = "skew_warnings"
        
        // Time sources
        private const val TIME_SOURCE_SNTP = "SNTP"
        private const val TIME_SOURCE_HTTPS = "HTTPS"
        private const val TIME_SOURCE_LOCAL = "LOCAL"
        
        // Thresholds
        private const val SKEW_WARNING_THRESHOLD_SECONDS = 120L
        private const val SKEW_CRITICAL_THRESHOLD_SECONDS = 300L
        private const val SYNC_INTERVAL_MINUTES = 30L
        
        // SNTP servers (fallback order)
        private val SNTP_SERVERS = listOf(
            "time.google.com",
            "time.cloudflare.com",
            "pool.ntp.org"
        )
        
        // HTTPS time sources (fallback order)
        private val HTTPS_TIME_SOURCES = listOf(
            "https://worldtimeapi.org/api/timezone/UTC",
            "https://timeapi.io/api/Time/current/zone?timeZone=UTC",
            "https://api.timezonedb.com/v2.1/get-time-zone"
        )
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val telemetryManager = TelemetryManager.getInstance(context)
    
    // Current time source and offset
    private val currentTimeSource = AtomicReference<String>(TIME_SOURCE_LOCAL)
    private val deviceOffsetSeconds = AtomicLong(0)
    private val lastSyncTime = AtomicReference<Instant>(Instant.now())
    
    // Monitoring state
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isMonitoring = AtomicReference(false)
    
    init {
        // Load saved time source and offset
        currentTimeSource.set(prefs.getString(KEY_TIME_SOURCE, TIME_SOURCE_LOCAL) ?: TIME_SOURCE_LOCAL)
        deviceOffsetSeconds.set(prefs.getLong(KEY_DEVICE_OFFSET, 0))
        
        val lastSyncString = prefs.getString(KEY_LAST_SYNC_TIME, null)
        if (lastSyncString != null) {
            try {
                lastSyncTime.set(Instant.parse(lastSyncString))
            } catch (e: Exception) {
                lastSyncTime.set(Instant.now())
            }
        }
        
        // Start monitoring
        startMonitoring()
    }
    
    /**
     * Start time skew monitoring
     */
    fun startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            scope.launch {
                while (isActive) {
                    try {
                        // Sync time if needed
                        if (shouldSyncTime()) {
                            syncTime()
                        }
                        
                        // Check for time skew
                        checkTimeSkew()
                        
                        // Wait before next check
                        delay(SYNC_INTERVAL_MINUTES * 60 * 1000)
                        
                    } catch (e: Exception) {
                        android.util.Log.e("TimeSkewMonitor", "Error in monitoring loop", e)
                        delay(60000) // Wait 1 minute on error
                    }
                }
            }
        }
    }
    
    /**
     * Stop time skew monitoring
     */
    fun stopMonitoring() {
        isMonitoring.set(false)
        scope.cancel()
    }
    
    /**
     * Get current network time with offset applied
     */
    fun getNetworkTime(): Instant {
        return Instant.now().plusSeconds(deviceOffsetSeconds.get())
    }
    
    /**
     * Get current time source
     */
    fun getCurrentTimeSource(): String {
        return currentTimeSource.get()
    }
    
    /**
     * Get device time offset in seconds
     */
    fun getDeviceOffset(): Long {
        return deviceOffsetSeconds.get()
    }
    
    /**
     * Check if time sync is needed
     */
    private fun shouldSyncTime(): Boolean {
        val lastSync = lastSyncTime.get()
        val now = Instant.now()
        val timeSinceLastSync = ChronoUnit.MINUTES.between(lastSync, now)
        
        return timeSinceLastSync >= SYNC_INTERVAL_MINUTES
    }
    
    /**
     * Sync time using available sources
     */
    private suspend fun syncTime() {
        android.util.Log.d("TimeSkewMonitor", "Starting time sync...")
        
        // Try SNTP first
        if (syncWithSNTP()) {
            android.util.Log.d("TimeSkewMonitor", "Time synced with SNTP")
            return
        }
        
        // Fall back to HTTPS
        if (syncWithHTTPS()) {
            android.util.Log.d("TimeSkewMonitor", "Time synced with HTTPS")
            return
        }
        
        // Fall back to local time
        android.util.Log.w("TimeSkewMonitor", "All time sources failed, using local time")
        currentTimeSource.set(TIME_SOURCE_LOCAL)
        deviceOffsetSeconds.set(0)
        lastSyncTime.set(Instant.now())
        
        saveTimeState()
    }
    
    /**
     * Sync time using SNTP
     */
    private suspend fun syncWithSNTP(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                for (server in SNTP_SERVERS) {
                    try {
                        val ntpTime = getSNTPTime(server)
                        if (ntpTime != null) {
                            val deviceTime = Instant.now()
                            val offset = ChronoUnit.SECONDS.between(deviceTime, ntpTime)
                            
                            currentTimeSource.set(TIME_SOURCE_SNTP)
                            deviceOffsetSeconds.set(offset)
                            lastSyncTime.set(Instant.now())
                            
                            saveTimeState()
                            
                            android.util.Log.d("TimeSkewMonitor", "SNTP sync successful with $server, offset: ${offset}s")
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TimeSkewMonitor", "SNTP sync failed with $server", e)
                    }
                }
                false
            } catch (e: Exception) {
                android.util.Log.e("TimeSkewMonitor", "SNTP sync error", e)
                false
            }
        }
    }
    
    /**
     * Get SNTP time from server
     */
    private suspend fun getSNTPTime(server: String): Instant? {
        return withContext(Dispatchers.IO) {
            try {
                // Simplified SNTP implementation
                // In a real implementation, this would use proper SNTP protocol
                val url = URL("http://$server")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val dateHeader = connection.getHeaderField("Date")
                if (dateHeader != null) {
                    val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
                    val instant = java.time.ZonedDateTime.parse(dateHeader, formatter).toInstant()
                    connection.disconnect()
                    instant
                } else {
                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Sync time using HTTPS time APIs
     */
    private suspend fun syncWithHTTPS(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                for (timeSource in HTTPS_TIME_SOURCES) {
                    try {
                        val networkTime = getHTTPSTime(timeSource)
                        if (networkTime != null) {
                            val deviceTime = Instant.now()
                            val offset = ChronoUnit.SECONDS.between(deviceTime, networkTime)
                            
                            // Validate offset is reasonable (within 1 hour)
                            if (kotlin.math.abs(offset) <= 3600) {
                                currentTimeSource.set(TIME_SOURCE_HTTPS)
                                deviceOffsetSeconds.set(offset)
                                lastSyncTime.set(Instant.now())
                                
                                saveTimeState()
                                
                                android.util.Log.d("TimeSkewMonitor", "HTTPS sync successful with $timeSource, offset: ${offset}s")
                                return@withContext true
                            } else {
                                android.util.Log.w("TimeSkewMonitor", "HTTPS time offset too large: ${offset}s")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TimeSkewMonitor", "HTTPS sync failed with $timeSource", e)
                    }
                }
                false
            } catch (e: Exception) {
                android.util.Log.e("TimeSkewMonitor", "HTTPS sync error", e)
                false
            }
        }
    }
    
    /**
     * Get time from HTTPS API
     */
    private suspend fun getHTTPSTime(timeSource: String): Instant? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(timeSource)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "AmbientScribe/1.0")
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    connection.disconnect()
                    
                    // Parse different time API formats
                    parseTimeAPIResponse(response)
                } else {
                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Parse time API response
     */
    private fun parseTimeAPIResponse(response: String): Instant? {
        return try {
            // Try to parse as ISO 8601 timestamp
            Instant.parse(response.trim('"'))
        } catch (e: Exception) {
            try {
                // Try to parse as Unix timestamp
                val timestamp = response.trim('"').toLong()
                Instant.ofEpochSecond(timestamp)
            } catch (e: Exception) {
                try {
                    // Try to parse as milliseconds timestamp
                    val timestamp = response.trim('"').toLong()
                    Instant.ofEpochMilli(timestamp)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * Check for time skew and log events
     */
    private fun checkTimeSkew() {
        val deviceTime = Instant.now()
        val networkTime = getNetworkTime()
        val skewSeconds = ChronoUnit.SECONDS.between(deviceTime, networkTime)
        val absSkew = kotlin.math.abs(skewSeconds)
        
        android.util.Log.d("TimeSkewMonitor", "Time skew check: device=$deviceTime, network=$networkTime, skew=${skewSeconds}s")
        
        if (absSkew > SKEW_WARNING_THRESHOLD_SECONDS) {
            val skewEvent = TimeSkewEvent(
                encounterId = "system",
                timestamp = deviceTime.toString(),
                deviceTier = "unknown",
                clinicId = null,
                deviceTime = deviceTime.toString(),
                serverTime = networkTime.toString(),
                skewSeconds = skewSeconds,
                timeSource = currentTimeSource.get()
            )
            
            telemetryManager.logEvent(skewEvent)
            
            // Log warning
            val warningCount = prefs.getInt(KEY_SKEW_WARNINGS, 0) + 1
            prefs.edit().putInt(KEY_SKEW_WARNINGS, warningCount).apply()
            
            android.util.Log.w("TimeSkewMonitor", "Time skew warning: ${absSkew}s (threshold: ${SKEW_WARNING_THRESHOLD_SECONDS}s)")
            
            if (absSkew > SKEW_CRITICAL_THRESHOLD_SECONDS) {
                android.util.Log.e("TimeSkewMonitor", "Critical time skew: ${absSkew}s")
            }
        }
    }
    
    /**
     * Save time state to preferences
     */
    private fun saveTimeState() {
        prefs.edit()
            .putString(KEY_TIME_SOURCE, currentTimeSource.get())
            .putLong(KEY_DEVICE_OFFSET, deviceOffsetSeconds.get())
            .putString(KEY_LAST_SYNC_TIME, lastSyncTime.get().toString())
            .apply()
    }
    
    /**
     * Get time skew statistics
     */
    fun getTimeSkewStats(): TimeSkewStats {
        return TimeSkewStats(
            currentTimeSource = currentTimeSource.get(),
            deviceOffsetSeconds = deviceOffsetSeconds.get(),
            lastSyncTime = lastSyncTime.get().toString(),
            skewWarnings = prefs.getInt(KEY_SKEW_WARNINGS, 0),
            isMonitoring = isMonitoring.get()
        )
    }
    
    /**
     * Force time sync
     */
    suspend fun forceSync() {
        android.util.Log.d("TimeSkewMonitor", "Forcing time sync...")
        syncTime()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopMonitoring()
    }
}

/**
 * Data classes for time skew monitoring
 */

data class TimeSkewStats(
    val currentTimeSource: String,
    val deviceOffsetSeconds: Long,
    val lastSyncTime: String,
    val skewWarnings: Int,
    val isMonitoring: Boolean
)
