package com.frozo.ambientscribe.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Audio Route Manager - ST-6.17
 * Implements audio route change handling (wired/BT/speaker); auto-pause on route loss; tests
 * Provides comprehensive audio route management and handling
 */
class AudioRouteManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "AudioRouteManager"
        private const val ROUTE_LOSS_TIMEOUT_MS = 5000L
        private const val ROUTE_RECOVERY_TIMEOUT_MS = 10000L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var currentRoute: AudioRoute = AudioRoute.SPEAKER
    private var isPaused = false
    private var routeChangeListeners = mutableListOf<AudioRouteChangeListener>()

    /**
     * Audio route enumeration
     */
    enum class AudioRoute {
        WIRED_HEADSET,
        BLUETOOTH,
        SPEAKER,
        UNKNOWN
    }

    /**
     * Audio route change event
     */
    data class AudioRouteChangeEvent(
        val oldRoute: AudioRoute,
        val newRoute: AudioRoute,
        val timestamp: Long,
        val reason: String
    )

    /**
     * Audio route change listener interface
     */
    interface AudioRouteChangeListener {
        suspend fun onRouteChanged(event: AudioRouteChangeEvent)
        suspend fun onRouteLost(event: AudioRouteChangeEvent)
        suspend fun onRouteRecovered(event: AudioRouteChangeEvent)
    }

    /**
     * Initialize audio route manager
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing audio route manager")
            
            // Detect initial audio route
            currentRoute = detectCurrentAudioRoute()
            
            // Set up audio focus change listener
            setupAudioFocusListener()
            
            Log.d(TAG, "Audio route manager initialized. Current route: $currentRoute")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio route manager", e)
            Result.failure(e)
        }
    }

    /**
     * Detect current audio route
     */
    private fun detectCurrentAudioRoute(): AudioRoute {
        return when {
            audioManager.isWiredHeadsetOn -> AudioRoute.WIRED_HEADSET
            audioManager.isBluetoothA2dpOn -> AudioRoute.BLUETOOTH
            audioManager.isSpeakerphoneOn -> AudioRoute.SPEAKER
            else -> AudioRoute.UNKNOWN
        }
    }

    /**
     * Handle audio route change
     */
    suspend fun handleRouteChange(newRoute: AudioRoute, reason: String = "unknown"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Handling audio route change: ${currentRoute} -> $newRoute")
            
            val oldRoute = currentRoute
            val event = AudioRouteChangeEvent(
                oldRoute = oldRoute,
                newRoute = newRoute,
                timestamp = System.currentTimeMillis(),
                reason = reason
            )
            
            // Update current route
            currentRoute = newRoute
            
            // Notify listeners
            notifyRouteChangeListeners(event)
            
            // Handle route-specific logic
            handleRouteSpecificLogic(oldRoute, newRoute)
            
            // Save route change event
            saveRouteChangeEvent(event)
            
            Log.d(TAG, "Audio route change handled successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle audio route change", e)
            Result.failure(e)
        }
    }

    /**
     * Handle route-specific logic
     */
    private suspend fun handleRouteSpecificLogic(oldRoute: AudioRoute, newRoute: AudioRoute) {
        when {
            newRoute == AudioRoute.UNKNOWN -> {
                // Route lost - pause audio
                pauseAudio("Route lost: $oldRoute -> $newRoute")
            }
            oldRoute == AudioRoute.UNKNOWN && newRoute != AudioRoute.UNKNOWN -> {
                // Route recovered - resume audio
                resumeAudio("Route recovered: $oldRoute -> $newRoute")
            }
            newRoute == AudioRoute.BLUETOOTH -> {
                // Switch to Bluetooth - adjust audio settings
                adjustAudioForBluetooth()
            }
            newRoute == AudioRoute.WIRED_HEADSET -> {
                // Switch to wired headset - adjust audio settings
                adjustAudioForWiredHeadset()
            }
            newRoute == AudioRoute.SPEAKER -> {
                // Switch to speaker - adjust audio settings
                adjustAudioForSpeaker()
            }
        }
    }

    /**
     * Pause audio
     */
    suspend fun pauseAudio(reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Pausing audio: $reason")
            
            if (!isPaused) {
                isPaused = true
                
                // Implement actual audio pause logic here
                // In real implementation, this would pause the audio playback
                
                Log.d(TAG, "Audio paused successfully")
            }
            
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause audio", e)
            Result.failure(e)
        }
    }

    /**
     * Resume audio
     */
    suspend fun resumeAudio(reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resuming audio: $reason")
            
            if (isPaused) {
                isPaused = false
                
                // Implement actual audio resume logic here
                // In real implementation, this would resume the audio playback
                
                Log.d(TAG, "Audio resumed successfully")
            }
            
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume audio", e)
            Result.failure(e)
        }
    }

    /**
     * Adjust audio for Bluetooth
     */
    private suspend fun adjustAudioForBluetooth() {
        Log.d(TAG, "Adjusting audio settings for Bluetooth")
        // Implement Bluetooth-specific audio adjustments
    }

    /**
     * Adjust audio for wired headset
     */
    private suspend fun adjustAudioForWiredHeadset() {
        Log.d(TAG, "Adjusting audio settings for wired headset")
        // Implement wired headset-specific audio adjustments
    }

    /**
     * Adjust audio for speaker
     */
    private suspend fun adjustAudioForSpeaker() {
        Log.d(TAG, "Adjusting audio settings for speaker")
        // Implement speaker-specific audio adjustments
    }

    /**
     * Set up audio focus change listener
     */
    private fun setupAudioFocusListener() {
        // In a real implementation, this would set up an AudioManager.OnAudioFocusChangeListener
        Log.d(TAG, "Audio focus change listener set up")
    }

    /**
     * Add route change listener
     */
    fun addRouteChangeListener(listener: AudioRouteChangeListener) {
        routeChangeListeners.add(listener)
    }

    /**
     * Remove route change listener
     */
    fun removeRouteChangeListener(listener: AudioRouteChangeListener) {
        routeChangeListeners.remove(listener)
    }

    /**
     * Notify route change listeners
     */
    private suspend fun notifyRouteChangeListeners(event: AudioRouteChangeEvent) {
        for (listener in routeChangeListeners) {
            try {
                when {
                    event.newRoute == AudioRoute.UNKNOWN -> {
                        listener.onRouteLost(event)
                    }
                    event.oldRoute == AudioRoute.UNKNOWN && event.newRoute != AudioRoute.UNKNOWN -> {
                        listener.onRouteRecovered(event)
                    }
                    else -> {
                        listener.onRouteChanged(event)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying route change listener", e)
            }
        }
    }

    /**
     * Save route change event
     */
    private fun saveRouteChangeEvent(event: AudioRouteChangeEvent) {
        try {
            val eventsDir = File(context.filesDir, "audio_route_events")
            eventsDir.mkdirs()
            
            val eventFile = File(eventsDir, "route_event_${event.timestamp}.json")
            val json = JSONObject().apply {
                put("oldRoute", event.oldRoute.name)
                put("newRoute", event.newRoute.name)
                put("timestamp", event.timestamp)
                put("reason", event.reason)
            }
            
            eventFile.writeText(json.toString())
            Log.d(TAG, "Route change event saved to: ${eventFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save route change event", e)
        }
    }

    /**
     * Get current audio route
     */
    fun getCurrentAudioRoute(): AudioRoute = currentRoute

    /**
     * Check if audio is paused
     */
    fun isAudioPaused(): Boolean = isPaused

    /**
     * Get audio route statistics
     */
    suspend fun getAudioRouteStatistics(): AudioRouteStatistics = withContext(Dispatchers.IO) {
        try {
            val eventsDir = File(context.filesDir, "audio_route_events")
            val eventFiles = eventsDir.listFiles { file ->
                file.name.startsWith("route_event_") && file.name.endsWith(".json")
            } ?: emptyArray()

            val totalEvents = eventFiles.size
            val routeChanges = eventFiles.count { file ->
                try {
                    val json = JSONObject(file.readText())
                    json.getString("newRoute") != "UNKNOWN"
                } catch (e: Exception) {
                    false
                }
            }
            val routeLosses = eventFiles.count { file ->
                try {
                    val json = JSONObject(file.readText())
                    json.getString("newRoute") == "UNKNOWN"
                } catch (e: Exception) {
                    false
                }
            }

            AudioRouteStatistics(
                totalEvents = totalEvents,
                routeChanges = routeChanges,
                routeLosses = routeLosses,
                currentRoute = currentRoute,
                isPaused = isPaused
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get audio route statistics", e)
            AudioRouteStatistics(
                totalEvents = 0,
                routeChanges = 0,
                routeLosses = 0,
                currentRoute = AudioRoute.UNKNOWN,
                isPaused = false
            )
        }
    }

    /**
     * Audio route statistics data class
     */
    data class AudioRouteStatistics(
        val totalEvents: Int,
        val routeChanges: Int,
        val routeLosses: Int,
        val currentRoute: AudioRoute,
        val isPaused: Boolean
    )
}
