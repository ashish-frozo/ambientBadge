package com.frozo.ambientscribe.ai

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Service for managing clinic formulary and medication validation
 */
class FormularyService(private val context: Context) {

    companion object {
        private const val TAG = "FormularyService"
        private const val FORMULARY_FILE = "formulary/formulary.json"
    }

    private val formulary: JSONObject by lazy {
        loadFormulary()
    }

    private fun loadFormulary(): JSONObject {
        return try {
            val inputStream = context.assets.open(FORMULARY_FILE)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            JSONObject(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading formulary: ${e.message}", e)
            JSONObject()
        }
    }

    /**
     * Check if medication is in formulary
     */
    fun isInFormulary(medicationName: String): Boolean {
        return try {
            val medications = formulary.optJSONArray("medications") ?: return false
            for (i in 0 until medications.length()) {
                val medication = medications.optJSONObject(i) ?: continue
                if (medication.optString("name").equals(medicationName, ignoreCase = true)) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking formulary: ${e.message}", e)
            false
        }
    }

    /**
     * Suggest generic alternative for medication
     */
    fun suggestGenericAlternative(medicationName: String): String? {
        return try {
            val medications = formulary.optJSONArray("medications") ?: return null
            for (i in 0 until medications.length()) {
                val medication = medications.optJSONObject(i) ?: continue
                if (medication.optString("name").equals(medicationName, ignoreCase = true)) {
                    return medication.optString("generic_alternative")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error suggesting alternative: ${e.message}", e)
            null
        }
    }

    /**
     * Get medication details
     */
    fun getMedicationDetails(medicationName: String): MedicationDetails? {
        return try {
            val medications = formulary.optJSONArray("medications") ?: return null
            for (i in 0 until medications.length()) {
                val medication = medications.optJSONObject(i) ?: continue
                if (medication.optString("name").equals(medicationName, ignoreCase = true)) {
                    return MedicationDetails(
                        name = medication.optString("name"),
                        genericName = medication.optString("generic_name"),
                        dosageForm = medication.optString("dosage_form"),
                        strength = medication.optString("strength"),
                        contraindications = medication.optJSONArray("contraindications")?.let { array ->
                            List(array.length()) { array.optString(it) }
                        } ?: emptyList(),
                        interactions = medication.optJSONArray("interactions")?.let { array ->
                            List(array.length()) { array.optString(it) }
                        } ?: emptyList(),
                        sideEffects = medication.optJSONArray("side_effects")?.let { array ->
                            List(array.length()) { array.optString(it) }
                        } ?: emptyList()
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting medication details: ${e.message}", e)
            null
        }
    }

    /**
     * Medication details data class
     */
    data class MedicationDetails(
        val name: String,
        val genericName: String,
        val dosageForm: String,
        val strength: String,
        val contraindications: List<String>,
        val interactions: List<String>,
        val sideEffects: List<String>
    )
}
