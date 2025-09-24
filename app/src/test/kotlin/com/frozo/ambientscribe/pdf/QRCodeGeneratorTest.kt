package com.frozo.ambientscribe.pdf

import android.graphics.Bitmap
import com.frozo.ambientscribe.ai.LLMService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for QRCodeGenerator - ST-4.3 implementation
 * Tests QR code generation and verification
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class QRCodeGeneratorTest {
    
    private lateinit var qrGenerator: QRCodeGenerator
    private lateinit var testEncounterNote: LLMService.EncounterNote
    private lateinit var testHmacKey: ByteArray
    
    @Before
    fun setup() {
        qrGenerator = QRCodeGenerator()
        testHmacKey = ByteArray(32) { it.toByte() }
        testEncounterNote = createTestEncounterNote()
    }
    
    @Test
    fun `test QR code generation with valid data`() = runTest {
        // Generate QR code
        val result = qrGenerator.generateQRCode(testEncounterNote, testHmacKey)
        
        // Verify success
        assertTrue(result.isSuccess)
        
        val qrData = result.getOrThrow()
        
        // Verify QR codes generated
        assertNotNull(qrData.qrCodes)
        assertTrue(qrData.qrCodes.isNotEmpty())
        
        // Verify each QR code
        qrData.qrCodes.forEach { qrCode ->
            assertNotNull(qrCode.bitmap)
            assertTrue(qrCode.bitmap.width > 0)
            assertTrue(qrCode.bitmap.height > 0)
            assertEquals(Bitmap.Config.ARGB_8888, qrCode.bitmap.config)
        }
        
        // Verify verification data
        assertNotNull(qrData.verificationData)
        assertEquals(testEncounterNote.metadata.encounterId, qrData.verificationData.encounterId)
        assertTrue(qrData.verificationData.jsonHash.isNotEmpty())
        assertTrue(qrData.verificationData.hmac.isNotEmpty())
    }
    
    @Test
    fun `test QR code verification with valid data`() = runTest {
        // Generate QR code
        val qrData = qrGenerator.generateQRCode(testEncounterNote, testHmacKey).getOrThrow()
        
        // Verify QR code
        val verificationResult = qrGenerator.verifyQRCode(qrData, testHmacKey)
        
        // Check verification success
        assertTrue(verificationResult.isSuccess)
        assertTrue(verificationResult.getOrThrow())
    }
    
    @Test
    fun `test QR code verification with tampered data`() = runTest {
        // Generate QR code
        val qrData = qrGenerator.generateQRCode(testEncounterNote, testHmacKey).getOrThrow()
        
        // Create tampered data by modifying JSON
        val tamperedData = qrData.copy(
            originalJson = qrData.originalJson.replace("Test complaint", "Tampered complaint")
        )
        
        // Verify tampered QR code
        val verificationResult = qrGenerator.verifyQRCode(tamperedData, testHmacKey)
        
        // Check verification failure
        assertTrue(verificationResult.isSuccess)
        assertFalse(verificationResult.getOrThrow())
    }
    
    @Test
    fun `test QR code verification with wrong HMAC key`() = runTest {
        // Generate QR code
        val qrData = qrGenerator.generateQRCode(testEncounterNote, testHmacKey).getOrThrow()
        
        // Create wrong key
        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        
        // Verify with wrong key
        val verificationResult = qrGenerator.verifyQRCode(qrData, wrongKey)
        
        // Check verification failure
        assertTrue(verificationResult.isSuccess)
        assertFalse(verificationResult.getOrThrow())
    }
    
    @Test
    fun `test QR code splitting for large data`() = runTest {
        // Create large encounter note
        val largeNote = createLargeEncounterNote()
        
        // Generate QR code
        val result = qrGenerator.generateQRCode(largeNote, testHmacKey)
        
        // Verify success
        assertTrue(result.isSuccess)
        
        val qrData = result.getOrThrow()
        
        // Verify multiple QR codes generated
        assertTrue(qrData.qrCodes.size > 1)
        
        // Verify QR code sequence
        qrData.qrCodes.forEachIndexed { index, qrCode ->
            assertEquals(index, qrCode.index)
            assertEquals(qrData.qrCodes.size, qrCode.total)
        }
        
        // Verify data can still be verified
        val verificationResult = qrGenerator.verifyQRCode(qrData, testHmacKey)
        assertTrue(verificationResult.isSuccess)
        assertTrue(verificationResult.getOrThrow())
    }
    
    private fun createTestEncounterNote(): LLMService.EncounterNote {
        return LLMService.EncounterNote(
            soap = LLMService.SOAPNote(
                subjective = listOf("Test complaint"),
                objective = listOf("Test finding"),
                assessment = listOf("Test diagnosis"),
                plan = listOf("Test plan"),
                confidence = 0.85f
            ),
            prescription = LLMService.Prescription(
                medications = listOf(
                    LLMService.Medication(
                        name = "Test medication",
                        dosage = "500mg",
                        frequency = "twice daily",
                        duration = "3 days",
                        instructions = "Take with food",
                        isGeneric = true
                    )
                ),
                instructions = listOf("Complete full course"),
                followUp = "Return if symptoms persist",
                confidence = 0.88f
            ),
            metadata = LLMService.EncounterMetadata(
                speakerTurns = 4,
                totalDuration = 120000,
                processingTime = 3000,
                modelVersion = "test-model",
                fallbackUsed = false,
                encounterId = "test-encounter-123",
                patientId = "test-patient-456"
            )
        )
    }
    
    private fun createLargeEncounterNote(): LLMService.EncounterNote {
        // Create note with lots of data to force QR code splitting
        return testEncounterNote.copy(
            soap = testEncounterNote.soap.copy(
                subjective = List(50) { "Large subjective data entry $it" },
                objective = List(50) { "Large objective data entry $it" },
                assessment = List(50) { "Large assessment data entry $it" },
                plan = List(50) { "Large plan data entry $it" }
            ),
            prescription = testEncounterNote.prescription.copy(
                medications = List(20) {
                    LLMService.Medication(
                        name = "Test medication $it",
                        dosage = "500mg",
                        frequency = "twice daily",
                        duration = "3 days",
                        instructions = "Take with food - detailed instructions for medication $it",
                        isGeneric = true
                    )
                },
                instructions = List(20) { "Detailed instruction $it for the complete treatment plan" }
            )
        )
    }
}
