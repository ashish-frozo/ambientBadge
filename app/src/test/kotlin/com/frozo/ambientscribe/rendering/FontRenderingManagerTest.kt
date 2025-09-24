package com.frozo.ambientscribe.rendering

import android.content.Context
import android.graphics.Typeface
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for FontRenderingManager - PT-7.3, PT-7.8
 */
@RunWith(RobolectricTestRunner::class)
class FontRenderingManagerTest {

    private lateinit var context: Context
    private lateinit var fontRenderingManager: FontRenderingManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fontRenderingManager = FontRenderingManager(context)
    }

    @Test
    fun `test initialization`() = runTest {
        // When
        val result = fontRenderingManager.initialize()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test get font for script - Devanagari`() = runTest {
        // Given
        val script = "Deva"
        
        // When
        val font = fontRenderingManager.getFontForScript(script)
        
        // Then
        assertNotNull(font)
    }

    @Test
    fun `test get font for script - Telugu`() = runTest {
        // Given
        val script = "Telu"
        
        // When
        val font = fontRenderingManager.getFontForScript(script)
        
        // Then
        assertNotNull(font)
    }

    @Test
    fun `test get font for script - Latin`() = runTest {
        // Given
        val script = "Latn"
        
        // When
        val font = fontRenderingManager.getFontForScript(script)
        
        // Then
        assertNotNull(font)
    }

    @Test
    fun `test render text - Devanagari`() = runTest {
        // Given
        val text = "नमस्ते"
        val script = "Deva"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertEquals(text, renderingResult.text)
        assertEquals(script, renderingResult.script)
        assertTrue(renderingResult.isRendered)
        assertTrue(renderingResult.characterCount > 0)
        assertTrue(renderingResult.complexScriptCount > 0)
    }

    @Test
    fun `test render text - Telugu`() = runTest {
        // Given
        val text = "నమస్కారం"
        val script = "Telu"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertEquals(text, renderingResult.text)
        assertEquals(script, renderingResult.script)
        assertTrue(renderingResult.isRendered)
        assertTrue(renderingResult.characterCount > 0)
        assertTrue(renderingResult.complexScriptCount > 0)
    }

    @Test
    fun `test render text - Latin`() = runTest {
        // Given
        val text = "Hello World"
        val script = "Latn"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertEquals(text, renderingResult.text)
        assertEquals(script, renderingResult.script)
        assertTrue(renderingResult.isRendered)
        assertEquals(11, renderingResult.characterCount)
        assertEquals(0, renderingResult.complexScriptCount)
    }

    @Test
    fun `test render text - Arabic`() = runTest {
        // Given
        val text = "مرحبا"
        val script = "Arab"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertEquals(text, renderingResult.text)
        assertEquals(script, renderingResult.script)
        assertTrue(renderingResult.isRendered)
        assertTrue(renderingResult.characterCount > 0)
        assertTrue(renderingResult.complexScriptCount > 0)
    }

    @Test
    fun `test render text - Cyrillic`() = runTest {
        // Given
        val text = "Привет"
        val script = "Cyrl"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertEquals(text, renderingResult.text)
        assertEquals(script, renderingResult.script)
        assertTrue(renderingResult.isRendered)
        assertTrue(renderingResult.characterCount > 0)
        assertTrue(renderingResult.complexScriptCount > 0)
    }

    @Test
    fun `test validate font availability`() = runTest {
        // When
        val result = fontRenderingManager.validateFontAvailability()
        
        // Then
        assertTrue(result.isSuccess)
        val validationResults = result.getOrThrow()
        assertNotNull(validationResults)
        assertTrue(validationResults.isNotEmpty())
    }

    @Test
    fun `test test font rendering across languages`() = runTest {
        // When
        val result = fontRenderingManager.testFontRenderingAcrossLanguages()
        
        // Then
        assertTrue(result.isSuccess)
        val testResult = result.getOrThrow()
        assertNotNull(testResult)
        assertTrue(testResult.totalTests > 0)
        assertTrue(testResult.successfulRenders >= 0)
        assertTrue(testResult.renderingResults.isNotEmpty())
    }

    @Test
    fun `test font config properties`() {
        // Given
        val config = FontRenderingManager.FontConfig(
            script = "Deva",
            fontFamily = "Noto Sans Devanagari",
            fontPath = "fonts/NotoSansDevanagari-Regular.ttf",
            isAvailable = true,
            fallbackFont = "sans-serif",
            supportsRTL = false,
            supportsLigatures = true
        )
        
        // Then
        assertEquals("Deva", config.script)
        assertEquals("Noto Sans Devanagari", config.fontFamily)
        assertEquals("fonts/NotoSansDevanagari-Regular.ttf", config.fontPath)
        assertTrue(config.isAvailable)
        assertEquals("sans-serif", config.fallbackFont)
        assertFalse(config.supportsRTL)
        assertTrue(config.supportsLigatures)
    }

    @Test
    fun `test text rendering result properties`() {
        // Given
        val result = FontRenderingManager.TextRenderingResult(
            text = "नमस्ते",
            script = "Deva",
            fontUsed = "Noto Sans Devanagari",
            isRendered = true,
            hasFallback = false,
            renderingTime = 10L,
            characterCount = 6,
            complexScriptCount = 6
        )
        
        // Then
        assertEquals("नमस्ते", result.text)
        assertEquals("Deva", result.script)
        assertEquals("Noto Sans Devanagari", result.fontUsed)
        assertTrue(result.isRendered)
        assertFalse(result.hasFallback)
        assertEquals(10L, result.renderingTime)
        assertEquals(6, result.characterCount)
        assertEquals(6, result.complexScriptCount)
    }

    @Test
    fun `test font validation result properties`() {
        // Given
        val result = FontRenderingManager.FontValidationResult(
            fontPath = "fonts/NotoSansDevanagari-Regular.ttf",
            isAvailable = true,
            supportsScript = true,
            characterCoverage = 0.95f,
            missingCharacters = listOf("missing1", "missing2"),
            recommendations = listOf("Font is good")
        )
        
        // Then
        assertEquals("fonts/NotoSansDevanagari-Regular.ttf", result.fontPath)
        assertTrue(result.isAvailable)
        assertTrue(result.supportsScript)
        assertEquals(0.95f, result.characterCoverage)
        assertEquals(2, result.missingCharacters.size)
        assertEquals(1, result.recommendations.size)
    }

    @Test
    fun `test font rendering test result properties`() {
        // Given
        val renderingResults = listOf(
            FontRenderingManager.TextRenderingResult(
                text = "Hello",
                script = "Latn",
                fontUsed = "Noto Sans",
                isRendered = true,
                hasFallback = false,
                renderingTime = 5L,
                characterCount = 5,
                complexScriptCount = 0
            )
        )
        
        val testResult = FontRenderingManager.FontRenderingTestResult(
            totalTests = 1,
            successfulRenders = 1,
            averageRenderingTime = 5L,
            renderingResults = renderingResults,
            overallSuccess = true,
            timestamp = System.currentTimeMillis()
        )
        
        // Then
        assertEquals(1, testResult.totalTests)
        assertEquals(1, testResult.successfulRenders)
        assertEquals(5L, testResult.averageRenderingTime)
        assertEquals(1, testResult.renderingResults.size)
        assertTrue(testResult.overallSuccess)
        assertTrue(testResult.timestamp > 0)
    }

    @Test
    fun `test count complex script characters - Devanagari`() = runTest {
        // Given
        val text = "नमस्ते दुनिया"
        val script = "Deva"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertTrue(renderingResult.complexScriptCount > 0)
    }

    @Test
    fun `test count complex script characters - Telugu`() = runTest {
        // Given
        val text = "నమస్కారం ప్రపంచం"
        val script = "Telu"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertTrue(renderingResult.complexScriptCount > 0)
    }

    @Test
    fun `test count complex script characters - Latin`() = runTest {
        // Given
        val text = "Hello World"
        val script = "Latn"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertEquals(0, renderingResult.complexScriptCount)
    }

    @Test
    fun `test rendering time measurement`() = runTest {
        // Given
        val text = "Test text"
        val script = "Latn"
        
        // When
        val result = fontRenderingManager.renderText(text, script)
        
        // Then
        assertTrue(result.isSuccess)
        val renderingResult = result.getOrThrow()
        assertTrue(renderingResult.renderingTime >= 0)
    }

    @Test
    fun `test font fallback system`() = runTest {
        // Given
        val script = "Unknown"
        
        // When
        val font = fontRenderingManager.getFontForScript(script)
        
        // Then
        assertNotNull(font)
        // Should return system default font
    }
}
