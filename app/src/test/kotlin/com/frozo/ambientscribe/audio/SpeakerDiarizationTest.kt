package com.frozo.ambientscribe.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class SpeakerDiarizationTest {

    private lateinit var diarization: SpeakerDiarization

    @Before
    fun setUp() {
        diarization = SpeakerDiarization(
            energyThresholdRatio = 1.5f,
            switchHysteresisMs = 500, // Shorter for testing
            minUtteranceDurationMs = 300, // Shorter for testing
            silenceThresholdMs = 200 // Shorter for testing
        )
    }

    @After
    fun tearDown() {
        // No specific cleanup needed
    }

    @Test
    fun `initial speaker should be unknown`() {
        assertEquals(SpeakerDiarization.SPEAKER_UNKNOWN, diarization.getCurrentSpeaker())
    }

    @Test
    fun `manual speaker assignment should work`() {
        diarization.setCurrentSpeaker(SpeakerDiarization.SPEAKER_DOCTOR)
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
        
        diarization.setCurrentSpeaker(SpeakerDiarization.SPEAKER_PATIENT)
        assertEquals(SpeakerDiarization.SPEAKER_PATIENT, diarization.getCurrentSpeaker())
    }

    @Test
    fun `speaker role swap should exchange doctor and patient`() {
        // Set initial speaker
        diarization.setCurrentSpeaker(SpeakerDiarization.SPEAKER_DOCTOR)
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
        
        // Swap roles
        diarization.swapSpeakerRoles()
        assertEquals(SpeakerDiarization.SPEAKER_PATIENT, diarization.getCurrentSpeaker())
        
        // Swap again
        diarization.swapSpeakerRoles()
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
    }

    @Test
    fun `reset should clear speaker state`() {
        // Set a speaker
        diarization.setCurrentSpeaker(SpeakerDiarization.SPEAKER_DOCTOR)
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
        
        // Reset
        diarization.reset()
        assertEquals(SpeakerDiarization.SPEAKER_UNKNOWN, diarization.getCurrentSpeaker())
    }

    @Test
    fun `speaker labels should be correct`() {
        assertEquals("Doctor", diarization.getSpeakerLabel(SpeakerDiarization.SPEAKER_DOCTOR))
        assertEquals("Patient", diarization.getSpeakerLabel(SpeakerDiarization.SPEAKER_PATIENT))
        assertEquals("Unknown", diarization.getSpeakerLabel(SpeakerDiarization.SPEAKER_UNKNOWN))
    }

    @Test
    fun `diarization flow should emit results`() = runTest {
        // Get flow
        val flow = diarization.getDiarizationFlow()
        assertNotNull(flow)
        
        // Manually set speaker to trigger emission
        diarization.setCurrentSpeaker(SpeakerDiarization.SPEAKER_DOCTOR)
        
        // Collect first result
        val result = withTimeout(1000) {
            flow.first()
        }
        
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, result.speakerId)
        assertEquals("Doctor", result.speakerLabel)
        assertTrue(result.isManuallyAssigned)
    }

    @Test
    fun `should identify first speaker as doctor`() {
        // Create audio data with active voice
        val timestamp = System.currentTimeMillis()
        val audioData = createAudioData(
            isVoiceActive = true,
            energy = 0.5f,
            timestamp = timestamp
        )
        
        // Process enough samples to trigger identification
        repeat(10) {
            diarization.processAudioData(audioData.copy(timestamp = timestamp + it * 100))
        }
        
        // First speaker should be identified as doctor
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
    }

    @Test
    fun `should detect speaker change based on energy`() {
        // First speaker (doctor)
        val timestamp = System.currentTimeMillis()
        val doctorAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.5f,
            timestamp = timestamp
        )
        
        // Process enough samples to establish doctor profile
        repeat(10) {
            diarization.processAudioData(doctorAudio.copy(timestamp = timestamp + it * 100))
        }
        
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
        
        // Add silence gap
        val silenceAudio = createAudioData(
            isVoiceActive = false,
            energy = 0.01f,
            timestamp = timestamp + 1500
        )
        repeat(5) {
            diarization.processAudioData(silenceAudio.copy(timestamp = timestamp + 1500 + it * 100))
        }
        
        // Second speaker (patient) with significantly different energy
        val patientAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.9f, // Much higher energy
            timestamp = timestamp + 2000
        )
        
        // Process enough samples to trigger speaker change
        repeat(10) {
            diarization.processAudioData(patientAudio.copy(timestamp = timestamp + 2000 + it * 100))
        }
        
        // Should detect speaker change
        assertEquals(SpeakerDiarization.SPEAKER_PATIENT, diarization.getCurrentSpeaker())
    }

    @Test
    fun `should maintain speaker identity during continuous speech`() {
        // Establish doctor profile
        val timestamp = System.currentTimeMillis()
        val doctorAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.5f,
            timestamp = timestamp
        )
        
        repeat(10) {
            diarization.processAudioData(doctorAudio.copy(timestamp = timestamp + it * 100))
        }
        
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
        
        // Continue with similar energy
        val continuedAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.55f, // Similar energy
            timestamp = timestamp + 1500
        )
        
        repeat(10) {
            diarization.processAudioData(continuedAudio.copy(timestamp = timestamp + 1500 + it * 100))
        }
        
        // Should maintain same speaker
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
    }

    @Test
    fun `should respect hysteresis time for speaker changes`() {
        // Establish doctor profile
        val timestamp = System.currentTimeMillis()
        val doctorAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.5f,
            timestamp = timestamp
        )
        
        repeat(10) {
            diarization.processAudioData(doctorAudio.copy(timestamp = timestamp + it * 100))
        }
        
        // Try to change speaker too soon (within hysteresis)
        val patientAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.9f,
            timestamp = timestamp + 400 // Less than hysteresis (500ms)
        )
        
        repeat(5) {
            diarization.processAudioData(patientAudio.copy(timestamp = timestamp + 400 + it * 50))
        }
        
        // Should still be doctor due to hysteresis
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
        
        // Now try after hysteresis period
        val laterPatientAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.9f,
            timestamp = timestamp + 600 // More than hysteresis
        )
        
        repeat(10) {
            diarization.processAudioData(laterPatientAudio.copy(timestamp = timestamp + 600 + it * 100))
        }
        
        // Should now change to patient
        assertEquals(SpeakerDiarization.SPEAKER_PATIENT, diarization.getCurrentSpeaker())
    }

    @Test
    fun `manual assignment should override automatic diarization`() {
        // Establish automatic diarization
        val timestamp = System.currentTimeMillis()
        val doctorAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.5f,
            timestamp = timestamp
        )
        
        repeat(10) {
            diarization.processAudioData(doctorAudio.copy(timestamp = timestamp + it * 100))
        }
        
        assertEquals(SpeakerDiarization.SPEAKER_DOCTOR, diarization.getCurrentSpeaker())
        
        // Manually override
        diarization.setCurrentSpeaker(SpeakerDiarization.SPEAKER_PATIENT)
        assertEquals(SpeakerDiarization.SPEAKER_PATIENT, diarization.getCurrentSpeaker())
        
        // Process more audio that would normally trigger a change
        val newAudio = createAudioData(
            isVoiceActive = true,
            energy = 0.1f, // Very different energy
            timestamp = timestamp + 1000
        )
        
        repeat(10) {
            diarization.processAudioData(newAudio.copy(timestamp = timestamp + 1000 + it * 100))
        }
        
        // Should still be patient due to manual override
        assertEquals(SpeakerDiarization.SPEAKER_PATIENT, diarization.getCurrentSpeaker())
    }

    @Test
    fun `speaker energy profile should update correctly`() {
        // Create a profile and update it
        val profile = SpeakerDiarization.SpeakerEnergyProfile()
        
        val updatedProfile = profile.update(0.5f)
        assertEquals(0.5f, updatedProfile.meanEnergy)
        assertEquals(0.5f, updatedProfile.peakEnergy)
        assertEquals(0.5f, updatedProfile.minEnergy)
        assertEquals(1, updatedProfile.energySamples)
        
        // Update again
        val finalProfile = updatedProfile.update(1.0f)
        assertEquals(0.75f, finalProfile.meanEnergy) // (0.5 + 1.0) / 2
        assertEquals(1.0f, finalProfile.peakEnergy)
        assertEquals(0.5f, finalProfile.minEnergy)
        assertEquals(2, finalProfile.energySamples)
    }

    @Test
    fun `similarity calculation should work correctly`() {
        // Create profile with known mean
        val profile = SpeakerDiarization.SpeakerEnergyProfile(
            meanEnergy = 0.5f,
            peakEnergy = 0.8f,
            minEnergy = 0.2f,
            energySamples = 10
        )
        
        // Test similarity calculations
        assertEquals(1.0f, profile.similarity(0.5f)) // Exact match
        assertEquals(0.8f, profile.similarity(0.4f)) // 0.4/0.5 = 0.8
        assertEquals(0.8f, profile.similarity(0.625f)) // 0.5/0.625 = 0.8
    }

    @Test
    fun `diarization error rate should be within acceptable range`() = runTest {
        // This test simulates a conversation and measures DER
        val timestamp = System.currentTimeMillis()
        
        // Create test conversation with known speaker turns
        val conversation = createTestConversation(timestamp)
        
        // Process the conversation
        conversation.forEach { (audioData, expectedSpeaker) ->
            diarization.processAudioData(audioData)
        }
        
        // Calculate DER by comparing final speaker assignments
        val finalAssignments = mutableMapOf<Long, Int>()
        val diarizationFlow = diarization.getDiarizationFlow()
        
        // Collect all diarization results
        val results = withTimeout(1000) {
            diarizationFlow.take(conversation.size).toList()
        }
        
        // Map results to timestamps
        results.forEach { result ->
            finalAssignments[result.timestamp] = result.speakerId
        }
        
        // Calculate error rate
        var errors = 0
        var total = 0
        
        conversation.forEach { (audioData, expectedSpeaker) ->
            val assignedSpeaker = finalAssignments[audioData.timestamp] ?: SpeakerDiarization.SPEAKER_UNKNOWN
            if (expectedSpeaker != SpeakerDiarization.SPEAKER_UNKNOWN && 
                assignedSpeaker != expectedSpeaker) {
                errors++
            }
            if (expectedSpeaker != SpeakerDiarization.SPEAKER_UNKNOWN) {
                total++
            }
        }
        
        val errorRate = if (total > 0) errors.toFloat() / total else 0f
        
        // DER should be ≤18% as per requirements
        assertTrue(errorRate <= 0.18f, "DER should be ≤18%, got ${errorRate * 100}%")
    }

    /**
     * Helper function to create audio data for testing
     */
    private fun createAudioData(
        isVoiceActive: Boolean,
        energy: Float,
        timestamp: Long
    ): SpeakerDiarization.AudioData {
        return SpeakerDiarization.AudioData(
            timestamp = timestamp,
            energyLevel = energy,
            isVoiceActive = isVoiceActive
        )
    }
    
    /**
     * Create a simulated conversation with known speaker turns
     * Returns pairs of (AudioData, ExpectedSpeakerId)
     */
    private fun createTestConversation(baseTimestamp: Long): List<Pair<SpeakerDiarization.AudioData, Int>> {
        val result = mutableListOf<Pair<SpeakerDiarization.AudioData, Int>>()
        var timestamp = baseTimestamp
        
        // Doctor speaking (10 samples)
        repeat(10) {
            val audio = createAudioData(
                isVoiceActive = true,
                energy = 0.5f + (Math.random() * 0.1 - 0.05).toFloat(),
                timestamp = timestamp
            )
            result.add(audio to SpeakerDiarization.SPEAKER_DOCTOR)
            timestamp += 100
        }
        
        // Silence (3 samples)
        repeat(3) {
            val audio = createAudioData(
                isVoiceActive = false,
                energy = 0.05f,
                timestamp = timestamp
            )
            result.add(audio to SpeakerDiarization.SPEAKER_UNKNOWN)
            timestamp += 100
        }
        
        // Patient speaking (12 samples)
        repeat(12) {
            val audio = createAudioData(
                isVoiceActive = true,
                energy = 0.8f + (Math.random() * 0.1 - 0.05).toFloat(),
                timestamp = timestamp
            )
            result.add(audio to SpeakerDiarization.SPEAKER_PATIENT)
            timestamp += 100
        }
        
        // Silence (2 samples)
        repeat(2) {
            val audio = createAudioData(
                isVoiceActive = false,
                energy = 0.05f,
                timestamp = timestamp
            )
            result.add(audio to SpeakerDiarization.SPEAKER_UNKNOWN)
            timestamp += 100
        }
        
        // Doctor speaking again (8 samples)
        repeat(8) {
            val audio = createAudioData(
                isVoiceActive = true,
                energy = 0.55f + (Math.random() * 0.1 - 0.05).toFloat(),
                timestamp = timestamp
            )
            result.add(audio to SpeakerDiarization.SPEAKER_DOCTOR)
            timestamp += 100
        }
        
        return result
    }
}
