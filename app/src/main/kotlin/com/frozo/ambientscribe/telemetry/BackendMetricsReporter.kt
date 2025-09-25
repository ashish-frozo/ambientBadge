package com.frozo.ambientscribe.telemetry

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Backend Metrics Reporter for PT-8 implementation
 * Handles optional backend metrics reporting to Railway when available (ST-8.4)
 */
class BackendMetricsReporter private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var INSTANCE: BackendMetricsReporter? = null
        
        fun getInstance(context: Context): BackendMetricsReporter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackendMetricsReporter(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val PREFS_NAME = "backend_metrics_prefs"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        
        // Sync settings
        private const val SYNC_INTERVAL_MS = 300000L // 5 minutes
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 30000L // 30 seconds
        private const val MAX_QUEUE_SIZE = 1000
        private const val BATCH_SIZE = 50
    }
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val eventQueue = ConcurrentLinkedQueue<TelemetryEvent>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Backend configuration
    private val backendUrl = AtomicReference<String?>(null)
    private val apiKey = AtomicReference<String?>(null)
    private val isSyncEnabled = AtomicBoolean(false)
    private val isOnline = AtomicBoolean(false)
    
    // Sync state
    private val lastSyncTime = AtomicLong(0)
    private val syncAttempts = AtomicLong(0)
    private val successfulSyncs = AtomicLong(0)
    private val failedSyncs = AtomicLong(0)
    
    // Offline storage
    private val offlineDir = File(context.filesDir, "offline_metrics")
    private val offlineEventsFile = File(offlineDir, "events.jsonl")
    private val syncJournalFile = File(offlineDir, "sync_journal.jsonl")
    
    init {
        // Load configuration
        loadConfiguration()
        
        // Ensure offline directory exists
        if (!offlineDir.exists()) {
            offlineDir.mkdirs()
        }
        
        // Start background sync
        startBackgroundSync()
    }
    
    /**
     * Configure backend connection
     */
    fun configureBackend(url: String, apiKey: String) {
        this.backendUrl.set(url)
        this.apiKey.set(apiKey)
        this.isSyncEnabled.set(true)
        
        prefs.edit()
            .putString(KEY_BACKEND_URL, url)
            .putString(KEY_API_KEY, apiKey)
            .putBoolean(KEY_SYNC_ENABLED, true)
            .apply()
        
        Log.d("BackendMetricsReporter", "Backend configured: $url")
    }
    
    /**
     * Disable backend sync
     */
    fun disableSync() {
        isSyncEnabled.set(false)
        prefs.edit()
            .putBoolean(KEY_SYNC_ENABLED, false)
            .apply()
        
        Log.d("BackendMetricsReporter", "Backend sync disabled")
    }
    
    /**
     * Queue event for backend reporting
     */
    fun queueEvent(event: TelemetryEvent) {
        if (!isSyncEnabled.get()) return
        
        // Add to queue
        eventQueue.offer(event)
        
        // Persist to offline storage
        persistEventOffline(event)
        
        // Check queue size
        if (eventQueue.size > MAX_QUEUE_SIZE) {
            // Remove oldest events
            repeat(eventQueue.size - MAX_QUEUE_SIZE) {
                eventQueue.poll()
            }
        }
        
        Log.d("BackendMetricsReporter", "Queued event for backend: ${event.eventType}")
    }
    
    /**
     * Start background sync process
     */
    private fun startBackgroundSync() {
        scope.launch {
            while (isActive) {
                try {
                    if (isSyncEnabled.get() && isOnline.get()) {
                        syncEvents()
                    }
                    
                    delay(SYNC_INTERVAL_MS)
                    
                } catch (e: Exception) {
                    Log.e("BackendMetricsReporter", "Error in background sync", e)
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }
    
    /**
     * Sync events to backend
     */
    private suspend fun syncEvents() {
        if (!isSyncEnabled.get() || backendUrl.get() == null) return
        
        val eventsToSync = mutableListOf<TelemetryEvent>()
        
        // Collect events from queue
        repeat(BATCH_SIZE) {
            eventQueue.poll()?.let { eventsToSync.add(it) }
        }
        
        if (eventsToSync.isEmpty()) return
        
        Log.d("BackendMetricsReporter", "Syncing ${eventsToSync.size} events to backend")
        
        try {
            val success = sendEventsToBackend(eventsToSync)
            
            if (success) {
                successfulSyncs.incrementAndGet()
                lastSyncTime.set(System.currentTimeMillis())
                
                // Log successful sync
                logSyncEvent("sync_success", eventsToSync.size)
                
                Log.d("BackendMetricsReporter", "Successfully synced ${eventsToSync.size} events")
                
            } else {
                failedSyncs.incrementAndGet()
                
                // Re-queue events for retry
                eventsToSync.forEach { eventQueue.offer(it) }
                
                // Log failed sync
                logSyncEvent("sync_failed", eventsToSync.size)
                
                Log.w("BackendMetricsReporter", "Failed to sync ${eventsToSync.size} events, re-queued")
            }
            
        } catch (e: Exception) {
            failedSyncs.incrementAndGet()
            
            // Re-queue events for retry
            eventsToSync.forEach { eventQueue.offer(it) }
            
            Log.e("BackendMetricsReporter", "Error syncing events", e)
        }
    }
    
    /**
     * Send events to backend
     */
    private suspend fun sendEventsToBackend(events: List<TelemetryEvent>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${backendUrl.get()}/v1/telemetry")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${apiKey.get()}")
                connection.setRequestProperty("User-Agent", "AmbientScribe/1.0")
                connection.setRequestProperty("X-Client-Version", "1.0.0")
                connection.setRequestProperty("X-Device-Tier", "A") // This would be dynamic
                
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                
                // Create batch request
                val batchRequest = TelemetryBatchRequest(
                    events = events,
                    clientId = getClientId(),
                    timestamp = Instant.now().toString(),
                    version = "1.0"
                )
                
                val requestJson = moshi.adapter(TelemetryBatchRequest::class.java).toJson(batchRequest)
                
                connection.outputStream.use { outputStream ->
                    outputStream.write(requestJson.toByteArray())
                }
                
                val responseCode = connection.responseCode
                val response = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream.bufferedReader().readText()
                }
                
                connection.disconnect()
                
                if (responseCode in 200..299) {
                    Log.d("BackendMetricsReporter", "Backend response: $response")
                    true
                } else {
                    Log.w("BackendMetricsReporter", "Backend error $responseCode: $response")
                    false
                }
                
            } catch (e: Exception) {
                Log.e("BackendMetricsReporter", "Error sending events to backend", e)
                false
            }
        }
    }
    
    /**
     * Persist event to offline storage
     */
    private fun persistEventOffline(event: TelemetryEvent) {
        try {
            val eventJson = event.toJsonString()
            offlineEventsFile.appendText("$eventJson\n")
            
        } catch (e: Exception) {
            Log.e("BackendMetricsReporter", "Error persisting event offline", e)
        }
    }
    
    /**
     * Log sync event
     */
    private fun logSyncEvent(eventType: String, eventCount: Int) {
        try {
            val syncEvent = SyncEvent(
                timestamp = Instant.now().toString(),
                eventType = eventType,
                eventCount = eventCount,
                queueSize = eventQueue.size,
                successfulSyncs = successfulSyncs.get(),
                failedSyncs = failedSyncs.get()
            )
            
            val syncEventJson = moshi.adapter(SyncEvent::class.java).toJson(syncEvent)
            syncJournalFile.appendText("$syncEventJson\n")
            
        } catch (e: Exception) {
            Log.e("BackendMetricsReporter", "Error logging sync event", e)
        }
    }
    
    /**
     * Load configuration from preferences
     */
    private fun loadConfiguration() {
        backendUrl.set(prefs.getString(KEY_BACKEND_URL, null))
        apiKey.set(prefs.getString(KEY_API_KEY, null))
        isSyncEnabled.set(prefs.getBoolean(KEY_SYNC_ENABLED, false))
        lastSyncTime.set(prefs.getLong(KEY_LAST_SYNC_TIME, 0))
    }
    
    /**
     * Get client ID for backend requests
     */
    private fun getClientId(): String {
        // In a real implementation, this would be a unique device identifier
        return "client_${System.currentTimeMillis()}"
    }
    
    /**
     * Set online/offline status
     */
    fun setOnlineStatus(online: Boolean) {
        isOnline.set(online)
        Log.d("BackendMetricsReporter", "Online status: $online")
    }
    
    /**
     * Get sync statistics
     */
    fun getSyncStats(): SyncStats {
        return SyncStats(
            isEnabled = isSyncEnabled.get(),
            isOnline = isOnline.get(),
            backendUrl = backendUrl.get(),
            queueSize = eventQueue.size,
            lastSyncTime = lastSyncTime.get(),
            successfulSyncs = successfulSyncs.get(),
            failedSyncs = failedSyncs.get(),
            offlineEventsCount = getOfflineEventsCount()
        )
    }
    
    /**
     * Get offline events count
     */
    private fun getOfflineEventsCount(): Int {
        return try {
            if (offlineEventsFile.exists()) {
                offlineEventsFile.readLines().size
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Force sync now
     */
    suspend fun forceSync() {
        Log.d("BackendMetricsReporter", "Forcing sync...")
        syncEvents()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * Data classes for backend metrics reporting
 */

data class TelemetryBatchRequest(
    val events: List<TelemetryEvent>,
    val clientId: String,
    val timestamp: String,
    val version: String
)

data class SyncEvent(
    val timestamp: String,
    val eventType: String,
    val eventCount: Int,
    val queueSize: Int,
    val successfulSyncs: Long,
    val failedSyncs: Long
)

data class SyncStats(
    val isEnabled: Boolean,
    val isOnline: Boolean,
    val backendUrl: String?,
    val queueSize: Int,
    val lastSyncTime: Long,
    val successfulSyncs: Long,
    val failedSyncs: Long,
    val offlineEventsCount: Int
)
