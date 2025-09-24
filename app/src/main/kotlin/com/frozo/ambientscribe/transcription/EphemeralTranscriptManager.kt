package com.frozo.ambientscribe.transcription

import android.content.Context
import android.content.SharedPreferences
import com.frozo.ambientscribe.security.AuditLogger
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages ephemeral transcripts that exist only in RAM and are purged on app termination.
 * Includes crash recovery hooks to ensure data is properly purged even after abnormal termination.
 */
class EphemeralTranscriptManager(
    private val context: Context,
    private val metricsCollector: MetricsCollector? = null
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    companion object {
        private const val PREFS_NAME = "ephemeral_transcript_prefs"
        private const val KEY_HAS_PENDING_PURGE = "has_pending_purge"
        private const val KEY_PENDING_SESSION_ID = "pending_session_id"
        private const val KEY_CRASH_TIMESTAMP = "crash_timestamp"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auditLogger = AuditLogger(context)
    
    private val isEphemeralModeActive = AtomicBoolean(false)
    private var currentSessionId: String? = null
    
    // In-memory storage for transcripts
    private val transcriptBuffer = mutableListOf<String>()
    private val metadataBuffer = mutableMapOf<String, Any>()
    
    init {
        // Check for pending purges from previous crashes
        checkForPendingPurge()
    }

    /**
     * Start a new ephemeral transcript session
     */
    fun startEphemeralSession(): String {
        if (isEphemeralModeActive.getAndSet(true)) {
            Timber.w("Ephemeral session already active, restarting")
            clearBuffers()
        }
        
        // Generate new session ID
        currentSessionId = UUID.randomUUID().toString()
        
        // Mark as having pending purge in case of crash
        markPendingPurge(currentSessionId!!)
        
        Timber.i("Started ephemeral transcript session: $currentSessionId")
        
        return currentSessionId!!
    }

    /**
     * Add a transcript segment to the ephemeral buffer
     */
    fun addTranscriptSegment(text: String, speakerId: Int, timestamp: Long) {
        if (!isEphemeralModeActive.get()) {
            Timber.w("Attempted to add transcript segment without active session")
            return
        }
        
        // Add to in-memory buffer only
        transcriptBuffer.add(text)
        
        // Add metadata
        val segmentId = "${currentSessionId}_${transcriptBuffer.size}"
        metadataBuffer[segmentId] = mapOf(
            "speaker_id" to speakerId,
            "timestamp" to timestamp,
            "length" to text.length
        )
        
        Timber.d("Added transcript segment: ${text.take(20)}... (${text.length} chars)")
    }

    /**
     * Get the complete transcript from the current session
     */
    fun getCompleteTranscript(): String {
        return transcriptBuffer.joinToString(" ")
    }

    /**
     * Get transcript metadata
     */
    fun getTranscriptMetadata(): Map<String, Any> {
        return mapOf(
            "session_id" to (currentSessionId ?: ""),
            "segment_count" to transcriptBuffer.size,
            "total_length" to transcriptBuffer.sumOf { it.length },
            "is_ephemeral" to true,
            "segments" to metadataBuffer
        )
    }

    /**
     * End the ephemeral session and purge all data
     */
    fun endEphemeralSession(): Boolean {
        if (!isEphemeralModeActive.getAndSet(false)) {
            Timber.w("No active ephemeral session to end")
            return false
        }
        
        val sessionId = currentSessionId
        if (sessionId != null) {
            // Clear the pending purge marker
            clearPendingPurge()
            
            // Audit log the purge
            coroutineScope.launch {
                auditLogger.logEvent(
                    eventType = "EPHEMERAL_SESSION_END",
                    details = mapOf(
                        "message" to "Ephemeral transcript session ended and purged",
                        "session_id" to sessionId,
                        "segment_count" to transcriptBuffer.size
                    )
                )
            }
            
            // Log metrics
            metricsCollector?.recordEvent("ephemeral_session_end", mapOf(
                "session_id" to sessionId,
                "segment_count" to transcriptBuffer.size.toString(),
                "total_length" to transcriptBuffer.sumOf { it.length }.toString()
            ))
        }
        
        // Clear all in-memory data
        clearBuffers()
        currentSessionId = null
        
        Timber.i("Ended and purged ephemeral transcript session: $sessionId")
        return true
    }

    /**
     * Check if there's a pending purge from a previous crash
     */
    private fun checkForPendingPurge() {
        val hasPendingPurge = prefs.getBoolean(KEY_HAS_PENDING_PURGE, false)
        
        if (hasPendingPurge) {
            val sessionId = prefs.getString(KEY_PENDING_SESSION_ID, "unknown") ?: "unknown"
            val timestamp = prefs.getLong(KEY_CRASH_TIMESTAMP, 0L)
            
            Timber.w("Detected pending purge from crashed session: $sessionId")
            
            // Audit log the abandoned purge
            coroutineScope.launch {
                auditLogger.logEvent(
                    eventType = "ABANDON_PURGE",
                    details = mapOf(
                        "message" to "Purged abandoned transcript after crash/restart",
                        "session_id" to sessionId,
                        "crash_timestamp" to timestamp,
                        "recovery_timestamp" to System.currentTimeMillis()
                    )
                )
            }
            
            // Log metrics
            metricsCollector?.recordEvent("ephemeral_abandon_purge", mapOf(
                "session_id" to sessionId,
                "crash_timestamp" to timestamp.toString(),
                "recovery_timestamp" to System.currentTimeMillis().toString()
            ))
            
            // Clear the pending purge
            clearPendingPurge()
        }
    }

    /**
     * Mark that we have a pending purge in case of crash
     */
    private fun markPendingPurge(sessionId: String) {
        prefs.edit()
            .putBoolean(KEY_HAS_PENDING_PURGE, true)
            .putString(KEY_PENDING_SESSION_ID, sessionId)
            .putLong(KEY_CRASH_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Clear the pending purge marker
     */
    private fun clearPendingPurge() {
        prefs.edit()
            .putBoolean(KEY_HAS_PENDING_PURGE, false)
            .remove(KEY_PENDING_SESSION_ID)
            .remove(KEY_CRASH_TIMESTAMP)
            .apply()
    }

    /**
     * Clear all in-memory buffers
     */
    private fun clearBuffers() {
        transcriptBuffer.clear()
        metadataBuffer.clear()
    }

    /**
     * Check if ephemeral mode is active
     */
    fun isEphemeralModeActive(): Boolean {
        return isEphemeralModeActive.get()
    }

    /**
     * Force purge all data (for emergency situations)
     */
    fun forcePurge() {
        val wasActive = isEphemeralModeActive.getAndSet(false)
        val sessionId = currentSessionId
        
        clearBuffers()
        currentSessionId = null
        clearPendingPurge()
        
        if (wasActive && sessionId != null) {
            // Audit log the force purge
            coroutineScope.launch {
                auditLogger.logEvent(
                    eventType = "FORCE_PURGE",
                    details = mapOf(
                        "message" to "Emergency force purge of ephemeral transcript",
                        "session_id" to sessionId
                    )
                )
            }
            
            Timber.w("Force purged ephemeral transcript session: $sessionId")
        }
    }
}
