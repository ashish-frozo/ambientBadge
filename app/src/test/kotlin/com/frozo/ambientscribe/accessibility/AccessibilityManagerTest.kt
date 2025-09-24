package com.frozo.ambientscribe.accessibility

import android.content.Context
import android.content.res.Configuration
import android.view.accessibility.AccessibilityManager
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
 * Unit tests for AccessibilityManager - PT-7.4, PT-7.5, PT-7.12
 */
@RunWith(RobolectricTestRunner::class)
class AccessibilityManagerTest {

    private lateinit var context: Context
    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var mockAccessibilityManager: AccessibilityManager
    private lateinit var mockConfiguration: Configuration

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        accessibilityManager = AccessibilityManager(context)
        
        // Mock AccessibilityManager
        mockAccessibilityManager = mockk<AccessibilityManager>()
        every { context.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns mockAccessibilityManager
        
        // Mock Configuration
        mockConfiguration = mockk<Configuration>()
        every { context.resources.configuration } returns mockConfiguration
        every { context.resources.displayMetrics } returns mockk()
    }

    @Test
    fun `test initialization`() = runTest {
        // Given
        every { mockAccessibilityManager.isEnabled } returns true
        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns emptyList()
        every { mockConfiguration.fontScale } returns 1.0f
        every { mockConfiguration.uiMode } returns Configuration.UI_MODE_NIGHT_NO
        
        // When
        val result = accessibilityManager.initialize()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test get accessibility config`() = runTest {
        // Given
        every { mockAccessibilityManager.isEnabled } returns true
        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns emptyList()
        every { mockConfiguration.fontScale } returns 1.5f
        every { mockConfiguration.uiMode } returns Configuration.UI_MODE_NIGHT_YES
        
        // When
        val config = accessibilityManager.getAccessibilityConfig()
        
        // Then
        assertNotNull(config)
        assertEquals(48, config.minTouchTargetSize)
        assertEquals(14, config.minFontSize)
        assertEquals(24, config.maxFontSize)
        assertEquals(4.5f, config.contrastRatio)
    }

    @Test
    fun `test validate touch target size - valid`() = runTest {
        // Given
        val elementId = "button1"
        val width = 60 // 60dp
        val height = 60 // 60dp
        
        // When
        val result = accessibilityManager.validateTouchTargetSize(elementId, width, height)
        
        // Then
        assertTrue(result.isValid)
        assertEquals(elementId, result.elementId)
        assertEquals(60, result.currentSize)
        assertEquals(48, result.minimumSize)
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun `test validate touch target size - invalid`() = runTest {
        // Given
        val elementId = "button1"
        val width = 30 // 30dp
        val height = 30 // 30dp
        
        // When
        val result = accessibilityManager.validateTouchTargetSize(elementId, width, height)
        
        // Then
        assertFalse(result.isValid)
        assertEquals(elementId, result.elementId)
        assertEquals(30, result.currentSize)
        assertEquals(48, result.minimumSize)
        assertTrue(result.recommendations.isNotEmpty())
        assertTrue(result.recommendations.any { it.contains("Touch target too small") })
    }

    @Test
    fun `test validate font size - valid`() = runTest {
        // Given
        val fontSize = 16f
        
        // When
        val result = accessibilityManager.validateFontSize(fontSize)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(16f, validation.originalSize)
        assertEquals(16f, validation.scaledSize)
        assertEquals(14, validation.minimumSize)
        assertEquals(24, validation.maximumSize)
    }

    @Test
    fun `test validate font size - too small`() = runTest {
        // Given
        val fontSize = 10f
        
        // When
        val result = accessibilityManager.validateFontSize(fontSize)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(10f, validation.originalSize)
        assertEquals(10f, validation.scaledSize)
        assertTrue(validation.recommendations.any { it.contains("Font size too small") })
    }

    @Test
    fun `test validate font size - too large`() = runTest {
        // Given
        val fontSize = 30f
        
        // When
        val result = accessibilityManager.validateFontSize(fontSize)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(30f, validation.originalSize)
        assertEquals(30f, validation.scaledSize)
        assertTrue(validation.recommendations.any { it.contains("Font size too large") })
    }

    @Test
    fun `test validate color contrast - valid`() = runTest {
        // Given
        val foregroundColor = 0xFF000000.toInt() // Black
        val backgroundColor = 0xFFFFFFFF.toInt() // White
        
        // When
        val result = accessibilityManager.validateColorContrast(foregroundColor, backgroundColor)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertTrue(validation.isValid)
        assertEquals(foregroundColor, validation.foregroundColor)
        assertEquals(backgroundColor, validation.backgroundColor)
        assertTrue(validation.contrastRatio >= 4.5f)
        assertEquals(4.5f, validation.minimumRatio)
    }

    @Test
    fun `test validate color contrast - invalid`() = runTest {
        // Given
        val foregroundColor = 0xFF808080.toInt() // Gray
        val backgroundColor = 0xFF808080.toInt() // Same gray
        
        // When
        val result = accessibilityManager.validateColorContrast(foregroundColor, backgroundColor)
        
        // Then
        assertTrue(result.isSuccess)
        val validation = result.getOrThrow()
        assertFalse(validation.isValid)
        assertEquals(foregroundColor, validation.foregroundColor)
        assertEquals(backgroundColor, validation.backgroundColor)
        assertTrue(validation.contrastRatio < 4.5f)
        assertTrue(validation.recommendations.any { it.contains("Insufficient color contrast") })
    }

    @Test
    fun `test get accessibility features status`() = runTest {
        // When
        val features = accessibilityManager.getAccessibilityFeaturesStatus()
        
        // Then
        assertNotNull(features)
        assertEquals(5, features.size)
        
        val screenReaderFeature = features.find { it.feature == "Screen Reader Support" }
        assertNotNull(screenReaderFeature)
        assertTrue(screenReaderFeature.isSupported)
        
        val largeTouchTargetsFeature = features.find { it.feature == "Large Touch Targets" }
        assertNotNull(largeTouchTargetsFeature)
        assertTrue(largeTouchTargetsFeature.isEnabled)
        assertTrue(largeTouchTargetsFeature.isSupported)
        
        val highContrastFeature = features.find { it.feature == "High Contrast Mode" }
        assertNotNull(highContrastFeature)
        assertTrue(highContrastFeature.isSupported)
        
        val largeTextFeature = features.find { it.feature == "Large Text Support" }
        assertNotNull(largeTextFeature)
        assertTrue(largeTextFeature.isSupported)
        
        val voiceFeedbackFeature = features.find { it.feature == "Voice Feedback" }
        assertNotNull(voiceFeedbackFeature)
        assertTrue(voiceFeedbackFeature.isEnabled)
        assertTrue(voiceFeedbackFeature.isSupported)
    }

    @Test
    fun `test enable accessibility features`() = runTest {
        // When
        val result = accessibilityManager.enableAccessibilityFeatures()
        
        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test accessibility violation properties`() {
        // Given
        val violation = AccessibilityManager.AccessibilityViolation(
            type = "Touch Target Size",
            severity = "High",
            element = "button1",
            description = "Touch target too small",
            fix = "Increase touch target size"
        )
        
        // Then
        assertEquals("Touch Target Size", violation.type)
        assertEquals("High", violation.severity)
        assertEquals("button1", violation.element)
        assertEquals("Touch target too small", violation.description)
        assertEquals("Increase touch target size", violation.fix)
    }

    @Test
    fun `test font size validation result properties`() {
        // Given
        val validation = AccessibilityManager.FontSizeValidationResult(
            originalSize = 16f,
            scaledSize = 20f,
            minimumSize = 14,
            maximumSize = 24,
            isValid = true,
            recommendations = listOf("Font size is appropriate")
        )
        
        // Then
        assertEquals(16f, validation.originalSize)
        assertEquals(20f, validation.scaledSize)
        assertEquals(14, validation.minimumSize)
        assertEquals(24, validation.maximumSize)
        assertTrue(validation.isValid)
        assertEquals(1, validation.recommendations.size)
    }

    @Test
    fun `test contrast validation result properties`() {
        // Given
        val validation = AccessibilityManager.ContrastValidationResult(
            foregroundColor = 0xFF000000.toInt(),
            backgroundColor = 0xFFFFFFFF.toInt(),
            contrastRatio = 21.0f,
            minimumRatio = 4.5f,
            isValid = true,
            recommendations = listOf("Contrast ratio is excellent")
        )
        
        // Then
        assertEquals(0xFF000000.toInt(), validation.foregroundColor)
        assertEquals(0xFFFFFFFF.toInt(), validation.backgroundColor)
        assertEquals(21.0f, validation.contrastRatio)
        assertEquals(4.5f, validation.minimumRatio)
        assertTrue(validation.isValid)
        assertEquals(1, validation.recommendations.size)
    }

    @Test
    fun `test accessibility config properties`() = runTest {
        // Given
        every { mockAccessibilityManager.isEnabled } returns true
        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns emptyList()
        every { mockConfiguration.fontScale } returns 1.2f
        every { mockConfiguration.uiMode } returns Configuration.UI_MODE_NIGHT_NO
        
        // When
        val config = accessibilityManager.getAccessibilityConfig()
        
        // Then
        assertTrue(config.isAccessibilityEnabled)
        assertFalse(config.isScreenReaderEnabled)
        assertFalse(config.isHighContrastEnabled)
        assertFalse(config.isLargeTextEnabled)
        assertEquals(1.2f, config.fontScale)
        assertEquals(48, config.minTouchTargetSize)
        assertEquals(14, config.minFontSize)
        assertEquals(24, config.maxFontSize)
        assertEquals(4.5f, config.contrastRatio)
        assertTrue(config.timestamp > 0)
    }

    @Test
    fun `test touch target validation result properties`() {
        // Given
        val validation = AccessibilityManager.TouchTargetValidationResult(
            elementId = "button1",
            currentSize = 50,
            minimumSize = 48,
            isValid = true,
            recommendations = listOf("Touch target size is appropriate")
        )
        
        // Then
        assertEquals("button1", validation.elementId)
        assertEquals(50, validation.currentSize)
        assertEquals(48, validation.minimumSize)
        assertTrue(validation.isValid)
        assertEquals(1, validation.recommendations.size)
    }
}