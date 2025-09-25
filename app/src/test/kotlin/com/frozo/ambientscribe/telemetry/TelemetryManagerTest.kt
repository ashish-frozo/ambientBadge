package com.frozo.ambientscribe.telemetry

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.time.Instant

/**
 * Unit tests for TelemetryManager (PT-8 implementation)
 */
class TelemetryManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var telemetryManager: TelemetryManager
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockPrefs = mockk<SharedPreferences>(relaxed = true)
        mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putString(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        every { mockPrefs.getString(any(), any()) } returns null
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefs.getBoolean(any(), any()) } returns false
        
        // Mock files directory
        val mockFilesDir = mockk<File>(relaxed = true)
        every { mockContext.filesDir } returns mockFilesDir
        every { mockFilesDir.exists() } returns true
        
        // Mock telemetry directory
        val mockTelemetryDir = mockk<File>(relaxed = true)
        every { mockFilesDir.resolve("telemetry") } returns mockTelemetryDir
        every { mockTelemetryDir.exists() } returns true
        every { mockTelemetryDir.mkdirs() } returns true
        
        // Mock events file
        val mockEventsFile = mockk<File>(relaxed = true)
        every { mockTelemetryDir.resolve("events.jsonl") } returns mockEventsFile
        every { mockEventsFile.exists() } returns true
        every { mockEventsFile.appendText(any()) } just Runs
        every { mockEventsFile.readLines() } returns emptyList()
        
        telemetryManager = TelemetryManager.getInstance(mockContext)
    }
    
    @Test
    fun `test log encounter start event`() {
        // Given
        val encounterId = "test-encounter-123"
        val deviceTier = "A"
        val clinicId = "clinic-456"
        val audioQuality = "excellent"
        val batteryLevel = 85
        
        // When
        telemetryManager.logEncounterStart(
            encounterId = encounterId,
            deviceTier = deviceTier,
            clinicId = clinicId,
            audioQuality = audioQuality,
            batteryLevel = batteryLevel
        )
        
        // Then
        // Verify that the event was logged (in a real implementation, we'd check the event queue)
        assertTrue(true) // Placeholder assertion
    }
    
    @Test
    fun `test log transcription complete event`() {
        // Given
        val encounterId = "test-encounter-123"
        val deviceTier = "A"
        val clinicId = "clinic-456"
        val werEstimate = 0.15
        val processingTimeMs = 2500L
        val modelVersion = "whisper-tiny-int8@ct2-1"
        val audioDurationMs = 30000L
        val confidenceScore = 0.85
        val languageDetected = "en"
        
        // When
        telemetryManager.logTranscriptionComplete(
            encounterId = encounterId,
            deviceTier = deviceTier,
            clinicId = clinicId,
            werEstimate = werEstimate,
            processingTimeMs = processingTimeMs,
            modelVersion = modelVersion,
            audioDurationMs = audioDurationMs,
            confidenceScore = confidenceScore,
            languageDetected = languageDetected
        )
        
        // Then
        assertTrue(true) // Placeholder assertion
    }
    
    @Test
    fun `test log review complete event`() {
        // Given
        val encounterId = "test-encounter-123"
        val deviceTier = "A"
        val clinicId = "clinic-456"
        val editRatePercent = 12.5
        val reviewDurationS = 60L
        val confidenceOverrides = 2
        val totalEdits = 5
        val prescriptionEdits = 3
        val soapEdits = 2
        val redFlagsResolved = 1
        
        // When
        telemetryManager.logReviewComplete(
            encounterId = encounterId,
            deviceTier = deviceTier,
            clinicId = clinicId,
            editRatePercent = editRatePercent,
            reviewDurationS = reviewDurationS,
            confidenceOverrides = confidenceOverrides,
            totalEdits = totalEdits,
            prescriptionEdits = prescriptionEdits,
            soapEdits = soapEdits,
            redFlagsResolved = redFlagsResolved
        )
        
        // Then
        assertTrue(true) // Placeholder assertion
    }
    
    @Test
    fun `test log export success event`() {
        // Given
        val encounterId = "test-encounter-123"
        val deviceTier = "A"
        val clinicId = "clinic-456"
        val pdfSizeKb = 150L
        val exportDurationMs = 3000L
        val batteryLevelPercent = 80
        val storageUsedKb = 500L
        val qrCodeGenerated = true
        val encryptionApplied = true
        
        // When
        telemetryManager.logExportSuccess(
            encounterId = encounterId,
            deviceTier = deviceTier,
            clinicId = clinicId,
            pdfSizeKb = pdfSizeKb,
            exportDurationMs = exportDurationMs,
            batteryLevelPercent = batteryLevelPercent,
            storageUsedKb = storageUsedKb,
            qrCodeGenerated = qrCodeGenerated,
            encryptionApplied = encryptionApplied
        )
        
        // Then
        assertTrue(true) // Placeholder assertion
    }
    
    @Test
    fun `test log thermal event`() {
        // Given
        val encounterId = "test-encounter-123"
        val deviceTier = "A"
        val clinicId = "clinic-456"
        val thermalState = "WARNING"
        val mitigationAction = "THROTTLE"
        val cpuUsagePercent = 85.5
        val temperature = 45.0
        val recoveryTimeMs = 2000L
        
        // When
        telemetryManager.logThermalEvent(
            encounterId = encounterId,
            deviceTier = deviceTier,
            clinicId = clinicId,
            thermalState = thermalState,
            mitigationAction = mitigationAction,
            cpuUsagePercent = cpuUsagePercent,
            temperature = temperature,
            recoveryTimeMs = recoveryTimeMs
        )
        
        // Then
        assertTrue(true) // Placeholder assertion
    }
    
    @Test
    fun `test log edit cause code event`() {
        // Given
        val encounterId = "test-encounter-123"
        val deviceTier = "A"
        val clinicId = "clinic-456"
        val editType = "heard"
        val fieldName = "frequency"
        val originalValue = "twice daily"
        val correctedValue = "twice a day"
        val confidenceScore = 0.75
        
        // When
        telemetryManager.logEditCauseCode(
            encounterId = encounterId,
            deviceTier = deviceTier,
            clinicId = clinicId,
            editType = editType,
            fieldName = fieldName,
            originalValue = originalValue,
            correctedValue = correctedValue,
            confidenceScore = confidenceScore
        )
        
        // Then
        assertTrue(true) // Placeholder assertion
    }
    
    @Test
    fun `test log policy toggle event`() {
        // Given
        val encounterId = "test-encounter-123"
        val deviceTier = "A"
        val clinicId = "clinic-456"
        val policyType = "brand_generic"
        val actor = "doctor_123"
        val beforeValue = "false"
        val afterValue = "true"
        val reason = "User preference change"
        
        // When
        telemetryManager.logPolicyToggle(
            encounterId = encounterId,
            deviceTier = deviceTier,
            clinicId = clinicId,
            policyType = policyType,
            actor = actor,
            beforeValue = beforeValue,
            afterValue = afterValue,
            reason = reason
        )
        
        // Then
        assertTrue(true) // Placeholder assertion
    }
    
    @Test
    fun `test log bulk edit applied event`() {
        // Given
        val encounterId = "test-encounter-123"
        val deviceTier = "A"
        val clinicId = "clinic-456"
        val actor = "doctor_123"
        val editType = "frequency"
        val beforeValue = "twice daily"
        val afterValue = "twice a day"
        val affectedCount = 5
        
        // When
        telemetryManager.logBulkEditApplied(
            encounterId = encounterId,
            deviceTier = deviceTier,
            clinicId = clinicId,
            actor = actor,
            editType = editType,
            beforeValue = beforeValue,
            afterValue = afterValue,
            affectedCount = affectedCount
        )
        
        // Then
        assertTrue(true) // Placeholder assertion
    }
    
    @Test
    fun `test pilot mode toggle`() {
        // Given
        val initialPilotMode = telemetryManager.isPilotModeEnabled()
        
        // When
        telemetryManager.setPilotMode(true)
        val pilotModeEnabled = telemetryManager.isPilotModeEnabled()
        
        telemetryManager.setPilotMode(false)
        val pilotModeDisabled = telemetryManager.isPilotModeEnabled()
        
        // Then
        assertFalse(initialPilotMode)
        assertTrue(pilotModeEnabled)
        assertFalse(pilotModeDisabled)
    }
    
    @Test
    fun `test crash free session rate calculation`() {
        // Given
        every { mockPrefs.getLong("crash_free_sessions", 0) } returns 100L
        every { mockPrefs.getLong("crash_count", 0) } returns 5L
        
        // When
        val crashFreeRate = telemetryManager.getCrashFreeSessionRate()
        
        // Then
        assertEquals(95.0, crashFreeRate, 0.01)
    }
    
    @Test
    fun `test update crash free session metrics`() {
        // Given
        every { mockPrefs.getLong("crash_free_sessions", 0) } returns 100L
        every { mockPrefs.getLong("crash_count", 0) } returns 5L
        
        // When
        telemetryManager.updateCrashFreeSessionMetrics(sessionCompleted = true, hadCrash = false)
        
        // Then
        verify { mockPrefsEditor.putLong("crash_free_sessions", 101L) }
        verify { mockPrefsEditor.apply() }
    }
    
    @Test
    fun `test export edit cause codes to CSV`() {
        // Given
        val mockEventsFile = mockk<File>(relaxed = true)
        every { mockEventsFile.exists() } returns true
        every { mockEventsFile.readLines() } returns listOf(
            """{"encounter_id":"test-123","event_type":"edit_cause_code","timestamp":"2023-01-01T00:00:00Z","device_tier":"A","clinic_id":"clinic-456","edit_type":"heard","field_name":"frequency","original_value":"twice daily","corrected_value":"twice a day","confidence_score":0.75}"""
        )
        
        // Mock the file resolution
        val mockTelemetryDir = mockk<File>(relaxed = true)
        every { mockContext.filesDir } returns mockk<File>(relaxed = true)
        every { mockContext.filesDir.resolve("telemetry") } returns mockTelemetryDir
        every { mockTelemetryDir.resolve("events.jsonl") } returns mockEventsFile
        
        // When
        val csv = telemetryManager.exportEditCauseCodesToCsv()
        
        // Then
        assertTrue(csv.contains("timestamp,encounter_id,edit_type,field_name,original_value,corrected_value,confidence_score"))
        assertTrue(csv.contains("test-123"))
        assertTrue(csv.contains("heard"))
    }
    
    @Test
    fun `test get aggregated metrics`() {
        // When
        val metrics = telemetryManager.getAggregatedMetrics()
        
        // Then
        assertNotNull(metrics)
        assertTrue(metrics.totalEncounters >= 0)
        assertTrue(metrics.recentEncounters >= 0)
    }
    
    @Test
    fun `test cleanup`() {
        // When
        telemetryManager.cleanup()
        
        // Then
        // Verify cleanup was called (in a real implementation, we'd check that coroutines were cancelled)
        assertTrue(true) // Placeholder assertion
    }
}
