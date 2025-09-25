package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OEM Permission Playbook for device-specific permission handling
 * 
 * Provides device-specific guidance for:
 * - MIUI (Xiaomi) permission handling
 * - Samsung One UI permission handling
 * - Oppo ColorOS permission handling
 * - Vivo FuntouchOS permission handling
 * - OnePlus OxygenOS permission handling
 * 
 * Features:
 * - Device detection
 * - OEM-specific permission flows
 * - In-app help integration
 * - Permission denial recovery
 * - User guidance and tutorials
 */
class OEMPermissionPlaybook private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var INSTANCE: OEMPermissionPlaybook? = null
        
        fun getInstance(context: Context): OEMPermissionPlaybook {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OEMPermissionPlaybook(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Current OEM state
    private val _currentOEM = MutableStateFlow(detectOEM())
    val currentOEM: StateFlow<String> = _currentOEM.asStateFlow()
    
    // Permission states
    private val _permissionStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionStates: StateFlow<Map<String, Boolean>> = _permissionStates.asStateFlow()
    
    /**
     * Detect current OEM
     */
    private fun detectOEM(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") -> "xiaomi"
            manufacturer.contains("samsung") || brand.contains("samsung") -> "samsung"
            manufacturer.contains("oppo") || brand.contains("oppo") -> "oppo"
            manufacturer.contains("vivo") || brand.contains("vivo") -> "vivo"
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> "oneplus"
            manufacturer.contains("huawei") || brand.contains("huawei") -> "huawei"
            manufacturer.contains("honor") || brand.contains("honor") -> "honor"
            manufacturer.contains("realme") || brand.contains("realme") -> "realme"
            manufacturer.contains("motorola") || brand.contains("motorola") -> "motorola"
            manufacturer.contains("lg") || brand.contains("lg") -> "lg"
            else -> "generic"
        }
    }
    
    /**
     * Get permission guidance for current OEM
     */
    fun getPermissionGuidance(permission: String): PermissionGuidance {
        return when (_currentOEM.value) {
            "xiaomi" -> getXiaomiGuidance(permission)
            "samsung" -> getSamsungGuidance(permission)
            "oppo" -> getOppoGuidance(permission)
            "vivo" -> getVivoGuidance(permission)
            "oneplus" -> getOnePlusGuidance(permission)
            "huawei" -> getHuaweiGuidance(permission)
            "honor" -> getHonorGuidance(permission)
            "realme" -> getRealmeGuidance(permission)
            "motorola" -> getMotorolaGuidance(permission)
            "lg" -> getLGGuidance(permission)
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get Xiaomi (MIUI) permission guidance
     */
    private fun getXiaomiGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on MIUI",
                steps = listOf(
                    "1. Go to Settings > Apps > Manage apps",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission",
                    "5. Also enable 'Autostart' for background recording",
                    "6. Go to Settings > Battery & performance > App battery saver",
                    "7. Select 'Ambient Scribe' and set to 'No restrictions'"
                ),
                settingsIntent = createXiaomiSettingsIntent(),
                helpUrl = "https://support.mi.com/global/help/faq/faq-1/faq-1-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on MIUI",
                steps = listOf(
                    "1. Go to Settings > Apps > Manage apps",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'",
                    "5. Set notification importance to 'High'"
                ),
                settingsIntent = createXiaomiSettingsIntent(),
                helpUrl = "https://support.mi.com/global/help/faq/faq-1/faq-1-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get Samsung (One UI) permission guidance
     */
    private fun getSamsungGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on Samsung",
                steps = listOf(
                    "1. Go to Settings > Apps",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission",
                    "5. Go to Settings > Device care > Battery",
                    "6. Tap 'App power management'",
                    "7. Find 'Ambient Scribe' and set to 'Unrestricted'"
                ),
                settingsIntent = createSamsungSettingsIntent(),
                helpUrl = "https://www.samsung.com/us/support/answer/ANS00000000"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on Samsung",
                steps = listOf(
                    "1. Go to Settings > Apps",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'",
                    "5. Set notification style to 'Detailed'"
                ),
                settingsIntent = createSamsungSettingsIntent(),
                helpUrl = "https://www.samsung.com/us/support/answer/ANS00000001"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get Oppo (ColorOS) permission guidance
     */
    private fun getOppoGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on Oppo",
                steps = listOf(
                    "1. Go to Settings > Apps > App management",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission",
                    "5. Go to Settings > Battery > App battery management",
                    "6. Find 'Ambient Scribe' and set to 'Allow background activity'"
                ),
                settingsIntent = createOppoSettingsIntent(),
                helpUrl = "https://www.oppo.com/en/support/faq/faq-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on Oppo",
                steps = listOf(
                    "1. Go to Settings > Apps > App management",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'",
                    "5. Set notification importance to 'High'"
                ),
                settingsIntent = createOppoSettingsIntent(),
                helpUrl = "https://www.oppo.com/en/support/faq/faq-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get Vivo (FuntouchOS) permission guidance
     */
    private fun getVivoGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on Vivo",
                steps = listOf(
                    "1. Go to Settings > Apps > App management",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission",
                    "5. Go to Settings > Battery > Background app management",
                    "6. Find 'Ambient Scribe' and set to 'Allow background activity'"
                ),
                settingsIntent = createVivoSettingsIntent(),
                helpUrl = "https://www.vivo.com/en/support/faq/faq-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on Vivo",
                steps = listOf(
                    "1. Go to Settings > Apps > App management",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'",
                    "5. Set notification importance to 'High'"
                ),
                settingsIntent = createVivoSettingsIntent(),
                helpUrl = "https://www.vivo.com/en/support/faq/faq-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get OnePlus (OxygenOS) permission guidance
     */
    private fun getOnePlusGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on OnePlus",
                steps = listOf(
                    "1. Go to Settings > Apps & notifications > App info",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission",
                    "5. Go to Settings > Battery > Battery optimization",
                    "6. Find 'Ambient Scribe' and set to 'Don't optimize'"
                ),
                settingsIntent = createOnePlusSettingsIntent(),
                helpUrl = "https://www.oneplus.com/support/faq/faq-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on OnePlus",
                steps = listOf(
                    "1. Go to Settings > Apps & notifications > App info",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'",
                    "5. Set notification importance to 'High'"
                ),
                settingsIntent = createOnePlusSettingsIntent(),
                helpUrl = "https://www.oneplus.com/support/faq/faq-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get Huawei permission guidance
     */
    private fun getHuaweiGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on Huawei",
                steps = listOf(
                    "1. Go to Settings > Apps > App permissions",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Enable 'Microphone' permission",
                    "4. Go to Settings > Battery > App launch",
                    "5. Find 'Ambient Scribe' and set to 'Manual'"
                ),
                settingsIntent = createHuaweiSettingsIntent(),
                helpUrl = "https://consumer.huawei.com/en/support/faq/faq-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on Huawei",
                steps = listOf(
                    "1. Go to Settings > Apps > App permissions",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Enable 'Notifications' permission",
                    "4. Set notification importance to 'High'"
                ),
                settingsIntent = createHuaweiSettingsIntent(),
                helpUrl = "https://consumer.huawei.com/en/support/faq/faq-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get Honor permission guidance
     */
    private fun getHonorGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on Honor",
                steps = listOf(
                    "1. Go to Settings > Apps > App permissions",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Enable 'Microphone' permission",
                    "4. Go to Settings > Battery > App launch",
                    "5. Find 'Ambient Scribe' and set to 'Manual'"
                ),
                settingsIntent = createHonorSettingsIntent(),
                helpUrl = "https://www.hihonor.com/en/support/faq/faq-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on Honor",
                steps = listOf(
                    "1. Go to Settings > Apps > App permissions",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Enable 'Notifications' permission",
                    "4. Set notification importance to 'High'"
                ),
                settingsIntent = createHonorSettingsIntent(),
                helpUrl = "https://www.hihonor.com/en/support/faq/faq-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get Realme permission guidance
     */
    private fun getRealmeGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on Realme",
                steps = listOf(
                    "1. Go to Settings > Apps > App management",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission",
                    "5. Go to Settings > Battery > App battery management",
                    "6. Find 'Ambient Scribe' and set to 'Allow background activity'"
                ),
                settingsIntent = createRealmeSettingsIntent(),
                helpUrl = "https://www.realme.com/en/support/faq/faq-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on Realme",
                steps = listOf(
                    "1. Go to Settings > Apps > App management",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'",
                    "5. Set notification importance to 'High'"
                ),
                settingsIntent = createRealmeSettingsIntent(),
                helpUrl = "https://www.realme.com/en/support/faq/faq-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get Motorola permission guidance
     */
    private fun getMotorolaGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on Motorola",
                steps = listOf(
                    "1. Go to Settings > Apps & notifications > App info",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission",
                    "5. Go to Settings > Battery > Battery optimization",
                    "6. Find 'Ambient Scribe' and set to 'Don't optimize'"
                ),
                settingsIntent = createMotorolaSettingsIntent(),
                helpUrl = "https://www.motorola.com/us/support/faq/faq-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on Motorola",
                steps = listOf(
                    "1. Go to Settings > Apps & notifications > App info",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'",
                    "5. Set notification importance to 'High'"
                ),
                settingsIntent = createMotorolaSettingsIntent(),
                helpUrl = "https://www.motorola.com/us/support/faq/faq-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get LG permission guidance
     */
    private fun getLGGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission on LG",
                steps = listOf(
                    "1. Go to Settings > Apps > App info",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission",
                    "5. Go to Settings > Battery > Battery optimization",
                    "6. Find 'Ambient Scribe' and set to 'Don't optimize'"
                ),
                settingsIntent = createLGSettingsIntent(),
                helpUrl = "https://www.lg.com/us/support/faq/faq-1"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications on LG",
                steps = listOf(
                    "1. Go to Settings > Apps > App info",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'",
                    "5. Set notification importance to 'High'"
                ),
                settingsIntent = createLGSettingsIntent(),
                helpUrl = "https://www.lg.com/us/support/faq/faq-2"
            )
            else -> getGenericGuidance(permission)
        }
    }
    
    /**
     * Get generic permission guidance
     */
    private fun getGenericGuidance(permission: String): PermissionGuidance {
        return when (permission) {
            "RECORD_AUDIO" -> PermissionGuidance(
                title = "Enable Microphone Permission",
                steps = listOf(
                    "1. Go to Settings > Apps",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Permissions'",
                    "4. Enable 'Microphone' permission"
                ),
                settingsIntent = createGenericSettingsIntent(),
                helpUrl = "https://support.google.com/android/answer/9431959"
            )
            "POST_NOTIFICATIONS" -> PermissionGuidance(
                title = "Enable Notifications",
                steps = listOf(
                    "1. Go to Settings > Apps",
                    "2. Find 'Ambient Scribe' and tap on it",
                    "3. Tap 'Notifications'",
                    "4. Enable 'Allow notifications'"
                ),
                settingsIntent = createGenericSettingsIntent(),
                helpUrl = "https://support.google.com/android/answer/9431959"
            )
            else -> PermissionGuidance(
                title = "Permission Required",
                steps = listOf("Please enable the required permission in Settings"),
                settingsIntent = createGenericSettingsIntent(),
                helpUrl = "https://support.google.com/android/answer/9431959"
            )
        }
    }
    
    /**
     * Create settings intent for Xiaomi
     */
    private fun createXiaomiSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for Samsung
     */
    private fun createSamsungSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for Oppo
     */
    private fun createOppoSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for Vivo
     */
    private fun createVivoSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for OnePlus
     */
    private fun createOnePlusSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for Huawei
     */
    private fun createHuaweiSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for Honor
     */
    private fun createHonorSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for Realme
     */
    private fun createRealmeSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for Motorola
     */
    private fun createMotorolaSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create settings intent for LG
     */
    private fun createLGSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Create generic settings intent
     */
    private fun createGenericSettingsIntent(): Intent {
        return Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:${context.packageName}")
        }
    }
    
    /**
     * Get OEM information
     */
    fun getOEMInfo(): Map<String, Any> {
        return mapOf(
            "oem" to _currentOEM.value,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT
        )
    }
    
    /**
     * Permission guidance data class
     */
    data class PermissionGuidance(
        val title: String,
        val steps: List<String>,
        val settingsIntent: Intent,
        val helpUrl: String
    )
}
