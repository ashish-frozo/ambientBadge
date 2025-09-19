package com.frozo.ambientscribe.performance

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Performance management system that integrates thermal monitoring with
 * adaptive threading for optimal performance across device tiers.
 */
class PerformanceManager(
    private val context: Context,
    private val thermalManager: ThermalManager = ThermalManager(context),
    private val deviceCapabilityDetector: DeviceCapabilityDetector = DeviceCapabilityDetector(context)
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isInitialized = AtomicBoolean(false)
    
    private val _performanceState = MutableStateFlow(PerformanceState())
    val performanceState: StateFlow<PerformanceState> = _performanceState
    
    private val currentThreadCount = AtomicInteger(4)
    private val currentContextSize = AtomicInteger(3000)
    
    /**
     * Performance state information
     */
    data class PerformanceState(
        val deviceTier: ThermalManager.DeviceTier = ThermalManager.DeviceTier.TIER_A,
        val thermalState: Int = 0,
        val cpuUsagePercent: Int = 0,
        val temperatureCelsius: Float = 0f,
        val recommendedThreads: Int = 4,
        val recommendedContextSize: Int = 3000,
        val instructionSets: Map<String, Boolean> = mapOf(
            "NEON" to true,
            "FP16" to false,
            "SDOT" to false
        ),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Interface for components that need performance updates
     */
    interface PerformanceAwareComponent {
        fun onPerformanceStateChanged(state: PerformanceState)
    }
    
    /**
     * Initialize the performance management system
     */
    fun initialize() {
        if (isInitialized.getAndSet(true)) {
            return
        }
        
        Timber.d("Initializing PerformanceManager")
        
        // Detect device tier
        val deviceTier = deviceCapabilityDetector.detectDeviceTier()
        
        // Detect instruction sets
        val instructionSets = deviceCapabilityDetector.detectAdvancedInstructionSets()
        
        // Set initial thread count based on device capabilities
        val initialThreads = deviceCapabilityDetector.detectOptimalThreadCount()
        currentThreadCount.set(initialThreads)
        
        // Configure thermal manager
        thermalManager.setDeviceTier(deviceTier)
        
        // Register for thermal updates
        thermalManager.registerComponent("performance-manager", object : ThermalManager.ThermalStateListener {
            override fun onThermalStateChanged(state: ThermalManager.ThermalState) {
                handleThermalStateChange(state)
            }
        })
        
        // Start thermal monitoring
        thermalManager.startMonitoring()
        
        // Update initial state
        updatePerformanceState(deviceTier, instructionSets)
        
        Timber.i("PerformanceManager initialized with device tier: $deviceTier, " +
                "threads: $initialThreads, instruction sets: $instructionSets")
    }
    
    /**
     * Get recommended thread count for optimal performance
     */
    fun getRecommendedThreadCount(): Int {
        return currentThreadCount.get()
    }
    
    /**
     * Get recommended context size for optimal performance
     */
    fun getRecommendedContextSize(): Int {
        return currentContextSize.get()
    }
    
    /**
     * Get thermal state flow for monitoring
     */
    fun getThermalStateFlow(): Flow<ThermalManager.ThermalState> {
        return thermalManager.getThermalStateFlow()
    }
    
    /**
     * Check if device supports specific instruction set
     */
    fun supportsInstructionSet(instructionSet: String): Boolean {
        return performanceState.value.instructionSets[instructionSet] ?: false
    }
    
    /**
     * Handle thermal state changes
     */
    private fun handleThermalStateChange(thermalState: ThermalManager.ThermalState) {
        // Update thread count and context size based on thermal state
        currentThreadCount.set(thermalState.recommendedThreads)
        currentContextSize.set(thermalState.recommendedContextSize)
        
        // Update performance state
        val currentState = _performanceState.value
        val updatedState = currentState.copy(
            thermalState = thermalState.thermalLevel,
            cpuUsagePercent = thermalState.cpuUsagePercent,
            temperatureCelsius = thermalState.temperatureCelsius,
            recommendedThreads = thermalState.recommendedThreads,
            recommendedContextSize = thermalState.recommendedContextSize,
            timestamp = thermalState.timestamp
        )
        
        _performanceState.value = updatedState
        
        // Log significant thermal state changes
        if (thermalState.thermalLevel > 0) {
            Timber.w("Thermal state changed: level=${thermalState.thermalLevel}, " +
                    "CPU=${thermalState.cpuUsagePercent}%, " +
                    "temp=${thermalState.temperatureCelsius}°C, " +
                    "threads=${thermalState.recommendedThreads}")
        }
    }
    
    /**
     * Update performance state with device capabilities
     */
    private fun updatePerformanceState(
        deviceTier: ThermalManager.DeviceTier,
        instructionSets: Map<String, Boolean>
    ) {
        val currentState = _performanceState.value
        val updatedState = currentState.copy(
            deviceTier = deviceTier,
            recommendedThreads = currentThreadCount.get(),
            recommendedContextSize = currentContextSize.get(),
            instructionSets = instructionSets,
            timestamp = System.currentTimeMillis()
        )
        
        _performanceState.value = updatedState
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        if (!isInitialized.getAndSet(false)) {
            return
        }
        
        thermalManager.unregisterComponent("performance-manager")
        thermalManager.cleanup()
        coroutineScope.cancel()
        
        Timber.d("PerformanceManager cleanup completed")
    }
}
