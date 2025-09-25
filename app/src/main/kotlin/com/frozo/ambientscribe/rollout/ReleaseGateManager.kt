package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Release Gate Manager for rollout control and quality gates
 * 
 * Manages release gates for:
 * - Performance thresholds (p95 latency, battery consumption)
 * - Privacy compliance (lint checks, data protection)
 * - Canary rollout with auto-rollback
 * - Quality metrics validation
 * - Rollout safety controls
 * 
 * Features:
 * - Performance monitoring
 * - Privacy validation
 * - Canary deployment
 * - Auto-rollback triggers
 * - Quality gate enforcement
 */
class ReleaseGateManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREFS_NAME = "release_gates"
        private const val KEY_CANARY_PERCENTAGE = "canary_percentage"
        private const val KEY_ROLLOUT_PHASE = "rollout_phase"
        private const val KEY_LAST_METRICS_CHECK = "last_metrics_check"
        private const val KEY_ROLLBACK_TRIGGERED = "rollback_triggered"
        private const val KEY_QUALITY_GATES_PASSED = "quality_gates_passed"
        
        // Performance thresholds
        private const val MAX_P95_LATENCY_MS = 8000L // 8 seconds
        private const val MAX_BATTERY_CONSUMPTION_PERCENT = 6.0 // 6% per hour
        private const val MIN_CRASH_FREE_SESSIONS = 99.5 // 99.5%
        
        // Canary settings
        private const val DEFAULT_CANARY_PERCENTAGE = 5
        private const val MAX_CANARY_PERCENTAGE = 50
        
        // Rollout phases
        const val PHASE_CANARY = "canary"
        const val PHASE_STABLE = "stable"
        const val PHASE_ROLLBACK = "rollback"
        
        @Volatile
        private var INSTANCE: ReleaseGateManager? = null
        
        fun getInstance(context: Context): ReleaseGateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReleaseGateManager(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Release state
    private val _canaryPercentage = MutableStateFlow(
        prefs.getInt(KEY_CANARY_PERCENTAGE, DEFAULT_CANARY_PERCENTAGE)
    )
    val canaryPercentage: StateFlow<Int> = _canaryPercentage.asStateFlow()
    
    private val _rolloutPhase = MutableStateFlow(
        prefs.getString(KEY_ROLLOUT_PHASE, PHASE_CANARY) ?: PHASE_CANARY
    )
    val rolloutPhase: StateFlow<String> = _rolloutPhase.asStateFlow()
    
    private val _qualityGatesPassed = MutableStateFlow(
        prefs.getBoolean(KEY_QUALITY_GATES_PASSED, false)
    )
    val qualityGatesPassed: StateFlow<Boolean> = _qualityGatesPassed.asStateFlow()
    
    // Atomic operations
    private val isCheckingGates = AtomicBoolean(false)
    private val isRollingBack = AtomicBoolean(false)
    
    // Dependencies
    private lateinit var featureFlagManager: FeatureFlagManager
    private lateinit var killSwitchManager: KillSwitchManager
    private lateinit var rampPlanManager: RampPlanManager
    
    /**
     * Initialize with dependencies
     */
    fun initialize(
        featureFlagManager: FeatureFlagManager,
        killSwitchManager: KillSwitchManager,
        rampPlanManager: RampPlanManager
    ) {
        this.featureFlagManager = featureFlagManager
        this.killSwitchManager = killSwitchManager
        this.rampPlanManager = rampPlanManager
    }
    
    /**
     * Check if release gates are passed
     */
    suspend fun checkReleaseGates(): GateCheckResult {
        if (isCheckingGates.get()) {
            return GateCheckResult.ALREADY_CHECKING
        }
        
        if (!isCheckingGates.compareAndSet(false, true)) {
            return GateCheckResult.ALREADY_CHECKING
        }
        
        try {
            val performanceResult = checkPerformanceGates()
            val privacyResult = checkPrivacyGates()
            val qualityResult = checkQualityGates()
            
            val allPassed = performanceResult.passed && privacyResult.passed && qualityResult.passed
            
            _qualityGatesPassed.value = allPassed
            prefs.edit().putBoolean(KEY_QUALITY_GATES_PASSED, allPassed).apply()
            
            if (allPassed) {
                logGatesPassed()
                return GateCheckResult.PASSED
            } else {
                logGatesFailed(performanceResult, privacyResult, qualityResult)
                return GateCheckResult.FAILED(
                    performanceResult.issues + privacyResult.issues + qualityResult.issues
                )
            }
            
        } catch (e: Exception) {
            logError("Failed to check release gates", e)
            return GateCheckResult.ERROR(e.message ?: "Unknown error")
        } finally {
            isCheckingGates.set(false)
        }
    }
    
    /**
     * Check performance gates
     */
    private suspend fun checkPerformanceGates(): GateResult {
        val issues = mutableListOf<String>()
        
        try {
            // Check p95 latency
            val p95Latency = getP95Latency()
            if (p95Latency > MAX_P95_LATENCY_MS) {
                issues.add("P95 latency too high: ${p95Latency}ms (max: ${MAX_P95_LATENCY_MS}ms)")
            }
            
            // Check battery consumption
            val batteryConsumption = getBatteryConsumption()
            if (batteryConsumption > MAX_BATTERY_CONSUMPTION_PERCENT) {
                issues.add("Battery consumption too high: ${batteryConsumption}% (max: ${MAX_BATTERY_CONSUMPTION_PERCENT}%)")
            }
            
            // Check crash-free sessions
            val crashFreeSessions = getCrashFreeSessions()
            if (crashFreeSessions < MIN_CRASH_FREE_SESSIONS) {
                issues.add("Crash-free sessions too low: ${crashFreeSessions}% (min: ${MIN_CRASH_FREE_SESSIONS}%)")
            }
            
        } catch (e: Exception) {
            issues.add("Error checking performance gates: ${e.message}")
        }
        
        return GateResult(issues.isEmpty(), issues)
    }
    
    /**
     * Check privacy gates
     */
    private suspend fun checkPrivacyGates(): GateResult {
        val issues = mutableListOf<String>()
        
        try {
            // Check privacy lint
            val privacyLintResult = runPrivacyLint()
            if (!privacyLintResult.passed) {
                issues.addAll(privacyLintResult.issues)
            }
            
            // Check data protection compliance
            val dataProtectionResult = checkDataProtectionCompliance()
            if (!dataProtectionResult.passed) {
                issues.addAll(dataProtectionResult.issues)
            }
            
            // Check consent management
            val consentResult = checkConsentManagement()
            if (!consentResult.passed) {
                issues.addAll(consentResult.issues)
            }
            
        } catch (e: Exception) {
            issues.add("Error checking privacy gates: ${e.message}")
        }
        
        return GateResult(issues.isEmpty(), issues)
    }
    
    /**
     * Check quality gates
     */
    private suspend fun checkQualityGates(): GateResult {
        val issues = mutableListOf<String>()
        
        try {
            // Check feature flags
            if (!featureFlagManager.isAmbientScribeEnabled()) {
                issues.add("Ambient Scribe feature flag disabled")
            }
            
            // Check kill switches
            if (killSwitchManager.isEmergencyKilled()) {
                issues.add("Emergency kill switch active")
            }
            
            // Check device compatibility
            val deviceCompatibility = checkDeviceCompatibility()
            if (!deviceCompatibility.passed) {
                issues.addAll(deviceCompatibility.issues)
            }
            
            // Check model integrity
            val modelIntegrity = checkModelIntegrity()
            if (!modelIntegrity.passed) {
                issues.addAll(modelIntegrity.issues)
            }
            
        } catch (e: Exception) {
            issues.add("Error checking quality gates: ${e.message}")
        }
        
        return GateResult(issues.isEmpty(), issues)
    }
    
    /**
     * Get p95 latency (mock implementation)
     */
    private suspend fun getP95Latency(): Long {
        // This would integrate with actual performance monitoring
        return 5000L // Mock value
    }
    
    /**
     * Get battery consumption (mock implementation)
     */
    private suspend fun getBatteryConsumption(): Double {
        // This would integrate with actual battery monitoring
        return 4.5 // Mock value
    }
    
    /**
     * Get crash-free sessions (mock implementation)
     */
    private suspend fun getCrashFreeSessions(): Double {
        // This would integrate with actual crash reporting
        return 99.8 // Mock value
    }
    
    /**
     * Run privacy lint (mock implementation)
     */
    private suspend fun runPrivacyLint(): GateResult {
        // This would integrate with actual privacy linting
        return GateResult(true, emptyList())
    }
    
    /**
     * Check data protection compliance (mock implementation)
     */
    private suspend fun checkDataProtectionCompliance(): GateResult {
        // This would integrate with actual compliance checking
        return GateResult(true, emptyList())
    }
    
    /**
     * Check consent management (mock implementation)
     */
    private suspend fun checkConsentManagement(): GateResult {
        // This would integrate with actual consent checking
        return GateResult(true, emptyList())
    }
    
    /**
     * Check device compatibility (mock implementation)
     */
    private suspend fun checkDeviceCompatibility(): GateResult {
        // This would integrate with actual device compatibility checking
        return GateResult(true, emptyList())
    }
    
    /**
     * Check model integrity (mock implementation)
     */
    private suspend fun checkModelIntegrity(): GateResult {
        // This would integrate with actual model integrity checking
        return GateResult(true, emptyList())
    }
    
    /**
     * Trigger rollback
     */
    suspend fun triggerRollback(reason: String): RollbackResult {
        if (isRollingBack.get()) {
            return RollbackResult.ALREADY_ROLLING_BACK
        }
        
        if (!isRollingBack.compareAndSet(false, true)) {
            return RollbackResult.ALREADY_ROLLING_BACK
        }
        
        try {
            // Set rollout phase to rollback
            _rolloutPhase.value = PHASE_ROLLBACK
            prefs.edit().putString(KEY_ROLLOUT_PHASE, PHASE_ROLLBACK).apply()
            
            // Activate kill switches
            killSwitchManager.activateEmergencyKillSwitch(reason, "release_gate")
            
            // Rollback ramp plan
            rampPlanManager.rollbackToPreviousPhase()
            
            // Log rollback
            logRollbackTriggered(reason)
            
            return RollbackResult.SUCCESS
            
        } catch (e: Exception) {
            logError("Failed to trigger rollback", e)
            return RollbackResult.ERROR(e.message ?: "Unknown error")
        } finally {
            isRollingBack.set(false)
        }
    }
    
    /**
     * Set canary percentage
     */
    fun setCanaryPercentage(percentage: Int): Boolean {
        if (percentage < 0 || percentage > MAX_CANARY_PERCENTAGE) {
            return false
        }
        
        _canaryPercentage.value = percentage
        prefs.edit().putInt(KEY_CANARY_PERCENTAGE, percentage).apply()
        
        logCanaryPercentageChanged(percentage)
        return true
    }
    
    /**
     * Get release gate status
     */
    fun getReleaseGateStatus(): ReleaseGateStatus {
        return ReleaseGateStatus(
            canaryPercentage = _canaryPercentage.value,
            rolloutPhase = _rolloutPhase.value,
            qualityGatesPassed = _qualityGatesPassed.value,
            isCheckingGates = isCheckingGates.get(),
            isRollingBack = isRollingBack.get(),
            lastMetricsCheck = prefs.getLong(KEY_LAST_METRICS_CHECK, 0L)
        )
    }
    
    /**
     * Log gates passed
     */
    private fun logGatesPassed() {
        android.util.Log.i("ReleaseGateManager", "Release gates passed")
    }
    
    /**
     * Log gates failed
     */
    private fun logGatesFailed(performance: GateResult, privacy: GateResult, quality: GateResult) {
        val allIssues = performance.issues + privacy.issues + quality.issues
        android.util.Log.w("ReleaseGateManager", "Release gates failed: ${allIssues.joinToString(", ")}")
    }
    
    /**
     * Log rollback triggered
     */
    private fun logRollbackTriggered(reason: String) {
        android.util.Log.w("ReleaseGateManager", "Rollback triggered: $reason")
    }
    
    /**
     * Log canary percentage changed
     */
    private fun logCanaryPercentageChanged(percentage: Int) {
        android.util.Log.i("ReleaseGateManager", "Canary percentage changed to: $percentage%")
    }
    
    /**
     * Log error
     */
    private fun logError(message: String, throwable: Throwable) {
        android.util.Log.e("ReleaseGateManager", message, throwable)
    }
    
    /**
     * Gate check result
     */
    sealed class GateCheckResult {
        object ALREADY_CHECKING : GateCheckResult()
        object PASSED : GateCheckResult()
        data class FAILED(val issues: List<String>) : GateCheckResult()
        data class ERROR(val message: String) : GateCheckResult()
    }
    
    /**
     * Rollback result
     */
    sealed class RollbackResult {
        object ALREADY_ROLLING_BACK : RollbackResult()
        object SUCCESS : RollbackResult()
        data class ERROR(val message: String) : RollbackResult()
    }
    
    /**
     * Gate result
     */
    data class GateResult(
        val passed: Boolean,
        val issues: List<String>
    )
    
    /**
     * Release gate status
     */
    data class ReleaseGateStatus(
        val canaryPercentage: Int,
        val rolloutPhase: String,
        val qualityGatesPassed: Boolean,
        val isCheckingGates: Boolean,
        val isRollingBack: Boolean,
        val lastMetricsCheck: Long
    )
}
