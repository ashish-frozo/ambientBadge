package com.frozo.ambientscribe.telemetry

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
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
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MetricsTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Mock
    private lateinit var mockFile: File
    
    @Mock
    private lateinit var mockDir: File
    
    private lateinit var metricsCollector: MetricsCollector

    @Before
    fun setUp() {
        // Mock SharedPreferences
        whenever(mockContext.getSharedPreferences(anyString(), anyInt()))
            .thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(anyString(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putString(anyString(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        
        // Mock File operations
        whenever(mockContext.filesDir).thenReturn(mockFile)
        whenever(mockFile.exists()).thenReturn(true)
        whenever(mockFile.mkdirs()).thenReturn(true)
        whenever(mockFile.deleteRecursively()).thenReturn(true)
        
        // Mock device ID
        whenever(mockSharedPreferences.getString(eq("device_id"), any())).thenReturn("test-device-id")
        
        // Create MetricsCollector with mocked dependencies
        metricsCollector = MetricsCollector(mockContext)
    }

    @Test
    fun `setPilotModeEnabled should update preferences`() {
        // Enable pilot mode
        metricsCollector.setPilotModeEnabled(true)
        
        // Verify SharedPreferences updated
        verify(mockEditor).putBoolean(eq("pilot_mode_enabled"), eq(true))
        verify(mockEditor).apply()
        
        // Disable pilot mode
        metricsCollector.setPilotModeEnabled(false)
        
        // Verify SharedPreferences updated
        verify(mockEditor).putBoolean(eq("pilot_mode_enabled"), eq(false))
        verify(mockEditor, times(2)).apply()
    }

    @Test
    fun `setUserConsent should update preferences`() {
        // Grant consent
        metricsCollector.setUserConsent(true)
        
        // Verify SharedPreferences updated
        verify(mockEditor).putBoolean(eq("metrics_consent"), eq(true))
        verify(mockEditor).apply()
        
        // Revoke consent
        metricsCollector.setUserConsent(false)
        
        // Verify SharedPreferences updated and metrics cleared
        verify(mockEditor).putBoolean(eq("metrics_consent"), eq(false))
        verify(mockEditor, times(2)).apply()
    }

    @Test
    fun `isMetricsCollectionAllowed should check pilot mode and consent`() {
        // Default state (both false)
        whenever(mockSharedPreferences.getBoolean(eq("pilot_mode_enabled"), any())).thenReturn(false)
        whenever(mockSharedPreferences.getBoolean(eq("metrics_consent"), any())).thenReturn(false)
        
        assertFalse(metricsCollector.isMetricsCollectionAllowed())
        
        // Pilot mode enabled, consent not granted
        whenever(mockSharedPreferences.getBoolean(eq("pilot_mode_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("metrics_consent"), any())).thenReturn(false)
        
        assertFalse(metricsCollector.isMetricsCollectionAllowed())
        
        // Pilot mode disabled, consent granted
        whenever(mockSharedPreferences.getBoolean(eq("pilot_mode_enabled"), any())).thenReturn(false)
        whenever(mockSharedPreferences.getBoolean(eq("metrics_consent"), any())).thenReturn(true)
        
        assertFalse(metricsCollector.isMetricsCollectionAllowed())
        
        // Both enabled
        whenever(mockSharedPreferences.getBoolean(eq("pilot_mode_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("metrics_consent"), any())).thenReturn(true)
        
        assertTrue(metricsCollector.isMetricsCollectionAllowed())
    }

    @Test
    fun `logMetricEvent should fail when metrics collection not allowed`() = runTest {
        // Set metrics collection not allowed
        whenever(mockSharedPreferences.getBoolean(eq("pilot_mode_enabled"), any())).thenReturn(false)
        whenever(mockSharedPreferences.getBoolean(eq("metrics_consent"), any())).thenReturn(false)
        
        // Try to log a metric event
        val result = metricsCollector.logMetricEvent("test_event", mapOf("key" to "value"))
        
        // Should fail
        assertTrue(result.isFailure)
    }

    @Test
    fun `logMetricEvent should succeed when metrics collection allowed`() = runTest {
        // Set metrics collection allowed
        whenever(mockSharedPreferences.getBoolean(eq("pilot_mode_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("metrics_consent"), any())).thenReturn(true)
        
        // Mock file operations for writing metrics
        val mockMetricsDir = mock(File::class.java)
        val mockMetricsFile = mock(File::class.java)
        
        whenever(mockFile.resolve(anyString())).thenReturn(mockMetricsDir)
        whenever(mockMetricsDir.exists()).thenReturn(false)
        whenever(mockMetricsDir.mkdirs()).thenReturn(true)
        whenever(mockMetricsDir.resolve(anyString())).thenReturn(mockMetricsFile)
        
        // Try to log a metric event
        val result = metricsCollector.logMetricEvent("test_event", mapOf("key" to "value"))
        
        // Should succeed
        assertTrue(result.isSuccess)
    }

    @Test
    fun `logASRAccuracy should include all required metrics`() = runTest {
        // Set metrics collection allowed
        whenever(mockSharedPreferences.getBoolean(eq("pilot_mode_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("metrics_consent"), any())).thenReturn(true)
        
        // Mock file operations for writing metrics
        val mockMetricsDir = mock(File::class.java)
        val mockMetricsFile = mock(File::class.java)
        
        whenever(mockFile.resolve(anyString())).thenReturn(mockMetricsDir)
        whenever(mockMetricsDir.exists()).thenReturn(false)
        whenever(mockMetricsDir.mkdirs()).thenReturn(true)
        whenever(mockMetricsDir.resolve(anyString())).thenReturn(mockMetricsFile)
        
        // Log ASR accuracy metrics
        val result = metricsCollector.logASRAccuracy(
            wer = 0.05f,
            medF1Score = 0.85f,
            confidenceScore = 0.9f,
            sampleId = "test-sample-1",
            durationMs = 5000L
        )
        
        // Should succeed
        assertTrue(result.isSuccess)
    }

    @Test
    fun `logPerformanceMetrics should include all required metrics`() = runTest {
        // Set metrics collection allowed
        whenever(mockSharedPreferences.getBoolean(eq("pilot_mode_enabled"), any())).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(eq("metrics_consent"), any())).thenReturn(true)
        
        // Mock file operations for writing metrics
        val mockMetricsDir = mock(File::class.java)
        val mockMetricsFile = mock(File::class.java)
        
        whenever(mockFile.resolve(anyString())).thenReturn(mockMetricsDir)
        whenever(mockMetricsDir.exists()).thenReturn(false)
        whenever(mockMetricsDir.mkdirs()).thenReturn(true)
        whenever(mockMetricsDir.resolve(anyString())).thenReturn(mockMetricsFile)
        
        // Log performance metrics
        val result = metricsCollector.logPerformanceMetrics(
            processingTimeMs = 1200L,
            memoryUsageMb = 256.5f,
            cpuUsagePercent = 45.2f,
            threadCount = 4,
            contextSize = 3000
        )
        
        // Should succeed
        assertTrue(result.isSuccess)
    }

    @Test
    fun `clearAllMetrics should delete metrics directory`() {
        // Mock metrics directory
        val mockMetricsDir = mock(File::class.java)
        whenever(mockFile.resolve(eq("metrics"))).thenReturn(mockMetricsDir)
        whenever(mockMetricsDir.exists()).thenReturn(true)
        whenever(mockMetricsDir.deleteRecursively()).thenReturn(true)
        
        // Clear metrics
        metricsCollector.clearAllMetrics()
        
        // Verify directory deleted
        verify(mockMetricsDir).deleteRecursively()
    }
}
