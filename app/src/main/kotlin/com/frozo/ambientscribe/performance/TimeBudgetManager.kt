package com.frozo.ambientscribe.performance

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Time Budget Manager - ST-6.19
 * Implements time-budget SLAs per stage (ASR chunk, LLM, PDF); timeout → user hint; telemetry
 * Provides comprehensive time budget management and monitoring
 */
class TimeBudgetManager(
    private val context: Context,
    private val deviceTierDetector: DeviceTierDetector
) {
    
    companion object {
        private const val TAG = "TimeBudgetManager"
        
        // Time budget SLAs (in milliseconds)
        private const val ASR_CHUNK_TIER_A_MS = 2000L
        private const val ASR_CHUNK_TIER_B_MS = 3000L
        private const val LLM_TIER_A_MS = 8000L
        private const val LLM_TIER_B_MS = 12000L
        private const val PDF_TIER_A_MS = 5000L
        private const val PDF_TIER_B_MS = 8000L
        
        // Timeout thresholds (percentage of SLA)
        private const val WARNING_THRESHOLD = 0.8f
        private const val CRITICAL_THRESHOLD = 0.95f
        private const val TIMEOUT_THRESHOLD = 1.0f
    }

    private val activeOperations = mutableMapOf<String, TimeBudgetOperation>()
    private val operationHistory = mutableListOf<TimeBudgetOperation>()

    /**
     * Processing stage enumeration
     */
    enum class ProcessingStage {
        ASR_CHUNK,
        LLM,
        PDF
    }

    /**
     * Time budget operation data class
     */
    data class TimeBudgetOperation(
        val id: String,
        val stage: ProcessingStage,
        val startTime: Long,
        val endTime: Long?,
        val slaMs: Long,
        val actualMs: Long?,
        val status: OperationStatus,
        val tier: DeviceTierDetector.DeviceTier,
        val metadata: Map<String, Any>
    )

    /**
     * Operation status enumeration
     */
    enum class OperationStatus {
        RUNNING,
        COMPLETED,
        WARNING,
        CRITICAL,
        TIMEOUT
    }

    /**
     * Time budget SLA data class
     */
    data class TimeBudgetSLA(
        val stage: ProcessingStage,
        val tier: DeviceTierDetector.DeviceTier,
        val slaMs: Long,
        val warningThresholdMs: Long,
        val criticalThresholdMs: Long,
        val timeoutThresholdMs: Long
    )

    /**
     * Time budget violation data class
     */
    data class TimeBudgetViolation(
        val operationId: String,
        val stage: ProcessingStage,
        val tier: DeviceTierDetector.DeviceTier,
        val violationType: ViolationType,
        val slaMs: Long,
        val actualMs: Long,
        val violationPercent: Float,
        val userHint: String,
        val timestamp: Long
    )

    /**
     * Violation type enumeration
     */
    enum class ViolationType {
        WARNING,
        CRITICAL,
        TIMEOUT
    }

    /**
     * Start time budget operation
     */
    suspend fun startOperation(
        operationId: String,
        stage: ProcessingStage,
        metadata: Map<String, Any> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting time budget operation: $operationId, stage: $stage")
            
            val capabilities = deviceTierDetector.loadDeviceCapabilities()
                ?: return Result.failure(IllegalStateException("No device capabilities found"))
            
            val sla = getSLAForStage(stage, capabilities.tier)
            
            val operation = TimeBudgetOperation(
                id = operationId,
                stage = stage,
                startTime = System.currentTimeMillis(),
                endTime = null,
                slaMs = sla.slaMs,
                actualMs = null,
                status = OperationStatus.RUNNING,
                tier = capabilities.tier,
                metadata = metadata
            )
            
            activeOperations[operationId] = operation
            
            Log.d(TAG, "Time budget operation started: $operationId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start time budget operation", e)
            Result.failure(e)
        }
    }

    /**
     * Complete time budget operation
     */
    suspend fun completeOperation(operationId: String): Result<TimeBudgetOperation> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Completing time budget operation: $operationId")
            
            val operation = activeOperations[operationId]
                ?: return Result.failure(IllegalStateException("Operation not found: $operationId"))
            
            val endTime = System.currentTimeMillis()
            val actualMs = endTime - operation.startTime
            
            val updatedOperation = operation.copy(
                endTime = endTime,
                actualMs = actualMs,
                status = determineOperationStatus(actualMs, operation.slaMs)
            )
            
            activeOperations.remove(operationId)
            operationHistory.add(updatedOperation)
            
            // Check for violations
            checkForViolations(updatedOperation)
            
            // Save operation result
            saveOperationResult(updatedOperation)
            
            Log.d(TAG, "Time budget operation completed: $operationId, actual: ${actualMs}ms, SLA: ${operation.slaMs}ms")
            Result.success(updatedOperation)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete time budget operation", e)
            Result.failure(e)
        }
    }

    /**
     * Get SLA for stage and tier
     */
    private fun getSLAForStage(stage: ProcessingStage, tier: DeviceTierDetector.DeviceTier): TimeBudgetSLA {
        val slaMs = when (stage) {
            ProcessingStage.ASR_CHUNK -> if (tier == DeviceTierDetector.DeviceTier.TIER_A) ASR_CHUNK_TIER_A_MS else ASR_CHUNK_TIER_B_MS
            ProcessingStage.LLM -> if (tier == DeviceTierDetector.DeviceTier.TIER_A) LLM_TIER_A_MS else LLM_TIER_B_MS
            ProcessingStage.PDF -> if (tier == DeviceTierDetector.DeviceTier.TIER_A) PDF_TIER_A_MS else PDF_TIER_B_MS
        }
        
        return TimeBudgetSLA(
            stage = stage,
            tier = tier,
            slaMs = slaMs,
            warningThresholdMs = (slaMs * WARNING_THRESHOLD).toLong(),
            criticalThresholdMs = (slaMs * CRITICAL_THRESHOLD).toLong(),
            timeoutThresholdMs = (slaMs * TIMEOUT_THRESHOLD).toLong()
        )
    }

    /**
     * Determine operation status based on actual time
     */
    private fun determineOperationStatus(actualMs: Long, slaMs: Long): OperationStatus {
        val violationPercent = actualMs.toFloat() / slaMs.toFloat()
        
        return when {
            violationPercent >= TIMEOUT_THRESHOLD -> OperationStatus.TIMEOUT
            violationPercent >= CRITICAL_THRESHOLD -> OperationStatus.CRITICAL
            violationPercent >= WARNING_THRESHOLD -> OperationStatus.WARNING
            else -> OperationStatus.COMPLETED
        }
    }

    /**
     * Check for time budget violations
     */
    private suspend fun checkForViolations(operation: TimeBudgetOperation) {
        if (operation.actualMs == null) return
        
        val violationPercent = operation.actualMs!!.toFloat() / operation.slaMs.toFloat()
        
        when {
            violationPercent >= TIMEOUT_THRESHOLD -> {
                val violation = TimeBudgetViolation(
                    operationId = operation.id,
                    stage = operation.stage,
                    tier = operation.tier,
                    violationType = ViolationType.TIMEOUT,
                    slaMs = operation.slaMs,
                    actualMs = operation.actualMs!!,
                    violationPercent = violationPercent,
                    userHint = generateTimeoutUserHint(operation.stage, operation.tier),
                    timestamp = System.currentTimeMillis()
                )
                handleTimeBudgetViolation(violation)
            }
            violationPercent >= CRITICAL_THRESHOLD -> {
                val violation = TimeBudgetViolation(
                    operationId = operation.id,
                    stage = operation.stage,
                    tier = operation.tier,
                    violationType = ViolationType.CRITICAL,
                    slaMs = operation.slaMs,
                    actualMs = operation.actualMs!!,
                    violationPercent = violationPercent,
                    userHint = generateCriticalUserHint(operation.stage, operation.tier),
                    timestamp = System.currentTimeMillis()
                )
                handleTimeBudgetViolation(violation)
            }
            violationPercent >= WARNING_THRESHOLD -> {
                val violation = TimeBudgetViolation(
                    operationId = operation.id,
                    stage = operation.stage,
                    tier = operation.tier,
                    violationType = ViolationType.WARNING,
                    slaMs = operation.slaMs,
                    actualMs = operation.actualMs!!,
                    violationPercent = violationPercent,
                    userHint = generateWarningUserHint(operation.stage, operation.tier),
                    timestamp = System.currentTimeMillis()
                )
                handleTimeBudgetViolation(violation)
            }
        }
    }

    /**
     * Handle time budget violation
     */
    private suspend fun handleTimeBudgetViolation(violation: TimeBudgetViolation) {
        Log.w(TAG, "Time budget violation detected: ${violation.violationType} for ${violation.stage}")
        
        // Save violation
        saveTimeBudgetViolation(violation)
        
        // Send telemetry
        sendViolationTelemetry(violation)
        
        // Show user hint
        showUserHint(violation.userHint)
    }

    /**
     * Generate timeout user hint
     */
    private fun generateTimeoutUserHint(stage: ProcessingStage, tier: DeviceTierDetector.DeviceTier): String {
        return when (stage) {
            ProcessingStage.ASR_CHUNK -> "Speech recognition is taking longer than expected. Please try speaking more clearly or check your microphone."
            ProcessingStage.LLM -> "Text processing is taking longer than expected. Please wait or try again with shorter text."
            ProcessingStage.PDF -> "PDF generation is taking longer than expected. Please wait or try again with a smaller document."
        }
    }

    /**
     * Generate critical user hint
     */
    private fun generateCriticalUserHint(stage: ProcessingStage, tier: DeviceTierDetector.DeviceTier): String {
        return when (stage) {
            ProcessingStage.ASR_CHUNK -> "Speech recognition is slow. Please speak clearly and avoid background noise."
            ProcessingStage.LLM -> "Text processing is slow. Please wait or try with shorter text."
            ProcessingStage.PDF -> "PDF generation is slow. Please wait or try with a smaller document."
        }
    }

    /**
     * Generate warning user hint
     */
    private fun generateWarningUserHint(stage: ProcessingStage, tier: DeviceTierDetector.DeviceTier): String {
        return when (stage) {
            ProcessingStage.ASR_CHUNK -> "Speech recognition is taking a bit longer than usual. Please continue speaking clearly."
            ProcessingStage.LLM -> "Text processing is taking a bit longer than usual. Please wait a moment."
            ProcessingStage.PDF -> "PDF generation is taking a bit longer than usual. Please wait a moment."
        }
    }

    /**
     * Show user hint
     */
    private suspend fun showUserHint(hint: String) {
        // In a real implementation, this would show a user notification or toast
        Log.d(TAG, "User hint: $hint")
    }

    /**
     * Send violation telemetry
     */
    private suspend fun sendViolationTelemetry(violation: TimeBudgetViolation) {
        // In a real implementation, this would send telemetry data to analytics
        Log.d(TAG, "Sending violation telemetry: ${violation.violationType} for ${violation.stage}")
    }

    /**
     * Save operation result
     */
    private fun saveOperationResult(operation: TimeBudgetOperation) {
        try {
            val resultsDir = File(context.filesDir, "time_budget_operations")
            resultsDir.mkdirs()
            
            val resultFile = File(resultsDir, "operation_${operation.id}_${operation.startTime}.json")
            val json = JSONObject().apply {
                put("id", operation.id)
                put("stage", operation.stage.name)
                put("startTime", operation.startTime)
                put("endTime", operation.endTime)
                put("slaMs", operation.slaMs)
                put("actualMs", operation.actualMs)
                put("status", operation.status.name)
                put("tier", operation.tier.name)
                put("metadata", JSONObject(operation.metadata))
            }
            
            resultFile.writeText(json.toString())
            Log.d(TAG, "Operation result saved to: ${resultFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save operation result", e)
        }
    }

    /**
     * Save time budget violation
     */
    private fun saveTimeBudgetViolation(violation: TimeBudgetViolation) {
        try {
            val violationsDir = File(context.filesDir, "time_budget_violations")
            violationsDir.mkdirs()
            
            val violationFile = File(violationsDir, "violation_${violation.timestamp}.json")
            val json = JSONObject().apply {
                put("operationId", violation.operationId)
                put("stage", violation.stage.name)
                put("tier", violation.tier.name)
                put("violationType", violation.violationType.name)
                put("slaMs", violation.slaMs)
                put("actualMs", violation.actualMs)
                put("violationPercent", violation.violationPercent)
                put("userHint", violation.userHint)
                put("timestamp", violation.timestamp)
            }
            
            violationFile.writeText(json.toString())
            Log.d(TAG, "Time budget violation saved to: ${violationFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save time budget violation", e)
        }
    }

    /**
     * Get time budget statistics
     */
    suspend fun getTimeBudgetStatistics(): TimeBudgetStatistics = withContext(Dispatchers.IO) {
        try {
            val resultsDir = File(context.filesDir, "time_budget_operations")
            val resultFiles = resultsDir.listFiles { file ->
                file.name.startsWith("operation_") && file.name.endsWith(".json")
            } ?: emptyArray()

            val totalOperations = resultFiles.size
            val completedOperations = resultFiles.count { file ->
                try {
                    val json = JSONObject(file.readText())
                    json.getString("status") == "COMPLETED"
                } catch (e: Exception) {
                    false
                }
            }
            val warningOperations = resultFiles.count { file ->
                try {
                    val json = JSONObject(file.readText())
                    json.getString("status") == "WARNING"
                } catch (e: Exception) {
                    false
                }
            }
            val criticalOperations = resultFiles.count { file ->
                try {
                    val json = JSONObject(file.readText())
                    json.getString("status") == "CRITICAL"
                } catch (e: Exception) {
                    false
                }
            }
            val timeoutOperations = resultFiles.count { file ->
                try {
                    val json = JSONObject(file.readText())
                    json.getString("status") == "TIMEOUT"
                } catch (e: Exception) {
                    false
                }
            }

            TimeBudgetStatistics(
                totalOperations = totalOperations,
                completedOperations = completedOperations,
                warningOperations = warningOperations,
                criticalOperations = criticalOperations,
                timeoutOperations = timeoutOperations,
                successRate = if (totalOperations > 0) (completedOperations.toFloat() / totalOperations) * 100f else 0f
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get time budget statistics", e)
            TimeBudgetStatistics(
                totalOperations = 0,
                completedOperations = 0,
                warningOperations = 0,
                criticalOperations = 0,
                timeoutOperations = 0,
                successRate = 0f
            )
        }
    }

    /**
     * Time budget statistics data class
     */
    data class TimeBudgetStatistics(
        val totalOperations: Int,
        val completedOperations: Int,
        val warningOperations: Int,
        val criticalOperations: Int,
        val timeoutOperations: Int,
        val successRate: Float
    )
}
