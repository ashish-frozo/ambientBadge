package com.frozo.ambientscribe.audio

import android.content.Context
import android.content.SharedPreferences
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AudioProcessingConfigTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Mock
    private lateinit var mockMetricsCollector: MetricsCollector
    
    private lateinit var audioProcessingConfig: AudioProcessingConfig

    @Before
    fun setUp() {
        // Mock SharedPreferences
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(anyString(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putString(anyString(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        
        // Default settings
        whenever(mockSharedPreferences.getBoolean(eq("ns_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("aec_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("agc_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getString(eq("ab_test_group"), any())).thenReturn("A")
        whenever(mockSharedPreferences.getString(eq("device_id"), any())).thenReturn("test-device-id")
        whenever(mockSharedPreferences.getString(eq("clinic_id"), any())).thenReturn("test-clinic-id")
        
        // Create AudioProcessingConfig with mocked dependencies
        audioProcessingConfig = AudioProcessingConfig(mockContext, mockMetricsCollector)
    }

    @Test
    fun testInitialSettings() = runTest {
        // Check initial settings
        assertTrue(audioProcessingConfig.nsEnabled.first())
        assertTrue(audioProcessingConfig.aecEnabled.first())
        assertTrue(audioProcessingConfig.agcEnabled.first())
    }

    @Test
    fun testNoiseSuppressionSetting() = runTest {
        // Change setting
        audioProcessingConfig.setNoiseSuppressionEnabled(false)
        
        // Verify preference updated
        verify(mockEditor).putBoolean(eq("ns_enabled"), eq(false))
        verify(mockEditor).apply()
        
        // Verify state updated
        assertFalse(audioProcessingConfig.nsEnabled.first())
    }

    @Test
    fun testEchoCancellationSetting() = runTest {
        // Change setting
        audioProcessingConfig.setEchoCancellationEnabled(false)
        
        // Verify preference updated
        verify(mockEditor).putBoolean(eq("aec_enabled"), eq(false))
        verify(mockEditor).apply()
        
        // Verify state updated
        assertFalse(audioProcessingConfig.aecEnabled.first())
    }

    @Test
    fun testAutomaticGainControlSetting() = runTest {
        // Change setting
        audioProcessingConfig.setAutomaticGainControlEnabled(false)
        
        // Verify preference updated
        verify(mockEditor).putBoolean(eq("agc_enabled"), eq(false))
        verify(mockEditor).apply()
        
        // Verify state updated
        assertFalse(audioProcessingConfig.agcEnabled.first())
    }

    @Test
    fun testApplySettings() {
        // Apply settings
        audioProcessingConfig.applySettings()
        
        // Verify settings are applied (no exceptions)
        assertTrue(true)
    }

    @Test
    fun testFeatureChangeTracking() = runTest {
        // Start tracking
        audioProcessingConfig.startFeatureChangeTracking(0.8f)
        
        // Stop tracking with final quality
        audioProcessingConfig.stopFeatureChangeTracking(0.9f)
        
        // Verify metrics logged
        verify(mockMetricsCollector).recordEvent(
            eq("audio_processing_impact"),
            any()
        )
    }

    @Test
    fun testGetCurrentConfig() {
        // Get current config
        val config = audioProcessingConfig.getCurrentConfig()
        
        // Verify config contains expected keys
        assertTrue(config.containsKey("ns_enabled"))
        assertTrue(config.containsKey("aec_enabled"))
        assertTrue(config.containsKey("agc_enabled"))
        assertTrue(config.containsKey("ab_test_group"))
        
        // Verify values
        assertEquals(true, config["ns_enabled"])
        assertEquals(true, config["aec_enabled"])
        assertEquals(true, config["agc_enabled"])
        assertEquals("A", config["ab_test_group"])
    }

    @Test
    fun testCreateAudioConfig() {
        // Get audio config
        val config = audioProcessingConfig.createAudioConfig()
        
        // Verify config contains expected keys
        assertTrue(config.containsKey("noiseSuppression"))
        assertTrue(config.containsKey("echoCancellation"))
        assertTrue(config.containsKey("automaticGainControl"))
        
        // Verify values
        assertEquals(true, config["noiseSuppression"])
        assertEquals(true, config["echoCancellation"])
        assertEquals(true, config["automaticGainControl"])
    }

    @Test
    fun testClinicIdSetting() {
        // Set clinic ID
        audioProcessingConfig.setClinicId("new-clinic-id")
        
        // Verify preference updated
        verify(mockEditor).putString(eq("clinic_id"), eq("new-clinic-id"))
        verify(mockEditor).apply()
        
        // Verify config updated
        val config = audioProcessingConfig.getCurrentConfig()
        assertEquals("new-clinic-id", config["clinic_id"])
    }
}