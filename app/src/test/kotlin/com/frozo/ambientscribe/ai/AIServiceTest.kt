package com.frozo.ambientscribe.ai

import android.content.Context
import android.content.res.AssetManager
import com.frozo.ambientscribe.performance.DeviceCapabilityDetector
import com.frozo.ambientscribe.performance.ThermalManager
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for AIService - Main coordinator for PT-2
 * 
 * Requirements tested:
 * - FR-4: Generate SOAP notes and prescriptions using local LLM
 * - ST-2.1 through ST-2.12: All PT-2 subtasks coordination
 * - End-to-end processing pipeline
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AIServiceTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockAssetManager: AssetManager
    
    @Mock
    private lateinit var mockDeviceCapabilityDetector: DeviceCapabilityDetector
    
    @Mock
    private lateinit var mockThermalManager: ThermalManager
    
    @Mock
    private lateinit var mockMetricsCollector: MetricsCollector
    
    private lateinit var aiService: AIService
    private lateinit var systemMockedStatic: MockedStatic<System>

    @Before
    fun setUp() {
        // Mock Context and AssetManager
        whenever(mockContext.assets).thenReturn(mockAssetManager)
        whenever(mockContext.filesDir).thenReturn(mock())
        
        // Mock device capabilities
        whenever(mockDeviceCapabilityDetector.getDeviceTier())
            .thenReturn(DeviceCapabilityDetector.DeviceTier.TIER_B)
        whenever(mockDeviceCapabilityDetector.getAvailableMemoryMB()).thenReturn(4096L)
        
        // Mock thermal manager
        whenever(mockThermalManager.getCurrentThermalState())
            .thenReturn(ThermalManager.ThermalState.NORMAL)
        
        // Mock asset files
        mockAssetFiles()
        
        // Mock System.loadLibrary calls
        systemMockedStatic = mockStatic(System::class.java)
        systemMockedStatic.`when`<Unit> { System.loadLibrary(any()) }.then { }
        
        aiService = AIService(
            mockContext, 
            mockDeviceCapabilityDetector, 
            mockThermalManager, 
            mockMetricsCollector
        )
    }

    @After
    fun tearDown() {
        try {
            aiService.cleanup()
        } catch (e: Exception) {
            // Ignore cleanup errors in tests
        }
        systemMockedStatic.close()
    }

    @Test
    fun `initialize should succeed with all components`() = runTest {
        val result = aiService.initialize()
        assertTrue(result)
    }

    @Test
    fun `initialize should fail when LLM initialization fails`() = runTest {
        // Mock LLM initialization failure by not providing model file
        whenever(mockAssetManager.open("models/llama-1.3b-medical-q4.gguf"))
            .thenThrow(java.io.IOException("Model not found"))
        
        val result = aiService.initialize()
        assertFalse(result)
    }

    @Test
    fun `generateEncounterNote should fail if not initialized`() = runTest {
        val result = aiService.generateEncounterNote(mock(), 2, 60000L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `generateEncounterNote should complete full pipeline successfully`() = runTest {
        // Initialize service
        aiService.initialize()
        
        val audioFile = mock<java.io.File>()
        val result = aiService.generateEncounterNote(audioFile, 8, 180000L)
        
        assertTrue(result.isSuccess)
        
        val encounterNote = result.getOrNull()!!
        
        // Verify encounter note structure
        assertNotNull(encounterNote)
        assertTrue(encounterNote.soap.subjective.isNotEmpty())
        assertTrue(encounterNote.soap.objective.isNotEmpty())
        assertTrue(encounterNote.soap.assessment.isNotEmpty())
        assertTrue(encounterNote.soap.plan.isNotEmpty())
        
        // Verify prescription structure
        assertTrue(encounterNote.prescription.medications.isNotEmpty())
        
        // Verify metadata
        assertEquals(8, encounterNote.metadata.speakerTurns)
        assertEquals(180000L, encounterNote.metadata.totalDuration)
    }

    @Test
    fun `generateEncounterNote should record comprehensive metrics`() = runTest {
        aiService.initialize()
        
        val audioFile = mock<java.io.File>()
        val result = aiService.generateEncounterNote(audioFile, 2, 60000L)
        
        assertTrue(result.isSuccess)
        
        // Verify metrics were recorded
        verify(mockMetricsCollector).recordEvent(eq("ai_processing_completed"), any())
        verify(mockMetricsCollector).recordMetric(eq("soap_confidence"), any())
        verify(mockMetricsCollector).recordMetric(eq("prescription_confidence"), any())
        verify(mockMetricsCollector).recordMetric(eq("processing_time_ms"), any())
    }

    @Test
    fun `generateEncounterNote should handle thermal throttling`() = runTest {
        // Mock critical thermal state
        whenever(mockThermalManager.getCurrentThermalState())
            .thenReturn(ThermalManager.ThermalState.CRITICAL)
        
        aiService.initialize()
        
        val audioFile = mock<java.io.File>()
        val result = aiService.generateEncounterNote(audioFile, 2, 60000L)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("thermal") == true)
    }

    @Test
    fun `cleanup should reset all state`() = runTest {
        aiService.initialize()
        aiService.cleanup()
        
        // Should fail to process after cleanup
        val audioFile = mock<java.io.File>()
        val result = aiService.generateEncounterNote(audioFile, 1, 1000L)
        assertTrue(result.isFailure)
    }

    @Test
    fun `generateEncounterNote should meet performance requirements`() = runTest {
        aiService.initialize()
        
        val audioFile = mock<java.io.File>()
        val startTime = System.currentTimeMillis()
        val result = aiService.generateEncounterNote(audioFile, 4, 120000L)
        val endTime = System.currentTimeMillis()
        
        assertTrue(result.isSuccess)
        
        val processingTime = endTime - startTime
        
        // Should meet performance target (adjust based on requirements)
        assertTrue(processingTime < 30000L) // Less than 30 seconds for mock
        
        val encounterNote = result.getOrNull()!!
        assertTrue(encounterNote.metadata.processingTime > 0)
    }

    private fun mockAssetFiles() {
        // Mock LLM model file
        val mockModelData = ByteArrayInputStream("mock llama model".toByteArray())
        whenever(mockAssetManager.open("models/llama-1.3b-medical-q4.gguf"))
            .thenReturn(mockModelData)
        
        // Mock schema file
        val mockSchemaData = ByteArrayInputStream("""{"type": "object"}""".toByteArray())
        whenever(mockAssetManager.open("schemas/encounter_note_v1.0.json"))
            .thenReturn(mockSchemaData)
        
        // Mock formulary files
        val mockFormularyData = ByteArrayInputStream("""{"medications": []}""".toByteArray())
        whenever(mockAssetManager.open("formulary/clinic_formulary.json"))
            .thenReturn(mockFormularyData)
        
        val mockMedicationData = ByteArrayInputStream("paracetamol\nibuprofen".toByteArray())
        whenever(mockAssetManager.open("formulary/common_medications.txt"))
            .thenReturn(mockMedicationData)
        
        val mockMedicalTermsData = ByteArrayInputStream("fever\ncough".toByteArray())
        whenever(mockAssetManager.open("medical_terms/common_terms.txt"))
            .thenReturn(mockMedicalTermsData)
    }
}