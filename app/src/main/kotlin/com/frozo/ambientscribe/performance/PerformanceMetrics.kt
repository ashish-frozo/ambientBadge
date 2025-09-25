package com.frozo.ambientscribe.performance

/**
 * Performance metrics data class
 * Contains various performance measurements and statistics
 */
data class PerformanceMetrics(
    val timestamp: Long = System.currentTimeMillis(),
    val memoryUsage: MemoryUsage,
    val batteryLevel: Float,
    val cpuUsage: Float,
    val thermalState: Int,
    val latency: LatencyMetrics,
    val deviceTier: String,
    val isCharging: Boolean = false,
    val networkType: String = "unknown"
)

/**
 * Memory usage data class
 */
data class MemoryUsage(
    val totalMemory: Long,
    val availableMemory: Long,
    val usedMemory: Long,
    val memoryPressure: Float,
    val heapSize: Long,
    val nativeHeapSize: Long
)

/**
 * Latency metrics data class
 */
data class LatencyMetrics(
    val firstTokenLatency: Long,
    val draftReadyLatency: Long,
    val transcriptionLatency: Long,
    val averageLatency: Long,
    val p95Latency: Long,
    val p99Latency: Long
)
