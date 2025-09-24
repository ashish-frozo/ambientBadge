package com.frozo.ambientscribe.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DSRLogScrubberTest {

    private lateinit var context: Context
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var dsrLogScrubber: DSRLogScrubber

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockAuditLogger = mockk(relaxed = true)

        // Clear test directories
        File(context.filesDir, "audit").deleteRecursively()
        File(context.filesDir, "audit_scrubbed").deleteRecursively()

        dsrLogScrubber = DSRLogScrubber(context, mockAuditLogger)
    }

    @Test
    fun `scrubEncounterLogs removes patient mapping while preserving audit integrity`() = runTest {
        // Given
        val encounterId = "enc_123"
        val patientId = "patient_456"
        val clinicSalt = "clinic_salt_789"

        // Create test audit log with patient mapping
        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit_2023-10-27.jsonl")
        auditFile.writeText("""
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"CONSENT_ON","timestamp":"2023-10-27T10:00:00.000Z","hmac":"abc123"}
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"EXPORT","timestamp":"2023-10-27T11:00:00.000Z","hmac":"def456"}
        """.trimIndent())

        // When
        val result = dsrLogScrubber.scrubEncounterLogs(encounterId, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)
        val scrubResult = result.getOrThrow()
        assertTrue(scrubResult.success)
        assertEquals(encounterId, scrubResult.encounterId)
        assertTrue(scrubResult.eventsScrubbed > 0)
        assertTrue(scrubResult.patientMappingsRemoved > 0)

        // Verify scrubbed file was created
        val scrubbedDir = File(context.filesDir, "audit_scrubbed")
        assertTrue(scrubbedDir.exists())
        val scrubbedFile = File(scrubbedDir, "audit_${encounterId}_scrubbed.jsonl")
        assertTrue(scrubbedFile.exists())

        // Verify scrubbed content doesn't contain patient ID
        val scrubbedContent = scrubbedFile.readText()
        assertFalse(scrubbedContent.contains(patientId))
        assertTrue(scrubbedContent.contains(encounterId)) // Encounter ID should remain
        assertTrue(scrubbedContent.contains("CONSENT_ON")) // Event types should remain
        assertTrue(scrubbedContent.contains("EXPORT"))

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "dsr_log_scrubbing" &&
                    it["encounter_id"] == encounterId
                }
            )
        }
    }

    @Test
    fun `scrubDateRangeLogs processes multiple encounters in date range`() = runTest {
        // Given
        val startDate = "2023-10-01"
        val endDate = "2023-10-31"
        val patientId = "patient_789"
        val clinicSalt = "clinic_salt_456"

        // Create test audit logs for multiple encounters
        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        
        val auditFile1 = File(auditDir, "audit_2023-10-15.jsonl")
        auditFile1.writeText("""
            {"encounter_id":"enc_001","patient_id":"$patientId","event":"CONSENT_ON","timestamp":"2023-10-15T10:00:00.000Z","hmac":"abc123"}
        """.trimIndent())

        val auditFile2 = File(auditDir, "audit_2023-10-20.jsonl")
        auditFile2.writeText("""
            {"encounter_id":"enc_002","patient_id":"$patientId","event":"EXPORT","timestamp":"2023-10-20T11:00:00.000Z","hmac":"def456"}
        """.trimIndent())

        // When
        val result = dsrLogScrubber.scrubDateRangeLogs(startDate, endDate, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)
        val scrubResult = result.getOrThrow()
        assertTrue(scrubResult.success)
        assertTrue(scrubResult.encountersProcessed > 0)
        assertTrue(scrubResult.eventsScrubbed > 0)
        assertTrue(scrubResult.patientMappingsRemoved > 0)

        // Verify scrubbed files were created
        val scrubbedDir = File(context.filesDir, "audit_scrubbed")
        assertTrue(scrubbedDir.exists())
        val scrubbedFiles = scrubbedDir.listFiles { file ->
            file.name.contains("_scrubbed.jsonl")
        }
        assertTrue(scrubbedFiles?.isNotEmpty() == true)
    }

    @Test
    fun `scrubEncounterLogs handles non-existent encounter gracefully`() = runTest {
        // Given
        val encounterId = "non_existent_encounter"
        val patientId = "patient_123"
        val clinicSalt = "clinic_salt_456"

        // When
        val result = dsrLogScrubber.scrubEncounterLogs(encounterId, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)
        val scrubResult = result.getOrThrow()
        assertTrue(scrubResult.success)
        assertEquals(encounterId, scrubResult.encounterId)
        assertEquals(0, scrubResult.eventsScrubbed)
        assertEquals(0, scrubResult.patientMappingsRemoved)
    }

    @Test
    fun `scrubDateRangeLogs handles empty date range gracefully`() = runTest {
        // Given
        val startDate = "2023-01-01"
        val endDate = "2023-01-31"
        val patientId = "patient_123"
        val clinicSalt = "clinic_salt_456"

        // When
        val result = dsrLogScrubber.scrubDateRangeLogs(startDate, endDate, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)
        val scrubResult = result.getOrThrow()
        assertTrue(scrubResult.success)
        assertEquals(0, scrubResult.encountersProcessed)
        assertEquals(0, scrubResult.eventsScrubbed)
        assertEquals(0, scrubResult.patientMappingsRemoved)
    }

    @Test
    fun `scrubEncounterLogs preserves HMAC chain integrity`() = runTest {
        // Given
        val encounterId = "enc_hmac_test"
        val patientId = "patient_hmac"
        val clinicSalt = "clinic_salt_hmac"

        // Create test audit log with HMAC chain
        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit_2023-10-27.jsonl")
        auditFile.writeText("""
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"CONSENT_ON","timestamp":"2023-10-27T10:00:00.000Z","hmac":"abc123","prev_hash":"000000"}
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"EXPORT","timestamp":"2023-10-27T11:00:00.000Z","hmac":"def456","prev_hash":"abc123"}
        """.trimIndent())

        // When
        val result = dsrLogScrubber.scrubEncounterLogs(encounterId, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)
        val scrubResult = result.getOrThrow()
        assertTrue(scrubResult.success)

        // Verify scrubbed file maintains HMAC structure
        val scrubbedFile = File(context.filesDir, "audit_scrubbed", "audit_${encounterId}_scrubbed.jsonl")
        assertTrue(scrubbedFile.exists())
        
        val scrubbedContent = scrubbedFile.readText()
        assertTrue(scrubbedContent.contains("hmac")) // HMAC should be preserved
        assertTrue(scrubbedContent.contains("prev_hash")) // Previous hash should be preserved
        assertFalse(scrubbedContent.contains(patientId)) // Patient ID should be removed
    }

    @Test
    fun `scrubEncounterLogs generates scrubbing statistics`() = runTest {
        // Given
        val encounterId = "enc_stats_test"
        val patientId = "patient_stats"
        val clinicSalt = "clinic_salt_stats"

        // Create test audit log with multiple events
        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit_2023-10-27.jsonl")
        auditFile.writeText("""
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"CONSENT_ON","timestamp":"2023-10-27T10:00:00.000Z","hmac":"abc123"}
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"EXPORT","timestamp":"2023-10-27T11:00:00.000Z","hmac":"def456"}
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"CONSENT_OFF","timestamp":"2023-10-27T12:00:00.000Z","hmac":"ghi789"}
        """.trimIndent())

        // When
        val result = dsrLogScrubber.scrubEncounterLogs(encounterId, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)
        val scrubResult = result.getOrThrow()
        assertTrue(scrubResult.success)
        assertEquals(encounterId, scrubResult.encounterId)
        assertEquals(3, scrubResult.eventsScrubbed) // All 3 events should be scrubbed
        assertEquals(3, scrubResult.patientMappingsRemoved) // All 3 patient mappings should be removed
        assertNotNull(scrubResult.scrubbingMetadata)
        assertTrue(scrubResult.scrubbingMetadata.isNotEmpty())
    }

    @Test
    fun `scrubEncounterLogs handles malformed JSON gracefully`() = runTest {
        // Given
        val encounterId = "enc_malformed"
        val patientId = "patient_malformed"
        val clinicSalt = "clinic_salt_malformed"

        // Create test audit log with malformed JSON
        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit_2023-10-27.jsonl")
        auditFile.writeText("""
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"CONSENT_ON","timestamp":"2023-10-27T10:00:00.000Z","hmac":"abc123"}
            {malformed json line}
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"EXPORT","timestamp":"2023-10-27T11:00:00.000Z","hmac":"def456"}
        """.trimIndent())

        // When
        val result = dsrLogScrubber.scrubEncounterLogs(encounterId, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)
        val scrubResult = result.getOrThrow()
        assertTrue(scrubResult.success)
        assertEquals(2, scrubResult.eventsScrubbed) // Only valid JSON events should be scrubbed
        assertEquals(2, scrubResult.patientMappingsRemoved)
    }

    @Test
    fun `scrubEncounterLogs creates scrubbed file with proper naming`() = runTest {
        // Given
        val encounterId = "enc_naming_test"
        val patientId = "patient_naming"
        val clinicSalt = "clinic_salt_naming"

        // Create test audit log
        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit_2023-10-27.jsonl")
        auditFile.writeText("""
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"CONSENT_ON","timestamp":"2023-10-27T10:00:00.000Z","hmac":"abc123"}
        """.trimIndent())

        // When
        val result = dsrLogScrubber.scrubEncounterLogs(encounterId, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)
        val scrubResult = result.getOrThrow()
        assertTrue(scrubResult.success)

        // Verify scrubbed file naming
        val scrubbedFile = File(context.filesDir, "audit_scrubbed", "audit_${encounterId}_scrubbed.jsonl")
        assertTrue(scrubbedFile.exists())
        assertTrue(scrubbedFile.name.contains(encounterId))
        assertTrue(scrubbedFile.name.contains("_scrubbed"))
        assertTrue(scrubbedFile.name.endsWith(".jsonl"))
    }

    @Test
    fun `scrubEncounterLogs logs audit event for scrubbing operation`() = runTest {
        // Given
        val encounterId = "enc_audit_test"
        val patientId = "patient_audit"
        val clinicSalt = "clinic_salt_audit"

        // Create test audit log
        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit_2023-10-27.jsonl")
        auditFile.writeText("""
            {"encounter_id":"$encounterId","patient_id":"$patientId","event":"CONSENT_ON","timestamp":"2023-10-27T10:00:00.000Z","hmac":"abc123"}
        """.trimIndent())

        // When
        val result = dsrLogScrubber.scrubEncounterLogs(encounterId, patientId, clinicSalt)

        // Then
        assertTrue(result.isSuccess)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "dsr_log_scrubbing" &&
                    it["encounter_id"] == encounterId &&
                    it["events_scrubbed"] == 1 &&
                    it["patient_mappings_removed"] == 1
                }
            )
        }
    }
}
