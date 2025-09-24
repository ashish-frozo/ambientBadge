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
class DeviceLossRecoveryServiceTest {

    private lateinit var context: Context
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var deviceLossRecoveryService: DeviceLossRecoveryService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockAuditLogger = mockk(relaxed = true)

        // Clear test directories
        File(context.filesDir, "recovery").deleteRecursively()
        File(context.filesDir, "clinic_keys").deleteRecursively()

        deviceLossRecoveryService = DeviceLossRecoveryService(context, mockAuditLogger)
    }

    @Test
    fun `encryptPdfWithClinicKey succeeds with valid parameters`() = runTest {
        // Given
        val pdfPath = createTestPdf()
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val clinicPublicKey = generateTestClinicPublicKey()

        // When
        val result = deviceLossRecoveryService.encryptPdfWithClinicKey(
            pdfPath, encounterId, clinicId, clinicPublicKey
        )

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()
        assertTrue(recoveryResult.success)
        assertEquals(encounterId, recoveryResult.encounterId)
        assertEquals(clinicId, recoveryResult.clinicId)
        assertNotNull(recoveryResult.encryptedPdfPath)
        assertTrue(File(recoveryResult.encryptedPdfPath).exists())

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "device_loss_recovery" &&
                    it["encounter_id"] == encounterId &&
                    it["clinic_id"] == clinicId
                }
            )
        }
    }

    @Test
    fun `encryptPdfWithClinicKey fails with non-existent PDF`() = runTest {
        // Given
        val pdfPath = "/non/existent/path.pdf"
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val clinicPublicKey = generateTestClinicPublicKey()

        // When
        val result = deviceLossRecoveryService.encryptPdfWithClinicKey(
            pdfPath, encounterId, clinicId, clinicPublicKey
        )

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("PDF file not found") == true)
    }

    @Test
    fun `encryptPdfWithClinicKey fails with invalid clinic public key`() = runTest {
        // Given
        val pdfPath = createTestPdf()
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val invalidClinicPublicKey = "invalid_key_format"

        // When
        val result = deviceLossRecoveryService.encryptPdfWithClinicKey(
            pdfPath, encounterId, clinicId, invalidClinicPublicKey
        )

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Invalid clinic public key") == true)
    }

    @Test
    fun `uploadEncryptedPdf succeeds with valid parameters`() = runTest {
        // Given
        val encryptedPdfPath = createTestEncryptedPdf()
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val serverEndpoint = "https://recovery.example.com/upload"

        // When
        val result = deviceLossRecoveryService.uploadEncryptedPdf(
            encryptedPdfPath, encounterId, clinicId, serverEndpoint
        )

        // Then
        assertTrue(result.isSuccess)
        val uploadResult = result.getOrThrow()
        assertTrue(uploadResult.success)
        assertEquals(encounterId, uploadResult.encounterId)
        assertEquals(clinicId, uploadResult.clinicId)
        assertNotNull(uploadResult.uploadId)
        assertNotNull(uploadResult.serverResponse)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "encrypted_pdf_upload" &&
                    it["encounter_id"] == encounterId &&
                    it["clinic_id"] == clinicId
                }
            )
        }
    }

    @Test
    fun `uploadEncryptedPdf fails with non-existent encrypted PDF`() = runTest {
        // Given
        val encryptedPdfPath = "/non/existent/encrypted.pdf"
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val serverEndpoint = "https://recovery.example.com/upload"

        // When
        val result = deviceLossRecoveryService.uploadEncryptedPdf(
            encryptedPdfPath, encounterId, clinicId, serverEndpoint
        )

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Encrypted PDF file not found") == true)
    }

    @Test
    fun `uploadEncryptedPdf fails with invalid server endpoint`() = runTest {
        // Given
        val encryptedPdfPath = createTestEncryptedPdf()
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val invalidServerEndpoint = "invalid_url"

        // When
        val result = deviceLossRecoveryService.uploadEncryptedPdf(
            encryptedPdfPath, encounterId, clinicId, invalidServerEndpoint
        )

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Invalid server endpoint") == true)
    }

    @Test
    fun `generateRecoveryMetadata creates valid metadata`() = runTest {
        // Given
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val encryptedPdfPath = createTestEncryptedPdf()
        val uploadId = "upload_789"

        // When
        val result = deviceLossRecoveryService.generateRecoveryMetadata(
            encounterId, clinicId, encryptedPdfPath, uploadId
        )

        // Then
        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertEquals(encounterId, metadata.encounterId)
        assertEquals(clinicId, metadata.clinicId)
        assertEquals(encryptedPdfPath, metadata.encryptedPdfPath)
        assertEquals(uploadId, metadata.uploadId)
        assertNotNull(metadata.timestamp)
        assertNotNull(metadata.recoveryId)
        assertTrue(metadata.recoveryId.startsWith("recovery_"))
    }

    @Test
    fun `saveRecoveryMetadata saves metadata to file`() = runTest {
        // Given
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val encryptedPdfPath = createTestEncryptedPdf()
        val uploadId = "upload_789"

        val metadataResult = deviceLossRecoveryService.generateRecoveryMetadata(
            encounterId, clinicId, encryptedPdfPath, uploadId
        )
        assertTrue(metadataResult.isSuccess)
        val metadata = metadataResult.getOrThrow()

        // When
        val result = deviceLossRecoveryService.saveRecoveryMetadata(metadata)

        // Then
        assertTrue(result.isSuccess)

        // Verify metadata file was created
        val recoveryDir = File(context.filesDir, "recovery")
        assertTrue(recoveryDir.exists())
        val metadataFile = File(recoveryDir, "recovery_${metadata.recoveryId}.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(encounterId))
        assertTrue(metadataJson.contains(clinicId))
        assertTrue(metadataJson.contains(encryptedPdfPath))
        assertTrue(metadataJson.contains(uploadId))
        assertTrue(metadataJson.contains(metadata.recoveryId))
    }

    @Test
    fun `performDeviceLossRecovery executes complete recovery workflow`() = runTest {
        // Given
        val pdfPath = createTestPdf()
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val clinicPublicKey = generateTestClinicPublicKey()
        val serverEndpoint = "https://recovery.example.com/upload"

        // When
        val result = deviceLossRecoveryService.performDeviceLossRecovery(
            pdfPath, encounterId, clinicId, clinicPublicKey, serverEndpoint
        )

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()
        assertTrue(recoveryResult.success)
        assertEquals(encounterId, recoveryResult.encounterId)
        assertEquals(clinicId, recoveryResult.clinicId)
        assertNotNull(recoveryResult.encryptedPdfPath)
        assertNotNull(recoveryResult.uploadId)
        assertNotNull(recoveryResult.recoveryId)

        // Verify audit logging for complete workflow
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "device_loss_recovery" &&
                    it["encounter_id"] == encounterId &&
                    it["clinic_id"] == clinicId
                }
            )
        }

        coVerify {
            mockAuditLogger.logEvent(
                encounterId = encounterId,
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "encrypted_pdf_upload" &&
                    it["encounter_id"] == encounterId &&
                    it["clinic_id"] == clinicId
                }
            )
        }
    }

    @Test
    fun `performDeviceLossRecovery fails if PDF encryption fails`() = runTest {
        // Given
        val pdfPath = "/non/existent/path.pdf"
        val encounterId = "enc_123"
        val clinicId = "clinic_456"
        val clinicPublicKey = generateTestClinicPublicKey()
        val serverEndpoint = "https://recovery.example.com/upload"

        // When
        val result = deviceLossRecoveryService.performDeviceLossRecovery(
            pdfPath, encounterId, clinicId, clinicPublicKey, serverEndpoint
        )

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun `testDeviceLossRecovery runs all tests successfully`() = runTest {
        // Given
        val clinicId = "test_clinic_123"
        val clinicPublicKey = generateTestClinicPublicKey()
        val serverEndpoint = "https://recovery.example.com/upload"

        // When
        val result = deviceLossRecoveryService.testDeviceLossRecovery(
            clinicId, clinicPublicKey, serverEndpoint
        )

        // Then
        assertTrue(result.isSuccess)
        val testResult = result.getOrThrow()
        assertTrue(testResult)

        // Verify audit logging for test
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "device_loss_recovery_test" &&
                    it["clinic_id"] == clinicId
                }
            )
        }
    }

    // Helper methods
    private fun createTestPdf(): String {
        val pdfFile = File(context.cacheDir, "test_document.pdf")
        pdfFile.writeText("This is a test PDF content for device loss recovery testing.")
        return pdfFile.absolutePath
    }

    private fun createTestEncryptedPdf(): String {
        val encryptedPdfFile = File(context.cacheDir, "test_document_encrypted.pdf")
        encryptedPdfFile.writeText("This is encrypted PDF content for device loss recovery testing.")
        return encryptedPdfFile.absolutePath
    }

    private fun generateTestClinicPublicKey(): String {
        return """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1234567890abcdef
            -----END PUBLIC KEY-----
        """.trimIndent()
    }
}
