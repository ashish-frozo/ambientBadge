package com.frozo.ambientscribe.clinic

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.frozo.ambientscribe.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Manages AHS doctor acknowledgement
 * Implements ST-4.14: Add AHS doctor acknowledgement line
 */
class AHSAcknowledgement(private val context: Context) {

    companion object {
        private const val TAG = "AHSAcknowledgement"
        private const val PREFS_NAME = "ahs_acknowledgement_prefs"
        private const val KEY_DOCTOR_NAME = "doctor_name"
        private const val KEY_DOCTOR_TITLE = "doctor_title"
        private const val KEY_ACKNOWLEDGEMENT_DATE = "acknowledgement_date"
        private const val KEY_ACKNOWLEDGEMENT_VERSION = "acknowledgement_version"
        private const val CURRENT_VERSION = "1.0"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Acknowledgement data
     */
    data class Acknowledgement(
        val doctorName: String,
        val doctorTitle: String,
        val acknowledgementDate: LocalDateTime,
        val version: String
    )

    /**
     * Save acknowledgement
     */
    suspend fun saveAcknowledgement(
        doctorName: String,
        doctorTitle: String
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Saving AHS acknowledgement")

            // Validate inputs
            if (doctorName.isBlank() || doctorTitle.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Doctor name and title required")
                )
            }

            // Save acknowledgement
            encryptedPrefs.edit()
                .putString(KEY_DOCTOR_NAME, doctorName)
                .putString(KEY_DOCTOR_TITLE, doctorTitle)
                .putString(
                    KEY_ACKNOWLEDGEMENT_DATE,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                )
                .putString(KEY_ACKNOWLEDGEMENT_VERSION, CURRENT_VERSION)
                .apply()

            Log.i(TAG, "AHS acknowledgement saved")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save AHS acknowledgement", e)
            Result.failure(e)
        }
    }

    /**
     * Get acknowledgement
     */
    fun getAcknowledgement(): Result<Acknowledgement> {
        return try {
            Log.d(TAG, "Getting AHS acknowledgement")

            val doctorName = encryptedPrefs.getString(KEY_DOCTOR_NAME, null)
                ?: return Result.failure(IllegalStateException("No acknowledgement found"))

            val doctorTitle = encryptedPrefs.getString(KEY_DOCTOR_TITLE, null)
                ?: return Result.failure(IllegalStateException("No acknowledgement found"))

            val dateStr = encryptedPrefs.getString(KEY_ACKNOWLEDGEMENT_DATE, null)
                ?: return Result.failure(IllegalStateException("No acknowledgement found"))

            val version = encryptedPrefs.getString(KEY_ACKNOWLEDGEMENT_VERSION, null)
                ?: return Result.failure(IllegalStateException("No acknowledgement found"))

            val date = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)

            val acknowledgement = Acknowledgement(
                doctorName = doctorName,
                doctorTitle = doctorTitle,
                acknowledgementDate = date,
                version = version
            )

            Log.i(TAG, "AHS acknowledgement retrieved")
            Result.success(acknowledgement)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get AHS acknowledgement", e)
            Result.failure(e)
        }
    }

    /**
     * Get acknowledgement text
     */
    fun getAcknowledgementText(acknowledgement: Acknowledgement): String {
        return context.getString(
            R.string.ahs_acknowledgement_format,
            acknowledgement.doctorName,
            acknowledgement.doctorTitle,
            acknowledgement.acknowledgementDate.format(
                DateTimeFormatter.ofPattern("dd MMM yyyy")
            )
        )
    }

    /**
     * Check if acknowledgement is current
     */
    fun isAcknowledgementCurrent(): Boolean {
        return try {
            val version = encryptedPrefs.getString(KEY_ACKNOWLEDGEMENT_VERSION, null)
            version == CURRENT_VERSION
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear acknowledgement
     */
    fun clearAcknowledgement() {
        try {
            encryptedPrefs.edit().clear().apply()
            Log.i(TAG, "AHS acknowledgement cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear AHS acknowledgement", e)
        }
    }
}
