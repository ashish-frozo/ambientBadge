package com.frozo.ambientscribe.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.frozo.ambientscribe.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages security features
 */
class SecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityManager"
        private const val BIOMETRIC_SESSION_DURATION = 5 * 60 * 1000L // 5 minutes
    }

    private val biometricManager = BiometricManager.from(context)
    private var lastBiometricAuthTime = 0L

    /**
     * Check if biometric authentication is available
     */
    fun isBiometricAvailable(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Show biometric prompt
     */
    suspend fun showBiometricPrompt(activity: FragmentActivity): Boolean {
        // Check if we have a valid session
        val now = System.currentTimeMillis()
        if (now - lastBiometricAuthTime < BIOMETRIC_SESSION_DURATION) {
            return true
        }

        // Create biometric prompt
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.authentication_required))
            .setSubtitle(context.getString(R.string.verify_identity))
            .setNegativeButtonText(context.getString(R.string.cancel))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        lastBiometricAuthTime = System.currentTimeMillis()
                        continuation.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        continuation.resume(false)
                    }

                    override fun onAuthenticationFailed() {
                        continuation.resume(false)
                    }
                })

            biometricPrompt.authenticate(promptInfo)

            continuation.invokeOnCancellation {
                biometricPrompt.cancelAuthentication()
            }
        }
    }

    /**
     * Prevent screenshots and screen recording
     */
    fun preventScreenCapture(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    /**
     * Allow screenshots and screen recording
     */
    fun allowScreenCapture(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}