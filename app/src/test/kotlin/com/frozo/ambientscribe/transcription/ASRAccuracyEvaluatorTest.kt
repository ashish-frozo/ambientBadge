package com.frozo.ambientscribe.transcription

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ASRAccuracyEvaluatorTest {

    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var accuracyEvaluator: ASRAccuracyEvaluator

    @Before
    fun setUp() {
        accuracyEvaluator = ASRAccuracyEvaluator(mockContext, pilotMode = true)
    }

    @Test
    fun `calculateWER should handle exact matches`() {
        val reference = "the quick brown fox jumps over the lazy dog"
        val hypothesis = "the quick brown fox jumps over the lazy dog"
        
        val wer = accuracyEvaluator.calculateWER(reference, hypothesis)
        assertEquals(0f, wer)
    }

    @Test
    fun `calculateWER should handle substitutions`() {
        val reference = "the quick brown fox jumps over the lazy dog"
        val hypothesis = "the quick brown fox jumps over the crazy dog"
        
        val wer = accuracyEvaluator.calculateWER(reference, hypothesis)
        assertEquals(1f / 9f, wer) // 1 substitution out of 9 words
    }

    @Test
    fun `calculateWER should handle insertions`() {
        val reference = "the quick brown fox jumps over the lazy dog"
        val hypothesis = "the quick brown fox jumps over the very lazy dog"
        
        val wer = accuracyEvaluator.calculateWER(reference, hypothesis)
        assertEquals(1f / 9f, wer) // 1 insertion out of 9 words
    }

    @Test
    fun `calculateWER should handle deletions`() {
        val reference = "the quick brown fox jumps over the lazy dog"
        val hypothesis = "the quick brown fox jumps over lazy dog"
        
        val wer = accuracyEvaluator.calculateWER(reference, hypothesis)
        assertEquals(1f / 9f, wer) // 1 deletion out of 9 words
    }

    @Test
    fun `calculateWER should handle mixed errors`() {
        val reference = "the quick brown fox jumps over the lazy dog"
        val hypothesis = "a quick red fox jumps above the dog"
        
        val wer = accuracyEvaluator.calculateWER(reference, hypothesis)
        // 3 substitutions (the→a, brown→red, over→above) + 2 deletions (lazy, the) = 5 errors
        assertEquals(5f / 9f, wer)
    }

    @Test
    fun `calculateWER should handle empty strings`() {
        // Empty reference, non-empty hypothesis
        assertEquals(1f, accuracyEvaluator.calculateWER("", "test"))
        
        // Non-empty reference, empty hypothesis
        assertEquals(1f, accuracyEvaluator.calculateWER("test", ""))
        
        // Both empty
        assertEquals(0f, accuracyEvaluator.calculateWER("", ""))
    }

    @Test
    fun `calculateMedF1Score should handle medical terms`() {
        // Reference with medical terms
        val reference = "Patient has fever and headache with a history of diabetes"
        
        // Perfect match
        val hypothesis1 = "Patient has fever and headache with a history of diabetes"
        assertEquals(1f, accuracyEvaluator.calculateMedF1Score(reference, hypothesis1))
        
        // Partial match (missing one term)
        val hypothesis2 = "Patient has fever with a history of diabetes"
        val f1Score2 = accuracyEvaluator.calculateMedF1Score(reference, hypothesis2)
        assertTrue(f1Score2 < 1f && f1Score2 > 0f)
        
        // No medical terms match
        val hypothesis3 = "Patient is feeling well today"
        assertEquals(0f, accuracyEvaluator.calculateMedF1Score(reference, hypothesis3))
    }

    @Test
    fun `evaluateAndLogAccuracy should calculate and return metrics`() = runTest {
        val reference = "Patient has fever and headache with a history of diabetes"
        val hypothesis = "Patient has fever and headache with history of diabetes"
        
        val result = accuracyEvaluator.evaluateAndLogAccuracy(
            reference = reference,
            hypothesis = hypothesis,
            sampleId = "test-sample",
            durationMs = 1000L
        )
        
        assertTrue(result.isSuccess)
        val metrics = result.getOrNull()!!
        
        // Check that all required metrics are present
        assertTrue(metrics.containsKey("wer"))
        assertTrue(metrics.containsKey("med_f1"))
        assertTrue(metrics.containsKey("confidence"))
        
        // WER should be low for this example (just one word missing)
        assertTrue(metrics["wer"]!! < 0.2f)
        
        // MedF1 should be high as all medical terms are present
        assertTrue(metrics["med_f1"]!! > 0.8f)
        
        // Confidence should be high
        assertTrue(metrics["confidence"]!! > 0.8f)
    }
}
