package com.frozo.ambientscribe.rollout

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ramp Plan Manager for controlled rollout
 * 
 * Manages rollout phases:
 * - Internal (0% of users)
 * - Pilot 1 (5% of users)
 * - Pilot 2 (25% of users)
 * - Pilot 3 (50% of users)
 * - Expansion (100% of users)
 * 
 * Features:
 * - Gradual rollout with percentage controls
 * - A/B testing within phases
 * - Rollback capabilities
 * - User segmentation
 * - Performance monitoring
 */
class RampPlanManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val PREFS_NAME = "ramp_plan"
        private const val KEY_CURRENT_PHASE = "current_phase"
        private const val KEY_USER_SEGMENT = "user_segment"
        private const val KEY_AB_TEST_GROUP = "ab_test_group"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_ROLLOUT_PERCENTAGE = "rollout_percentage"
        
        // Rollout phases
        const val PHASE_INTERNAL = "internal"
        const val PHASE_PILOT_1 = "pilot_1"
        const val PHASE_PILOT_2 = "pilot_2"
        const val PHASE_PILOT_3 = "pilot_3"
        const val PHASE_EXPANSION = "expansion"
        
        // Rollout percentages
        private const val PERCENTAGE_INTERNAL = 0
        private const val PERCENTAGE_PILOT_1 = 5
        private const val PERCENTAGE_PILOT_2 = 25
        private const val PERCENTAGE_PILOT_3 = 50
        private const val PERCENTAGE_EXPANSION = 100
        
        // Default values
        private const val DEFAULT_PHASE = PHASE_INTERNAL
        
        @Volatile
        private var INSTANCE: RampPlanManager? = null
        
        fun getInstance(context: Context): RampPlanManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RampPlanManager(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Current phase state
    private val _currentPhase = MutableStateFlow(
        prefs.getString(KEY_CURRENT_PHASE, DEFAULT_PHASE) ?: DEFAULT_PHASE
    )
    val currentPhase: StateFlow<String> = _currentPhase.asStateFlow()
    
    // User segmentation
    private val _userSegment = MutableStateFlow(
        prefs.getString(KEY_USER_SEGMENT, "control") ?: "control"
    )
    val userSegment: StateFlow<String> = _userSegment.asStateFlow()
    
    private val _abTestGroup = MutableStateFlow(
        prefs.getString(KEY_AB_TEST_GROUP, "control") ?: "control"
    )
    val abTestGroup: StateFlow<String> = _abTestGroup.asStateFlow()
    
    // Rollout configuration
    private var rolloutPercentage: Int = PERCENTAGE_INTERNAL
    private var lastUpdate: Long = 0L
    
    // Atomic operations
    private val isUpdating = AtomicBoolean(false)
    
    init {
        loadStoredValues()
        determineUserSegment()
    }
    
    /**
     * Load stored values
     */
    private fun loadStoredValues() {
        rolloutPercentage = prefs.getInt(KEY_ROLLOUT_PERCENTAGE, PERCENTAGE_INTERNAL)
        lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
    }
    
    /**
     * Determine user segment based on current phase
     */
    private fun determineUserSegment() {
        val userId = generateUserId()
        val segment = when (_currentPhase.value) {
            PHASE_INTERNAL -> "internal"
            PHASE_PILOT_1 -> if (isUserInRollout(userId, PERCENTAGE_PILOT_1)) "pilot_1" else "control"
            PHASE_PILOT_2 -> if (isUserInRollout(userId, PERCENTAGE_PILOT_2)) "pilot_2" else "control"
            PHASE_PILOT_3 -> if (isUserInRollout(userId, PERCENTAGE_PILOT_3)) "pilot_3" else "control"
            PHASE_EXPANSION -> "expansion"
            else -> "control"
        }
        
        _userSegment.value = segment
        prefs.edit().putString(KEY_USER_SEGMENT, segment).apply()
        
        // Determine A/B test group
        val abGroup = if (segment != "control") {
            if (userId.hashCode() % 2 == 0) "test_a" else "test_b"
        } else {
            "control"
        }
        
        _abTestGroup.value = abGroup
        prefs.edit().putString(KEY_AB_TEST_GROUP, abGroup).apply()
    }
    
    /**
     * Generate user ID for consistent segmentation
     */
    private fun generateUserId(): String {
        val storedId = prefs.getString("user_id", null)
        if (storedId != null) {
            return storedId
        }
        
        val userId = "user_${System.currentTimeMillis()}_${(Math.random() * 1000000).toInt()}"
        prefs.edit().putString("user_id", userId).apply()
        return userId
    }
    
    /**
     * Check if user is in rollout based on percentage
     */
    private fun isUserInRollout(userId: String, percentage: Int): Boolean {
        val hash = userId.hashCode()
        val bucket = Math.abs(hash) % 100
        return bucket < percentage
    }
    
    /**
     * Check if user has access to feature
     */
    fun hasFeatureAccess(): Boolean {
        return try {
            when (_userSegment.value) {
                "internal" -> true
                "pilot_1", "pilot_2", "pilot_3" -> true
                "expansion" -> true
                else -> false
            }
        } catch (e: Exception) {
            logError("Failed to check feature access", e)
            false
        }
    }
    
    /**
     * Get current rollout percentage
     */
    fun getCurrentRolloutPercentage(): Int {
        return when (_currentPhase.value) {
            PHASE_INTERNAL -> PERCENTAGE_INTERNAL
            PHASE_PILOT_1 -> PERCENTAGE_PILOT_1
            PHASE_PILOT_2 -> PERCENTAGE_PILOT_2
            PHASE_PILOT_3 -> PERCENTAGE_PILOT_3
            PHASE_EXPANSION -> PERCENTAGE_EXPANSION
            else -> 0
        }
    }
    
    /**
     * Advance to next phase
     */
    fun advanceToNextPhase(): PhaseAdvanceResult {
        if (isUpdating.get()) {
            return PhaseAdvanceResult.ALREADY_UPDATING
        }
        
        if (!isUpdating.compareAndSet(false, true)) {
            return PhaseAdvanceResult.ALREADY_UPDATING
        }
        
        try {
            val currentPhase = _currentPhase.value
            val nextPhase = getNextPhase(currentPhase)
            
            if (nextPhase == null) {
                return PhaseAdvanceResult.ALREADY_AT_MAX_PHASE
            }
            
            _currentPhase.value = nextPhase
            rolloutPercentage = getRolloutPercentageForPhase(nextPhase)
            
            // Re-determine user segment
            determineUserSegment()
            
            // Save state
            saveState()
            
            logPhaseAdvance(currentPhase, nextPhase)
            return PhaseAdvanceResult.SUCCESS(nextPhase)
            
        } catch (e: Exception) {
            logError("Failed to advance phase", e)
            return PhaseAdvanceResult.ERROR(e.message ?: "Unknown error")
        } finally {
            isUpdating.set(false)
        }
    }
    
    /**
     * Rollback to previous phase
     */
    fun rollbackToPreviousPhase(): PhaseRollbackResult {
        if (isUpdating.get()) {
            return PhaseRollbackResult.ALREADY_UPDATING
        }
        
        try {
            val currentPhase = _currentPhase.value
            val previousPhase = getPreviousPhase(currentPhase)
            
            if (previousPhase == null) {
                return PhaseRollbackResult.ALREADY_AT_MIN_PHASE
            }
            
            _currentPhase.value = previousPhase
            rolloutPercentage = getRolloutPercentageForPhase(previousPhase)
            
            // Re-determine user segment
            determineUserSegment()
            
            // Save state
            saveState()
            
            logPhaseRollback(currentPhase, previousPhase)
            return PhaseRollbackResult.SUCCESS(previousPhase)
            
        } catch (e: Exception) {
            logError("Failed to rollback phase", e)
            return PhaseRollbackResult.ERROR(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Get next phase
     */
    private fun getNextPhase(currentPhase: String): String? {
        return when (currentPhase) {
            PHASE_INTERNAL -> PHASE_PILOT_1
            PHASE_PILOT_1 -> PHASE_PILOT_2
            PHASE_PILOT_2 -> PHASE_PILOT_3
            PHASE_PILOT_3 -> PHASE_EXPANSION
            PHASE_EXPANSION -> null
            else -> null
        }
    }
    
    /**
     * Get previous phase
     */
    private fun getPreviousPhase(currentPhase: String): String? {
        return when (currentPhase) {
            PHASE_INTERNAL -> null
            PHASE_PILOT_1 -> PHASE_INTERNAL
            PHASE_PILOT_2 -> PHASE_PILOT_1
            PHASE_PILOT_3 -> PHASE_PILOT_2
            PHASE_EXPANSION -> PHASE_PILOT_3
            else -> null
        }
    }
    
    /**
     * Get rollout percentage for phase
     */
    private fun getRolloutPercentageForPhase(phase: String): Int {
        return when (phase) {
            PHASE_INTERNAL -> PERCENTAGE_INTERNAL
            PHASE_PILOT_1 -> PERCENTAGE_PILOT_1
            PHASE_PILOT_2 -> PERCENTAGE_PILOT_2
            PHASE_PILOT_3 -> PERCENTAGE_PILOT_3
            PHASE_EXPANSION -> PERCENTAGE_EXPANSION
            else -> 0
        }
    }
    
    /**
     * Save state to preferences
     */
    private fun saveState() {
        lastUpdate = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_CURRENT_PHASE, _currentPhase.value)
            .putString(KEY_USER_SEGMENT, _userSegment.value)
            .putString(KEY_AB_TEST_GROUP, _abTestGroup.value)
            .putInt(KEY_ROLLOUT_PERCENTAGE, rolloutPercentage)
            .putLong(KEY_LAST_UPDATE, lastUpdate)
            .apply()
    }
    
    /**
     * Get ramp plan status
     */
    fun getRampPlanStatus(): Map<String, Any> {
        return mapOf(
            "current_phase" to _currentPhase.value,
            "user_segment" to _userSegment.value,
            "ab_test_group" to _abTestGroup.value,
            "rollout_percentage" to rolloutPercentage,
            "has_feature_access" to hasFeatureAccess(),
            "last_update" to lastUpdate
        )
    }
    
    /**
     * Get phase information
     */
    fun getPhaseInfo(phase: String): PhaseInfo? {
        return when (phase) {
            PHASE_INTERNAL -> PhaseInfo("Internal", PERCENTAGE_INTERNAL, "Internal testing only")
            PHASE_PILOT_1 -> PhaseInfo("Pilot 1", PERCENTAGE_PILOT_1, "Limited pilot with 5% of users")
            PHASE_PILOT_2 -> PhaseInfo("Pilot 2", PERCENTAGE_PILOT_2, "Expanded pilot with 25% of users")
            PHASE_PILOT_3 -> PhaseInfo("Pilot 3", PERCENTAGE_PILOT_3, "Full pilot with 50% of users")
            PHASE_EXPANSION -> PhaseInfo("Expansion", PERCENTAGE_EXPANSION, "Full rollout to 100% of users")
            else -> null
        }
    }
    
    /**
     * Log phase advance
     */
    private fun logPhaseAdvance(fromPhase: String, toPhase: String) {
        android.util.Log.i("RampPlanManager", "Phase advanced: $fromPhase -> $toPhase")
    }
    
    /**
     * Log phase rollback
     */
    private fun logPhaseRollback(fromPhase: String, toPhase: String) {
        android.util.Log.i("RampPlanManager", "Phase rolled back: $fromPhase -> $toPhase")
    }
    
    /**
     * Log error
     */
    private fun logError(message: String, throwable: Throwable) {
        android.util.Log.e("RampPlanManager", message, throwable)
    }
    
    /**
     * Phase advance result
     */
    sealed class PhaseAdvanceResult {
        object ALREADY_UPDATING : PhaseAdvanceResult()
        object ALREADY_AT_MAX_PHASE : PhaseAdvanceResult()
        data class SUCCESS(val newPhase: String) : PhaseAdvanceResult()
        data class ERROR(val message: String) : PhaseAdvanceResult()
    }
    
    /**
     * Phase rollback result
     */
    sealed class PhaseRollbackResult {
        object ALREADY_UPDATING : PhaseRollbackResult()
        object ALREADY_AT_MIN_PHASE : PhaseRollbackResult()
        data class SUCCESS(val newPhase: String) : PhaseRollbackResult()
        data class ERROR(val message: String) : PhaseRollbackResult()
    }
    
    /**
     * Phase information
     */
    data class PhaseInfo(
        val name: String,
        val percentage: Int,
        val description: String
    )
}
