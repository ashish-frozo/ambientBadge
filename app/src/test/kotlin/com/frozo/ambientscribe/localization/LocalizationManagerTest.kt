package com.frozo.ambientscribe.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
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
 * Unit tests for LocalizationManager - ST-7.1, ST-7.2, ST-7.3
 */
@RunWith(RobolectricTestRunner::class)
class LocalizationManagerTest {

    private lateinit var context: Context
    private lateinit var localizationManager: LocalizationManager
    private lateinit var mockResources: Resources
    private lateinit var mockConfiguration: Configuration

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        localizationManager = LocalizationManager(context)
        
        // Mock resources and configuration
        mockResources = mockk<Resources>()
        mockConfiguration = mockk<Configuration>()
        
        every { context.resources } returns mockResources
        every { mockResources.configuration } returns mockConfiguration
        every { mockResources.displayMetrics } returns mockk()
    }

    @Test
    fun `test initialization`() = runTest {
        // When
        val result = localizationManager.initialize()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test get supported languages`() {
        // When
        val supportedLanguages = localizationManager.getSupportedLanguages()
        
        // Then
        assertEquals(3, supportedLanguages.size)
        assertTrue(supportedLanguages.any { it.code == "en" })
        assertTrue(supportedLanguages.any { it.code == "hi" })
        assertTrue(supportedLanguages.any { it.code == "te" })
    }

    @Test
    fun `test set language to English`() = runTest {
        // When
        val result = localizationManager.setLanguage("en")
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals("en", localizationManager.getCurrentLanguage())
    }

    @Test
    fun `test set language to Hindi`() = runTest {
        // When
        val result = localizationManager.setLanguage("hi")
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals("hi", localizationManager.getCurrentLanguage())
    }

    @Test
    fun `test set unsupported language fails`() = runTest {
        // When
        val result = localizationManager.setLanguage("xyz")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `test get string with resource ID`() {
        // Given
        val resourceId = 123
        val expectedString = "Test String"
        
        every { context.getString(resourceId) } returns expectedString
        
        // When
        val result = localizationManager.getString(resourceId)
        
        // Then
        assertEquals(expectedString, result)
    }

    @Test
    fun `test get string with fallback`() {
        // Given
        val resourceId = 123
        val fallback = "Fallback String"
        
        every { context.getString(resourceId) } throws Exception("Resource not found")
        
        // When
        val result = localizationManager.getStringWithFallback(resourceId, fallback)
        
        // Then
        assertEquals(fallback, result)
    }

    @Test
    fun `test is current language RTL`() {
        // Given
        localizationManager.setLanguage("en")
        
        // When
        val isRTL = localizationManager.isCurrentLanguageRTL()
        
        // Then
        assertFalse(isRTL)
    }

    @Test
    fun `test is current language Devanagari`() {
        // Given
        localizationManager.setLanguage("hi")
        
        // When
        val isDevanagari = localizationManager.isCurrentLanguageDevanagari()
        
        // Then
        assertTrue(isDevanagari)
    }

    @Test
    fun `test is current language Telugu`() {
        // Given
        localizationManager.setLanguage("te")
        
        // When
        val isTelugu = localizationManager.isCurrentLanguageTelugu()
        
        // Then
        assertTrue(isTelugu)
    }

    @Test
    fun `test translate text from English to Hindi`() = runTest {
        // Given
        val text = "Hello"
        val sourceLanguage = "en"
        val targetLanguage = "hi"
        
        // When
        val result = localizationManager.translateText(text, targetLanguage, sourceLanguage)
        
        // Then
        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertEquals(text, translationResult.originalText)
        assertEquals("नमस्ते", translationResult.translatedText)
        assertEquals(sourceLanguage, translationResult.sourceLanguage)
        assertEquals(targetLanguage, translationResult.targetLanguage)
    }

    @Test
    fun `test translate text from Hindi to English`() = runTest {
        // Given
        val text = "नमस्ते"
        val sourceLanguage = "hi"
        val targetLanguage = "en"
        
        // When
        val result = localizationManager.translateText(text, targetLanguage, sourceLanguage)
        
        // Then
        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertEquals(text, translationResult.originalText)
        assertEquals("Hello", translationResult.translatedText)
        assertEquals(sourceLanguage, translationResult.sourceLanguage)
        assertEquals(targetLanguage, translationResult.targetLanguage)
    }

    @Test
    fun `test translate text from English to Telugu`() = runTest {
        // Given
        val text = "Hello"
        val sourceLanguage = "en"
        val targetLanguage = "te"
        
        // When
        val result = localizationManager.translateText(text, targetLanguage, sourceLanguage)
        
        // Then
        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertEquals(text, translationResult.originalText)
        assertEquals("నమస్కారం", translationResult.translatedText)
        assertEquals(sourceLanguage, translationResult.sourceLanguage)
        assertEquals(targetLanguage, translationResult.targetLanguage)
    }

    @Test
    fun `test enable Telugu language`() = runTest {
        // When
        val result = localizationManager.enableTeluguLanguage()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test disable Telugu language`() = runTest {
        // Given
        localizationManager.setLanguage("te")
        
        // When
        val result = localizationManager.disableTeluguLanguage()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals("en", localizationManager.getCurrentLanguage())
    }

    @Test
    fun `test get localization config`() = runTest {
        // When
        val config = localizationManager.getLocalizationConfig()
        
        // Then
        assertNotNull(config)
        assertEquals("en", config.currentLanguage)
        assertEquals(3, config.supportedLanguages.size)
        assertFalse(config.isTeluguEnabled)
        assertEquals("en", config.fallbackLanguage)
    }

    @Test
    fun `test validate localization setup`() = runTest {
        // When
        val result = localizationManager.validateLocalizationSetup()
        
        // Then
        assertTrue(result.isSuccess)
        val validationResult = result.getOrThrow()
        assertTrue(validationResult.isSetupValid)
        assertEquals(3, validationResult.supportedLanguagesCount)
        assertTrue(validationResult.currentLanguageSupported)
        assertFalse(validationResult.rtlSupport)
        assertTrue(validationResult.devanagariSupport)
        assertTrue(validationResult.teluguSupport)
    }

    @Test
    fun `test supported language properties`() {
        // When
        val supportedLanguages = localizationManager.getSupportedLanguages()
        
        // Then
        val englishLang = supportedLanguages.find { it.code == "en" }
        assertNotNull(englishLang)
        assertEquals("English", englishLang.name)
        assertEquals("English", englishLang.nativeName)
        assertEquals("Latn", englishLang.script)
        assertFalse(englishLang.isRTL)
        assertTrue(englishLang.isEnabled)
        
        val hindiLang = supportedLanguages.find { it.code == "hi" }
        assertNotNull(hindiLang)
        assertEquals("Hindi", hindiLang.name)
        assertEquals("हिन्दी", hindiLang.nativeName)
        assertEquals("Deva", hindiLang.script)
        assertFalse(hindiLang.isRTL)
        assertTrue(hindiLang.isEnabled)
        
        val teluguLang = supportedLanguages.find { it.code == "te" }
        assertNotNull(teluguLang)
        assertEquals("Telugu", teluguLang.name)
        assertEquals("తెలుగు", teluguLang.nativeName)
        assertEquals("Telu", teluguLang.script)
        assertFalse(teluguLang.isRTL)
        assertFalse(teluguLang.isEnabled)
    }

    @Test
    fun `test translation confidence`() = runTest {
        // Given
        val text = "Hello"
        val targetLanguage = "hi"
        
        // When
        val result = localizationManager.translateText(text, targetLanguage)
        
        // Then
        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertTrue(translationResult.confidence > 0.9f)
    }

    @Test
    fun `test translation timestamp`() = runTest {
        // Given
        val text = "Hello"
        val targetLanguage = "hi"
        val beforeTime = System.currentTimeMillis()
        
        // When
        val result = localizationManager.translateText(text, targetLanguage)
        
        // Then
        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertTrue(translationResult.timestamp >= beforeTime)
        assertTrue(translationResult.timestamp <= System.currentTimeMillis())
    }
}
