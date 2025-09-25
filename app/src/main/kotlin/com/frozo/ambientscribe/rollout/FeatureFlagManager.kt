package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Feature Flag Manager for controlling rollout and feature availability
 * 
 * Manages feature flags for:
 * - ambient_scribe_enabled: Master switch for the entire app
 * - llm_processing_enabled: Controls LLM processing capabilities
 * - te_language_enabled: Controls Telugu language support
 * 
 * Features:
 * - Local storage with SharedPreferences
 * - Remote configuration support
 * - A/B testing capabilities
 * - Audit logging for flag changes
 * - Graceful degradation on flag failures
 */
class FeatureFlagManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREFS_NAME = "feature_flags"
        private const val KEY_AMBIENT_SCRIBE_ENABLED = "ambient_scribe_enabled"
        private const val KEY_LLM_PROCESSING_ENABLED = "llm_processing_enabled"
        private const val KEY_TE_LANGUAGE_ENABLED = "te_language_enabled"
        private const val KEY_REMOTE_CONFIG_URL = "remote_config_url"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_AB_TEST_GROUP = "ab_test_group"
        
        // Default values
        private const val DEFAULT_AMBIENT_SCRIBE_ENABLED = true
        private const val DEFAULT_LLM_PROCESSING_ENABLED = true
        private const val DEFAULT_TE_LANGUAGE_ENABLED = false
        
        @Volatile
        private var INSTANCE: FeatureFlagManager? = null
        
        fun getInstance(context: Context): FeatureFlagManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FeatureFlagManager(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Feature flag states
    private val _ambientScribeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AMBIENT_SCRIBE_ENABLED, DEFAULT_AMBIENT_SCRIBE_ENABLED)
    )
    val ambientScribeEnabled: StateFlow<Boolean> = _ambientScribeEnabled.asStateFlow()
    
    private val _llmProcessingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LLM_PROCESSING_ENABLED, DEFAULT_LLM_PROCESSING_ENABLED)
    )
    val llmProcessingEnabled: StateFlow<Boolean> = _llmProcessingEnabled.asStateFlow()
    
    private val _teLanguageEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_TE_LANGUAGE_ENABLED, DEFAULT_TE_LANGUAGE_ENABLED)
    )
    val teLanguageEnabled: StateFlow<Boolean> = _teLanguageEnabled.asStateFlow()
    
    // A/B testing groups
    private val abTestGroups = ConcurrentHashMap<String, String>()
    
    // Remote configuration
    private var remoteConfigUrl: String? = null
    private var lastUpdate: Long = 0L
    
    init {
        loadStoredValues()
    }
    
    /**
     * Load stored values from SharedPreferences
     */
    private fun loadStoredValues() {
        remoteConfigUrl = prefs.getString(KEY_REMOTE_CONFIG_URL, null)
        lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        
        // Load A/B test groups
        val abTestGroupJson = prefs.getString(KEY_AB_TEST_GROUP, "{}")
        // Parse JSON and populate abTestGroups (simplified for this example)
        abTestGroups["default"] = "control"
    }
    
    /**
     * Check if ambient scribe is enabled
     */
    fun isAmbientScribeEnabled(): Boolean {
        return try {
            _ambientScribeEnabled.value
        } catch (e: Exception) {
            // Graceful degradation - default to disabled on error
            false
        }
    }
    
    /**
     * Check if LLM processing is enabled
     */
    fun isLLMProcessingEnabled(): Boolean {
        return try {
            _llmProcessingEnabled.value && isAmbientScribeEnabled()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if Telugu language is enabled
     */
    fun isTeLanguageEnabled(): Boolean {
        return try {
            _teLanguageEnabled.value && isAmbientScribeEnabled()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Set ambient scribe enabled state
     */
    fun setAmbientScribeEnabled(enabled: Boolean, source: String = "manual") {
        try {
            _ambientScribeEnabled.value = enabled
            prefs.edit().putBoolean(KEY_AMBIENT_SCRIBE_ENABLED, enabled).apply()
            
            // Log flag change
            logFlagChange("ambient_scribe_enabled", enabled, source)
        } catch (e: Exception) {
            // Log error but don't crash
            logError("Failed to set ambient_scribe_enabled", e)
        }
    }
    
    /**
     * Set LLM processing enabled state
     */
    fun setLLMProcessingEnabled(enabled: Boolean, source: String = "manual") {
        try {
            _llmProcessingEnabled.value = enabled
            prefs.edit().putBoolean(KEY_LLM_PROCESSING_ENABLED, enabled).apply()
            
            logFlagChange("llm_processing_enabled", enabled, source)
        } catch (e: Exception) {
            logError("Failed to set llm_processing_enabled", e)
        }
    }
    
    /**
     * Set Telugu language enabled state
     */
    fun setTeLanguageEnabled(enabled: Boolean, source: String = "manual") {
        try {
            _teLanguageEnabled.value = enabled
            prefs.edit().putBoolean(KEY_TE_LANGUAGE_ENABLED, enabled).apply()
            
            logFlagChange("te_language_enabled", enabled, source)
        } catch (e: Exception) {
            logError("Failed to set te_language_enabled", e)
        }
    }
    
    /**
     * Get A/B test group for a feature
     */
    fun getABTestGroup(feature: String): String {
        return abTestGroups[feature] ?: "control"
    }
    
    /**
     * Set A/B test group for a feature
     */
    fun setABTestGroup(feature: String, group: String) {
        abTestGroups[feature] = group
        // Store in preferences
        val json = abTestGroups.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
        prefs.edit().putString(KEY_AB_TEST_GROUP, "{$json}").apply()
    }
    
    /**
     * Update feature flags from remote configuration
     */
    suspend fun updateFromRemoteConfig(config: Map<String, Any>) {
        try {
            config["ambient_scribe_enabled"]?.let { value ->
                if (value is Boolean) {
                    setAmbientScribeEnabled(value, "remote")
                }
            }
            
            config["llm_processing_enabled"]?.let { value ->
                if (value is Boolean) {
                    setLLMProcessingEnabled(value, "remote")
                }
            }
            
            config["te_language_enabled"]?.let { value ->
                if (value is Boolean) {
                    setTeLanguageEnabled(value, "remote")
                }
            }
            
            // Update A/B test groups
            config["ab_test_groups"]?.let { value ->
                if (value is Map<*, *>) {
                    value.forEach { (k, v) ->
                        if (k is String && v is String) {
                            setABTestGroup(k, v)
                        }
                    }
                }
            }
            
            lastUpdate = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_UPDATE, lastUpdate).apply()
            
        } catch (e: Exception) {
            logError("Failed to update from remote config", e)
        }
    }
    
    /**
     * Get all feature flags as a map
     */
    fun getAllFlags(): Map<String, Any> {
        return mapOf(
            "ambient_scribe_enabled" to isAmbientScribeEnabled(),
            "llm_processing_enabled" to isLLMProcessingEnabled(),
            "te_language_enabled" to isTeLanguageEnabled(),
            "ab_test_groups" to abTestGroups.toMap(),
            "last_update" to lastUpdate
        )
    }
    
    /**
     * Reset all flags to defaults
     */
    fun resetToDefaults() {
        setAmbientScribeEnabled(DEFAULT_AMBIENT_SCRIBE_ENABLED, "reset")
        setLLMProcessingEnabled(DEFAULT_LLM_PROCESSING_ENABLED, "reset")
        setTeLanguageEnabled(DEFAULT_TE_LANGUAGE_ENABLED, "reset")
    }
    
    /**
     * Check if remote config is available
     */
    fun isRemoteConfigAvailable(): Boolean {
        return remoteConfigUrl != null && 
               (System.currentTimeMillis() - lastUpdate) < 24 * 60 * 60 * 1000L // 24 hours
    }
    
    /**
     * Log flag change for audit purposes
     */
    private fun logFlagChange(flag: String, value: Boolean, source: String) {
        // This would integrate with the audit logging system
        // For now, we'll use a simple log
        android.util.Log.i("FeatureFlagManager", "Flag changed: $flag=$value (source: $source)")
    }
    
    /**
     * Log error for debugging
     */
    private fun logError(message: String, throwable: Throwable) {
        android.util.Log.e("FeatureFlagManager", message, throwable)
    }
}
