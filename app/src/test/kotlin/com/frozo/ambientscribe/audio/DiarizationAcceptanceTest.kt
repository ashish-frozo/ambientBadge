package com.frozo.ambientscribe.audio

import android.content.Context
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class DiarizationAcceptanceTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockMetricsCollector: MetricsCollector
    
    private lateinit var diarizationEvaluator: DiarizationEvaluator
    private lateinit var speakerDiarization: SpeakerDiarization

    @Before
    fun setUp() {
        // Mock metrics collector
        whenever(mockMetricsCollector.logMetricEvent(any(), any())).thenReturn(Result.success("test-event-id"))
        
        // Create evaluator with mocked dependencies
        diarizationEvaluator = DiarizationEvaluator(mockContext, mockMetricsCollector)
        
        // Create speaker diarization with mocked context
        speakerDiarization = SpeakerDiarization(mockContext)
    }

    @Test
    fun `diarization quality should be UNKNOWN initially`() = runTest {
        val quality = diarizationEvaluator.diarizationQuality.value
        assertEquals(DiarizationEvaluator.DiarizationQuality.UNKNOWN, quality)
        
        val metrics = diarizationEvaluator.getDiarizationMetrics()
        assertEquals(0.0f, metrics["der"] as Float)
        assertEquals(1.0f, metrics["swap_accuracy"] as Float)
        assertEquals("UNKNOWN", metrics["quality"])
        assertFalse(metrics["fallback_mode"] as Boolean)
    }

    @Test
    fun `DER calculation should meet acceptance criteria`() = runTest {
        // Add samples with consistent speaker assignment (good diarization)
        repeat(30) {
            diarizationEvaluator.addDiarizationSample(
                speakerId = SpeakerDiarization.SPEAKER_DOCTOR,
                energyLevel = 0.5f,
                confidence = 0.9f,
                isManuallyAssigned = false
            )
        }
        
        // DER should be low (good quality)
        val metrics = diarizationEvaluator.getDiarizationMetrics()
        assertTrue((metrics["der"] as Float) <= DiarizationEvaluator.DER_THRESHOLD_GOOD)
        assertEquals(DiarizationEvaluator.DiarizationQuality.GOOD.name, metrics["quality"])
        assertFalse(metrics["fallback_mode"] as Boolean)
    }

    @Test
    fun `poor diarization should trigger fallback mode`() = runTest {
        // Add samples with inconsistent speaker assignment (poor diarization)
        repeat(30) { i ->
            diarizationEvaluator.addDiarizationSample(
                speakerId = if (i % 2 == 0) SpeakerDiarization.SPEAKER_DOCTOR else SpeakerDiarization.SPEAKER_PATIENT,
                energyLevel = 0.5f,
                confidence = 0.5f,
                isManuallyAssigned = false
            )
        }
        
        // DER should be high (poor quality)
        val metrics = diarizationEvaluator.getDiarizationMetrics()
        assertTrue((metrics["der"] as Float) > DiarizationEvaluator.DER_THRESHOLD_MODERATE)
        assertEquals(DiarizationEvaluator.DiarizationQuality.POOR.name, metrics["quality"])
        assertTrue(metrics["fallback_mode"] as Boolean)
    }

    @Test
    fun `frequent manual swaps should trigger fallback mode`() = runTest {
        // Record automatic speaker swaps
        repeat(10) {
            diarizationEvaluator.recordSpeakerSwap(
                fromSpeakerId = SpeakerDiarization.SPEAKER_DOCTOR,
                toSpeakerId = SpeakerDiarization.SPEAKER_PATIENT,
                isAutomatic = true
            )
        }
        
        // Record manual corrections to most of them
        repeat(8) {
            diarizationEvaluator.recordSpeakerSwap(
                fromSpeakerId = SpeakerDiarization.SPEAKER_PATIENT,
                toSpeakerId = SpeakerDiarization.SPEAKER_DOCTOR,
                isAutomatic = false
            )
        }
        
        // Update swap accuracy
        repeat(5) {
            diarizationEvaluator.addDiarizationSample(
                speakerId = SpeakerDiarization.SPEAKER_DOCTOR,
                energyLevel = 0.5f,
                confidence = 0.5f,
                isManuallyAssigned = false
            )
        }
        
        // Swap accuracy should be low and fallback mode should be enabled
        val metrics = diarizationEvaluator.getDiarizationMetrics()
        assertTrue((metrics["swap_accuracy"] as Float) < DiarizationEvaluator.SWAP_ACCURACY_THRESHOLD)
        assertTrue(metrics["fallback_mode"] as Boolean)
    }

    @Test
    fun `single-speaker mode should prevent speaker changes`() = runTest {
        // Create audio data with varying energy levels
        val lowEnergyAudio = AudioCapture.AudioData(
            samples = ShortArray(100) { 100 },
            timestamp = System.currentTimeMillis(),
            isVoiceActive = true,
            energyLevel = 0.1f
        )
        
        val highEnergyAudio = AudioCapture.AudioData(
            samples = ShortArray(100) { 1000 },
            timestamp = System.currentTimeMillis() + 1000,
            isVoiceActive = true,
            energyLevel = 0.8f
        )
        
        // Process first speaker (doctor)
        speakerDiarization.processAudioData(lowEnergyAudio)
        val firstSpeaker = speakerDiarization.getCurrentSpeaker()
        
        // Force fallback mode
        val evaluatorField = SpeakerDiarization::class.java.getDeclaredField("isSingleSpeakerMode")
        evaluatorField.isAccessible = true
        val isSingleSpeakerMode = evaluatorField.get(speakerDiarization) as AtomicBoolean
        isSingleSpeakerMode.set(true)
        
        // Process second speaker with different energy
        // In fallback mode, speaker should not change
        speakerDiarization.processAudioData(highEnergyAudio)
        val secondSpeaker = speakerDiarization.getCurrentSpeaker()
        
        // Speaker should not change in fallback mode
        assertEquals(firstSpeaker, secondSpeaker)
        assertTrue(speakerDiarization.isInSingleSpeakerMode())
    }

    @Test
    fun `manual role swap should work even in fallback mode`() = runTest {
        // Set initial speaker
        speakerDiarization.setCurrentSpeaker(SpeakerDiarization.SPEAKER_DOCTOR)
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, speakerDiarization.getCurrentSpeaker())
        
        // Force fallback mode
        val evaluatorField = SpeakerDiarization::class.java.getDeclaredField("isSingleSpeakerMode")
        evaluatorField.isAccessible = true
        val isSingleSpeakerMode = evaluatorField.get(speakerDiarization) as AtomicBoolean
        isSingleSpeakerMode.set(true)
        
        // Manual swap should still work
        speakerDiarization.swapSpeakerRoles()
        
        // Speaker should be changed to patient
        assertEquals(SpeakerDiarization.SPEAKER_PATIENT, speakerDiarization.getCurrentSpeaker())
    }
}
