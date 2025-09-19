package com.frozo.ambientscribe.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.frozo.ambientscribe.MainActivity
import timber.log.Timber

/**
 * Foreground service to keep the app from being affected by Doze mode
 * during recording sessions.
 */
class DozeExclusionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ambient_scribe_recording_channel"
        private const val CHANNEL_NAME = "Recording Service"
        private const val CHANNEL_DESCRIPTION = "Keeps recording active even when screen is off"
        
        // Intent actions
        const val ACTION_START_SERVICE = "com.frozo.ambientscribe.START_RECORDING_SERVICE"
        const val ACTION_STOP_SERVICE = "com.frozo.ambientscribe.STOP_RECORDING_SERVICE"
        
        // Service state
        private var isRunning = false
        
        /**
         * Check if the service is currently running
         */
        fun isServiceRunning(): Boolean {
            return isRunning
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("DozeExclusionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> startForegroundService()
            ACTION_STOP_SERVICE -> stopForegroundService()
        }
        
        // Restart if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Start the service in foreground mode with notification
     */
    private fun startForegroundService() {
        if (isRunning) {
            Timber.d("Service already running")
            return
        }
        
        Timber.i("Starting DozeExclusionService")
        isRunning = true
        
        // Create notification channel for Android O+
        createNotificationChannel()
        
        // Create notification
        val notification = createNotification()
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, notification)
        
        // Acquire wake lock
        acquireWakeLock()
        
        Timber.d("DozeExclusionService started")
    }

    /**
     * Stop the foreground service
     */
    private fun stopForegroundService() {
        if (!isRunning) {
            Timber.d("Service not running")
            return
        }
        
        Timber.i("Stopping DozeExclusionService")
        isRunning = false
        
        // Release wake lock
        releaseWakeLock()
        
        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        
        // Stop the service
        stopSelf()
        
        Timber.d("DozeExclusionService stopped")
    }

    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Timber.d("Notification channel created")
        }
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(): Notification {
        // Create intent to open main activity when notification is clicked
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create stop action
        val stopIntent = Intent(this, DozeExclusionService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ambient Scribe Recording")
            .setContentText("Recording in progress")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Recording", stopPendingIntent)
            .build()
    }

    /**
     * Acquire partial wake lock to keep CPU running
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AmbientScribe::DozeExclusionService"
        ).apply {
            acquire()
        }
        
        Timber.d("Wake lock acquired")
    }

    /**
     * Release wake lock if held
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("Wake lock released")
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Ensure wake lock is released
        releaseWakeLock()
        
        isRunning = false
        Timber.d("DozeExclusionService destroyed")
    }
}
