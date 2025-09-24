package com.frozo.ambientscribe.ai

import android.util.Log
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Locale

/**
 * Fallback SOAP note generator for ST-2.4
 * Provides rules-based generation when LLM fails
 */
class FallbackSOAPGenerator {
    
    companion object {
        private const val TAG = "FallbackSOAPGenerator"
    }
    
    @Parcelize
    data class FallbackResult(
        val soap: LLMService.SOAPNote,
        val prescription: LLMService.Prescription
    ) : Parcelable
    
    // Medical keywords for rule-based extraction
    private val symptomKeywords = mapOf(
        "headache" to "Patient reports headache",
        "fever" to "Patient has fever",
        "cough" to "Patient presents with cough",
        "pain" to "Patient complains of pain",
        "nausea" to "Patient experiences nausea",
        "dizziness" to "Patient reports dizziness",
        "fatigue" to "Patient feels fatigued",
        "chest pain" to "Patient has chest pain",
        "shortness of breath" to "Patient reports breathing difficulty"
    )
    
    private val objectiveKeywords = mapOf(
        "temperature" to "Temperature recorded",
        "blood pressure" to "Blood pressure measured",
        "heart rate" to "Heart rate assessed", 
        "weight" to "Weight documented",
        "examination" to "Physical examination performed"
    )
    
    private val assessmentKeywords = mapOf(
        "infection" to "Possible infection",
        "hypertension" to "Hypertension",
        "diabetes" to "Diabetes mellitus",
        "migraine" to "Migraine headache",
        "gastritis" to "Gastritis",
        "bronchitis" to "Bronchitis"
    )
    
    private val planKeywords = mapOf(
        "medication" to "Prescribe medication as indicated",
        "rest" to "Advise rest and adequate sleep",
        "fluids" to "Increase fluid intake",
        "follow up" to "Schedule follow-up visit",
        "monitoring" to "Continue monitoring symptoms"
    )
    
    private val commonMedications = listOf(
        LLMService.Medication(
            name = "paracetamol",
            dosage = "500mg", 
            frequency = "twice daily",
            duration = "3 days",
            instructions = "Take with food"
        ),
        LLMService.Medication(
            name = "ibuprofen",
            dosage = "400mg",
            frequency = "three times daily", 
            duration = "5 days",
            instructions = "Take after meals"
        )
    )
    
    /**
     * Generate SOAP note and prescription using rule-based approach
     */
    fun generateFromTranscript(transcript: String): FallbackResult {
        Log.i(TAG, "Generating fallback SOAP note from transcript: ${transcript.length} chars")
        
        val lowerTranscript = transcript.lowercase(Locale.ROOT)
        
        // Extract SOAP components using keyword matching
        val subjective = extractSubjective(lowerTranscript)
        val objective = extractObjective(lowerTranscript)
        val assessment = extractAssessment(lowerTranscript)  
        val plan = extractPlan(lowerTranscript)
        
        // Generate basic prescription based on symptoms
        val medications = generateMedications(lowerTranscript)
        val instructions = generateInstructions(lowerTranscript)
        
        val soapNote = LLMService.SOAPNote(
            subjective = subjective,
            objective = objective,
            assessment = assessment,
            plan = plan,
            confidence = 0.6f // Lower confidence for rule-based generation
        )
        
        val prescription = LLMService.Prescription(
            medications = medications,
            instructions = instructions,
            followUp = "Follow up in 3-5 days if symptoms persist",
            confidence = 0.6f
        )
        
        Log.i(TAG, "Generated fallback SOAP: S=${subjective.size}, O=${objective.size}, A=${assessment.size}, P=${plan.size}")
        
        return FallbackResult(soapNote, prescription)
    }
    
    private fun extractSubjective(transcript: String): List<String> {
        val subjective = mutableListOf<String>()
        
        symptomKeywords.forEach { (keyword, description) ->
            if (transcript.contains(keyword)) {
                subjective.add(description)
            }
        }
        
        // Add generic entries if none found
        if (subjective.isEmpty()) {
            subjective.addAll(listOf(
                "Patient presents with chief complaint",
                "Patient reports symptoms as described"
            ))
        }
        
        return subjective.take(5) // Limit to 5 items as per requirements
    }
    
    private fun extractObjective(transcript: String): List<String> {
        val objective = mutableListOf<String>()
        
        objectiveKeywords.forEach { (keyword, description) ->
            if (transcript.contains(keyword)) {
                objective.add(description)
            }
        }
        
        // Add generic vitals if none found
        if (objective.isEmpty()) {
            objective.addAll(listOf(
                "Vital signs within normal limits",
                "Physical examination unremarkable"
            ))
        }
        
        return objective.take(5)
    }
    
    private fun extractAssessment(transcript: String): List<String> {
        val assessment = mutableListOf<String>()
        
        assessmentKeywords.forEach { (keyword, description) ->
            if (transcript.contains(keyword)) {
                assessment.add(description)
            }
        }
        
        // Add generic assessment if none found
        if (assessment.isEmpty()) {
            // Try to infer from symptoms
            when {
                transcript.contains("headache") -> assessment.add("Tension headache")
                transcript.contains("fever") && transcript.contains("cough") -> 
                    assessment.add("Upper respiratory tract infection")
                transcript.contains("pain") -> assessment.add("Pain syndrome")
                else -> assessment.add("Symptomatic presentation requiring evaluation")
            }
        }
        
        return assessment.take(5)
    }
    
    private fun extractPlan(transcript: String): List<String> {
        val plan = mutableListOf<String>()
        
        planKeywords.forEach { (keyword, description) ->
            if (transcript.contains(keyword)) {
                plan.add(description)
            }
        }
        
        // Add standard plan elements if none found
        if (plan.isEmpty()) {
            plan.addAll(listOf(
                "Symptomatic treatment as appropriate",
                "Patient education provided",
                "Return if symptoms worsen"
            ))
        }
        
        return plan.take(5)
    }
    
    private fun generateMedications(transcript: String): List<LLMService.Medication> {
        val medications = mutableListOf<LLMService.Medication>()
        
        // Simple rule-based medication selection
        when {
            transcript.contains("headache") || transcript.contains("pain") -> {
                medications.add(commonMedications[0]) // paracetamol
            }
            transcript.contains("fever") -> {
                medications.add(commonMedications[0]) // paracetamol for fever
            }
            transcript.contains("inflammation") || transcript.contains("swelling") -> {
                medications.add(commonMedications[1]) // ibuprofen for inflammation
            }
        }
        
        // Add generic medication if none selected
        if (medications.isEmpty()) {
            medications.add(commonMedications[0]) // Default to paracetamol
        }
        
        return medications.take(3) // Limit to 3 medications
    }
    
    private fun generateInstructions(transcript: String): List<String> {
        val instructions = mutableListOf<String>()
        
        // Add context-appropriate instructions
        when {
            transcript.contains("fever") -> {
                instructions.add("Maintain adequate hydration")
                instructions.add("Rest and avoid strenuous activity")
            }
            transcript.contains("cough") -> {
                instructions.add("Avoid irritants and stay hydrated") 
                instructions.add("Use humidifier if available")
            }
            transcript.contains("headache") -> {
                instructions.add("Ensure adequate rest")
                instructions.add("Apply cold compress if helpful")
            }
        }
        
        // Add general instructions if none specific
        if (instructions.isEmpty()) {
            instructions.addAll(listOf(
                "Take medications as prescribed",
                "Maintain adequate rest and nutrition",
                "Monitor symptoms closely"
            ))
        }
        
        return instructions.take(3)
    }
}