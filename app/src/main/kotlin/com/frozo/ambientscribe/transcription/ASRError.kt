package com.frozo.ambientscribe.transcription

/**
 * ASR error taxonomy for categorizing transcription errors
 */
sealed class ASRError(
    val message: String,
    val errorCode: Int,
    val recoverable: Boolean,
    val cause: Throwable? = null
) {
    /**
     * Network-related errors (connectivity, timeouts, etc.)
     */
    class NetworkError(
        message: String = "Network error occurred during transcription",
        errorCode: Int = ERROR_NETWORK,
        cause: Throwable? = null
    ) : ASRError(message, errorCode, true, cause)
    
    /**
     * CPU thermal throttling errors (device overheating)
     */
    class ThermalError(
        message: String = "Device overheating, transcription performance reduced",
        errorCode: Int = ERROR_CPU_THERMAL,
        cause: Throwable? = null
    ) : ASRError(message, errorCode, true, cause)
    
    /**
     * Decoder failures (model loading, inference errors)
     */
    class DecoderError(
        message: String = "ASR decoder failed during transcription",
        errorCode: Int = ERROR_DECODER_FAIL,
        cause: Throwable? = null
    ) : ASRError(message, errorCode, false, cause)
    
    /**
     * Audio input errors (microphone issues, audio format)
     */
    class AudioInputError(
        message: String = "Audio input error occurred",
        errorCode: Int = ERROR_AUDIO_INPUT,
        cause: Throwable? = null
    ) : ASRError(message, errorCode, true, cause)
    
    /**
     * Permission errors (microphone access denied)
     */
    class PermissionError(
        message: String = "Permission denied for audio recording",
        errorCode: Int = ERROR_PERMISSION,
        cause: Throwable? = null
    ) : ASRError(message, errorCode, false, cause)
    
    /**
     * Resource errors (memory, disk space)
     */
    class ResourceError(
        message: String = "Insufficient resources for transcription",
        errorCode: Int = ERROR_RESOURCE,
        cause: Throwable? = null
    ) : ASRError(message, errorCode, false, cause)
    
    /**
     * Initialization errors (model not found, setup failure)
     */
    class InitializationError(
        message: String = "Failed to initialize ASR system",
        errorCode: Int = ERROR_INITIALIZATION,
        cause: Throwable? = null
    ) : ASRError(message, errorCode, false, cause)
    
    /**
     * Unknown errors (catch-all for unclassified errors)
     */
    class UnknownError(
        message: String = "Unknown error occurred during transcription",
        errorCode: Int = ERROR_UNKNOWN,
        cause: Throwable? = null
    ) : ASRError(message, errorCode, false, cause)
    
    companion object {
        // Error codes
        const val ERROR_NETWORK = 1001
        const val ERROR_CPU_THERMAL = 1002
        const val ERROR_DECODER_FAIL = 1003
        const val ERROR_AUDIO_INPUT = 1004
        const val ERROR_PERMISSION = 1005
        const val ERROR_RESOURCE = 1006
        const val ERROR_INITIALIZATION = 1007
        const val ERROR_UNKNOWN = 1999
        
        /**
         * Factory method to create appropriate ASR error from exception
         */
        fun fromException(exception: Throwable): ASRError {
            return when {
                // Network errors
                exception.message?.contains("network", ignoreCase = true) == true ||
                exception.message?.contains("connection", ignoreCase = true) == true ||
                exception.message?.contains("timeout", ignoreCase = true) == true ->
                    NetworkError(cause = exception)
                
                // Thermal errors
                exception.message?.contains("thermal", ignoreCase = true) == true ||
                exception.message?.contains("temperature", ignoreCase = true) == true ||
                exception.message?.contains("overheat", ignoreCase = true) == true ->
                    ThermalError(cause = exception)
                
                // Decoder errors
                exception.message?.contains("decoder", ignoreCase = true) == true ||
                exception.message?.contains("model", ignoreCase = true) == true ||
                exception.message?.contains("inference", ignoreCase = true) == true ->
                    DecoderError(cause = exception)
                
                // Audio input errors
                exception.message?.contains("audio", ignoreCase = true) == true ||
                exception.message?.contains("microphone", ignoreCase = true) == true ||
                exception.message?.contains("recording", ignoreCase = true) == true ->
                    AudioInputError(cause = exception)
                
                // Permission errors
                exception.message?.contains("permission", ignoreCase = true) == true ||
                exception.message?.contains("denied", ignoreCase = true) == true ->
                    PermissionError(cause = exception)
                
                // Resource errors
                exception.message?.contains("memory", ignoreCase = true) == true ||
                exception.message?.contains("resource", ignoreCase = true) == true ||
                exception.message?.contains("space", ignoreCase = true) == true ->
                    ResourceError(cause = exception)
                
                // Initialization errors
                exception.message?.contains("initialize", ignoreCase = true) == true ||
                exception.message?.contains("setup", ignoreCase = true) == true ->
                    InitializationError(cause = exception)
                
                // Default to unknown error
                else -> UnknownError(cause = exception)
            }
        }
    }
}
