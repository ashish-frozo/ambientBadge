package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Device Allowlist Manager for pilot phases
 * 
 * Manages device allowlists for:
 * - Pilot phase 1 (internal testing)
 * - Pilot phase 2 (limited external testing)
 * - Pilot phase 3 (expanded testing)
 * - Production rollout
 * 
 * Features:
 * - Device fingerprinting
 * - Pilot phase management
 * - Remote allowlist updates
 * - Graceful degradation for non-allowlisted devices
 * - Audit logging for allowlist checks
 */
class DeviceAllowlistManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREFS_NAME = "device_allowlist"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PILOT_PHASE = "pilot_phase"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_ALLOWLIST_VERSION = "allowlist_version"
        
        // Pilot phases
        const val PHASE_INTERNAL = "internal"
        const val PHASE_PILOT_1 = "pilot_1"
        const val PHASE_PILOT_2 = "pilot_2"
        const val PHASE_PILOT_3 = "pilot_3"
        const val PHASE_PRODUCTION = "production"
        
        // Default values
        private const val DEFAULT_PILOT_PHASE = PHASE_INTERNAL
        
        @Volatile
        private var INSTANCE: DeviceAllowlistManager? = null
        
        fun getInstance(context: Context): DeviceAllowlistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceAllowlistManager(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Device information
    private val deviceId: String
    private val deviceFingerprint: String
    
    // Pilot phase state
    private val _currentPilotPhase = MutableStateFlow(
        prefs.getString(KEY_PILOT_PHASE, DEFAULT_PILOT_PHASE) ?: DEFAULT_PILOT_PHASE
    )
    val currentPilotPhase: StateFlow<String> = _currentPilotPhase.asStateFlow()
    
    // Allowlist data
    private val allowlists = ConcurrentHashMap<String, Set<String>>()
    private var lastUpdate: Long = 0L
    private var allowlistVersion: String = "1.0.0"
    
    init {
        deviceId = generateDeviceId()
        deviceFingerprint = generateDeviceFingerprint()
        loadStoredValues()
    }
    
    /**
     * Generate unique device ID
     */
    private fun generateDeviceId(): String {
        val storedId = prefs.getString(KEY_DEVICE_ID, null)
        if (storedId != null) {
            return storedId
        }
        
        // Generate new device ID based on device characteristics
        val deviceId = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.FINGERPRINT.hashCode()}"
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        return deviceId
    }
    
    /**
     * Generate device fingerprint
     */
    private fun generateDeviceFingerprint(): String {
        val fingerprint = "${Build.MANUFACTURER}|${Build.MODEL}|${Build.VERSION.RELEASE}|${Build.FINGERPRINT}"
        return fingerprint.hashCode().toString()
    }
    
    /**
     * Load stored values
     */
    private fun loadStoredValues() {
        lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        allowlistVersion = prefs.getString(KEY_ALLOWLIST_VERSION, "1.0.0") ?: "1.0.0"
    }
    
    /**
     * Check if device is allowed for current pilot phase
     */
    fun isDeviceAllowed(): Boolean {
        return try {
            val phase = _currentPilotPhase.value
            when (phase) {
                PHASE_INTERNAL -> isDeviceInAllowlist(PHASE_INTERNAL)
                PHASE_PILOT_1 -> isDeviceInAllowlist(PHASE_PILOT_1)
                PHASE_PILOT_2 -> isDeviceInAllowlist(PHASE_PILOT_2)
                PHASE_PILOT_3 -> isDeviceInAllowlist(PHASE_PILOT_3)
                PHASE_PRODUCTION -> true // All devices allowed in production
                else -> false
            }
        } catch (e: Exception) {
            logError("Failed to check device allowlist", e)
            false
        }
    }
    
    /**
     * Check if device is in specific allowlist
     */
    private fun isDeviceInAllowlist(phase: String): Boolean {
        val allowlist = allowlists[phase] ?: return false
        return allowlist.contains(deviceId) || allowlist.contains(deviceFingerprint)
    }
    
    /**
     * Get current pilot phase
     */
    fun getCurrentPilotPhase(): String {
        return _currentPilotPhase.value
    }
    
    /**
     * Set pilot phase
     */
    fun setPilotPhase(phase: String, source: String = "manual") {
        try {
            _currentPilotPhase.value = phase
            prefs.edit().putString(KEY_PILOT_PHASE, phase).apply()
            
            logPilotPhaseChange(phase, source)
        } catch (e: Exception) {
            logError("Failed to set pilot phase", e)
        }
    }
    
    /**
     * Update allowlists from remote source
     */
    suspend fun updateAllowlists(allowlistData: Map<String, List<String>>) {
        try {
            allowlistData.forEach { (phase, devices) ->
                allowlists[phase] = devices.toSet()
            }
            
            lastUpdate = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_LAST_UPDATE, lastUpdate)
                .apply()
            
            logAllowlistUpdate(allowlistData.keys.size)
        } catch (e: Exception) {
            logError("Failed to update allowlists", e)
        }
    }
    
    /**
     * Add device to allowlist
     */
    fun addDeviceToAllowlist(phase: String, deviceId: String) {
        try {
            val currentAllowlist = allowlists[phase]?.toMutableSet() ?: mutableSetOf()
            currentAllowlist.add(deviceId)
            allowlists[phase] = currentAllowlist
            
            logDeviceAddedToAllowlist(phase, deviceId)
        } catch (e: Exception) {
            logError("Failed to add device to allowlist", e)
        }
    }
    
    /**
     * Remove device from allowlist
     */
    fun removeDeviceFromAllowlist(phase: String, deviceId: String) {
        try {
            val currentAllowlist = allowlists[phase]?.toMutableSet() ?: mutableSetOf()
            currentAllowlist.remove(deviceId)
            allowlists[phase] = currentAllowlist
            
            logDeviceRemovedFromAllowlist(phase, deviceId)
        } catch (e: Exception) {
            logError("Failed to remove device from allowlist", e)
        }
    }
    
    /**
     * Get device information
     */
    fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "device_id" to deviceId,
            "device_fingerprint" to deviceFingerprint,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT,
            "current_pilot_phase" to _currentPilotPhase.value,
            "is_allowed" to isDeviceAllowed(),
            "last_update" to lastUpdate,
            "allowlist_version" to allowlistVersion
        )
    }
    
    /**
     * Get allowlist status for all phases
     */
    fun getAllowlistStatus(): Map<String, Any> {
        return mapOf(
            "current_phase" to _currentPilotPhase.value,
            "is_allowed" to isDeviceAllowed(),
            "allowlists" to allowlists.mapValues { it.value.size },
            "last_update" to lastUpdate,
            "allowlist_version" to allowlistVersion
        )
    }
    
    /**
     * Check if device meets minimum requirements
     */
    fun checkDeviceRequirements(): DeviceRequirementStatus {
        return try {
            val requirements = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            // Check Android version
            if (Build.VERSION.SDK_INT < 29) {
                requirements.add("Android 10+ required")
            }
            
            // Check RAM
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
            if (maxMemory < 2048) {
                warnings.add("Low RAM: ${maxMemory}MB (recommended: 2GB+)")
            }
            
            // Check storage
            val storage = context.getExternalFilesDir(null)?.totalSpace ?: 0
            val storageGB = storage / (1024 * 1024 * 1024)
            if (storageGB < 4) {
                warnings.add("Low storage: ${storageGB}GB (recommended: 4GB+)")
            }
            
            DeviceRequirementStatus(
                meetsRequirements = requirements.isEmpty(),
                requirements = requirements,
                warnings = warnings
            )
        } catch (e: Exception) {
            logError("Failed to check device requirements", e)
            DeviceRequirementStatus(
                meetsRequirements = false,
                requirements = listOf("Error checking requirements"),
                warnings = emptyList()
            )
        }
    }
    
    /**
     * Log pilot phase change
     */
    private fun logPilotPhaseChange(phase: String, source: String) {
        android.util.Log.i("DeviceAllowlistManager", "Pilot phase changed to: $phase (source: $source)")
    }
    
    /**
     * Log allowlist update
     */
    private fun logAllowlistUpdate(phaseCount: Int) {
        android.util.Log.i("DeviceAllowlistManager", "Allowlists updated: $phaseCount phases")
    }
    
    /**
     * Log device added to allowlist
     */
    private fun logDeviceAddedToAllowlist(phase: String, deviceId: String) {
        android.util.Log.i("DeviceAllowlistManager", "Device added to allowlist: $deviceId (phase: $phase)")
    }
    
    /**
     * Log device removed from allowlist
     */
    private fun logDeviceRemovedFromAllowlist(phase: String, deviceId: String) {
        android.util.Log.i("DeviceAllowlistManager", "Device removed from allowlist: $deviceId (phase: $phase)")
    }
    
    /**
     * Log error
     */
    private fun logError(message: String, throwable: Throwable) {
        android.util.Log.e("DeviceAllowlistManager", message, throwable)
    }
    
    /**
     * Device requirement status
     */
    data class DeviceRequirementStatus(
        val meetsRequirements: Boolean,
        val requirements: List<String>,
        val warnings: List<String>
    )
}
