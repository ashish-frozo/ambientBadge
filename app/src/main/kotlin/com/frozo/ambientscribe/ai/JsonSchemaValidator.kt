package com.frozo.ambientscribe.ai

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Validates JSON output against schema
 */
class JsonSchemaValidator(private val context: Context) {

    companion object {
        private const val TAG = "JsonSchemaValidator"
        private const val SCHEMA_VERSION = "1.0.0"
        private const val SCHEMA_FILE = "schemas/encounter_note_v1.0.json"
    }

    private val schema: JSONObject by lazy {
        loadSchema()
    }

    private fun loadSchema(): JSONObject {
        return try {
            val inputStream = context.assets.open(SCHEMA_FILE)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            JSONObject(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading schema: ${e.message}", e)
            JSONObject()
        }
    }

    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    /**
     * Validate JSON against schema
     */
    fun validate(json: String): ValidationResult {
        return try {
            val jsonObject = JSONObject(json)
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            // Check schema version
            val version = jsonObject.optString("version")
            if (version != SCHEMA_VERSION) {
                errors.add("Schema version mismatch. Expected: $SCHEMA_VERSION, Found: $version")
            }

            // Check timestamp
            val timestamp = jsonObject.optLong("timestamp")
            if (timestamp <= 0) {
                errors.add("Invalid or missing timestamp")
            }

            // Validate SOAP note
            validateSoapNote(jsonObject.optJSONObject("soap"), errors, warnings)

            // Validate prescription
            validatePrescription(jsonObject.optJSONObject("prescription"), errors, warnings)

            // Validate metadata
            validateMetadata(jsonObject.optJSONObject("metadata"), errors, warnings)

            ValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error validating JSON: ${e.message}", e)
            ValidationResult(
                isValid = false,
                errors = listOf("Invalid JSON format: ${e.message}")
            )
        }
    }

    private fun validateSoapNote(
        soap: JSONObject?,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        if (soap == null) {
            errors.add("Missing SOAP note")
            return
        }

        // Validate subjective
        validateStringList(soap, "subjective", errors, warnings)

        // Validate objective
        validateStringList(soap, "objective", errors, warnings)

        // Validate assessment
        validateStringList(soap, "assessment", errors, warnings)

        // Validate plan
        validateStringList(soap, "plan", errors, warnings)

        // Validate confidence
        val confidence = soap.optDouble("confidence", -1.0)
        if (confidence < 0 || confidence > 1) {
            errors.add("Invalid SOAP confidence value: $confidence")
        }
    }

    private fun validatePrescription(
        prescription: JSONObject?,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        if (prescription == null) {
            errors.add("Missing prescription")
            return
        }

        // Validate medications
        val medications = prescription.optJSONArray("medications")
        if (medications == null) {
            errors.add("Missing medications array")
        } else {
            for (i in 0 until medications.length()) {
                val medication = medications.optJSONObject(i)
                if (medication == null) {
                    errors.add("Invalid medication at index $i")
                    continue
                }

                // Check required fields
                listOf("name", "dosage", "frequency", "duration", "instructions").forEach { field ->
                    if (!medication.has(field) || medication.optString(field).isBlank()) {
                        errors.add("Missing or empty $field in medication at index $i")
                    }
                }
            }
        }

        // Validate instructions
        validateStringList(prescription, "instructions", errors, warnings)

        // Validate follow-up
        if (!prescription.has("followUp") || prescription.optString("followUp").isBlank()) {
            warnings.add("Missing or empty follow-up instructions")
        }

        // Validate confidence
        val confidence = prescription.optDouble("confidence", -1.0)
        if (confidence < 0 || confidence > 1) {
            errors.add("Invalid prescription confidence value: $confidence")
        }
    }

    private fun validateMetadata(
        metadata: JSONObject?,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        if (metadata == null) {
            errors.add("Missing metadata")
            return
        }

        // Check required fields
        val requiredFields = listOf(
            "speakerTurns" to Int::class.java,
            "totalDuration" to Long::class.java,
            "processingTime" to Long::class.java,
            "modelVersion" to String::class.java,
            "fallbackUsed" to Boolean::class.java
        )

        requiredFields.forEach { (field, type) ->
            if (!metadata.has(field)) {
                errors.add("Missing metadata field: $field")
            } else {
                when (type) {
                    Int::class.java -> {
                        val value = metadata.optInt(field, -1)
                        if (value < 0) {
                            errors.add("Invalid $field value: $value")
                        }
                    }
                    Long::class.java -> {
                        val value = metadata.optLong(field, -1)
                        if (value < 0) {
                            errors.add("Invalid $field value: $value")
                        }
                    }
                    String::class.java -> {
                        val value = metadata.optString(field)
                        if (value.isBlank()) {
                            errors.add("Empty $field value")
                        }
                    }
                    Boolean::class.java -> {
                        if (!metadata.has(field)) {
                            errors.add("Missing $field value")
                        }
                    }
                }
            }
        }
    }

    private fun validateStringList(
        obj: JSONObject,
        field: String,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val array = obj.optJSONArray(field)
        if (array == null) {
            errors.add("Missing $field array")
            return
        }

        if (array.length() == 0) {
            warnings.add("Empty $field array")
            return
        }

        for (i in 0 until array.length()) {
            val item = array.optString(i)
            if (item.isBlank()) {
                errors.add("Empty string in $field at index $i")
            }
        }
    }
}