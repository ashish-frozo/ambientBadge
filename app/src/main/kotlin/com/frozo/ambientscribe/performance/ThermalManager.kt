package com.frozo.ambientscribe.performance

import android.content.Context
import android.os.*
import android.os.Process
import android.system.Os
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * Thermal management system that monitors CPU usage and temperature
 * to dynamically adjust thread count and performance parameters.
 */
class ThermalManager(
    private val context: Context,
    private val highThresholdPercent: Int = 85,
    private val recoveryThresholdPercent: Int = 60,
    private val monitoringIntervalMs: Long = 1000,
    private val highTempDurationThresholdMs: Long = 10000,
    private val recoveryDurationThresholdMs: Long = 30000
) {
    companion object {
        // Thread count limits
        private const val MIN_THREADS = 2
        private const val MAX_THREADS = 6
        private const val DEFAULT_THREADS = 4
        
        // CPU core paths
        private const val CPU_PRESENT_PATH = "/sys/devices/system/cpu/present"
        private const val CPU_ONLINE_PATH = "/sys/devices/system/cpu/online"
        private const val CPU_FREQ_PATH_FORMAT = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq"
        private const val CPU_TEMP_PATH = "/sys/class/thermal/thermal_zone0/temp"
        
        // Thermal states
        private const val THERMAL_STATE_NORMAL = 0
        private const val THERMAL_STATE_MODERATE = 1
        private const val THERMAL_STATE_SEVERE = 2
        
        // Context size limits based on thermal state
        private const val NORMAL_CONTEXT_SIZE = 3000
        private const val MODERATE_CONTEXT_SIZE = 2000
        private const val SEVERE_CONTEXT_SIZE = 1000
    }

    private val isMonitoring = AtomicBoolean(false)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null
    
    private val currentThreadCount = AtomicInteger(DEFAULT_THREADS)
    private val currentThermalState = AtomicInteger(THERMAL_STATE_NORMAL)
    private val currentCpuUsage = AtomicInteger(0)
    private val currentTemperature = AtomicInteger(0)
    private val currentContextSize = AtomicInteger(NORMAL_CONTEXT_SIZE)
    
    private val thermalStateChannel = Channel<ThermalState>(Channel.CONFLATED)
    private val deviceTierRef = AtomicReference(DeviceTier.TIER_A)
    
    // CPU usage tracking
    private var prevTotalCpuTime = 0L
    private var prevIdleCpuTime = 0L
    private val cpuCoreCount = getAvailableCpuCores()
    
    // Thermal state tracking
    private var highTempStartTime = 0L
    private var recoveryStartTime = 0L
    
    // Registered components for thermal state updates
    private val registeredComponents = ConcurrentHashMap<String, ThermalStateListener>()
    
    /**
     * Device tier classification
     */
    enum class DeviceTier {
        TIER_A,  // High-end devices
        TIER_B   // Mid/low-end devices
    }
    
    /**
     * Thermal state information
     */
    data class ThermalState(
        val thermalLevel: Int,
        val cpuUsagePercent: Int,
        val temperatureCelsius: Float,
        val recommendedThreads: Int,
        val recommendedContextSize: Int,
        val timestamp: Long
    )
    
    /**
     * Interface for components that need thermal state updates
     */
    interface ThermalStateListener {
        fun onThermalStateChanged(state: ThermalState)
    }
    
    /**
     * Start monitoring thermal and CPU conditions
     */
    fun startMonitoring() {
        if (isMonitoring.getAndSet(true)) {
            return
        }
        
        Timber.d("Starting thermal monitoring with interval: $monitoringIntervalMs ms")
        
        monitoringJob = coroutineScope.launch {
            monitorLoop()
        }
    }
    
    /**
     * Stop monitoring thermal and CPU conditions
     */
    fun stopMonitoring() {
        if (!isMonitoring.getAndSet(false)) {
            return
        }
        
        monitoringJob?.cancel()
        monitoringJob = null
        
        Timber.d("Thermal monitoring stopped")
    }
    
    /**
     * Register a component to receive thermal state updates
     */
    fun registerComponent(componentId: String, listener: ThermalStateListener) {
        registeredComponents[componentId] = listener
        Timber.d("Component registered for thermal updates: $componentId")
        
        // Send current state immediately
        val currentState = getCurrentThermalState()
        listener.onThermalStateChanged(currentState)
    }
    
    /**
     * Unregister a component from thermal state updates
     */
    fun unregisterComponent(componentId: String) {
        registeredComponents.remove(componentId)
        Timber.d("Component unregistered from thermal updates: $componentId")
    }
    
    /**
     * Get flow of thermal state changes
     */
    fun getThermalStateFlow(): Flow<ThermalState> = flow {
        while (isMonitoring.get()) {
            try {
                val state = thermalStateChannel.receive()
                emit(state)
            } catch (e: Exception) {
                Timber.w(e, "Error in thermal state flow")
                break
            }
        }
    }
    
    /**
     * Get current thermal state
     */
    fun getCurrentThermalState(): ThermalState {
        return ThermalState(
            thermalLevel = currentThermalState.get(),
            cpuUsagePercent = currentCpuUsage.get(),
            temperatureCelsius = currentTemperature.get() / 1000f,
            recommendedThreads = currentThreadCount.get(),
            recommendedContextSize = currentContextSize.get(),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Set device tier based on device capabilities
     */
    fun setDeviceTier(tier: DeviceTier) {
        deviceTierRef.set(tier)
        
        // Adjust thread count based on tier
        val initialThreads = when (tier) {
            DeviceTier.TIER_A -> min(MAX_THREADS, cpuCoreCount - 2) // Leave 2 cores for system
            DeviceTier.TIER_B -> MIN_THREADS
        }
        
        currentThreadCount.set(max(MIN_THREADS, initialThreads))
        Timber.i("Device tier set to $tier, initial thread count: ${currentThreadCount.get()}")
    }
    
    /**
     * Get recommended thread count based on current thermal state
     */
    fun getRecommendedThreadCount(): Int {
        return currentThreadCount.get()
    }
    
    /**
     * Get recommended context size based on current thermal state
     */
    fun getRecommendedContextSize(): Int {
        return currentContextSize.get()
    }
    
    /**
     * Main monitoring loop
     */
    private suspend fun monitorLoop() = withContext(Dispatchers.Default) {
        // Set thread priority for monitoring
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        
        Timber.d("Thermal monitoring loop started")
        
        while (isMonitoring.get() && isActive) {
            try {
                // Monitor CPU usage
                val cpuUsage = monitorCpuUsage()
                currentCpuUsage.set(cpuUsage)
                
                // Monitor temperature
                val temperature = monitorTemperature()
                currentTemperature.set(temperature)
                
                // Update thermal state
                updateThermalState(cpuUsage, temperature)
                
                // Emit current state
                val currentState = getCurrentThermalState()
                thermalStateChannel.trySend(currentState)
                
                // Notify registered components
                notifyComponents(currentState)
                
                // Log thermal state periodically
                if (currentThermalState.get() != THERMAL_STATE_NORMAL) {
                    Timber.d("Thermal state: ${currentThermalState.get()}, " +
                            "CPU: $cpuUsage%, Temp: ${temperature/1000f}°C, " +
                            "Threads: ${currentThreadCount.get()}")
                }
                
                // Wait for next monitoring interval
                delay(monitoringIntervalMs)
                
            } catch (e: Exception) {
                Timber.e(e, "Error in thermal monitoring loop")
                delay(monitoringIntervalMs * 2) // Wait longer on error
            }
        }
        
        Timber.d("Thermal monitoring loop ended")
    }
    
    /**
     * Monitor CPU usage percentage
     */
    private fun monitorCpuUsage(): Int {
        try {
            val reader = BufferedReader(FileReader("/proc/stat"))
            val cpuLine = reader.readLine()
            reader.close()
            
            val cpuInfo = cpuLine.split("\\s+".toRegex())
            
            val user = cpuInfo[1].toLong()
            val nice = cpuInfo[2].toLong()
            val system = cpuInfo[3].toLong()
            val idle = cpuInfo[4].toLong()
            val iowait = cpuInfo[5].toLong()
            val irq = cpuInfo[6].toLong()
            val softirq = cpuInfo[7].toLong()
            val steal = cpuInfo[8].toLong()
            
            val totalCpuTime = user + nice + system + idle + iowait + irq + softirq + steal
            val idleCpuTime = idle + iowait
            
            var cpuUsage = 0
            
            if (prevTotalCpuTime > 0 && prevIdleCpuTime > 0) {
                val totalDiff = totalCpuTime - prevTotalCpuTime
                val idleDiff = idleCpuTime - prevIdleCpuTime
                
                cpuUsage = ((totalDiff - idleDiff) * 100 / totalDiff).toInt()
            }
            
            prevTotalCpuTime = totalCpuTime
            prevIdleCpuTime = idleCpuTime
            
            return cpuUsage
            
        } catch (e: Exception) {
            Timber.w(e, "Error monitoring CPU usage")
            return 0
        }
    }
    
    /**
     * Monitor CPU temperature in milliCelsius
     */
    private fun monitorTemperature(): Int {
        try {
            val tempFile = File(CPU_TEMP_PATH)
            if (!tempFile.exists()) {
                // Try alternative thermal zones
                for (i in 0 until 10) {
                    val altPath = "/sys/class/thermal/thermal_zone$i/temp"
                    val altFile = File(altPath)
                    if (altFile.exists()) {
                        val temp = BufferedReader(FileReader(altFile)).use { it.readLine() }.toInt()
                        return temp
                    }
                }
                return 0
            }
            
            val temp = BufferedReader(FileReader(tempFile)).use { it.readLine() }.toInt()
            return temp
            
        } catch (e: Exception) {
            Timber.w(e, "Error monitoring temperature")
            return 0
        }
    }
    
    /**
     * Update thermal state based on CPU usage and temperature
     */
    private fun updateThermalState(cpuUsage: Int, temperature: Int) {
        val currentTime = System.currentTimeMillis()
        val currentState = currentThermalState.get()
        
        // Check for high CPU usage
        if (cpuUsage > highThresholdPercent) {
            if (highTempStartTime == 0L) {
                highTempStartTime = currentTime
            } else if (currentTime - highTempStartTime >= highTempDurationThresholdMs) {
                // Sustained high CPU usage
                if (currentState < THERMAL_STATE_MODERATE) {
                    updateToModerateState()
                }
            }
            
            // Reset recovery timer
            recoveryStartTime = 0L
            
        } else if (cpuUsage < recoveryThresholdPercent) {
            if (recoveryStartTime == 0L) {
                recoveryStartTime = currentTime
            } else if (currentTime - recoveryStartTime >= recoveryDurationThresholdMs) {
                // Sustained low CPU usage
                if (currentState > THERMAL_STATE_NORMAL) {
                    updateToNormalState()
                }
            }
            
            // Reset high temp timer
            highTempStartTime = 0L
            
        } else {
            // In between thresholds, reset both timers
            highTempStartTime = 0L
            recoveryStartTime = 0L
        }
        
        // Check for extreme temperature (immediate action)
        val tempCelsius = temperature / 1000f
        if (tempCelsius > 45f && currentState < THERMAL_STATE_SEVERE) {
            updateToSevereState()
        }
    }
    
    /**
     * Update to normal thermal state
     */
    private fun updateToNormalState() {
        val previousState = currentThermalState.getAndSet(THERMAL_STATE_NORMAL)
        if (previousState != THERMAL_STATE_NORMAL) {
            // Restore normal thread count based on device tier
            val normalThreads = when (deviceTierRef.get()) {
                DeviceTier.TIER_A -> min(MAX_THREADS, cpuCoreCount - 2)
                DeviceTier.TIER_B -> min(4, cpuCoreCount - 1)
            }
            
            currentThreadCount.set(max(MIN_THREADS, normalThreads))
            currentContextSize.set(NORMAL_CONTEXT_SIZE)
            
            Timber.i("Thermal state changed to NORMAL, thread count: ${currentThreadCount.get()}, " +
                    "context size: ${currentContextSize.get()}")
        }
    }
    
    /**
     * Update to moderate thermal state
     */
    private fun updateToModerateState() {
        val previousState = currentThermalState.getAndSet(THERMAL_STATE_MODERATE)
        if (previousState != THERMAL_STATE_MODERATE) {
            // Reduce thread count
            val moderateThreads = when (deviceTierRef.get()) {
                DeviceTier.TIER_A -> min(4, cpuCoreCount - 2)
                DeviceTier.TIER_B -> MIN_THREADS
            }
            
            currentThreadCount.set(max(MIN_THREADS, moderateThreads))
            currentContextSize.set(MODERATE_CONTEXT_SIZE)
            
            Timber.w("Thermal state changed to MODERATE, thread count: ${currentThreadCount.get()}, " +
                    "context size: ${currentContextSize.get()}")
        }
    }
    
    /**
     * Update to severe thermal state
     */
    private fun updateToSevereState() {
        val previousState = currentThermalState.getAndSet(THERMAL_STATE_SEVERE)
        if (previousState != THERMAL_STATE_SEVERE) {
            // Minimum thread count
            currentThreadCount.set(MIN_THREADS)
            currentContextSize.set(SEVERE_CONTEXT_SIZE)
            
            Timber.e("Thermal state changed to SEVERE, thread count: ${currentThreadCount.get()}, " +
                    "context size: ${currentContextSize.get()}")
        }
    }
    
    /**
     * Notify registered components of thermal state change
     */
    private fun notifyComponents(state: ThermalState) {
        for ((_, listener) in registeredComponents) {
            try {
                listener.onThermalStateChanged(state)
            } catch (e: Exception) {
                Timber.w(e, "Error notifying component of thermal state change")
            }
        }
    }
    
    /**
     * Get available CPU cores count
     */
    private fun getAvailableCpuCores(): Int {
        try {
            // Try to get from Runtime
            val runtimeCores = Runtime.getRuntime().availableProcessors()
            if (runtimeCores > 0) {
                return runtimeCores
            }
            
            // Try to read from sysfs
            val cpuPresentFile = File(CPU_PRESENT_PATH)
            if (cpuPresentFile.exists()) {
                val content = BufferedReader(FileReader(cpuPresentFile)).use { it.readLine() }
                val range = content.split("-")
                if (range.size == 2) {
                    val min = range[0].toInt()
                    val max = range[1].toInt()
                    return max - min + 1
                }
            }
            
            // Default to 4 cores if detection fails
            return 4
            
        } catch (e: Exception) {
            Timber.w(e, "Error detecting CPU cores")
            return 4 // Default to 4 cores
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopMonitoring()
        registeredComponents.clear()
        thermalStateChannel.close()
        coroutineScope.cancel()
        
        Timber.d("ThermalManager cleanup completed")
    }
    
    /**
     * Simulate a thermal state for testing
     * This method is only used in tests and should not be called in production code
     */
    @VisibleForTesting
    fun simulateThermalState(state: ThermalState) {
        // Map the ThermalState enum to the internal thermal state integer
        val internalState = when (state.thermalLevel) {
            THERMAL_STATE_NORMAL -> THERMAL_STATE_NORMAL
            THERMAL_STATE_MODERATE -> THERMAL_STATE_MODERATE
            THERMAL_STATE_SEVERE -> THERMAL_STATE_SEVERE
            else -> THERMAL_STATE_NORMAL
        }
        
        // Update internal state
        currentThermalState.set(internalState)
        currentCpuUsage.set(state.cpuUsagePercent)
        currentTemperature.set((state.temperatureCelsius * 1000).toInt())
        
        // Update thread count and context size based on thermal state
        when (internalState) {
            THERMAL_STATE_NORMAL -> updateToNormalState()
            THERMAL_STATE_MODERATE -> updateToModerateState()
            THERMAL_STATE_SEVERE -> updateToSevereState()
        }
        
        // Create new state object
        val updatedState = getCurrentThermalState()
        
        // Notify registered components
        notifyComponents(updatedState)
        
        // Send to channel
        thermalStateChannel.trySend(updatedState)
        
        Timber.d("Simulated thermal state: ${state.thermalLevel}, threads: ${currentThreadCount.get()}")
    }
}
