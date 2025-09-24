package com.frozo.ambientscribe.ai

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for PrescriptionValidator - ST-2.7 requirements
 * Validates prescription field accuracy ≥95%
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PrescriptionValidatorTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var prescriptionValidator: PrescriptionValidator
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockContext.getString(org.mockito.kotlin.any())).thenReturn("Error message")
        prescriptionValidator = PrescriptionValidator(mockContext)
    }
    
    @Test
    fun testValidMedication() = runTest {
        val validMedication = LLMService.Medication(
            name = "paracetamol",
            dosage = "500mg",
            frequency = "twice daily",
            duration = "3 days",
            instructions = "Take with food",
            isGeneric = true
        )
        
        val result = prescriptionValidator.validateMedication(validMedication)
        
        assertNull(result.nameError)
        assertNull(result.dosageError)
        assertNull(result.frequencyError)
        assertNull(result.durationError)
        assertNull(result.instructionsError)
    }
    
    @Test
    fun testMedicationWithEmptyName() = runTest {
        val invalidMedication = LLMService.Medication(
            name = "",
            dosage = "500mg",
            frequency = "twice daily",
            duration = "3 days",
            instructions = "Take with food",
            isGeneric = true
        )
        
        val result = prescriptionValidator.validateMedication(invalidMedication)
        
        assertNotNull(result.nameError)
        assertNull(result.dosageError)
        assertNull(result.frequencyError)
        assertNull(result.durationError)
        assertNull(result.instructionsError)
    }
    
    @Test
    fun testMedicationWithEmptyDosage() = runTest {
        val invalidMedication = LLMService.Medication(
            name = "paracetamol",
            dosage = "",
            frequency = "twice daily",
            duration = "3 days",
            instructions = "Take with food",
            isGeneric = true
        )
        
        val result = prescriptionValidator.validateMedication(invalidMedication)
        
        assertNull(result.nameError)
        assertNotNull(result.dosageError)
        assertNull(result.frequencyError)
        assertNull(result.durationError)
        assertNull(result.instructionsError)
    }
    
    @Test
    fun testMedicationWithEmptyFrequency() = runTest {
        val invalidMedication = LLMService.Medication(
            name = "paracetamol",
            dosage = "500mg",
            frequency = "",
            duration = "3 days",
            instructions = "Take with food",
            isGeneric = true
        )
        
        val result = prescriptionValidator.validateMedication(invalidMedication)
        
        assertNull(result.nameError)
        assertNull(result.dosageError)
        assertNotNull(result.frequencyError)
        assertNull(result.durationError)
        assertNull(result.instructionsError)
    }
    
    @Test
    fun testMedicationWithEmptyDuration() = runTest {
        val invalidMedication = LLMService.Medication(
            name = "paracetamol",
            dosage = "500mg",
            frequency = "twice daily",
            duration = "",
            instructions = "Take with food",
            isGeneric = true
        )
        
        val result = prescriptionValidator.validateMedication(invalidMedication)
        
        assertNull(result.nameError)
        assertNull(result.dosageError)
        assertNull(result.frequencyError)
        assertNotNull(result.durationError)
        assertNull(result.instructionsError)
    }
    
    @Test
    fun testMedicationWithEmptyInstructions() = runTest {
        val invalidMedication = LLMService.Medication(
            name = "paracetamol",
            dosage = "500mg",
            frequency = "twice daily",
            duration = "3 days",
            instructions = "",
            isGeneric = true
        )
        
        val result = prescriptionValidator.validateMedication(invalidMedication)
        
        assertNull(result.nameError)
        assertNull(result.dosageError)
        assertNull(result.frequencyError)
        assertNull(result.durationError)
        assertNotNull(result.instructionsError)
    }
    
    @Test
    fun testMedicationWithAllFieldsEmpty() = runTest {
        val invalidMedication = LLMService.Medication(
            name = "",
            dosage = "",
            frequency = "",
            duration = "",
            instructions = "",
            isGeneric = true
        )
        
        val result = prescriptionValidator.validateMedication(invalidMedication)
        
        assertNotNull(result.nameError)
        assertNotNull(result.dosageError)
        assertNotNull(result.frequencyError)
        assertNotNull(result.durationError)
        assertNotNull(result.instructionsError)
    }
}