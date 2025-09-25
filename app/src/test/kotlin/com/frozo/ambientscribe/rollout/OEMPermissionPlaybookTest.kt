package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for OEMPermissionPlaybook
 * 
 * Tests OEM permission guidance functionality including:
 * - Device detection and OEM identification
 * - Permission guidance generation
 * - Settings intent creation
 * - Error handling and fallbacks
 */
class OEMPermissionPlaybookTest {
    
    private lateinit var mockContext: Context
    private lateinit var oemPermissionPlaybook: OEMPermissionPlaybook
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        
        // Mock Build constants
        mockkStatic(Build::class)
        every { Build.MANUFACTURER } returns "TestManufacturer"
        every { Build.BRAND } returns "TestBrand"
        every { Build.MODEL } returns "TestModel"
        every { Build.VERSION.RELEASE } returns "13"
        every { Build.VERSION.SDK_INT } returns 33
        every { Build.FINGERPRINT } returns "test/fingerprint/123"
        
        // Reset singleton instance
        OEMPermissionPlaybook::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        oemPermissionPlaybook = OEMPermissionPlaybook.getInstance(mockContext)
    }
    
    @Test
    @DisplayName("Test OEM detection for Xiaomi")
    fun testOEMDetectionForXiaomi() {
        // Given
        every { Build.MANUFACTURER } returns "xiaomi"
        every { Build.BRAND } returns "xiaomi"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("xiaomi", oemInfo["oem"])
        assertEquals("xiaomi", oemInfo["manufacturer"])
        assertEquals("xiaomi", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for Samsung")
    fun testOEMDetectionForSamsung() {
        // Given
        every { Build.MANUFACTURER } returns "samsung"
        every { Build.BRAND } returns "samsung"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("samsung", oemInfo["oem"])
        assertEquals("samsung", oemInfo["manufacturer"])
        assertEquals("samsung", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for Oppo")
    fun testOEMDetectionForOppo() {
        // Given
        every { Build.MANUFACTURER } returns "oppo"
        every { Build.BRAND } returns "oppo"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("oppo", oemInfo["oem"])
        assertEquals("oppo", oemInfo["manufacturer"])
        assertEquals("oppo", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for Vivo")
    fun testOEMDetectionForVivo() {
        // Given
        every { Build.MANUFACTURER } returns "vivo"
        every { Build.BRAND } returns "vivo"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("vivo", oemInfo["oem"])
        assertEquals("vivo", oemInfo["manufacturer"])
        assertEquals("vivo", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for OnePlus")
    fun testOEMDetectionForOnePlus() {
        // Given
        every { Build.MANUFACTURER } returns "oneplus"
        every { Build.BRAND } returns "oneplus"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("oneplus", oemInfo["oem"])
        assertEquals("oneplus", oemInfo["manufacturer"])
        assertEquals("oneplus", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for Huawei")
    fun testOEMDetectionForHuawei() {
        // Given
        every { Build.MANUFACTURER } returns "huawei"
        every { Build.BRAND } returns "huawei"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("huawei", oemInfo["oem"])
        assertEquals("huawei", oemInfo["manufacturer"])
        assertEquals("huawei", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for Honor")
    fun testOEMDetectionForHonor() {
        // Given
        every { Build.MANUFACTURER } returns "honor"
        every { Build.BRAND } returns "honor"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("honor", oemInfo["oem"])
        assertEquals("honor", oemInfo["manufacturer"])
        assertEquals("honor", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for Realme")
    fun testOEMDetectionForRealme() {
        // Given
        every { Build.MANUFACTURER } returns "realme"
        every { Build.BRAND } returns "realme"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("realme", oemInfo["oem"])
        assertEquals("realme", oemInfo["manufacturer"])
        assertEquals("realme", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for Motorola")
    fun testOEMDetectionForMotorola() {
        // Given
        every { Build.MANUFACTURER } returns "motorola"
        every { Build.BRAND } returns "motorola"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("motorola", oemInfo["oem"])
        assertEquals("motorola", oemInfo["manufacturer"])
        assertEquals("motorola", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for LG")
    fun testOEMDetectionForLG() {
        // Given
        every { Build.MANUFACTURER } returns "lg"
        every { Build.BRAND } returns "lg"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("lg", oemInfo["oem"])
        assertEquals("lg", oemInfo["manufacturer"])
        assertEquals("lg", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test OEM detection for generic device")
    fun testOEMDetectionForGenericDevice() {
        // Given
        every { Build.MANUFACTURER } returns "unknown"
        every { Build.BRAND } returns "unknown"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("generic", oemInfo["oem"])
        assertEquals("unknown", oemInfo["manufacturer"])
        assertEquals("unknown", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test permission guidance for RECORD_AUDIO on Xiaomi")
    fun testPermissionGuidanceForRecordAudioOnXiaomi() {
        // Given
        every { Build.MANUFACTURER } returns "xiaomi"
        every { Build.BRAND } returns "xiaomi"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
        
        // Then
        assertTrue(guidance.title.contains("MIUI"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Permissions") })
        assertTrue(guidance.steps.any { it.contains("Microphone") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for POST_NOTIFICATIONS on Samsung")
    fun testPermissionGuidanceForPostNotificationsOnSamsung() {
        // Given
        every { Build.MANUFACTURER } returns "samsung"
        every { Build.BRAND } returns "samsung"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("POST_NOTIFICATIONS")
        
        // Then
        assertTrue(guidance.title.contains("Samsung"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Notifications") })
        assertTrue(guidance.steps.any { it.contains("Allow notifications") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for RECORD_AUDIO on Oppo")
    fun testPermissionGuidanceForRecordAudioOnOppo() {
        // Given
        every { Build.MANUFACTURER } returns "oppo"
        every { Build.BRAND } returns "oppo"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
        
        // Then
        assertTrue(guidance.title.contains("Oppo"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Permissions") })
        assertTrue(guidance.steps.any { it.contains("Microphone") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for POST_NOTIFICATIONS on Vivo")
    fun testPermissionGuidanceForPostNotificationsOnVivo() {
        // Given
        every { Build.MANUFACTURER } returns "vivo"
        every { Build.BRAND } returns "vivo"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("POST_NOTIFICATIONS")
        
        // Then
        assertTrue(guidance.title.contains("Vivo"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Notifications") })
        assertTrue(guidance.steps.any { it.contains("Allow notifications") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for RECORD_AUDIO on OnePlus")
    fun testPermissionGuidanceForRecordAudioOnOnePlus() {
        // Given
        every { Build.MANUFACTURER } returns "oneplus"
        every { Build.BRAND } returns "oneplus"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
        
        // Then
        assertTrue(guidance.title.contains("OnePlus"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Permissions") })
        assertTrue(guidance.steps.any { it.contains("Microphone") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for POST_NOTIFICATIONS on Huawei")
    fun testPermissionGuidanceForPostNotificationsOnHuawei() {
        // Given
        every { Build.MANUFACTURER } returns "huawei"
        every { Build.BRAND } returns "huawei"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("POST_NOTIFICATIONS")
        
        // Then
        assertTrue(guidance.title.contains("Huawei"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Permissions") })
        assertTrue(guidance.steps.any { it.contains("Notifications") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for RECORD_AUDIO on Honor")
    fun testPermissionGuidanceForRecordAudioOnHonor() {
        // Given
        every { Build.MANUFACTURER } returns "honor"
        every { Build.BRAND } returns "honor"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
        
        // Then
        assertTrue(guidance.title.contains("Honor"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Permissions") })
        assertTrue(guidance.steps.any { it.contains("Microphone") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for POST_NOTIFICATIONS on Realme")
    fun testPermissionGuidanceForPostNotificationsOnRealme() {
        // Given
        every { Build.MANUFACTURER } returns "realme"
        every { Build.BRAND } returns "realme"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("POST_NOTIFICATIONS")
        
        // Then
        assertTrue(guidance.title.contains("Realme"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Notifications") })
        assertTrue(guidance.steps.any { it.contains("Allow notifications") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for RECORD_AUDIO on Motorola")
    fun testPermissionGuidanceForRecordAudioOnMotorola() {
        // Given
        every { Build.MANUFACTURER } returns "motorola"
        every { Build.BRAND } returns "motorola"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
        
        // Then
        assertTrue(guidance.title.contains("Motorola"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Permissions") })
        assertTrue(guidance.steps.any { it.contains("Microphone") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for POST_NOTIFICATIONS on LG")
    fun testPermissionGuidanceForPostNotificationsOnLG() {
        // Given
        every { Build.MANUFACTURER } returns "lg"
        every { Build.BRAND } returns "lg"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("POST_NOTIFICATIONS")
        
        // Then
        assertTrue(guidance.title.contains("LG"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertTrue(guidance.steps.any { it.contains("Apps") })
        assertTrue(guidance.steps.any { it.contains("Notifications") })
        assertTrue(guidance.steps.any { it.contains("Allow notifications") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test permission guidance for unknown permission")
    fun testPermissionGuidanceForUnknownPermission() {
        // Given
        every { Build.MANUFACTURER } returns "generic"
        every { Build.BRAND } returns "generic"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("UNKNOWN_PERMISSION")
        
        // Then
        assertTrue(guidance.title.contains("Permission Required"))
        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.steps.any { it.contains("Settings") })
        assertNotNull(guidance.settingsIntent)
        assertNotNull(guidance.helpUrl)
    }
    
    @Test
    @DisplayName("Test settings intent creation")
    fun testSettingsIntentCreation() {
        // Given
        every { Build.MANUFACTURER } returns "xiaomi"
        every { Build.BRAND } returns "xiaomi"
        every { mockContext.packageName } returns "com.test.app"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
        
        // Then
        assertNotNull(guidance.settingsIntent)
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", guidance.settingsIntent.action)
        assertTrue(guidance.settingsIntent.data.toString().contains("com.test.app"))
    }
    
    @Test
    @DisplayName("Test help URL format")
    fun testHelpUrlFormat() {
        // Given
        every { Build.MANUFACTURER } returns "samsung"
        every { Build.BRAND } returns "samsung"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
        
        // Then
        assertNotNull(guidance.helpUrl)
        assertTrue(guidance.helpUrl.startsWith("https://"))
        assertTrue(guidance.helpUrl.contains("samsung"))
    }
    
    @Test
    @DisplayName("Test permission guidance steps format")
    fun testPermissionGuidanceStepsFormat() {
        // Given
        every { Build.MANUFACTURER } returns "oppo"
        every { Build.BRAND } returns "oppo"
        
        // When
        val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
        
        // Then
        assertTrue(guidance.steps.isNotEmpty())
        guidance.steps.forEach { step ->
            assertTrue(step.isNotEmpty())
            assertTrue(step.contains(".") || step.contains(":") || step.contains("'"))
        }
    }
    
    @Test
    @DisplayName("Test OEM info completeness")
    fun testOEMInfoCompleteness() {
        // Given
        every { Build.MANUFACTURER } returns "test"
        every { Build.BRAND } returns "test"
        every { Build.MODEL } returns "test"
        every { Build.VERSION.RELEASE } returns "13"
        every { Build.VERSION.SDK_INT } returns 33
        every { Build.FINGERPRINT } returns "test/fingerprint/123"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertTrue(oemInfo.containsKey("oem"))
        assertTrue(oemInfo.containsKey("manufacturer"))
        assertTrue(oemInfo.containsKey("brand"))
        assertTrue(oemInfo.containsKey("model"))
        assertTrue(oemInfo.containsKey("version"))
        assertTrue(oemInfo.containsKey("sdk_version"))
    }
    
    @Test
    @DisplayName("Test case insensitive OEM detection")
    fun testCaseInsensitiveOEMDetection() {
        // Given
        every { Build.MANUFACTURER } returns "XIAOMI"
        every { Build.BRAND } returns "Xiaomi"
        
        // When
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        
        // Then
        assertEquals("xiaomi", oemInfo["oem"])
        assertEquals("XIAOMI", oemInfo["manufacturer"])
        assertEquals("Xiaomi", oemInfo["brand"])
    }
    
    @Test
    @DisplayName("Test permission guidance for all supported permissions")
    fun testPermissionGuidanceForAllSupportedPermissions() {
        // Given
        every { Build.MANUFACTURER } returns "samsung"
        every { Build.BRAND } returns "samsung"
        
        val permissions = listOf("RECORD_AUDIO", "POST_NOTIFICATIONS", "UNKNOWN_PERMISSION")
        
        // When & Then
        permissions.forEach { permission ->
            val guidance = oemPermissionPlaybook.getPermissionGuidance(permission)
            assertNotNull(guidance)
            assertTrue(guidance.title.isNotEmpty())
            assertTrue(guidance.steps.isNotEmpty())
            assertNotNull(guidance.settingsIntent)
            assertNotNull(guidance.helpUrl)
        }
    }
    
    @Test
    @DisplayName("Test concurrent permission guidance requests")
    fun testConcurrentPermissionGuidanceRequests() {
        // Given
        every { Build.MANUFACTURER } returns "xiaomi"
        every { Build.BRAND } returns "xiaomi"
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) {
                    val guidance = oemPermissionPlaybook.getPermissionGuidance("RECORD_AUDIO")
                    assertNotNull(guidance)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        val oemInfo = oemPermissionPlaybook.getOEMInfo()
        assertNotNull(oemInfo)
    }
}
