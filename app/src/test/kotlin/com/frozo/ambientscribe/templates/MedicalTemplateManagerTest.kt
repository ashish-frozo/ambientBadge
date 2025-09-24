package com.frozo.ambientscribe.templates

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for MedicalTemplateManager - PT-7.9, PT-7.10
 */
@RunWith(RobolectricTestRunner::class)
class MedicalTemplateManagerTest {

    private lateinit var context: Context
    private lateinit var medicalTemplateManager: MedicalTemplateManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        medicalTemplateManager = MedicalTemplateManager(context)
    }

    @Test
    fun `test initialization`() = runTest {
        // When
        val result = medicalTemplateManager.initialize()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test get template by ID - English consultation`() = runTest {
        // Given
        val templateId = "consultation_en"
        
        // When
        val template = medicalTemplateManager.getTemplate(templateId)
        
        // Then
        assertNotNull(template)
        assertEquals(templateId, template!!.id)
        assertEquals("Consultation Note", template.name)
        assertEquals("en", template.language)
        assertEquals("Consultation", template.category)
        assertTrue(template.isApproved)
    }

    @Test
    fun `test get template by ID - Hindi consultation`() = runTest {
        // Given
        val templateId = "consultation_hi"
        
        // When
        val template = medicalTemplateManager.getTemplate(templateId)
        
        // Then
        assertNotNull(template)
        assertEquals(templateId, template!!.id)
        assertEquals("परामर्श नोट", template.name)
        assertEquals("hi", template.language)
        assertEquals("Consultation", template.category)
        assertTrue(template.isApproved)
    }

    @Test
    fun `test get template by ID - Telugu consultation`() = runTest {
        // Given
        val templateId = "consultation_te"
        
        // When
        val template = medicalTemplateManager.getTemplate(templateId)
        
        // Then
        assertNotNull(template)
        assertEquals(templateId, template!!.id)
        assertEquals("సలహా నోట్", template.name)
        assertEquals("te", template.language)
        assertEquals("Consultation", template.category)
        assertTrue(template.isApproved)
    }

    @Test
    fun `test get template by ID - not found`() = runTest {
        // Given
        val templateId = "nonexistent_template"
        
        // When
        val template = medicalTemplateManager.getTemplate(templateId)
        
        // Then
        assertTrue(template == null)
    }

    @Test
    fun `test get templates by language - English`() = runTest {
        // Given
        val language = "en"
        
        // When
        val templates = medicalTemplateManager.getTemplatesByLanguage(language)
        
        // Then
        assertNotNull(templates)
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.all { it.language == language })
    }

    @Test
    fun `test get templates by language - Hindi`() = runTest {
        // Given
        val language = "hi"
        
        // When
        val templates = medicalTemplateManager.getTemplatesByLanguage(language)
        
        // Then
        assertNotNull(templates)
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.all { it.language == language })
    }

    @Test
    fun `test get templates by language - Telugu`() = runTest {
        // Given
        val language = "te"
        
        // When
        val templates = medicalTemplateManager.getTemplatesByLanguage(language)
        
        // Then
        assertNotNull(templates)
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.all { it.language == language })
    }

    @Test
    fun `test get templates by category - Consultation`() = runTest {
        // Given
        val category = "Consultation"
        
        // When
        val templates = medicalTemplateManager.getTemplatesByCategory(category)
        
        // Then
        assertNotNull(templates)
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.all { it.category == category })
    }

    @Test
    fun `test get templates by category - Prescription`() = runTest {
        // Given
        val category = "Prescription"
        
        // When
        val templates = medicalTemplateManager.getTemplatesByCategory(category)
        
        // Then
        assertNotNull(templates)
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.all { it.category == category })
    }

    @Test
    fun `test get approved templates`() = runTest {
        // When
        val templates = medicalTemplateManager.getApprovedTemplates()
        
        // Then
        assertNotNull(templates)
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.all { it.isApproved })
    }

    @Test
    fun `test generate document - English consultation`() = runTest {
        // Given
        val templateId = "consultation_en"
        val placeholders = mapOf(
            "patient_name" to "John Doe",
            "consultation_date" to "2024-01-15",
            "consultation_time" to "10:30 AM",
            "doctor_name" to "Dr. Smith",
            "chief_complaint" to "Chest pain",
            "history_present_illness" to "Patient reports chest pain for 2 days",
            "physical_examination" to "Vital signs stable, chest clear",
            "assessment_plan" to "Possible angina, recommend ECG",
            "follow_up" to "Follow up in 1 week"
        )
        
        // When
        val result = medicalTemplateManager.generateDocument(templateId, placeholders)
        
        // Then
        assertTrue(result.isSuccess)
        val document = result.getOrThrow()
        assertEquals(templateId, document.templateId)
        assertEquals("en", document.language)
        assertTrue(document.content.contains("John Doe"))
        assertTrue(document.content.contains("Chest pain"))
        assertTrue(document.content.contains("Dr. Smith"))
    }

    @Test
    fun `test generate document - Hindi consultation`() = runTest {
        // Given
        val templateId = "consultation_hi"
        val placeholders = mapOf(
            "patient_name" to "राम शर्मा",
            "consultation_date" to "2024-01-15",
            "consultation_time" to "10:30 AM",
            "doctor_name" to "डॉ. सिंह",
            "chief_complaint" to "छाती में दर्द",
            "history_present_illness" to "रोगी को 2 दिन से छाती में दर्द",
            "physical_examination" to "जीवन संकेत स्थिर, छाती साफ",
            "assessment_plan" to "संभावित एनजाइना, ECG की सिफारिश",
            "follow_up" to "1 सप्ताह में अनुवर्ती"
        )
        
        // When
        val result = medicalTemplateManager.generateDocument(templateId, placeholders)
        
        // Then
        assertTrue(result.isSuccess)
        val document = result.getOrThrow()
        assertEquals(templateId, document.templateId)
        assertEquals("hi", document.language)
        assertTrue(document.content.contains("राम शर्मा"))
        assertTrue(document.content.contains("छाती में दर्द"))
        assertTrue(document.content.contains("डॉ. सिंह"))
    }

    @Test
    fun `test generate document - missing placeholders`() = runTest {
        // Given
        val templateId = "consultation_en"
        val placeholders = mapOf(
            "patient_name" to "John Doe"
            // Missing required placeholders
        )
        
        // When
        val result = medicalTemplateManager.generateDocument(templateId, placeholders)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `test generate document - invalid template ID`() = runTest {
        // Given
        val templateId = "nonexistent_template"
        val placeholders = mapOf("patient_name" to "John Doe")
        
        // When
        val result = medicalTemplateManager.generateDocument(templateId, placeholders)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `test get template placeholders`() = runTest {
        // Given
        val templateId = "consultation_en"
        
        // When
        val placeholders = medicalTemplateManager.getTemplatePlaceholders(templateId)
        
        // Then
        assertNotNull(placeholders)
        assertTrue(placeholders.isNotEmpty())
        assertTrue(placeholders.any { it.key == "patient_name" })
        assertTrue(placeholders.any { it.key == "doctor_name" })
        assertTrue(placeholders.any { it.key == "chief_complaint" })
    }

    @Test
    fun `test get template statistics`() = runTest {
        // When
        val stats = medicalTemplateManager.getTemplateStatistics()
        
        // Then
        assertNotNull(stats)
        assertTrue(stats["totalTemplates"] as Int > 0)
        assertTrue(stats["approvedTemplates"] as Int > 0)
        assertTrue((stats["languages"] as List<*>).isNotEmpty())
        assertTrue((stats["categories"] as List<*>).isNotEmpty())
    }

    @Test
    fun `test medical template properties`() {
        // Given
        val template = MedicalTemplateManager.MedicalTemplate(
            id = "test_template",
            name = "Test Template",
            language = "en",
            category = "Test",
            content = "Test content {placeholder}",
            placeholders = listOf("placeholder"),
            legalDisclaimer = "Test disclaimer",
            version = "1.0.0",
            isApproved = true,
            clinicId = "clinic_001",
            lastModified = System.currentTimeMillis()
        )
        
        // Then
        assertEquals("test_template", template.id)
        assertEquals("Test Template", template.name)
        assertEquals("en", template.language)
        assertEquals("Test", template.category)
        assertEquals("Test content {placeholder}", template.content)
        assertEquals(1, template.placeholders.size)
        assertEquals("Test disclaimer", template.legalDisclaimer)
        assertEquals("1.0.0", template.version)
        assertTrue(template.isApproved)
        assertEquals("clinic_001", template.clinicId)
        assertTrue(template.lastModified > 0)
    }

    @Test
    fun `test template placeholder properties`() {
        // Given
        val placeholder = MedicalTemplateManager.TemplatePlaceholder(
            key = "patient_name",
            description = "Patient's full name",
            isRequired = true,
            dataType = "text",
            example = "John Doe"
        )
        
        // Then
        assertEquals("patient_name", placeholder.key)
        assertEquals("Patient's full name", placeholder.description)
        assertTrue(placeholder.isRequired)
        assertEquals("text", placeholder.dataType)
        assertEquals("John Doe", placeholder.example)
    }

    @Test
    fun `test generated document properties`() {
        // Given
        val document = MedicalTemplateManager.GeneratedDocument(
            templateId = "consultation_en",
            content = "Generated content",
            placeholders = mapOf("key" to "value"),
            language = "en",
            timestamp = System.currentTimeMillis(),
            clinicId = "clinic_001",
            patientId = "patient_001",
            encounterId = "encounter_001"
        )
        
        // Then
        assertEquals("consultation_en", document.templateId)
        assertEquals("Generated content", document.content)
        assertEquals(1, document.placeholders.size)
        assertEquals("en", document.language)
        assertTrue(document.timestamp > 0)
        assertEquals("clinic_001", document.clinicId)
        assertEquals("patient_001", document.patientId)
        assertEquals("encounter_001", document.encounterId)
    }

    @Test
    fun `test template validation result properties`() {
        // Given
        val validation = MedicalTemplateManager.TemplateValidationResult(
            templateId = "test_template",
            isValid = true,
            issues = listOf("Issue 1"),
            recommendations = listOf("Recommendation 1"),
            placeholderValidation = mapOf("placeholder" to true)
        )
        
        // Then
        assertEquals("test_template", validation.templateId)
        assertTrue(validation.isValid)
        assertEquals(1, validation.issues.size)
        assertEquals(1, validation.recommendations.size)
        assertEquals(1, validation.placeholderValidation.size)
        assertTrue(validation.placeholderValidation["placeholder"] == true)
    }

    @Test
    fun `test get placeholder description`() = runTest {
        // Given
        val placeholder = "patient_name"
        
        // When
        val description = medicalTemplateManager.getPlaceholderDescription(placeholder)
        
        // Then
        assertNotNull(description)
        assertTrue(description.isNotEmpty())
    }

    @Test
    fun `test get placeholder data type`() = runTest {
        // Given
        val placeholder = "consultation_date"
        
        // When
        val dataType = medicalTemplateManager.getPlaceholderDataType(placeholder)
        
        // Then
        assertNotNull(dataType)
        assertEquals("date", dataType)
    }

    @Test
    fun `test get placeholder example`() = runTest {
        // Given
        val placeholder = "patient_name"
        
        // When
        val example = medicalTemplateManager.getPlaceholderExample(placeholder)
        
        // Then
        assertNotNull(example)
        assertTrue(example.isNotEmpty())
    }

    @Test
    fun `test template categories`() = runTest {
        // When
        val categories = medicalTemplateManager.templateCategories
        
        // Then
        assertNotNull(categories)
        assertTrue(categories.isNotEmpty())
        assertTrue(categories.contains("Consultation"))
        assertTrue(categories.contains("Prescription"))
        assertTrue(categories.contains("Diagnosis"))
        assertTrue(categories.contains("Treatment Plan"))
        assertTrue(categories.contains("Follow-up"))
        assertTrue(categories.contains("Emergency"))
        assertTrue(categories.contains("Discharge Summary"))
    }
}
