package com.frozo.ambientscribe.telemetry

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Metrics Aggregator for PT-8 implementation
 * Handles KPI calculations and metrics aggregation
 */
class MetricsAggregator {
    
    private val encounterMetrics = ConcurrentHashMap<String, EncounterMetrics>()
    private val clinicMetrics = ConcurrentHashMap<String, ClinicMetrics>()
    private val deviceTierMetrics = ConcurrentHashMap<String, DeviceTierMetrics>()
    
    // Pilot mode metrics (ST-8.2, ST-8.9)
    private val pilotMetrics = PilotModeMetrics()
    
    // Proxy metrics for real consults (ST-8.12)
    private val proxyMetrics = ProxyMetrics()
    
    /**
     * Update metrics based on telemetry event
     */
    fun updateMetrics(event: TelemetryEvent) {
        when (event) {
            is EncounterStartEvent -> {
                encounterMetrics[event.encounterId] = EncounterMetrics(
                    encounterId = event.encounterId,
                    clinicId = event.clinicId,
                    deviceTier = event.deviceTier,
                    startTime = Instant.parse(event.timestamp)
                )
            }
            
            is TranscriptionCompleteEvent -> {
                encounterMetrics[event.encounterId]?.let { metrics ->
                    metrics.werEstimate = event.werEstimate
                    metrics.processingTimeMs = event.processingTimeMs
                    metrics.modelVersion = event.modelVersion
                    metrics.audioDurationMs = event.audioDurationMs
                    metrics.confidenceScore = event.confidenceScore
                    metrics.languageDetected = event.languageDetected
                }
                
                // Update pilot mode metrics if enabled
                if (pilotMetrics.isEnabled) {
                    pilotMetrics.addTranscriptionEvent(event)
                }
            }
            
            is ReviewCompleteEvent -> {
                encounterMetrics[event.encounterId]?.let { metrics ->
                    metrics.editRatePercent = event.editRatePercent
                    metrics.reviewDurationS = event.reviewDurationS
                    metrics.confidenceOverrides = event.confidenceOverrides
                    metrics.totalEdits = event.totalEdits
                    metrics.prescriptionEdits = event.prescriptionEdits
                    metrics.soapEdits = event.soapEdits
                    metrics.redFlagsResolved = event.redFlagsResolved
                    metrics.completed = true
                }
                
                // Update proxy metrics for real consults
                proxyMetrics.addReviewEvent(event)
            }
            
            is ExportSuccessEvent -> {
                encounterMetrics[event.encounterId]?.let { metrics ->
                    metrics.pdfSizeKb = event.pdfSizeKb
                    metrics.exportDurationMs = event.exportDurationMs
                    metrics.batteryLevelPercent = event.batteryLevelPercent
                    metrics.exported = true
                }
            }
            
            is ThermalEvent -> {
                encounterMetrics[event.encounterId]?.let { metrics ->
                    metrics.thermalEvents.add(event)
                }
            }
            
            is EditCauseCodeEvent -> {
                encounterMetrics[event.encounterId]?.let { metrics ->
                    metrics.editCauseCodes.add(event)
                }
            }
            
            is BulkEditAppliedEvent -> {
                // Handle bulk edit events
            }
            
            is CrashFreeSessionEvent -> {
                // Handle crash-free session events
            }
            
            is PolicyToggleEvent -> {
                // Handle policy toggle events
            }
            
            is TimeSkewEvent -> {
                // Handle time skew events
            }
        }
        
        // Update clinic and device tier metrics
        updateClinicMetrics(event)
        updateDeviceTierMetrics(event)
    }
    
    /**
     * Get aggregated metrics
     */
    fun getMetrics(): AggregatedMetrics {
        val now = Instant.now()
        val last24Hours = now.minus(24, ChronoUnit.HOURS)
        
        // Filter recent encounters
        val recentEncounters = encounterMetrics.values.filter { 
            it.startTime.isAfter(last24Hours) 
        }
        
        return AggregatedMetrics(
            totalEncounters = encounterMetrics.size,
            recentEncounters = recentEncounters.size,
            completedEncounters = recentEncounters.count { it.completed },
            exportedEncounters = recentEncounters.count { it.exported },
            averageWER = calculateAverageWER(recentEncounters),
            averageProcessingTime = calculateAverageProcessingTime(recentEncounters),
            averageEditRate = calculateAverageEditRate(recentEncounters),
            averageReviewDuration = calculateAverageReviewDuration(recentEncounters),
            thermalEventCount = recentEncounters.sumOf { it.thermalEvents.size },
            pilotMetrics = if (pilotMetrics.isEnabled) pilotMetrics.getSummary() else null,
            proxyMetrics = proxyMetrics.getSummary(),
            clinicMetrics = clinicMetrics.values.toList(),
            deviceTierMetrics = deviceTierMetrics.values.toList(),
            lastUpdated = now.toString()
        )
    }
    
    /**
     * Update clinic-level metrics
     */
    private fun updateClinicMetrics(event: TelemetryEvent) {
        val clinicId = event.clinicId ?: return
        
        clinicMetrics.computeIfAbsent(clinicId) { 
            ClinicMetrics(clinicId = clinicId) 
        }.let { metrics ->
            when (event) {
                is EncounterStartEvent -> {
                    metrics.totalEncounters++
                    metrics.deviceTierDistribution[event.deviceTier] = 
                        metrics.deviceTierDistribution.getOrDefault(event.deviceTier, 0) + 1
                }
                is ReviewCompleteEvent -> {
                    metrics.totalEditRate += event.editRatePercent
                    metrics.totalPrescriptionEdits += event.prescriptionEdits
                    metrics.totalSOAPEdits += event.soapEdits
                    metrics.totalConfidenceOverrides += event.confidenceOverrides
                }
                is ExportSuccessEvent -> {
                    metrics.totalExports++
                    metrics.totalPdfSize += event.pdfSizeKb
                }
                is TranscriptionCompleteEvent -> {
                    // Handle transcription events
                }
                is ThermalEvent -> {
                    // Handle thermal events
                }
                is EditCauseCodeEvent -> {
                    // Handle edit cause code events
                }
                is BulkEditAppliedEvent -> {
                    // Handle bulk edit events
                }
                is CrashFreeSessionEvent -> {
                    // Handle crash-free session events
                }
                is PolicyToggleEvent -> {
                    // Handle policy toggle events
                }
                is TimeSkewEvent -> {
                    // Handle time skew events
                }
            }
        }
    }
    
    /**
     * Update device tier metrics
     */
    private fun updateDeviceTierMetrics(event: TelemetryEvent) {
        deviceTierMetrics.computeIfAbsent(event.deviceTier) { 
            DeviceTierMetrics(deviceTier = event.deviceTier) 
        }.let { metrics ->
            when (event) {
                is EncounterStartEvent -> {
                    metrics.totalEncounters++
                }
                is TranscriptionCompleteEvent -> {
                    metrics.totalWER += event.werEstimate
                    metrics.totalProcessingTime += event.processingTimeMs
                    metrics.transcriptionCount++
                }
                is ReviewCompleteEvent -> {
                    metrics.totalEditRate += event.editRatePercent
                    metrics.totalReviewDuration += event.reviewDurationS
                    metrics.reviewCount++
                }
                is ExportSuccessEvent -> {
                    metrics.totalExports++
                    metrics.totalPdfSize += event.pdfSizeKb
                }
                is ThermalEvent -> {
                    metrics.thermalEventCount++
                    if (event.thermalState == "SEVERE") {
                        metrics.severeThermalCount++
                    } else {
                        // Not severe thermal state
                    }
                }
                is EditCauseCodeEvent -> {
                    // Handle edit cause code events
                }
                is BulkEditAppliedEvent -> {
                    // Handle bulk edit events
                }
                is CrashFreeSessionEvent -> {
                    // Handle crash-free session events
                }
                is PolicyToggleEvent -> {
                    // Handle policy toggle events
                }
                is TimeSkewEvent -> {
                    // Handle time skew events
                }
            }
        }
    }
    
    /**
     * Calculate average WER across encounters
     */
    private fun calculateAverageWER(encounters: Collection<EncounterMetrics>): Double {
        val validWERs = encounters.mapNotNull { it.werEstimate }.filter { it >= 0 }
        return if (validWERs.isNotEmpty()) {
            validWERs.average()
        } else {
            0.0
        }
    }
    
    /**
     * Calculate average processing time
     */
    private fun calculateAverageProcessingTime(encounters: Collection<EncounterMetrics>): Double {
        val validTimes = encounters.mapNotNull { it.processingTimeMs }.filter { it > 0 }
        return if (validTimes.isNotEmpty()) {
            validTimes.average()
        } else {
            0.0
        }
    }
    
    /**
     * Calculate average edit rate
     */
    private fun calculateAverageEditRate(encounters: Collection<EncounterMetrics>): Double {
        val validRates = encounters.mapNotNull { it.editRatePercent }.filter { it >= 0 }
        return if (validRates.isNotEmpty()) {
            validRates.average()
        } else {
            0.0
        }
    }
    
    /**
     * Calculate average review duration
     */
    private fun calculateAverageReviewDuration(encounters: Collection<EncounterMetrics>): Double {
        val validDurations = encounters.mapNotNull { it.reviewDurationS }.filter { it > 0 }
        return if (validDurations.isNotEmpty()) {
            validDurations.average()
        } else {
            0.0
        }
    }
    
    /**
     * Update aggregated metrics (called periodically)
     */
    fun updateAggregatedMetrics() {
        // Calculate running averages for clinic metrics
        clinicMetrics.values.forEach { metrics ->
            if (metrics.totalEncounters > 0) {
                metrics.averageEditRate = metrics.totalEditRate.toDouble() / metrics.totalEncounters
                metrics.averagePdfSize = if (metrics.totalExports > 0) {
                    metrics.totalPdfSize.toDouble() / metrics.totalExports
                } else 0.0
            }
        }
        
        // Calculate running averages for device tier metrics
        deviceTierMetrics.values.forEach { metrics ->
            if (metrics.transcriptionCount > 0) {
                metrics.averageWER = metrics.totalWER.toDouble() / metrics.transcriptionCount
                metrics.averageProcessingTime = metrics.totalProcessingTime.toDouble() / metrics.transcriptionCount
            }
            if (metrics.reviewCount > 0) {
                metrics.averageEditRate = metrics.totalEditRate.toDouble() / metrics.reviewCount
                metrics.averageReviewDuration = metrics.totalReviewDuration.toDouble() / metrics.reviewCount
            }
        }
    }
}

/**
 * Data classes for metrics aggregation
 */

data class EncounterMetrics(
    val encounterId: String,
    val clinicId: String?,
    val deviceTier: String,
    val startTime: Instant,
    var werEstimate: Double? = null,
    var processingTimeMs: Long? = null,
    var modelVersion: String? = null,
    var audioDurationMs: Long? = null,
    var confidenceScore: Double? = null,
    var languageDetected: String? = null,
    var editRatePercent: Double? = null,
    var reviewDurationS: Long? = null,
    var confidenceOverrides: Int = 0,
    var totalEdits: Int = 0,
    var prescriptionEdits: Int = 0,
    var soapEdits: Int = 0,
    var redFlagsResolved: Int = 0,
    var pdfSizeKb: Long? = null,
    var exportDurationMs: Long? = null,
    var batteryLevelPercent: Int? = null,
    var completed: Boolean = false,
    var exported: Boolean = false,
    val thermalEvents: MutableList<ThermalEvent> = mutableListOf(),
    val editCauseCodes: MutableList<EditCauseCodeEvent> = mutableListOf()
)

data class ClinicMetrics(
    val clinicId: String,
    var totalEncounters: Int = 0,
    var totalEditRate: Double = 0.0,
    var totalPrescriptionEdits: Int = 0,
    var totalSOAPEdits: Int = 0,
    var totalConfidenceOverrides: Int = 0,
    var totalExports: Int = 0,
    var totalPdfSize: Long = 0,
    var averageEditRate: Double = 0.0,
    var averagePdfSize: Double = 0.0,
    val deviceTierDistribution: MutableMap<String, Int> = mutableMapOf()
)

data class DeviceTierMetrics(
    val deviceTier: String,
    var totalEncounters: Int = 0,
    var transcriptionCount: Int = 0,
    var reviewCount: Int = 0,
    var totalExports: Int = 0,
    var totalWER: Double = 0.0,
    var totalProcessingTime: Long = 0,
    var totalEditRate: Double = 0.0,
    var totalReviewDuration: Long = 0,
    var totalPdfSize: Long = 0,
    var thermalEventCount: Int = 0,
    var severeThermalCount: Int = 0,
    var averageWER: Double = 0.0,
    var averageProcessingTime: Double = 0.0,
    var averageEditRate: Double = 0.0,
    var averageReviewDuration: Double = 0.0
)

data class AggregatedMetrics(
    val totalEncounters: Int,
    val recentEncounters: Int,
    val completedEncounters: Int,
    val exportedEncounters: Int,
    val averageWER: Double,
    val averageProcessingTime: Double,
    val averageEditRate: Double,
    val averageReviewDuration: Double,
    val thermalEventCount: Int,
    val pilotMetrics: PilotMetricsSummary?,
    val proxyMetrics: ProxyMetricsSummary,
    val clinicMetrics: List<ClinicMetrics>,
    val deviceTierMetrics: List<DeviceTierMetrics>,
    val lastUpdated: String
)

data class PilotMetricsSummary(
    val totalTranscriptions: Int,
    val averageWER: Double,
    val averageF1Score: Double,
    val averageConfidence: Double,
    val modelVersions: Map<String, Int>,
    val languageDistribution: Map<String, Int>
)

data class ProxyMetricsSummary(
    val totalReviews: Int,
    val averageEditRate: Double,
    val averagePrescriptionConfirmRate: Double,
    val averageSOAPConfirmRate: Double,
    val totalConfidenceOverrides: Int,
    val redFlagsResolved: Int
)
