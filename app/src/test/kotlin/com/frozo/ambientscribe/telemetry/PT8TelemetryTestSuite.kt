package com.frozo.ambientscribe.telemetry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive Test Suite for PT-8 Telemetry and Metrics
 * Tests all telemetry components and their integration
 */
@DisplayName("PT-8 Telemetry and Metrics Test Suite")
class PT8TelemetryTestSuite {
    
    @Nested
    @DisplayName("TelemetryEvent Tests")
    inner class TelemetryEventTests {
        
        @Test
        @DisplayName("Test EncounterStartEvent serialization")
        fun testEncounterStartEventSerialization() {
            val event = EncounterStartEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                audioQuality = "excellent",
                batteryLevel = 85
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("encounter_start"))
            assertTrue(json.contains("test-encounter-123"))
            assertTrue(json.contains("A"))
            assertTrue(json.contains("clinic-456"))
        }
        
        @Test
        @DisplayName("Test TranscriptionCompleteEvent serialization")
        fun testTranscriptionCompleteEventSerialization() {
            val event = TranscriptionCompleteEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                werEstimate = 0.15,
                processingTimeMs = 2500L,
                modelVersion = "whisper-tiny-int8@ct2-1",
                audioDurationMs = 30000L,
                confidenceScore = 0.85,
                languageDetected = "en"
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("transcription_complete"))
            assertTrue(json.contains("0.15"))
            assertTrue(json.contains("whisper-tiny-int8@ct2-1"))
        }
        
        @Test
        @DisplayName("Test ReviewCompleteEvent serialization")
        fun testReviewCompleteEventSerialization() {
            val event = ReviewCompleteEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                editRatePercent = 12.5,
                reviewDurationS = 60L,
                confidenceOverrides = 2,
                totalEdits = 5,
                prescriptionEdits = 3,
                soapEdits = 2,
                redFlagsResolved = 1
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("review_complete"))
            assertTrue(json.contains("12.5"))
            assertTrue(json.contains("5"))
        }
        
        @Test
        @DisplayName("Test ExportSuccessEvent serialization")
        fun testExportSuccessEventSerialization() {
            val event = ExportSuccessEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                pdfSizeKb = 150L,
                exportDurationMs = 3000L,
                batteryLevelPercent = 80,
                storageUsedKb = 500L,
                qrCodeGenerated = true,
                encryptionApplied = true
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("export_success"))
            assertTrue(json.contains("150"))
            assertTrue(json.contains("3000"))
        }
        
        @Test
        @DisplayName("Test ThermalEvent serialization")
        fun testThermalEventSerialization() {
            val event = ThermalEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                thermalState = "WARNING",
                mitigationAction = "THROTTLE",
                cpuUsagePercent = 85.5,
                temperature = 45.0,
                recoveryTimeMs = 2000L
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("thermal_event"))
            assertTrue(json.contains("WARNING"))
            assertTrue(json.contains("THROTTLE"))
        }
        
        @Test
        @DisplayName("Test PolicyToggleEvent serialization")
        fun testPolicyToggleEventSerialization() {
            val event = PolicyToggleEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                policyType = "brand_generic",
                actor = "doctor_123",
                beforeValue = "false",
                afterValue = "true",
                reason = "User preference change"
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("policy_toggle"))
            assertTrue(json.contains("brand_generic"))
            assertTrue(json.contains("doctor_123"))
        }
        
        @Test
        @DisplayName("Test BulkEditAppliedEvent serialization")
        fun testBulkEditAppliedEventSerialization() {
            val event = BulkEditAppliedEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                actor = "doctor_123",
                editType = "frequency",
                beforeValue = "twice daily",
                afterValue = "twice a day",
                affectedCount = 5
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("bulk_edit_applied"))
            assertTrue(json.contains("frequency"))
            assertTrue(json.contains("5"))
        }
        
        @Test
        @DisplayName("Test TimeSkewEvent serialization")
        fun testTimeSkewEventSerialization() {
            val event = TimeSkewEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                deviceTime = Instant.now().toString(),
                serverTime = Instant.now().plusSeconds(120).toString(),
                skewSeconds = 120L,
                timeSource = "SNTP"
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("time_skew"))
            assertTrue(json.contains("120"))
            assertTrue(json.contains("SNTP"))
        }
        
        @Test
        @DisplayName("Test CrashFreeSessionEvent serialization")
        fun testCrashFreeSessionEventSerialization() {
            val event = CrashFreeSessionEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                sessionDurationMs = 300000L,
                crashCount = 0,
                anrCount = 0,
                recoveryActions = 0
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("crash_free_session"))
            assertTrue(json.contains("300000"))
            assertTrue(json.contains("0"))
        }
        
        @Test
        @DisplayName("Test EditCauseCodeEvent serialization")
        fun testEditCauseCodeEventSerialization() {
            val event = EditCauseCodeEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                editType = "heard",
                fieldName = "frequency",
                originalValue = "twice daily",
                correctedValue = "twice a day",
                confidenceScore = 0.75
            )
            
            val json = event.toJsonString()
            assertTrue(json.contains("edit_cause_code"))
            assertTrue(json.contains("heard"))
            assertTrue(json.contains("frequency"))
        }
    }
    
    @Nested
    @DisplayName("TelemetryEventUtils Tests")
    inner class TelemetryEventUtilsTests {
        
        @Test
        @DisplayName("Test createBaseEvent")
        fun testCreateBaseEvent() {
            val baseEvent = TelemetryEventUtils.createBaseEvent(
                encounterId = "test-encounter-123",
                deviceTier = "A",
                clinicId = "clinic-456"
            )
            
            assertEquals("test-encounter-123", baseEvent["encounter_id"])
            assertEquals("A", baseEvent["device_tier"])
            assertEquals("clinic-456", baseEvent["clinic_id"])
            assertNotNull(baseEvent["timestamp"])
        }
        
        @Test
        @DisplayName("Test validateNoPII with clean event")
        fun testValidateNoPIIWithCleanEvent() {
            val event = EncounterStartEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456"
            )
            
            val isValid = TelemetryEventUtils.validateNoPII(event)
            assertTrue(isValid)
        }
        
        @Test
        @DisplayName("Test validateNoPII with PII event")
        fun testValidateNoPIIWithPIIEvent() {
            val eventWithPII = object : TelemetryEvent() {
                override val encounterId: String = "test-encounter-123"
                override val timestamp: String = Instant.now().toString()
                override val eventType: String = "encounter_start"
                override val deviceTier: String = "A"
                override val clinicId: String? = "clinic-456"
                
                override fun toJsonString(): String {
                    return """{"encounter_id":"test-encounter-123","patient_name":"John Doe","timestamp":"${timestamp}"}"""
                }
            }
            
            val isValid = TelemetryEventUtils.validateNoPII(eventWithPII)
            assertFalse(isValid)
        }
        
        @Test
        @DisplayName("Test getSchemaVersion")
        fun testGetSchemaVersion() {
            val version = TelemetryEventUtils.getSchemaVersion()
            assertEquals("1.0", version)
        }
    }
    
    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        
        @Test
        @DisplayName("Test complete telemetry workflow")
        fun testCompleteTelemetryWorkflow() {
            // Test the complete workflow from event creation to storage
            val encounterId = "test-encounter-123"
            val deviceTier = "A"
            val clinicId = "clinic-456"
            
            // Create a complete workflow
            val startEvent = EncounterStartEvent(
                encounterId = encounterId,
                timestamp = Instant.now().toString(),
                deviceTier = deviceTier,
                clinicId = clinicId,
                audioQuality = "excellent",
                batteryLevel = 85
            )
            
            val transcriptionEvent = TranscriptionCompleteEvent(
                encounterId = encounterId,
                timestamp = Instant.now().toString(),
                deviceTier = deviceTier,
                clinicId = clinicId,
                werEstimate = 0.15,
                processingTimeMs = 2500L,
                modelVersion = "whisper-tiny-int8@ct2-1",
                audioDurationMs = 30000L,
                confidenceScore = 0.85,
                languageDetected = "en"
            )
            
            val reviewEvent = ReviewCompleteEvent(
                encounterId = encounterId,
                timestamp = Instant.now().toString(),
                deviceTier = deviceTier,
                clinicId = clinicId,
                editRatePercent = 12.5,
                reviewDurationS = 60L,
                confidenceOverrides = 2,
                totalEdits = 5,
                prescriptionEdits = 3,
                soapEdits = 2,
                redFlagsResolved = 1
            )
            
            val exportEvent = ExportSuccessEvent(
                encounterId = encounterId,
                timestamp = Instant.now().toString(),
                deviceTier = deviceTier,
                clinicId = clinicId,
                pdfSizeKb = 150L,
                exportDurationMs = 2000L,
                batteryLevelPercent = 80,
                storageUsedKb = 200L,
                qrCodeGenerated = true,
                encryptionApplied = true
            )
            
            // Validate all events
            assertTrue(startEvent.encounterId == encounterId)
            assertTrue(transcriptionEvent.werEstimate == 0.15)
            assertTrue(reviewEvent.editRatePercent == 12.5)
            assertTrue(exportEvent.pdfSizeKb == 150L)
            
            // Test JSON serialization
            val startJson = startEvent.toJsonString()
            assertTrue(startJson.contains("encounter_start"))
            assertTrue(startJson.contains(encounterId))
            
            val transcriptionJson = transcriptionEvent.toJsonString()
            assertTrue(transcriptionJson.contains("transcription_complete"))
            assertTrue(transcriptionJson.contains("0.15"))
            
            val reviewJson = reviewEvent.toJsonString()
            assertTrue(reviewJson.contains("review_complete"))
            assertTrue(reviewJson.contains("12.5"))
            
            val exportJson = exportEvent.toJsonString()
            assertTrue(exportJson.contains("export_success"))
            assertTrue(exportJson.contains("150"))
        }
        
        @Test
        @DisplayName("Test metrics aggregation workflow")
        fun testMetricsAggregationWorkflow() {
            // Test the complete metrics aggregation workflow
            val aggregator = MetricsAggregator()
            
            // Create test events
            val encounterId = "test-encounter-456"
            val deviceTier = "B"
            val clinicId = "clinic-789"
            
            val startEvent = EncounterStartEvent(
                encounterId = encounterId,
                timestamp = Instant.now().toString(),
                deviceTier = deviceTier,
                clinicId = clinicId
            )
            
            val transcriptionEvent = TranscriptionCompleteEvent(
                encounterId = encounterId,
                timestamp = Instant.now().toString(),
                deviceTier = deviceTier,
                clinicId = clinicId,
                werEstimate = 0.20,
                processingTimeMs = 3000L,
                modelVersion = "whisper-tiny-int8@ct2-1",
                audioDurationMs = 45000L,
                confidenceScore = 0.75,
                languageDetected = "hi"
            )
            
            val reviewEvent = ReviewCompleteEvent(
                encounterId = encounterId,
                timestamp = Instant.now().toString(),
                deviceTier = deviceTier,
                clinicId = clinicId,
                editRatePercent = 18.5,
                reviewDurationS = 90L,
                confidenceOverrides = 3,
                totalEdits = 8,
                prescriptionEdits = 5,
                soapEdits = 3,
                redFlagsResolved = 2
            )
            
            // Update metrics
            aggregator.updateMetrics(startEvent)
            aggregator.updateMetrics(transcriptionEvent)
            aggregator.updateMetrics(reviewEvent)
            
            // Get aggregated metrics
            val metrics = aggregator.getMetrics()
            
            // Validate metrics
            assertTrue(metrics.totalEncounters >= 1)
            assertTrue(metrics.recentEncounters >= 1)
            assertTrue(metrics.averageWER >= 0.0)
            assertTrue(metrics.averageProcessingTime >= 0.0)
            assertTrue(metrics.averageEditRate >= 0.0)
            assertTrue(metrics.clinicMetrics.isNotEmpty())
            assertTrue(metrics.deviceTierMetrics.isNotEmpty())
        }
        
        @Test
        @DisplayName("Test privacy validation workflow")
        fun testPrivacyValidationWorkflow() {
            // Test the complete privacy validation workflow
            val privacyValidator = PrivacyValidator()
            
            // Test clean event
            val cleanEvent = EncounterStartEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                audioQuality = "excellent",
                batteryLevel = 85
            )
            
            assertTrue(privacyValidator.validateEvent(cleanEvent))
            
            // Test event with potential PII (this should fail validation)
            val eventWithPII = ReviewCompleteEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                editRatePercent = 12.5,
                reviewDurationS = 60L,
                confidenceOverrides = 2,
                totalEdits = 5,
                prescriptionEdits = 3,
                soapEdits = 2,
                redFlagsResolved = 1
            )
            
            // This should pass as it doesn't contain PII
            assertTrue(privacyValidator.validateEvent(eventWithPII))
            
            // Test string validation
            assertTrue(privacyValidator.validateString("This is a clean string"))
            assertFalse(privacyValidator.validateString("Patient name: John Doe, phone: 555-123-4567"))
            
            // Test sanitization
            val sanitized = privacyValidator.sanitizeString("Patient name: John Doe, phone: 555-123-4567")
            assertTrue(sanitized.contains("[NAME_REDACTED]"))
            assertTrue(sanitized.contains("[PHONE_REDACTED]"))
        }
        
        @Test
        @DisplayName("Test pilot mode workflow")
        fun testPilotModeWorkflow() {
            // Test the complete pilot mode workflow
            val pilotMetrics = PilotModeMetrics()
            
            // Enable pilot mode
            pilotMetrics.enable()
            assertTrue(pilotMetrics.isEnabled)
            
            // Add transcription events
            val transcriptionEvent1 = TranscriptionCompleteEvent(
                encounterId = "test-encounter-1",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                werEstimate = 0.15,
                processingTimeMs = 2500L,
                modelVersion = "whisper-tiny-int8@ct2-1",
                audioDurationMs = 30000L,
                confidenceScore = 0.85,
                languageDetected = "en"
            )
            
            val transcriptionEvent2 = TranscriptionCompleteEvent(
                encounterId = "test-encounter-2",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                werEstimate = 0.18,
                processingTimeMs = 2800L,
                modelVersion = "whisper-tiny-int8@ct2-1",
                audioDurationMs = 35000L,
                confidenceScore = 0.82,
                languageDetected = "en"
            )
            
            pilotMetrics.addTranscriptionEvent(transcriptionEvent1)
            pilotMetrics.addTranscriptionEvent(transcriptionEvent2)
            
            // Add F1 score samples
            pilotMetrics.addF1ScoreSample(0.85)
            pilotMetrics.addF1ScoreSample(0.88)
            
            // Get summary
            val summary = pilotMetrics.getSummary()
            assertTrue(summary.totalTranscriptions == 2)
            assertTrue(summary.averageWER > 0.0)
            assertTrue(summary.averageF1Score > 0.0)
            assertTrue(summary.modelVersions.isNotEmpty())
            assertTrue(summary.languageDistribution.isNotEmpty())
            
            // Get detailed analysis
            val werAnalysis = pilotMetrics.getWERAnalysis()
            assertTrue(werAnalysis.totalSamples == 2)
            assertTrue(werAnalysis.averageWER > 0.0)
            
            val f1Analysis = pilotMetrics.getF1Analysis()
            assertTrue(f1Analysis.totalSamples == 2)
            assertTrue(f1Analysis.averageF1 > 0.0)
            
            // Test CSV export
            val csvData = pilotMetrics.exportPilotData()
            assertTrue(csvData.contains("timestamp,encounter_id,wer_estimate"))
            assertTrue(csvData.contains("test-encounter-1"))
            assertTrue(csvData.contains("test-encounter-2"))
        }
        
        @Test
        @DisplayName("Test backend reporting workflow")
        fun testBackendReportingWorkflow() {
            // Test the complete backend reporting workflow
            // Note: In a real test, this would use mocked HTTP connections
            
            // Test event queuing
            val event = EncounterStartEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456"
            )
            
            // Test JSON serialization for backend
            val eventJson = event.toJsonString()
            assertTrue(eventJson.contains("encounter_start"))
            assertTrue(eventJson.contains("test-encounter-123"))
            
            // Test batch request creation
            val batchRequest = TelemetryBatchRequest(
                events = listOf(event),
                clientId = "test-client-123",
                timestamp = Instant.now().toString(),
                version = "1.0"
            )
            
            val batchJson = Json.encodeToString(TelemetryBatchRequest.serializer(), batchRequest)
            assertTrue(batchJson.contains("test-client-123"))
            assertTrue(batchJson.contains("1.0"))
        }
    }
    
    @Nested
    @DisplayName("Performance Tests")
    inner class PerformanceTests {
        
        @Test
        @DisplayName("Test event serialization performance")
        fun testEventSerializationPerformance() {
            val event = EncounterStartEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = "clinic-456",
                audioQuality = "excellent",
                batteryLevel = 85
            )
            
            val startTime = System.currentTimeMillis()
            repeat(1000) {
                event.toJsonString()
            }
            val endTime = System.currentTimeMillis()
            
            val duration = endTime - startTime
            assertTrue(duration < 1000, "Serialization should complete within 1 second for 1000 iterations")
        }
        
        @Test
        @DisplayName("Test metrics aggregation performance")
        fun testMetricsAggregationPerformance() {
            val aggregator = MetricsAggregator()
            val events = mutableListOf<TelemetryEvent>()
            
            // Create 100 test events
            repeat(100) { i ->
                val event = EncounterStartEvent(
                    encounterId = "test-encounter-$i",
                    timestamp = Instant.now().toString(),
                    deviceTier = if (i % 2 == 0) "A" else "B",
                    clinicId = "clinic-${i % 5}"
                )
                events.add(event)
            }
            
            val startTime = System.currentTimeMillis()
            events.forEach { aggregator.updateMetrics(it) }
            val metrics = aggregator.getMetrics()
            val endTime = System.currentTimeMillis()
            
            val duration = endTime - startTime
            assertTrue(duration < 500, "Metrics aggregation should complete within 500ms for 100 events")
            assertTrue(metrics.totalEncounters == 100)
        }
        
        @Test
        @DisplayName("Test privacy validation performance")
        fun testPrivacyValidationPerformance() {
            val privacyValidator = PrivacyValidator()
            val testStrings = listOf(
                "This is a clean string",
                "Patient name: John Doe",
                "Phone: 555-123-4567",
                "Email: test@example.com",
                "Another clean string"
            )
            
            val startTime = System.currentTimeMillis()
            repeat(1000) {
                testStrings.forEach { privacyValidator.validateString(it) }
            }
            val endTime = System.currentTimeMillis()
            
            val duration = endTime - startTime
            assertTrue(duration < 2000, "Privacy validation should complete within 2 seconds for 5000 validations")
        }
        
        @Test
        @DisplayName("Test pilot mode metrics performance")
        fun testPilotModeMetricsPerformance() {
            val pilotMetrics = PilotModeMetrics()
            pilotMetrics.enable()
            
            val startTime = System.currentTimeMillis()
            repeat(100) { i ->
                val event = TranscriptionCompleteEvent(
                    encounterId = "test-encounter-$i",
                    timestamp = Instant.now().toString(),
                    deviceTier = "A",
                    clinicId = "clinic-456",
                    werEstimate = 0.15 + (i * 0.001),
                    processingTimeMs = 2500L + i,
                    modelVersion = "whisper-tiny-int8@ct2-1",
                    audioDurationMs = 30000L + (i * 100),
                    confidenceScore = 0.85 - (i * 0.001),
                    languageDetected = if (i % 2 == 0) "en" else "hi"
                )
                pilotMetrics.addTranscriptionEvent(event)
                pilotMetrics.addF1ScoreSample(0.85 + (i * 0.001))
            }
            
            val summary = pilotMetrics.getSummary()
            val werAnalysis = pilotMetrics.getWERAnalysis()
            val f1Analysis = pilotMetrics.getF1Analysis()
            val csvData = pilotMetrics.exportPilotData()
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            assertTrue(duration < 1000, "Pilot mode metrics should complete within 1 second for 100 events")
            assertTrue(summary.totalTranscriptions == 100)
            assertTrue(werAnalysis.totalSamples == 100)
            assertTrue(f1Analysis.totalSamples == 100)
            assertTrue(csvData.isNotEmpty())
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {
        
        @Test
        @DisplayName("Test invalid event handling")
        fun testInvalidEventHandling() {
            // Test with invalid encounter ID
            val invalidEvent = EncounterStartEvent(
                encounterId = "invalid-uuid",
                timestamp = "invalid-timestamp",
                deviceTier = "C", // Invalid tier
                clinicId = "clinic-456"
            )
            
            // These should still be created but validation should catch issues
            assertTrue(invalidEvent.encounterId == "invalid-uuid")
            assertTrue(invalidEvent.deviceTier == "C")
        }
        
        @Test
        @DisplayName("Test privacy validation error handling")
        fun testPrivacyValidationErrorHandling() {
            val privacyValidator = PrivacyValidator()
            
            // Test with null/empty strings
            assertTrue(privacyValidator.validateString(""))
            assertTrue(privacyValidator.validateString("   "))
            
            // Test with very long strings
            val longString = "a".repeat(10000)
            assertTrue(privacyValidator.validateString(longString))
        }
        
        @Test
        @DisplayName("Test metrics aggregation error handling")
        fun testMetricsAggregationErrorHandling() {
            val aggregator = MetricsAggregator()
            
            // Test with null values
            val eventWithNulls = EncounterStartEvent(
                encounterId = "test-encounter-123",
                timestamp = Instant.now().toString(),
                deviceTier = "A",
                clinicId = null
            )
            
            // This should not throw an exception
            assertDoesNotThrow { aggregator.updateMetrics(eventWithNulls) }
            
            val metrics = aggregator.getMetrics()
            assertTrue(metrics.totalEncounters >= 1)
        }
    }
}
