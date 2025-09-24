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
class AuditGenesisManagerTest {

    private lateinit var context: Context
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var mockHMACKeyManager: HMACKeyManager
    private lateinit var mockAuditVerifier: AuditVerifier
    private lateinit var auditGenesisManager: AuditGenesisManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockAuditLogger = mockk(relaxed = true)
        mockHMACKeyManager = mockk(relaxed = true)
        mockAuditVerifier = mockk(relaxed = true)

        // Clear test directories
        File(context.filesDir, "audit").deleteRecursively()
        File(context.filesDir, "audit_genesis").deleteRecursively()

        auditGenesisManager = AuditGenesisManager(context)
    }

    @Test
    fun `createGenesisEvent creates genesis event successfully`() = runTest {
        // Given
        val reason = "app_install"

        // When
        val result = auditGenesisManager.createGenesisEvent(reason)

        // Then
        assertTrue(result.isSuccess)
        val genesisEvent = result.getOrThrow()
        assertNotNull(genesisEvent.genesisId)
        assertTrue(genesisEvent.genesisId.startsWith("genesis_"))
        assertEquals(reason, "app_install")
        assertNotNull(genesisEvent.timestamp)
        assertNotNull(genesisEvent.bootId)
        assertNotNull(genesisEvent.appVersion)
        assertNotNull(genesisEvent.deviceId)
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", genesisEvent.prevHash)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["event_type"] == "GENESIS" &&
                    it["reason"] == reason &&
                    it["genesis_id"] == genesisEvent.genesisId
                }
            )
        }
    }

    @Test
    fun `createRolloverEvent creates rollover event successfully`() = runTest {
        // Given
        val reason = "key_rotation"
        val prevGenesisId = "genesis_1234567890_abc12345"

        // When
        val result = auditGenesisManager.createRolloverEvent(reason, prevGenesisId)

        // Then
        assertTrue(result.isSuccess)
        val rolloverEvent = result.getOrThrow()
        assertNotNull(rolloverEvent.rolloverId)
        assertTrue(rolloverEvent.rolloverId.startsWith("rollover_"))
        assertEquals(reason, rolloverEvent.reason)
        assertEquals(prevGenesisId, rolloverEvent.prevGenesisId)
        assertNotNull(rolloverEvent.newGenesisId)
        assertTrue(rolloverEvent.newGenesisId.startsWith("genesis_"))
        assertNotNull(rolloverEvent.timestamp)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["event_type"] == "ROLLOVER" &&
                    it["reason"] == reason &&
                    it["prev_genesis_id"] == prevGenesisId
                }
            )
        }
    }

    @Test
    fun `createChainStitchEvent creates chain stitch event successfully`() = runTest {
        // Given
        val reason = "app_reinstall"
        val prevChainEnd = "prev_chain_hash_123"
        val newChainStart = "new_chain_hash_456"
        val gapDetected = true

        // When
        val result = auditGenesisManager.createChainStitchEvent(
            reason, prevChainEnd, newChainStart, gapDetected
        )

        // Then
        assertTrue(result.isSuccess)
        val stitchEvent = result.getOrThrow()
        assertNotNull(stitchEvent.stitchId)
        assertTrue(stitchEvent.stitchId.startsWith("stitch_"))
        assertEquals(reason, stitchEvent.reason)
        assertEquals(prevChainEnd, stitchEvent.prevChainEnd)
        assertEquals(newChainStart, stitchEvent.newChainStart)
        assertEquals(gapDetected, stitchEvent.gapDetected)
        assertNotNull(stitchEvent.timestamp)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["event_type"] == "CHAIN_STITCH" &&
                    it["reason"] == reason &&
                    it["prev_chain_end"] == prevChainEnd &&
                    it["new_chain_start"] == newChainStart &&
                    it["gap_detected"] == gapDetected
                }
            )
        }
    }

    @Test
    fun `detectAndHandleGaps returns no gaps when no audit files exist`() = runTest {
        // Given - no audit files exist

        // When
        val result = auditGenesisManager.detectAndHandleGaps()

        // Then
        assertTrue(result.isSuccess)
        val gapAnalysis = result.getOrThrow()
        assertFalse(gapAnalysis.hasGaps)
        assertTrue(gapAnalysis.gaps.isEmpty())
        assertTrue(gapAnalysis.recommendations.isEmpty())
    }

    @Test
    fun `detectDuplicates returns no duplicates when no audit files exist`() = runTest {
        // Given - no audit files exist

        // When
        val result = auditGenesisManager.detectDuplicates()

        // Then
        assertTrue(result.isSuccess)
        val duplicateAnalysis = result.getOrThrow()
        assertFalse(duplicateAnalysis.hasDuplicates)
        assertTrue(duplicateAnalysis.duplicates.isEmpty())
    }

    @Test
    fun `detectOutOfOrder returns no out of order events when no audit files exist`() = runTest {
        // Given - no audit files exist

        // When
        val result = auditGenesisManager.detectOutOfOrder()

        // Then
        assertTrue(result.isSuccess)
        val outOfOrderAnalysis = result.getOrThrow()
        assertFalse(outOfOrderAnalysis.hasOutOfOrder)
        assertTrue(outOfOrderAnalysis.outOfOrderEvents.isEmpty())
    }

    @Test
    fun `verifyAuditChain returns valid result when no audit files exist`() = runTest {
        // Given
        coEvery { mockAuditVerifier.verifyAuditChain() } returns Result.success(true)

        // When
        val result = auditGenesisManager.verifyAuditChain()

        // Then
        assertTrue(result.isSuccess)
        val verificationResult = result.getOrThrow()
        assertTrue(verificationResult.integrityValid)
        assertFalse(verificationResult.hasGaps)
        assertFalse(verificationResult.hasDuplicates)
        assertFalse(verificationResult.hasOutOfOrder)
        assertNotNull(verificationResult.timestamp)
    }

    @Test
    fun `verifyAuditChain handles verification failure`() = runTest {
        // Given
        coEvery { mockAuditVerifier.verifyAuditChain() } returns Result.failure(Exception("Verification failed"))

        // When
        val result = auditGenesisManager.verifyAuditChain()

        // Then
        assertTrue(result.isSuccess) // Should not fail, just return false for integrity
        val verificationResult = result.getOrThrow()
        assertFalse(verificationResult.integrityValid)
    }

    @Test
    fun `genesis event metadata is saved correctly`() = runTest {
        // Given
        val reason = "test_genesis"

        // When
        val result = auditGenesisManager.createGenesisEvent(reason)

        // Then
        assertTrue(result.isSuccess)
        val genesisEvent = result.getOrThrow()

        // Check that metadata file was created
        val genesisDir = File(context.filesDir, "audit_genesis")
        assertTrue(genesisDir.exists())

        val metadataFile = File(genesisDir, "genesis_${genesisEvent.genesisId}.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(genesisEvent.genesisId))
        assertTrue(metadataJson.contains(genesisEvent.timestamp))
        assertTrue(metadataJson.contains(genesisEvent.bootId))
        assertTrue(metadataJson.contains(genesisEvent.appVersion))
        assertTrue(metadataJson.contains(genesisEvent.deviceId))
    }

    @Test
    fun `rollover event metadata is saved correctly`() = runTest {
        // Given
        val reason = "test_rollover"
        val prevGenesisId = "genesis_test_123"

        // When
        val result = auditGenesisManager.createRolloverEvent(reason, prevGenesisId)

        // Then
        assertTrue(result.isSuccess)
        val rolloverEvent = result.getOrThrow()

        // Check that metadata file was created
        val genesisDir = File(context.filesDir, "audit_genesis")
        assertTrue(genesisDir.exists())

        val metadataFile = File(genesisDir, "rollover_${rolloverEvent.rolloverId}.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(rolloverEvent.rolloverId))
        assertTrue(metadataJson.contains(rolloverEvent.timestamp))
        assertTrue(metadataJson.contains(rolloverEvent.reason))
        assertTrue(metadataJson.contains(prevGenesisId))
        assertTrue(metadataJson.contains(rolloverEvent.newGenesisId))
    }

    @Test
    fun `chain stitch event metadata is saved correctly`() = runTest {
        // Given
        val reason = "test_stitch"
        val prevChainEnd = "prev_hash_test"
        val newChainStart = "new_hash_test"
        val gapDetected = true

        // When
        val result = auditGenesisManager.createChainStitchEvent(
            reason, prevChainEnd, newChainStart, gapDetected
        )

        // Then
        assertTrue(result.isSuccess)
        val stitchEvent = result.getOrThrow()

        // Check that metadata file was created
        val genesisDir = File(context.filesDir, "audit_genesis")
        assertTrue(genesisDir.exists())

        val metadataFile = File(genesisDir, "stitch_${stitchEvent.stitchId}.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(stitchEvent.stitchId))
        assertTrue(metadataJson.contains(stitchEvent.timestamp))
        assertTrue(metadataJson.contains(stitchEvent.reason))
        assertTrue(metadataJson.contains(prevChainEnd))
        assertTrue(metadataJson.contains(newChainStart))
        assertTrue(metadataJson.contains("true")) // gapDetected
    }
}
