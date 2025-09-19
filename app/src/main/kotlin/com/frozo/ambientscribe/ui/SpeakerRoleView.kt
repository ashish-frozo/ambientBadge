package com.frozo.ambientscribe.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.frozo.ambientscribe.R
import com.frozo.ambientscribe.audio.SpeakerDiarization
import timber.log.Timber

/**
 * Custom view for displaying and swapping speaker roles (doctor/patient).
 * Provides one-tap role swap functionality.
 */
class SpeakerRoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentSpeakerId = SpeakerDiarization.SPEAKER_UNKNOWN
    private var speakerLabel = "Unknown"
    private var confidence = 0f
    private var isManuallyAssigned = false
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val iconPaint = Paint().apply {
        isAntiAlias = true
    }
    
    private var doctorIcon: Drawable? = null
    private var patientIcon: Drawable? = null
    private var swapIcon: Drawable? = null
    
    private var onSpeakerSwapListener: (() -> Unit)? = null
    
    init {
        doctorIcon = ContextCompat.getDrawable(context, R.drawable.ic_doctor)
        patientIcon = ContextCompat.getDrawable(context, R.drawable.ic_patient)
        swapIcon = ContextCompat.getDrawable(context, R.drawable.ic_swap)
        
        // Set up click listener
        isClickable = true
        isFocusable = true
    }
    
    /**
     * Set listener for speaker role swap events
     */
    fun setOnSpeakerSwapListener(listener: () -> Unit) {
        onSpeakerSwapListener = listener
    }
    
    /**
     * Update speaker information
     */
    fun updateSpeaker(
        speakerId: Int,
        label: String,
        confidence: Float,
        isManual: Boolean
    ) {
        currentSpeakerId = speakerId
        speakerLabel = label
        this.confidence = confidence
        isManuallyAssigned = isManual
        
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Draw background based on speaker
        val bgColor = when (currentSpeakerId) {
            SpeakerDiarization.SPEAKER_DOCTOR -> Color.parseColor("#1976D2") // Blue
            SpeakerDiarization.SPEAKER_PATIENT -> Color.parseColor("#4CAF50") // Green
            else -> Color.parseColor("#9E9E9E") // Gray
        }
        
        backgroundPaint.color = bgColor
        canvas.drawRoundRect(0f, 0f, width, height, 20f, 20f, backgroundPaint)
        
        // Draw speaker icon
        val icon = when (currentSpeakerId) {
            SpeakerDiarization.SPEAKER_DOCTOR -> doctorIcon
            SpeakerDiarization.SPEAKER_PATIENT -> patientIcon
            else -> null
        }
        
        icon?.let {
            it.setBounds(16, (height / 2 - 24).toInt(), 16 + 48, (height / 2 + 24).toInt())
            it.draw(canvas)
        }
        
        // Draw speaker label
        val textX = if (icon != null) 80f else 16f
        val textY = height / 2 + 15
        canvas.drawText(speakerLabel, textX, textY, textPaint)
        
        // Draw confidence indicator if not manually assigned
        if (!isManuallyAssigned && currentSpeakerId != SpeakerDiarization.SPEAKER_UNKNOWN) {
            val confidenceWidth = (width - 100) * confidence
            val confidencePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = when {
                    confidence >= 0.8f -> Color.parseColor("#4CAF50") // Green
                    confidence >= 0.6f -> Color.parseColor("#FFC107") // Yellow
                    else -> Color.parseColor("#F44336") // Red
                }
            }
            
            canvas.drawRect(80f, height - 10, 80f + confidenceWidth, height - 5, confidencePaint)
        }
        
        // Draw swap icon
        swapIcon?.let {
            it.setBounds(
                (width - 48).toInt(), 
                (height / 2 - 24).toInt(), 
                (width - 16).toInt(), 
                (height / 2 + 24).toInt()
            )
            it.draw(canvas)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val width = width.toFloat()
            
            // Check if tap was on swap icon area
            if (event.x > width - 64) {
                Timber.d("Speaker role swap tapped")
                onSpeakerSwapListener?.invoke()
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
}
