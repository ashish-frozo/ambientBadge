package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for UploadPolicyManager
 * 
 * Tests upload policy functionality including:
 * - Clinic-level policy configuration
 * - Network condition monitoring
 * - WorkManager constraint generation
 * - Policy compliance validation
 */
class UploadPolicyManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var uploadPolicyManager: UploadPolicyManager
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockPrefs = mockk<SharedPreferences>(relaxed = true)
        mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        mockConnectivityManager = mockk<ConnectivityManager>(relaxed = true)
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putString(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        
        // Reset singleton instance
        UploadPolicyManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        uploadPolicyManager = UploadPolicyManager.getInstance(mockContext)
    }
    
    @Test
    @DisplayName("Test upload policy manager initialization")
    fun testUploadPolicyManagerInitialization() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = UploadPolicyManager.getInstance(mockContext)
        
        // Then
        assertTrue(manager.wifiOnly.value)
        assertFalse(manager.meteredOk.value)
        assertEquals("test_clinic", manager.clinicId.value)
    }
    
    @Test
    @DisplayName("Test upload policy setting")
    fun testUploadPolicySetting() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        uploadPolicyManager.setUploadPolicy("test_clinic", false, true, "2.0.0")
        
        // Then
        assertFalse(uploadPolicyManager.wifiOnly.value)
        assertTrue(uploadPolicyManager.meteredOk.value)
        assertEquals("test_clinic", uploadPolicyManager.clinicId.value)
        assertEquals("2.0.0", uploadPolicyManager.policyVersion.value)
    }
    
    @Test
    @DisplayName("Test upload allowed with WiFi connection")
    fun testUploadAllowedWithWiFiConnection() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock WiFi connection
        val mockNetwork = mockk<android.net.Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns true
        
        // When
        val result = uploadPolicyManager.isUploadAllowed()
        
        // Then
        assertTrue(result is UploadPolicyManager.UploadAllowedResult.ALLOWED)
        assertTrue((result as UploadPolicyManager.UploadAllowedResult.ALLOWED).reason.contains("WiFi"))
    }
    
    @Test
    @DisplayName("Test upload allowed with metered connection when allowed")
    fun testUploadAllowedWithMeteredConnectionWhenAllowed() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Set policy to allow metered connections
        uploadPolicyManager.setUploadPolicy("test_clinic", false, true, "1.0.0")
        
        // Mock metered connection
        val mockNetwork = mockk<android.net.Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns false
        
        // When
        val result = uploadPolicyManager.isUploadAllowed()
        
        // Then
        assertTrue(result is UploadPolicyManager.UploadAllowedResult.ALLOWED)
        assertTrue((result as UploadPolicyManager.UploadAllowedResult.ALLOWED).reason.contains("Metered"))
    }
    
    @Test
    @DisplayName("Test upload blocked with metered connection when not allowed")
    fun testUploadBlockedWithMeteredConnectionWhenNotAllowed() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Set policy to not allow metered connections
        uploadPolicyManager.setUploadPolicy("test_clinic", true, false, "1.0.0")
        
        // Mock metered connection
        val mockNetwork = mockk<android.net.Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns false
        
        // When
        val result = uploadPolicyManager.isUploadAllowed()
        
        // Then
        assertTrue(result is UploadPolicyManager.UploadAllowedResult.BLOCKED)
        assertTrue((result as UploadPolicyManager.UploadAllowedResult.BLOCKED).reason.contains("Metered"))
    }
    
    @Test
    @DisplayName("Test upload blocked with no connection")
    fun testUploadBlockedWithNoConnection() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock no connection
        every { mockConnectivityManager.activeNetwork } returns null
        
        // When
        val result = uploadPolicyManager.isUploadAllowed()
        
        // Then
        assertTrue(result is UploadPolicyManager.UploadAllowedResult.BLOCKED)
        assertTrue((result as UploadPolicyManager.UploadAllowedResult.BLOCKED).reason.contains("No network"))
    }
    
    @Test
    @DisplayName("Test upload blocked with unsupported connection type")
    fun testUploadBlockedWithUnsupportedConnectionType() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock unsupported connection
        val mockNetwork = mockk<android.net.Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns true
        
        // When
        val result = uploadPolicyManager.isUploadAllowed()
        
        // Then
        assertTrue(result is UploadPolicyManager.UploadAllowedResult.BLOCKED)
        assertTrue((result as UploadPolicyManager.UploadAllowedResult.BLOCKED).reason.contains("Unsupported"))
    }
    
    @Test
    @DisplayName("Test WorkManager constraints generation")
    fun testWorkManagerConstraintsGeneration() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val constraints = uploadPolicyManager.getWorkManagerConstraints()
        
        // Then
        assertTrue(constraints.requiresWifi)
        assertFalse(constraints.allowsMetered)
        assertFalse(constraints.requiresCharging)
        assertTrue(constraints.requiresBatteryNotLow)
        assertFalse(constraints.requiresDeviceIdle)
    }
    
    @Test
    @DisplayName("Test WorkManager constraints with metered allowed")
    fun testWorkManagerConstraintsWithMeteredAllowed() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns false
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Set policy to allow metered connections
        uploadPolicyManager.setUploadPolicy("test_clinic", false, true, "1.0.0")
        
        // When
        val constraints = uploadPolicyManager.getWorkManagerConstraints()
        
        // Then
        assertFalse(constraints.requiresWifi)
        assertTrue(constraints.allowsMetered)
        assertFalse(constraints.requiresCharging)
        assertTrue(constraints.requiresBatteryNotLow)
        assertFalse(constraints.requiresDeviceIdle)
    }
    
    @Test
    @DisplayName("Test upload policy status retrieval")
    fun testUploadPolicyStatusRetrieval() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock network connection
        val mockNetwork = mockk<android.net.Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns true
        
        // When
        val status = uploadPolicyManager.getUploadPolicyStatus()
        
        // Then
        assertTrue(status.containsKey("clinicId"))
        assertTrue(status.containsKey("wifiOnly"))
        assertTrue(status.containsKey("meteredOk"))
        assertTrue(status.containsKey("policyVersion"))
        assertTrue(status.containsKey("networkInfo"))
        assertTrue(status.containsKey("uploadAllowed"))
        assertTrue(status.containsKey("workManagerConstraints"))
        assertTrue(status.containsKey("lastUpdate"))
    }
    
    @Test
    @DisplayName("Test policy compliance validation")
    fun testPolicyComplianceValidation() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock network connection
        val mockNetwork = mockk<android.net.Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns true
        
        // When
        val compliance = uploadPolicyManager.validatePolicyCompliance()
        
        // Then
        assertTrue(compliance.isCompliant)
        assertTrue(compliance.issues.isEmpty())
    }
    
    @Test
    @DisplayName("Test policy compliance validation with missing clinic ID")
    fun testPolicyComplianceValidationWithMissingClinicId() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val compliance = uploadPolicyManager.validatePolicyCompliance()
        
        // Then
        assertFalse(compliance.isCompliant)
        assertTrue(compliance.issues.contains("No clinic ID set"))
    }
    
    @Test
    @DisplayName("Test policy compliance validation with conflicting settings")
    fun testPolicyComplianceValidationWithConflictingSettings() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Set conflicting settings
        uploadPolicyManager.setUploadPolicy("test_clinic", true, true, "1.0.0")
        
        // When
        val compliance = uploadPolicyManager.validatePolicyCompliance()
        
        // Then
        assertFalse(compliance.isCompliant)
        assertTrue(compliance.issues.contains("Conflicting settings"))
    }
    
    @Test
    @DisplayName("Test policy compliance validation with no network")
    fun testPolicyComplianceValidationWithNoNetwork() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock no network connection
        every { mockConnectivityManager.activeNetwork } returns null
        
        // When
        val compliance = uploadPolicyManager.validatePolicyCompliance()
        
        // Then
        assertFalse(compliance.isCompliant)
        assertTrue(compliance.issues.contains("No network connection available"))
    }
    
    @Test
    @DisplayName("Test upload queue status retrieval")
    fun testUploadQueueStatusRetrieval() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val queueStatus = uploadPolicyManager.getUploadQueueStatus()
        
        // Then
        assertTrue(queueStatus.containsKey("queueSize"))
        assertTrue(queueStatus.containsKey("pendingUploads"))
        assertTrue(queueStatus.containsKey("failedUploads"))
        assertTrue(queueStatus.containsKey("lastUploadTime"))
        assertTrue(queueStatus.containsKey("isProcessing"))
    }
    
    @Test
    @DisplayName("Test error handling in upload allowance checking")
    fun testErrorHandlingInUploadAllowanceChecking() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock connectivity manager to throw exception
        every { mockConnectivityManager.activeNetwork } throws RuntimeException("Test error")
        
        // When
        val result = uploadPolicyManager.isUploadAllowed()
        
        // Then
        assertTrue(result is UploadPolicyManager.UploadAllowedResult.BLOCKED)
        assertTrue((result as UploadPolicyManager.UploadAllowedResult.BLOCKED).reason.contains("Error"))
    }
    
    @Test
    @DisplayName("Test error handling in policy compliance validation")
    fun testErrorHandlingInPolicyComplianceValidation() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock connectivity manager to throw exception
        every { mockConnectivityManager.activeNetwork } throws RuntimeException("Test error")
        
        // When
        val compliance = uploadPolicyManager.validatePolicyCompliance()
        
        // Then
        assertFalse(compliance.isCompliant)
        assertTrue(compliance.issues.any { it.contains("Error") })
    }
    
    @Test
    @DisplayName("Test concurrent upload policy operations")
    fun testConcurrentUploadPolicyOperations() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Mock network connection
        val mockNetwork = mockk<android.net.Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns true
        
        // When
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(50) {
                    when (threadId % 4) {
                        0 -> uploadPolicyManager.setUploadPolicy("clinic_$threadId", threadId % 2 == 0, threadId % 2 == 1, "1.0.0")
                        1 -> uploadPolicyManager.isUploadAllowed()
                        2 -> uploadPolicyManager.getWorkManagerConstraints()
                        3 -> uploadPolicyManager.validatePolicyCompliance()
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertNotNull(uploadPolicyManager.getUploadPolicyStatus())
    }
    
    @Test
    @DisplayName("Test upload allowed result types")
    fun testUploadAllowedResultTypes() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Test different result types
        val results = listOf(
            UploadPolicyManager.UploadAllowedResult.ALLOWED("Test reason"),
            UploadPolicyManager.UploadAllowedResult.BLOCKED("Test reason")
        )
        
        // When & Then
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result is UploadPolicyManager.UploadAllowedResult)
        }
    }
    
    @Test
    @DisplayName("Test network info data class")
    fun testNetworkInfoDataClass() {
        // Given
        val networkInfo = UploadPolicyManager.NetworkInfo(
            isConnected = true,
            isWifi = true,
            isMetered = false,
            connectionType = "wifi"
        )
        
        // When & Then
        assertTrue(networkInfo.isConnected)
        assertTrue(networkInfo.isWifi)
        assertFalse(networkInfo.isMetered)
        assertEquals("wifi", networkInfo.connectionType)
    }
    
    @Test
    @DisplayName("Test WorkManager constraints data class")
    fun testWorkManagerConstraintsDataClass() {
        // Given
        val constraints = UploadPolicyManager.WorkManagerConstraints(
            requiresWifi = true,
            allowsMetered = false,
            requiresCharging = false,
            requiresBatteryNotLow = true,
            requiresDeviceIdle = false
        )
        
        // When & Then
        assertTrue(constraints.requiresWifi)
        assertFalse(constraints.allowsMetered)
        assertFalse(constraints.requiresCharging)
        assertTrue(constraints.requiresBatteryNotLow)
        assertFalse(constraints.requiresDeviceIdle)
    }
    
    @Test
    @DisplayName("Test policy compliance result data class")
    fun testPolicyComplianceResultDataClass() {
        // Given
        val issues = listOf("Test issue 1", "Test issue 2")
        
        // When
        val compliantResult = UploadPolicyManager.PolicyComplianceResult(true, emptyList())
        val nonCompliantResult = UploadPolicyManager.PolicyComplianceResult(false, issues)
        
        // Then
        assertTrue(compliantResult.isCompliant)
        assertTrue(compliantResult.issues.isEmpty())
        assertFalse(nonCompliantResult.isCompliant)
        assertEquals(issues, nonCompliantResult.issues)
    }
    
    @Test
    @DisplayName("Test upload queue status data class")
    fun testUploadQueueStatusDataClass() {
        // Given
        val queueStatus = UploadPolicyManager.UploadQueueStatus(
            queueSize = 10,
            pendingUploads = 5,
            failedUploads = 2,
            lastUploadTime = 1234567890L,
            isProcessing = true
        )
        
        // When & Then
        assertEquals(10, queueStatus.queueSize)
        assertEquals(5, queueStatus.pendingUploads)
        assertEquals(2, queueStatus.failedUploads)
        assertEquals(1234567890L, queueStatus.lastUploadTime)
        assertTrue(queueStatus.isProcessing)
    }
    
    @Test
    @DisplayName("Test upload policy status with different network types")
    fun testUploadPolicyStatusWithDifferentNetworkTypes() {
        // Given
        every { mockPrefs.getBoolean(any(), any()) } returns true
        every { mockPrefs.getString(any(), any()) } returns "test_clinic"
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val networkTypes = listOf(
            Pair(NetworkCapabilities.TRANSPORT_WIFI, "wifi"),
            Pair(NetworkCapabilities.TRANSPORT_CELLULAR, "cellular"),
            Pair(NetworkCapabilities.TRANSPORT_ETHERNET, "ethernet")
        )
        
        // When & Then
        networkTypes.forEach { (transport, expectedType) ->
            val mockNetwork = mockk<android.net.Network>()
            val mockCapabilities = mockk<NetworkCapabilities>()
            every { mockConnectivityManager.activeNetwork } returns mockNetwork
            every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
            every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns (transport == NetworkCapabilities.TRANSPORT_WIFI)
            every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns (transport == NetworkCapabilities.TRANSPORT_CELLULAR)
            every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns (transport == NetworkCapabilities.TRANSPORT_ETHERNET)
            every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns true
            
            val status = uploadPolicyManager.getUploadPolicyStatus()
            assertNotNull(status)
            assertTrue(status.containsKey("networkInfo"))
        }
    }
}
