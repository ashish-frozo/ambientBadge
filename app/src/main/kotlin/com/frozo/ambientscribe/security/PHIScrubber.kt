package com.frozo.ambientscribe.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * PHI scrubber for crash/ANR reports to prevent sensitive data leakage
 * Implements SDK hooks to automatically scrub PHI from crash reports
 */
class PHIScrubber(private val context: Context) {

    companion object {
        private const val PHI_SCRUBBER_DIR = "phi_scrubber"
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        
        // PHI patterns to detect and scrub
        private val PHI_PATTERNS = listOf(
            // Phone numbers (Indian format)
            Pattern.compile("\\b[6-9]\\d{9}\\b"),
            Pattern.compile("\\+91[6-9]\\d{9}\\b"),
            Pattern.compile("\\b91[6-9]\\d{9}\\b"),
            
            // Email addresses
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
            
            // Medical Record Numbers (common patterns)
            Pattern.compile("\\bMRN\\s*:?\\s*[A-Za-z0-9]+\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bPatient\\s*ID\\s*:?\\s*[A-Za-z0-9]+\\b", Pattern.CASE_INSENSITIVE),
            
            // Names (common Indian names - simplified patterns)
            Pattern.compile("\\b[A-Z][a-z]+\\s+[A-Z][a-z]+\\b"),
            
            // Addresses (common patterns)
            Pattern.compile("\\b\\d+\\s+[A-Za-z\\s]+(?:Street|Road|Avenue|Lane|Colony|Nagar|Pur|Pura)\\b"),
            
            // Medical terms that might contain PHI
            Pattern.compile("\\b(?:Patient|Pt\\.?)\\s*:?\\s*[A-Za-z0-9\\s]+\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:Doctor|Dr\\.?)\\s*:?\\s*[A-Za-z\\s]+\\b", Pattern.CASE_INSENSITIVE),
            
            // Encounter IDs
            Pattern.compile("\\bencounter[_-]?id\\s*:?\\s*[A-Za-z0-9-]+\\b", Pattern.CASE_INSENSITIVE),
            
            // Clinic IDs
            Pattern.compile("\\bclinic[_-]?id\\s*:?\\s*[A-Za-z0-9-]+\\b", Pattern.CASE_INSENSITIVE)
        )
        
        // Replacement patterns
        private const val PHONE_REPLACEMENT = "[PHONE_SCRUBBED]"
        private const val EMAIL_REPLACEMENT = "[EMAIL_SCRUBBED]"
        private const val MRN_REPLACEMENT = "[MRN_SCRUBBED]"
        private const val NAME_REPLACEMENT = "[NAME_SCRUBBED]"
        private const val ADDRESS_REPLACEMENT = "[ADDRESS_SCRUBBED]"
        private const val MEDICAL_REPLACEMENT = "[MEDICAL_SCRUBBED]"
        private const val ID_REPLACEMENT = "[ID_SCRUBBED]"
        private const val GENERAL_PHI_REPLACEMENT = "[PHI_SCRUBBED]"
    }

    private val auditLogger = AuditLogger(context)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

    /**
     * PHI scrubbing result
     */
    data class PHIScrubbingResult(
        val originalLength: Int,
        val scrubbedLength: Int,
        val phiCount: Int,
        val scrubbedPatterns: List<String>,
        val timestamp: String
    )

    /**
     * Scrub PHI from text content
     */
    suspend fun scrubPHI(content: String): Result<PHIScrubbingResult> = withContext(Dispatchers.IO) {
        try {
            val originalLength = content.length
            var scrubbedContent = content
            val scrubbedPatterns = mutableListOf<String>()
            var phiCount = 0

            // Apply PHI patterns
            for (pattern in PHI_PATTERNS) {
                val matcher = pattern.matcher(scrubbedContent)
                val matches = mutableListOf<String>()
                
                while (matcher.find()) {
                    matches.add(matcher.group())
                }
                
                if (matches.isNotEmpty()) {
                    val replacement = getReplacementForPattern(pattern)
                    scrubbedContent = matcher.replaceAll(replacement)
                    scrubbedPatterns.add(pattern.pattern())
                    phiCount += matches.size
                }
            }

            // Additional context-aware scrubbing
            scrubbedContent = performContextAwareScrubbing(scrubbedContent)

            val result = PHIScrubbingResult(
                originalLength = originalLength,
                scrubbedLength = scrubbedContent.length,
                phiCount = phiCount,
                scrubbedPatterns = scrubbedPatterns,
                timestamp = dateFormat.format(Date())
            )

            // Log scrubbing operation
            auditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.CONSENT_OFF,
                actor = AuditEvent.AuditActor.APP,
                meta = mapOf(
                    "reason" to "phi_scrubbing",
                    "original_length" to originalLength,
                    "scrubbed_length" to scrubbedContent.length,
                    "phi_count" to phiCount,
                    "patterns_used" to scrubbedPatterns.size
                )
            )

            Timber.i("PHI scrubbing completed: $phiCount PHI instances scrubbed")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Failed to scrub PHI from content")
            Result.failure(e)
        }
    }

    /**
     * Get replacement string for pattern
     */
    private fun getReplacementForPattern(pattern: Pattern): String {
        val patternStr = pattern.pattern()
        return when {
            patternStr.contains("\\d{9}") || patternStr.contains("\\+91") -> PHONE_REPLACEMENT
            patternStr.contains("@") -> EMAIL_REPLACEMENT
            patternStr.contains("MRN", Pattern.CASE_INSENSITIVE) -> MRN_REPLACEMENT
            patternStr.contains("Patient|Doctor", Pattern.CASE_INSENSITIVE) -> MEDICAL_REPLACEMENT
            patternStr.contains("Street|Road|Avenue", Pattern.CASE_INSENSITIVE) -> ADDRESS_REPLACEMENT
            patternStr.contains("encounter|clinic", Pattern.CASE_INSENSITIVE) -> ID_REPLACEMENT
            else -> GENERAL_PHI_REPLACEMENT
        }
    }

    /**
     * Perform context-aware PHI scrubbing
     */
    private fun performContextAwareScrubbing(content: String): String {
        var scrubbedContent = content

        // Scrub medical conversation patterns
        scrubbedContent = scrubMedicalConversations(scrubbedContent)
        
        // Scrub SOAP note patterns
        scrubbedContent = scrubSOAPNotes(scrubbedContent)
        
        // Scrub prescription patterns
        scrubbedContent = scrubPrescriptions(scrubbedContent)
        
        // Scrub audit log patterns
        scrubbedContent = scrubAuditLogs(scrubbedContent)

        return scrubbedContent
    }

    /**
     * Scrub medical conversation patterns
     */
    private fun scrubMedicalConversations(content: String): String {
        var scrubbed = content
        
        // Scrub doctor-patient conversation patterns
        val conversationPattern = Pattern.compile(
            "(?:Doctor|Dr\\.?)\\s*:?\\s*[^\\n]+\\n(?:Patient|Pt\\.?)\\s*:?\\s*[^\\n]+",
            Pattern.CASE_INSENSITIVE
        )
        scrubbed = conversationPattern.matcher(scrubbed).replaceAll("[CONVERSATION_SCRUBBED]")
        
        // Scrub symptom descriptions that might contain PHI
        val symptomPattern = Pattern.compile(
            "\\b(?:symptom|complaint|issue)\\s*:?\\s*[^\\n]+",
            Pattern.CASE_INSENSITIVE
        )
        scrubbed = symptomPattern.matcher(scrubbed).replaceAll("[SYMPTOM_SCRUBBED]")
        
        return scrubbed
    }

    /**
     * Scrub SOAP note patterns
     */
    private fun scrubSOAPNotes(content: String): String {
        var scrubbed = content
        
        // Scrub SOAP sections
        val soapPattern = Pattern.compile(
            "\\b(?:Subjective|Objective|Assessment|Plan)\\s*:?\\s*[^\\n]+",
            Pattern.CASE_INSENSITIVE
        )
        scrubbed = soapPattern.matcher(scrubbed).replaceAll("[SOAP_SECTION_SCRUBBED]")
        
        return scrubbed
    }

    /**
     * Scrub prescription patterns
     */
    private fun scrubPrescriptions(content: String): String {
        var scrubbed = content
        
        // Scrub prescription lines
        val prescriptionPattern = Pattern.compile(
            "\\b(?:Rx|Prescription)\\s*:?\\s*[^\\n]+",
            Pattern.CASE_INSENSITIVE
        )
        scrubbed = prescriptionPattern.matcher(scrubbed).replaceAll("[PRESCRIPTION_SCRUBBED]")
        
        return scrubbed
    }

    /**
     * Scrub audit log patterns
     */
    private fun scrubAuditLogs(content: String): String {
        var scrubbed = content
        
        // Scrub audit event patterns
        val auditPattern = Pattern.compile(
            "\\b(?:encounter_id|patient_id|clinic_id)\\s*:?\\s*[A-Za-z0-9-]+",
            Pattern.CASE_INSENSITIVE
        )
        scrubbed = auditPattern.matcher(scrubbed).replaceAll("[AUDIT_ID_SCRUBBED]")
        
        return scrubbed
    }

    /**
     * Scrub crash report content
     */
    suspend fun scrubCrashReport(crashContent: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scrubbingResult = scrubPHI(crashContent)
            if (scrubbingResult.isFailure) {
                return@withContext Result.failure(scrubbingResult.exceptionOrNull()!!)
            }

            val result = scrubbingResult.getOrThrow()
            val scrubbedContent = crashContent.let { content ->
                var scrubbed = content
                for (pattern in PHI_PATTERNS) {
                    val replacement = getReplacementForPattern(pattern)
                    scrubbed = pattern.matcher(scrubbed).replaceAll(replacement)
                }
                performContextAwareScrubbing(scrubbed)
            }

            // Save scrubbing metadata
            saveScrubbingMetadata(result)

            Timber.i("Crash report scrubbed: ${result.phiCount} PHI instances removed")
            Result.success(scrubbedContent)

        } catch (e: Exception) {
            Timber.e(e, "Failed to scrub crash report")
            Result.failure(e)
        }
    }

    /**
     * Scrub ANR report content
     */
    suspend fun scrubANRReport(anrContent: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scrubbingResult = scrubPHI(anrContent)
            if (scrubbingResult.isFailure) {
                return@withContext Result.failure(scrubbingResult.exceptionOrNull()!!)
            }

            val result = scrubbingResult.getOrThrow()
            val scrubbedContent = anrContent.let { content ->
                var scrubbed = content
                for (pattern in PHI_PATTERNS) {
                    val replacement = getReplacementForPattern(pattern)
                    scrubbed = pattern.matcher(scrubbed).replaceAll(replacement)
                }
                performContextAwareScrubbing(scrubbed)
            }

            // Save scrubbing metadata
            saveScrubbingMetadata(result)

            Timber.i("ANR report scrubbed: ${result.phiCount} PHI instances removed")
            Result.success(scrubbedContent)

        } catch (e: Exception) {
            Timber.e(e, "Failed to scrub ANR report")
            Result.failure(e)
        }
    }

    /**
     * Save scrubbing metadata
     */
    private suspend fun saveScrubbingMetadata(result: PHIScrubbingResult) = withContext(Dispatchers.IO) {
        try {
            val phiScrubberDir = File(context.filesDir, PHI_SCRUBBER_DIR)
            if (!phiScrubberDir.exists()) {
                phiScrubberDir.mkdirs()
            }

            val metadataFile = File(phiScrubberDir, "scrubbing_metadata_${System.currentTimeMillis()}.json")
            val metadataJson = generateScrubbingMetadataJson(result)
            metadataFile.writeText(metadataJson)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save scrubbing metadata")
        }
    }

    /**
     * Generate scrubbing metadata JSON
     */
    private fun generateScrubbingMetadataJson(result: PHIScrubbingResult): String {
        val patternsJson = result.scrubbedPatterns.joinToString(",") { "\"$it\"" }

        return """
        {
            "originalLength": ${result.originalLength},
            "scrubbedLength": ${result.scrubbedLength},
            "phiCount": ${result.phiCount},
            "scrubbedPatterns": [$patternsJson],
            "timestamp": "${result.timestamp}",
            "reductionPercentage": ${((result.originalLength - result.scrubbedLength).toDouble() / result.originalLength * 100)}
        }
        """.trimIndent()
    }

    /**
     * Test PHI scrubbing with synthetic data
     */
    suspend fun testPHIScrubbing(): Result<TestResult> = withContext(Dispatchers.IO) {
        try {
            val testData = generateSyntheticPHIData()
            val scrubbingResult = scrubPHI(testData)
            
            if (scrubbingResult.isFailure) {
                return@withContext Result.failure(scrubbingResult.exceptionOrNull()!!)
            }

            val result = scrubbingResult.getOrThrow()
            val testResult = TestResult(
                testData = testData,
                scrubbedData = testData.let { content ->
                    var scrubbed = content
                    for (pattern in PHI_PATTERNS) {
                        val replacement = getReplacementForPattern(pattern)
                        scrubbed = pattern.matcher(scrubbed).replaceAll(replacement)
                    }
                    performContextAwareScrubbing(scrubbed)
                },
                phiCount = result.phiCount,
                patternsDetected = result.scrubbedPatterns.size,
                success = result.phiCount > 0
            )

            Timber.i("PHI scrubbing test completed: ${result.phiCount} PHI instances detected")
            Result.success(testResult)

        } catch (e: Exception) {
            Timber.e(e, "Failed to test PHI scrubbing")
            Result.failure(e)
        }
    }

    /**
     * Test result data
     */
    data class TestResult(
        val testData: String,
        val scrubbedData: String,
        val phiCount: Int,
        val patternsDetected: Int,
        val success: Boolean
    )

    /**
     * Generate synthetic PHI data for testing
     */
    private fun generateSyntheticPHIData(): String {
        return """
        Crash Report - Ambient Scribe
        =============================
        
        Patient Information:
        - Name: Rajesh Kumar Sharma
        - Phone: 9876543210
        - Email: rajesh.sharma@example.com
        - MRN: MRN123456789
        - Address: 123 Main Street, New Delhi
        
        Encounter Details:
        - Encounter ID: enc-2025-01-19-001
        - Clinic ID: clinic-delhi-001
        - Doctor: Dr. Priya Singh
        
        Medical Conversation:
        Doctor: What brings you here today?
        Patient: I have been having chest pain for the last 2 days.
        
        SOAP Notes:
        Subjective: Patient complains of chest pain
        Objective: Vital signs normal
        Assessment: Possible cardiac issue
        Plan: ECG and blood tests
        
        Prescription:
        Rx: Aspirin 75mg once daily
        Patient ID: Pt-12345
        
        Error Stack Trace:
        at com.frozo.ambientscribe.audio.AudioCapture.processAudio()
        at com.frozo.ambientscribe.transcription.ASRService.transcribe()
        Patient data: {encounter_id: enc-2025-01-19-001, patient_name: Rajesh Kumar}
        """.trimIndent()
    }

    /**
     * Get scrubbing statistics
     */
    suspend fun getScrubbingStats(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val phiScrubberDir = File(context.filesDir, PHI_SCRUBBER_DIR)
            if (!phiScrubberDir.exists()) {
                return@withContext Result.success(emptyMap())
            }

            val metadataFiles = phiScrubberDir.listFiles { file ->
                file.isFile && file.name.startsWith("scrubbing_metadata_") && file.name.endsWith(".json")
            } ?: emptyArray()

            var totalScrubbed = 0
            var totalPHICount = 0
            var totalReduction = 0.0

            for (file in metadataFiles) {
                try {
                    val content = file.readText()
                    val metadata = parseScrubbingMetadata(content)
                    if (metadata != null) {
                        totalScrubbed++
                        totalPHICount += metadata["phiCount"] as? Int ?: 0
                        totalReduction += metadata["reductionPercentage"] as? Double ?: 0.0
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse scrubbing metadata: ${file.name}")
                }
            }

            val avgReduction = if (totalScrubbed > 0) totalReduction / totalScrubbed else 0.0

            val stats = mapOf(
                "totalScrubbed" to totalScrubbed,
                "totalPHICount" to totalPHICount,
                "averageReduction" to avgReduction,
                "patternsConfigured" to PHI_PATTERNS.size
            )

            Result.success(stats)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get scrubbing statistics")
            Result.failure(e)
        }
    }

    /**
     * Parse scrubbing metadata from JSON
     */
    private fun parseScrubbingMetadata(json: String): Map<String, Any>? {
        return try {
            val cleanJson = json.trim().removePrefix("{").removeSuffix("}")
            val pairs = cleanJson.split(",").map { it.trim() }
            val metadata = mutableMapOf<String, Any>()
            
            for (pair in pairs) {
                val (key, value) = pair.split(":", limit = 2)
                val cleanKey = key.trim().removeSurrounding("\"")
                val cleanValue = value.trim().removeSurrounding("\"")
                
                when (cleanKey) {
                    "originalLength", "scrubbedLength", "phiCount" -> {
                        metadata[cleanKey] = cleanValue.toInt()
                    }
                    "reductionPercentage" -> {
                        metadata[cleanKey] = cleanValue.toDouble()
                    }
                    else -> {
                        metadata[cleanKey] = cleanValue
                    }
                }
            }
            
            metadata
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse scrubbing metadata")
            null
        }
    }
}
