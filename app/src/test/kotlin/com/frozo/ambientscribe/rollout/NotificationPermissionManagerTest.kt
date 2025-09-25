package com.frozo.ambientscribe.rollout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for NotificationPermissionManager
 * 
 * Tests notification permission functionality including:
 * - Permission checking and validation
 * - Permission request handling
 * - Denial UX and recovery
 * - Error handling and graceful degradation
 */
class NotificationPermissionManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockOEMPermissionPlaybook: OEMPermissionPlaybook
    private lateinit var notificationPermissionManager: NotificationPermissionManager
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockOEMPermissionPlaybook = mockk<OEMPermissionPlaybook>(relaxed = true)
        
        // Mock Build constants
        mockkStatic(Build::class)
        every { Build.VERSION.SDK_INT } returns 33 // Android 13
        
        // Mock ContextCompat
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // Reset singleton instance
        NotificationPermissionManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        notificationPermissionManager = NotificationPermissionManager.getInstance(mockContext)
        notificationPermissionManager.initialize(mockOEMPermissionPlaybook)
    }
    
    @Test
    @DisplayName("Test notification permission manager initialization")
    fun testNotificationPermissionManagerInitialization() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // When
        val manager = NotificationPermissionManager.getInstance(mockContext)
        
        // Then
        assertNotNull(manager)
        assertTrue(manager.hasNotificationPermission())
        assertTrue(manager.isNotificationPermissionRequired())
    }
    
    @Test
    @DisplayName("Test notification permission granted")
    fun testNotificationPermissionGranted() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // When
        val hasPermission = notificationPermissionManager.hasNotificationPermission()
        
        // Then
        assertTrue(hasPermission)
    }
    
    @Test
    @DisplayName("Test notification permission denied")
    fun testNotificationPermissionDenied() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // When
        val hasPermission = notificationPermissionManager.hasNotificationPermission()
        
        // Then
        assertFalse(hasPermission)
    }
    
    @Test
    @DisplayName("Test notification permission not required for older Android versions")
    fun testNotificationPermissionNotRequiredForOlderAndroidVersions() {
        // Given
        every { Build.VERSION.SDK_INT } returns 30 // Android 11
        
        // When
        val isRequired = notificationPermissionManager.isNotificationPermissionRequired()
        val hasPermission = notificationPermissionManager.hasNotificationPermission()
        
        // Then
        assertFalse(isRequired)
        assertTrue(hasPermission) // Should return true for older versions
    }
    
    @Test
    @DisplayName("Test notification permission required for Android 13+")
    fun testNotificationPermissionRequiredForAndroid13Plus() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33 // Android 13
        
        // When
        val isRequired = notificationPermissionManager.isNotificationPermissionRequired()
        
        // Then
        assertTrue(isRequired)
    }
    
    @Test
    @DisplayName("Test permission can be requested when not granted")
    fun testPermissionCanBeRequestedWhenNotGranted() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val canRequest = notificationPermissionManager.canRequestPermission()
        
        // Then
        assertTrue(canRequest)
    }
    
    @Test
    @DisplayName("Test permission cannot be requested when already granted")
    fun testPermissionCannotBeRequestedWhenAlreadyGranted() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // When
        val canRequest = notificationPermissionManager.canRequestPermission()
        
        // Then
        assertFalse(canRequest)
    }
    
    @Test
    @DisplayName("Test permission cannot be requested when denied too many times")
    fun testPermissionCannotBeRequestedWhenDeniedTooManyTimes() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 3 // Max denials
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val canRequest = notificationPermissionManager.canRequestPermission()
        
        // Then
        assertFalse(canRequest)
    }
    
    @Test
    @DisplayName("Test permission cannot be requested during cooldown")
    fun testPermissionCannotBeRequestedDuringCooldown() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 1
        every { mockPrefs.getLong(any(), any()) } returns System.currentTimeMillis() - (12 * 60 * 60 * 1000L) // 12 hours ago
        
        // When
        val canRequest = notificationPermissionManager.canRequestPermission()
        
        // Then
        assertFalse(canRequest)
    }
    
    @Test
    @DisplayName("Test permission request rationale for first denial")
    fun testPermissionRequestRationaleForFirstDenial() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val rationale = notificationPermissionManager.getPermissionRequestRationale()
        
        // Then
        assertTrue(rationale.title.contains("Enable Notifications"))
        assertTrue(rationale.message.contains("Ambient Scribe needs notification permission"))
        assertTrue(rationale.showRationale)
        assertTrue(rationale.canRequest)
    }
    
    @Test
    @DisplayName("Test permission request rationale for second denial")
    fun testPermissionRequestRationaleForSecondDenial() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 1
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val rationale = notificationPermissionManager.getPermissionRequestRationale()
        
        // Then
        assertTrue(rationale.title.contains("Notifications Required"))
        assertTrue(rationale.message.contains("Without notifications"))
        assertTrue(rationale.showRationale)
        assertTrue(rationale.canRequest)
    }
    
    @Test
    @DisplayName("Test permission request rationale for third denial")
    fun testPermissionRequestRationaleForThirdDenial() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 2
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val rationale = notificationPermissionManager.getPermissionRequestRationale()
        
        // Then
        assertTrue(rationale.title.contains("Last Chance"))
        assertTrue(rationale.message.contains("last chance"))
        assertTrue(rationale.showRationale)
        assertTrue(rationale.canRequest)
    }
    
    @Test
    @DisplayName("Test permission request rationale for permanent denial")
    fun testPermissionRequestRationaleForPermanentDenial() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 3
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val rationale = notificationPermissionManager.getPermissionRequestRationale()
        
        // Then
        assertTrue(rationale.title.contains("Notifications Disabled"))
        assertTrue(rationale.message.contains("permanently disabled"))
        assertFalse(rationale.showRationale)
        assertFalse(rationale.canRequest)
    }
    
    @Test
    @DisplayName("Test permission result handling for granted permission")
    fun testPermissionResultHandlingForGrantedPermission() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val mockPrefsEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        notificationPermissionManager.handlePermissionResult(true)
        
        // Then
        assertTrue(notificationPermissionManager.hasNotificationPermission.value)
        assertTrue(notificationPermissionManager.permissionRequested.value)
        assertEquals(0, notificationPermissionManager.permissionDeniedCount.value)
    }
    
    @Test
    @DisplayName("Test permission result handling for denied permission")
    fun testPermissionResultHandlingForDeniedPermission() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val mockPrefsEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        notificationPermissionManager.handlePermissionResult(false)
        
        // Then
        assertFalse(notificationPermissionManager.hasNotificationPermission.value)
        assertTrue(notificationPermissionManager.permissionRequested.value)
        assertEquals(1, notificationPermissionManager.permissionDeniedCount.value)
    }
    
    @Test
    @DisplayName("Test permission denial guidance")
    fun testPermissionDenialGuidance() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 1
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock OEM permission playbook
        val mockGuidance = OEMPermissionPlaybook.PermissionGuidance(
            "Test Title",
            listOf("Step 1", "Step 2"),
            mockk<android.content.Intent>(relaxed = true),
            "https://test.com"
        )
        every { mockOEMPermissionPlaybook.getPermissionGuidance(any()) } returns mockGuidance
        
        // When
        val guidance = notificationPermissionManager.getPermissionDenialGuidance()
        
        // Then
        assertTrue(guidance.title.contains("Enable Notifications"))
        assertTrue(guidance.message.contains("best experience"))
        assertTrue(guidance.canRetry)
        assertTrue(guidance.showSettings)
        assertEquals(mockGuidance, guidance.guidance)
    }
    
    @Test
    @DisplayName("Test foreground service notification will work")
    fun testForegroundServiceNotificationWillWork() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // When
        val willWork = notificationPermissionManager.willForegroundServiceNotificationWork()
        
        // Then
        assertTrue(willWork)
    }
    
    @Test
    @DisplayName("Test foreground service notification will not work without permission")
    fun testForegroundServiceNotificationWillNotWorkWithoutPermission() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // When
        val willWork = notificationPermissionManager.willForegroundServiceNotificationWork()
        
        // Then
        assertFalse(willWork)
    }
    
    @Test
    @DisplayName("Test foreground service notification will work on older Android versions")
    fun testForegroundServiceNotificationWillWorkOnOlderAndroidVersions() {
        // Given
        every { Build.VERSION.SDK_INT } returns 30 // Android 11
        
        // When
        val willWork = notificationPermissionManager.willForegroundServiceNotificationWork()
        
        // Then
        assertTrue(willWork)
    }
    
    @Test
    @DisplayName("Test fallback notification strategy")
    fun testFallbackNotificationStrategy() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // When
        val strategy = notificationPermissionManager.getFallbackNotificationStrategy()
        
        // Then
        assertEquals(NotificationPermissionManager.FallbackNotificationStrategy.NORMAL, strategy)
    }
    
    @Test
    @DisplayName("Test fallback notification strategy for denied permission")
    fun testFallbackNotificationStrategyForDeniedPermission() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // When
        val strategy = notificationPermissionManager.getFallbackNotificationStrategy()
        
        // Then
        assertEquals(NotificationPermissionManager.FallbackNotificationStrategy.REDUCED, strategy)
    }
    
    @Test
    @DisplayName("Test notification permission status retrieval")
    fun testNotificationPermissionStatusRetrieval() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val status = notificationPermissionManager.getNotificationPermissionStatus()
        
        // Then
        assertTrue(status.hasPermission)
        assertTrue(status.isRequired)
        assertFalse(status.canRequest)
        assertEquals(0, status.deniedCount)
        assertFalse(status.requested)
        assertNotNull(status.rationale)
        assertNotNull(status.denialGuidance)
        assertNotNull(status.fallbackStrategy)
    }
    
    @Test
    @DisplayName("Test reset permission state")
    fun testResetPermissionState() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val mockPrefsEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.clear() } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        
        // When
        notificationPermissionManager.resetPermissionState()
        
        // Then
        assertTrue(notificationPermissionManager.hasNotificationPermission.value)
        assertFalse(notificationPermissionManager.permissionRequested.value)
        assertEquals(0, notificationPermissionManager.permissionDeniedCount.value)
    }
    
    @Test
    @DisplayName("Test error handling in permission checking")
    fun testErrorHandlingInPermissionChecking() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } throws RuntimeException("Test error")
        
        // When
        val hasPermission = notificationPermissionManager.hasNotificationPermission()
        
        // Then
        assertFalse(hasPermission) // Should return false on error (fail-safe)
    }
    
    @Test
    @DisplayName("Test concurrent permission operations")
    fun testConcurrentPermissionOperations() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val mockPrefsEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        every { mockPrefs.getInt(any(), any()) } returns 0
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) {
                    when (threadId % 3) {
                        0 -> notificationPermissionManager.hasNotificationPermission()
                        1 -> notificationPermissionManager.canRequestPermission()
                        2 -> notificationPermissionManager.handlePermissionResult(threadId % 2 == 0)
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertNotNull(notificationPermissionManager.getNotificationPermissionStatus())
    }
    
    @Test
    @DisplayName("Test permission rationale for different denial counts")
    fun testPermissionRationaleForDifferentDenialCounts() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val testCases = listOf(0, 1, 2, 3, 4, 5)
        
        // When & Then
        testCases.forEach { denialCount ->
            every { mockPrefs.getInt(any(), any()) } returns denialCount
            val rationale = notificationPermissionManager.getPermissionRequestRationale()
            assertNotNull(rationale)
            assertTrue(rationale.title.isNotEmpty())
            assertTrue(rationale.message.isNotEmpty())
        }
    }
    
    @Test
    @DisplayName("Test fallback notification strategy for different scenarios")
    fun testFallbackNotificationStrategyForDifferentScenarios() {
        // Given
        every { Build.VERSION.SDK_INT } returns 33
        
        val testCases = listOf(
            Pair(PackageManager.PERMISSION_GRANTED, NotificationPermissionManager.FallbackNotificationStrategy.NORMAL),
            Pair(PackageManager.PERMISSION_DENIED, NotificationPermissionManager.FallbackNotificationStrategy.REDUCED)
        )
        
        // When & Then
        testCases.forEach { (permission, expectedStrategy) ->
            every { ContextCompat.checkSelfPermission(any(), any()) } returns permission
            val strategy = notificationPermissionManager.getFallbackNotificationStrategy()
            assertEquals(expectedStrategy, strategy)
        }
    }
}
