package com.frozo.ambientscribe.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.frozo.ambientscribe.R
import com.frozo.ambientscribe.transcription.ASRError

/**
 * Custom view to display ASR errors with appropriate icons and recovery actions
 */
class ASRErrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val errorIconView: ImageView
    private val errorTitleView: TextView
    private val errorMessageView: TextView
    private val errorActionButton: Button
    
    private var onRetryListener: (() -> Unit)? = null
    private var currentError: ASRError? = null

    init {
        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_asr_error, this, true)
        
        // Get view references
        errorIconView = findViewById(R.id.errorIcon)
        errorTitleView = findViewById(R.id.errorTitle)
        errorMessageView = findViewById(R.id.errorMessage)
        errorActionButton = findViewById(R.id.errorActionButton)
        
        // Set default visibility
        visibility = View.GONE
        
        // Set up action button
        errorActionButton.setOnClickListener {
            onRetryListener?.invoke()
        }
    }

    /**
     * Set retry action listener
     */
    fun setOnRetryListener(listener: () -> Unit) {
        onRetryListener = listener
    }

    /**
     * Display an ASR error
     */
    fun showError(error: ASRError) {
        currentError = error
        
        // Set error icon based on error type
        errorIconView.setImageDrawable(getErrorIcon(error))
        
        // Set error title based on error type
        errorTitleView.text = getErrorTitle(error)
        
        // Set error message
        errorMessageView.text = error.message
        
        // Configure action button based on recoverability
        if (error.recoverable) {
            errorActionButton.visibility = View.VISIBLE
            errorActionButton.text = context.getString(R.string.retry)
        } else {
            errorActionButton.visibility = View.GONE
        }
        
        // Show the error view
        visibility = View.VISIBLE
    }

    /**
     * Hide the error view
     */
    fun hideError() {
        visibility = View.GONE
        currentError = null
    }

    /**
     * Get appropriate icon for error type
     */
    private fun getErrorIcon(error: ASRError): Drawable? {
        val iconResId = when (error) {
            is ASRError.NetworkError -> android.R.drawable.ic_dialog_alert
            is ASRError.ThermalError -> android.R.drawable.ic_dialog_alert
            is ASRError.DecoderError -> android.R.drawable.ic_dialog_alert
            is ASRError.AudioInputError -> android.R.drawable.ic_dialog_alert
            is ASRError.PermissionError -> android.R.drawable.ic_dialog_alert
            is ASRError.ResourceError -> android.R.drawable.ic_dialog_alert
            is ASRError.InitializationError -> android.R.drawable.ic_dialog_alert
            is ASRError.UnknownError -> android.R.drawable.ic_dialog_alert
        }
        
        return ContextCompat.getDrawable(context, iconResId)
    }

    /**
     * Get appropriate title for error type
     */
    private fun getErrorTitle(error: ASRError): String {
        return when (error) {
            is ASRError.NetworkError -> context.getString(R.string.network_error_title)
            is ASRError.ThermalError -> context.getString(R.string.thermal_error_title)
            is ASRError.DecoderError -> context.getString(R.string.decoder_error_title)
            is ASRError.AudioInputError -> context.getString(R.string.audio_error_title)
            is ASRError.PermissionError -> context.getString(R.string.permission_error_title)
            is ASRError.ResourceError -> context.getString(R.string.resource_error_title)
            is ASRError.InitializationError -> context.getString(R.string.initialization_error_title)
            is ASRError.UnknownError -> context.getString(R.string.unknown_error_title)
        }
    }
}
