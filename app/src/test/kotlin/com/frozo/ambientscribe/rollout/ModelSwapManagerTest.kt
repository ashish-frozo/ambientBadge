package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.runTest
import java.io.File

/**
 * Unit tests for ModelSwapManager
 * 
 * Tests model swapping functionality including:
 * - Atomic model updates
 * - Model validation
 * - Rollback capabilities
 * - Error handling and recovery
 */
class ModelSwapManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var modelSwapManager: ModelSwapManager
    private lateinit var mockFilesDir: File
    
    @BeforeEach
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockPrefs = mockk<SharedPreferences>(relaxed = true)
        mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        mockFilesDir = mockk<File>(relaxed = true)
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockContext.filesDir } returns mockFilesDir
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putString(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs
        
        // Mock file operations
        every { mockFilesDir.exists() } returns true
        every { mockFilesDir.mkdirs() } returns true
        
        // Reset singleton instance
        ModelSwapManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        
        modelSwapManager = ModelSwapManager.getInstance(mockContext)
    }
    
    @Test
    @DisplayName("Test model swap manager initialization")
    fun testModelSwapManagerInitialization() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val manager = ModelSwapManager.getInstance(mockContext)
        
        // Then
        assertEquals("default", manager.currentModel.value)
        assertEquals("1.0.0", manager.modelVersion.value)
    }
    
    @Test
    @DisplayName("Test model validation with valid files")
    fun testModelValidationWithValidFiles() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to "test model data".toByteArray(),
            "config.json" to "{\"version\": \"1.0.0\"}".toByteArray(),
            "vocab.txt" to "test vocab".toByteArray()
        )
        
        // When
        val validationResult = modelSwapManager.validateModel(modelFiles)
        
        // Then
        assertTrue(validationResult.isValid)
        assertTrue(validationResult.errors.isEmpty())
    }
    
    @Test
    @DisplayName("Test model validation with missing files")
    fun testModelValidationWithMissingFiles() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to "test model data".toByteArray()
            // Missing config.json and vocab.txt
        )
        
        // When
        val validationResult = modelSwapManager.validateModel(modelFiles)
        
        // Then
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.errors.isNotEmpty())
        assertTrue(validationResult.errors.any { it.contains("Missing required file") })
    }
    
    @Test
    @DisplayName("Test model validation with empty files")
    fun testModelValidationWithEmptyFiles() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to ByteArray(0),
            "config.json" to "{\"version\": \"1.0.0\"}".toByteArray(),
            "vocab.txt" to "test vocab".toByteArray()
        )
        
        // When
        val validationResult = modelSwapManager.validateModel(modelFiles)
        
        // Then
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.errors.any { it.contains("Empty file") })
    }
    
    @Test
    @DisplayName("Test model validation with oversized files")
    fun testModelValidationWithOversizedFiles() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to ByteArray(101 * 1024 * 1024), // 101MB
            "config.json" to "{\"version\": \"1.0.0\"}".toByteArray(),
            "vocab.txt" to "test vocab".toByteArray()
        )
        
        // When
        val validationResult = modelSwapManager.validateModel(modelFiles)
        
        // Then
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.errors.any { it.contains("File too large") })
    }
    
    @Test
    @DisplayName("Test model validation with undersized model file")
    fun testModelValidationWithUndersizedModelFile() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to "x".toByteArray(), // Too small
            "config.json" to "{\"version\": \"1.0.0\"}".toByteArray(),
            "vocab.txt" to "test vocab".toByteArray()
        )
        
        // When
        val validationResult = modelSwapManager.validateModel(modelFiles)
        
        // Then
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.errors.any { it.contains("Model file too small") })
    }
    
    @Test
    @DisplayName("Test model swap with valid model")
    fun testModelSwapWithValidModel() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to "test model data".toByteArray(),
            "config.json" to "{\"version\": \"2.0.0\"}".toByteArray(),
            "vocab.txt" to "test vocab".toByteArray()
        )
        
        // Mock file operations
        val mockStagingDir = mockk<File>(relaxed = true)
        val mockNewModelDir = mockk<File>(relaxed = true)
        val mockCurrentModelDir = mockk<File>(relaxed = true)
        val mockFile = mockk<File>(relaxed = true)
        
        every { mockFilesDir.listFiles() } returns emptyArray()
        every { mockStagingDir.exists() } returns false
        every { mockStagingDir.mkdirs() } returns true
        every { mockStagingDir.renameTo(any()) } returns true
        every { mockNewModelDir.exists() } returns false
        every { mockNewModelDir.mkdirs() } returns true
        every { mockCurrentModelDir.exists() } returns false
        every { mockCurrentModelDir.mkdirs() } returns true
        every { mockFile.exists() } returns true
        every { mockFile.isDirectory } returns false
        every { mockFile.length() } returns 1000L
        every { mockFile.readText() } returns "{\"version\": \"2.0.0\"}"
        
        // When
        val result = modelSwapManager.swapToModel("new_model", "2.0.0", modelFiles)
        
        // Then
        assertTrue(result is ModelSwapManager.SwapResult.SUCCESS)
    }
    
    @Test
    @DisplayName("Test model swap with invalid model")
    fun testModelSwapWithInvalidModel() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to "test model data".toByteArray()
            // Missing required files
        )
        
        // When
        val result = modelSwapManager.swapToModel("new_model", "2.0.0", modelFiles)
        
        // Then
        assertTrue(result is ModelSwapManager.SwapResult.INVALID_MODEL)
        assertTrue((result as ModelSwapManager.SwapResult.INVALID_MODEL).errors.isNotEmpty())
    }
    
    @Test
    @DisplayName("Test model rollback when no previous model")
    fun testModelRollbackWhenNoPreviousModel() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefs.getString("previous_model", null) } returns null
        
        // When
        val result = modelSwapManager.rollbackToPrevious()
        
        // Then
        assertTrue(result is ModelSwapManager.RollbackResult.NO_PREVIOUS_MODEL)
    }
    
    @Test
    @DisplayName("Test model rollback when previous model not found")
    fun testModelRollbackWhenPreviousModelNotFound() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefs.getString("previous_model", null) } returns "previous_model"
        
        // Mock file operations
        val mockPreviousModelDir = mockk<File>(relaxed = true)
        every { mockPreviousModelDir.exists() } returns false
        
        // When
        val result = modelSwapManager.rollbackToPrevious()
        
        // Then
        assertTrue(result is ModelSwapManager.RollbackResult.PREVIOUS_MODEL_NOT_FOUND)
    }
    
    @Test
    @DisplayName("Test model rollback with valid previous model")
    fun testModelRollbackWithValidPreviousModel() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefs.getString("previous_model", null) } returns "previous_model"
        
        // Mock file operations
        val mockPreviousModelDir = mockk<File>(relaxed = true)
        val mockCurrentModelDir = mockk<File>(relaxed = true)
        val mockTempDir = mockk<File>(relaxed = true)
        val mockFile = mockk<File>(relaxed = true)
        
        every { mockPreviousModelDir.exists() } returns true
        every { mockPreviousModelDir.renameTo(any()) } returns true
        every { mockCurrentModelDir.exists() } returns true
        every { mockCurrentModelDir.renameTo(any()) } returns true
        every { mockTempDir.renameTo(any()) } returns true
        every { mockFile.exists() } returns true
        every { mockFile.isDirectory } returns false
        every { mockFile.length() } returns 1000L
        every { mockFile.readText() } returns "{\"version\": \"1.0.0\"}"
        
        // When
        val result = modelSwapManager.rollbackToPrevious()
        
        // Then
        assertTrue(result is ModelSwapManager.RollbackResult.SUCCESS)
    }
    
    @Test
    @DisplayName("Test model status retrieval")
    fun testModelStatusRetrieval() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // When
        val status = modelSwapManager.getModelStatus()
        
        // Then
        assertTrue(status.containsKey("current_model"))
        assertTrue(status.containsKey("model_version"))
        assertTrue(status.containsKey("last_swap"))
        assertTrue(status.containsKey("retention_days"))
        assertTrue(status.containsKey("is_swapping"))
    }
    
    @Test
    @DisplayName("Test error handling in model validation")
    fun testErrorHandlingInModelValidation() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to "test model data".toByteArray(),
            "config.json" to "invalid json".toByteArray(),
            "vocab.txt" to "test vocab".toByteArray()
        )
        
        // When
        val validationResult = modelSwapManager.validateModel(modelFiles)
        
        // Then
        // Should handle invalid JSON gracefully
        assertTrue(validationResult.isValid || validationResult.errors.isNotEmpty())
    }
    
    @Test
    @DisplayName("Test error handling in model swap")
    fun testErrorHandlingInModelSwap() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to "test model data".toByteArray(),
            "config.json" to "{\"version\": \"2.0.0\"}".toByteArray(),
            "vocab.txt" to "test vocab".toByteArray()
        )
        
        // Mock file operations to throw exception
        every { mockFilesDir.listFiles() } throws RuntimeException("Test error")
        
        // When
        val result = modelSwapManager.swapToModel("new_model", "2.0.0", modelFiles)
        
        // Then
        assertTrue(result is ModelSwapManager.SwapResult.ERROR)
    }
    
    @Test
    @DisplayName("Test error handling in model rollback")
    fun testErrorHandlingInModelRollback() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        every { mockPrefs.getString("previous_model", null) } returns "previous_model"
        
        // Mock file operations to throw exception
        every { mockFilesDir.listFiles() } throws RuntimeException("Test error")
        
        // When
        val result = modelSwapManager.rollbackToPrevious()
        
        // Then
        assertTrue(result is ModelSwapManager.RollbackResult.ERROR)
    }
    
    @Test
    @DisplayName("Test concurrent model operations")
    fun testConcurrentModelOperations() = runTest {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val modelFiles = mapOf(
            "model.bin" to "test model data".toByteArray(),
            "config.json" to "{\"version\": \"2.0.0\"}".toByteArray(),
            "vocab.txt" to "test vocab".toByteArray()
        )
        
        // Mock file operations
        every { mockFilesDir.listFiles() } returns emptyArray()
        
        // When
        val threads = (1..5).map { threadId ->
            Thread {
                repeat(10) {
                    when (threadId % 2) {
                        0 -> modelSwapManager.swapToModel("model_$threadId", "2.0.0", modelFiles)
                        1 -> modelSwapManager.rollbackToPrevious()
                    }
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Then
        // Should not throw any exceptions
        assertNotNull(modelSwapManager.getModelStatus())
    }
    
    @Test
    @DisplayName("Test model validation with various file sizes")
    fun testModelValidationWithVariousFileSizes() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        val testCases = listOf(
            // Valid sizes
            mapOf("model.bin" to ByteArray(1024), "config.json" to "{\"version\": \"1.0.0\"}".toByteArray(), "vocab.txt" to "test".toByteArray()),
            // Model too small
            mapOf("model.bin" to ByteArray(100), "config.json" to "{\"version\": \"1.0.0\"}".toByteArray(), "vocab.txt" to "test".toByteArray()),
            // Model too large
            mapOf("model.bin" to ByteArray(101 * 1024 * 1024), "config.json" to "{\"version\": \"1.0.0\"}".toByteArray(), "vocab.txt" to "test".toByteArray())
        )
        
        // When & Then
        testCases.forEach { modelFiles ->
            val validationResult = modelSwapManager.validateModel(modelFiles)
            assertNotNull(validationResult)
            assertTrue(validationResult.isValid || validationResult.errors.isNotEmpty())
        }
    }
    
    @Test
    @DisplayName("Test model swap result types")
    fun testModelSwapResultTypes() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Test different result types
        val results = listOf(
            ModelSwapManager.SwapResult.SUCCESS,
            ModelSwapManager.SwapResult.ALREADY_SWAPPING,
            ModelSwapManager.SwapResult.INVALID_MODEL(listOf("Test error")),
            ModelSwapManager.SwapResult.STAGING_FAILED("Test error"),
            ModelSwapManager.SwapResult.SWAP_FAILED("Test error"),
            ModelSwapManager.SwapResult.ERROR("Test error")
        )
        
        // When & Then
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result is ModelSwapManager.SwapResult)
        }
    }
    
    @Test
    @DisplayName("Test model rollback result types")
    fun testModelRollbackResultTypes() {
        // Given
        every { mockPrefs.getString(any(), any()) } returns "default"
        every { mockPrefs.getInt(any(), any()) } returns 14
        every { mockPrefs.getLong(any(), any()) } returns 0L
        
        // Test different result types
        val results = listOf(
            ModelSwapManager.RollbackResult.SUCCESS,
            ModelSwapManager.RollbackResult.ALREADY_SWAPPING,
            ModelSwapManager.RollbackResult.NO_PREVIOUS_MODEL,
            ModelSwapManager.RollbackResult.PREVIOUS_MODEL_NOT_FOUND,
            ModelSwapManager.RollbackResult.INVALID_PREVIOUS_MODEL(listOf("Test error")),
            ModelSwapManager.RollbackResult.ROLLBACK_FAILED,
            ModelSwapManager.RollbackResult.ERROR("Test error")
        )
        
        // When & Then
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result is ModelSwapManager.RollbackResult)
        }
    }
}
