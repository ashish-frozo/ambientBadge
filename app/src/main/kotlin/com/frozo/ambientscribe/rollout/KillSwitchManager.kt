package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kill Switch Manager for immediate feature disable
 * 
 * Provides emergency kill switches for:
 * - Audio capture disable
 * - LLM processing disable
 * - App functionality disable
 * 
 * Features:
 * - Immediate effect (no restart required)
 * - Multiple kill switch levels
 * - Remote configuration support
 * - Audit logging for kill switch activations
 * - Graceful degradation
 */
class KillSwitchManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREFS_NAME = "kill_switches"
        private const val KEY_AUDIO_CAPTURE_DISABLED = "audio_capture_disabled"
        private const val KEY_LLM_PROCESSING_DISABLED = "llm_processing_disabled"
        private const val KEY_APP_DISABLED = "app_disabled"
        private const val KEY_EMERGENCY_DISABLED = "emergency_disabled"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_REASON = "reason"
        private const val KEY_ACTIVATED_BY = "activated_by"
        
        // Default values
        private const val DEFAULT_AUDIO_CAPTURE_DISABLED = false
        private const val DEFAULT_LLM_PROCESSING_DISABLED = false
        private const val DEFAULT_APP_DISABLED = false
        private const val DEFAULT_EMERGENCY_DISABLED = false
        
        @Volatile
        private var INSTANCE: KillSwitchManager? = null
        
        fun getInstance(context: Context): KillSwitchManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KillSwitchManager(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Kill switch states
    private val _audioCaptureDisabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUDIO_CAPTURE_DISABLED, DEFAULT_AUDIO_CAPTURE_DISABLED)
    )
    val audioCaptureDisabled: StateFlow<Boolean> = _audioCaptureDisabled.asStateFlow()
    
    private val _llmProcessingDisabled = MutableStateFlow(
        prefs.getBoolean(KEY_LLM_PROCESSING_DISABLED, DEFAULT_LLM_PROCESSING_DISABLED)
    )
    val llmProcessingDisabled: StateFlow<Boolean> = _llmProcessingDisabled.asStateFlow()
    
    private val _appDisabled = MutableStateFlow(
        prefs.getBoolean(KEY_APP_DISABLED, DEFAULT_APP_DISABLED)
    )
    val appDisabled: StateFlow<Boolean> = _appDisabled.asStateFlow()
    
    private val _emergencyDisabled = MutableStateFlow(
        prefs.getBoolean(KEY_EMERGENCY_DISABLED, DEFAULT_EMERGENCY_DISABLED)
    )
    val emergencyDisabled: StateFlow<Boolean> = _emergencyDisabled.asStateFlow()
    
    // Atomic flags for immediate checks
    private val audioCaptureKilled = AtomicBoolean(_audioCaptureDisabled.value)
    private val llmProcessingKilled = AtomicBoolean(_llmProcessingDisabled.value)
    private val appKilled = AtomicBoolean(_appDisabled.value)
    private val emergencyKilled = AtomicBoolean(_emergencyDisabled.value)
    
    // Kill switch metadata
    private var lastUpdate: Long = 0L
    private var reason: String = ""
    private var activatedBy: String = ""
    
    init {
        loadStoredValues()
    }
    
    /**
     * Load stored values from SharedPreferences
     */
    private fun loadStoredValues() {
        lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        reason = prefs.getString(KEY_REASON, "") ?: ""
        activatedBy = prefs.getString(KEY_ACTIVATED_BY, "") ?: ""
    }
    
    /**
     * Check if audio capture is killed
     */
    fun isAudioCaptureKilled(): Boolean {
        return try {
            audioCaptureKilled.get() || emergencyKilled.get()
        } catch (e: Exception) {
            // Graceful degradation - assume killed on error
            true
        }
    }
    
    /**
     * Check if LLM processing is killed
     */
    fun isLLMProcessingKilled(): Boolean {
        return try {
            llmProcessingKilled.get() || emergencyKilled.get()
        } catch (e: Exception) {
            true
        }
    }
    
    /**
     * Check if app is killed
     */
    fun isAppKilled(): Boolean {
        return try {
            appKilled.get() || emergencyKilled.get()
        } catch (e: Exception) {
            true
        }
    }
    
    /**
     * Check if emergency kill switch is active
     */
    fun isEmergencyKilled(): Boolean {
        return try {
            emergencyKilled.get()
        } catch (e: Exception) {
            true
        }
    }
    
    /**
     * Kill audio capture
     */
    fun killAudioCapture(reason: String, activatedBy: String = "system") {
        try {
            _audioCaptureDisabled.value = true
            audioCaptureKilled.set(true)
            updateMetadata(reason, activatedBy)
            prefs.edit()
                .putBoolean(KEY_AUDIO_CAPTURE_DISABLED, true)
                .apply()
            
            logKillSwitchActivation("audio_capture", reason, activatedBy)
        } catch (e: Exception) {
            logError("Failed to kill audio capture", e)
        }
    }
    
    /**
     * Kill LLM processing
     */
    fun killLLMProcessing(reason: String, activatedBy: String = "system") {
        try {
            _llmProcessingDisabled.value = true
            llmProcessingKilled.set(true)
            updateMetadata(reason, activatedBy)
            prefs.edit()
                .putBoolean(KEY_LLM_PROCESSING_DISABLED, true)
                .apply()
            
            logKillSwitchActivation("llm_processing", reason, activatedBy)
        } catch (e: Exception) {
            logError("Failed to kill LLM processing", e)
        }
    }
    
    /**
     * Kill app functionality
     */
    fun killApp(reason: String, activatedBy: String = "system") {
        try {
            _appDisabled.value = true
            appKilled.set(true)
            updateMetadata(reason, activatedBy)
            prefs.edit()
                .putBoolean(KEY_APP_DISABLED, true)
                .apply()
            
            logKillSwitchActivation("app", reason, activatedBy)
        } catch (e: Exception) {
            logError("Failed to kill app", e)
        }
    }
    
    /**
     * Activate emergency kill switch (kills everything)
     */
    fun activateEmergencyKillSwitch(reason: String, activatedBy: String = "system") {
        try {
            _emergencyDisabled.value = true
            emergencyKilled.set(true)
            updateMetadata(reason, activatedBy)
            prefs.edit()
                .putBoolean(KEY_EMERGENCY_DISABLED, true)
                .apply()
            
            logKillSwitchActivation("emergency", reason, activatedBy)
        } catch (e: Exception) {
            logError("Failed to activate emergency kill switch", e)
        }
    }
    
    /**
     * Restore audio capture
     */
    fun restoreAudioCapture(activatedBy: String = "system") {
        try {
            _audioCaptureDisabled.value = false
            audioCaptureKilled.set(false)
            updateMetadata("Audio capture restored", activatedBy)
            prefs.edit()
                .putBoolean(KEY_AUDIO_CAPTURE_DISABLED, false)
                .apply()
            
            logKillSwitchRestoration("audio_capture", activatedBy)
        } catch (e: Exception) {
            logError("Failed to restore audio capture", e)
        }
    }
    
    /**
     * Restore LLM processing
     */
    fun restoreLLMProcessing(activatedBy: String = "system") {
        try {
            _llmProcessingDisabled.value = false
            llmProcessingKilled.set(false)
            updateMetadata("LLM processing restored", activatedBy)
            prefs.edit()
                .putBoolean(KEY_LLM_PROCESSING_DISABLED, false)
                .apply()
            
            logKillSwitchRestoration("llm_processing", activatedBy)
        } catch (e: Exception) {
            logError("Failed to restore LLM processing", e)
        }
    }
    
    /**
     * Restore app functionality
     */
    fun restoreApp(activatedBy: String = "system") {
        try {
            _appDisabled.value = false
            appKilled.set(false)
            updateMetadata("App functionality restored", activatedBy)
            prefs.edit()
                .putBoolean(KEY_APP_DISABLED, false)
                .apply()
            
            logKillSwitchRestoration("app", activatedBy)
        } catch (e: Exception) {
            logError("Failed to restore app", e)
        }
    }
    
    /**
     * Deactivate emergency kill switch
     */
    fun deactivateEmergencyKillSwitch(activatedBy: String = "system") {
        try {
            _emergencyDisabled.value = false
            emergencyKilled.set(false)
            updateMetadata("Emergency kill switch deactivated", activatedBy)
            prefs.edit()
                .putBoolean(KEY_EMERGENCY_DISABLED, false)
                .apply()
            
            logKillSwitchRestoration("emergency", activatedBy)
        } catch (e: Exception) {
            logError("Failed to deactivate emergency kill switch", e)
        }
    }
    
    /**
     * Get kill switch status
     */
    fun getKillSwitchStatus(): Map<String, Any> {
        return mapOf(
            "audio_capture_disabled" to isAudioCaptureKilled(),
            "llm_processing_disabled" to isLLMProcessingKilled(),
            "app_disabled" to isAppKilled(),
            "emergency_disabled" to isEmergencyKilled(),
            "last_update" to lastUpdate,
            "reason" to reason,
            "activated_by" to activatedBy
        )
    }
    
    /**
     * Update metadata
     */
    private fun updateMetadata(reason: String, activatedBy: String) {
        this.reason = reason
        this.activatedBy = activatedBy
        lastUpdate = System.currentTimeMillis()
        
        prefs.edit()
            .putString(KEY_REASON, reason)
            .putString(KEY_ACTIVATED_BY, activatedBy)
            .putLong(KEY_LAST_UPDATE, lastUpdate)
            .apply()
    }
    
    /**
     * Log kill switch activation
     */
    private fun logKillSwitchActivation(switch: String, reason: String, activatedBy: String) {
        android.util.Log.w("KillSwitchManager", "Kill switch activated: $switch (reason: $reason, by: $activatedBy)")
    }
    
    /**
     * Log kill switch restoration
     */
    private fun logKillSwitchRestoration(switch: String, activatedBy: String) {
        android.util.Log.i("KillSwitchManager", "Kill switch restored: $switch (by: $activatedBy)")
    }
    
    /**
     * Log error
     */
    private fun logError(message: String, throwable: Throwable) {
        android.util.Log.e("KillSwitchManager", message, throwable)
    }
}
