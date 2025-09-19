package com.frozo.ambientscribe.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.frozo.ambientscribe.R
import com.frozo.ambientscribe.audio.AudioProcessingConfig
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Custom view for displaying and managing audio processing settings (NS, AEC, AGC).
 */
class AudioProcessingSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var nsSwitch: Switch
    private lateinit var aecSwitch: Switch
    private lateinit var agcSwitch: Switch
    private var audioProcessingConfig: AudioProcessingConfig? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_audio_processing_settings, this, true)
        orientation = VERTICAL

        nsSwitch = findViewById(R.id.switchNoiseSuppression)
        aecSwitch = findViewById(R.id.switchEchoCancellation)
        agcSwitch = findViewById(R.id.switchAutomaticGainControl)

        setupListeners()
    }

    /**
     * Sets the AudioProcessingConfig instance to interact with and observes its state flows.
     */
    fun setAudioProcessingConfig(config: AudioProcessingConfig, lifecycleOwner: LifecycleOwner) {
        this.audioProcessingConfig = config
        updateUI()
        
        // Observe state flows for changes
        lifecycleOwner.lifecycleScope.launch {
            config.nsEnabled.collectLatest { enabled ->
                if (nsSwitch.isChecked != enabled) {
                    nsSwitch.isChecked = enabled
                }
            }
        }
        
        lifecycleOwner.lifecycleScope.launch {
            config.aecEnabled.collectLatest { enabled ->
                if (aecSwitch.isChecked != enabled) {
                    aecSwitch.isChecked = enabled
                }
            }
        }
        
        lifecycleOwner.lifecycleScope.launch {
            config.agcEnabled.collectLatest { enabled ->
                if (agcSwitch.isChecked != enabled) {
                    agcSwitch.isChecked = enabled
                }
            }
        }
    }

    private fun setupListeners() {
        nsSwitch.setOnCheckedChangeListener { _, isChecked ->
            audioProcessingConfig?.setNoiseSuppressionEnabled(isChecked)
            Timber.d("Noise Suppression toggled: $isChecked")
        }

        aecSwitch.setOnCheckedChangeListener { _, isChecked ->
            audioProcessingConfig?.setEchoCancellationEnabled(isChecked)
            Timber.d("Echo Cancellation toggled: $isChecked")
        }

        agcSwitch.setOnCheckedChangeListener { _, isChecked ->
            audioProcessingConfig?.setAutomaticGainControlEnabled(isChecked)
            Timber.d("Automatic Gain Control toggled: $isChecked")
        }
    }

    /**
     * Updates the UI switches to reflect the current settings from AudioProcessingConfig.
     */
    fun updateUI() {
        audioProcessingConfig?.let { config ->
            val currentSettings = config.getCurrentConfig()
            nsSwitch.isChecked = currentSettings["ns_enabled"] as Boolean
            aecSwitch.isChecked = currentSettings["aec_enabled"] as Boolean
            agcSwitch.isChecked = currentSettings["agc_enabled"] as Boolean
            Timber.d("AudioProcessingSettingsView UI updated.")
        } ?: Timber.w("AudioProcessingConfig not set for AudioProcessingSettingsView.")
    }
}