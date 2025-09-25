package com.frozo.ambientscribe.telemetry

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Unit tests for PrivacyValidator (PT-8 implementation)
 */
class PrivacyValidatorTest {
    
    private lateinit var privacyValidator: PrivacyValidator
    
    @BeforeEach
    fun setUp() {
        privacyValidator = PrivacyValidator()
    }
    
    @Test
    fun `test validate event with no PII`() {
        // Given
        val event = EncounterStartEvent(
            encounterId = "test-encounter-123",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456",
            audioQuality = "excellent",
            batteryLevel = 85
        )
        
        // When
        val isValid = privacyValidator.validateEvent(event)
        
        // Then
        assertTrue(isValid)
    }
    
    @Test
    fun `test validate event with phone number PII`() {
        // Given
        val event = EncounterStartEvent(
            encounterId = "test-encounter-123",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456",
            audioQuality = "excellent",
            batteryLevel = 85
        )
        
        // Mock the toJsonString to return content with phone number
        val eventWithPhone = object : TelemetryEvent() {
            override val encounterId: String = "test-encounter-123"
            override val timestamp: String = Instant.now().toString()
            override val eventType: String = "encounter_start"
            override val deviceTier: String = "A"
            override val clinicId: String? = "clinic-456"
            
            override fun toJsonString(): String {
                return """{"encounter_id":"test-encounter-123","phone":"555-123-4567","timestamp":"${timestamp}"}"""
            }
        }
        
        // When
        val isValid = privacyValidator.validateEvent(eventWithPhone)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate event with email PII`() {
        // Given
        val eventWithEmail = object : TelemetryEvent() {
            override val encounterId: String = "test-encounter-123"
            override val timestamp: String = Instant.now().toString()
            override val eventType: String = "encounter_start"
            override val deviceTier: String = "A"
            override val clinicId: String? = "clinic-456"
            
            override fun toJsonString(): String {
                return """{"encounter_id":"test-encounter-123","email":"doctor@clinic.com","timestamp":"${timestamp}"}"""
            }
        }
        
        // When
        val isValid = privacyValidator.validateEvent(eventWithEmail)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate event with SSN PII`() {
        // Given
        val eventWithSSN = object : TelemetryEvent() {
            override val encounterId: String = "test-encounter-123"
            override val timestamp: String = Instant.now().toString()
            override val eventType: String = "encounter_start"
            override val deviceTier: String = "A"
            override val clinicId: String? = "clinic-456"
            
            override fun toJsonString(): String {
                return """{"encounter_id":"test-encounter-123","ssn":"123-45-6789","timestamp":"${timestamp}"}"""
            }
        }
        
        // When
        val isValid = privacyValidator.validateEvent(eventWithSSN)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate event with MRN PII`() {
        // Given
        val eventWithMRN = object : TelemetryEvent() {
            override val encounterId: String = "test-encounter-123"
            override val timestamp: String = Instant.now().toString()
            override val eventType: String = "encounter_start"
            override val deviceTier: String = "A"
            override val clinicId: String? = "clinic-456"
            
            override fun toJsonString(): String {
                return """{"encounter_id":"test-encounter-123","mrn":"MRN: 123456789","timestamp":"${timestamp}"}"""
            }
        }
        
        // When
        val isValid = privacyValidator.validateEvent(eventWithMRN)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate event with name PII`() {
        // Given
        val eventWithName = object : TelemetryEvent() {
            override val encounterId: String = "test-encounter-123"
            override val timestamp: String = Instant.now().toString()
            override val eventType: String = "encounter_start"
            override val deviceTier: String = "A"
            override val clinicId: String? = "clinic-456"
            
            override fun toJsonString(): String {
                return """{"encounter_id":"test-encounter-123","doctor_name":"Dr. John Smith","timestamp":"${timestamp}"}"""
            }
        }
        
        // When
        val isValid = privacyValidator.validateEvent(eventWithName)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate event with address PII`() {
        // Given
        val eventWithAddress = object : TelemetryEvent() {
            override val encounterId: String = "test-encounter-123"
            override val timestamp: String = Instant.now().toString()
            override val eventType: String = "encounter_start"
            override val deviceTier: String = "A"
            override val clinicId: String? = "clinic-456"
            
            override fun toJsonString(): String {
                return """{"encounter_id":"test-encounter-123","address":"123 Main Street","timestamp":"${timestamp}"}"""
            }
        }
        
        // When
        val isValid = privacyValidator.validateEvent(eventWithAddress)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with no PII`() {
        // Given
        val content = "This is a normal string with no PII"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertTrue(isValid)
    }
    
    @Test
    fun `test validate string with phone number`() {
        // Given
        val content = "Contact us at 555-123-4567 for more information"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with email`() {
        // Given
        val content = "Send email to doctor@clinic.com"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with SSN`() {
        // Given
        val content = "SSN: 123-45-6789"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with MRN`() {
        // Given
        val content = "Patient MRN: 123456789"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with name`() {
        // Given
        val content = "Dr. John Smith will see you"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with address`() {
        // Given
        val content = "Visit us at 123 Main Street"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with patient ID`() {
        // Given
        val content = "Patient ID: 12345"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with DOB`() {
        // Given
        val content = "DOB: 01/15/1980"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test validate string with age`() {
        // Given
        val content = "Age: 43 years"
        
        // When
        val isValid = privacyValidator.validateString(content)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test sanitize string with phone number`() {
        // Given
        val content = "Contact us at 555-123-4567 for more information"
        
        // When
        val sanitized = privacyValidator.sanitizeString(content)
        
        // Then
        assertTrue(sanitized.contains("[PHONE_REDACTED]"))
        assertFalse(sanitized.contains("555-123-4567"))
    }
    
    @Test
    fun `test sanitize string with email`() {
        // Given
        val content = "Send email to doctor@clinic.com"
        
        // When
        val sanitized = privacyValidator.sanitizeString(content)
        
        // Then
        assertTrue(sanitized.contains("[EMAIL_REDACTED]"))
        assertFalse(sanitized.contains("doctor@clinic.com"))
    }
    
    @Test
    fun `test sanitize string with SSN`() {
        // Given
        val content = "SSN: 123-45-6789"
        
        // When
        val sanitized = privacyValidator.sanitizeString(content)
        
        // Then
        assertTrue(sanitized.contains("[SSN_REDACTED]"))
        assertFalse(sanitized.contains("123-45-6789"))
    }
    
    @Test
    fun `test sanitize string with MRN`() {
        // Given
        val content = "Patient MRN: 123456789"
        
        // When
        val sanitized = privacyValidator.sanitizeString(content)
        
        // Then
        assertTrue(sanitized.contains("[MRN_REDACTED]"))
        assertFalse(sanitized.contains("123456789"))
    }
    
    @Test
    fun `test sanitize string with name`() {
        // Given
        val content = "Dr. John Smith will see you"
        
        // When
        val sanitized = privacyValidator.sanitizeString(content)
        
        // Then
        assertTrue(sanitized.contains("[NAME_REDACTED]"))
        assertFalse(sanitized.contains("Dr. John Smith"))
    }
    
    @Test
    fun `test sanitize string with address`() {
        // Given
        val content = "Visit us at 123 Main Street"
        
        // When
        val sanitized = privacyValidator.sanitizeString(content)
        
        // Then
        assertTrue(sanitized.contains("[ADDRESS_REDACTED]"))
        assertFalse(sanitized.contains("123 Main Street"))
    }
    
    @Test
    fun `test validate event fields with valid data`() {
        // Given
        val event = EncounterStartEvent(
            encounterId = "550e8400-e29b-41d4-a716-446655440000",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456"
        )
        
        // When
        val result = privacyValidator.validateEventFields(event)
        
        // Then
        assertTrue(result.isValid)
        assertTrue(result.violations.isEmpty())
    }
    
    @Test
    fun `test validate event fields with invalid encounter ID`() {
        // Given
        val event = EncounterStartEvent(
            encounterId = "invalid-uuid",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456"
        )
        
        // When
        val result = privacyValidator.validateEventFields(event)
        
        // Then
        assertFalse(result.isValid)
        assertTrue(result.violations.contains("Invalid encounter ID format"))
    }
    
    @Test
    fun `test validate event fields with invalid device tier`() {
        // Given
        val event = EncounterStartEvent(
            encounterId = "550e8400-e29b-41d4-a716-446655440000",
            timestamp = Instant.now().toString(),
            deviceTier = "C", // Invalid tier
            clinicId = "clinic-456"
        )
        
        // When
        val result = privacyValidator.validateEventFields(event)
        
        // Then
        assertFalse(result.isValid)
        assertTrue(result.violations.contains("Invalid device tier"))
    }
    
    @Test
    fun `test validate event fields with invalid timestamp`() {
        // Given
        val event = EncounterStartEvent(
            encounterId = "550e8400-e29b-41d4-a716-446655440000",
            timestamp = "invalid-timestamp",
            deviceTier = "A",
            clinicId = "clinic-456"
        )
        
        // When
        val result = privacyValidator.validateEventFields(event)
        
        // Then
        assertFalse(result.isValid)
        assertTrue(result.violations.contains("Invalid timestamp format"))
    }
    
    @Test
    fun `test validate event fields with PII in clinic ID`() {
        // Given
        val event = EncounterStartEvent(
            encounterId = "550e8400-e29b-41d4-a716-446655440000",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456-phone-555-123-4567" // Contains phone number
        )
        
        // When
        val result = privacyValidator.validateEventFields(event)
        
        // Then
        assertFalse(result.isValid)
        assertTrue(result.violations.contains("Clinic ID contains PII"))
    }
    
    @Test
    fun `test get detection stats`() {
        // When
        val stats = privacyValidator.getDetectionStats()
        
        // Then
        assertNotNull(stats)
        assertTrue(stats.totalValidations >= 0)
        assertTrue(stats.piiDetections >= 0)
    }
}
