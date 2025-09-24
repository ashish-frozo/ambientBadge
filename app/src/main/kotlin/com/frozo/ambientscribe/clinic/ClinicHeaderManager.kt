package com.frozo.ambientscribe.clinic

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages clinic header information and assets
 */
class ClinicHeaderManager(private val context: Context) {

    companion object {
        private const val TAG = "ClinicHeaderManager"
        private const val PREFS_NAME = "clinic_header_prefs"
        private const val KEY_CLINIC_NAME = "clinic_name"
        private const val KEY_CLINIC_ADDRESS = "clinic_address"
        private const val KEY_DOCTOR_NAME = "doctor_name"
        private const val KEY_DOCTOR_TITLE = "doctor_title"
        private const val KEY_DOCTOR_REGISTRATION = "doctor_registration"
        private const val KEY_LOGO_PATH = "logo_path"
        private const val LOGO_FILE_NAME = "clinic_logo.png"
        private const val MAX_LOGO_SIZE = 1024 * 1024 // 1MB
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get clinic name
     */
    fun getClinicName(): String? {
        return encryptedPrefs.getString(KEY_CLINIC_NAME, null)
    }

    /**
     * Set clinic name
     */
    fun setClinicName(name: String) {
        encryptedPrefs.edit().putString(KEY_CLINIC_NAME, name).apply()
    }

    /**
     * Get clinic address
     */
    fun getClinicAddress(): String? {
        return encryptedPrefs.getString(KEY_CLINIC_ADDRESS, null)
    }

    /**
     * Set clinic address
     */
    fun setClinicAddress(address: String) {
        encryptedPrefs.edit().putString(KEY_CLINIC_ADDRESS, address).apply()
    }

    /**
     * Get doctor name
     */
    fun getDoctorName(): String? {
        return encryptedPrefs.getString(KEY_DOCTOR_NAME, null)
    }

    /**
     * Set doctor name
     */
    fun setDoctorName(name: String) {
        encryptedPrefs.edit().putString(KEY_DOCTOR_NAME, name).apply()
    }

    /**
     * Get doctor title
     */
    fun getDoctorTitle(): String? {
        return encryptedPrefs.getString(KEY_DOCTOR_TITLE, null)
    }

    /**
     * Set doctor title
     */
    fun setDoctorTitle(title: String) {
        encryptedPrefs.edit().putString(KEY_DOCTOR_TITLE, title).apply()
    }

    /**
     * Get doctor registration number
     */
    fun getDoctorRegistrationNumber(): String? {
        return encryptedPrefs.getString(KEY_DOCTOR_REGISTRATION, null)
    }

    /**
     * Set doctor registration number
     */
    fun setDoctorRegistrationNumber(registration: String) {
        encryptedPrefs.edit().putString(KEY_DOCTOR_REGISTRATION, registration).apply()
    }

    /**
     * Get logo file path
     */
    fun getLogoPath(): String? {
        return encryptedPrefs.getString(KEY_LOGO_PATH, null)
    }

    /**
     * Set clinic logo from Uri
     */
    suspend fun setClinicLogo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Read bitmap from Uri
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@withContext false

            // Check size
            if (bitmap.byteCount > MAX_LOGO_SIZE) {
                Log.e(TAG, "Logo size exceeds maximum allowed size")
                return@withContext false
            }

            // Save bitmap to internal storage
            val logoFile = File(context.filesDir, LOGO_FILE_NAME)
            FileOutputStream(logoFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            // Save path in preferences
            encryptedPrefs.edit().putString(KEY_LOGO_PATH, logoFile.absolutePath).apply()

            true
        } catch (e: IOException) {
            Log.e(TAG, "Error saving clinic logo", e)
            false
        }
    }

    /**
     * Clear all clinic header data
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
        val logoFile = File(context.filesDir, LOGO_FILE_NAME)
        if (logoFile.exists()) {
            logoFile.delete()
        }
    }

    /**
     * Validate clinic header data
     */
    fun validateHeaderData(): Boolean {
        return if (getClinicName().isNullOrBlank() || 
                  getClinicAddress().isNullOrBlank() || 
                  getDoctorName().isNullOrBlank() || 
                  getDoctorRegistrationNumber().isNullOrBlank()) {
            false
        } else {
            true
        }
    }
}