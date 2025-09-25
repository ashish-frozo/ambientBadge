package com.frozo.ambientscribe.telemetry

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Proxy Metrics Handler for PT-8 implementation
 * Handles proxy metrics for real consults (edit-rate and Rx confirm-rates) (ST-8.12)
 */
class ProxyMetrics {
    
    // Clinic-level metrics
    private val clinicMetrics = ConcurrentHashMap<String, ClinicProxyMetrics>()
    
    // Doctor-level metrics
    private val doctorMetrics = ConcurrentHashMap<String, DoctorProxyMetrics>()
    
    // Device tier metrics
    private val deviceTierMetrics = ConcurrentHashMap<String, DeviceTierProxyMetrics>()
    
    // Overall counters
    private val totalReviews = AtomicLong(0)
    private val totalEditRate = AtomicLong(0) // Sum of all edit rates
    private val totalPrescriptionConfirms = AtomicLong(0)
    private val totalSOAPConfirms = AtomicLong(0)
    private val totalConfidenceOverrides = AtomicInteger(0)
    private val totalRedFlagsResolved = AtomicInteger(0)
    
    /**
     * Add review event to proxy metrics
     */
    fun addReviewEvent(event: ReviewCompleteEvent) {
        totalReviews.incrementAndGet()
        totalEditRate.addAndGet((event.editRatePercent * 100).toLong()) // Convert to basis points
        totalConfidenceOverrides.addAndGet(event.confidenceOverrides)
        totalRedFlagsResolved.addAndGet(event.redFlagsResolved)
        
        // Update clinic metrics
        event.clinicId?.let { clinicId ->
            clinicMetrics.computeIfAbsent(clinicId) { 
                ClinicProxyMetrics(clinicId = clinicId) 
            }.let { metrics ->
                metrics.totalReviews++
                metrics.totalEditRate += event.editRatePercent
                metrics.totalConfidenceOverrides += event.confidenceOverrides
                metrics.totalRedFlagsResolved += event.redFlagsResolved
                metrics.lastUpdated = Instant.now().toString()
            }
        }
        
        // Update device tier metrics
        deviceTierMetrics.computeIfAbsent(event.deviceTier) { 
            DeviceTierProxyMetrics(deviceTier = event.deviceTier) 
        }.let { metrics ->
            metrics.totalReviews++
            metrics.totalEditRate += event.editRatePercent
            metrics.totalConfidenceOverrides += event.confidenceOverrides
            metrics.totalRedFlagsResolved += event.redFlagsResolved
            metrics.lastUpdated = Instant.now().toString()
        }
    }
    
    /**
     * Add prescription confirmation event
     */
    fun addPrescriptionConfirmation(
        encounterId: String,
        clinicId: String?,
        deviceTier: String,
        confirmed: Boolean,
        prescriptionCount: Int
    ) {
        if (confirmed) {
            totalPrescriptionConfirms.addAndGet(prescriptionCount.toLong())
        }
        
        // Update clinic metrics
        clinicId?.let { id ->
            clinicMetrics.computeIfAbsent(id) { 
                ClinicProxyMetrics(clinicId = id) 
            }.let { metrics ->
                if (confirmed) {
                    metrics.totalPrescriptionConfirms += prescriptionCount
                }
                metrics.totalPrescriptionReviews++
                metrics.lastUpdated = Instant.now().toString()
            }
        }
        
        // Update device tier metrics
        deviceTierMetrics.computeIfAbsent(deviceTier) { 
            DeviceTierProxyMetrics(deviceTier = deviceTier) 
        }.let { metrics ->
            if (confirmed) {
                metrics.totalPrescriptionConfirms += prescriptionCount
            }
            metrics.totalPrescriptionReviews++
            metrics.lastUpdated = Instant.now().toString()
        }
    }
    
    /**
     * Add SOAP confirmation event
     */
    fun addSOAPConfirmation(
        encounterId: String,
        clinicId: String?,
        deviceTier: String,
        confirmed: Boolean,
        soapSectionCount: Int
    ) {
        if (confirmed) {
            totalSOAPConfirms.addAndGet(soapSectionCount.toLong())
        }
        
        // Update clinic metrics
        clinicId?.let { id ->
            clinicMetrics.computeIfAbsent(id) { 
                ClinicProxyMetrics(clinicId = id) 
            }.let { metrics ->
                if (confirmed) {
                    metrics.totalSOAPConfirms += soapSectionCount
                }
                metrics.totalSOAPReviews++
                metrics.lastUpdated = Instant.now().toString()
            }
        }
        
        // Update device tier metrics
        deviceTierMetrics.computeIfAbsent(deviceTier) { 
            DeviceTierProxyMetrics(deviceTier = deviceTier) 
        }.let { metrics ->
            if (confirmed) {
                metrics.totalSOAPConfirms += soapSectionCount
            }
            metrics.totalSOAPReviews++
            metrics.lastUpdated = Instant.now().toString()
        }
    }
    
    /**
     * Get proxy metrics summary
     */
    fun getSummary(): ProxyMetricsSummary {
        val totalReviewsCount = totalReviews.get()
        
        return ProxyMetricsSummary(
            totalReviews = totalReviewsCount.toInt(),
            averageEditRate = if (totalReviewsCount > 0) {
                totalEditRate.get() / (totalReviewsCount * 100.0) // Convert back from basis points
            } else 0.0,
            averagePrescriptionConfirmRate = if (totalReviewsCount > 0) {
                totalPrescriptionConfirms.get() / totalReviewsCount.toDouble()
            } else 0.0,
            averageSOAPConfirmRate = if (totalReviewsCount > 0) {
                totalSOAPConfirms.get() / totalReviewsCount.toDouble()
            } else 0.0,
            totalConfidenceOverrides = totalConfidenceOverrides.get(),
            redFlagsResolved = totalRedFlagsResolved.get()
        )
    }
    
    /**
     * Get clinic-specific metrics
     */
    fun getClinicMetrics(clinicId: String): ClinicProxyMetrics? {
        return clinicMetrics[clinicId]
    }
    
    /**
     * Get device tier metrics
     */
    fun getDeviceTierMetrics(deviceTier: String): DeviceTierProxyMetrics? {
        return deviceTierMetrics[deviceTier]
    }
    
    /**
     * Get metrics dashboard data
     */
    fun getDashboardData(): ProxyMetricsDashboard {
        val clinicSummaries = clinicMetrics.values.map { it.toSummary() }
        val deviceTierSummaries = deviceTierMetrics.values.map { it.toSummary() }
        
        return ProxyMetricsDashboard(
            overallMetrics = getSummary(),
            topClinicsByEditRate = clinicSummaries.sortedByDescending { it.averageEditRate }.take(10),
            topClinicsByConfirmRate = clinicSummaries.sortedByDescending { it.averagePrescriptionConfirmRate }.take(10),
            deviceTierComparison = deviceTierSummaries,
            lastUpdated = Instant.now().toString()
        )
    }
    
    /**
     * Export metrics to CSV
     */
    fun exportToCsv(): String {
        val csvBuilder = StringBuilder()
        csvBuilder.appendLine("clinic_id,device_tier,total_reviews,avg_edit_rate,prescription_confirms,soap_confirms,confidence_overrides,red_flags_resolved,last_updated")
        
        clinicMetrics.values.forEach { clinic ->
            deviceTierMetrics.values.forEach { deviceTier ->
                csvBuilder.appendLine("${clinic.clinicId},${deviceTier.deviceTier},${clinic.totalReviews},${clinic.averageEditRate},${clinic.totalPrescriptionConfirms},${clinic.totalSOAPConfirms},${clinic.totalConfidenceOverrides},${clinic.totalRedFlagsResolved},${clinic.lastUpdated}")
            }
        }
        
        return csvBuilder.toString()
    }
    
    /**
     * Clear all metrics
     */
    fun clearMetrics() {
        clinicMetrics.clear()
        doctorMetrics.clear()
        deviceTierMetrics.clear()
        totalReviews.set(0)
        totalEditRate.set(0)
        totalPrescriptionConfirms.set(0)
        totalSOAPConfirms.set(0)
        totalConfidenceOverrides.set(0)
        totalRedFlagsResolved.set(0)
    }
}

/**
 * Data classes for proxy metrics
 */

data class ClinicProxyMetrics(
    val clinicId: String,
    var totalReviews: Int = 0,
    var totalEditRate: Double = 0.0,
    var totalPrescriptionConfirms: Int = 0,
    var totalSOAPConfirms: Int = 0,
    var totalConfidenceOverrides: Int = 0,
    var totalRedFlagsResolved: Int = 0,
    var totalPrescriptionReviews: Int = 0,
    var totalSOAPReviews: Int = 0,
    var lastUpdated: String = ""
) {
    val averageEditRate: Double
        get() = if (totalReviews > 0) totalEditRate / totalReviews else 0.0
    
    val averagePrescriptionConfirmRate: Double
        get() = if (totalPrescriptionReviews > 0) totalPrescriptionConfirms / totalPrescriptionReviews.toDouble() else 0.0
    
    val averageSOAPConfirmRate: Double
        get() = if (totalSOAPReviews > 0) totalSOAPConfirms / totalSOAPReviews.toDouble() else 0.0
    
    fun toSummary(): ClinicProxyMetricsSummary {
        return ClinicProxyMetricsSummary(
            clinicId = clinicId,
            totalReviews = totalReviews,
            averageEditRate = averageEditRate,
            averagePrescriptionConfirmRate = averagePrescriptionConfirmRate,
            averageSOAPConfirmRate = averageSOAPConfirmRate,
            totalConfidenceOverrides = totalConfidenceOverrides,
            totalRedFlagsResolved = totalRedFlagsResolved,
            lastUpdated = lastUpdated
        )
    }
}

data class DoctorProxyMetrics(
    val doctorId: String,
    val clinicId: String,
    var totalReviews: Int = 0,
    var totalEditRate: Double = 0.0,
    var totalPrescriptionConfirms: Int = 0,
    var totalSOAPConfirms: Int = 0,
    var totalConfidenceOverrides: Int = 0,
    var totalRedFlagsResolved: Int = 0,
    var lastUpdated: String = ""
) {
    val averageEditRate: Double
        get() = if (totalReviews > 0) totalEditRate / totalReviews else 0.0
    
    val averagePrescriptionConfirmRate: Double
        get() = if (totalReviews > 0) totalPrescriptionConfirms / totalReviews.toDouble() else 0.0
    
    val averageSOAPConfirmRate: Double
        get() = if (totalReviews > 0) totalSOAPConfirms / totalReviews.toDouble() else 0.0
}

data class DeviceTierProxyMetrics(
    val deviceTier: String,
    var totalReviews: Int = 0,
    var totalEditRate: Double = 0.0,
    var totalPrescriptionConfirms: Int = 0,
    var totalSOAPConfirms: Int = 0,
    var totalConfidenceOverrides: Int = 0,
    var totalRedFlagsResolved: Int = 0,
    var totalPrescriptionReviews: Int = 0,
    var totalSOAPReviews: Int = 0,
    var lastUpdated: String = ""
) {
    val averageEditRate: Double
        get() = if (totalReviews > 0) totalEditRate / totalReviews else 0.0
    
    val averagePrescriptionConfirmRate: Double
        get() = if (totalPrescriptionReviews > 0) totalPrescriptionConfirms / totalPrescriptionReviews.toDouble() else 0.0
    
    val averageSOAPConfirmRate: Double
        get() = if (totalSOAPReviews > 0) totalSOAPConfirms / totalSOAPReviews.toDouble() else 0.0
    
    fun toSummary(): DeviceTierProxyMetricsSummary {
        return DeviceTierProxyMetricsSummary(
            deviceTier = deviceTier,
            totalReviews = totalReviews,
            averageEditRate = averageEditRate,
            averagePrescriptionConfirmRate = averagePrescriptionConfirmRate,
            averageSOAPConfirmRate = averageSOAPConfirmRate,
            totalConfidenceOverrides = totalConfidenceOverrides,
            totalRedFlagsResolved = totalRedFlagsResolved,
            lastUpdated = lastUpdated
        )
    }
}

data class ClinicProxyMetricsSummary(
    val clinicId: String,
    val totalReviews: Int,
    val averageEditRate: Double,
    val averagePrescriptionConfirmRate: Double,
    val averageSOAPConfirmRate: Double,
    val totalConfidenceOverrides: Int,
    val totalRedFlagsResolved: Int,
    val lastUpdated: String
)

data class DeviceTierProxyMetricsSummary(
    val deviceTier: String,
    val totalReviews: Int,
    val averageEditRate: Double,
    val averagePrescriptionConfirmRate: Double,
    val averageSOAPConfirmRate: Double,
    val totalConfidenceOverrides: Int,
    val totalRedFlagsResolved: Int,
    val lastUpdated: String
)

data class ProxyMetricsDashboard(
    val overallMetrics: ProxyMetricsSummary,
    val topClinicsByEditRate: List<ClinicProxyMetricsSummary>,
    val topClinicsByConfirmRate: List<ClinicProxyMetricsSummary>,
    val deviceTierComparison: List<DeviceTierProxyMetricsSummary>,
    val lastUpdated: String
)
