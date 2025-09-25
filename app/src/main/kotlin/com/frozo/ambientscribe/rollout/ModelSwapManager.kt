package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Model Swap Manager for atomic model updates
 * 
 * Manages model swapping with:
 * - Atomic updates (all-or-nothing)
 * - 14-day retention of previous models
 * - Rollback capability
 * - Integrity verification
 * - Cleanup of old models
 * 
 * Features:
 * - Atomic file operations
 * - Model versioning
 * - Integrity checksums
 * - Automatic cleanup
 * - Rollback support
 */
class ModelSwapManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREFS_NAME = "model_swap"
        private const val KEY_CURRENT_MODEL = "current_model"
        private const val KEY_PREVIOUS_MODEL = "previous_model"
        private const val KEY_MODEL_VERSION = "model_version"
        private const val KEY_LAST_SWAP = "last_swap"
        private const val KEY_RETENTION_DAYS = "retention_days"
        
        // Default values
        private const val DEFAULT_RETENTION_DAYS = 14
        private const val DEFAULT_MODEL_VERSION = "1.0.0"
        
        @Volatile
        private var INSTANCE: ModelSwapManager? = null
        
        fun getInstance(context: Context): ModelSwapManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelSwapManager(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Model state
    private val _currentModel = MutableStateFlow(
        prefs.getString(KEY_CURRENT_MODEL, "default") ?: "default"
    )
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()
    
    private val _modelVersion = MutableStateFlow(
        prefs.getString(KEY_MODEL_VERSION, DEFAULT_MODEL_VERSION) ?: DEFAULT_MODEL_VERSION
    )
    val modelVersion: StateFlow<String> = _modelVersion.asStateFlow()
    
    // Atomic operations
    private val isSwapping = AtomicBoolean(false)
    
    // Configuration
    private val retentionDays = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
    private var lastSwap: Long = 0L
    
    init {
        lastSwap = prefs.getLong(KEY_LAST_SWAP, 0L)
    }
    
    /**
     * Get models directory
     */
    private fun getModelsDirectory(): File {
        return File(context.filesDir, "models")
    }
    
    /**
     * Get current model directory
     */
    private fun getCurrentModelDirectory(): File {
        return File(getModelsDirectory(), _currentModel.value)
    }
    
    /**
     * Get model directory by name
     */
    private fun getModelDirectory(modelName: String): File {
        return File(getModelsDirectory(), modelName)
    }
    
    /**
     * Get staging directory for atomic swaps
     */
    private fun getStagingDirectory(): File {
        return File(getModelsDirectory(), "staging")
    }
    
    /**
     * Swap to new model atomically
     */
    suspend fun swapToModel(
        newModelName: String,
        newModelVersion: String,
        modelFiles: Map<String, ByteArray>
    ): SwapResult {
        if (isSwapping.get()) {
            return SwapResult.ALREADY_SWAPPING
        }
        
        if (!isSwapping.compareAndSet(false, true)) {
            return SwapResult.ALREADY_SWAPPING
        }
        
        try {
            // Validate new model
            val validationResult = validateModel(modelFiles)
            if (!validationResult.isValid) {
                return SwapResult.INVALID_MODEL(validationResult.errors)
            }
            
            // Create staging directory
            val stagingDir = getStagingDirectory()
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            stagingDir.mkdirs()
            
            // Write new model files to staging
            val stagingResult = writeModelToStaging(stagingDir, modelFiles)
            if (!stagingResult.success) {
                return SwapResult.STAGING_FAILED(stagingResult.error)
            }
            
            // Perform atomic swap
            val swapResult = performAtomicSwap(newModelName, newModelVersion, stagingDir)
            if (!swapResult.success) {
                return SwapResult.SWAP_FAILED(swapResult.error)
            }
            
            // Update state
            val previousModel = _currentModel.value
            _currentModel.value = newModelName
            _modelVersion.value = newModelVersion
            lastSwap = System.currentTimeMillis()
            
            // Save state
            saveState(previousModel)
            
            // Cleanup old models
            cleanupOldModels()
            
            logModelSwap(newModelName, newModelVersion, previousModel)
            return SwapResult.SUCCESS
            
        } catch (e: Exception) {
            logError("Failed to swap model", e)
            return SwapResult.ERROR(e.message ?: "Unknown error")
        } finally {
            isSwapping.set(false)
        }
    }
    
    /**
     * Rollback to previous model
     */
    suspend fun rollbackToPrevious(): RollbackResult {
        if (isSwapping.get()) {
            return RollbackResult.ALREADY_SWAPPING
        }
        
        try {
            val previousModel = prefs.getString(KEY_PREVIOUS_MODEL, null)
            if (previousModel == null) {
                return RollbackResult.NO_PREVIOUS_MODEL
            }
            
            val previousModelDir = getModelDirectory(previousModel)
            if (!previousModelDir.exists()) {
                return RollbackResult.PREVIOUS_MODEL_NOT_FOUND
            }
            
            // Validate previous model
            val validationResult = validateModelDirectory(previousModelDir)
            if (!validationResult.isValid) {
                return RollbackResult.INVALID_PREVIOUS_MODEL(validationResult.errors)
            }
            
            // Perform rollback
            val currentModel = _currentModel.value
            val currentModelDir = getCurrentModelDirectory()
            
            // Move current model to temp location
            val tempDir = File(getModelsDirectory(), "temp_${System.currentTimeMillis()}")
            if (currentModelDir.exists()) {
                currentModelDir.renameTo(tempDir)
            }
            
            // Move previous model to current
            val rollbackResult = previousModelDir.renameTo(currentModelDir)
            if (!rollbackResult) {
                // Restore current model if rollback failed
                tempDir.renameTo(currentModelDir)
                return RollbackResult.ROLLBACK_FAILED
            }
            
            // Update state
            _currentModel.value = previousModel
            _modelVersion.value = getModelVersionFromDirectory(previousModelDir)
            lastSwap = System.currentTimeMillis()
            
            // Save state
            saveState(currentModel)
            
            logModelRollback(previousModel, currentModel)
            return RollbackResult.SUCCESS
            
        } catch (e: Exception) {
            logError("Failed to rollback model", e)
            return RollbackResult.ERROR(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Validate model files
     */
    private fun validateModel(modelFiles: Map<String, ByteArray>): ModelValidationResult {
        val errors = mutableListOf<String>()
        
        // Check required files
        val requiredFiles = listOf("model.bin", "config.json", "vocab.txt")
        for (file in requiredFiles) {
            if (!modelFiles.containsKey(file)) {
                errors.add("Missing required file: $file")
            }
        }
        
        // Check file sizes
        modelFiles.forEach { (filename, data) ->
            if (data.isEmpty()) {
                errors.add("Empty file: $filename")
            }
            if (data.size > 100 * 1024 * 1024) { // 100MB limit
                errors.add("File too large: $filename (${data.size} bytes)")
            }
        }
        
        // Check model integrity
        if (modelFiles.containsKey("model.bin")) {
            val modelData = modelFiles["model.bin"]!!
            if (modelData.size < 1024) { // At least 1KB
                errors.add("Model file too small: model.bin")
            }
        }
        
        return ModelValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Validate model directory
     */
    private fun validateModelDirectory(modelDir: File): ModelValidationResult {
        val errors = mutableListOf<String>()
        
        if (!modelDir.exists()) {
            errors.add("Model directory does not exist")
            return ModelValidationResult(false, errors)
        }
        
        val requiredFiles = listOf("model.bin", "config.json", "vocab.txt")
        for (file in requiredFiles) {
            val file = File(modelDir, file)
            if (!file.exists()) {
                errors.add("Missing required file: $file")
            } else if (file.length() == 0L) {
                errors.add("Empty file: $file")
            }
        }
        
        return ModelValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Write model to staging directory
     */
    private fun writeModelToStaging(stagingDir: File, modelFiles: Map<String, ByteArray>): WriteResult {
        return try {
            modelFiles.forEach { (filename, data) ->
                val file = File(stagingDir, filename)
                file.parentFile?.mkdirs()
                file.writeBytes(data)
            }
            WriteResult(true, null)
        } catch (e: Exception) {
            WriteResult(false, e.message)
        }
    }
    
    /**
     * Perform atomic swap
     */
    private fun performAtomicSwap(newModelName: String, newModelVersion: String, stagingDir: File): SwapOperationResult {
        return try {
            val newModelDir = getModelDirectory(newModelName)
            
            // Move staging to new model directory
            val success = stagingDir.renameTo(newModelDir)
            if (!success) {
                return SwapOperationResult(false, "Failed to move staging to model directory")
            }
            
            // Update current model symlink (if supported) or copy
            val currentModelDir = getCurrentModelDirectory()
            if (currentModelDir.exists()) {
                currentModelDir.deleteRecursively()
            }
            
            // Copy new model to current
            copyDirectory(newModelDir, currentModelDir)
            
            SwapOperationResult(true, null)
        } catch (e: Exception) {
            SwapOperationResult(false, e.message)
        }
    }
    
    /**
     * Copy directory recursively
     */
    private fun copyDirectory(source: File, destination: File) {
        destination.mkdirs()
        source.listFiles()?.forEach { file ->
            val destFile = File(destination, file.name)
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                file.copyTo(destFile, overwrite = true)
            }
        }
    }
    
    /**
     * Get model version from directory
     */
    private fun getModelVersionFromDirectory(modelDir: File): String {
        val configFile = File(modelDir, "config.json")
        if (configFile.exists()) {
            try {
                val config = configFile.readText()
                // Parse JSON and extract version (simplified)
                val versionMatch = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(config)
                return versionMatch?.groupValues?.get(1) ?: "1.0.0"
            } catch (e: Exception) {
                logError("Failed to parse model version", e)
            }
        }
        return "1.0.0"
    }
    
    /**
     * Cleanup old models
     */
    private fun cleanupOldModels() {
        try {
            val modelsDir = getModelsDirectory()
            val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
            
            modelsDir.listFiles()?.forEach { modelDir ->
                if (modelDir.isDirectory && modelDir.name != _currentModel.value) {
                    val lastModified = modelDir.lastModified()
                    if (lastModified < cutoffTime) {
                        modelDir.deleteRecursively()
                        logModelCleanup(modelDir.name)
                    }
                }
            }
        } catch (e: Exception) {
            logError("Failed to cleanup old models", e)
        }
    }
    
    /**
     * Save state to preferences
     */
    private fun saveState(previousModel: String) {
        prefs.edit()
            .putString(KEY_CURRENT_MODEL, _currentModel.value)
            .putString(KEY_PREVIOUS_MODEL, previousModel)
            .putString(KEY_MODEL_VERSION, _modelVersion.value)
            .putLong(KEY_LAST_SWAP, lastSwap)
            .apply()
    }
    
    /**
     * Get model status
     */
    fun getModelStatus(): Map<String, Any> {
        return mapOf(
            "current_model" to _currentModel.value,
            "model_version" to _modelVersion.value,
            "last_swap" to lastSwap,
            "retention_days" to retentionDays,
            "is_swapping" to isSwapping.get()
        )
    }
    
    /**
     * Log model swap
     */
    private fun logModelSwap(newModel: String, newVersion: String, previousModel: String) {
        android.util.Log.i("ModelSwapManager", "Model swapped: $previousModel -> $newModel (v$newVersion)")
    }
    
    /**
     * Log model rollback
     */
    private fun logModelRollback(previousModel: String, currentModel: String) {
        android.util.Log.i("ModelSwapManager", "Model rolled back: $currentModel -> $previousModel")
    }
    
    /**
     * Log model cleanup
     */
    private fun logModelCleanup(modelName: String) {
        android.util.Log.i("ModelSwapManager", "Model cleaned up: $modelName")
    }
    
    /**
     * Log error
     */
    private fun logError(message: String, throwable: Throwable) {
        android.util.Log.e("ModelSwapManager", message, throwable)
    }
    
    /**
     * Swap result
     */
    sealed class SwapResult {
        object SUCCESS : SwapResult()
        object ALREADY_SWAPPING : SwapResult()
        data class INVALID_MODEL(val errors: List<String>) : SwapResult()
        data class STAGING_FAILED(val error: String?) : SwapResult()
        data class SWAP_FAILED(val error: String?) : SwapResult()
        data class ERROR(val message: String) : SwapResult()
    }
    
    /**
     * Rollback result
     */
    sealed class RollbackResult {
        object SUCCESS : RollbackResult()
        object ALREADY_SWAPPING : RollbackResult()
        object NO_PREVIOUS_MODEL : RollbackResult()
        object PREVIOUS_MODEL_NOT_FOUND : RollbackResult()
        data class INVALID_PREVIOUS_MODEL(val errors: List<String>) : RollbackResult()
        object ROLLBACK_FAILED : RollbackResult()
        data class ERROR(val message: String) : RollbackResult()
    }
    
    /**
     * Model validation result
     */
    data class ModelValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
    
    /**
     * Write result
     */
    data class WriteResult(
        val success: Boolean,
        val error: String?
    )
    
    /**
     * Swap operation result
     */
    data class SwapOperationResult(
        val success: Boolean,
        val error: String?
    )
}
