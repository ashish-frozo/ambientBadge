package com.frozo.ambientscribe.ai

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import org.mockito.Mockito.mockStatic
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for LLMService - ST-2.6 implementation
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class LLMServiceTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockAssetManager: AssetManager
    
    private lateinit var llmService: LLMService
    private lateinit var systemMockedStatic: MockedStatic<System>
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock asset manager
        whenever(mockContext.assets).thenReturn(mockAssetManager)
        whenever(mockContext.filesDir).thenReturn(File("/tmp/test"))
        
        // Mock schema file
        val schemaJson = """
            {
                "type": "object",
                "properties": {
                    "soap": {"type": "object"},
                    "prescription": {"type": "object"}
                }
            }
        """.trimIndent()
        whenever(mockAssetManager.open("schemas/encounter_note_v1.0.json"))
            .thenReturn(ByteArrayInputStream(schemaJson.toByteArray()))
        
        // Mock model file (empty for testing)
        whenever(mockAssetManager.open("models/llama_1.1b_q4.bin"))
            .thenReturn(ByteArrayInputStream(ByteArray(1024 * 1024))) // 1MB dummy file
        
        // Mock vocab file
        whenever(mockAssetManager.open("vocab/llm_vocab.json"))
            .thenReturn(ByteArrayInputStream("{}".toByteArray()))
        
        // Mock config file
        whenever(mockAssetManager.open("config/llm_config.json"))
            .thenReturn(ByteArrayInputStream("{}".toByteArray()))
        
        // Mock System.loadLibrary calls
        systemMockedStatic = mockStatic(System::class.java)
        systemMockedStatic.`when`<Unit> { System.loadLibrary(org.mockito.ArgumentMatchers.any<String>()) }.then { }
        
        llmService = LLMService()
    }
    
    @After
    fun tearDown() {
        try {
            llmService.cleanup()
        } catch (e: Exception) {
            // Ignore cleanup errors in tests
        }
        systemMockedStatic.close()
    }
    
    @Test
    fun testInitialization() = runTest {
        val result = llmService.initialize(mockContext)
        assertTrue(result, "LLM initialization should succeed")
    }
    
    @Test
    fun testGenerateEncounterNote() = runTest {
        // Initialize first
        llmService.initialize(mockContext)
        
        // Create test audio file
        val audioFile = File.createTempFile("test", ".wav")
        audioFile.writeText("Patient has headache and fever")
        
        val result = llmService.generateEncounterNote(audioFile)
        
        // Verify structure
        assertNotNull(result.soap)
        assertNotNull(result.prescription)
        assertNotNull(result.metadata)
        
        // Verify SOAP note
        assertTrue(result.soap.subjective.isNotEmpty())
        assertTrue(result.soap.objective.isNotEmpty())
        assertTrue(result.soap.assessment.isNotEmpty())
        assertTrue(result.soap.plan.isNotEmpty())
        assertTrue(result.soap.confidence >= 0.0f)
        
        // Verify prescription
        assertTrue(result.prescription.medications.isNotEmpty())
        assertTrue(result.prescription.instructions.isNotEmpty())
        assertFalse(result.prescription.followUp.isBlank())
        assertTrue(result.prescription.confidence >= 0.0f)
        
        // Verify metadata
        assertTrue(result.metadata.speakerTurns > 0)
        assertTrue(result.metadata.totalDuration > 0)
        assertTrue(result.metadata.processingTime > 0)
        assertFalse(result.metadata.modelVersion.isBlank())
        assertFalse(result.metadata.encounterId.isBlank())
        assertFalse(result.metadata.patientId.isBlank())
    }
    
    @Test
    fun testConcurrentAccess() = runTest {
        // Initialize first
        llmService.initialize(mockContext)
        
        // Create test audio file
        val audioFile = File.createTempFile("test", ".wav")
        audioFile.writeText("Patient has headache and fever")
        
        // Run multiple concurrent generations
        val results = coroutineScope {
            (1..5).map {
                async {
                    llmService.generateEncounterNote(audioFile)
                }
            }.map { it.await() }
        }
        
        // Verify all completed
        assertEquals(5, results.size)
        results.forEach { result ->
            assertNotNull(result.soap)
            assertNotNull(result.prescription)
            assertNotNull(result.metadata)
        }
    }
    
    @Test
    fun testCleanup() = runTest {
        // Initialize first
        llmService.initialize(mockContext)
        
        // Clean up
        llmService.cleanup()
        
        // Verify can't generate after cleanup
        val audioFile = File.createTempFile("test", ".wav")
        audioFile.writeText("test")
        
        try {
            llmService.generateEncounterNote(audioFile)
            fail("Should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }
}