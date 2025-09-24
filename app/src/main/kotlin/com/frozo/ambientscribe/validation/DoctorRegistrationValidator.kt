package com.frozo.ambientscribe.validation

import android.content.Context
import android.util.Log
import com.frozo.ambientscribe.R
import java.util.regex.Pattern

/**
 * Validates doctor registration numbers based on state-specific rules
 * Implements ST-4.12: Doctor reg# validation
 */
class DoctorRegistrationValidator(private val context: Context) {

    companion object {
        private const val TAG = "DoctorRegValidator"

        // State-specific registration number patterns
        private val STATE_PATTERNS = mapOf(
            "MH" to Pattern.compile("""^MH-\d{5}-[A-Z]$"""),  // Maharashtra
            "KA" to Pattern.compile("""^KMC-\d{5}[A-Z]$"""),  // Karnataka
            "TN" to Pattern.compile("""^TNM-\d{6}$"""),       // Tamil Nadu
            "AP" to Pattern.compile("""^AP-\d{5}/\d{2}$"""),  // Andhra Pradesh
            "TS" to Pattern.compile("""^TS-\d{5}/\d{2}$"""),  // Telangana
            "DL" to Pattern.compile("""^DMC/\d{5}[A-Z]$"""),  // Delhi
            "UP" to Pattern.compile("""^UPMC-\d{5}$"""),      // Uttar Pradesh
            "WB" to Pattern.compile("""^WB-\d{6}$""")         // West Bengal
        )

        // Length limits by state
        private val STATE_LENGTHS = mapOf(
            "MH" to 9,  // MH-12345-A
            "KA" to 9,  // KMC-12345A
            "TN" to 10, // TNM-123456
            "AP" to 11, // AP-12345/12
            "TS" to 11, // TS-12345/12
            "DL" to 10, // DMC/12345A
            "UP" to 10, // UPMC-12345
            "WB" to 9   // WB-123456
        )
    }

    /**
     * Validation result
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val error: ValidationError) : ValidationResult()
    }

    /**
     * Validation errors
     */
    sealed class ValidationError {
        object EmptyRegistration : ValidationError()
        object InvalidState : ValidationError()
        object InvalidFormat : ValidationError()
        object InvalidLength : ValidationError()
        object InvalidChecksum : ValidationError()
    }

    /**
     * Validate doctor registration number
     */
    fun validate(registrationNumber: String): ValidationResult {
        try {
            // Check if empty
            if (registrationNumber.isBlank()) {
                return ValidationResult.Invalid(ValidationError.EmptyRegistration)
            }

            // Extract state code
            val stateCode = extractStateCode(registrationNumber)
                ?: return ValidationResult.Invalid(ValidationError.InvalidState)

            // Check length
            val expectedLength = STATE_LENGTHS[stateCode]
                ?: return ValidationResult.Invalid(ValidationError.InvalidState)
            if (registrationNumber.length != expectedLength) {
                return ValidationResult.Invalid(ValidationError.InvalidLength)
            }

            // Check format
            val pattern = STATE_PATTERNS[stateCode]
                ?: return ValidationResult.Invalid(ValidationError.InvalidState)
            if (!pattern.matcher(registrationNumber).matches()) {
                return ValidationResult.Invalid(ValidationError.InvalidFormat)
            }

            // Validate checksum (if applicable)
            if (!validateChecksum(registrationNumber, stateCode)) {
                return ValidationResult.Invalid(ValidationError.InvalidChecksum)
            }

            Log.i(TAG, "Registration number validated: $registrationNumber")
            return ValidationResult.Valid

        } catch (e: Exception) {
            Log.e(TAG, "Validation failed for: $registrationNumber", e)
            return ValidationResult.Invalid(ValidationError.InvalidFormat)
        }
    }

    /**
     * Get error message for validation error
     */
    fun getErrorMessage(error: ValidationError): String {
        return when (error) {
            is ValidationError.EmptyRegistration -> 
                context.getString(R.string.error_empty_registration)
            is ValidationError.InvalidState -> 
                context.getString(R.string.error_invalid_state)
            is ValidationError.InvalidFormat -> 
                context.getString(R.string.error_invalid_format)
            is ValidationError.InvalidLength -> 
                context.getString(R.string.error_invalid_length)
            is ValidationError.InvalidChecksum -> 
                context.getString(R.string.error_invalid_checksum)
        }
    }

    /**
     * Extract state code from registration number
     */
    private fun extractStateCode(registrationNumber: String): String? {
        // Try direct state code match
        STATE_PATTERNS.keys.forEach { state ->
            if (registrationNumber.startsWith(state)) {
                return state
            }
        }

        // Try council prefix match
        when {
            registrationNumber.startsWith("KMC") -> return "KA"
            registrationNumber.startsWith("TNM") -> return "TN"
            registrationNumber.startsWith("DMC") -> return "DL"
            registrationNumber.startsWith("UPMC") -> return "UP"
        }

        return null
    }

    /**
     * Validate checksum (state-specific)
     */
    private fun validateChecksum(registrationNumber: String, stateCode: String): Boolean {
        // Some states have checksum validation
        return when (stateCode) {
            "MH" -> validateMaharashtraChecksum(registrationNumber)
            "KA" -> validateKarnatakaChecksum(registrationNumber)
            else -> true // Other states don't have checksum
        }
    }

    /**
     * Validate Maharashtra registration checksum
     */
    private fun validateMaharashtraChecksum(registrationNumber: String): Boolean {
        try {
            // MH-12345-A format
            val numPart = registrationNumber.substring(3, 8)
            val checkPart = registrationNumber.last()

            // Calculate checksum
            val sum = numPart.sumOf { it.toString().toInt() }
            val expectedCheck = 'A' + (sum % 26)

            return checkPart == expectedCheck

        } catch (e: Exception) {
            Log.e(TAG, "Maharashtra checksum validation failed", e)
            return false
        }
    }

    /**
     * Validate Karnataka registration checksum
     */
    private fun validateKarnatakaChecksum(registrationNumber: String): Boolean {
        try {
            // KMC-12345A format
            val numPart = registrationNumber.substring(4, 9)
            val checkPart = registrationNumber.last()

            // Calculate checksum
            val sum = numPart.sumOf { it.toString().toInt() }
            val expectedCheck = 'A' + (sum % 26)

            return checkPart == expectedCheck

        } catch (e: Exception) {
            Log.e(TAG, "Karnataka checksum validation failed", e)
            return false
        }
    }

    /**
     * Get example format for state
     */
    fun getExampleFormat(stateCode: String): String {
        return when (stateCode) {
            "MH" -> "MH-12345-A"
            "KA" -> "KMC-12345A"
            "TN" -> "TNM-123456"
            "AP" -> "AP-12345/12"
            "TS" -> "TS-12345/12"
            "DL" -> "DMC/12345A"
            "UP" -> "UPMC-12345"
            "WB" -> "WB-123456"
            else -> ""
        }
    }
}
