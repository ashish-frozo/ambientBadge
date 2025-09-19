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
import org.mockito.kotlin.whenever
import org.webrtc.audio.WebRtcAudioUtils
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
        
        // Mock WebRTC audio utils
        val webRtcMockedStatic = mockStatic(WebRtcAudioUtils::class.java)
        webRtcMockedStatic.`when`<Unit> { 
            WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(any()) 
        }.then { }
        webRtcMockedStatic.`when`<Unit> { 
            WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(any()) 
        }.then { }
        webRtcMockedStatic.`when`<Unit> { 
            WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(any()) 
        }.then { }
        
        // Default settings
        whenever(mockSharedPreferences.getBoolean(eq("ns_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("aec_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("agc_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getString(eq("ab_test_group"), any())).thenReturn("A")
        
        // Create AudioProcessingConfig with mocked dependencies
        audioProcessingConfig = AudioProcessingConfig(mockContext, mockMetricsCollector)
    }

    @Test
    fun `initial settings should be loaded from preferences`() = runTest {
        // Check initial settings
        assertTrue(audioProcessingConfig.nsEnabled.first())
        assertTrue(audioProcessingConfig.aecEnabled.first())
        assertTrue(audioProcessingConfig.agcEnabled.first())
    }

    @Test
    fun `setNoiseSuppressionEnabled should update preferences and apply settings`() = runTest {
        // Change setting
        audioProcessingConfig.setNoiseSuppressionEnabled(false)
        
        // Verify preference updated
        verify(mockEditor).putBoolean(eq("ns_enabled"), eq(false))
        verify(mockEditor).apply()
        
        // Verify setting applied to WebRTC
        verify(WebRtcAudioUtils::class.java, times(1)).setWebRtcBasedNoiseSuppressor(false)
        
        // Verify state updated
        assertFalse(audioProcessingConfig.nsEnabled.first())
    }

    @Test
    fun `setEchoCancellationEnabled should update preferences and apply settings`() = runTest {
        // Change setting
        audioProcessingConfig.setEchoCancellationEnabled(false)
        
        // Verify preference updated
        verify(mockEditor).putBoolean(eq("aec_enabled"), eq(false))
        verify(mockEditor).apply()
        
        // Verify setting applied to WebRTC
        verify(WebRtcAudioUtils::class.java, times(1)).setWebRtcBasedAcousticEchoCanceler(false)
        
        // Verify state updated
        assertFalse(audioProcessingConfig.aecEnabled.first())
    }

    @Test
    fun `setAutomaticGainControlEnabled should update preferences and apply settings`() = runTest {
        // Change setting
        audioProcessingConfig.setAutomaticGainControlEnabled(false)
        
        // Verify preference updated
        verify(mockEditor).putBoolean(eq("agc_enabled"), eq(false))
        verify(mockEditor).apply()
        
        // Verify setting applied to WebRTC
        verify(WebRtcAudioUtils::class.java, times(1)).setWebRtcBasedAutomaticGainControl(false)
        
        // Verify state updated
        assertFalse(audioProcessingConfig.agcEnabled.first())
    }

    @Test
    fun `applySettings should apply all settings to WebRTC`() {
        // Apply settings
        audioProcessingConfig.applySettings()
        
        // Verify all settings applied
        verify(WebRtcAudioUtils::class.java, times(1)).setWebRtcBasedNoiseSuppressor(true)
        verify(WebRtcAudioUtils::class.java, times(1)).setWebRtcBasedAcousticEchoCanceler(true)
        verify(WebRtcAudioUtils::class.java, times(1)).setWebRtcBasedAutomaticGainControl(true)
    }

    @Test
    fun `feature change tracking should log impact`() = runTest {
        // Start tracking
        audioProcessingConfig.startFeatureChangeTracking(0.8f)
        
        // Stop tracking with final quality
        audioProcessingConfig.stopFeatureChangeTracking(0.9f)
        
        // Verify metrics logged
        verify(mockMetricsCollector).logMetricEvent(
            eq("audio_processing_impact"),
            any()
        )
    }

    @Test
    fun `getCurrentConfig should return current settings`() {
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
}
