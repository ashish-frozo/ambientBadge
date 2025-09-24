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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ConsentManagerTest {

    private lateinit var context: Context
    private lateinit var consentManager: ConsentManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        consentManager = ConsentManager(context)
    }

    @Test
    fun testInitialConsentStatus() {
        val encounterId = "test-encounter-123"
        val status = consentManager.getConsentStatus(encounterId)
        assertEquals(ConsentManager.ConsentStatus.NOT_SET, status)
    }

    @Test
    fun testGiveConsent() = runBlocking {
        val encounterId = "test-encounter-123"
        
        val result = consentManager.giveConsent(encounterId)
        
        assertTrue(result.isSuccess)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
        assertTrue(consentManager.hasConsent(encounterId))
    }

    @Test
    fun testWithdrawConsent() = runBlocking {
        val encounterId = "test-encounter-123"
        
        // Give consent first
        consentManager.giveConsent(encounterId)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
        
        // Withdraw consent
        val result = consentManager.withdrawConsent(encounterId)
        
        assertTrue(result.isSuccess)
        assertEquals(ConsentManager.ConsentStatus.WITHDRAWN, consentManager.getConsentStatus(encounterId))
        assertFalse(consentManager.hasConsent(encounterId))
    }

    @Test
    fun testConsentExpiration() = runBlocking {
        val encounterId = "test-encounter-123"
        
        // Give consent
        consentManager.giveConsent(encounterId)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
        
        // Mark as expired
        val result = consentManager.markConsentExpired(encounterId)
        
        assertTrue(result.isSuccess)
        assertEquals(ConsentManager.ConsentStatus.EXPIRED, consentManager.getConsentStatus(encounterId))
        assertFalse(consentManager.hasConsent(encounterId))
    }

    @Test
    fun testConsentHistory() = runBlocking {
        val encounterId = "test-encounter-123"
        
        // Give consent
        consentManager.giveConsent(encounterId)
        
        val history = consentManager.getConsentHistory(encounterId)
        assertEquals(1, history.size)
        assertEquals(encounterId, history[0].encounterId)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, history[0].status)
    }

    @Test
    fun testConsentStats() = runBlocking {
        val encounterId1 = "test-encounter-123"
        val encounterId2 = "test-encounter-456"
        
        // Give consent for first encounter
        consentManager.giveConsent(encounterId1)
        
        // Withdraw consent for second encounter
        consentManager.giveConsent(encounterId2)
        consentManager.withdrawConsent(encounterId2)
        
        val stats = consentManager.getConsentStats()
        
        assertTrue(stats["given"]!! >= 1)
        assertTrue(stats["withdrawn"]!! >= 1)
    }

    @Test
    fun testCleanupExpiredConsent() = runBlocking {
        val encounterId = "test-encounter-123"
        
        // Give consent
        consentManager.giveConsent(encounterId)
        
        // Mark as expired
        consentManager.markConsentExpired(encounterId)
        
        // Cleanup expired consent
        val result = consentManager.cleanupExpiredConsent()
        
        assertTrue(result.isSuccess)
        // Note: In a real test, we would need to mock time to test actual expiration
    }

    @Test
    fun testConsentWithMeta() = runBlocking {
        val encounterId = "test-encounter-123"
        val meta = mapOf("reason" to "user_request", "source" to "ui")
        
        val result = consentManager.giveConsent(
            encounterId = encounterId,
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = meta
        )
        
        assertTrue(result.isSuccess)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
    }

    @Test
    fun testMultipleConsentChanges() = runBlocking {
        val encounterId = "test-encounter-123"
        
        // Give consent
        consentManager.giveConsent(encounterId)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
        
        // Withdraw consent
        consentManager.withdrawConsent(encounterId)
        assertEquals(ConsentManager.ConsentStatus.WITHDRAWN, consentManager.getConsentStatus(encounterId))
        
        // Give consent again
        consentManager.giveConsent(encounterId)
        assertEquals(ConsentManager.ConsentStatus.GIVEN, consentManager.getConsentStatus(encounterId))
    }
}
