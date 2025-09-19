package com.frozo.ambientscribe.services

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class OEMKillerWatchdogTest {

    @Mock
    private lateinit var mockApplication: Application
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Mock
    private lateinit var mockMetricsCollector: MetricsCollector
    
    @Mock
    private lateinit var mockLifecycleOwner: LifecycleOwner
    
    @Mock
    private lateinit var mockProcessLifecycleOwner: ProcessLifecycleOwner
    
    @Mock
    private lateinit var mockLifecycle: Lifecycle
    
    @Mock
    private lateinit var mockHandler: Handler
    
    private lateinit var watchdog: OEMKillerWatchdog

    @Before
    fun setUp() {
        // Mock Timber
        val timberMockedStatic = mockStatic(Timber::class.java)
        timberMockedStatic.`when`<Unit> { Timber.d(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.i(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.w(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.e(anyString()) }.thenAnswer { }
        timberMockedStatic.`when`<Unit> { Timber.e(any<Throwable>(), anyString()) }.thenAnswer { }
        
        // Mock SharedPreferences
        whenever(mockApplication.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(anyString(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        whenever(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        
        // Mock ProcessLifecycleOwner
        val processLifecycleOwnerMockedStatic = mockStatic(ProcessLifecycleOwner::class.java)
        processLifecycleOwnerMockedStatic.`when`<ProcessLifecycleOwner> { 
            ProcessLifecycleOwner.get() 
        }.thenReturn(mockProcessLifecycleOwner)
        whenever(mockProcessLifecycleOwner.lifecycle).thenReturn(mockLifecycle)
        
        // Mock SystemClock
        val systemClockMockedStatic = mockStatic(SystemClock::class.java)
        systemClockMockedStatic.`when`<Long> { 
            SystemClock.elapsedRealtime() 
        }.thenReturn(1000L)
        
        // Mock Build for OEM detection
        setFinalStatic(Build::class.java.getField("MANUFACTURER"), "xiaomi")
        
        // Create watchdog with mocked dependencies
        watchdog = OEMKillerWatchdog(mockApplication, mockMetricsCollector)
    }

    @Test
    fun `start should set running state and record heartbeat`() {
        // When
        watchdog.start()
        
        // Then
        verify(mockEditor).putBoolean("was_running", true)
        verify(mockEditor).putLong(eq("last_heartbeat"), any())
        verify(mockEditor, times(2)).apply()
    }

    @Test
    fun `stop should clear running state`() {
        // Given
        watchdog.start()
        
        // When
        watchdog.stop()
        
        // Then
        verify(mockEditor).putBoolean("was_running", false)
    }

    @Test
    fun `abnormal termination should be detected`() {
        // Given
        whenever(mockSharedPreferences.getBoolean("was_running", false)).thenReturn(true)
        whenever(mockSharedPreferences.getLong("last_heartbeat", 0L)).thenReturn(900L) // Recent heartbeat
        
        // When
        val watchdog = OEMKillerWatchdog(mockApplication, mockMetricsCollector)
        
        // Then
        verify(mockEditor).putBoolean(eq("abnormal_termination"), eq(true))
        verify(mockEditor).putString(eq("oem_type"), eq("MIUI"))
        verify(mockMetricsCollector)?.logMetricEvent(eq("oem_killer_detected"), any())
    }

    @Test
    fun `getOEMOptimizationGuidance should return correct guidance for device`() {
        // When
        val guidance = watchdog.getOEMOptimizationGuidance()
        
        // Then
        assertTrue(guidance.contains("MIUI"))
    }

    @Test
    fun `autoRestart should clear abnormal termination flag`() {
        // Given
        whenever(mockSharedPreferences.getBoolean("abnormal_termination", false)).thenReturn(true)
        
        // When
        watchdog.autoRestart()
        
        // Then
        verify(mockEditor).putBoolean("abnormal_termination", false)
        verify(mockMetricsCollector)?.logMetricEvent(eq("oem_killer_auto_restart"), any())
    }

    @Test
    fun `lifecycle events should control watchdog state`() {
        // When
        watchdog.onStart(mockLifecycleOwner)
        
        // Then
        verify(mockEditor).putBoolean("was_running", true)
        
        // When
        watchdog.onStop(mockLifecycleOwner)
        
        // Then
        verify(mockEditor).putBoolean("was_running", false)
    }
    
    // Helper method to set final static fields for testing
    @Throws(Exception::class)
    private fun setFinalStatic(field: Field, newValue: Any) {
        field.isAccessible = true
        
        val modifiersField = Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
        
        field.set(null, newValue)
    }
}
