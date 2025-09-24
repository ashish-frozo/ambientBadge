package com.frozo.ambientscribe.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SecurityComplianceTest {

    private lateinit var context: Context
    private lateinit var auditLogger: AuditLogger
    private lateinit var consentManager: ConsentManager
    private lateinit var patientIdHasher: PatientIdHasher
    private lateinit var dataSubjectRightsService: DataSubjectRightsService
    private lateinit var dataPurgeService: DataPurgeService
    private lateinit var keystoreKeyManager: KeystoreKeyManager
    private lateinit var auditVerifier: AuditVerifier

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        auditLogger = AuditLogger(context)
        consentManager = ConsentManager(context)
        patientIdHasher = PatientIdHasher(context)
        dataSubjectRightsService = DataSubjectRightsService(context)
        dataPurgeService = DataPurgeService(context)
        keystoreKeyManager = KeystoreKeyManager(context)
        auditVerifier = AuditVerifier(context)
    }

    @Test
    fun testAuditLoggingCompliance() = runBlocking {
        val encounterId = "test-encounter-123"
        
        // Log various audit events
        val result1 = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.CONSENT_ON,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf("reason" to "user_request")
        )
        
        assertTrue(result1.isSuccess)
        
        val result2 = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.EXPORT,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf("file_size" to "1024")
        )
        
        assertTrue(result2.isSuccess)
        
        // Verify audit chain
        val verificationResult = auditVerifier.verifyAuditChain()
        assertTrue(verificationResult.isValid)
        assertTrue(verificationResult.validEvents >= 2)
    }

    @Test
    fun testConsentManagementCompliance() = runBlocking {
        val encounterId = "test-encounter-456"
        
        // Test consent workflow
        val giveResult = consentManager.giveConsent(encounterId)
        assertTrue(giveResult.isSuccess)
        assertTrue(consentManager.hasConsent(encounterId))
        
        val withdrawResult = consentManager.withdrawConsent(encounterId)
        assertTrue(withdrawResult.isSuccess)
        assertFalse(consentManager.hasConsent(encounterId))
        
        // Test consent statistics
        val stats = consentManager.getConsentStats()
        assertTrue(stats.containsKey("given"))
        assertTrue(stats.containsKey("withdrawn"))
    }

    @Test
    fun testPatientIdHashingCompliance() {
        val phone = "9876543210"
        val clinicId = "clinic-123"
        
        // Test hashing
        val hashedRef = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        assertNotNull(hashedRef)
        assertTrue(hashedRef.hash.startsWith("hash:"))
        assertTrue(hashedRef.hash.contains("SHA-256"))
        
        // Test verification
        val isValid = patientIdHasher.verifyPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId, hashedRef.hash)
        assertTrue(isValid)
        
        // Test different clinics produce different hashes
        val hashedRef2 = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, "clinic-456")
        assertFalse(hashedRef.hash == hashedRef2.hash)
    }

    @Test
    fun testDataSubjectRightsCompliance() = runBlocking {
        val encounterId = "test-encounter-789"
        
        // Give consent first
        consentManager.giveConsent(encounterId)
        
        // Test export
        val exportResult = dataSubjectRightsService.exportEncounterData(encounterId)
        assertTrue(exportResult.isSuccess)
        
        // Test delete
        val deleteResult = dataSubjectRightsService.deleteEncounterData(encounterId)
        assertTrue(deleteResult.isSuccess)
        
        // Verify consent is withdrawn after delete
        assertFalse(consentManager.hasConsent(encounterId))
    }

    @Test
    fun testDataPurgeCompliance() = runBlocking {
        // Test purge statistics
        val stats = dataPurgeService.getPurgeStats()
        assertTrue(stats.containsKey("retention_days"))
        assertTrue(stats.containsKey("expired_encounters"))
        assertTrue(stats.containsKey("total_expired_files"))
        
        // Test automatic purge (should not fail even with no data)
        val purgeResult = dataPurgeService.runAutomaticPurge()
        assertTrue(purgeResult.isSuccess)
    }

    @Test
    fun testKeystoreKeyManagementCompliance() = runBlocking {
        val keyAlias = "test_key_123"
        
        // Test key creation
        val keyResult = keystoreKeyManager.getOrCreateKey(keyAlias)
        assertNotNull(keyResult)
        
        // Test key metadata
        val metadata = keystoreKeyManager.getKeyMetadata(keyAlias)
        assertNotNull(metadata)
        assertEquals(keyAlias, metadata!!.keyAlias)
        assertTrue(metadata.isActive)
        
        // Test key statistics
        val stats = keystoreKeyManager.getKeyStats()
        assertTrue(stats.containsKey("total_active_keys"))
        assertTrue(stats.containsKey("keys_needing_rotation"))
        
        // Test key integrity
        val isIntact = keystoreKeyManager.verifyKeyIntegrity(keyAlias)
        assertTrue(isIntact)
    }

    @Test
    fun testEncryptionServicesCompliance() = runBlocking {
        // Test PDF encryption service
        val pdfEncryptionService = PDFEncryptionService(context)
        val testData = "test pdf content"
        
        // Create a temporary file for testing
        val tempFile = java.io.File.createTempFile("test", ".pdf", context.cacheDir)
        tempFile.writeText(testData)
        
        try {
            val encryptResult = pdfEncryptionService.encryptPdf(tempFile.absolutePath, "test-encounter-123")
            assertTrue(encryptResult.isSuccess)
            
            val encryptedPath = encryptResult.getOrThrow()
            assertTrue(pdfEncryptionService.isFileEncrypted(encryptedPath))
            
            val decryptResult = pdfEncryptionService.decryptPdf(encryptedPath, "test-encounter-123")
            assertTrue(decryptResult.isSuccess)
            
        } finally {
            tempFile.delete()
        }
        
        // Test JSON encryption service
        val jsonEncryptionService = JSONEncryptionService(context)
        val testJson = mapOf("test" to "data", "number" to 123)
        
        val encryptJsonResult = jsonEncryptionService.encryptJson(testJson)
        assertTrue(encryptJsonResult.isSuccess)
    }

    @Test
    fun testSecurityManagerCompliance() {
        val securityManager = SecurityManager(context)
        
        // Test biometric availability check
        val isBiometricAvailable = securityManager.isBiometricAvailable()
        // This will depend on device capabilities, so we just test it doesn't crash
        assertNotNull(isBiometricAvailable)
        
        // Test screen capture prevention (would need activity context in real test)
        // securityManager.preventScreenCapture(activity)
        // securityManager.allowScreenCapture(activity)
    }

    @Test
    fun testEndToEndSecurityWorkflow() = runBlocking {
        val encounterId = "e2e-test-encounter"
        val phone = "9876543210"
        val clinicId = "clinic-e2e-test"
        
        // 1. Hash patient ID
        val hashedRef = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        assertNotNull(hashedRef)
        
        // 2. Give consent
        val consentResult = consentManager.giveConsent(encounterId)
        assertTrue(consentResult.isSuccess)
        
        // 3. Log audit events
        val auditResult1 = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.CONSENT_ON,
            actor = AuditEvent.AuditActor.DOCTOR
        )
        assertTrue(auditResult1.isSuccess)
        
        val auditResult2 = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.EXPORT,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf("patient_hash" to hashedRef.hash)
        )
        assertTrue(auditResult2.isSuccess)
        
        // 4. Export data
        val exportResult = dataSubjectRightsService.exportEncounterData(encounterId)
        assertTrue(exportResult.isSuccess)
        
        // 5. Verify audit chain
        val verificationResult = auditVerifier.verifyEncounterEvents(encounterId)
        assertTrue(verificationResult.isValid)
        assertTrue(verificationResult.validEvents >= 2)
        
        // 6. Clean up
        val deleteResult = dataSubjectRightsService.deleteEncounterData(encounterId)
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun testPrivacyCompliance() = runBlocking {
        val encounterId = "privacy-test-encounter"
        val phone = "9876543210"
        val clinicId = "clinic-privacy-test"
        
        // Test that patient data is properly hashed
        val hashedRef = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        assertFalse(hashedRef.hash.contains(phone)) // Should not contain original phone number
        
        // Test consent tracking
        consentManager.giveConsent(encounterId)
        assertTrue(consentManager.hasConsent(encounterId))
        
        // Test data subject rights
        val exportResult = dataSubjectRightsService.exportEncounterData(encounterId)
        assertTrue(exportResult.isSuccess)
        
        // Test data deletion
        val deleteResult = dataSubjectRightsService.deleteEncounterData(encounterId)
        assertTrue(deleteResult.isSuccess)
        assertFalse(consentManager.hasConsent(encounterId))
    }
}
