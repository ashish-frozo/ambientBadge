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
class ConsentOffJobCancellerTest {

    private lateinit var context: Context
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var consentOffJobCanceller: ConsentOffJobCanceller

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockAuditLogger = mockk(relaxed = true)

        // Clear test directories
        File(context.filesDir, "docs").deleteRecursively()
        File(context.filesDir, "audit").deleteRecursively()
        File(context.filesDir, "telemetry").deleteRecursively()

        consentOffJobCanceller = ConsentOffJobCanceller(context)
    }

    @Test
    fun `cancelEncounterJobs cancels all jobs for encounter`() = runTest {
        // Given
        val encounterId = "enc_123"

        // When
        val result = consentOffJobCanceller.cancelEncounterJobs(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val cancelResult = result.getOrThrow()
        assertEquals(encounterId, cancelResult.encounterId)
        assertTrue(cancelResult.cancelledJobs >= 0)
        assertTrue(cancelResult.wipedFiles >= 0)
        assertNotNull(cancelResult.timestamp)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CANCELLED_COUNT,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "consent_off_job_cancellation" &&
                    it["encounter_id"] == encounterId
                }
            )
        }
    }

    @Test
    fun `wipeQueuedPayloads removes all payloads for encounter`() = runTest {
        // Given
        val encounterId = "enc_456"
        
        // Create test payload files
        createTestPayloadFiles(encounterId)

        // When
        val result = consentOffJobCanceller.wipeQueuedPayloads(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val wipeResult = result.getOrThrow()
        assertTrue(wipeResult.success)
        assertEquals(encounterId, wipeResult.encounterId)
        assertTrue(wipeResult.payloadsWiped > 0)
        assertNotNull(wipeResult.timestamp)

        // Verify payload files were removed
        val docsDir = File(context.filesDir, "docs")
        val auditDir = File(context.filesDir, "audit")
        val telemetryDir = File(context.filesDir, "telemetry")

        val docsFiles = docsDir.listFiles { file -> file.name.contains(encounterId) } ?: emptyArray()
        val auditFiles = auditDir.listFiles { file -> file.name.contains(encounterId) } ?: emptyArray()
        val telemetryFiles = telemetryDir.listFiles { file -> file.name.contains(encounterId) } ?: emptyArray()

        assertTrue(docsFiles.isEmpty())
        assertTrue(auditFiles.isEmpty())
        assertTrue(telemetryFiles.isEmpty())
    }

    @Test
    fun `wipeQueuedPayloads handles non-existent payloads gracefully`() = runTest {
        // Given
        val encounterId = "non_existent_encounter"

        // When
        val result = consentOffJobCanceller.wipeQueuedPayloads(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val wipeResult = result.getOrThrow()
        assertTrue(wipeResult.success)
        assertEquals(encounterId, wipeResult.encounterId)
        assertEquals(0, wipeResult.payloadsWiped)
    }

    @Test
    fun `cancelBackgroundTasks cancels all background tasks`() = runTest {
        // Given
        val encounterId = "enc_789"

        // When
        val result = consentOffJobCanceller.cancelBackgroundTasks(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val cancelResult = result.getOrThrow()
        assertTrue(cancelResult.success)
        assertEquals(encounterId, cancelResult.encounterId)
        assertTrue(cancelResult.tasksCancelled >= 0)
        assertNotNull(cancelResult.timestamp)
    }

    @Test
    fun `performConsentOffCleanup executes complete cleanup workflow`() = runTest {
        // Given
        val encounterId = "enc_cleanup_test"
        
        // Create test payload files
        createTestPayloadFiles(encounterId)

        // When
        val result = consentOffJobCanceller.performConsentOffCleanup(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val cleanupResult = result.getOrThrow()
        assertTrue(cleanupResult.success)
        assertEquals(encounterId, cleanupResult.encounterId)
        assertTrue(cleanupResult.jobsCancelled >= 0)
        assertTrue(cleanupResult.payloadsWiped >= 0)
        assertTrue(cleanupResult.tasksCancelled >= 0)
        assertNotNull(cleanupResult.timestamp)

        // Verify audit logging for complete workflow
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CANCELLED_COUNT,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "consent_off_job_cancellation" &&
                    it["encounter_id"] == encounterId
                }
            )
        }

        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CANCELLED_COUNT,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "consent_off_payload_wipe" &&
                    it["encounter_id"] == encounterId
                }
            )
        }

        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CANCELLED_COUNT,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "consent_off_background_cancellation" &&
                    it["encounter_id"] == encounterId
                }
            )
        }
    }

    @Test
    fun `performConsentOffCleanup handles empty encounter gracefully`() = runTest {
        // Given
        val encounterId = "empty_encounter"

        // When
        val result = consentOffJobCanceller.performConsentOffCleanup(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val cleanupResult = result.getOrThrow()
        assertTrue(cleanupResult.success)
        assertEquals(encounterId, cleanupResult.encounterId)
        assertEquals(0, cleanupResult.jobsCancelled)
        assertEquals(0, cleanupResult.payloadsWiped)
        assertEquals(0, cleanupResult.tasksCancelled)
    }

    @Test
    fun `testConsentOffCancellation runs all tests successfully`() = runTest {
        // Given
        val encounterId = "test_encounter_123"

        // When
        val result = consentOffJobCanceller.testConsentOffCancellation(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val testResult = result.getOrThrow()
        assertTrue(testResult)

        // Verify audit logging for test
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.CANCELLED_COUNT,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "consent_off_cancellation_test" &&
                    it["encounter_id"] == encounterId
                }
            )
        }
    }

    @Test
    fun `cleanup statistics are calculated correctly`() = runTest {
        // Given
        val encounterId = "enc_stats_test"
        
        // Create multiple test payload files
        createTestPayloadFiles(encounterId, count = 5)

        // When
        val result = consentOffJobCanceller.performConsentOffCleanup(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val cleanupResult = result.getOrThrow()
        assertTrue(cleanupResult.success)
        assertEquals(encounterId, cleanupResult.encounterId)
        assertTrue(cleanupResult.payloadsWiped >= 5) // At least 5 payloads should be wiped
        assertNotNull(cleanupResult.timestamp)
    }

    @Test
    fun `cleanup metadata is saved correctly`() = runTest {
        // Given
        val encounterId = "enc_metadata_test"
        
        // Create test payload files
        createTestPayloadFiles(encounterId)

        // When
        val result = consentOffJobCanceller.performConsentOffCleanup(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val cleanupResult = result.getOrThrow()
        assertTrue(cleanupResult.success)

        // Verify metadata file was created
        val cleanupDir = File(context.filesDir, "consent_off_cleanup")
        assertTrue(cleanupDir.exists())
        val metadataFile = File(cleanupDir, "cleanup_${encounterId}_${cleanupResult.timestamp}.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(encounterId))
        assertTrue(metadataJson.contains(cleanupResult.timestamp))
        assertTrue(metadataJson.contains("true")) // success
    }

    @Test
    fun `cleanup handles different payload types correctly`() = runTest {
        // Given
        val encounterId = "enc_payload_types_test"
        
        // Create different types of payload files
        val docsDir = File(context.filesDir, "docs")
        docsDir.mkdirs()
        File(docsDir, "${encounterId}_document.pdf").writeText("PDF content")
        File(docsDir, "${encounterId}_note.txt").writeText("Note content")

        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        File(auditDir, "${encounterId}_audit.json").writeText("Audit content")

        val telemetryDir = File(context.filesDir, "telemetry")
        telemetryDir.mkdirs()
        File(telemetryDir, "${encounterId}_telemetry.json").writeText("Telemetry content")

        // When
        val result = consentOffJobCanceller.performConsentOffCleanup(encounterId)

        // Then
        assertTrue(result.isSuccess)
        val cleanupResult = result.getOrThrow()
        assertTrue(cleanupResult.success)
        assertTrue(cleanupResult.payloadsWiped >= 4) // At least 4 different payload types

        // Verify all payload files were removed
        val docsFiles = docsDir.listFiles { file -> file.name.contains(encounterId) } ?: emptyArray()
        val auditFiles = auditDir.listFiles { file -> file.name.contains(encounterId) } ?: emptyArray()
        val telemetryFiles = telemetryDir.listFiles { file -> file.name.contains(encounterId) } ?: emptyArray()

        assertTrue(docsFiles.isEmpty())
        assertTrue(auditFiles.isEmpty())
        assertTrue(telemetryFiles.isEmpty())
    }

    // Helper methods
    private fun createTestPayloadFiles(encounterId: String, count: Int = 3) {
        val docsDir = File(context.filesDir, "docs")
        docsDir.mkdirs()
        
        val auditDir = File(context.filesDir, "audit")
        auditDir.mkdirs()
        
        val telemetryDir = File(context.filesDir, "telemetry")
        telemetryDir.mkdirs()

        repeat(count) { i ->
            File(docsDir, "${encounterId}_doc_$i.pdf").writeText("Document content $i")
            File(auditDir, "${encounterId}_audit_$i.json").writeText("Audit content $i")
            File(telemetryDir, "${encounterId}_telemetry_$i.json").writeText("Telemetry content $i")
        }
    }
}
