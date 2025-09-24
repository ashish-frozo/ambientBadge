package com.frozo.ambientscribe.audio

/**
 * Audio data container
 */
data class AudioData(
    val samples: ShortArray,
    val energyLevel: Float,
    val isVoiceActive: Boolean,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioData

        if (!samples.contentEquals(other.samples)) return false
        if (energyLevel != other.energyLevel) return false
        if (isVoiceActive != other.isVoiceActive) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + energyLevel.hashCode()
        result = 31 * result + isVoiceActive.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
