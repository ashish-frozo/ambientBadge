package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Upload Policy Manager for clinic-level upload settings
 * 
 * Manages upload policies for:
 * - Wi-Fi only vs metered OK settings
 * - WorkManager constraint enforcement
 * - Network condition monitoring
 * - Upload queue management
 * 
 * Features:
 * - Clinic-level policy configuration
 * - Network condition detection
 * - WorkManager constraint integration
 * - Upload queue management
 * - Policy compliance monitoring
 */
class UploadPolicyManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREFS_NAME = "upload_policy"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_METERED_OK = "metered_ok"
        private const val KEY_CLINIC_ID = "clinic_id"
        private const val KEY_POLICY_VERSION = "policy_version"
        private const val KEY_LAST_UPDATE = "last_update"
        
        // Default values
        private const val DEFAULT_WIFI_ONLY = true
        private const val DEFAULT_METERED_OK = false
        private const val DEFAULT_POLICY_VERSION = "1.0.0"
        
        @Volatile
        private var INSTANCE: UploadPolicyManager? = null
        
        fun getInstance(context: Context): UploadPolicyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UploadPolicyManager(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Upload policy state
    private val _wifiOnly = MutableStateFlow(
        prefs.getBoolean(KEY_WIFI_ONLY, DEFAULT_WIFI_ONLY)
    )
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()
    
    private val _meteredOk = MutableStateFlow(
        prefs.getBoolean(KEY_METERED_OK, DEFAULT_METERED_OK)
    )
    val meteredOk: StateFlow<Boolean> = _meteredOk.asStateFlow()
    
    private val _clinicId = MutableStateFlow(
        prefs.getString(KEY_CLINIC_ID, "") ?: ""
    )
    val clinicId: StateFlow<String> = _clinicId.asStateFlow()
    
    private val _policyVersion = MutableStateFlow(
        prefs.getString(KEY_POLICY_VERSION, DEFAULT_POLICY_VERSION) ?: DEFAULT_POLICY_VERSION
    )
    val policyVersion: StateFlow<String> = _policyVersion.asStateFlow()
    
    // Network monitoring
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Set upload policy for clinic
     */
    fun setUploadPolicy(
        clinicId: String,
        wifiOnly: Boolean,
        meteredOk: Boolean,
        policyVersion: String = "1.0.0"
    ) {
        _clinicId.value = clinicId
        _wifiOnly.value = wifiOnly
        _meteredOk.value = meteredOk
        _policyVersion.value = policyVersion
        
        // Save to preferences
        prefs.edit()
            .putString(KEY_CLINIC_ID, clinicId)
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .putBoolean(KEY_METERED_OK, meteredOk)
            .putString(KEY_POLICY_VERSION, policyVersion)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
        
        logPolicyUpdated(clinicId, wifiOnly, meteredOk, policyVersion)
    }
    
    /**
     * Check if upload is allowed based on current network conditions
     */
    fun isUploadAllowed(): UploadAllowedResult {
        return try {
            val networkInfo = getCurrentNetworkInfo()
            
            when {
                networkInfo.isWifi -> {
                    // WiFi connection - always allowed
                    UploadAllowedResult.ALLOWED("WiFi connection detected")
                }
                networkInfo.isMetered && _meteredOk.value -> {
                    // Metered connection and metered OK
                    UploadAllowedResult.ALLOWED("Metered connection allowed by policy")
                }
                networkInfo.isMetered && !_meteredOk.value -> {
                    // Metered connection but not allowed
                    UploadAllowedResult.BLOCKED("Metered connection not allowed by policy")
                }
                !networkInfo.isConnected -> {
                    // No connection
                    UploadAllowedResult.BLOCKED("No network connection")
                }
                else -> {
                    // Other connection type
                    UploadAllowedResult.BLOCKED("Unsupported connection type")
                }
            }
        } catch (e: Exception) {
            logError("Failed to check upload allowance", e)
            UploadAllowedResult.BLOCKED("Error checking network conditions")
        }
    }
    
    /**
     * Get WorkManager constraints based on current policy
     */
    fun getWorkManagerConstraints(): WorkManagerConstraints {
        return WorkManagerConstraints(
            requiresWifi = _wifiOnly.value,
            allowsMetered = _meteredOk.value,
            requiresCharging = false,
            requiresBatteryNotLow = true,
            requiresDeviceIdle = false
        )
    }
    
    /**
     * Get current network information
     */
    private fun getCurrentNetworkInfo(): NetworkInfo {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            if (capabilities == null) {
                return NetworkInfo(
                    isConnected = false,
                    isWifi = false,
                    isMetered = false,
                    connectionType = "none"
                )
            }
            
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            
            val connectionType = when {
                isWifi -> "wifi"
                isCellular -> "cellular"
                isEthernet -> "ethernet"
                else -> "unknown"
            }
            
            NetworkInfo(
                isConnected = true,
                isWifi = isWifi,
                isMetered = isMetered,
                connectionType = connectionType
            )
        } catch (e: Exception) {
            logError("Failed to get network info", e)
            NetworkInfo(
                isConnected = false,
                isWifi = false,
                isMetered = false,
                connectionType = "error"
            )
        }
    }
    
    /**
     * Get upload policy status
     */
    fun getUploadPolicyStatus(): UploadPolicyStatus {
        val networkInfo = getCurrentNetworkInfo()
        val uploadAllowed = isUploadAllowed()
        
        return UploadPolicyStatus(
            clinicId = _clinicId.value,
            wifiOnly = _wifiOnly.value,
            meteredOk = _meteredOk.value,
            policyVersion = _policyVersion.value,
            networkInfo = networkInfo,
            uploadAllowed = uploadAllowed,
            workManagerConstraints = getWorkManagerConstraints(),
            lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        )
    }
    
    /**
     * Validate policy compliance
     */
    fun validatePolicyCompliance(): PolicyComplianceResult {
        val issues = mutableListOf<String>()
        
        try {
            // Check if policy is set
            if (_clinicId.value.isEmpty()) {
                issues.add("No clinic ID set")
            }
            
            // Check for conflicting settings
            if (_wifiOnly.value && _meteredOk.value) {
                issues.add("Conflicting settings: WiFi only and metered OK both enabled")
            }
            
            // Check network conditions
            val networkInfo = getCurrentNetworkInfo()
            if (!networkInfo.isConnected) {
                issues.add("No network connection available")
            }
            
            // Check if current network meets policy requirements
            val uploadAllowed = isUploadAllowed()
            if (uploadAllowed is UploadAllowedResult.BLOCKED) {
                issues.add("Current network does not meet policy requirements: ${uploadAllowed.reason}")
            }
            
        } catch (e: Exception) {
            issues.add("Error validating policy compliance: ${e.message}")
        }
        
        return PolicyComplianceResult(
            isCompliant = issues.isEmpty(),
            issues = issues
        )
    }
    
    /**
     * Get upload queue status
     */
    fun getUploadQueueStatus(): UploadQueueStatus {
        // This would integrate with actual upload queue monitoring
        return UploadQueueStatus(
            queueSize = 0,
            pendingUploads = 0,
            failedUploads = 0,
            lastUploadTime = 0L,
            isProcessing = false
        )
    }
    
    /**
     * Log policy updated
     */
    private fun logPolicyUpdated(
        clinicId: String,
        wifiOnly: Boolean,
        meteredOk: Boolean,
        policyVersion: String
    ) {
        android.util.Log.i(
            "UploadPolicyManager",
            "Upload policy updated: clinic=$clinicId, wifiOnly=$wifiOnly, meteredOk=$meteredOk, version=$policyVersion"
        )
    }
    
    /**
     * Log error
     */
    private fun logError(message: String, throwable: Throwable) {
        android.util.Log.e("UploadPolicyManager", message, throwable)
    }
    
    /**
     * Upload allowed result
     */
    sealed class UploadAllowedResult {
        data class ALLOWED(val reason: String) : UploadAllowedResult()
        data class BLOCKED(val reason: String) : UploadAllowedResult()
    }
    
    /**
     * Network information
     */
    data class NetworkInfo(
        val isConnected: Boolean,
        val isWifi: Boolean,
        val isMetered: Boolean,
        val connectionType: String
    )
    
    /**
     * WorkManager constraints
     */
    data class WorkManagerConstraints(
        val requiresWifi: Boolean,
        val allowsMetered: Boolean,
        val requiresCharging: Boolean,
        val requiresBatteryNotLow: Boolean,
        val requiresDeviceIdle: Boolean
    )
    
    /**
     * Upload policy status
     */
    data class UploadPolicyStatus(
        val clinicId: String,
        val wifiOnly: Boolean,
        val meteredOk: Boolean,
        val policyVersion: String,
        val networkInfo: NetworkInfo,
        val uploadAllowed: UploadAllowedResult,
        val workManagerConstraints: WorkManagerConstraints,
        val lastUpdate: Long
    )
    
    /**
     * Policy compliance result
     */
    data class PolicyComplianceResult(
        val isCompliant: Boolean,
        val issues: List<String>
    )
    
    /**
     * Upload queue status
     */
    data class UploadQueueStatus(
        val queueSize: Int,
        val pendingUploads: Int,
        val failedUploads: Int,
        val lastUploadTime: Long,
        val isProcessing: Boolean
    )
}
