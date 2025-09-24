package com.frozo.ambientscribe.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PHIScrubberTest {

    private lateinit var context: Context
    private lateinit var phiScrubber: PHIScrubber

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        phiScrubber = PHIScrubber(context)
    }

    @Test
    fun testPhoneNumberScrubbing() = runBlocking {
        val testContent = "Patient phone number is 9876543210 and alternate is +919876543210"
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
        assertTrue(scrubbingResult.scrubbedPatterns.isNotEmpty())
    }

    @Test
    fun testEmailScrubbing() = runBlocking {
        val testContent = "Contact email: patient@example.com and doctor@clinic.com"
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
    }

    @Test
    fun testMRNScrubbing() = runBlocking {
        val testContent = "Patient MRN: MRN123456789 and Patient ID: Pt-98765"
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
    }

    @Test
    fun testNameScrubbing() = runBlocking {
        val testContent = "Patient: Rajesh Kumar Sharma, Doctor: Dr. Priya Singh"
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
    }

    @Test
    fun testAddressScrubbing() = runBlocking {
        val testContent = "Address: 123 Main Street, New Delhi, 110001"
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
    }

    @Test
    fun testMedicalConversationScrubbing() = runBlocking {
        val testContent = """
            Doctor: What brings you here today?
            Patient: I have been having chest pain for the last 2 days.
            Doctor: Let me examine you.
        """.trimIndent()
        
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
    }

    @Test
    fun testSOAPNoteScrubbing() = runBlocking {
        val testContent = """
            Subjective: Patient complains of chest pain
            Objective: Vital signs normal
            Assessment: Possible cardiac issue
            Plan: ECG and blood tests
        """.trimIndent()
        
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
    }

    @Test
    fun testPrescriptionScrubbing() = runBlocking {
        val testContent = """
            Rx: Aspirin 75mg once daily
            Patient ID: Pt-12345
            Prescription: Paracetamol 500mg
        """.trimIndent()
        
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
    }

    @Test
    fun testAuditLogScrubbing() = runBlocking {
        val testContent = """
            encounter_id: enc-2025-01-19-001
            patient_id: pt-12345
            clinic_id: clinic-delhi-001
        """.trimIndent()
        
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
    }

    @Test
    fun testCrashReportScrubbing() = runBlocking {
        val crashContent = """
            Crash Report - Ambient Scribe
            =============================
            
            Patient Information:
            - Name: Rajesh Kumar Sharma
            - Phone: 9876543210
            - Email: rajesh.sharma@example.com
            - MRN: MRN123456789
            
            Error Stack Trace:
            at com.frozo.ambientscribe.audio.AudioCapture.processAudio()
            Patient data: {encounter_id: enc-2025-01-19-001, patient_name: Rajesh Kumar}
        """.trimIndent()
        
        val result = phiScrubber.scrubCrashReport(crashContent)
        
        assertTrue(result.isSuccess)
        val scrubbedContent = result.getOrThrow()
        assertFalse(scrubbedContent.contains("9876543210"))
        assertFalse(scrubbedContent.contains("rajesh.sharma@example.com"))
        assertFalse(scrubbedContent.contains("MRN123456789"))
        assertFalse(scrubbedContent.contains("Rajesh Kumar Sharma"))
    }

    @Test
    fun testANRReportScrubbing() = runBlocking {
        val anrContent = """
            ANR Report - Ambient Scribe
            ===========================
            
            Patient: Priya Singh
            Phone: +919876543210
            Encounter: enc-2025-01-19-002
            
            Thread dump:
            at com.frozo.ambientscribe.transcription.ASRService.transcribe()
            Patient data: {name: Priya Singh, phone: +919876543210}
        """.trimIndent()
        
        val result = phiScrubber.scrubANRReport(anrContent)
        
        assertTrue(result.isSuccess)
        val scrubbedContent = result.getOrThrow()
        assertFalse(scrubbedContent.contains("+919876543210"))
        assertFalse(scrubbedContent.contains("Priya Singh"))
        assertFalse(scrubbedContent.contains("enc-2025-01-19-002"))
    }

    @Test
    fun testSyntheticPHITesting() = runBlocking {
        val result = phiScrubber.testPHIScrubbing()
        
        assertTrue(result.isSuccess)
        val testResult = result.getOrThrow()
        assertTrue(testResult.success)
        assertTrue(testResult.phiCount > 0)
        assertTrue(testResult.patternsDetected > 0)
        assertNotNull(testResult.testData)
        assertNotNull(testResult.scrubbedData)
    }

    @Test
    fun testNoPHIContent() = runBlocking {
        val testContent = "This is a normal text without any PHI information."
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertEquals(0, scrubbingResult.phiCount)
        assertTrue(scrubbingResult.scrubbedPatterns.isEmpty())
    }

    @Test
    fun testMixedContentScrubbing() = runBlocking {
        val testContent = """
            System Log:
            - User logged in: user123
            - Database connection established
            - Patient data loaded: Rajesh Kumar (9876543210)
            - Error occurred in module: audio_processing
            - Contact: rajesh@example.com
        """.trimIndent()
        
        val result = phiScrubber.scrubPHI(testContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
        assertTrue(scrubbingResult.scrubbedPatterns.isNotEmpty())
    }

    @Test
    fun testScrubbingStatistics() = runBlocking {
        // First perform some scrubbing operations
        phiScrubber.scrubPHI("Test content with phone 9876543210")
        phiScrubber.scrubPHI("Another test with email test@example.com")
        
        val result = phiScrubber.getScrubbingStats()
        
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()
        assertTrue(stats.containsKey("totalScrubbed"))
        assertTrue(stats.containsKey("totalPHICount"))
        assertTrue(stats.containsKey("patternsConfigured"))
    }

    @Test
    fun testComplexMedicalContent() = runBlocking {
        val complexContent = """
            Medical Report - 2025-01-19
            ===========================
            
            Patient Details:
            - Name: Dr. Rajesh Kumar Sharma
            - Phone: +91-9876543210
            - Email: rajesh.sharma@hospital.com
            - MRN: MRN-2025-001234
            - Address: 123 Medical Street, New Delhi, 110001
            
            Encounter Information:
            - Encounter ID: enc-2025-01-19-001
            - Clinic ID: clinic-delhi-medical-001
            - Doctor: Dr. Priya Singh (priya.singh@hospital.com)
            
            Conversation:
            Doctor: What symptoms are you experiencing?
            Patient: I have chest pain and shortness of breath.
            Doctor: How long have you had these symptoms?
            Patient: About 3 days now.
            
            SOAP Notes:
            Subjective: 45-year-old male presents with chest pain and SOB
            Objective: BP 140/90, HR 95, O2 Sat 96%
            Assessment: Possible MI, rule out other cardiac causes
            Plan: ECG, cardiac enzymes, chest X-ray
            
            Prescription:
            Rx: Aspirin 75mg OD, Atorvastatin 20mg OD
            Patient ID: Pt-2025-001234
            
            Follow-up: Contact at 9876543210 or rajesh.sharma@hospital.com
        """.trimIndent()
        
        val result = phiScrubber.scrubPHI(complexContent)
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertTrue(scrubbingResult.phiCount > 0)
        assertTrue(scrubbingResult.scrubbedPatterns.size > 1)
        
        // Verify specific patterns were detected
        assertTrue(scrubbingResult.scrubbedPatterns.any { it.contains("\\d{9}") })
        assertTrue(scrubbingResult.scrubbedPatterns.any { it.contains("@") })
        assertTrue(scrubbingResult.scrubbedPatterns.any { it.contains("MRN", ignoreCase = true) })
    }

    @Test
    fun testEmptyContent() = runBlocking {
        val result = phiScrubber.scrubPHI("")
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertEquals(0, scrubbingResult.phiCount)
        assertEquals(0, scrubbingResult.originalLength)
        assertEquals(0, scrubbingResult.scrubbedLength)
    }

    @Test
    fun testNullContent() = runBlocking {
        val result = phiScrubber.scrubPHI("")
        
        assertTrue(result.isSuccess)
        val scrubbingResult = result.getOrThrow()
        assertEquals(0, scrubbingResult.phiCount)
    }
}
