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
class DPDPComplianceTest {

    private lateinit var context: Context
    private lateinit var consentManager: ConsentManager
    private lateinit var patientIdHasher: PatientIdHasher
    private lateinit var dataSubjectRightsService: DataSubjectRightsService
    private lateinit var dsrLogScrubber: DSRLogScrubber
    private lateinit var auditLogger: AuditLogger

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        consentManager = ConsentManager(context)
        patientIdHasher = PatientIdHasher(context)
        dataSubjectRightsService = DataSubjectRightsService(context)
        dsrLogScrubber = DSRLogScrubber(context)
        auditLogger = AuditLogger(context)
    }

    @Test
    fun testExplicitConsentRequirement() = runBlocking {
        val encounterId = "dpdp-test-encounter"
        
        // Test that data processing requires explicit consent
        assertFalse(consentManager.hasConsent(encounterId))
        assertEquals(ConsentManager.ConsentStatus.NOT_SET, consentManager.getConsentStatus(encounterId))
        
        // Give explicit consent
        val consentResult = consentManager.giveConsent(
            encounterId = encounterId,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf(
                "consent_type" to "explicit",
                "legal_basis" to "consent",
                "purpose" to "medical_transcription"
            )
        )
        
        assertTrue(consentResult.isSuccess)
        assertTrue(consentManager.hasConsent(encounterId))
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
    }

    @Test
    fun testConsentWithdrawal() = runBlocking {
        val encounterId = "dpdp-withdrawal-test"
        
        // Give consent
        consentManager.giveConsent(encounterId)
        assertTrue(consentManager.hasConsent(encounterId))
        
        // Withdraw consent
        val withdrawalResult = consentManager.withdrawConsent(
            encounterId = encounterId,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf(
                "withdrawal_reason" to "patient_request",
                "legal_basis" to "consent_withdrawal"
            )
        )
        
        assertTrue(withdrawalResult.isSuccess)
        assertFalse(consentManager.hasConsent(encounterId))
        assertEquals(ConsentManager.ConsentStatus.WITHDRAWN, consentManager.getConsentStatus(encounterId))
    }

    @Test
    fun testDataMinimization() = runBlocking {
        val phone = "9876543210"
        val clinicId = "clinic-dpdp-test"
        
        // Test that patient data is minimized through hashing
        val hashedRef = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        // Verify original data is not stored
        assertFalse(hashedRef.hash.contains(phone))
        assertTrue(hashedRef.hash.startsWith("hash:"))
        assertTrue(hashedRef.hash.contains("SHA-256"))
        
        // Verify hash can be verified without storing original
        val isValid = patientIdHasher.verifyPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId, hashedRef.hash)
        assertTrue(isValid)
    }

    @Test
    fun testDataSubjectRights() = runBlocking {
        val encounterId = "dpdp-dsr-test"
        
        // Give consent first
        consentManager.giveConsent(encounterId)
        
        // Test right to access (export)
        val exportResult = dataSubjectRightsService.exportEncounterData(encounterId)
        assertTrue(exportResult.isSuccess)
        
        // Test right to erasure (delete)
        val deleteResult = dataSubjectRightsService.deleteEncounterData(encounterId)
        assertTrue(deleteResult.isSuccess)
        
        // Verify data is deleted
        assertFalse(consentManager.hasConsent(encounterId))
    }

    @Test
    fun testDataRetentionLimits() = runBlocking {
        val encounterId = "dpdp-retention-test"
        
        // Give consent
        consentManager.giveConsent(encounterId)
        
        // Mark as expired (simulating 24-hour limit)
        val expireResult = consentManager.markConsentExpired(encounterId)
        assertTrue(expireResult.isSuccess)
        
        // Verify expired consent is not valid
        assertFalse(consentManager.hasConsent(encounterId))
        assertEquals(ConsentManager.ConsentStatus.EXPIRED, consentManager.getConsentStatus(encounterId))
    }

    @Test
    fun testAuditTrailRequirement() = runBlocking {
        val encounterId = "dpdp-audit-test"
        
        // Test that all data processing is audited
        val consentResult = consentManager.giveConsent(encounterId)
        assertTrue(consentResult.isSuccess)
        
        val exportResult = dataSubjectRightsService.exportEncounterData(encounterId)
        assertTrue(exportResult.isSuccess)
        
        val deleteResult = dataSubjectRightsService.deleteEncounterData(encounterId)
        assertTrue(deleteResult.isSuccess)
        
        // Verify audit events were logged
        val verificationResult = auditLogger.verifyAuditChain()
        assertTrue(verificationResult.isSuccess)
        assertTrue(verificationResult.getOrThrow())
    }

    @Test
    fun testDataPortability() = runBlocking {
        val encounterId = "dpdp-portability-test"
        
        // Give consent
        consentManager.giveConsent(encounterId)
        
        // Test data export in structured format
        val exportResult = dataSubjectRightsService.exportEncounterData(encounterId)
        assertTrue(exportResult.isSuccess)
        
        val exportPath = exportResult.getOrThrow()
        val exportFile = java.io.File(exportPath)
        assertTrue(exportFile.exists())
        assertTrue(exportFile.length() > 0)
        
        // Verify export contains structured data
        val exportContent = exportFile.readText()
        assertTrue(exportContent.contains("encounter_id"))
        assertTrue(exportContent.contains("consent_status"))
    }

    @Test
    fun testDataAccuracy() = runBlocking {
        val encounterId = "dpdp-accuracy-test"
        val phone = "9876543210"
        val clinicId = "clinic-accuracy-test"
        
        // Test that data is accurate and up-to-date
        val hashedRef = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        // Verify hash is consistent
        val verifyResult = patientIdHasher.verifyPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId, hashedRef.hash)
        assertTrue(verifyResult)
        
        // Test data rectification (update)
        val updatedPhone = "9876543211"
        val updatedHashedRef = patientIdHasher.hashPatientId(updatedPhone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        // Verify updated data is different
        assertFalse(hashedRef.hash == updatedHashedRef.hash)
    }

    @Test
    fun testPurposeLimitation() = runBlocking {
        val encounterId = "dpdp-purpose-test"
        
        // Test that data is processed only for specified purposes
        val consentResult = consentManager.giveConsent(
            encounterId = encounterId,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf(
                "purpose" to "medical_transcription",
                "legal_basis" to "consent",
                "data_categories" to "audio,transcript,soap_notes"
            )
        )
        
        assertTrue(consentResult.isSuccess)
        
        // Verify consent includes specific purpose
        val consentHistory = consentManager.getConsentHistory(encounterId)
        assertTrue(consentHistory.isNotEmpty())
        val consentEvent = consentHistory[0]
        assertTrue(consentEvent.meta.containsKey("purpose"))
        assertEquals("medical_transcription", consentEvent.meta["purpose"])
    }

    @Test
    fun testStorageLimitation() = runBlocking {
        val encounterId = "dpdp-storage-test"
        
        // Test that data is not stored longer than necessary
        consentManager.giveConsent(encounterId)
        
        // Simulate data processing
        dataSubjectRightsService.exportEncounterData(encounterId)
        
        // Test data deletion after purpose is fulfilled
        val deleteResult = dataSubjectRightsService.deleteEncounterData(encounterId)
        assertTrue(deleteResult.isSuccess)
        
        // Verify data is deleted
        assertFalse(consentManager.hasConsent(encounterId))
    }

    @Test
    fun testLogScrubbingCompliance() = runBlocking {
        val encounterId = "dpdp-scrub-test"
        
        // Give consent and create some data
        consentManager.giveConsent(encounterId)
        dataSubjectRightsService.exportEncounterData(encounterId)
        
        // Test log scrubbing for DSR compliance
        val scrubResult = dsrLogScrubber.scrubEncounterLogs(encounterId, "dsr_request")
        assertTrue(scrubResult.isSuccess)
        
        val scrubData = scrubResult.getOrThrow()
        assertTrue(scrubData.scrubbedEvents > 0)
        assertTrue(scrubData.totalSizeReduced >= 0)
    }

    @Test
    fun testConsentGranularity() = runBlocking {
        val encounterId = "dpdp-granularity-test"
        
        // Test granular consent for different data types
        val audioConsent = consentManager.giveConsent(
            encounterId = "${encounterId}_audio",
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf(
                "data_type" to "audio",
                "purpose" to "transcription",
                "retention_period" to "24_hours"
            )
        )
        
        val transcriptConsent = consentManager.giveConsent(
            encounterId = "${encounterId}_transcript",
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf(
                "data_type" to "transcript",
                "purpose" to "soap_generation",
                "retention_period" to "90_days"
            )
        )
        
        assertTrue(audioConsent.isSuccess)
        assertTrue(transcriptConsent.isSuccess)
        
        // Verify different consent types
        assertTrue(consentManager.hasConsent("${encounterId}_audio"))
        assertTrue(consentManager.hasConsent("${encounterId}_transcript"))
    }

    @Test
    fun testDataBreachNotification() = runBlocking {
        val encounterId = "dpdp-breach-test"
        
        // Simulate data breach scenario
        consentManager.giveConsent(encounterId)
        
        // Log breach event
        val breachResult = auditLogger.logEvent(
            encounterId = encounterId,
            eventType = AuditEvent.AuditEventType.ERROR,
            actor = AuditEvent.AuditActor.APP,
            meta = mapOf(
                "breach_type" to "unauthorized_access",
                "data_categories" to "personal_data",
                "affected_records" to "1",
                "notification_required" to "true"
            )
        )
        
        assertTrue(breachResult.isSuccess)
        
        // Verify breach is logged
        val verificationResult = auditLogger.verifyAuditChain()
        assertTrue(verificationResult.isSuccess)
    }

    @Test
    fun testDataProcessingLawfulness() = runBlocking {
        val encounterId = "dpdp-lawfulness-test"
        
        // Test that data processing has lawful basis
        val consentResult = consentManager.giveConsent(
            encounterId = encounterId,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf(
                "legal_basis" to "consent",
                "purpose" to "medical_care",
                "necessity" to "essential",
                "proportionality" to "minimal"
            )
        )
        
        assertTrue(consentResult.isSuccess)
        
        // Verify legal basis is recorded
        val consentHistory = consentManager.getConsentHistory(encounterId)
        val consentEvent = consentHistory[0]
        assertEquals("consent", consentEvent.meta["legal_basis"])
        assertEquals("medical_care", consentEvent.meta["purpose"])
    }

    @Test
    fun testDataSubjectRightsCompleteness() = runBlocking {
        val encounterId = "dpdp-completeness-test"
        
        // Test all data subject rights
        consentManager.giveConsent(encounterId)
        
        // Right to access
        val accessResult = dataSubjectRightsService.exportEncounterData(encounterId)
        assertTrue(accessResult.isSuccess)
        
        // Right to rectification (update consent)
        val updateResult = consentManager.giveConsent(
            encounterId = encounterId,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf("rectification" to "true")
        )
        assertTrue(updateResult.isSuccess)
        
        // Right to erasure
        val erasureResult = dataSubjectRightsService.deleteEncounterData(encounterId)
        assertTrue(erasureResult.isSuccess)
        
        // Right to portability (already tested in access)
        // Right to restriction (consent withdrawal)
        val restrictionResult = consentManager.withdrawConsent(encounterId)
        assertTrue(restrictionResult.isSuccess)
    }
}
