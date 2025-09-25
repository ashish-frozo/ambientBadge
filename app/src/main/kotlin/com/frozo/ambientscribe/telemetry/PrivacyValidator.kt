package com.frozo.ambientscribe.telemetry

import android.util.Log
import java.util.regex.Pattern

/**
 * Privacy Validator for PT-8 implementation
 * Ensures no PII (Personally Identifiable Information) in telemetry data (ST-8.8)
 */
class PrivacyValidator {
    
    companion object {
        private const val TAG = "PrivacyValidator"
        
        // PII detection patterns
        private val PHONE_PATTERN = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b")
        private val EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b")
        private val SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")
        private val MRN_PATTERN = Pattern.compile("\\bMRN\\s*:?\\s*\\d+\\b", Pattern.CASE_INSENSITIVE)
        private val NAME_PATTERN = Pattern.compile("\\b(?:Dr\\.?|Mr\\.?|Ms\\.?|Mrs\\.?)\\s+[A-Z][a-z]+\\s+[A-Z][a-z]+\\b")
        private val ADDRESS_PATTERN = Pattern.compile("\\b\\d+\\s+[A-Za-z0-9\\s,.-]+(?:Street|St|Avenue|Ave|Road|Rd|Drive|Dr|Lane|Ln|Boulevard|Blvd)\\b", Pattern.CASE_INSENSITIVE)
        
        // Medical PII patterns
        private val PATIENT_ID_PATTERN = Pattern.compile("\\b(?:Patient|Pt)\\.?\\s*ID\\s*:?\\s*\\d+\\b", Pattern.CASE_INSENSITIVE)
        private val DOB_PATTERN = Pattern.compile("\\b(?:DOB|Birth)\\s*:?\\s*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b", Pattern.CASE_INSENSITIVE)
        private val AGE_PATTERN = Pattern.compile("\\b(?:Age)\\s*:?\\s*\\d{1,3}\\s*(?:years?|yrs?)\\b", Pattern.CASE_INSENSITIVE)
        
        // Common PII keywords
        private val PII_KEYWORDS = setOf(
            "patient", "name", "phone", "email", "address", "ssn", "mrn",
            "birth", "age", "gender", "race", "ethnicity", "insurance",
            "policy", "member", "id", "number", "social", "security"
        )
    }
    
    /**
     * Validate that a telemetry event contains no PII
     */
    fun validateEvent(event: TelemetryEvent): Boolean {
        val eventJson = event.toJsonString()
        
        return try {
            // Check for PII patterns
            val hasPhone = PHONE_PATTERN.matcher(eventJson).find()
            val hasEmail = EMAIL_PATTERN.matcher(eventJson).find()
            val hasSSN = SSN_PATTERN.matcher(eventJson).find()
            val hasMRN = MRN_PATTERN.matcher(eventJson).find()
            val hasName = NAME_PATTERN.matcher(eventJson).find()
            val hasAddress = ADDRESS_PATTERN.matcher(eventJson).find()
            val hasPatientId = PATIENT_ID_PATTERN.matcher(eventJson).find()
            val hasDOB = DOB_PATTERN.matcher(eventJson).find()
            val hasAge = AGE_PATTERN.matcher(eventJson).find()
            
            // Check for PII keywords
            val hasPIIKeywords = PII_KEYWORDS.any { keyword ->
                eventJson.contains(keyword, ignoreCase = true)
            }
            
            val hasPII = hasPhone || hasEmail || hasSSN || hasMRN || hasName || 
                        hasAddress || hasPatientId || hasDOB || hasAge || hasPIIKeywords
            
            if (hasPII) {
                Log.w(TAG, "PII detected in telemetry event: ${event.eventType}")
                Log.w(TAG, "PII patterns found: phone=$hasPhone, email=$hasEmail, ssn=$hasSSN, mrn=$hasMRN, name=$hasName, address=$hasAddress, patientId=$hasPatientId, dob=$hasDOB, age=$hasAge, keywords=$hasPIIKeywords")
                return false
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating telemetry event for PII", e)
            false
        }
    }
    
    /**
     * Validate a string for PII content
     */
    fun validateString(content: String): Boolean {
        return try {
            val hasPhone = PHONE_PATTERN.matcher(content).find()
            val hasEmail = EMAIL_PATTERN.matcher(content).find()
            val hasSSN = SSN_PATTERN.matcher(content).find()
            val hasMRN = MRN_PATTERN.matcher(content).find()
            val hasName = NAME_PATTERN.matcher(content).find()
            val hasAddress = ADDRESS_PATTERN.matcher(content).find()
            val hasPatientId = PATIENT_ID_PATTERN.matcher(content).find()
            val hasDOB = DOB_PATTERN.matcher(content).find()
            val hasAge = AGE_PATTERN.matcher(content).find()
            
            val hasPIIKeywords = PII_KEYWORDS.any { keyword ->
                content.contains(keyword, ignoreCase = true)
            }
            
            val hasPII = hasPhone || hasEmail || hasSSN || hasMRN || hasName || 
                        hasAddress || hasPatientId || hasDOB || hasAge || hasPIIKeywords
            
            if (hasPII) {
                Log.w(TAG, "PII detected in string content")
                return false
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating string for PII", e)
            false
        }
    }
    
    /**
     * Sanitize a string by removing PII
     */
    fun sanitizeString(content: String): String {
        var sanitized = content
        
        try {
            // Replace phone numbers
            sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[PHONE_REDACTED]")
            
            // Replace email addresses
            sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[EMAIL_REDACTED]")
            
            // Replace SSNs
            sanitized = SSN_PATTERN.matcher(sanitized).replaceAll("[SSN_REDACTED]")
            
            // Replace MRNs
            sanitized = MRN_PATTERN.matcher(sanitized).replaceAll("[MRN_REDACTED]")
            
            // Replace names
            sanitized = NAME_PATTERN.matcher(sanitized).replaceAll("[NAME_REDACTED]")
            
            // Replace addresses
            sanitized = ADDRESS_PATTERN.matcher(sanitized).replaceAll("[ADDRESS_REDACTED]")
            
            // Replace patient IDs
            sanitized = PATIENT_ID_PATTERN.matcher(sanitized).replaceAll("[PATIENT_ID_REDACTED]")
            
            // Replace DOBs
            sanitized = DOB_PATTERN.matcher(sanitized).replaceAll("[DOB_REDACTED]")
            
            // Replace ages
            sanitized = AGE_PATTERN.matcher(sanitized).replaceAll("[AGE_REDACTED]")
            
            // Replace PII keywords with generic terms
            PII_KEYWORDS.forEach { keyword ->
                sanitized = sanitized.replace(Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE), "[REDACTED]")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing string", e)
            return "[SANITIZATION_ERROR]"
        }
        
        return sanitized
    }
    
    /**
     * Validate telemetry event fields for PII
     */
    fun validateEventFields(event: TelemetryEvent): ValidationResult {
        val violations = mutableListOf<String>()
        
        try {
            // Check encounter ID format (should be UUID)
            if (!isValidUUID(event.encounterId)) {
                violations.add("Invalid encounter ID format")
            }
            
            // Check clinic ID (should not contain PII)
            event.clinicId?.let { clinicId ->
                if (!validateString(clinicId)) {
                    violations.add("Clinic ID contains PII")
                }
            }
            
            // Check device tier (should be A or B)
            if (event.deviceTier !in listOf("A", "B")) {
                violations.add("Invalid device tier")
            }
            
            // Check timestamp format
            if (!isValidTimestamp(event.timestamp)) {
                violations.add("Invalid timestamp format")
            }
            
            // Event-specific validation
            when (event) {
                is TranscriptionCompleteEvent -> {
                    if (event.modelVersion.isBlank()) {
                        violations.add("Model version is blank")
                    }
                }
                is ReviewCompleteEvent -> {
                    if (event.editRatePercent < 0 || event.editRatePercent > 100) {
                        violations.add("Invalid edit rate percentage")
                    }
                }
                is ExportSuccessEvent -> {
                    if (event.pdfSizeKb < 0) {
                        violations.add("Invalid PDF size")
                    }
                }
                is ThermalEvent -> {
                    if (event.thermalState !in listOf("NORMAL", "WARNING", "SEVERE")) {
                        violations.add("Invalid thermal state")
                    }
                }
                is EditCauseCodeEvent -> {
                    // Handle edit cause code events
                }
                is EncounterStartEvent -> {
                    // Handle encounter start events
                }
                is BulkEditAppliedEvent -> {
                    // Handle bulk edit events
                }
                is CrashFreeSessionEvent -> {
                    // Handle crash-free session events
                }
                is PolicyToggleEvent -> {
                    // Handle policy toggle events
                }
                is TimeSkewEvent -> {
                    // Handle time skew events
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating event fields", e)
            violations.add("Validation error: ${e.message}")
        }
        
        return ValidationResult(
            isValid = violations.isEmpty(),
            violations = violations
        )
    }
    
    /**
     * Check if string is a valid UUID
     */
    private fun isValidUUID(uuid: String): Boolean {
        return try {
            java.util.UUID.fromString(uuid)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if string is a valid timestamp
     */
    private fun isValidTimestamp(timestamp: String): Boolean {
        return try {
            java.time.Instant.parse(timestamp)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get PII detection statistics
     */
    fun getDetectionStats(): PIIDetectionStats {
        return PIIDetectionStats(
            totalValidations = 0, // This would be tracked in a real implementation
            piiDetections = 0,
            phoneDetections = 0,
            emailDetections = 0,
            ssnDetections = 0,
            mrnDetections = 0,
            nameDetections = 0,
            addressDetections = 0,
            patientIdDetections = 0,
            dobDetections = 0,
            ageDetections = 0,
            keywordDetections = 0
        )
    }
}

/**
 * Data classes for privacy validation
 */

data class ValidationResult(
    val isValid: Boolean,
    val violations: List<String>
)

data class PIIDetectionStats(
    val totalValidations: Int,
    val piiDetections: Int,
    val phoneDetections: Int,
    val emailDetections: Int,
    val ssnDetections: Int,
    val mrnDetections: Int,
    val nameDetections: Int,
    val addressDetections: Int,
    val patientIdDetections: Int,
    val dobDetections: Int,
    val ageDetections: Int,
    val keywordDetections: Int
)
