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
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class TLSCertificatePinnerTest {

    private lateinit var context: Context
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var tlsCertificatePinner: TLSCertificatePinner

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockAuditLogger = mockk(relaxed = true)

        // Clear test directories
        File(context.filesDir, "cert_pins").deleteRecursively()
        File(context.filesDir, "pin_rotation").deleteRecursively()
        File(context.filesDir, "pin_tests").deleteRecursively()

        tlsCertificatePinner = TLSCertificatePinner(context, mockAuditLogger)
    }

    @Test
    fun `createPinnedHttpClient creates client successfully`() = runTest {
        // Given
        val hostname = "example.com"

        // When
        val client = tlsCertificatePinner.createPinnedHttpClient(hostname)

        // Then
        assertNotNull(client)
        assertNotNull(client.certificatePinner)
    }

    @Test
    fun `addCertificatePin succeeds with valid certificate`() = runTest {
        // Given
        val hostname = "example.com"
        val certificate = createTestCertificate(hostname)
        val pinType = "sha256"

        // When
        val result = tlsCertificatePinner.addCertificatePin(hostname, certificate, pinType)

        // Then
        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertNotNull(metadata.pinId)
        assertTrue(metadata.pinId.startsWith("pin_${hostname.replace(".", "_")}_sha256_"))
        assertEquals(hostname, metadata.hostname)
        assertEquals(pinType, metadata.pinType)
        assertNotNull(metadata.pinValue)
        assertTrue(metadata.pinValue.startsWith("sha256/"))
        assertTrue(metadata.isActive)
        assertEquals(0, metadata.rotationCount)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.POLICY_TOGGLE,
                actor = AuditEvent.AuditActor.ADMIN,
                meta = match {
                    it["operation"] == "cert_pin_add" &&
                    it["hostname"] == hostname &&
                    it["pin_id"] == metadata.pinId
                }
            )
        }
    }

    @Test
    fun `addCertificatePin fails with invalid pin type`() = runTest {
        // Given
        val hostname = "example.com"
        val certificate = createTestCertificate(hostname)
        val invalidPinType = "invalid"

        // When
        val result = tlsCertificatePinner.addCertificatePin(hostname, certificate, invalidPinType)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Unsupported pin type") == true)
    }

    @Test
    fun `rotateCertificatePin rotates pin successfully`() = runTest {
        // Given
        val hostname = "example.com"
        val oldCertificate = createTestCertificate(hostname)
        val newCertificate = createTestCertificate(hostname)

        // First add a pin
        val addResult = tlsCertificatePinner.addCertificatePin(hostname, oldCertificate, "sha256")
        assertTrue(addResult.isSuccess)
        val oldPin = addResult.getOrThrow()

        // When
        val result = tlsCertificatePinner.rotateCertificatePin(hostname, newCertificate, "test_rotation")

        // Then
        assertTrue(result.isSuccess)
        val rotationResult = result.getOrThrow()
        assertTrue(rotationResult.success)
        assertEquals(oldPin.pinId, rotationResult.oldPinId)
        assertNotNull(rotationResult.newPinId)
        assertTrue(rotationResult.newPinId != oldPin.pinId)
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
                    it["operation"] == "cert_pin_rotation" &&
                    it["hostname"] == hostname &&
                    it["old_pin_id"] == oldPin.pinId
                }
            )
        }
    }

    @Test
    fun `rotateCertificatePin fails with no existing pin`() = runTest {
        // Given
        val hostname = "example.com"
        val newCertificate = createTestCertificate(hostname)

        // When
        val result = tlsCertificatePinner.rotateCertificatePin(hostname, newCertificate, "test_rotation")

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("No active pin found") == true)
    }

    @Test
    fun `performPinBreakTest succeeds with valid connection`() = runTest {
        // Given
        val hostname = "example.com"
        val certificate = createTestCertificate(hostname)

        // Add a pin first
        val addResult = tlsCertificatePinner.addCertificatePin(hostname, certificate, "sha256")
        assertTrue(addResult.isSuccess)

        // When
        val result = tlsCertificatePinner.performPinBreakTest(hostname)

        // Then
        assertTrue(result.isSuccess)
        val testResult = result.getOrThrow()
        assertNotNull(testResult.testId)
        assertTrue(testResult.testId.startsWith("test_${hostname.replace(".", "_")}_"))
        assertEquals(hostname, testResult.hostname)
        assertEquals("pin_break_test", testResult.testType)
        assertNotNull(testResult.timestamp)
        assertTrue(testResult.responseTime >= 0)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["operation"] == "cert_pin_break_test" &&
                    it["hostname"] == hostname &&
                    it["test_id"] == testResult.testId
                }
            )
        }
    }

    @Test
    fun `performPinBreakTest handles connection failure gracefully`() = runTest {
        // Given
        val hostname = "invalid-hostname-that-does-not-exist.com"

        // When
        val result = tlsCertificatePinner.performPinBreakTest(hostname)

        // Then
        assertTrue(result.isSuccess) // Should not throw, but return failure result
        val testResult = result.getOrThrow()
        assertFalse(testResult.success)
        assertNotNull(testResult.testId)
        assertEquals(hostname, testResult.hostname)
        assertFalse(testResult.connectionSuccessful)
        assertFalse(testResult.pinValidationPassed)
        assertNotNull(testResult.errorMessage)
    }

    @Test
    fun `generateRotationPlaybook creates comprehensive playbook`() = runTest {
        // Given
        val hostname = "example.com"
        val oldCertificate = createTestCertificate(hostname)
        val newCertificate = createTestCertificate(hostname)

        // Add a pin first
        val addResult = tlsCertificatePinner.addCertificatePin(hostname, oldCertificate, "sha256")
        assertTrue(addResult.isSuccess)
        val oldPin = addResult.getOrThrow()

        // When
        val result = tlsCertificatePinner.generateRotationPlaybook(hostname, oldPin, oldPin)

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        assertEquals(6, result.size) // Should have 6 steps

        // Verify playbook steps
        val steps = result
        assertEquals(1, steps[0].step)
        assertTrue(steps[0].action.contains("Backup"))
        assertTrue(steps[0].description.contains("backup"))

        assertEquals(2, steps[1].step)
        assertTrue(steps[1].action.contains("Validate"))
        assertTrue(steps[1].description.contains("certificate"))

        assertEquals(3, steps[2].step)
        assertTrue(steps[2].action.contains("Add"))
        assertTrue(steps[2].description.contains("pin"))

        assertEquals(4, steps[3].step)
        assertTrue(steps[3].action.contains("Test"))
        assertTrue(steps[3].description.contains("connection"))

        assertEquals(5, steps[4].step)
        assertTrue(steps[4].action.contains("Deactivate"))
        assertTrue(steps[4].description.contains("old"))

        assertEquals(6, steps[5].step)
        assertTrue(steps[5].action.contains("Monitor"))
        assertTrue(steps[5].description.contains("24 hours"))

        // Verify playbook file was created
        val rotationDir = File(context.filesDir, "pin_rotation")
        assertTrue(rotationDir.exists())

        val playbookFiles = rotationDir.listFiles { file ->
            file.name.startsWith("rotation_playbook_${hostname}_") && file.name.endsWith(".json")
        }
        assertTrue(playbookFiles?.isNotEmpty() == true)
    }

    @Test
    fun `pin metadata is stored correctly`() = runTest {
        // Given
        val hostname = "example.com"
        val certificate = createTestCertificate(hostname)
        val pinType = "sha256"

        // When
        val result = tlsCertificatePinner.addCertificatePin(hostname, certificate, pinType)

        // Then
        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()

        // Verify pin file was created
        val pinDir = File(context.filesDir, "cert_pins")
        assertTrue(pinDir.exists())

        val metadataFile = File(pinDir, "${metadata.pinId}_metadata.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(metadata.pinId))
        assertTrue(metadataJson.contains(hostname))
        assertTrue(metadataJson.contains(pinType))
        assertTrue(metadataJson.contains(metadata.pinValue))
        assertTrue(metadataJson.contains("true")) // isActive
    }

    @Test
    fun `pin rotation creates backup correctly`() = runTest {
        // Given
        val hostname = "example.com"
        val oldCertificate = createTestCertificate(hostname)
        val newCertificate = createTestCertificate(hostname)

        // Add a pin first
        val addResult = tlsCertificatePinner.addCertificatePin(hostname, oldCertificate, "sha256")
        assertTrue(addResult.isSuccess)
        val oldPin = addResult.getOrThrow()

        // When
        val result = tlsCertificatePinner.rotateCertificatePin(hostname, newCertificate, "backup_test")

        // Then
        assertTrue(result.isSuccess)
        val rotationResult = result.getOrThrow()
        assertTrue(rotationResult.backupCreated)

        // Verify backup file was created
        val pinDir = File(context.filesDir, "cert_pins")
        val backupDir = File(pinDir, "backups")
        assertTrue(backupDir.exists())

        val backupFiles = backupDir.listFiles { file ->
            file.name.startsWith("${oldPin.pinId}_backup_") && file.name.endsWith(".json")
        }
        assertTrue(backupFiles?.isNotEmpty() == true)
    }

    @Test
    fun `test result is stored correctly`() = runTest {
        // Given
        val hostname = "example.com"
        val certificate = createTestCertificate(hostname)

        // Add a pin first
        val addResult = tlsCertificatePinner.addCertificatePin(hostname, certificate, "sha256")
        assertTrue(addResult.isSuccess)

        // When
        val result = tlsCertificatePinner.performPinBreakTest(hostname)

        // Then
        assertTrue(result.isSuccess)
        val testResult = result.getOrThrow()

        // Verify test file was created
        val testDir = File(context.filesDir, "pin_tests")
        assertTrue(testDir.exists())

        val testFile = File(testDir, "test_${testResult.testId}.json")
        assertTrue(testFile.exists())

        // Verify test content
        val testJson = testFile.readText()
        assertTrue(testJson.contains(testResult.testId))
        assertTrue(testJson.contains(hostname))
        assertTrue(testJson.contains("pin_break_test"))
        assertTrue(testJson.contains(testResult.success.toString()))
    }

    // Helper method to create test certificate
    private fun createTestCertificate(hostname: String): X509Certificate {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        // Create a mock certificate for testing
        // In a real implementation, this would use a proper certificate builder
        return object : X509Certificate() {
            override fun getVersion(): Int = 3
            override fun getSerialNumber(): java.math.BigInteger = java.math.BigInteger.ONE
            override fun getIssuerDN(): X500Principal = X500Principal("CN=Test CA")
            override fun getSubjectDN(): X500Principal = X500Principal("CN=$hostname")
            override fun getNotBefore(): Date = Date()
            override fun getNotAfter(): Date = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
            override fun getTBSCertificate(): ByteArray = "test".toByteArray()
            override fun getSignature(): ByteArray = "test".toByteArray()
            override fun getSigAlgName(): String = "SHA256withRSA"
            override fun getSigAlgOID(): String = "1.2.840.113549.1.1.11"
            override fun getSigAlgParams(): ByteArray = "test".toByteArray()
            override fun getIssuerUniqueID(): BooleanArray = booleanArrayOf()
            override fun getSubjectUniqueID(): BooleanArray = booleanArrayOf()
            override fun getKeyUsage(): BooleanArray = BooleanArray(9)
            override fun getExtendedKeyUsage(): List<String> = emptyList()
            override fun getBasicConstraints(): Int = -1
            override fun getEncoded(): ByteArray = "test_certificate_$hostname".toByteArray()
            override fun verify(key: java.security.PublicKey) {}
            override fun verify(key: java.security.PublicKey, sigProvider: String) {}
            override fun toString(): String = "Test Certificate for $hostname"
            override fun getPublicKey(): java.security.PublicKey = keyPair.public
            override fun checkValidity() {}
            override fun checkValidity(date: Date) {}
            override fun getCriticalExtensionOIDs(): java.util.Set<String> = emptySet()
            override fun getExtensionValue(oid: String): ByteArray? = null
            override fun getNonCriticalExtensionOIDs(): java.util.Set<String> = emptySet()
            override fun hasUnsupportedCriticalExtension(): Boolean = false
        }
    }
}
