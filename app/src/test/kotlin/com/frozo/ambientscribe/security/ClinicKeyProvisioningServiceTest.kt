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
class ClinicKeyProvisioningServiceTest {

    private lateinit var context: Context
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var clinicKeyProvisioningService: ClinicKeyProvisioningService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockAuditLogger = mockk(relaxed = true)

        // Clear test directories
        File(context.filesDir, "clinic_keys").deleteRecursively()
        File(context.filesDir, "key_metadata").deleteRecursively()

        clinicKeyProvisioningService = ClinicKeyProvisioningService(context)
    }

    @Test
    fun `uploadClinicKey succeeds with valid RSA key`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val publicKeyPem = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1234567890abcdef
            -----END PUBLIC KEY-----
        """.trimIndent()
        val keyType = "RSA"

        // When
        val result = clinicKeyProvisioningService.uploadClinicKey(clinicId, publicKeyPem, keyType)

        // Then
        assertTrue(result.isSuccess)
        val provisioningResult = result.getOrThrow()
        assertTrue(provisioningResult.success)
        assertEquals(clinicId, provisioningResult.clinicId)
        assertEquals(keyType, provisioningResult.keyType)
        assertEquals("upload", provisioningResult.operation)
        assertNotNull(provisioningResult.keyId)
        assertTrue(provisioningResult.keyId.contains(clinicId))
        assertTrue(provisioningResult.keyId.contains("rsa"))
        assertNotNull(provisioningResult.timestamp)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "key_upload" &&
                    it["clinic_id"] == clinicId &&
                    it["key_type"] == keyType
                }
            )
        }
    }

    @Test
    fun `uploadClinicKey fails with invalid key format`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val invalidKeyPem = "invalid_key_format"
        val keyType = "RSA"

        // When
        val result = clinicKeyProvisioningService.uploadClinicKey(clinicId, invalidKeyPem, keyType)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Invalid public key format") == true)
    }

    @Test
    fun `rotateClinicKey succeeds with valid parameters`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val currentKeyId = "clinic_123_rsa_1234567890_abc12345"
        val newPublicKeyPem = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA9876543210fedcba
            -----END PUBLIC KEY-----
        """.trimIndent()
        val keyType = "RSA"

        // When
        val result = clinicKeyProvisioningService.rotateClinicKey(
            clinicId, currentKeyId, newPublicKeyPem, keyType
        )

        // Then
        assertTrue(result.isSuccess)
        val provisioningResult = result.getOrThrow()
        assertTrue(provisioningResult.success)
        assertEquals(clinicId, provisioningResult.clinicId)
        assertEquals(keyType, provisioningResult.keyType)
        assertEquals("rotation", provisioningResult.operation)
        assertNotNull(provisioningResult.keyId)
        assertTrue(provisioningResult.keyId.contains(clinicId))
        assertTrue(provisioningResult.keyId.contains("rsa"))
        assertNotNull(provisioningResult.timestamp)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "key_rotation" &&
                    it["clinic_id"] == clinicId &&
                    it["old_key_id"] == currentKeyId
                }
            )
        }
    }

    @Test
    fun `pinKey succeeds with valid key ID`() = runTest {
        // Given
        val keyId = "clinic_123_rsa_1234567890_abc12345"

        // When
        val result = clinicKeyProvisioningService.pinKey(keyId)

        // Then
        assertTrue(result.isSuccess)
        val pinResult = result.getOrThrow()
        assertTrue(pinResult)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "key_pinning" &&
                    it["key_id"] == keyId
                }
            )
        }
    }

    @Test
    fun `rollbackKey succeeds with valid parameters`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val currentKeyId = "clinic_123_rsa_1234567890_abc12345"

        // When
        val result = clinicKeyProvisioningService.rollbackKey(clinicId, currentKeyId)

        // Then
        assertTrue(result.isSuccess)
        val provisioningResult = result.getOrThrow()
        assertTrue(provisioningResult.success)
        assertEquals(clinicId, provisioningResult.clinicId)
        assertEquals("rollback", provisioningResult.operation)
        assertNotNull(provisioningResult.keyId)
        assertNotNull(provisioningResult.timestamp)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "key_rollback" &&
                    it["clinic_id"] == clinicId &&
                    it["from_key_id"] == currentKeyId
                }
            )
        }
    }

    @Test
    fun `getActiveKeysForClinic returns empty list when no keys exist`() = runTest {
        // Given - no keys exist

        // When
        val result = clinicKeyProvisioningService.getActiveKeysForClinic("clinic_123")

        // Then
        assertTrue(result.isSuccess)
        val activeKeys = result.getOrThrow()
        assertTrue(activeKeys.isEmpty())
    }

    @Test
    fun `cleanupExpiredKeys returns zero when no expired keys exist`() = runTest {
        // Given - no expired keys exist

        // When
        val result = clinicKeyProvisioningService.cleanupExpiredKeys()

        // Then
        assertTrue(result.isSuccess)
        val cleanedCount = result.getOrThrow()
        assertEquals(0, cleanedCount)
    }

    @Test
    fun `testKeyProvisioning runs all tests successfully`() = runTest {
        // Given
        val clinicId = "test_clinic_123"

        // When
        val result = clinicKeyProvisioningService.testKeyProvisioning(clinicId)

        // Then
        assertTrue(result.isSuccess)
        val testResult = result.getOrThrow()
        assertTrue(testResult)

        // Verify that test created some keys
        val activeKeysResult = clinicKeyProvisioningService.getActiveKeysForClinic(clinicId)
        assertTrue(activeKeysResult.isSuccess)
        val activeKeys = activeKeysResult.getOrThrow()
        assertTrue(activeKeys.isNotEmpty())
    }

    @Test
    fun `key metadata is saved correctly`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val publicKeyPem = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1234567890abcdef
            -----END PUBLIC KEY-----
        """.trimIndent()

        // When
        val result = clinicKeyProvisioningService.uploadClinicKey(clinicId, publicKeyPem, "RSA")

        // Then
        assertTrue(result.isSuccess)
        val provisioningResult = result.getOrThrow()

        // Check that key file was created
        val clinicKeysDir = File(context.filesDir, "clinic_keys")
        assertTrue(clinicKeysDir.exists())

        val keyFile = File(clinicKeysDir, "${provisioningResult.keyId}.key")
        assertTrue(keyFile.exists())

        // Check that metadata file was created
        val keyMetadataDir = File(context.filesDir, "key_metadata")
        assertTrue(keyMetadataDir.exists())

        val metadataFile = File(keyMetadataDir, "${provisioningResult.keyId}.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(provisioningResult.keyId))
        assertTrue(metadataJson.contains(clinicId))
        assertTrue(metadataJson.contains("RSA"))
        assertTrue(metadataJson.contains("true")) // isActive
    }

    @Test
    fun `key rotation creates new metadata files`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val currentKeyId = "clinic_123_rsa_1234567890_abc12345"
        val newPublicKeyPem = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA9876543210fedcba
            -----END PUBLIC KEY-----
        """.trimIndent()

        // When
        val result = clinicKeyProvisioningService.rotateClinicKey(
            clinicId, currentKeyId, newPublicKeyPem, "RSA"
        )

        // Then
        assertTrue(result.isSuccess)
        val provisioningResult = result.getOrThrow()

        // Check that new key file was created
        val clinicKeysDir = File(context.filesDir, "clinic_keys")
        val newKeyFile = File(clinicKeysDir, "${provisioningResult.keyId}.key")
        assertTrue(newKeyFile.exists())

        // Check that new metadata file was created
        val keyMetadataDir = File(context.filesDir, "key_metadata")
        val newMetadataFile = File(keyMetadataDir, "${provisioningResult.keyId}.json")
        assertTrue(newMetadataFile.exists())

        // Verify new metadata content
        val metadataJson = newMetadataFile.readText()
        assertTrue(metadataJson.contains(provisioningResult.keyId))
        assertTrue(metadataJson.contains(clinicId))
        assertTrue(metadataJson.contains("RSA"))
    }

    @Test
    fun `pinKey updates metadata correctly`() = runTest {
        // Given
        val keyId = "clinic_123_rsa_1234567890_abc12345"

        // When
        val result = clinicKeyProvisioningService.pinKey(keyId)

        // Then
        assertTrue(result.isSuccess)

        // Check that metadata file was updated
        val keyMetadataDir = File(context.filesDir, "key_metadata")
        val metadataFile = File(keyMetadataDir, "${keyId}.json")
        if (metadataFile.exists()) {
            val metadataJson = metadataFile.readText()
            assertTrue(metadataJson.contains("true")) // isPinned
        }
    }
}
