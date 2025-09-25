package com.frozo.ambientscribe.telemetry

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Unit tests for PilotModeMetrics (PT-8 implementation)
 */
class PilotModeMetricsTest {
    
    private lateinit var pilotMetrics: PilotModeMetrics
    
    @BeforeEach
    fun setUp() {
        pilotMetrics = PilotModeMetrics()
    }
    
    @Test
    fun `test enable pilot mode`() {
        // Given
        assertFalse(pilotMetrics.isEnabled)
        
        // When
        pilotMetrics.enable()
        
        // Then
        assertTrue(pilotMetrics.isEnabled)
    }
    
    @Test
    fun `test disable pilot mode`() {
        // Given
        pilotMetrics.enable()
        assertTrue(pilotMetrics.isEnabled)
        
        // When
        pilotMetrics.disable()
        
        // Then
        assertFalse(pilotMetrics.isEnabled)
    }
    
    @Test
    fun `test add transcription event when disabled`() {
        // Given
        pilotMetrics.disable()
        val event = TranscriptionCompleteEvent(
            encounterId = "test-encounter-123",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456",
            werEstimate = 0.15,
            processingTimeMs = 2500L,
            modelVersion = "whisper-tiny-int8@ct2-1",
            audioDurationMs = 30000L,
            confidenceScore = 0.85,
            languageDetected = "en"
        )
        
        // When
        pilotMetrics.addTranscriptionEvent(event)
        
        // Then
        val summary = pilotMetrics.getSummary()
        assertEquals(0, summary.totalTranscriptions)
    }
    
    @Test
    fun `test add transcription event when enabled`() {
        // Given
        pilotMetrics.enable()
        val event = TranscriptionCompleteEvent(
            encounterId = "test-encounter-123",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456",
            werEstimate = 0.15,
            processingTimeMs = 2500L,
            modelVersion = "whisper-tiny-int8@ct2-1",
            audioDurationMs = 30000L,
            confidenceScore = 0.85,
            languageDetected = "en"
        )
        
        // When
        pilotMetrics.addTranscriptionEvent(event)
        
        // Then
        val summary = pilotMetrics.getSummary()
        assertEquals(1, summary.totalTranscriptions)
        assertTrue(summary.averageWER > 0.0)
        assertTrue(summary.modelVersions.isNotEmpty())
        assertTrue(summary.languageDistribution.isNotEmpty())
    }
    
    @Test
    fun `test F1 score calculation`() {
        // Given
        pilotMetrics.enable()
        
        // When
        val f1Score = pilotMetrics.calculateF1Score(80, 10, 20)
        
        // Then
        assertTrue(f1Score > 0.0)
        assertTrue(f1Score <= 1.0)
    }
    
    @Test
    fun `test F1 score calculation with zero values`() {
        // Given
        pilotMetrics.enable()
        
        // When
        val f1Score = pilotMetrics.calculateF1Score(0, 0, 0)
        
        // Then
        assertEquals(0.0, f1Score)
    }
    
    @Test
    fun `test add F1 score sample`() {
        // Given
        pilotMetrics.enable()
        
        // When
        pilotMetrics.addF1ScoreSample(0.85)
        pilotMetrics.addF1ScoreSample(0.90)
        
        // Then
        val summary = pilotMetrics.getSummary()
        assertTrue(summary.averageF1Score > 0.0)
        
        val f1Analysis = pilotMetrics.getF1Analysis()
        assertEquals(2, f1Analysis.totalSamples)
        assertTrue(f1Analysis.averageF1 > 0.0)
    }
    
    @Test
    fun `test WER analysis`() {
        // Given
        pilotMetrics.enable()
        val event1 = TranscriptionCompleteEvent(
            encounterId = "test-encounter-1",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456",
            werEstimate = 0.15,
            processingTimeMs = 2500L,
            modelVersion = "whisper-tiny-int8@ct2-1",
            audioDurationMs = 30000L,
            confidenceScore = 0.85,
            languageDetected = "en"
        )
        
        val event2 = TranscriptionCompleteEvent(
            encounterId = "test-encounter-2",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456",
            werEstimate = 0.20,
            processingTimeMs = 2800L,
            modelVersion = "whisper-tiny-int8@ct2-1",
            audioDurationMs = 35000L,
            confidenceScore = 0.80,
            languageDetected = "en"
        )
        
        // When
        pilotMetrics.addTranscriptionEvent(event1)
        pilotMetrics.addTranscriptionEvent(event2)
        
        // Then
        val werAnalysis = pilotMetrics.getWERAnalysis()
        assertEquals(2, werAnalysis.totalSamples)
        assertTrue(werAnalysis.averageWER > 0.0)
        assertTrue(werAnalysis.minWER > 0.0)
        assertTrue(werAnalysis.maxWER > 0.0)
    }
    
    @Test
    fun `test clear data`() {
        // Given
        pilotMetrics.enable()
        pilotMetrics.addF1ScoreSample(0.85)
        val event = TranscriptionCompleteEvent(
            encounterId = "test-encounter-123",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456",
            werEstimate = 0.15,
            processingTimeMs = 2500L,
            modelVersion = "whisper-tiny-int8@ct2-1",
            audioDurationMs = 30000L,
            confidenceScore = 0.85,
            languageDetected = "en"
        )
        pilotMetrics.addTranscriptionEvent(event)
        
        // When
        pilotMetrics.clearData()
        
        // Then
        val summary = pilotMetrics.getSummary()
        assertEquals(0, summary.totalTranscriptions)
        assertEquals(0.0, summary.averageWER)
        assertEquals(0.0, summary.averageF1Score)
    }
    
    @Test
    fun `test export pilot data`() {
        // Given
        pilotMetrics.enable()
        val event = TranscriptionCompleteEvent(
            encounterId = "test-encounter-123",
            timestamp = Instant.now().toString(),
            deviceTier = "A",
            clinicId = "clinic-456",
            werEstimate = 0.15,
            processingTimeMs = 2500L,
            modelVersion = "whisper-tiny-int8@ct2-1",
            audioDurationMs = 30000L,
            confidenceScore = 0.85,
            languageDetected = "en"
        )
        pilotMetrics.addTranscriptionEvent(event)
        
        // When
        val csvData = pilotMetrics.exportPilotData()
        
        // Then
        assertTrue(csvData.contains("timestamp,encounter_id,wer_estimate"))
        assertTrue(csvData.contains("test-encounter-123"))
        assertTrue(csvData.contains("0.15"))
    }
}
