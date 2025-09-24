package com.frozo.ambientscribe.ai

import android.content.Context
import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize

/**
 * Medical entity extraction and ASR bias correction for ST-2.9
 */
class MedicalEntityExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "MedicalEntityExtractor"
    }
    
    @Parcelize
    data class EntityExtractionResult(
        val entities: List<MedicalEntity>,
        val correctedText: String,
        val confidence: Float,
        val corrections: List<TextCorrection>,
        val processingTimeMs: Long
    ) : Parcelable
    
    @Parcelize
    data class MedicalEntity(
        val text: String,
        val type: EntityType,
        val startIndex: Int,
        val endIndex: Int,
        val confidence: Float
    ) : Parcelable
    
    @Parcelize
    data class TextCorrection(
        val originalText: String,
        val correctedText: String,
        val confidence: Float
    ) : Parcelable
    
    enum class EntityType {
        MEDICATION, SYMPTOM, DIAGNOSIS, VITAL_SIGN
    }
    
    private val medicalTerms = listOf(
        "paracetamol", "ibuprofen", "amoxicillin", "headache", "fever", 
        "cough", "blood pressure", "temperature", "twice daily"
    )
    
    suspend fun extractEntities(transcript: String): EntityExtractionResult {
        val startTime = System.currentTimeMillis()
        
        val entities = mutableListOf<MedicalEntity>()
        var correctedText = transcript
        val corrections = mutableListOf<TextCorrection>()
        
        // Simple entity extraction
        medicalTerms.forEach { term ->
            if (correctedText.contains(term, ignoreCase = true)) {
                val startIndex = correctedText.indexOf(term, ignoreCase = true)
                entities.add(MedicalEntity(
                    text = term,
                    type = EntityType.MEDICATION,
                    startIndex = startIndex,
                    endIndex = startIndex + term.length,
                    confidence = 0.8f
                ))
            }
        }
        
        // Simple corrections
        val commonCorrections = mapOf(
            "panadol" to "paracetamol",
            "advil" to "ibuprofen",
            "bp" to "blood pressure"
        )
        
        commonCorrections.forEach { (from, to) ->
            if (correctedText.contains(from, ignoreCase = true)) {
                correctedText = correctedText.replace(from, to, ignoreCase = true)
                corrections.add(TextCorrection(from, to, 0.9f))
            }
        }
        
        return EntityExtractionResult(
            entities = entities,
            correctedText = correctedText,
            confidence = 0.8f,
            corrections = corrections,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    fun getMedicalBiasTerms(): List<String> = medicalTerms
    
    fun correctWithFormulary(transcript: String): String {
        var corrected = transcript
        corrected = corrected.replace("panadol", "paracetamol", ignoreCase = true)
        corrected = corrected.replace("advil", "ibuprofen", ignoreCase = true)
        return corrected
    }
}