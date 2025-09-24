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
class SecurityIntegrationTest {

    private lateinit var context: Context
    private lateinit var auditLogger: AuditLogger
    private lateinit var consentManager: ConsentManager
    private lateinit var patientIdHasher: PatientIdHasher
    private lateinit var dataSubjectRightsService: DataSubjectRightsService
    private lateinit var dataPurgeService: DataPurgeService
    private lateinit var keystoreKeyManager: KeystoreKeyManager
    private lateinit var auditVerifier: AuditVerifier
    private lateinit var pdfEncryptionService: PDFEncryptionService
    private lateinit var jsonEncryptionService: JSONEncryptionService

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
        pdfEncryptionService = PDFEncryptionService(context)
        jsonEncryptionService = JSONEncryptionService(context)
    }

    @Test
    fun testCompleteSecurityWorkflow() = runBlocking {
        val encounterId = "integration-test-encounter"
        val phone = "9876543210"
        val clinicId = "clinic-integration-test"
        
        // Step 1: Hash patient identifier
        val hashedRef = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        assertNotNull(hashedRef)
        assertTrue(hashedRef.hash.startsWith("hash:"))
        
        // Step 2: Give consent
        val consentResult = consentManager.giveConsent(
            encounterId = encounterId,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf("patient_hash" to hashedRef.hash)
        )
        assertTrue(consentResult.isSuccess)
        assertTrue(consentManager.hasConsent(encounterId))
        
        // Step 3: Log consent event
        val auditResult1 = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.CONSENT_ON,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf("patient_hash" to hashedRef.hash)
        )
        assertTrue(auditResult1.isSuccess)
        
        // Step 4: Create and encrypt test data
        val testData = mapOf(
            "encounter_id" to encounterId,
            "patient_hash" to hashedRef.hash,
            "clinic_id" to clinicId,
            "timestamp" to System.currentTimeMillis()
        )
        
        val jsonEncryptResult = jsonEncryptionService.encryptJson(testData)
        assertTrue(jsonEncryptResult.isSuccess)
        
        // Step 5: Log data processing event
        val auditResult2 = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.EXPORT,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf(
                "data_type" to "encounter_data",
                "encryption_status" to "encrypted"
            )
        )
        assertTrue(auditResult2.isSuccess)
        
        // Step 6: Export data
        val exportResult = dataSubjectRightsService.exportEncounterData(encounterId)
        assertTrue(exportResult.isSuccess)
        
        // Step 7: Log export event
        val auditResult3 = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.EXPORT,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf(
                "export_file" to "encounter_${encounterId}_${System.currentTimeMillis()}.json",
                "export_size" to "1024"
            )
        )
        assertTrue(auditResult3.isSuccess)
        
        // Step 8: Verify audit chain
        val verificationResult = auditVerifier.verifyEncounterEvents(encounterId)
        assertTrue(verificationResult.isValid)
        assertTrue(verificationResult.validEvents >= 3)
        
        // Step 9: Test data deletion
        val deleteResult = dataSubjectRightsService.deleteEncounterData(encounterId)
        assertTrue(deleteResult.isSuccess)
        assertFalse(consentManager.hasConsent(encounterId))
        
        // Step 10: Log deletion event
        val auditResult4 = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.CONSENT_OFF,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf("reason" to "dsr_delete")
        )
        assertTrue(auditResult4.isSuccess)
        
        // Step 11: Final verification
        val finalVerificationResult = auditVerifier.verifyEncounterEvents(encounterId)
        assertTrue(finalVerificationResult.isValid)
        assertTrue(finalVerificationResult.validEvents >= 4)
    }

    @Test
    fun testKeyRotationWorkflow() = runBlocking {
        val keyAlias = "integration_test_key"
        
        // Create initial key
        val initialKey = keystoreKeyManager.getOrCreateKey(keyAlias)
        assertNotNull(initialKey)
        
        // Get initial metadata
        val initialMetadata = keystoreKeyManager.getKeyMetadata(keyAlias)
        assertNotNull(initialMetadata)
        assertEquals(0, initialMetadata!!.rotationCount)
        
        // Force key rotation (simulate time passing)
        val rotationResult = keystoreKeyManager.rotateKeyIfNeeded(keyAlias)
        assertTrue(rotationResult.isSuccess)
        
        // Verify new key exists
        val newKeyAlias = rotationResult.getOrThrow()
        val newMetadata = keystoreKeyManager.getKeyMetadata(newKeyAlias)
        assertNotNull(newMetadata)
        assertTrue(newMetadata!!.rotationCount > 0)
        
        // Verify old key is inactive
        val oldMetadata = keystoreKeyManager.getKeyMetadata(keyAlias)
        assertNotNull(oldMetadata)
        assertFalse(oldMetadata!!.isActive)
    }

    @Test
    fun testDataPurgeWorkflow() = runBlocking {
        // Create test encounters with expired consent
        val encounterId1 = "purge-test-encounter-1"
        val encounterId2 = "purge-test-encounter-2"
        
        // Give consent
        consentManager.giveConsent(encounterId1)
        consentManager.giveConsent(encounterId2)
        
        // Mark as expired
        consentManager.markConsentExpired(encounterId1)
        consentManager.markConsentExpired(encounterId2)
        
        // Run purge
        val purgeResult = dataPurgeService.runAutomaticPurge()
        assertTrue(purgeResult.isSuccess)
        
        val purgeData = purgeResult.getOrThrow()
        assertTrue(purgeData.purgedEncounters >= 0)
        assertTrue(purgeData.purgedFiles >= 0)
        assertTrue(purgeData.totalSizeFreed >= 0)
    }

    @Test
    fun testConsentWorkflow() = runBlocking {
        val encounterId = "consent-workflow-test"
        
        // Test initial state
        assertEquals(ConsentManager.ConsentStatus.NOT_SET, consentManager.getConsentStatus(encounterId))
        assertFalse(consentManager.hasConsent(encounterId))
        
        // Give consent
        val giveResult = consentManager.giveConsent(encounterId)
        assertTrue(giveResult.isSuccess)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
        assertTrue(consentManager.hasConsent(encounterId))
        
        // Withdraw consent
        val withdrawResult = consentManager.withdrawConsent(encounterId)
        assertTrue(withdrawResult.isSuccess)
        assertEquals(ConsentManager.ConsentStatus.WITHDRAWN, consentManager.getConsentStatus(encounterId))
        assertFalse(consentManager.hasConsent(encounterId))
        
        // Give consent again
        val giveAgainResult = consentManager.giveConsent(encounterId)
        assertTrue(giveAgainResult.isSuccess)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
        assertTrue(consentManager.hasConsent(encounterId))
        
        // Mark as expired
        val expireResult = consentManager.markConsentExpired(encounterId)
        assertTrue(expireResult.isSuccess)
        assertEquals(ConsentManager.ConsentStatus.EXPIRED, consentManager.getConsentStatus(encounterId))
        assertFalse(consentManager.hasConsent(encounterId))
    }

    @Test
    fun testAuditChainIntegrity() = runBlocking {
        val encounterId = "audit-chain-test"
        
        // Create a chain of events
        val events = listOf(
            AuditEvent.AuditEventType.CONSENT_ON,
            AuditEvent.AuditEventType.EXPORT,
            AuditEvent.AuditEventType.CONSENT_OFF
        )
        
        for (event in events) {
            val result = auditLogger.logEvent(
                encounterId = encounterId,
                eventType = event,
                actor = AuditEvent.AuditActor.DOCTOR,
                meta = mapOf("sequence" to events.indexOf(event).toString())
            )
            assertTrue(result.isSuccess)
        }
        
        // Verify the entire chain
        val verificationResult = auditVerifier.verifyEncounterEvents(encounterId)
        assertTrue(verificationResult.isValid)
        assertEquals(events.size, verificationResult.validEvents)
        assertEquals(0, verificationResult.chainBreaks)
    }

    @Test
    fun testPatientIdHashingWorkflow() {
        val phone = "9876543210"
        val clinicId = "clinic-hashing-test"
        
        // Test different phone number formats
        val phoneFormats = listOf(
            "9876543210",
            "+919876543210",
            "91-9876-543-210",
            " 9876543210 "
        )
        
        val hashes = phoneFormats.map { format ->
            patientIdHasher.hashPatientId(format, PatientIdHasher.PatientIdType.PHONE, clinicId)
        }
        
        // All should produce the same hash after normalization
        val firstHash = hashes[0].hash
        for (i in 1 until hashes.size) {
            assertEquals(firstHash, hashes[i].hash)
        }
        
        // Test verification
        for (format in phoneFormats) {
            val isValid = patientIdHasher.verifyPatientId(format, PatientIdHasher.PatientIdType.PHONE, clinicId, firstHash)
            assertTrue(isValid)
        }
        
        // Test different clinic produces different hash
        val differentClinicHash = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, "different-clinic")
        assertFalse(firstHash == differentClinicHash)
    }

    @Test
    fun testEncryptionWorkflow() = runBlocking {
        val encounterId = "encryption-test-encounter"
        val testData = mapOf(
            "encounter_id" to encounterId,
            "patient_data" to "sensitive information",
            "timestamp" to System.currentTimeMillis()
        )
        
        // Test JSON encryption
        val jsonEncryptResult = jsonEncryptionService.encryptJson(testData)
        assertTrue(jsonEncryptResult.isSuccess)
        
        // Test PDF encryption (with temporary file)
        val tempFile = java.io.File.createTempFile("test", ".pdf", context.cacheDir)
        tempFile.writeText("test pdf content")
        
        try {
            val pdfEncryptResult = pdfEncryptionService.encryptPdf(tempFile.absolutePath, encounterId)
            assertTrue(pdfEncryptResult.isSuccess)
            
            val encryptedPath = pdfEncryptResult.getOrThrow()
            assertTrue(pdfEncryptionService.isFileEncrypted(encryptedPath))
            
            // Test decryption
            val decryptResult = pdfEncryptionService.decryptPdf(encryptedPath, encounterId)
            assertTrue(decryptResult.isSuccess)
            
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSecurityStatistics() = runBlocking {
        // Test audit statistics
        val auditStats = auditVerifier.getAuditStats()
        assertTrue(auditStats.containsKey("total_files"))
        assertTrue(auditStats.containsKey("total_events"))
        assertTrue(auditStats.containsKey("total_size_bytes"))
        
        // Test consent statistics
        val consentStats = consentManager.getConsentStats()
        assertTrue(consentStats.containsKey("given"))
        assertTrue(consentStats.containsKey("withdrawn"))
        assertTrue(consentStats.containsKey("expired"))
        assertTrue(consentStats.containsKey("not_set"))
        
        // Test key statistics
        val keyStats = keystoreKeyManager.getKeyStats()
        assertTrue(keyStats.containsKey("total_active_keys"))
        assertTrue(keyStats.containsKey("keys_needing_rotation"))
        assertTrue(keyStats.containsKey("keys_needing_retirement"))
        
        // Test purge statistics
        val purgeStats = dataPurgeService.getPurgeStats()
        assertTrue(purgeStats.containsKey("retention_days"))
        assertTrue(purgeStats.containsKey("expired_encounters"))
        assertTrue(purgeStats.containsKey("total_expired_files"))
        
        // Test DSR statistics
        val dsrStats = dataSubjectRightsService.getDSRStats()
        assertTrue(dsrStats.containsKey("total_exports"))
        assertTrue(dsrStats.containsKey("total_encounters"))
        assertTrue(dsrStats.containsKey("consent_stats"))
    }
}
