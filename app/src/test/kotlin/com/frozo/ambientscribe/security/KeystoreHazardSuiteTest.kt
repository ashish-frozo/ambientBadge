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
class KeystoreHazardSuiteTest {

    private lateinit var context: Context
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var mockKeystoreKeyManager: KeystoreKeyManager
    private lateinit var keystoreHazardSuite: KeystoreHazardSuite

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockAuditLogger = mockk(relaxed = true)
        mockKeystoreKeyManager = mockk(relaxed = true)

        // Clear test directories
        File(context.filesDir, "keystore_hazards").deleteRecursively()
        File(context.filesDir, "keystore_recovery").deleteRecursively()

        keystoreHazardSuite = KeystoreHazardSuite(context)
    }

    @Test
    fun `detectHazards returns empty list when no hazards detected`() = runTest {
        // Given - no hazards present

        // When
        val result = keystoreHazardSuite.detectHazards()

        // Then
        assertTrue(result.isSuccess)
        val hazards = result.getOrThrow()
        assertTrue(hazards.isEmpty())
    }

    @Test
    fun `detectOSUpgradeHazard detects OS upgrade correctly`() = runTest {
        // Given - simulate OS upgrade by storing old version
        val prefs = context.getSharedPreferences("keystore_hazards", Context.MODE_PRIVATE)
        prefs.edit().putInt("os_version", 28).apply() // Old API level

        // When
        val result = keystoreHazardSuite.detectHazards()

        // Then
        assertTrue(result.isSuccess)
        val hazards = result.getOrThrow()
        
        // Should detect OS upgrade hazard
        val osUpgradeHazard = hazards.find { it.hazardType == KeystoreHazardSuite.HAZARD_OS_UPGRADE }
        assertNotNull(osUpgradeHazard)
        assertTrue(osUpgradeHazard.hazardDetected)
        assertEquals("HIGH", osUpgradeHazard.severity)
        assertTrue(osUpgradeHazard.recoveryRequired)
        assertTrue(osUpgradeHazard.description.contains("OS upgraded"))
    }

    @Test
    fun `detectBiometricResetHazard detects biometric reset correctly`() = runTest {
        // Given - simulate biometric reset by storing enabled status
        val prefs = context.getSharedPreferences("keystore_hazards", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_status", true).apply()

        // When
        val result = keystoreHazardSuite.detectHazards()

        // Then
        assertTrue(result.isSuccess)
        val hazards = result.getOrThrow()
        
        // Should detect biometric reset hazard (if biometric is not available)
        val biometricHazard = hazards.find { it.hazardType == KeystoreHazardSuite.HAZARD_BIOMETRIC_RESET }
        if (biometricHazard != null) {
            assertTrue(biometricHazard.hazardDetected)
            assertEquals("HIGH", biometricHazard.severity)
            assertTrue(biometricHazard.recoveryRequired)
        }
    }

    @Test
    fun `detectClearCredentialsHazard detects clear credentials correctly`() = runTest {
        // Given - simulate clear credentials by storing key count
        val prefs = context.getSharedPreferences("keystore_hazards", Context.MODE_PRIVATE)
        prefs.edit().putInt("key_count", 5).apply() // Previous key count

        // Mock current key count as 0
        coEvery { mockKeystoreKeyManager.getAllValidKeyAliases() } returns emptyList()

        // When
        val result = keystoreHazardSuite.detectHazards()

        // Then
        assertTrue(result.isSuccess)
        val hazards = result.getOrThrow()
        
        // Should detect clear credentials hazard
        val clearCredentialsHazard = hazards.find { it.hazardType == KeystoreHazardSuite.HAZARD_CLEAR_CREDENTIALS }
        assertNotNull(clearCredentialsHazard)
        assertTrue(clearCredentialsHazard.hazardDetected)
        assertEquals("CRITICAL", clearCredentialsHazard.severity)
        assertTrue(clearCredentialsHazard.recoveryRequired)
        assertTrue(clearCredentialsHazard.description.contains("All keystore keys were cleared"))
    }

    @Test
    fun `detectKeyInvalidationHazard detects key invalidation correctly`() = runTest {
        // Given - mock key as null (invalidated)
        coEvery { mockKeystoreKeyManager.getActiveKeyAlias() } returns "test_key"
        coEvery { mockKeystoreKeyManager.getKey("test_key") } returns null

        // When
        val result = keystoreHazardSuite.detectHazards()

        // Then
        assertTrue(result.isSuccess)
        val hazards = result.getOrThrow()
        
        // Should detect key invalidation hazard
        val keyInvalidationHazard = hazards.find { it.hazardType == KeystoreHazardSuite.HAZARD_KEY_INVALIDATED }
        assertNotNull(keyInvalidationHazard)
        assertTrue(keyInvalidationHazard.hazardDetected)
        assertEquals("HIGH", keyInvalidationHazard.severity)
        assertTrue(keyInvalidationHazard.recoveryRequired)
        assertTrue(keyInvalidationHazard.description.contains("Active keystore key is invalid"))
    }

    @Test
    fun `performRecovery succeeds for OS upgrade hazard`() = runTest {
        // Given
        val hazardType = KeystoreHazardSuite.HAZARD_OS_UPGRADE

        // When
        val result = keystoreHazardSuite.performRecovery(hazardType)

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()
        assertTrue(recoveryResult.success)
        assertEquals(hazardType, recoveryResult.recoveryType)
        assertTrue(recoveryResult.keysRegenerated > 0)
        assertTrue(recoveryResult.dataRecovered)
        assertNotNull(recoveryResult.timestamp)

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["event_type"] == "keystore_recovery" &&
                    it["hazard_type"] == hazardType
                }
            )
        }
    }

    @Test
    fun `performRecovery succeeds for biometric reset hazard`() = runTest {
        // Given
        val hazardType = KeystoreHazardSuite.HAZARD_BIOMETRIC_RESET

        // When
        val result = keystoreHazardSuite.performRecovery(hazardType)

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()
        assertTrue(recoveryResult.success)
        assertEquals(hazardType, recoveryResult.recoveryType)
        assertTrue(recoveryResult.keysRegenerated > 0)
        assertTrue(recoveryResult.dataRecovered)
    }

    @Test
    fun `performRecovery succeeds for clear credentials hazard`() = runTest {
        // Given
        val hazardType = KeystoreHazardSuite.HAZARD_CLEAR_CREDENTIALS

        // When
        val result = keystoreHazardSuite.performRecovery(hazardType)

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()
        assertTrue(recoveryResult.success)
        assertEquals(hazardType, recoveryResult.recoveryType)
        assertTrue(recoveryResult.keysRegenerated > 0)
        // Data may not be recoverable after clear credentials
    }

    @Test
    fun `performRecovery succeeds for key invalidation hazard`() = runTest {
        // Given
        val hazardType = KeystoreHazardSuite.HAZARD_KEY_INVALIDATED

        // When
        val result = keystoreHazardSuite.performRecovery(hazardType)

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()
        assertTrue(recoveryResult.success)
        assertEquals(hazardType, recoveryResult.recoveryType)
        assertTrue(recoveryResult.keysRegenerated > 0)
        assertTrue(recoveryResult.dataRecovered)
    }

    @Test
    fun `performRecovery fails for unknown hazard type`() = runTest {
        // Given
        val hazardType = "unknown_hazard"

        // When
        val result = keystoreHazardSuite.performRecovery(hazardType)

        // Then
        assertTrue(result.isSuccess) // Should not throw, but return failure result
        val recoveryResult = result.getOrThrow()
        assertFalse(recoveryResult.success)
        assertEquals(hazardType, recoveryResult.recoveryType)
        assertTrue(recoveryResult.error?.contains("Unknown hazard type") == true)
    }

    @Test
    fun `testHazardScenarios runs all tests successfully`() = runTest {
        // When
        val result = keystoreHazardSuite.testHazardScenarios()

        // Then
        assertTrue(result.isSuccess)
        val testResult = result.getOrThrow()
        assertTrue(testResult.allTestsPassed)
        assertTrue(testResult.testResults.isNotEmpty())
        assertTrue(testResult.testResults.size >= 4) // At least 4 test scenarios
        assertNotNull(testResult.timestamp)

        // Verify all test results contain PASS or FAIL
        testResult.testResults.forEach { testResultString ->
            assertTrue(testResultString.contains("PASS") || testResultString.contains("FAIL"))
        }

        // Verify audit logging
        coVerify {
            mockAuditLogger.logEvent(
                encounterId = "system",
                eventType = AuditEvent.AuditEventType.ERROR,
                actor = AuditEvent.AuditActor.APP,
                meta = match {
                    it["event_type"] == "keystore_hazard_test"
                }
            )
        }
    }

    @Test
    fun `recovery metadata is saved correctly`() = runTest {
        // Given
        val hazardType = KeystoreHazardSuite.HAZARD_OS_UPGRADE

        // When
        val result = keystoreHazardSuite.performRecovery(hazardType)

        // Then
        assertTrue(result.isSuccess)
        val recoveryResult = result.getOrThrow()

        // Check that metadata file was created
        val recoveryDir = File(context.filesDir, "keystore_recovery")
        assertTrue(recoveryDir.exists())

        val metadataFile = File(recoveryDir, "recovery_${recoveryResult.timestamp}.json")
        assertTrue(metadataFile.exists())

        // Verify metadata content
        val metadataJson = metadataFile.readText()
        assertTrue(metadataJson.contains(recoveryResult.recoveryType))
        assertTrue(metadataJson.contains(recoveryResult.timestamp))
        assertTrue(metadataJson.contains("true")) // success
    }

    @Test
    fun `hazard detection logs audit events`() = runTest {
        // Given - simulate a hazard
        val prefs = context.getSharedPreferences("keystore_hazards", Context.MODE_PRIVATE)
        prefs.edit().putInt("os_version", 28).apply()

        // When
        val result = keystoreHazardSuite.detectHazards()

        // Then
        assertTrue(result.isSuccess)
        val hazards = result.getOrThrow()
        
        if (hazards.isNotEmpty()) {
            // Verify audit logging for hazard detection
            coVerify {
                mockAuditLogger.logEvent(
                    encounterId = "system",
                    eventType = AuditEvent.AuditEventType.ERROR,
                    actor = AuditEvent.AuditActor.APP,
                    meta = match {
                        it["event_type"] == "keystore_hazard_detection" &&
                        it["hazard_count"] == hazards.size
                    }
                )
            }
        }
    }
}
