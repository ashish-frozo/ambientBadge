package com.frozo.ambientscribe.ai

import android.content.Context
import android.util.Log
import com.frozo.ambientscribe.performance.DeviceCapabilityDetector
import com.frozo.ambientscribe.performance.ThermalManager
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Main AI service that coordinates LLM, validation, and fallback generation
 */
class AIService(
    private val context: Context,
    private val deviceCapabilityDetector: DeviceCapabilityDetector,
    private val thermalManager: ThermalManager,
    private val metricsCollector: MetricsCollector
) {

    companion object {
        private const val TAG = "AIService"
        private const val MIN_CONFIDENCE = 0.6f
        private const val HIGH_CONFIDENCE = 0.8f
    }

    private val llmService = LLMService()
    private val jsonSchemaValidator = JsonSchemaValidator(context)
    private val prescriptionValidator = PrescriptionValidator(context)
    private val formularyService = FormularyService(context)
    private val aiResourceManager = AIResourceManager(context, deviceCapabilityDetector, thermalManager, metricsCollector)

    /**
     * Confidence level
     */
    enum class ConfidenceLevel {
        GREEN,  // ≥0.8
        AMBER,  // 0.6-0.79
        RED     // <0.6
    }

    /**
     * Initialize AI service
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // Initialize LLM
            llmService.initialize(context)

            // Load resources
            aiResourceManager.loadResources()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AI service: ${e.message}", e)
            false
        }
    }

    /**
     * Generate encounter note
     */
    suspend fun generateEncounterNote(
        audioFile: File,
        speakerTurns: Int,
        totalDuration: Long
    ): Result<LLMService.EncounterNote> = withContext(Dispatchers.Default) {
        try {
            // Check if AI should be throttled
            if (aiResourceManager.shouldThrottleAI()) {
                return@withContext Result.failure(Exception("AI is currently throttled"))
            }

            // Generate note using LLM
            val note = llmService.generateEncounterNote(audioFile)

            // Validate note
            val validationResult = validateEncounterNote(note)
            if (!validationResult.isValid) {
                Log.w(TAG, "Validation failed: ${validationResult.errors}")
                // Try fallback generation
                val fallbackNote = generateFallbackNote(audioFile, validationResult.errors)
                return@withContext Result.success(fallbackNote)
            }

            // Validate prescriptions
            note.prescription.medications.forEach { medication ->
                val prescriptionResult = prescriptionValidator.validateMedication(medication)
                if (prescriptionResult.nameError != null ||
                    prescriptionResult.dosageError != null ||
                    prescriptionResult.frequencyError != null ||
                    prescriptionResult.durationError != null ||
                    prescriptionResult.instructionsError != null) {
                    Log.w(TAG, "Prescription validation failed: $prescriptionResult")
                }
            }

            // Check formulary compliance
            note.prescription.medications.forEach { medication ->
                if (!formularyService.isInFormulary(medication.name)) {
                    Log.w(TAG, "Medication not in formulary: ${medication.name}")
                    val alternative = formularyService.suggestGenericAlternative(medication.name)
                    if (alternative != null) {
                        Log.i(TAG, "Suggested alternative: $alternative")
                    }
                }
            }

            Result.success(note)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating encounter note: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Validate encounter note
     */
    private fun validateEncounterNote(note: LLMService.EncounterNote): JsonSchemaValidator.ValidationResult {
        val json = JSONObject().apply {
            put("version", "1.0.0")
            put("timestamp", System.currentTimeMillis())
            put("soap", JSONObject().apply {
                put("subjective", note.soap.subjective)
                put("objective", note.soap.objective)
                put("assessment", note.soap.assessment)
                put("plan", note.soap.plan)
                put("confidence", note.soap.confidence)
            })
            put("prescription", JSONObject().apply {
                put("medications", note.prescription.medications.map { medication ->
                    JSONObject().apply {
                        put("name", medication.name)
                        put("dosage", medication.dosage)
                        put("frequency", medication.frequency)
                        put("duration", medication.duration)
                        put("instructions", medication.instructions)
                    }
                })
                put("instructions", note.prescription.instructions)
                put("followUp", note.prescription.followUp)
                put("confidence", note.prescription.confidence)
            })
            put("metadata", JSONObject().apply {
                put("speakerTurns", note.metadata.speakerTurns)
                put("totalDuration", note.metadata.totalDuration)
                put("processingTime", note.metadata.processingTime)
                put("modelVersion", note.metadata.modelVersion)
                put("fallbackUsed", note.metadata.fallbackUsed)
            })
        }

        return jsonSchemaValidator.validate(json.toString())
    }

    /**
     * Get confidence level
     */
    private fun getConfidenceLevel(confidence: Float): ConfidenceLevel {
        return when {
            confidence >= HIGH_CONFIDENCE -> ConfidenceLevel.GREEN
            confidence >= MIN_CONFIDENCE -> ConfidenceLevel.AMBER
            else -> ConfidenceLevel.RED
        }
    }

    /**
     * Generate fallback note
     */
    private suspend fun generateFallbackNote(
        audioFile: File,
        errors: List<String>
    ): LLMService.EncounterNote = withContext(Dispatchers.Default) {
        // Implement fallback generation logic
        // This could use a simpler model or rule-based approach
        TODO("Implement fallback generation")
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        llmService.cleanup()
        aiResourceManager.cleanup()
    }
}