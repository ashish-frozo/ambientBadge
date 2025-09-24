package com.frozo.ambientscribe.security

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SecurityManagerTest {

    private lateinit var context: Context
    private lateinit var securityManager: SecurityManager
    private lateinit var mockActivity: Activity
    private lateinit var mockFragmentActivity: FragmentActivity
    private lateinit var mockBiometricManager: BiometricManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockActivity = mockk(relaxed = true)
        mockFragmentActivity = mockk(relaxed = true)
        mockBiometricManager = mockk()

        // Mock window flags
        val mockWindow = mockk<android.view.Window>(relaxed = true)
        every { mockActivity.window } returns mockWindow
        
        securityManager = SecurityManager(context)
    }

    @Test
    fun testPreventScreenCapture() {
        securityManager.preventScreenCapture(mockActivity)
        
        verify {
            mockActivity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    @Test
    fun testAllowScreenCapture() {
        securityManager.allowScreenCapture(mockActivity)
        
        verify {
            mockActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Test
    fun testBiometricAvailability_Success() {
        every {
            mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS

        assertTrue(securityManager.isBiometricAvailable())
    }

    @Test
    fun testBiometricAvailability_NoHardware() {
        every {
            mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        assertFalse(securityManager.isBiometricAvailable())
    }

    @Test
    fun testBiometricAvailability_HardwareUnavailable() {
        every {
            mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE

        assertFalse(securityManager.isBiometricAvailable())
    }

    @Test
    fun testBiometricAvailability_NoneEnrolled() {
        every {
            mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        assertFalse(securityManager.isBiometricAvailable())
    }

    @Test
    fun testBiometricSession_ReuseWithinTimeout() = runBlocking {
        // First authentication
        val result1 = securityManager.showBiometricPrompt(mockFragmentActivity)
        assertTrue(result1)

        // Second authentication within timeout
        val result2 = securityManager.showBiometricPrompt(mockFragmentActivity)
        assertTrue(result2)
    }

    @Test
    fun testBiometricSession_ExpiredTimeout() = runBlocking {
        // First authentication
        val result1 = securityManager.showBiometricPrompt(mockFragmentActivity)
        assertTrue(result1)

        // Wait for session to expire
        Thread.sleep(5 * 60 * 1000 + 100) // 5 minutes + 100ms

        // Second authentication after timeout
        val result2 = securityManager.showBiometricPrompt(mockFragmentActivity)
        assertFalse(result2)
    }
}
