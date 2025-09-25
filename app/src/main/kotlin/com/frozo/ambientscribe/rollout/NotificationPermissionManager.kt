package com.frozo.ambientscribe.rollout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification Permission Manager for Android 13+ notification handling
 * 
 * Manages notification permissions for:
 * - POST_NOTIFICATIONS permission (Android 13+)
 * - Foreground service notification persistence
 * - Permission denial UX and recovery
 * - Fallback notification strategies
 * 
 * Features:
 * - Runtime permission checking
 * - Permission request handling
 * - Denial recovery guidance
 * - Foreground service integration
 * - User education and onboarding
 */
class NotificationPermissionManager private constructor(
    private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "notification_permissions"
        private const val KEY_PERMISSION_REQUESTED = "permission_requested"
        private const val KEY_PERMISSION_DENIED_COUNT = "permission_denied_count"
        private const val KEY_LAST_REQUEST_TIME = "last_request_time"
        private const val KEY_USER_EDUCATED = "user_educated"
        
        // Permission request limits
        private const val MAX_DENIAL_COUNT = 3
        private const val REQUEST_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24 hours
        
        @Volatile
        private var INSTANCE: NotificationPermissionManager? = null
        
        fun getInstance(context: Context): NotificationPermissionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationPermissionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Permission state
    private val _hasNotificationPermission = MutableStateFlow(hasNotificationPermission())
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()
    
    private val _permissionRequested = MutableStateFlow(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSION_REQUESTED, false)
    )
    val permissionRequested: StateFlow<Boolean> = _permissionRequested.asStateFlow()
    
    private val _permissionDeniedCount = MutableStateFlow(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PERMISSION_DENIED_COUNT, 0)
    )
    val permissionDeniedCount: StateFlow<Int> = _permissionDeniedCount.asStateFlow()
    
    // Dependencies
    private lateinit var oemPermissionPlaybook: OEMPermissionPlaybook
    
    /**
     * Initialize with dependencies
     */
    fun initialize(oemPermissionPlaybook: OEMPermissionPlaybook) {
        this.oemPermissionPlaybook = oemPermissionPlaybook
    }
    
    /**
     * Check if notification permission is granted
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for older versions
        }
    }
    
    /**
     * Check if notification permission is required
     */
    fun isNotificationPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
    
    /**
     * Check if permission can be requested
     */
    fun canRequestPermission(): Boolean {
        if (!isNotificationPermissionRequired()) {
            return false
        }
        
        if (hasNotificationPermission()) {
            return false
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deniedCount = prefs.getInt(KEY_PERMISSION_DENIED_COUNT, 0)
        val lastRequestTime = prefs.getLong(KEY_LAST_REQUEST_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        
        // Check if user has denied too many times
        if (deniedCount >= MAX_DENIAL_COUNT) {
            return false
        }
        
        // Check cooldown period
        if (currentTime - lastRequestTime < REQUEST_COOLDOWN_MS) {
            return false
        }
        
        return true
    }
    
    /**
     * Get permission request rationale
     */
    fun getPermissionRequestRationale(): PermissionRationale {
        val deniedCount = _permissionDeniedCount.value
        
        return when {
            deniedCount == 0 -> PermissionRationale(
                title = "Enable Notifications",
                message = "Ambient Scribe needs notification permission to show recording status and important updates.",
                showRationale = true,
                canRequest = true
            )
            deniedCount == 1 -> PermissionRationale(
                title = "Notifications Required",
                message = "Without notifications, you won't see recording status or important app updates. Please enable notifications in Settings.",
                showRationale = true,
                canRequest = true
            )
            deniedCount == 2 -> PermissionRationale(
                title = "Last Chance for Notifications",
                message = "This is your last chance to enable notifications. Without them, the app will have limited functionality.",
                showRationale = true,
                canRequest = true
            )
            else -> PermissionRationale(
                title = "Notifications Disabled",
                message = "Notifications have been permanently disabled. You can enable them manually in Settings.",
                showRationale = false,
                canRequest = false
            )
        }
    }
    
    /**
     * Handle permission request result
     */
    fun handlePermissionResult(granted: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        if (granted) {
            // Permission granted
            _hasNotificationPermission.value = true
            _permissionRequested.value = true
            _permissionDeniedCount.value = 0
            
            prefs.edit()
                .putBoolean(KEY_PERMISSION_REQUESTED, true)
                .putInt(KEY_PERMISSION_DENIED_COUNT, 0)
                .putLong(KEY_LAST_REQUEST_TIME, System.currentTimeMillis())
                .apply()
            
            logPermissionGranted()
        } else {
            // Permission denied
            val deniedCount = _permissionDeniedCount.value + 1
            _permissionDeniedCount.value = deniedCount
            _permissionRequested.value = true
            
            prefs.edit()
                .putBoolean(KEY_PERMISSION_REQUESTED, true)
                .putInt(KEY_PERMISSION_DENIED_COUNT, deniedCount)
                .putLong(KEY_LAST_REQUEST_TIME, System.currentTimeMillis())
                .apply()
            
            logPermissionDenied(deniedCount)
        }
    }
    
    /**
     * Get permission denial guidance
     */
    fun getPermissionDenialGuidance(): PermissionDenialGuidance {
        val deniedCount = _permissionDeniedCount.value
        val oemGuidance = oemPermissionPlaybook.getPermissionGuidance("POST_NOTIFICATIONS")
        
        return when {
            deniedCount < MAX_DENIAL_COUNT -> PermissionDenialGuidance(
                title = "Enable Notifications",
                message = "Please enable notifications to get the best experience with Ambient Scribe.",
                guidance = oemGuidance,
                canRetry = true,
                showSettings = true
            )
            else -> PermissionDenialGuidance(
                title = "Notifications Permanently Disabled",
                message = "Notifications have been permanently disabled. You can enable them manually in Settings if needed.",
                guidance = oemGuidance,
                canRetry = false,
                showSettings = true
            )
        }
    }
    
    /**
     * Check if foreground service notification will work
     */
    fun willForegroundServiceNotificationWork(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission()
        } else {
            true // No permission required for older versions
        }
    }
    
    /**
     * Get fallback notification strategy
     */
    fun getFallbackNotificationStrategy(): FallbackNotificationStrategy {
        return when {
            hasNotificationPermission() -> FallbackNotificationStrategy.NORMAL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> FallbackNotificationStrategy.REDUCED
            else -> FallbackNotificationStrategy.NORMAL
        }
    }
    
    /**
     * Get notification permission status
     */
    fun getNotificationPermissionStatus(): NotificationPermissionStatus {
        return NotificationPermissionStatus(
            hasPermission = hasNotificationPermission(),
            isRequired = isNotificationPermissionRequired(),
            canRequest = canRequestPermission(),
            deniedCount = _permissionDeniedCount.value,
            requested = _permissionRequested.value,
            rationale = getPermissionRequestRationale(),
            denialGuidance = getPermissionDenialGuidance(),
            fallbackStrategy = getFallbackNotificationStrategy()
        )
    }
    
    /**
     * Reset permission state (for testing)
     */
    fun resetPermissionState() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        _hasNotificationPermission.value = hasNotificationPermission()
        _permissionRequested.value = false
        _permissionDeniedCount.value = 0
    }
    
    /**
     * Log permission granted
     */
    private fun logPermissionGranted() {
        android.util.Log.i("NotificationPermissionManager", "Notification permission granted")
    }
    
    /**
     * Log permission denied
     */
    private fun logPermissionDenied(deniedCount: Int) {
        android.util.Log.w("NotificationPermissionManager", "Notification permission denied (count: $deniedCount)")
    }
    
    /**
     * Permission rationale data class
     */
    data class PermissionRationale(
        val title: String,
        val message: String,
        val showRationale: Boolean,
        val canRequest: Boolean
    )
    
    /**
     * Permission denial guidance data class
     */
    data class PermissionDenialGuidance(
        val title: String,
        val message: String,
        val guidance: OEMPermissionPlaybook.PermissionGuidance,
        val canRetry: Boolean,
        val showSettings: Boolean
    )
    
    /**
     * Notification permission status data class
     */
    data class NotificationPermissionStatus(
        val hasPermission: Boolean,
        val isRequired: Boolean,
        val canRequest: Boolean,
        val deniedCount: Int,
        val requested: Boolean,
        val rationale: PermissionRationale,
        val denialGuidance: PermissionDenialGuidance,
        val fallbackStrategy: FallbackNotificationStrategy
    )
    
    /**
     * Fallback notification strategy enum
     */
    enum class FallbackNotificationStrategy {
        NORMAL,
        REDUCED,
        DISABLED
    }
}
