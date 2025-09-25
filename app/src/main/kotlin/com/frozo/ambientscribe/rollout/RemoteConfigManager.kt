package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Remote Config Manager for signed configuration updates
 * 
 * Manages remote configuration with:
 * - Ed25519 signature verification
 * - Fail-closed on bad signature
 * - Configuration caching and validation
 * - Rollback on signature failure
 * 
 * Features:
 * - Ed25519 signature verification
 * - Configuration validation
 * - Caching and persistence
 * - Fail-safe behavior
 * - Rollback capabilities
 */
class RemoteConfigManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREFS_NAME = "remote_config"
        private const val KEY_CONFIG_DATA = "config_data"
        private const val KEY_CONFIG_SIGNATURE = "config_signature"
        private const val KEY_CONFIG_VERSION = "config_version"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_PUBLIC_KEY = "public_key"
        
        // Signature algorithm
        private const val SIGNATURE_ALGORITHM = "Ed25519"
        
        // Default public key (in production, this would be embedded in the app)
        private const val DEFAULT_PUBLIC_KEY = "MCowBQYDK2VwAyEA" // Base64 encoded Ed25519 public key
        
        @Volatile
        private var INSTANCE: RemoteConfigManager? = null
        
        fun getInstance(context: Context): RemoteConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteConfigManager(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Configuration state
    private val _configData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val configData: StateFlow<Map<String, Any>> = _configData.asStateFlow()
    
    private val _configVersion = MutableStateFlow(
        prefs.getString(KEY_CONFIG_VERSION, "1.0.0") ?: "1.0.0"
    )
    val configVersion: StateFlow<String> = _configVersion.asStateFlow()
    
    private val _isConfigValid = MutableStateFlow(false)
    val isConfigValid: StateFlow<Boolean> = _isConfigValid.asStateFlow()
    
    // Atomic operations
    private val isUpdating = AtomicBoolean(false)
    
    // Public key for signature verification
    private var publicKey: PublicKey? = null
    
    init {
        loadStoredConfig()
        initializePublicKey()
    }
    
    /**
     * Initialize public key for signature verification
     */
    private fun initializePublicKey() {
        try {
            val keyBytes = android.util.Base64.decode(DEFAULT_PUBLIC_KEY, android.util.Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(SIGNATURE_ALGORITHM)
            publicKey = keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            logError("Failed to initialize public key", e)
            publicKey = null
        }
    }
    
    /**
     * Load stored configuration
     */
    private fun loadStoredConfig() {
        try {
            val configJson = prefs.getString(KEY_CONFIG_DATA, "{}")
            val signature = prefs.getString(KEY_CONFIG_SIGNATURE, "")
            val version = prefs.getString(KEY_CONFIG_VERSION, "1.0.0") ?: "1.0.0"
            
            if (configJson != null && signature != null) {
                val config = parseConfigJson(configJson)
                val isValid = verifySignature(configJson, signature)
                
                if (isValid) {
                    _configData.value = config
                    _configVersion.value = version
                    _isConfigValid.value = true
                } else {
                    logError("Stored configuration signature invalid", null)
                    _isConfigValid.value = false
                }
            }
        } catch (e: Exception) {
            logError("Failed to load stored configuration", e)
            _isConfigValid.value = false
        }
    }
    
    /**
     * Update configuration from remote source
     */
    suspend fun updateConfig(
        configJson: String,
        signature: String,
        version: String
    ): ConfigUpdateResult {
        if (isUpdating.get()) {
            return ConfigUpdateResult.ALREADY_UPDATING
        }
        
        if (!isUpdating.compareAndSet(false, true)) {
            return ConfigUpdateResult.ALREADY_UPDATING
        }
        
        try {
            // Verify signature
            val signatureValid = verifySignature(configJson, signature)
            if (!signatureValid) {
                logError("Configuration signature verification failed", null)
                return ConfigUpdateResult.INVALID_SIGNATURE
            }
            
            // Parse configuration
            val config = parseConfigJson(configJson)
            if (config.isEmpty()) {
                return ConfigUpdateResult.INVALID_CONFIG
            }
            
            // Validate configuration
            val validationResult = validateConfig(config)
            if (!validationResult.isValid) {
                return ConfigUpdateResult.INVALID_CONFIG
            }
            
            // Update configuration
            _configData.value = config
            _configVersion.value = version
            _isConfigValid.value = true
            
            // Save to preferences
            saveConfig(configJson, signature, version)
            
            logConfigUpdated(version)
            return ConfigUpdateResult.SUCCESS
            
        } catch (e: Exception) {
            logError("Failed to update configuration", e)
            return ConfigUpdateResult.ERROR(e.message ?: "Unknown error")
        } finally {
            isUpdating.set(false)
        }
    }
    
    /**
     * Verify Ed25519 signature
     */
    private fun verifySignature(data: String, signature: String): Boolean {
        return try {
            if (publicKey == null) {
                logError("Public key not initialized", null)
                return false
            }
            
            val signatureBytes = android.util.Base64.decode(signature, android.util.Base64.DEFAULT)
            val dataBytes = data.toByteArray()
            
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)
            sig.update(dataBytes)
            
            val isValid = sig.verify(signatureBytes)
            if (!isValid) {
                logError("Signature verification failed", null)
            }
            
            isValid
        } catch (e: Exception) {
            logError("Error verifying signature", e)
            false
        }
    }
    
    /**
     * Parse configuration JSON
     */
    private fun parseConfigJson(configJson: String): Map<String, Any> {
        return try {
            // Simple JSON parsing (in production, use a proper JSON library)
            val config = mutableMapOf<String, Any>()
            
            // Extract key-value pairs (simplified implementation)
            val regex = "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            regex.findAll(configJson).forEach { matchResult ->
                val key = matchResult.groupValues[1]
                val value = matchResult.groupValues[2]
                config[key] = value
            }
            
            config
        } catch (e: Exception) {
            logError("Failed to parse configuration JSON", e)
            emptyMap()
        }
    }
    
    /**
     * Validate configuration
     */
    private fun validateConfig(config: Map<String, Any>): ConfigValidationResult {
        val issues = mutableListOf<String>()
        
        try {
            // Check required fields
            val requiredFields = listOf("version", "timestamp", "features")
            for (field in requiredFields) {
                if (!config.containsKey(field)) {
                    issues.add("Missing required field: $field")
                }
            }
            
            // Validate version format
            val version = config["version"] as? String
            if (version != null && !version.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
                issues.add("Invalid version format: $version")
            }
            
            // Validate timestamp
            val timestamp = config["timestamp"] as? String
            if (timestamp != null) {
                try {
                    val timestampLong = timestamp.toLong()
                    val currentTime = System.currentTimeMillis()
                    val timeDiff = Math.abs(currentTime - timestampLong)
                    
                    // Check if timestamp is within reasonable range (1 hour)
                    if (timeDiff > 60 * 60 * 1000) {
                        issues.add("Timestamp too old: $timestamp")
                    }
                } catch (e: Exception) {
                    issues.add("Invalid timestamp format: $timestamp")
                }
            }
            
            // Validate features
            val features = config["features"] as? Map<String, Any>
            if (features != null) {
                for ((key, value) in features) {
                    if (value !is Boolean) {
                        issues.add("Feature value must be boolean: $key")
                    }
                }
            }
            
        } catch (e: Exception) {
            issues.add("Error validating configuration: ${e.message}")
        }
        
        return ConfigValidationResult(issues.isEmpty(), issues)
    }
    
    /**
     * Save configuration to preferences
     */
    private fun saveConfig(configJson: String, signature: String, version: String) {
        prefs.edit()
            .putString(KEY_CONFIG_DATA, configJson)
            .putString(KEY_CONFIG_SIGNATURE, signature)
            .putString(KEY_CONFIG_VERSION, version)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Get configuration value
     */
    fun getConfigValue(key: String, defaultValue: Any? = null): Any? {
        return if (_isConfigValid.value) {
            _configData.value[key] ?: defaultValue
        } else {
            defaultValue
        }
    }
    
    /**
     * Get boolean configuration value
     */
    fun getBooleanConfig(key: String, defaultValue: Boolean = false): Boolean {
        return when (val value = getConfigValue(key)) {
            is Boolean -> value
            is String -> value.toBoolean()
            else -> defaultValue
        }
    }
    
    /**
     * Get string configuration value
     */
    fun getStringConfig(key: String, defaultValue: String = ""): String {
        return when (val value = getConfigValue(key)) {
            is String -> value
            else -> defaultValue
        }
    }
    
    /**
     * Get integer configuration value
     */
    fun getIntConfig(key: String, defaultValue: Int = 0): Int {
        return when (val value = getConfigValue(key)) {
            is Int -> value
            is String -> value.toIntOrNull() ?: defaultValue
            else -> defaultValue
        }
    }
    
    /**
     * Check if configuration is valid
     */
    fun isConfigurationValid(): Boolean {
        return _isConfigValid.value
    }
    
    /**
     * Get configuration status
     */
    fun getConfigurationStatus(): ConfigurationStatus {
        return ConfigurationStatus(
            isValid = _isConfigValid.value,
            version = _configVersion.value,
            data = _configData.value,
            lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L),
            isUpdating = isUpdating.get()
        )
    }
    
    /**
     * Reset configuration to defaults
     */
    fun resetToDefaults() {
        _configData.value = emptyMap()
        _configVersion.value = "1.0.0"
        _isConfigValid.value = false
        
        prefs.edit()
            .remove(KEY_CONFIG_DATA)
            .remove(KEY_CONFIG_SIGNATURE)
            .remove(KEY_CONFIG_VERSION)
            .remove(KEY_LAST_UPDATE)
            .apply()
        
        logConfigReset()
    }
    
    /**
     * Log configuration updated
     */
    private fun logConfigUpdated(version: String) {
        android.util.Log.i("RemoteConfigManager", "Configuration updated to version: $version")
    }
    
    /**
     * Log configuration reset
     */
    private fun logConfigReset() {
        android.util.Log.i("RemoteConfigManager", "Configuration reset to defaults")
    }
    
    /**
     * Log error
     */
    private fun logError(message: String, throwable: Throwable?) {
        android.util.Log.e("RemoteConfigManager", message, throwable)
    }
    
    /**
     * Config update result
     */
    sealed class ConfigUpdateResult {
        object ALREADY_UPDATING : ConfigUpdateResult()
        object INVALID_SIGNATURE : ConfigUpdateResult()
        object INVALID_CONFIG : ConfigUpdateResult()
        object SUCCESS : ConfigUpdateResult()
        data class ERROR(val message: String) : ConfigUpdateResult()
    }
    
    /**
     * Config validation result
     */
    data class ConfigValidationResult(
        val isValid: Boolean,
        val issues: List<String>
    )
    
    /**
     * Configuration status
     */
    data class ConfigurationStatus(
        val isValid: Boolean,
        val version: String,
        val data: Map<String, Any>,
        val lastUpdate: Long,
        val isUpdating: Boolean
    )
}
