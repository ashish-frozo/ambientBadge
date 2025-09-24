package com.frozo.ambientscribe.performance

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for DeviceTierDetector - PT-6.1
 */
@RunWith(RobolectricTestRunner::class)
class DeviceTierDetectorTest {

    private lateinit var context: Context
    private lateinit var deviceTierDetector: DeviceTierDetector
    private lateinit var mockConfiguration: Configuration

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        deviceTierDetector = DeviceTierDetector(context)
        mockConfiguration = mockk<Configuration>()
        
        every { context.resources.configuration } returns mockConfiguration
        every { context.resources.displayMetrics } returns mockk()
    }

    @Test
    fun `test initialization`() = runTest {
        // When
        val result = deviceTierDetector.initialize()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test detect device tier - Tier A`() = runTest {
        // Given
        mockkStatic(Build::class)
        every { Build.MODEL } returns "Pixel 6a"
        every { Build.VERSION.SDK_INT } returns 33
        every { mockConfiguration.screenLayout } returns Configuration.SCREENLAYOUT_SIZE_LARGE
        
        // When
        val tier = deviceTierDetector.detectDeviceTier()
        
        // Then
        assertEquals(DeviceTier.TIER_A, tier)
    }

    @Test
    fun `test detect device tier - Tier B`() = runTest {
        // Given
        mockkStatic(Build::class)
        every { Build.MODEL } returns "Redmi 10"
        every { Build.VERSION.SDK_INT } returns 30
        every { mockConfiguration.screenLayout } returns Configuration.SCREENLAYOUT_SIZE_NORMAL
        
        // When
        val tier = deviceTierDetector.detectDeviceTier()
        
        // Then
        assertEquals(DeviceTier.TIER_B, tier)
    }

    @Test
    fun `test detect device tier - Unsupported`() = runTest {
        // Given
        mockkStatic(Build::class)
        every { Build.MODEL } returns "Old Device"
        every { Build.VERSION.SDK_INT } returns 25
        every { mockConfiguration.screenLayout } returns Configuration.SCREENLAYOUT_SIZE_SMALL
        
        // When
        val tier = deviceTierDetector.detectDeviceTier()
        
        // Then
        assertEquals(DeviceTier.UNSUPPORTED, tier)
    }

    @Test
    fun `test get performance settings for Tier A`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        
        // When
        val settings = deviceTierDetector.getPerformanceSettings(tier)
        
        // Then
        assertNotNull(settings)
        assertEquals(8.0, settings.maxModelLoadTime)
        assertEquals(0.8, settings.maxFirstTokenLatency)
        assertEquals(8.0, settings.maxDraftReadyLatency)
        assertEquals(6.0, settings.maxBatteryConsumption)
        assertEquals(4, settings.maxThreads)
        assertTrue(settings.enableAdvancedFeatures)
    }

    @Test
    fun `test get performance settings for Tier B`() = runTest {
        // Given
        val tier = DeviceTier.TIER_B
        
        // When
        val settings = deviceTierDetector.getPerformanceSettings(tier)
        
        // Then
        assertNotNull(settings)
        assertEquals(12.0, settings.maxModelLoadTime)
        assertEquals(1.2, settings.maxFirstTokenLatency)
        assertEquals(12.0, settings.maxDraftReadyLatency)
        assertEquals(8.0, settings.maxBatteryConsumption)
        assertEquals(2, settings.maxThreads)
        assertFalse(settings.enableAdvancedFeatures)
    }

    @Test
    fun `test get device capabilities`() = runTest {
        // When
        val capabilities = deviceTierDetector.getDeviceCapabilities()
        
        // Then
        assertNotNull(capabilities)
        assertTrue(capabilities.ramGB > 0)
        assertTrue(capabilities.cpuCores > 0)
        assertTrue(capabilities.storageGB > 0)
        assertTrue(capabilities.apiLevel > 0)
    }

    @Test
    fun `test is device supported`() = runTest {
        // Given
        val capabilities = DeviceCapabilities(
            ramGB = 4.0,
            cpuCores = 6,
            storageGB = 64.0,
            gpuScore = 80.0,
            apiLevel = 30,
            hasMicrophone = true,
            hasAudioOutput = true,
            hasWifi = true,
            hasBluetooth = true
        )
        
        // When
        val isSupported = deviceTierDetector.isDeviceSupported(capabilities)
        
        // Then
        assertTrue(isSupported)
    }

    @Test
    fun `test is device not supported - low RAM`() = runTest {
        // Given
        val capabilities = DeviceCapabilities(
            ramGB = 1.0, // Too low
            cpuCores = 4,
            storageGB = 32.0,
            gpuScore = 60.0,
            apiLevel = 30,
            hasMicrophone = true,
            hasAudioOutput = true,
            hasWifi = true,
            hasBluetooth = true
        )
        
        // When
        val isSupported = deviceTierDetector.isDeviceSupported(capabilities)
        
        // Then
        assertFalse(isSupported)
    }

    @Test
    fun `test is device not supported - low API level`() = runTest {
        // Given
        val capabilities = DeviceCapabilities(
            ramGB = 4.0,
            cpuCores = 4,
            storageGB = 32.0,
            gpuScore = 60.0,
            apiLevel = 25, // Too low
            hasMicrophone = true,
            hasAudioOutput = true,
            hasWifi = true,
            hasBluetooth = true
        )
        
        // When
        val isSupported = deviceTierDetector.isDeviceSupported(capabilities)
        
        // Then
        assertFalse(isSupported)
    }

    @Test
    fun `test get tier specific recommendations`() = runTest {
        // Given
        val tier = DeviceTier.TIER_A
        
        // When
        val recommendations = deviceTierDetector.getTierSpecificRecommendations(tier)
        
        // Then
        assertNotNull(recommendations)
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.any { it.contains("Tier A") })
    }

    @Test
    fun `test validate device compatibility`() = runTest {
        // When
        val validation = deviceTierDetector.validateDeviceCompatibility()
        
        // Then
        assertNotNull(validation)
        assertTrue(validation.isCompatible)
        assertNotNull(validation.detectedTier)
        assertTrue(validation.capabilities.ramGB > 0)
    }

    @Test
    fun `test get device information`() = runTest {
        // When
        val deviceInfo = deviceTierDetector.getDeviceInformation()
        
        // Then
        assertNotNull(deviceInfo)
        assertTrue(deviceInfo.model.isNotEmpty())
        assertTrue(deviceInfo.manufacturer.isNotEmpty())
        assertTrue(deviceInfo.apiLevel > 0)
        assertNotNull(deviceInfo.tier)
    }
}