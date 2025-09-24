package com.frozo.ambientscribe.ai

import android.content.Context
import android.os.Parcelable
import com.frozo.ambientscribe.R
import kotlinx.parcelize.Parcelize

/**
 * Validates prescription data
 */
class PrescriptionValidator(private val context: Context) {

    /**
     * Validation result data class
     */
    @Parcelize
    data class ValidationResult(
        val nameError: String? = null,
        val dosageError: String? = null,
        val frequencyError: String? = null,
        val durationError: String? = null,
        val instructionsError: String? = null
    ) : Parcelable

    /**
     * Validates a medication
     */
    fun validateMedication(medication: LLMService.Medication): ValidationResult {
        var result = ValidationResult()

        // Validate name
        if (medication.name.isBlank()) {
            result = result.copy(nameError = context.getString(R.string.error_empty_registration))
        }

        // Validate dosage
        if (medication.dosage.isBlank()) {
            result = result.copy(dosageError = context.getString(R.string.error_empty_registration))
        }

        // Validate frequency
        if (medication.frequency.isBlank()) {
            result = result.copy(frequencyError = context.getString(R.string.error_empty_registration))
        }

        // Validate duration
        if (medication.duration.isBlank()) {
            result = result.copy(durationError = context.getString(R.string.error_empty_registration))
        }

        // Validate instructions
        if (medication.instructions.isBlank()) {
            result = result.copy(instructionsError = context.getString(R.string.error_empty_registration))
        }

        return result
    }
}