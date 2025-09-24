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
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ClinicKeyCustodyServiceTest {

    private lateinit var context: Context
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var clinicKeyCustodyService: ClinicKeyCustodyService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockAuditLogger = mockk(relaxed = true)

        // Clear test directories
        File(context.filesDir, "clinic_vault").deleteRecursively()
        File(context.filesDir, "vault_audit").deleteRecursively()
        File(context.filesDir, "vault_recovery").deleteRecursively()

        clinicKeyCustodyService = ClinicKeyCustodyService(context, mockAuditLogger)
    }

    @Test
    fun `generateAndStoreClinicKey creates key successfully`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val keyType = "RSA"
        val keySize = 2048

        // When
        val result = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, keyType, keySize)

        // Then
        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertNotNull(metadata.keyId)
        assertTrue(metadata.keyId.startsWith("clinic_${clinicId}_rsa_"))
        assertEquals(clinicId, metadata.clinicId)
        assertEquals(keyType, metadata.keyType)
        assertEquals(keySize, metadata.keySize)
        assertTrue(metadata.isActive)
        assertEquals(0, metadata.accessCount)
        assertEquals(0, metadata.rotationCount)
        assertNotNull(metadata.createdAt)
        assertNotNull(metadata.expiresAt)
        assertNotNull(metadata.vaultLocation)
        assertNotNull(metadata.checksum)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "clinic_key_generation" &&
                    it["clinic_id"] == clinicId &&
                    it["key_id"] == metadata.keyId
                }
            )
        }
    }

    @Test
    fun `generateAndStoreClinicKey fails with invalid key type`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val invalidKeyType = "INVALID"
        val keySize = 2048

        // When
        val result = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, invalidKeyType, keySize)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun `rotateClinicKey rotates key successfully`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val keyType = "RSA"
        val keySize = 2048

        // First create a key
        val createResult = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, keyType, keySize)
        assertTrue(createResult.isSuccess)
        val currentKey = createResult.getOrThrow()

        // When
        val result = clinicKeyCustodyService.rotateClinicKey(clinicId, currentKey.keyId, "test_rotation")

        // Then
        assertTrue(result.isSuccess)
        val rotationResult = result.getOrThrow()
        assertTrue(rotationResult.success)
        assertEquals(currentKey.keyId, rotationResult.oldPinId)
        assertNotNull(rotationResult.newPinId)
        assertTrue(rotationResult.newPinId != currentKey.keyId)
        assertNotNull(rotationResult.rotationTimestamp)
        assertTrue(rotationResult.backupCreated)
        assertTrue(rotationResult.rollbackAvailable)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "clinic_key_rotation" &&
                    it["clinic_id"] == clinicId &&
                    it["old_key_id"] == currentKey.keyId
                }
            )
        }
    }

    @Test
    fun `rotateClinicKey fails with non-existent key`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val nonExistentKeyId = "non_existent_key"

        // When
        val result = clinicKeyCustodyService.rotateClinicKey(clinicId, nonExistentKeyId, "test_rotation")

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Key not found") == true)
    }

    @Test
    fun `accessClinicKey succeeds with valid key`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val keyType = "RSA"
        val keySize = 2048

        // Create a key first
        val createResult = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, keyType, keySize)
        assertTrue(createResult.isSuccess)
        val keyMetadata = createResult.getOrThrow()

        // When
        val result = clinicKeyCustodyService.accessClinicKey(
            keyMetadata.keyId,
            "test_actor",
            "test_operation",
            "test_reason"
        )

        // Then
        assertTrue(result.isSuccess)
        val privateKey = result.getOrThrow()
        assertNotNull(privateKey)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "clinic_key_access" &&
                    it["key_id"] == keyMetadata.keyId &&
                    it["actor"] == "test_actor"
                }
            )
        }
    }

    @Test
    fun `accessClinicKey fails with non-existent key`() = runTest {
        // Given
        val nonExistentKeyId = "non_existent_key"

        // When
        val result = clinicKeyCustodyService.accessClinicKey(
            nonExistentKeyId,
            "test_actor",
            "test_operation",
            "test_reason"
        )

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Key not found") == true)
    }

    @Test
    fun `performRecoveryProcedure succeeds with existing keys`() = runTest {
        // Given
        val clinicId = "clinic_123"
        val recoveryReason = "test_recovery"

        // Create some keys first
        val createResult1 = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, "RSA", 2048)
        assertTrue(createResult1.isSuccess)
        val createResult2 = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, "RSA", 2048)
        assertTrue(createResult2.isSuccess)

        // When
        val result = clinicKeyCustodyService.performRecoveryProcedure(clinicId, recoveryReason)

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()
        assertTrue(recoveryResult.success)
        assertNotNull(recoveryResult.recoveryId)
        assertTrue(recoveryResult.recoveryId.startsWith("recovery_${clinicId}_"))
        assertTrue(recoveryResult.keysRecovered >= 0)
        assertNotNull(recoveryResult.auditTrail)
        assertNotNull(recoveryResult.timestamp)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "clinic_key_recovery" &&
                    it["clinic_id"] == clinicId &&
                    it["recovery_id"] == recoveryResult.recoveryId
                }
            )
        }
    }

    @Test
    fun `performRecoveryProcedure handles no keys gracefully`() = runTest {
        // Given
        val clinicId = "clinic_with_no_keys"
        val recoveryReason = "test_recovery"

        // When
        val result = clinicKeyCustodyService.performRecoveryProcedure(clinicId, recoveryReason)

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()
        assertTrue(recoveryResult.success) // Should succeed even with no keys
        assertNotNull(recoveryResult.recoveryId)
        assertEquals(0, recoveryResult.keysRecovered)
        assertNotNull(recoveryResult.auditTrail)
    }

    @Test
    fun `generateRecoveryDocumentation creates documentation successfully`() = runTest {
        // When
        val result = clinicKeyCustodyService.generateRecoveryDocumentation()

        // Then
        assertTrue(result.isSuccess)
        val docPath = result.getOrThrow()
        assertNotNull(docPath)
        assertTrue(docPath.endsWith(".md"))

        // Verify documentation file exists
        val docFile = File(docPath)
        assertTrue(docFile.exists())
        assertTrue(docFile.length() > 0)

        // Verify documentation content
        val docContent = docFile.readText()
        assertTrue(docContent.contains("Clinic Key Recovery Procedure Documentation"))
        assertTrue(docContent.contains("Recovery Scenarios"))
        assertTrue(docContent.contains("Recovery Steps"))
        assertTrue(docContent.contains("Backup Locations"))
        assertTrue(docContent.contains("Contact Information"))
        assertTrue(docContent.contains("Recovery Time Objectives"))
    }

    @Test
    fun `key metadata is stored correctly`() = runTest {
        // Given
        val clinicId = "clinic_metadata_test"
        val keyType = "RSA"
        val keySize = 2048

        // When
        val result = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, keyType, keySize)

        // Then
        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()

        // Verify metadata file was created
        val vaultDir = File(context.filesDir, "clinic_vault")
        assertTrue(vaultDir.exists())

        val metadataFile = File(vaultDir, "${metadata.keyId}_metadata.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(metadata.keyId))
        assertTrue(metadataJson.contains(clinicId))
        assertTrue(metadataJson.contains(keyType))
        assertTrue(metadataJson.contains(keySize.toString()))
        assertTrue(metadataJson.contains("true")) // isActive
    }

    @Test
    fun `key rotation creates backup correctly`() = runTest {
        // Given
        val clinicId = "clinic_backup_test"
        val keyType = "RSA"
        val keySize = 2048

        // Create a key first
        val createResult = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, keyType, keySize)
        assertTrue(createResult.isSuccess)
        val currentKey = createResult.getOrThrow()

        // When
        val result = clinicKeyCustodyService.rotateClinicKey(clinicId, currentKey.keyId, "backup_test")

        // Then
        assertTrue(result.isSuccess)
        val rotationResult = result.getOrThrow()
        assertTrue(rotationResult.backupCreated)

        // Verify backup file was created
        val vaultDir = File(context.filesDir, "clinic_vault")
        val backupDir = File(vaultDir, "backups")
        assertTrue(backupDir.exists())

        val backupFiles = backupDir.listFiles { file ->
            file.name.startsWith("${currentKey.keyId}_backup_") && file.name.endsWith(".json")
        }
        assertTrue(backupFiles?.isNotEmpty() == true)
    }

    @Test
    fun `access audit is logged correctly`() = runTest {
        // Given
        val clinicId = "clinic_audit_test"
        val keyType = "RSA"
        val keySize = 2048

        // Create a key first
        val createResult = clinicKeyCustodyService.generateAndStoreClinicKey(clinicId, keyType, keySize)
        assertTrue(createResult.isSuccess)
        val keyMetadata = createResult.getOrThrow()

        // When
        val result = clinicKeyCustodyService.accessClinicKey(
            keyMetadata.keyId,
            "audit_test_actor",
            "audit_test_operation",
            "audit_test_reason"
        )

        // Then
        assertTrue(result.isSuccess)

        // Verify access audit file was created
        val auditDir = File(context.filesDir, "vault_audit")
        assertTrue(auditDir.exists())

        val auditFiles = auditDir.listFiles { file ->
            file.name.startsWith("access_audit_") && file.name.endsWith(".jsonl")
        }
        assertTrue(auditFiles?.isNotEmpty() == true)

        // Verify audit content
        val auditFile = auditFiles!!.first()
        val auditContent = auditFile.readText()
        assertTrue(auditContent.contains(keyMetadata.keyId))
        assertTrue(auditContent.contains("audit_test_actor"))
        assertTrue(auditContent.contains("audit_test_operation"))
        assertTrue(auditContent.contains("audit_test_reason"))
        assertTrue(auditContent.contains("true")) // success
    }
}
