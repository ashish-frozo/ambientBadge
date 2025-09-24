package com.frozo.ambientscribe.security

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class AuditEventTest {

    @Test
    fun testAuditEventCreation() {
        val encounterId = "test-encounter-123"
        val kid = "kid-2025Q3"
        val prevHash = "sha256:abc123"
        val event = AuditEvent.AuditEventType.CONSENT_ON
        val ts = "2025-01-19T10:30:00.000Z"
        val actor = AuditEvent.AuditActor.DOCTOR
        val meta = mapOf("reason" to "user_request")

        val auditEvent = AuditEvent(
            encounterId = encounterId,
            kid = kid,
            prevHash = prevHash,
            event = event,
            ts = ts,
            actor = actor,
            meta = meta
        )

        assertEquals(encounterId, auditEvent.encounterId)
        assertEquals(kid, auditEvent.kid)
        assertEquals(prevHash, auditEvent.prevHash)
        assertEquals(event, auditEvent.event)
        assertEquals(ts, auditEvent.ts)
        assertEquals(actor, auditEvent.actor)
        assertEquals(meta, auditEvent.meta)
    }

    @Test
    fun testToJsonString() {
        val auditEvent = AuditEvent(
            encounterId = "test-encounter-123",
            kid = "kid-2025Q3",
            prevHash = "sha256:abc123",
            event = AuditEvent.AuditEventType.CONSENT_ON,
            ts = "2025-01-19T10:30:00.000Z",
            actor = AuditEvent.AuditActor.DOCTOR,
            meta = mapOf("reason" to "user_request")
        )

        val json = auditEvent.toJsonString()
        
        assertTrue(json.contains("\"encounter_id\":\"test-encounter-123\""))
        assertTrue(json.contains("\"kid\":\"kid-2025Q3\""))
        assertTrue(json.contains("\"prev_hash\":\"sha256:abc123\""))
        assertTrue(json.contains("\"event\":\"CONSENT_ON\""))
        assertTrue(json.contains("\"ts\":\"2025-01-19T10:30:00.000Z\""))
        assertTrue(json.contains("\"actor\":\"DOCTOR\""))
        assertTrue(json.contains("\"meta\":{\"reason\":\"user_request\"}"))
    }

    @Test
    fun testToJsonStringWithoutMeta() {
        val auditEvent = AuditEvent(
            encounterId = "test-encounter-123",
            kid = "kid-2025Q3",
            prevHash = "sha256:abc123",
            event = AuditEvent.AuditEventType.CONSENT_ON,
            ts = "2025-01-19T10:30:00.000Z",
            actor = AuditEvent.AuditActor.DOCTOR
        )

        val json = auditEvent.toJsonString()
        
        assertTrue(json.contains("\"encounter_id\":\"test-encounter-123\""))
        assertTrue(json.contains("\"kid\":\"kid-2025Q3\""))
        assertTrue(json.contains("\"prev_hash\":\"sha256:abc123\""))
        assertTrue(json.contains("\"event\":\"CONSENT_ON\""))
        assertTrue(json.contains("\"ts\":\"2025-01-19T10:30:00.000Z\""))
        assertTrue(json.contains("\"actor\":\"DOCTOR\""))
        assertTrue(!json.contains("meta"))
    }

    @Test
    fun testFromJsonString() {
        val json = """{"encounter_id":"test-encounter-123","kid":"kid-2025Q3","prev_hash":"sha256:abc123","event":"CONSENT_ON","ts":"2025-01-19T10:30:00.000Z","actor":"DOCTOR","meta":{"reason":"user_request"}}"""
        
        val auditEvent = AuditEvent.fromJsonString(json)
        
        assertNotNull(auditEvent)
        assertEquals("test-encounter-123", auditEvent!!.encounterId)
        assertEquals("kid-2025Q3", auditEvent.kid)
        assertEquals("sha256:abc123", auditEvent.prevHash)
        assertEquals(AuditEvent.AuditEventType.CONSENT_ON, auditEvent.event)
        assertEquals("2025-01-19T10:30:00.000Z", auditEvent.ts)
        assertEquals(AuditEvent.AuditActor.DOCTOR, auditEvent.actor)
        assertEquals("user_request", auditEvent.meta["reason"])
    }

    @Test
    fun testFromJsonStringInvalid() {
        val invalidJson = "invalid json"
        val auditEvent = AuditEvent.fromJsonString(invalidJson)
        
        assertEquals(null, auditEvent)
    }

    @Test
    fun testEventTypes() {
        val eventTypes = AuditEvent.AuditEventType.values()
        
        assertTrue(eventTypes.contains(AuditEvent.AuditEventType.CONSENT_ON))
        assertTrue(eventTypes.contains(AuditEvent.AuditEventType.CONSENT_OFF))
        assertTrue(eventTypes.contains(AuditEvent.AuditEventType.EXPORT))
        assertTrue(eventTypes.contains(AuditEvent.AuditEventType.ERROR))
        assertTrue(eventTypes.contains(AuditEvent.AuditEventType.PURGE_BUFFER))
        assertTrue(eventTypes.contains(AuditEvent.AuditEventType.PURGE_30S))
        assertTrue(eventTypes.contains(AuditEvent.AuditEventType.SESSION_END))
        assertTrue(eventTypes.contains(AuditEvent.AuditEventType.ABANDON_PURGE))
    }

    @Test
    fun testActorTypes() {
        val actorTypes = AuditEvent.AuditActor.values()
        
        assertTrue(actorTypes.contains(AuditEvent.AuditActor.APP))
        assertTrue(actorTypes.contains(AuditEvent.AuditActor.DOCTOR))
        assertTrue(actorTypes.contains(AuditEvent.AuditActor.ADMIN))
    }
}
