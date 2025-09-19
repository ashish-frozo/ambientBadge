package com.frozo.ambientscribe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.frozo.ambientscribe.audio.AudioCapture
import com.frozo.ambientscribe.audio.AudioProcessingConfig
import com.frozo.ambientscribe.audio.SpeakerDiarization
import com.frozo.ambientscribe.services.DozeExclusionManager
import com.frozo.ambientscribe.services.OEMKillerWatchdog
import com.frozo.ambientscribe.transcription.ASRError
import com.frozo.ambientscribe.transcription.AudioTranscriptionPipeline
import com.frozo.ambientscribe.ui.ASRErrorView
import com.frozo.ambientscribe.ui.AudioProcessingSettingsView
import com.frozo.ambientscribe.ui.SpeakerRoleView
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Main activity for Ambient Scribe application
 */
class MainActivity : AppCompatActivity() {

    private lateinit var transcriptionPipeline: AudioTranscriptionPipeline
    private lateinit var dozeExclusionManager: DozeExclusionManager
    private lateinit var oemKillerWatchdog: OEMKillerWatchdog
    private var isRecording = false
    
    private lateinit var speakerRoleView: SpeakerRoleView
    private lateinit var transcriptionTextView: TextView
    private lateinit var recordButton: Button
    private lateinit var swapRolesButton: Button
    private lateinit var deleteLast30sButton: Button
    private lateinit var ephemeralModeCheckbox: CheckBox
    private lateinit var errorView: ASRErrorView
    private lateinit var audioProcessingSettingsView: AudioProcessingSettingsView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            initializeAudio()
        } else {
            Toast.makeText(this, "Audio permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Timber.d("MainActivity created")
        
        // Initialize UI components
        speakerRoleView = findViewById(R.id.speakerRoleView)
        transcriptionTextView = findViewById(R.id.transcriptionTextView)
        recordButton = findViewById(R.id.recordButton)
        swapRolesButton = findViewById(R.id.swapRolesButton)
        deleteLast30sButton = findViewById(R.id.deleteLast30sButton)
        ephemeralModeCheckbox = findViewById(R.id.ephemeralModeCheckbox)
        errorView = findViewById(R.id.errorView)
        audioProcessingSettingsView = findViewById(R.id.audioProcessingSettingsView)
        
        // Initialize Doze exclusion manager
        dozeExclusionManager = DozeExclusionManager(this)
        
        // Get OEM killer watchdog from application
        oemKillerWatchdog = (application as AmbientScribeApplication).oemKillerWatchdog
        
        // Check for abnormal termination
        if (oemKillerWatchdog.wasAbnormallyTerminated()) {
            val details = oemKillerWatchdog.getAbnormalTerminationDetails()
            val oemType = details["oem_type"] as String
            
            // Show guidance
            val guidance = oemKillerWatchdog.getOEMOptimizationGuidance()
            Toast.makeText(this, "App was terminated by $oemType. $guidance", Toast.LENGTH_LONG).show()
            
            // Clear the flag
            oemKillerWatchdog.clearAbnormalTermination()
        }
        
        // Set up UI listeners
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        
        swapRolesButton.setOnClickListener {
            swapSpeakerRoles()
        }
        
        speakerRoleView.setOnSpeakerSwapListener {
            swapSpeakerRoles()
        }
        
        deleteLast30sButton.setOnClickListener {
            deleteLast30Seconds()
        }
        
        // Set up error view retry listener
        errorView.setOnRetryListener {
            handleErrorRetry()
        }
        
        checkPermissionsAndInitialize()
    }

    private fun checkPermissionsAndInitialize() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val allPermissionsGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allPermissionsGranted) {
            initializeAudio()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun initializeAudio() {
        transcriptionPipeline = AudioTranscriptionPipeline(this)
        
        lifecycleScope.launch {
            val result = transcriptionPipeline.initialize()
            if (result.isSuccess) {
                Timber.i("Transcription pipeline initialized successfully")
                
                // Set up audio processing settings
                val audioProcessingConfig = transcriptionPipeline.getAudioProcessingConfig()
                audioProcessingSettingsView.setAudioProcessingConfig(audioProcessingConfig, this@MainActivity)
                
                updateUI()
                
                // Check if battery optimization exclusion is needed
                if (!dozeExclusionManager.isIgnoringBatteryOptimizations()) {
                    // Request battery optimization exclusion
                    val intent = dozeExclusionManager.createBatteryOptimizationExclusionIntent()
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to request battery optimization exclusion")
                    }
                }
            } else {
                Timber.e("Failed to initialize transcription pipeline: ${result.exceptionOrNull()}")
                Toast.makeText(this@MainActivity, "Failed to initialize audio", Toast.LENGTH_LONG).show()
                
                // Show initialization error
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    val error = ASRError.InitializationError(
                        message = "Failed to initialize: ${exception.message}",
                        cause = exception
                    )
                    handleASRError(error)
                }
            }
        }
    }
    
    private fun updateUI() {
        // Update UI based on current state
        recordButton.text = if (isRecording) "Stop Recording" else "Start Recording"
        
        // Set initial speaker role view state
        val currentSpeaker = transcriptionPipeline.getCurrentSpeaker()
        speakerRoleView.updateSpeaker(
            speakerId = currentSpeaker,
            label = transcriptionPipeline.getSpeakerLabel(currentSpeaker),
            confidence = 0.7f,
            isManual = false
        )
    }

    private fun startRecording() {
        if (isRecording) return
        
        // Hide any previous errors
        errorView.hideError()
        
        // Start Doze exclusion service
        dozeExclusionManager.startDozeExclusionService()
        
        lifecycleScope.launch {
            // Check if ephemeral mode is enabled
            val ephemeralMode = ephemeralModeCheckbox.isChecked
            
            val result = transcriptionPipeline.startTranscription(ephemeralMode)
            if (result.isSuccess) {
                isRecording = true
                Timber.i("Recording started" + if (ephemeralMode) " in ephemeral mode" else "")
                
                val toastMessage = if (ephemeralMode) {
                    "Recording started in ephemeral mode (RAM-only)"
                } else {
                    "Recording started"
                }
                Toast.makeText(this@MainActivity, toastMessage, Toast.LENGTH_SHORT).show()
                updateUI()
                
                // Collect transcription results
                launch {
                    transcriptionPipeline.getTranscriptionFlow().collect { result ->
                        // Update transcription text
                        val currentText = transcriptionTextView.text.toString()
                        val newText = if (currentText.isEmpty()) {
                            result.transcription.text
                        } else {
                            "$currentText ${result.transcription.text}"
                        }
                        transcriptionTextView.text = newText
                        
                        Timber.v("Transcription: ${result.transcription.text}")
                    }
                }
                
                // Collect diarization results
                launch {
                    transcriptionPipeline.getDiarizationFlow().collect { result ->
                        // Update speaker role view
                        speakerRoleView.updateSpeaker(
                            speakerId = result.speakerId,
                            label = result.speakerLabel,
                            confidence = result.confidence,
                            isManual = result.isManuallyAssigned
                        )
                        
                        Timber.d("Diarization: ${result.speakerLabel}")
                    }
                }
                
                // Collect error events
                launch {
                    transcriptionPipeline.getErrorFlow().collect { error ->
                        handleASRError(error)
                    }
                }
            } else {
                Timber.e("Failed to start recording: ${result.exceptionOrNull()}")
                Toast.makeText(this@MainActivity, "Failed to start recording", Toast.LENGTH_LONG).show()
                
                // Show error
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    val error = ASRError.fromException(exception)
                    handleASRError(error)
                }
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        
        // Stop Doze exclusion service
        dozeExclusionManager.stopDozeExclusionService()
        
        lifecycleScope.launch {
            val result = transcriptionPipeline.stopTranscription()
            if (result.isSuccess) {
                isRecording = false
                Timber.i("Recording stopped")
                Toast.makeText(this@MainActivity, "Recording stopped", Toast.LENGTH_SHORT).show()
                updateUI()
                
                // Verify buffer is empty after stopping
                val isBufferEmpty = transcriptionPipeline.verifyAudioBufferEmpty()
                if (!isBufferEmpty) {
                    Timber.w("Audio buffer not purged after session end!")
                } else {
                    Timber.d("Audio buffer successfully purged after session end")
                }
            } else {
                Timber.e("Failed to stop recording: ${result.exceptionOrNull()}")
            }
        }
    }
    
    /**
     * Swap doctor/patient roles (one-tap role swap)
     */
    private fun swapSpeakerRoles() {
        transcriptionPipeline.swapSpeakerRoles()
        Toast.makeText(this, R.string.roles_swapped, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Delete last 30 seconds of audio
     */
    private fun deleteLast30Seconds() {
        lifecycleScope.launch {
            // Delete last 30 seconds
            transcriptionPipeline.deleteLast30Seconds()
            
            // Verify buffer is empty
            val isBufferEmpty = transcriptionPipeline.verifyAudioBufferEmpty()
            
            // Show appropriate message
            if (isBufferEmpty) {
                Toast.makeText(this@MainActivity, R.string.audio_purged, Toast.LENGTH_SHORT).show()
                Timber.i("Last 30 seconds deleted successfully, buffer verified empty")
            } else {
                Toast.makeText(this@MainActivity, R.string.buffer_not_empty_warning, Toast.LENGTH_LONG).show()
                Timber.w("Failed to completely purge audio buffer!")
            }
        }
    }
    
    /**
     * Handle ASR errors
     */
    private fun handleASRError(error: ASRError) {
        Timber.w("Handling ASR error: ${error.message} [${error.errorCode}]")
        
        // Show error in UI
        runOnUiThread {
            errorView.showError(error)
        }
        
        // Handle specific error types
        when (error) {
            is ASRError.ThermalError -> {
                // Just show warning, transcription continues with reduced quality
                Toast.makeText(
                    this,
                    "Device overheating, reducing transcription quality",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            is ASRError.DecoderError -> {
                // Serious error, stop recording if active
                if (isRecording) {
                    stopRecording()
                }
            }
            
            is ASRError.AudioInputError -> {
                // Try to recover audio input
                if (isRecording) {
                    stopRecording()
                    // Wait a moment before restarting
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(1000)
                        startRecording()
                    }
                }
            }
            
            is ASRError.NetworkError -> {
                // Just show warning, will retry automatically
            }
            
            else -> {
                // For other errors, stop recording if not recoverable
                if (!error.recoverable && isRecording) {
                    stopRecording()
                }
            }
        }
    }
    
    /**
     * Handle retry action from error view
     */
    private fun handleErrorRetry() {
        Timber.d("Retry requested for ASR error")
        
        // Hide error view
        errorView.hideError()
        
        // If not recording, try to start
        if (!isRecording) {
            startRecording()
        } else {
            // If already recording, restart the pipeline
            lifecycleScope.launch {
                stopRecording()
                kotlinx.coroutines.delay(1000)
                startRecording()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Stop Doze exclusion service if recording
        if (isRecording && ::dozeExclusionManager.isInitialized) {
            dozeExclusionManager.stopDozeExclusionService()
        }
        
        // Clean up transcription pipeline
        if (::transcriptionPipeline.isInitialized) {
            transcriptionPipeline.cleanup()
        }
        
        Timber.d("MainActivity destroyed")
    }
}