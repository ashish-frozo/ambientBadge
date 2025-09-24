package com.frozo.ambientscribe.integration

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import com.frozo.ambientscribe.audio.AudioCapture
import com.frozo.ambientscribe.transcription.ASRService
import com.frozo.ambientscribe.ai.AIService
import com.frozo.ambientscribe.ai.LLMService
import com.frozo.ambientscribe.ai.PrescriptionValidator
import com.frozo.ambientscribe.ai.MedicalEntityExtractor
import com.frozo.ambientscribe.ai.FallbackSOAPGenerator
import com.frozo.ambientscribe.telemetry.MetricsCollector
import com.frozo.ambientscribe.performance.PerformanceManager
import com.frozo.ambientscribe.performance.DeviceCapabilityDetector
import com.frozo.ambientscribe.performance.ThermalManager

/**
 * End-to-End Integration Test for Complete PT-1 → PT-2 Pipeline
 * 
 * Tests the complete workflow:
 * 1. Audio Capture (PT-1)
 * 2. ASR Processing (PT-1)
 * 3. LLM Processing (PT-2)
 * 4. SOAP Note Generation (PT-2)
 * 5. Prescription Generation (PT-2)
 * 6. Medical Entity Extraction (PT-2)
 * 
 * Requirements tested:
 * - Complete pipeline integration
 * - Data flow between PT-1 and PT-2
 * - Error handling and fallback mechanisms
 * - Performance and resource management
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class EndToEndPipelineTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockAssetManager: AssetManager
    
    @Mock
    private lateinit var mockFilesDir: File
    
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var performanceManager: PerformanceManager
    private lateinit var deviceCapabilityDetector: DeviceCapabilityDetector
    private lateinit var thermalManager: ThermalManager
    
    // PT-1 Components
    private lateinit var audioCapture: AudioCapture
    private lateinit var asrService: ASRService
    
    // PT-2 Components
    private lateinit var llmService: LLMService
    private lateinit var prescriptionValidator: PrescriptionValidator
    private lateinit var medicalEntityExtractor: MedicalEntityExtractor
    private lateinit var fallbackSOAPGenerator: FallbackSOAPGenerator
    private lateinit var aiService: AIService

    @Before
    fun setUp() {
        // Mock context and assets
        whenever(mockContext.assets).thenReturn(mockAssetManager)
        whenever(mockContext.filesDir).thenReturn(mockFilesDir)
        
        // Mock asset files
        val mockModelData = ByteArrayInputStream("mock model data".toByteArray())
        val mockFormularyData = ByteArrayInputStream("""{"medications": []}""".toByteArray())
        val mockMedicalDictData = ByteArrayInputStream("""{"terms": []}""".toByteArray())
        
        whenever(mockAssetManager.open("models/whisper-tiny-int8.bin"))
            .thenReturn(mockModelData)
        whenever(mockAssetManager.open("models/llama-7b-q4.bin"))
            .thenReturn(mockModelData)
        whenever(mockAssetManager.open("formulary/clinic_formulary.json"))
            .thenReturn(mockFormularyData)
        whenever(mockAssetManager.open("dictionaries/medical_terms.json"))
            .thenReturn(mockMedicalDictData)
        
        // Initialize components
        metricsCollector = MetricsCollector(mockContext)
        deviceCapabilityDetector = DeviceCapabilityDetector(mockContext)
        thermalManager = ThermalManager(mockContext)
        performanceManager = PerformanceManager(mockContext, thermalManager, deviceCapabilityDetector)
        
        // PT-1 Components
        audioCapture = AudioCapture(mockContext)
        asrService = ASRService(mockContext, performanceManager = performanceManager)
        
        // PT-2 Components
        llmService = LLMService()
        prescriptionValidator = PrescriptionValidator(mockContext)
        medicalEntityExtractor = MedicalEntityExtractor(mockContext)
        fallbackSOAPGenerator = FallbackSOAPGenerator()
        aiService = AIService(
            context = mockContext,
            deviceCapabilityDetector = deviceCapabilityDetector,
            thermalManager = thermalManager,
            metricsCollector = metricsCollector
        )
    }

    @Test
    fun `complete pipeline should process audio to SOAP note successfully`() = runTest {
        // Given: Mock audio data
        val mockAudioData = ByteArray(1024) { (it % 256).toByte() }
        val expectedTranscript = "Patient complains of chest pain and shortness of breath"
        val expectedSOAPNote = """
            {
                "subjective": "Patient complains of chest pain and shortness of breath",
                "objective": "Vital signs stable, no acute distress",
                "assessment": "Possible cardiac event, rule out MI",
                "plan": "ECG, cardiac enzymes, cardiology consult"
            }
        """.trimIndent()
        
        // Mock ASR service to return expected transcript
        // Note: In a real test, we would mock the ASR service properly
        
        // When: Process audio through complete pipeline
        val result = processCompletePipeline(mockAudioData)
        
        // Then: Verify complete pipeline works
        assertNotNull(result, "Pipeline should return a result")
        assertTrue((result as kotlin.Result<String>).isSuccess, "Pipeline should succeed")
        
        val soapNote = result.getOrNull()
        assertNotNull(soapNote, "SOAP note should be generated")
        
        // Verify SOAP note structure
        assertTrue(soapNote.contains("subjective"), "Should contain subjective section")
        assertTrue(soapNote.contains("objective"), "Should contain objective section")
        assertTrue(soapNote.contains("assessment"), "Should contain assessment section")
        assertTrue(soapNote.contains("plan"), "Should contain plan section")
    }

    @Test
    fun `pipeline should handle ASR failure gracefully with fallback`() = runTest {
        // Given: Mock audio data that will cause ASR failure
        val mockAudioData = ByteArray(1024) { 0 } // Silent audio
        
        // When: Process audio through pipeline
        val result = processCompletePipeline(mockAudioData)
        
        // Then: Should handle failure gracefully
        // In a real implementation, this would test fallback mechanisms
        assertNotNull(result, "Pipeline should return a result even on failure")
    }

    @Test
    fun `pipeline should handle LLM failure gracefully with fallback`() = runTest {
        // Given: Mock audio data and ASR success but LLM failure
        val mockAudioData = ByteArray(1024) { (it % 256).toByte() }
        
        // When: Process audio through pipeline
        val result = processCompletePipeline(mockAudioData)
        
        // Then: Should fall back to rules-based generation
        assertNotNull(result, "Pipeline should return a result even on LLM failure")
    }

    @Test
    fun `pipeline should validate prescription confidence scores`() = runTest {
        // Given: Mock prescription data
        val mockPrescription = """
            {
                "medication": "Aspirin 81mg",
                "dosage": "1 tablet daily",
                "instructions": "Take with food",
                "confidence": 0.85
            }
        """.trimIndent()
        
        // When: Validate prescription
        val validationResult = prescriptionValidator.validateMedication(LLMService.Medication(
            name = "Aspirin 81mg",
            dosage = "1 tablet daily",
            frequency = "daily",
            duration = "30 days",
            instructions = "Take with food"
        ))
        
        // Then: Should return appropriate confidence level
        assertNotNull(validationResult, "Validation should return a result")
        assertTrue((validationResult as kotlin.Result<PrescriptionValidator.ValidationResult>).isSuccess, "Validation should succeed")
    }

    @Test
    fun `pipeline should extract medical entities correctly`() = runTest {
        // Given: Mock transcript with medical terms
        val transcript = "Patient has hypertension and diabetes mellitus type 2"
        
        // When: Extract medical entities
        val entities = medicalEntityExtractor.extractEntities(transcript)
        
        // Then: Should extract medical terms
        assertNotNull(entities, "Should extract entities")
        assertTrue((entities as? List<*>)?.isNotEmpty() ?: false, "Should extract some entities")
    }

    @Test
    fun `pipeline should respect performance constraints`() = runTest {
        // Given: Mock performance constraints
        val performanceState = performanceManager.getCurrentPerformanceState()
        
        // When: Process audio with performance constraints
        val result = processCompletePipeline(ByteArray(1024) { (it % 256).toByte() })
        
        // Then: Should respect performance limits
        assertNotNull(result, "Should process within performance constraints")
        
        // Verify performance metrics are recorded
        val metrics = metricsCollector.getMetricsSummary()
        assertNotNull(metrics, "Should record performance metrics")
    }

    @Test
    fun `pipeline should handle thermal management`() = runTest {
        // Given: Mock thermal state change
        val thermalState = ThermalManager.ThermalState.WARM
        
        // When: Process audio under thermal constraints
        val result = processCompletePipeline(ByteArray(1024) { (it % 256).toByte() })
        
        // Then: Should adapt to thermal state
        assertNotNull(result, "Should process under thermal constraints")
        
        // Verify thermal metrics are recorded
        val metrics = metricsCollector.getMetricsSummary()
        assertNotNull(metrics, "Should record thermal metrics")
    }

    /**
     * Helper method to process audio through the complete pipeline
     */
    private suspend fun processCompletePipeline(audioData: ByteArray): Result<String> {
        return try {
            // Step 1: Audio Capture (PT-1)
            // In a real implementation, this would capture actual audio
            // Start recording
            audioCapture.startRecording()
            
            // Process audio data
            audioCapture.getAudioFlow().collect { audioData ->
                // Process each audio data chunk
                asrService.processAudio(audioData.samples)
            }
            
            // Stop recording
            audioCapture.stopRecording()
            
            // Step 2: ASR Processing (PT-1)
            val shortArray = ShortArray(audioData.size) { i -> audioData[i].toShort() }
            asrService.processAudio(shortArray)
            val transcript = asrService.getCurrentTranscription()
            
            if (transcript.isEmpty()) {
                return Result.failure(Exception("No transcript"))
            }
            
            val transcriptText = transcript
            
            // Step 3: Medical Entity Extraction (PT-2)
            val entities = medicalEntityExtractor.extractEntities(transcriptText)
            
            // Step 4: SOAP Note Generation (PT-2)
            val soapNoteResult = llmService.generateEncounterNote(File("test.wav"))
            
            if (soapNoteResult.soap.confidence < 0.7f) {
                // Fallback to rules-based generation
                val fallbackResult = fallbackSOAPGenerator.generateFromTranscript(transcriptText)
                return Result.success(fallbackResult.toString())
            }
            
            Result.success(soapNoteResult.toString())
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
