package com.frozo.ambientscribe.transcription

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ASRErrorTest {

    @Test
    fun `NetworkError should have correct properties`() {
        val error = ASRError.NetworkError("Test network error")
        
        assertEquals("Test network error", error.message)
        assertEquals(ASRError.ERROR_NETWORK, error.errorCode)
        assertTrue(error.recoverable)
        assertEquals(null, error.cause)
    }

    @Test
    fun `ThermalError should have correct properties`() {
        val error = ASRError.ThermalError("Test thermal error")
        
        assertEquals("Test thermal error", error.message)
        assertEquals(ASRError.ERROR_CPU_THERMAL, error.errorCode)
        assertTrue(error.recoverable)
        assertEquals(null, error.cause)
    }

    @Test
    fun `DecoderError should have correct properties`() {
        val error = ASRError.DecoderError("Test decoder error")
        
        assertEquals("Test decoder error", error.message)
        assertEquals(ASRError.ERROR_DECODER_FAIL, error.errorCode)
        assertFalse(error.recoverable)
        assertEquals(null, error.cause)
    }

    @Test
    fun `AudioInputError should have correct properties`() {
        val error = ASRError.AudioInputError("Test audio error")
        
        assertEquals("Test audio error", error.message)
        assertEquals(ASRError.ERROR_AUDIO_INPUT, error.errorCode)
        assertTrue(error.recoverable)
        assertEquals(null, error.cause)
    }

    @Test
    fun `PermissionError should have correct properties`() {
        val error = ASRError.PermissionError("Test permission error")
        
        assertEquals("Test permission error", error.message)
        assertEquals(ASRError.ERROR_PERMISSION, error.errorCode)
        assertFalse(error.recoverable)
        assertEquals(null, error.cause)
    }

    @Test
    fun `ResourceError should have correct properties`() {
        val error = ASRError.ResourceError("Test resource error")
        
        assertEquals("Test resource error", error.message)
        assertEquals(ASRError.ERROR_RESOURCE, error.errorCode)
        assertFalse(error.recoverable)
        assertEquals(null, error.cause)
    }

    @Test
    fun `InitializationError should have correct properties`() {
        val error = ASRError.InitializationError("Test initialization error")
        
        assertEquals("Test initialization error", error.message)
        assertEquals(ASRError.ERROR_INITIALIZATION, error.errorCode)
        assertFalse(error.recoverable)
        assertEquals(null, error.cause)
    }

    @Test
    fun `UnknownError should have correct properties`() {
        val error = ASRError.UnknownError("Test unknown error")
        
        assertEquals("Test unknown error", error.message)
        assertEquals(ASRError.ERROR_UNKNOWN, error.errorCode)
        assertFalse(error.recoverable)
        assertEquals(null, error.cause)
    }

    @Test
    fun `fromException should correctly classify network errors`() {
        val exception = Exception("Failed due to network connection issue")
        val error = ASRError.fromException(exception)
        
        assertTrue(error is ASRError.NetworkError)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `fromException should correctly classify thermal errors`() {
        val exception = Exception("Device overheating, thermal throttling applied")
        val error = ASRError.fromException(exception)
        
        assertTrue(error is ASRError.ThermalError)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `fromException should correctly classify decoder errors`() {
        val exception = Exception("Failed to initialize decoder model")
        val error = ASRError.fromException(exception)
        
        assertTrue(error is ASRError.DecoderError)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `fromException should correctly classify audio errors`() {
        val exception = Exception("Failed to access microphone for audio recording")
        val error = ASRError.fromException(exception)
        
        assertTrue(error is ASRError.AudioInputError)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `fromException should correctly classify permission errors`() {
        val exception = Exception("Permission denied for audio recording")
        val error = ASRError.fromException(exception)
        
        assertTrue(error is ASRError.PermissionError)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `fromException should correctly classify resource errors`() {
        val exception = Exception("Insufficient memory resources for transcription")
        val error = ASRError.fromException(exception)
        
        assertTrue(error is ASRError.ResourceError)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `fromException should correctly classify initialization errors`() {
        val exception = Exception("Failed to initialize ASR system")
        val error = ASRError.fromException(exception)
        
        assertTrue(error is ASRError.InitializationError)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `fromException should default to UnknownError for unclassified errors`() {
        val exception = Exception("Some random error message")
        val error = ASRError.fromException(exception)
        
        assertTrue(error is ASRError.UnknownError)
        assertEquals(exception, error.cause)
    }
}
