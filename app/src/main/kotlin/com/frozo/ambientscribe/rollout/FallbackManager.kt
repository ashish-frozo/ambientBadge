package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fallback Manager for graceful degradation
 * 
 * Provides fallback mechanisms when features are disabled:
 * - Manual note entry when audio capture is disabled
 * - Rule-based generation when LLM is disabled
 * - Basic functionality when app features are disabled
 * 
 * Features:
 * - Automatic fallback detection
 * - User notification of fallback mode
 * - Seamless transition between modes
 * - Audit logging for fallback activations
 */
class FallbackManager private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var INSTANCE: FallbackManager? = null
        
        fun getInstance(context: Context): FallbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FallbackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Fallback states
    private val _isInFallbackMode = MutableStateFlow(false)
    val isInFallbackMode: StateFlow<Boolean> = _isInFallbackMode.asStateFlow()
    
    private val _fallbackReason = MutableStateFlow("")
    val fallbackReason: StateFlow<String> = _fallbackReason.asStateFlow()
    
    private val _fallbackType = MutableStateFlow(FallbackType.NONE)
    val fallbackType: StateFlow<FallbackType> = _fallbackType.asStateFlow()
    
    // Dependencies
    private lateinit var featureFlagManager: FeatureFlagManager
    private lateinit var killSwitchManager: KillSwitchManager
    
    /**
     * Initialize with dependencies
     */
    fun initialize(featureFlagManager: FeatureFlagManager, killSwitchManager: KillSwitchManager) {
        this.featureFlagManager = featureFlagManager
        this.killSwitchManager = killSwitchManager
    }
    
    /**
     * Check if fallback mode should be activated
     */
    fun checkFallbackMode(): FallbackType {
        return try {
            when {
                killSwitchManager.isEmergencyKilled() -> {
                    activateFallback(FallbackType.EMERGENCY, "Emergency kill switch activated")
                    FallbackType.EMERGENCY
                }
                killSwitchManager.isAppKilled() -> {
                    activateFallback(FallbackType.APP_DISABLED, "App functionality disabled")
                    FallbackType.APP_DISABLED
                }
                killSwitchManager.isAudioCaptureKilled() -> {
                    activateFallback(FallbackType.AUDIO_DISABLED, "Audio capture disabled")
                    FallbackType.AUDIO_DISABLED
                }
                killSwitchManager.isLLMProcessingKilled() -> {
                    activateFallback(FallbackType.LLM_DISABLED, "LLM processing disabled")
                    FallbackType.LLM_DISABLED
                }
                !featureFlagManager.isAmbientScribeEnabled() -> {
                    activateFallback(FallbackType.FEATURE_DISABLED, "Ambient Scribe feature disabled")
                    FallbackType.FEATURE_DISABLED
                }
                else -> {
                    deactivateFallback()
                    FallbackType.NONE
                }
            }
        } catch (e: Exception) {
            logError("Failed to check fallback mode", e)
            activateFallback(FallbackType.ERROR, "Error checking fallback mode: ${e.message}")
            FallbackType.ERROR
        }
    }
    
    /**
     * Activate fallback mode
     */
    private fun activateFallback(type: FallbackType, reason: String) {
        _isInFallbackMode.value = true
        _fallbackReason.value = reason
        _fallbackType.value = type
        
        logFallbackActivation(type, reason)
    }
    
    /**
     * Deactivate fallback mode
     */
    private fun deactivateFallback() {
        _isInFallbackMode.value = false
        _fallbackReason.value = ""
        _fallbackType.value = FallbackType.NONE
    }
    
    /**
     * Get fallback UI message for user
     */
    fun getFallbackMessage(): String {
        return when (_fallbackType.value) {
            FallbackType.EMERGENCY -> "Emergency mode: App functionality temporarily disabled"
            FallbackType.APP_DISABLED -> "App disabled: Please contact support"
            FallbackType.AUDIO_DISABLED -> "Audio disabled: Manual note entry available"
            FallbackType.LLM_DISABLED -> "AI processing disabled: Using basic generation"
            FallbackType.FEATURE_DISABLED -> "Feature disabled: Limited functionality available"
            FallbackType.ERROR -> "Error mode: Please restart the app"
            FallbackType.NONE -> ""
        }
    }
    
    /**
     * Get fallback action available to user
     */
    fun getFallbackAction(): FallbackAction {
        return when (_fallbackType.value) {
            FallbackType.EMERGENCY -> FallbackAction.CONTACT_SUPPORT
            FallbackType.APP_DISABLED -> FallbackAction.CONTACT_SUPPORT
            FallbackType.AUDIO_DISABLED -> FallbackAction.MANUAL_ENTRY
            FallbackType.LLM_DISABLED -> FallbackAction.BASIC_GENERATION
            FallbackType.FEATURE_DISABLED -> FallbackAction.LIMITED_FUNCTIONALITY
            FallbackType.ERROR -> FallbackAction.RESTART_APP
            FallbackType.NONE -> FallbackAction.NONE
        }
    }
    
    /**
     * Check if manual note entry is available
     */
    fun isManualNoteEntryAvailable(): Boolean {
        return _fallbackType.value in listOf(
            FallbackType.AUDIO_DISABLED,
            FallbackType.LLM_DISABLED,
            FallbackType.FEATURE_DISABLED
        )
    }
    
    /**
     * Check if basic generation is available
     */
    fun isBasicGenerationAvailable(): Boolean {
        return _fallbackType.value in listOf(
            FallbackType.LLM_DISABLED,
            FallbackType.FEATURE_DISABLED
        )
    }
    
    /**
     * Check if limited functionality is available
     */
    fun isLimitedFunctionalityAvailable(): Boolean {
        return _fallbackType.value == FallbackType.FEATURE_DISABLED
    }
    
    /**
     * Launch manual note entry activity
     */
    fun launchManualNoteEntry() {
        try {
            val intent = Intent(context, ManualNoteEntryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            
            logFallbackAction("manual_note_entry")
        } catch (e: Exception) {
            logError("Failed to launch manual note entry", e)
        }
    }
    
    /**
     * Get fallback configuration
     */
    fun getFallbackConfig(): Map<String, Any> {
        return mapOf(
            "is_in_fallback_mode" to _isInFallbackMode.value,
            "fallback_type" to _fallbackType.value.name,
            "fallback_reason" to _fallbackReason.value,
            "manual_note_entry_available" to isManualNoteEntryAvailable(),
            "basic_generation_available" to isBasicGenerationAvailable(),
            "limited_functionality_available" to isLimitedFunctionalityAvailable()
        )
    }
    
    /**
     * Log fallback activation
     */
    private fun logFallbackActivation(type: FallbackType, reason: String) {
        android.util.Log.w("FallbackManager", "Fallback activated: ${type.name} (reason: $reason)")
    }
    
    /**
     * Log fallback action
     */
    private fun logFallbackAction(action: String) {
        android.util.Log.i("FallbackManager", "Fallback action: $action")
    }
    
    /**
     * Log error
     */
    private fun logError(message: String, throwable: Throwable) {
        android.util.Log.e("FallbackManager", message, throwable)
    }
    
    /**
     * Fallback types
     */
    enum class FallbackType {
        NONE,
        EMERGENCY,
        APP_DISABLED,
        AUDIO_DISABLED,
        LLM_DISABLED,
        FEATURE_DISABLED,
        ERROR
    }
    
    /**
     * Fallback actions
     */
    enum class FallbackAction {
        NONE,
        MANUAL_ENTRY,
        BASIC_GENERATION,
        LIMITED_FUNCTIONALITY,
        CONTACT_SUPPORT,
        RESTART_APP
    }
}

/**
 * Manual Note Entry Activity (placeholder)
 * This would be implemented as a separate activity
 */
class ManualNoteEntryActivity : android.app.Activity() {
    // Implementation would go here
    // This is a placeholder for the actual activity
}
