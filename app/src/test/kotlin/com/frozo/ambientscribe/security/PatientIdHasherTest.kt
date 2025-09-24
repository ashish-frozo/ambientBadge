package com.frozo.ambientscribe.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PatientIdHasherTest {

    private lateinit var context: Context
    private lateinit var patientIdHasher: PatientIdHasher

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        patientIdHasher = PatientIdHasher(context)
    }

    @Test
    fun testHashPhoneNumber() {
        val phone = "9876543210"
        val clinicId = "clinic-123"
        
        val result = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        assertNotNull(result)
        assertEquals(PatientIdHasher.PatientIdType.PHONE, result.idType)
        assertEquals(phone.length, result.originalLength)
        assertTrue(result.hash.startsWith("hash:"))
        assertTrue(result.hash.contains("SHA-256"))
    }

    @Test
    fun testHashMRN() {
        val mrn = "MRN123456"
        val clinicId = "clinic-123"
        
        val result = patientIdHasher.hashPatientId(mrn, PatientIdHasher.PatientIdType.MRN, clinicId)
        
        assertNotNull(result)
        assertEquals(PatientIdHasher.PatientIdType.MRN, result.idType)
        assertEquals(mrn.length, result.originalLength)
        assertTrue(result.hash.startsWith("hash:"))
    }

    @Test
    fun testHashOther() {
        val other = "patient-abc-123"
        val clinicId = "clinic-123"
        
        val result = patientIdHasher.hashPatientId(other, PatientIdHasher.PatientIdType.OTHER, clinicId)
        
        assertNotNull(result)
        assertEquals(PatientIdHasher.PatientIdType.OTHER, result.idType)
        assertEquals(other.length, result.originalLength)
        assertTrue(result.hash.startsWith("hash:"))
    }

    @Test
    fun testPhoneNumberNormalization() {
        val phone1 = "9876543210"
        val phone2 = "+919876543210"
        val phone3 = "91-9876-543-210"
        val clinicId = "clinic-123"
        
        val result1 = patientIdHasher.hashPatientId(phone1, PatientIdHasher.PatientIdType.PHONE, clinicId)
        val result2 = patientIdHasher.hashPatientId(phone2, PatientIdHasher.PatientIdType.PHONE, clinicId)
        val result3 = patientIdHasher.hashPatientId(phone3, PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        // All should produce the same hash after normalization
        assertEquals(result1.hash, result2.hash)
        assertEquals(result1.hash, result3.hash)
    }

    @Test
    fun testMRNNormalization() {
        val mrn1 = "mrn123456"
        val mrn2 = "MRN123456"
        val mrn3 = " mrn123456 "
        val clinicId = "clinic-123"
        
        val result1 = patientIdHasher.hashPatientId(mrn1, PatientIdHasher.PatientIdType.MRN, clinicId)
        val result2 = patientIdHasher.hashPatientId(mrn2, PatientIdHasher.PatientIdType.MRN, clinicId)
        val result3 = patientIdHasher.hashPatientId(mrn3, PatientIdHasher.PatientIdType.MRN, clinicId)
        
        // All should produce the same hash after normalization
        assertEquals(result1.hash, result2.hash)
        assertEquals(result1.hash, result3.hash)
    }

    @Test
    fun testOtherNormalization() {
        val other1 = "patient-123"
        val other2 = " patient-123 "
        val clinicId = "clinic-123"
        
        val result1 = patientIdHasher.hashPatientId(other1, PatientIdHasher.PatientIdType.OTHER, clinicId)
        val result2 = patientIdHasher.hashPatientId(other2, PatientIdHasher.PatientIdType.OTHER, clinicId)
        
        // Both should produce the same hash after normalization
        assertEquals(result1.hash, result2.hash)
    }

    @Test
    fun testVerifyPatientId() {
        val phone = "9876543210"
        val clinicId = "clinic-123"
        
        val hashedRef = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        // Verify with correct ID
        val isValid = patientIdHasher.verifyPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId, hashedRef.hash)
        assertTrue(isValid)
        
        // Verify with incorrect ID
        val isInvalid = patientIdHasher.verifyPatientId("1234567890", PatientIdHasher.PatientIdType.PHONE, clinicId, hashedRef.hash)
        assertFalse(isInvalid)
    }

    @Test
    fun testDifferentClinicsProduceDifferentHashes() {
        val phone = "9876543210"
        val clinic1 = "clinic-123"
        val clinic2 = "clinic-456"
        
        val result1 = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinic1)
        val result2 = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinic2)
        
        // Different clinics should produce different hashes
        assertFalse(result1.hash == result2.hash)
    }

    @Test
    fun testSaltRotation() {
        val phone = "9876543210"
        val clinicId = "clinic-123"
        
        // Hash with current salt
        val result1 = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        // Rotate salt
        patientIdHasher.rotateSalt(clinicId)
        
        // Hash again with new salt
        val result2 = patientIdHasher.hashPatientId(phone, PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        // Should produce different hashes due to different salts
        assertFalse(result1.hash == result2.hash)
    }

    @Test
    fun testSaltStats() {
        val clinicId = "clinic-123"
        
        // Generate some hashes to create salt data
        patientIdHasher.hashPatientId("9876543210", PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        val stats = patientIdHasher.getSaltStats()
        
        assertTrue(stats.containsKey("total_salts"))
        assertTrue(stats.containsKey("expired_salts"))
        assertTrue(stats.containsKey("active_clinics"))
        assertTrue(stats.containsKey("clinics"))
    }

    @Test
    fun testNeedsSaltRotation() {
        val clinicId = "clinic-123"
        
        // Initially should not need rotation
        assertFalse(patientIdHasher.needsSaltRotation(clinicId))
        
        // After generating a hash, should still not need rotation
        patientIdHasher.hashPatientId("9876543210", PatientIdHasher.PatientIdType.PHONE, clinicId)
        assertFalse(patientIdHasher.needsSaltRotation(clinicId))
    }

    @Test
    fun testCleanupOldSalts() {
        val clinicId = "clinic-123"
        
        // Generate some hashes
        patientIdHasher.hashPatientId("9876543210", PatientIdHasher.PatientIdType.PHONE, clinicId)
        
        // Cleanup old salts (should not remove current salt)
        patientIdHasher.cleanupOldSalts()
        
        // Should still be able to hash with same clinic
        val result = patientIdHasher.hashPatientId("9876543210", PatientIdHasher.PatientIdType.PHONE, clinicId)
        assertNotNull(result)
    }
}
