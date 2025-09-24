package com.frozo.ambientscribe.ai

import android.content.Context
import android.os.Parcelable
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Service for interacting with local LLM
 */
class LLMService {

    companion object {
        private const val TAG = "LLMService"
        private const val MODEL_FILE = "models/llama_1.1b_q4.bin"
        private const val VOCAB_FILE = "vocab/llm_vocab.json"
        private const val CONFIG_FILE = "config/llm_config.json"
    }

    private var isInitialized = false
    private var nativeHandle: Long = 0

    /**
     * SOAP note data class
     */
    @Parcelize
    data class SOAPNote(
        val subjective: List<String>,
        val objective: List<String>,
        val assessment: List<String>,
        val plan: List<String>,
        val confidence: Float
    ) : Parcelable

    /**
     * Prescription medication data class
     */
    @Parcelize
data class Medication(
            val name: String,
            val dosage: String,
            val frequency: String,
            val duration: String,
            val instructions: String,
            val isGeneric: Boolean = true
    ) : Parcelable

    /**
     * Prescription data class
     */
    @Parcelize
    data class Prescription(
        val medications: List<Medication>,
        val instructions: List<String>,
        val followUp: String,
        val confidence: Float
    ) : Parcelable

    /**
     * Encounter metadata data class
     */
    @Parcelize
    data class EncounterMetadata(
        val speakerTurns: Int,
        val totalDuration: Long,
        val processingTime: Long,
        val modelVersion: String,
        val fallbackUsed: Boolean,
        val encounterId: String,
        val patientId: String
    ) : Parcelable

    /**
     * Encounter note data class
     */
    @Parcelize
    data class EncounterNote(
        val soap: SOAPNote,
        val prescription: Prescription,
        val metadata: EncounterMetadata
    ) : Parcelable

    /**
     * Initialize LLM service
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                return@withContext true
            }

            // Load model
            val modelFile = File(context.filesDir, MODEL_FILE)
            if (!modelFile.exists()) {
                context.assets.open(MODEL_FILE).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Load vocabulary
            val vocabFile = File(context.filesDir, VOCAB_FILE)
            if (!vocabFile.exists()) {
                context.assets.open(VOCAB_FILE).use { input ->
                    vocabFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Load config
            val configFile = File(context.filesDir, CONFIG_FILE)
            if (!configFile.exists()) {
                context.assets.open(CONFIG_FILE).use { input ->
                    configFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Initialize native model
            nativeHandle = initializeNative(
                modelFile.absolutePath,
                vocabFile.absolutePath,
                configFile.absolutePath
            )
            isInitialized = true

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LLM: ${e.message}", e)
            false
        }
    }

    /**
     * Generate encounter note
     */
    suspend fun generateEncounterNote(audioFile: File): EncounterNote = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized) {
                throw IllegalStateException("LLM not initialized")
            }

            // Generate note using native model
            val result = generateNative(nativeHandle, audioFile.absolutePath)

            // Parse result JSON
            val json = JSONObject(result)
            val soap = json.getJSONObject("soap")
            val prescription = json.getJSONObject("prescription")
            val metadata = json.getJSONObject("metadata")

            EncounterNote(
                soap = SOAPNote(
                    subjective = soap.getJSONArray("subjective").toStringList(),
                    objective = soap.getJSONArray("objective").toStringList(),
                    assessment = soap.getJSONArray("assessment").toStringList(),
                    plan = soap.getJSONArray("plan").toStringList(),
                    confidence = soap.getDouble("confidence").toFloat()
                ),
                prescription = Prescription(
                    medications = (0 until prescription.getJSONArray("medications").length()).map { i ->
                        val medication = prescription.getJSONArray("medications").getJSONObject(i)
                        Medication(
                            name = medication.getString("name"),
                            dosage = medication.getString("dosage"),
                            frequency = medication.getString("frequency"),
                            duration = medication.getString("duration"),
                            instructions = medication.getString("instructions")
                        )
                    },
                    instructions = prescription.getJSONArray("instructions").toStringList(),
                    followUp = prescription.getString("followUp"),
                    confidence = prescription.getDouble("confidence").toFloat()
                ),
                metadata = EncounterMetadata(
                    speakerTurns = metadata.getInt("speakerTurns"),
                    totalDuration = metadata.getLong("totalDuration"),
                    processingTime = metadata.getLong("processingTime"),
                    modelVersion = metadata.getString("modelVersion"),
                    fallbackUsed = metadata.getBoolean("fallbackUsed"),
                    encounterId = metadata.getString("encounterId"),
                    patientId = metadata.getString("patientId")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating encounter note: ${e.message}", e)
            throw e
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        if (isInitialized) {
            cleanupNative(nativeHandle)
            isInitialized = false
            nativeHandle = 0
        }
    }

    private external fun initializeNative(
        modelPath: String,
        vocabPath: String,
        configPath: String
    ): Long

    private external fun generateNative(
        handle: Long,
        audioPath: String
    ): String

    private external fun cleanupNative(handle: Long)

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { getString(it) }
    }

    init {
        System.loadLibrary("llama")
    }
}