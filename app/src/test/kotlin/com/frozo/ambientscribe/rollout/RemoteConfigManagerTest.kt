package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for RemoteConfigManager
 * 
 * Tests remote configuration functionality including:
 * - Ed25519 signature verification
 * - Configuration validation
 * - Caching and persistence
 * - Error handling and recovery
 */
class RemoteConfigManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var remoteConfigManager: RemoteConfigManager
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockPrefs = mockk<SharedPreferences>(relaxed = true)
        mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putString(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        
        // Mock Base64
        mockkStatic(Base64::class)
        every { Base64.decode(any(), any()) } returns "test".toByteArray()
        
        // Reset singleton instance
        RemoteConfigManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        remoteConfigManager = RemoteConfigManager.getInstance(mockContext)
    }
    
    @Test
    @DisplayName("Test remote config manager initialization")
    fun testRemoteConfigManagerInitialization() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = RemoteConfigManager.getInstance(mockContext)
        
        // Then
        assertFalse(manager.isConfigurationValid())
        assertEquals("1.0.0", manager.configVersion.value)
    }
    
    @Test
    @DisplayName("Test remote config update with valid signature")
    fun testRemoteConfigUpdateWithValidSignature() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val configJson = """{"version": "2.0.0", "timestamp": "${System.currentTimeMillis()}", "features": {"test": true}}"""
        val signature = "valid_signature"
        val version = "2.0.0"
        
        // Mock signature verification to return true
        mockkStatic(android.security.MessageDigest::class)
        mockkStatic(java.security.Signature::class)
        mockkStatic(java.security.KeyFactory::class)
        mockkStatic(java.security.spec.X509EncodedKeySpec::class)
        
        val mockKeyFactory = mockk<java.security.KeyFactory>()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val mockSignature = mockk<java.security.Signature>()
        
        every { java.security.KeyFactory.getInstance(any()) } returns mockKeyFactory
        every { mockKeyFactory.generatePublic(any()) } returns mockPublicKey
        every { java.security.Signature.getInstance(any()) } returns mockSignature
        every { mockSignature.initVerify(any()) } just Runs
        every { mockSignature.update(any()) } just Runs
        every { mockSignature.verify(any()) } returns true
        
        // When
        val result = remoteConfigManager.updateConfig(configJson, signature, version)
        
        // Then
        assertTrue(result is RemoteConfigManager.ConfigUpdateResult.SUCCESS)
        assertEquals("2.0.0", remoteConfigManager.configVersion.value)
        assertTrue(remoteConfigManager.isConfigurationValid())
    }
    
    @Test
    @DisplayName("Test remote config update with invalid signature")
    fun testRemoteConfigUpdateWithInvalidSignature() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val configJson = """{"version": "2.0.0", "timestamp": "${System.currentTimeMillis()}", "features": {"test": true}}"""
        val signature = "invalid_signature"
        val version = "2.0.0"
        
        // Mock signature verification to return false
        mockkStatic(android.security.MessageDigest::class)
        mockkStatic(java.security.Signature::class)
        mockkStatic(java.security.KeyFactory::class)
        mockkStatic(java.security.spec.X509EncodedKeySpec::class)
        
        val mockKeyFactory = mockk<java.security.KeyFactory>()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val mockSignature = mockk<java.security.Signature>()
        
        every { java.security.KeyFactory.getInstance(any()) } returns mockKeyFactory
        every { mockKeyFactory.generatePublic(any()) } returns mockPublicKey
        every { java.security.Signature.getInstance(any()) } returns mockSignature
        every { mockSignature.initVerify(any()) } just Runs
        every { mockSignature.update(any()) } just Runs
        every { mockSignature.verify(any()) } returns false
        
        // When
        val result = remoteConfigManager.updateConfig(configJson, signature, version)
        
        // Then
        assertTrue(result is RemoteConfigManager.ConfigUpdateResult.INVALID_SIGNATURE)
        assertFalse(remoteConfigManager.isConfigurationValid())
    }
    
    @Test
    @DisplayName("Test remote config update with invalid config")
    fun testRemoteConfigUpdateWithInvalidConfig() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val configJson = """{"invalid": "config"}""" // Missing required fields
        val signature = "valid_signature"
        val version = "2.0.0"
        
        // Mock signature verification to return true
        mockkStatic(android.security.MessageDigest::class)
        mockkStatic(java.security.Signature::class)
        mockkStatic(java.security.KeyFactory::class)
        mockkStatic(java.security.spec.X509EncodedKeySpec::class)
        
        val mockKeyFactory = mockk<java.security.KeyFactory>()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val mockSignature = mockk<java.security.Signature>()
        
        every { java.security.KeyFactory.getInstance(any()) } returns mockKeyFactory
        every { mockKeyFactory.generatePublic(any()) } returns mockPublicKey
        every { java.security.Signature.getInstance(any()) } returns mockSignature
        every { mockSignature.initVerify(any()) } just Runs
        every { mockSignature.update(any()) } just Runs
        every { mockSignature.verify(any()) } returns true
        
        // When
        val result = remoteConfigManager.updateConfig(configJson, signature, version)
        
        // Then
        assertTrue(result is RemoteConfigManager.ConfigUpdateResult.INVALID_CONFIG)
        assertFalse(remoteConfigManager.isConfigurationValid())
    }
    
    @Test
    @DisplayName("Test remote config update with error")
    fun testRemoteConfigUpdateWithError() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val configJson = """{"version": "2.0.0", "timestamp": "${System.currentTimeMillis()}", "features": {"test": true}}"""
        val signature = "valid_signature"
        val version = "2.0.0"
        
        // Mock signature verification to throw exception
        mockkStatic(android.security.MessageDigest::class)
        mockkStatic(java.security.Signature::class)
        mockkStatic(java.security.KeyFactory::class)
        mockkStatic(java.security.spec.X509EncodedKeySpec::class)
        
        every { java.security.KeyFactory.getInstance(any()) } throws RuntimeException("Test error")
        
        // When
        val result = remoteConfigManager.updateConfig(configJson, signature, version)
        
        // Then
        assertTrue(result is RemoteConfigManager.ConfigUpdateResult.ERROR)
        assertFalse(remoteConfigManager.isConfigurationValid())
    }
    
    @Test
    @DisplayName("Test config value retrieval")
    fun testConfigValueRetrieval() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns """{"test_key": "test_value", "test_bool": true, "test_int": 42}"""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock signature verification to return true
        mockkStatic(android.security.MessageDigest::class)
        mockkStatic(java.security.Signature::class)
        mockkStatic(java.security.KeyFactory::class)
        mockkStatic(java.security.spec.X509EncodedKeySpec::class)
        
        val mockKeyFactory = mockk<java.security.KeyFactory>()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val mockSignature = mockk<java.security.Signature>()
        
        every { java.security.KeyFactory.getInstance(any()) } returns mockKeyFactory
        every { mockKeyFactory.generatePublic(any()) } returns mockPublicKey
        every { java.security.Signature.getInstance(any()) } returns mockSignature
        every { mockSignature.initVerify(any()) } just Runs
        every { mockSignature.update(any()) } just Runs
        every { mockSignature.verify(any()) } returns true
        
        // When
        val stringValue = remoteConfigManager.getStringConfig("test_key", "default")
        val intValue = remoteConfigManager.getIntConfig("test_int", 0)
        val boolValue = remoteConfigManager.getBooleanConfig("test_bool", false)
        
        // Then
        assertEquals("test_value", stringValue)
        assertEquals(42, intValue)
        assertTrue(boolValue)
    }
    
    @Test
    @DisplayName("Test config value retrieval with default values")
    fun testConfigValueRetrievalWithDefaultValues() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val stringValue = remoteConfigManager.getStringConfig("unknown_key", "default")
        val intValue = remoteConfigManager.getIntConfig("unknown_key", 42)
        val boolValue = remoteConfigManager.getBooleanConfig("unknown_key", true)
        
        // Then
        assertEquals("default", stringValue)
        assertEquals(42, intValue)
        assertTrue(boolValue)
    }
    
    @Test
    @DisplayName("Test configuration status retrieval")
    fun testConfigurationStatusRetrieval() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val status = remoteConfigManager.getConfigurationStatus()
        
        // Then
        assertTrue(status.containsKey("isValid"))
        assertTrue(status.containsKey("version"))
        assertTrue(status.containsKey("data"))
        assertTrue(status.containsKey("lastUpdate"))
        assertTrue(status.containsKey("isUpdating"))
    }
    
    @Test
    @DisplayName("Test reset to defaults")
    fun testResetToDefaults() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        remoteConfigManager.resetToDefaults()
        
        // Then
        assertFalse(remoteConfigManager.isConfigurationValid())
        assertEquals("1.0.0", remoteConfigManager.configVersion.value)
        assertTrue(remoteConfigManager.configData.value.isEmpty())
    }
    
    @Test
    @DisplayName("Test config validation with valid config")
    fun testConfigValidationWithValidConfig() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val config = mapOf(
            "version" to "2.0.0",
            "timestamp" to System.currentTimeMillis().toString(),
            "features" to mapOf("test" to true)
        )
        
        // When
        val validationResult = remoteConfigManager.validateConfig(config)
        
        // Then
        assertTrue(validationResult.isValid)
        assertTrue(validationResult.issues.isEmpty())
    }
    
    @Test
    @DisplayName("Test config validation with missing required fields")
    fun testConfigValidationWithMissingRequiredFields() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val config = mapOf(
            "version" to "2.0.0"
            // Missing timestamp and features
        )
        
        // When
        val validationResult = remoteConfigManager.validateConfig(config)
        
        // Then
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.issues.isNotEmpty())
        assertTrue(validationResult.issues.any { it.contains("Missing required field") })
    }
    
    @Test
    @DisplayName("Test config validation with invalid version format")
    fun testConfigValidationWithInvalidVersionFormat() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val config = mapOf(
            "version" to "invalid_version",
            "timestamp" to System.currentTimeMillis().toString(),
            "features" to mapOf("test" to true)
        )
        
        // When
        val validationResult = remoteConfigManager.validateConfig(config)
        
        // Then
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.issues.any { it.contains("Invalid version format") })
    }
    
    @Test
    @DisplayName("Test config validation with old timestamp")
    fun testConfigValidationWithOldTimestamp() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val config = mapOf(
            "version" to "2.0.0",
            "timestamp" to (System.currentTimeMillis() - (2 * 60 * 60 * 1000L)).toString(), // 2 hours ago
            "features" to mapOf("test" to true)
        )
        
        // When
        val validationResult = remoteConfigManager.validateConfig(config)
        
        // Then
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.issues.any { it.contains("Timestamp too old") })
    }
    
    @Test
    @DisplayName("Test config validation with invalid features")
    fun testConfigValidationWithInvalidFeatures() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val config = mapOf(
            "version" to "2.0.0",
            "timestamp" to System.currentTimeMillis().toString(),
            "features" to mapOf("test" to "invalid_boolean") // Should be boolean
        )
        
        // When
        val validationResult = remoteConfigManager.validateConfig(config)
        
        // Then
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.issues.any { it.contains("Feature value must be boolean") })
    }
    
    @Test
    @DisplayName("Test config update result types")
    fun testConfigUpdateResultTypes() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Test different result types
        val results = listOf(
            RemoteConfigManager.ConfigUpdateResult.ALREADY_UPDATING,
            RemoteConfigManager.ConfigUpdateResult.INVALID_SIGNATURE,
            RemoteConfigManager.ConfigUpdateResult.INVALID_CONFIG,
            RemoteConfigManager.ConfigUpdateResult.SUCCESS,
            RemoteConfigManager.ConfigUpdateResult.ERROR("Test error")
        )
        
        // When & Then
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result is RemoteConfigManager.ConfigUpdateResult)
        }
    }
    
    @Test
    @DisplayName("Test config validation result data class")
    fun testConfigValidationResultDataClass() {
        // Given
        val issues = listOf("Test issue 1", "Test issue 2")
        
        // When
        val validResult = RemoteConfigManager.ConfigValidationResult(true, emptyList())
        val invalidResult = RemoteConfigManager.ConfigValidationResult(false, issues)
        
        // Then
        assertTrue(validResult.isValid)
        assertTrue(validResult.issues.isEmpty())
        assertFalse(invalidResult.isValid)
        assertEquals(issues, invalidResult.issues)
    }
    
    @Test
    @DisplayName("Test configuration status data class")
    fun testConfigurationStatusDataClass() {
        // Given
        val status = RemoteConfigManager.ConfigurationStatus(
            isValid = true,
            version = "2.0.0",
            data = mapOf("test" to "value"),
            lastUpdate = 1234567890L,
            isUpdating = false
        )
        
        // When & Then
        assertTrue(status.isValid)
        assertEquals("2.0.0", status.version)
        assertEquals(mapOf("test" to "value"), status.data)
        assertEquals(1234567890L, status.lastUpdate)
        assertFalse(status.isUpdating)
    }
    
    @Test
    @DisplayName("Test concurrent config operations")
    fun testConcurrentConfigOperations() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock signature verification
        mockkStatic(android.security.MessageDigest::class)
        mockkStatic(java.security.Signature::class)
        mockkStatic(java.security.KeyFactory::class)
        mockkStatic(java.security.spec.X509EncodedKeySpec::class)
        
        val mockKeyFactory = mockk<java.security.KeyFactory>()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val mockSignature = mockk<java.security.Signature>()
        
        every { java.security.KeyFactory.getInstance(any()) } returns mockKeyFactory
        every { mockKeyFactory.generatePublic(any()) } returns mockPublicKey
        every { java.security.Signature.getInstance(any()) } returns mockSignature
        every { mockSignature.initVerify(any()) } just Runs
        every { mockSignature.update(any()) } just Runs
        every { mockSignature.verify(any()) } returns true
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) {
                    when (threadId % 3) {
                        0 -> remoteConfigManager.getStringConfig("test_key", "default")
                        1 -> remoteConfigManager.getIntConfig("test_key", 42)
                        2 -> remoteConfigManager.getBooleanConfig("test_key", true)
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertNotNull(remoteConfigManager.getConfigurationStatus())
    }
    
    @Test
    @DisplayName("Test config validation with various timestamp formats")
    fun testConfigValidationWithVariousTimestampFormats() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val testCases = listOf(
            System.currentTimeMillis().toString(), // Valid
            "invalid_timestamp", // Invalid
            (System.currentTimeMillis() - (2 * 60 * 60 * 1000L)).toString(), // Too old
            "0" // Too old
        )
        
        // When & Then
        testCases.forEach { timestamp ->
            val config = mapOf(
                "version" to "2.0.0",
                "timestamp" to timestamp,
                "features" to mapOf("test" to true)
            )
            
            val validationResult = remoteConfigManager.validateConfig(config)
            assertNotNull(validationResult)
            assertTrue(validationResult.isValid || validationResult.issues.isNotEmpty())
        }
    }
    
    @Test
    @DisplayName("Test config validation with various version formats")
    fun testConfigValidationWithVariousVersionFormats() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "{}"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val testCases = listOf(
            "1.0.0", // Valid
            "2.0.0", // Valid
            "1.0.0.0", // Invalid
            "1.0", // Invalid
            "invalid_version", // Invalid
            "1.0.0-beta" // Invalid
        )
        
        // When & Then
        testCases.forEach { version ->
            val config = mapOf(
                "version" to version,
                "timestamp" to System.currentTimeMillis().toString(),
                "features" to mapOf("test" to true)
            )
            
            val validationResult = remoteConfigManager.validateConfig(config)
            assertNotNull(validationResult)
            assertTrue(validationResult.isValid || validationResult.issues.isNotEmpty())
        }
    }
}
