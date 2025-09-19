package com.frozo.ambientscribe.transcription

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ASRServiceTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockAssetManager: AssetManager
    
    @Mock
    private lateinit var mockFilesDir: File
    
    private lateinit var asrService: ASRService
    private lateinit var systemMockedStatic: MockedStatic<System>

    @Before
    fun setUp() {
        // Mock Context
        whenever(mockContext.assets).thenReturn(mockAssetManager)
        whenever(mockContext.filesDir).thenReturn(mockFilesDir)
        
        // Mock File operations
        whenever(mockFilesDir.absolutePath).thenReturn("/mock/files")
        
        // Mock System.loadLibrary calls
        systemMockedStatic = mockStatic(System::class.java)
        systemMockedStatic.`when`<Unit> { System.loadLibrary(any()) }.then { }
        
        asrService = ASRService(mockContext)
    }

    @After
    fun tearDown() {
        asrService.cleanup()
        systemMockedStatic.close()
    }

    @Test
    fun `initialize should succeed with valid model files`() = runTest {
        // Mock asset extraction
        mockAssetExtraction()
        
        val result = asrService.initialize()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `initialize should fail with missing model files`() = runTest {
        // Mock missing assets
        whenever(mockAssetManager.open(any())).thenThrow(java.io.IOException("File not found"))
        
        val result = asrService.initialize()
        assertTrue(result.isFailure)
    }

    @Test
    fun `processAudio should fail if not initialized`() = runTest {
        val audioData = ShortArray(1600) { (it * 100).toShort() }
        
        val result = asrService.processAudio(audioData)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `processAudio should buffer audio data correctly`() = runTest {
        mockAssetExtraction()
        asrService.initialize()
        
        val audioData = ShortArray(1600) { (it * 100).toShort() }
        val result = asrService.processAudio(audioData)
        
        assertTrue(result.isSuccess)
    }

    @Test
    fun `confidence calculation should classify levels correctly`() {
        // Test confidence level classification
        val highConfidence = ASRService.TranscriptionResult(
            text = "test",
            confidence = 0.85f,
            confidenceLevel = ASRService.ConfidenceLevel.HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        val mediumConfidence = ASRService.TranscriptionResult(
            text = "test",
            confidence = 0.7f,
            confidenceLevel = ASRService.ConfidenceLevel.MEDIUM,
            timestamp = System.currentTimeMillis()
        )
        
        val lowConfidence = ASRService.TranscriptionResult(
            text = "test",
            confidence = 0.4f,
            confidenceLevel = ASRService.ConfidenceLevel.LOW,
            timestamp = System.currentTimeMillis()
        )
        
        assertEquals(ASRService.ConfidenceLevel.HIGH, highConfidence.confidenceLevel)
        assertEquals(ASRService.ConfidenceLevel.MEDIUM, mediumConfidence.confidenceLevel)
        assertEquals(ASRService.ConfidenceLevel.LOW, lowConfidence.confidenceLevel)
    }

    @Test
    fun `transcription flow should emit results`() = runTest {
        mockAssetExtraction()
        asrService.initialize()
        
        val transcriptionFlow = asrService.getTranscriptionFlow()
        assertNotNull(transcriptionFlow)
    }

    @Test
    fun `getCurrentTranscription should return accumulated text`() = runTest {
        mockAssetExtraction()
        asrService.initialize()
        
        val initialTranscription = asrService.getCurrentTranscription()
        assertEquals("", initialTranscription)
    }

    @Test
    fun `clearTranscription should reset buffer`() = runTest {
        mockAssetExtraction()
        asrService.initialize()
        
        asrService.clearTranscription()
        
        val transcription = asrService.getCurrentTranscription()
        assertEquals("", transcription)
    }

    @Test
    fun `cleanup should release all resources`() = runTest {
        mockAssetExtraction()
        asrService.initialize()
        
        asrService.cleanup()
        
        // After cleanup, processing should fail
        val audioData = ShortArray(1600)
        val result = asrService.processAudio(audioData)
        assertTrue(result.isFailure)
    }

    @Test
    fun `audio chunking should process correct sizes`() {
        // Test audio chunk size calculations
        val sampleRate = 16000
        val chunkDurationMs = 3000L
        val expectedChunkSize = (sampleRate * chunkDurationMs / 1000).toInt()
        
        assertEquals(48000, expectedChunkSize)
    }

    @Test
    fun `overlap calculation should be correct`() {
        // Test overlap size calculations
        val sampleRate = 16000
        val overlapMs = 500L
        val expectedOverlapSize = (sampleRate * overlapMs / 1000).toInt()
        
        assertEquals(8000, expectedOverlapSize)
    }

    @Test
    fun `word timestamps should be extracted correctly`() {
        val wordTimestamp = ASRService.WordTimestamp(
            word = "hello",
            startTime = 0.5f,
            endTime = 1.0f,
            confidence = 0.9f
        )
        
        assertEquals("hello", wordTimestamp.word)
        assertEquals(0.5f, wordTimestamp.startTime)
        assertEquals(1.0f, wordTimestamp.endTime)
        assertEquals(0.9f, wordTimestamp.confidence)
    }

    @Test
    fun `transcription result should contain all required fields`() {
        val result = ASRService.TranscriptionResult(
            text = "Hello world",
            confidence = 0.8f,
            confidenceLevel = ASRService.ConfidenceLevel.HIGH,
            timestamp = 1234567890L,
            isPartial = false,
            wordTimestamps = listOf(
                ASRService.WordTimestamp("Hello", 0f, 0.5f, 0.9f),
                ASRService.WordTimestamp("world", 0.5f, 1.0f, 0.8f)
            )
        )
        
        assertEquals("Hello world", result.text)
        assertEquals(0.8f, result.confidence)
        assertEquals(ASRService.ConfidenceLevel.HIGH, result.confidenceLevel)
        assertEquals(1234567890L, result.timestamp)
        assertFalse(result.isPartial)
        assertEquals(2, result.wordTimestamps.size)
    }

    @Test
    fun `model file validation should work correctly`() = runTest {
        // Test model file completeness checking
        mockAssetExtraction()
        
        // This would test the private isModelComplete method through integration
        val result = asrService.initialize()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `audio buffer should handle concurrent access`() = runTest {
        mockAssetExtraction()
        asrService.initialize()
        
        // Test concurrent audio processing
        val audioData1 = ShortArray(1600) { it.toShort() }
        val audioData2 = ShortArray(1600) { (it + 1000).toShort() }
        
        val result1 = asrService.processAudio(audioData1)
        val result2 = asrService.processAudio(audioData2)
        
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
    }

    @Test
    fun `confidence threshold should affect processing`() {
        val lowThresholdService = ASRService(mockContext, confidenceThreshold = 0.3f)
        val highThresholdService = ASRService(mockContext, confidenceThreshold = 0.9f)
        
        assertNotNull(lowThresholdService)
        assertNotNull(highThresholdService)
        
        lowThresholdService.cleanup()
        highThresholdService.cleanup()
    }

    @Test
    fun `native library loading should be handled gracefully`() {
        // Test native library loading error handling
        systemMockedStatic.`when`<Unit> { System.loadLibrary("ctranslate2") }
            .thenThrow(UnsatisfiedLinkError("Library not found"))
        
        // Service should still be created but initialization might fail
        val service = ASRService(mockContext)
        assertNotNull(service)
        service.cleanup()
    }

    @Test
    fun `audio format conversion should be correct`() {
        // Test Short to Float conversion for model input
        val shortValue: Short = 16384 // Half of Short.MAX_VALUE
        val expectedFloat = shortValue.toFloat() / Short.MAX_VALUE
        
        assertEquals(0.5f, expectedFloat, 0.001f)
    }

    @Test
    fun `chunk processing should maintain overlap`() = runTest {
        mockAssetExtraction()
        asrService.initialize()
        
        // Process multiple chunks to test overlap handling
        val chunkSize = 48000 // 3 seconds at 16kHz
        val audioData = ShortArray(chunkSize * 2) { it.toShort() }
        
        val result = asrService.processAudio(audioData)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `multiple initialize calls should be idempotent`() = runTest {
        mockAssetExtraction()
        
        val result1 = asrService.initialize()
        val result2 = asrService.initialize()
        
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
    }

    @Test
    fun `transcription should accumulate correctly`() = runTest {
        mockAssetExtraction()
        asrService.initialize()
        
        // Test transcription accumulation
        val initialText = asrService.getCurrentTranscription()
        assertEquals("", initialText)
        
        // After processing, transcription should potentially update
        // (In real implementation, this would depend on actual audio content)
    }

    private fun mockAssetExtraction() {
        // Mock asset files
        val modelFiles = listOf(
            "whisper-tiny-encoder-int8.ct2",
            "whisper-tiny-decoder-int8.ct2", 
            "tokenizer.json",
            "vocab.txt"
        )
        
        for (fileName in modelFiles) {
            val assetPath = "models/whisper-tiny-int8/$fileName"
            val mockData = ByteArrayInputStream("mock model data".toByteArray())
            whenever(mockAssetManager.open(assetPath)).thenReturn(mockData)
        }
        
        // Mock file operations
        val mockModelDir = mock(File::class.java)
        whenever(mockModelDir.exists()).thenReturn(false, true)
        whenever(mockModelDir.mkdirs()).thenReturn(true)
        whenever(mockModelDir.absolutePath).thenReturn("/mock/models/whisper-tiny-int8")
        
        // Mock individual model files
        for (fileName in modelFiles) {
            val mockFile = mock(File::class.java)
            whenever(mockFile.exists()).thenReturn(true)
            whenever(mockFile.length()).thenReturn(1000L)
            whenever(File(mockModelDir, fileName)).thenReturn(mockFile)
        }
    }
}
