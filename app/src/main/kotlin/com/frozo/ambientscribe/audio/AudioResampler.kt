package com.frozo.ambientscribe.audio

import timber.log.Timber

/**
 * High-quality audio resampler for converting between different sample rates.
 * Uses linear interpolation for efficient resampling with good quality.
 */
class AudioResampler(
    private val inputSampleRate: Int,
    private val outputSampleRate: Int
) {
    companion object {
        // Quality settings
        private const val HIGH_QUALITY = true // Use higher quality algorithm
    }
    
    // Resampling ratio
    private val ratio = outputSampleRate.toDouble() / inputSampleRate.toDouble()
    
    // Resampling stats
    private var totalInputSamples = 0L
    private var totalOutputSamples = 0L
    private var underruns = 0
    private var overruns = 0
    
    init {
        Timber.d("AudioResampler initialized: $inputSampleRate Hz -> $outputSampleRate Hz (ratio: $ratio)")
    }
    
    /**
     * Resample audio data from input sample rate to output sample rate
     */
    fun resample(input: ShortArray): ShortArray {
        if (inputSampleRate == outputSampleRate) {
            return input.clone() // No resampling needed
        }
        
        totalInputSamples += input.size
        
        // Calculate output size
        val outputSize = (input.size * ratio).toInt()
        val output = ShortArray(outputSize)
        
        if (HIGH_QUALITY) {
            // High-quality linear interpolation resampling
            resampleLinear(input, output)
        } else {
            // Simple nearest-neighbor resampling
            resampleNearest(input, output)
        }
        
        totalOutputSamples += output.size
        
        // Check for underruns/overruns
        val expectedRatio = totalOutputSamples.toDouble() / totalInputSamples.toDouble()
        if (expectedRatio < ratio * 0.95) {
            underruns++
            Timber.w("Audio resampling underrun detected: $expectedRatio (expected: $ratio)")
        } else if (expectedRatio > ratio * 1.05) {
            overruns++
            Timber.w("Audio resampling overrun detected: $expectedRatio (expected: $ratio)")
        }
        
        return output
    }
    
    /**
     * Resample using linear interpolation (high quality)
     */
    private fun resampleLinear(input: ShortArray, output: ShortArray) {
        for (i in output.indices) {
            val srcIdx = i / ratio
            val srcIdxInt = srcIdx.toInt()
            val srcIdxFrac = srcIdx - srcIdxInt
            
            if (srcIdxInt >= input.size - 1) {
                // Handle edge case at the end of input
                output[i] = input.lastOrNull() ?: 0
            } else {
                // Linear interpolation between adjacent samples
                val sample1 = input[srcIdxInt].toInt()
                val sample2 = input[srcIdxInt + 1].toInt()
                val interpolated = sample1 + ((sample2 - sample1) * srcIdxFrac).toInt()
                output[i] = interpolated.toShort()
            }
        }
    }
    
    /**
     * Resample using nearest-neighbor (lower quality, faster)
     */
    private fun resampleNearest(input: ShortArray, output: ShortArray) {
        for (i in output.indices) {
            val srcIdx = (i / ratio).toInt()
            if (srcIdx < input.size) {
                output[i] = input[srcIdx]
            }
        }
    }
    
    /**
     * Get resampling statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "input_sample_rate" to inputSampleRate,
            "output_sample_rate" to outputSampleRate,
            "ratio" to ratio,
            "total_input_samples" to totalInputSamples,
            "total_output_samples" to totalOutputSamples,
            "underruns" to underruns,
            "overruns" to overruns
        )
    }
    
    /**
     * Reset statistics
     */
    fun resetStats() {
        totalInputSamples = 0
        totalOutputSamples = 0
        underruns = 0
        overruns = 0
    }
}
