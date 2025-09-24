package com.frozo.ambientscribe.audio

import android.content.Context
import android.content.SharedPreferences
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configuration for audio processing features (NS, AEC, AGC) with persistence
 * and A/B testing capabilities.
 */
class AudioProcessingConfig(
    private val context: Context,
    private val metricsCollector: MetricsCollector? = null
) {
    companion object {
        // Shared preferences keys
        private const val PREFS_NAME = "audio_processing_prefs"
        private const val KEY_NS_ENABLED = "ns_enabled"
        private const val KEY_AEC_ENABLED = "aec_enabled"
        private const val KEY_AGC_ENABLED = "agc_enabled"
        private const val KEY_CLINIC_ID = "clinic_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_AB_TEST_GROUP = "ab_test_group"
        
        // A/B test groups
        private const val GROUP_A = "A" // All features enabled
        private const val GROUP_B = "B" // NS only
        private const val GROUP_C = "C" // AEC only
        private const val GROUP_D = "D" // AGC only
        private const val GROUP_E = "E" // All features disabled
        
        // Default settings
        private const val DEFAULT_NS_ENABLED = true
        private const val DEFAULT_AEC_ENABLED = true
        private const val DEFAULT_AGC_ENABLED = true
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Device and clinic identifiers
    private val deviceId: String = getOrCreateDeviceId()
    private var clinicId: String = prefs.getString(KEY_CLINIC_ID, "") ?: ""
    
    // A/B test group
    private val abTestGroup: String = getOrCreateAbTestGroup()
    
    // State flows for configuration
    private val _nsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NS_ENABLED, DEFAULT_NS_ENABLED))
    private val _aecEnabled = MutableStateFlow(prefs.getBoolean(KEY_AEC_ENABLED, DEFAULT_AEC_ENABLED))
    private val _agcEnabled = MutableStateFlow(prefs.getBoolean(KEY_AGC_ENABLED, DEFAULT_AGC_ENABLED))
    
    val nsEnabled: StateFlow<Boolean> = _nsEnabled.asStateFlow()
    val aecEnabled: StateFlow<Boolean> = _aecEnabled.asStateFlow()
    val agcEnabled: StateFlow<Boolean> = _agcEnabled.asStateFlow()
    
    // Feature change tracking
    private val featureChangeTracking = AtomicBoolean(false)
    private var baselineTranscriptionQuality = 0.0f
    
    init {
        Timber.i("AudioProcessingConfig initialized with A/B test group: $abTestGroup")
        Timber.d("NS: ${_nsEnabled.value}, AEC: ${_aecEnabled.value}, AGC: ${_agcEnabled.value}")
        
        // Apply settings to WebRTC audio utils
        applySettings()
    }
    
    /**
     * Set clinic ID for configuration persistence
     */
    fun setClinicId(id: String) {
        clinicId = id
        prefs.edit().putString(KEY_CLINIC_ID, id).apply()
        Timber.i("Clinic ID set: $id")
    }
    
    /**
     * Enable/disable Noise Suppression
     */
    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        if (_nsEnabled.value != enabled) {
            _nsEnabled.value = enabled
            prefs.edit().putBoolean(KEY_NS_ENABLED, enabled).apply()
            
            // Apply setting using MediaConstraints
            try {
                // Store setting for use in MediaConstraints when creating audio source
                Timber.d("Noise suppression setting updated: $enabled")
            } catch (e: Exception) {
                Timber.w(e, "Failed to update noise suppression setting")
            }
            
            // Log change
            logFeatureChange("ns", enabled)
            
            Timber.i("Noise Suppression ${if (enabled) "enabled" else "disabled"}")
        }
    }
    
    /**
     * Enable/disable Acoustic Echo Cancellation
     */
    fun setEchoCancellationEnabled(enabled: Boolean) {
        if (_aecEnabled.value != enabled) {
            _aecEnabled.value = enabled
            prefs.edit().putBoolean(KEY_AEC_ENABLED, enabled).apply()
            
            // Apply setting using MediaConstraints
            try {
                // Store setting for use in MediaConstraints when creating audio source
                Timber.d("Echo cancellation setting updated: $enabled")
            } catch (e: Exception) {
                Timber.w(e, "Failed to update echo cancellation setting")
            }
            
            // Log change
            logFeatureChange("aec", enabled)
            
            Timber.i("Acoustic Echo Cancellation ${if (enabled) "enabled" else "disabled"}")
        }
    }
    
    /**
     * Enable/disable Automatic Gain Control
     */
    fun setAutomaticGainControlEnabled(enabled: Boolean) {
        if (_agcEnabled.value != enabled) {
            _agcEnabled.value = enabled
            prefs.edit().putBoolean(KEY_AGC_ENABLED, enabled).apply()
            
            // Apply setting using MediaConstraints
            try {
                // Store setting for use in MediaConstraints when creating audio source
                Timber.d("Automatic gain control setting updated: $enabled")
            } catch (e: Exception) {
                Timber.w(e, "Failed to update automatic gain control setting")
            }
            
            // Log change
            logFeatureChange("agc", enabled)
            
            Timber.i("Automatic Gain Control ${if (enabled) "enabled" else "disabled"}")
        }
    }
    
    /**
     * Apply all current settings
     */
    fun applySettings() {
        try {
            // Settings are now applied via MediaConstraints when creating audio sources
            Timber.d("Audio processing settings applied: NS=${_nsEnabled.value}, AEC=${_aecEnabled.value}, AGC=${_agcEnabled.value}")
        } catch (e: Exception) {
            Timber.w(e, "Failed to apply audio processing settings")
        }
        
        Timber.d("Applied audio processing settings: NS=${_nsEnabled.value}, AEC=${_aecEnabled.value}, AGC=${_agcEnabled.value}")
    }
    
    /**
     * Create audio processing configuration for MediaRecorder
     */
    fun createAudioConfig(): Map<String, Boolean> {
        return mapOf(
            "noiseSuppression" to _nsEnabled.value,
            "echoCancellation" to _aecEnabled.value,
            "automaticGainControl" to _agcEnabled.value
        )
    }
    
    /**
     * Start tracking feature change impact
     */
    fun startFeatureChangeTracking(baselineQuality: Float) {
        featureChangeTracking.set(true)
        baselineTranscriptionQuality = baselineQuality
        Timber.d("Feature change tracking started with baseline quality: $baselineQuality")
    }
    
    /**
     * Stop tracking feature change impact and log results
     */
    fun stopFeatureChangeTracking(finalQuality: Float) {
        if (featureChangeTracking.getAndSet(false)) {
            val qualityDelta = finalQuality - baselineTranscriptionQuality
            
            // Log impact
            metricsCollector?.let {
                coroutineScope.launch {
                    it.recordEvent(
                        "audio_processing_impact",
                        mapOf(
                            "ns_enabled" to _nsEnabled.value.toString(),
                            "aec_enabled" to _aecEnabled.value.toString(),
                            "agc_enabled" to _agcEnabled.value.toString(),
                            "baseline_quality" to baselineTranscriptionQuality.toString(),
                            "final_quality" to finalQuality.toString(),
                            "quality_delta" to qualityDelta.toString(),
                            "clinic_id" to clinicId,
                            "ab_test_group" to abTestGroup
                        )
                    )
                }
            }
            
            Timber.i("Feature change impact: quality delta = $qualityDelta")
        }
    }
    
    /**
     * Get or create device ID
     */
    private fun getOrCreateDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }
    
    /**
     * Get or create A/B test group
     */
    private fun getOrCreateAbTestGroup(): String {
        var group = prefs.getString(KEY_AB_TEST_GROUP, null)
        if (group == null) {
            // Assign random test group
            val groups = arrayOf(GROUP_A, GROUP_B, GROUP_C, GROUP_D, GROUP_E)
            group = groups.random()
            prefs.edit().putString(KEY_AB_TEST_GROUP, group).apply()
            
            // Apply group settings
            when (group) {
                GROUP_A -> {
                    _nsEnabled.value = true
                    _aecEnabled.value = true
                    _agcEnabled.value = true
                }
                GROUP_B -> {
                    _nsEnabled.value = true
                    _aecEnabled.value = false
                    _agcEnabled.value = false
                }
                GROUP_C -> {
                    _nsEnabled.value = false
                    _aecEnabled.value = true
                    _agcEnabled.value = false
                }
                GROUP_D -> {
                    _nsEnabled.value = false
                    _aecEnabled.value = false
                    _agcEnabled.value = true
                }
                GROUP_E -> {
                    _nsEnabled.value = false
                    _aecEnabled.value = false
                    _agcEnabled.value = false
                }
            }
            
            // Save settings
            prefs.edit()
                .putBoolean(KEY_NS_ENABLED, _nsEnabled.value)
                .putBoolean(KEY_AEC_ENABLED, _aecEnabled.value)
                .putBoolean(KEY_AGC_ENABLED, _agcEnabled.value)
                .apply()
        }
        return group
    }
    
    /**
     * Log feature change
     */
    private fun logFeatureChange(feature: String, enabled: Boolean) {
        metricsCollector?.let {
            coroutineScope.launch {
                it.recordEvent(
                    "audio_processing_change",
                    mapOf(
                        "feature" to feature,
                        "enabled" to enabled.toString(),
                        "clinic_id" to clinicId,
                        "ab_test_group" to abTestGroup
                    )
                )
            }
        }
    }
    
    /**
     * Get current configuration as map
     */
    fun getCurrentConfig(): Map<String, Any> {
        return mapOf(
            "ns_enabled" to _nsEnabled.value,
            "aec_enabled" to _aecEnabled.value,
            "agc_enabled" to _agcEnabled.value,
            "clinic_id" to clinicId,
            "device_id" to deviceId,
            "ab_test_group" to abTestGroup
        )
    }
}
