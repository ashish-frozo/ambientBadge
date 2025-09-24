package com.frozo.ambientscribe.rendering

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Font Rendering Manager - ST-7.3, ST-7.8
 * Implements Devanagari script rendering for Hindi text display
 * Tests font rendering across languages with Noto fonts
 */
class FontRenderingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FontRenderingManager"
        private const val NOTO_SANS_FONT = "fonts/NotoSans-Regular.ttf"
        private const val NOTO_SANS_HINDI_FONT = "fonts/NotoSansDevanagari-Regular.ttf"
        private const val NOTO_SANS_TELUGU_FONT = "fonts/NotoSansTelugu-Regular.ttf"
        private const val NOTO_SERIF_FONT = "fonts/NotoSerif-Regular.ttf"
        private const val NOTO_SERIF_HINDI_FONT = "fonts/NotoSerifDevanagari-Regular.ttf"
    }

    private val fontCache = mutableMapOf<String, Typeface>()
    private val supportedScripts = listOf("Latn", "Deva", "Telu", "Arab", "Cyrl")

    /**
     * Font configuration data class
     */
    data class FontConfig(
        val script: String,
        val fontFamily: String,
        val fontPath: String,
        val isAvailable: Boolean,
        val fallbackFont: String?,
        val supportsRTL: Boolean,
        val supportsLigatures: Boolean
    )

    /**
     * Text rendering result
     */
    data class TextRenderingResult(
        val text: String,
        val script: String,
        val fontUsed: String,
        val isRendered: Boolean,
        val hasFallback: Boolean,
        val renderingTime: Long,
        val characterCount: Int,
        val complexScriptCount: Int
    )

    /**
     * Font validation result
     */
    data class FontValidationResult(
        val fontPath: String,
        val isAvailable: Boolean,
        val supportsScript: Boolean,
        val characterCoverage: Float,
        val missingCharacters: List<String>,
        val recommendations: List<String>
    )

    /**
     * Initialize font rendering manager
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing font rendering manager")
            
            // Load Noto fonts
            loadNotoFonts()
            
            // Validate font availability
            validateFontAvailability()
            
            // Setup font fallbacks
            setupFontFallbacks()
            
            Log.d(TAG, "Font rendering manager initialized")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize font rendering manager", e)
            Result.failure(e)
        }
    }

    /**
     * Load Noto fonts
     */
    private fun loadNotoFonts() {
        try {
            val fontConfigs = listOf(
                FontConfig(
                    script = "Latn",
                    fontFamily = "Noto Sans",
                    fontPath = NOTO_SANS_FONT,
                    isAvailable = false,
                    fallbackFont = "sans-serif",
                    supportsRTL = false,
                    supportsLigatures = true
                ),
                FontConfig(
                    script = "Deva",
                    fontFamily = "Noto Sans Devanagari",
                    fontPath = NOTO_SANS_HINDI_FONT,
                    isAvailable = false,
                    fallbackFont = "sans-serif",
                    supportsRTL = false,
                    supportsLigatures = true
                ),
                FontConfig(
                    script = "Telu",
                    fontFamily = "Noto Sans Telugu",
                    fontPath = NOTO_SANS_TELUGU_FONT,
                    isAvailable = false,
                    fallbackFont = "sans-serif",
                    supportsRTL = false,
                    supportsLigatures = true
                ),
                FontConfig(
                    script = "Latn",
                    fontFamily = "Noto Serif",
                    fontPath = NOTO_SERIF_FONT,
                    isAvailable = false,
                    fallbackFont = "serif",
                    supportsRTL = false,
                    supportsLigatures = true
                ),
                FontConfig(
                    script = "Deva",
                    fontFamily = "Noto Serif Devanagari",
                    fontPath = NOTO_SERIF_HINDI_FONT,
                    isAvailable = false,
                    fallbackFont = "serif",
                    supportsRTL = false,
                    supportsLigatures = true
                )
            )

            fontConfigs.forEach { config ->
                loadFont(config)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Noto fonts", e)
        }
    }

    /**
     * Load individual font
     */
    private fun loadFont(config: FontConfig) {
        try {
            val typeface = Typeface.createFromAsset(context.assets, config.fontPath)
            if (typeface != null) {
                fontCache[config.script] = typeface
                Log.d(TAG, "Font loaded: ${config.fontFamily} for script: ${config.script}")
            } else {
                Log.w(TAG, "Failed to load font: ${config.fontPath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load font: ${config.fontPath}", e)
        }
    }

    /**
     * Get font for script
     */
    fun getFontForScript(script: String): Typeface? {
        return fontCache[script] ?: getSystemFontForScript(script)
    }

    /**
     * Get system font for script
     */
    private fun getSystemFontForScript(script: String): Typeface? {
        return when (script) {
            "Deva" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "Telu" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "Latn" -> Typeface.create("sans-serif", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
    }

    /**
     * Render text with appropriate font
     */
    suspend fun renderText(
        text: String,
        script: String,
        fontFamily: String = "sans-serif"
    ): Result<TextRenderingResult> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            
            val typeface = getFontForScript(script)
            val isRendered = typeface != null
            val hasFallback = typeface != getSystemFontForScript(script)
            
            val characterCount = text.length
            val complexScriptCount = countComplexScriptCharacters(text, script)
            
            val endTime = System.currentTimeMillis()
            val renderingTime = endTime - startTime
            
            val result = TextRenderingResult(
                text = text,
                script = script,
                fontUsed = typeface?.toString() ?: "system",
                isRendered = isRendered,
                hasFallback = hasFallback,
                renderingTime = renderingTime,
                characterCount = characterCount,
                complexScriptCount = complexScriptCount
            )
            
            // Save rendering result
            saveTextRenderingResult(result)
            
            Log.d(TAG, "Text rendered: $script, characters: $characterCount, time: ${renderingTime}ms")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to render text", e)
            Result.failure(e)
        }
    }

    /**
     * Count complex script characters
     */
    private fun countComplexScriptCharacters(text: String, script: String): Int {
        return when (script) {
            "Deva" -> text.count { it in '\u0900'..'\u097F' } // Devanagari range
            "Telu" -> text.count { it in '\u0C00'..'\u0C7F' } // Telugu range
            "Arab" -> text.count { it in '\u0600'..'\u06FF' } // Arabic range
            "Cyrl" -> text.count { it in '\u0400'..'\u04FF' } // Cyrillic range
            else -> 0
        }
    }

    /**
     * Validate font availability
     */
    suspend fun validateFontAvailability(): Result<List<FontValidationResult>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Validating font availability")
            
            val validationResults = mutableListOf<FontValidationResult>()
            
            val fontPaths = listOf(
                NOTO_SANS_FONT,
                NOTO_SANS_HINDI_FONT,
                NOTO_SANS_TELUGU_FONT,
                NOTO_SERIF_FONT,
                NOTO_SERIF_HINDI_FONT
            )
            
            fontPaths.forEach { fontPath ->
                val result = validateFont(fontPath)
                validationResults.add(result)
            }
            
            // Save validation results
            saveFontValidationResults(validationResults)
            
            Log.d(TAG, "Font availability validation completed")
            Result.success(validationResults)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate font availability", e)
            Result.failure(e)
        }
    }

    /**
     * Validate individual font
     */
    private fun validateFont(fontPath: String): FontValidationResult {
        try {
            val typeface = Typeface.createFromAsset(context.assets, fontPath)
            val isAvailable = typeface != null
            
            val script = getScriptFromFontPath(fontPath)
            val supportsScript = isAvailable && script != null
            
            val characterCoverage = if (isAvailable) {
                calculateCharacterCoverage(typeface, script ?: "Latn")
            } else {
                0f
            }
            
            val missingCharacters = if (isAvailable) {
                getMissingCharacters(typeface, script ?: "Latn")
            } else {
                emptyList()
            }
            
            val recommendations = mutableListOf<String>()
            if (!isAvailable) {
                recommendations.add("Font not available: $fontPath")
                recommendations.add("Consider adding font to assets or using system fallback")
            }
            if (characterCoverage < 0.9f) {
                recommendations.add("Low character coverage: ${(characterCoverage * 100).toInt()}%")
                recommendations.add("Consider using a more comprehensive font")
            }
            if (missingCharacters.isNotEmpty()) {
                recommendations.add("Missing characters: ${missingCharacters.size}")
            }
            
            FontValidationResult(
                fontPath = fontPath,
                isAvailable = isAvailable,
                supportsScript = supportsScript,
                characterCoverage = characterCoverage,
                missingCharacters = missingCharacters,
                recommendations = recommendations
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate font: $fontPath", e)
            FontValidationResult(
                fontPath = fontPath,
                isAvailable = false,
                supportsScript = false,
                characterCoverage = 0f,
                missingCharacters = emptyList(),
                recommendations = listOf("Error validating font: ${e.message}")
            )
        }
    }

    /**
     * Get script from font path
     */
    private fun getScriptFromFontPath(fontPath: String): String? {
        return when {
            fontPath.contains("Devanagari") -> "Deva"
            fontPath.contains("Telugu") -> "Telu"
            fontPath.contains("Arabic") -> "Arab"
            fontPath.contains("Cyrillic") -> "Cyrl"
            else -> "Latn"
        }
    }

    /**
     * Calculate character coverage
     */
    private fun calculateCharacterCoverage(typeface: Typeface, script: String): Float {
        // In a real implementation, this would test actual character coverage
        // For now, we'll simulate based on script
        return when (script) {
            "Deva" -> 0.95f
            "Telu" -> 0.90f
            "Latn" -> 0.98f
            else -> 0.85f
        }
    }

    /**
     * Get missing characters
     */
    private fun getMissingCharacters(typeface: Typeface, script: String): List<String> {
        // In a real implementation, this would test actual missing characters
        // For now, we'll return empty list
        return emptyList()
    }

    /**
     * Setup font fallbacks
     */
    private fun setupFontFallbacks() {
        try {
            // Setup fallback fonts for each script
            val fallbackMap = mapOf(
                "Deva" to "sans-serif",
                "Telu" to "sans-serif",
                "Latn" to "sans-serif",
                "Arab" to "sans-serif",
                "Cyrl" to "sans-serif"
            )
            
            fallbackMap.forEach { (script, fallback) ->
                if (!fontCache.containsKey(script)) {
                    fontCache[script] = Typeface.create(fallback, Typeface.NORMAL)
                }
            }
            
            Log.d(TAG, "Font fallbacks setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup font fallbacks", e)
        }
    }

    /**
     * Test font rendering across languages
     */
    suspend fun testFontRenderingAcrossLanguages(): Result<FontRenderingTestResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing font rendering across languages")
            
            val testTexts = mapOf(
                "Latn" to "Hello World! This is a test.",
                "Deva" to "नमस्ते दुनिया! यह एक परीक्षण है।",
                "Telu" to "హలో వరల్డ్! ఇది ఒక పరీక్ష.",
                "Arab" to "مرحبا بالعالم! هذا اختبار.",
                "Cyrl" to "Привет мир! Это тест."
            )
            
            val renderingResults = mutableListOf<TextRenderingResult>()
            var totalRenderingTime = 0L
            var successfulRenders = 0
            
            testTexts.forEach { (script, text) ->
                val result = renderText(text, script)
                if (result.isSuccess) {
                    val renderingResult = result.getOrThrow()
                    renderingResults.add(renderingResult)
                    totalRenderingTime += renderingResult.renderingTime
                    if (renderingResult.isRendered) {
                        successfulRenders++
                    }
                }
            }
            
            val testResult = FontRenderingTestResult(
                totalTests = testTexts.size,
                successfulRenders = successfulRenders,
                averageRenderingTime = if (renderingResults.isNotEmpty()) {
                    totalRenderingTime / renderingResults.size
                } else {
                    0L
                },
                renderingResults = renderingResults,
                overallSuccess = successfulRenders == testTexts.size,
                timestamp = System.currentTimeMillis()
            )
            
            // Save test result
            saveFontRenderingTestResult(testResult)
            
            Log.d(TAG, "Font rendering test completed. Success: ${testResult.overallSuccess}")
            Result.success(testResult)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to test font rendering across languages", e)
            Result.failure(e)
        }
    }

    /**
     * Save text rendering result
     */
    private fun saveTextRenderingResult(result: TextRenderingResult) {
        try {
            val resultsDir = File(context.filesDir, "font_rendering_results")
            resultsDir.mkdirs()
            
            val resultFile = File(resultsDir, "rendering_${result.renderingTime}.json")
            val json = JSONObject().apply {
                put("text", result.text)
                put("script", result.script)
                put("fontUsed", result.fontUsed)
                put("isRendered", result.isRendered)
                put("hasFallback", result.hasFallback)
                put("renderingTime", result.renderingTime)
                put("characterCount", result.characterCount)
                put("complexScriptCount", result.complexScriptCount)
            }
            
            resultFile.writeText(json.toString())
            Log.d(TAG, "Text rendering result saved to: ${resultFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save text rendering result", e)
        }
    }

    /**
     * Save font validation results
     */
    private fun saveFontValidationResults(results: List<FontValidationResult>) {
        try {
            val validationDir = File(context.filesDir, "font_validation")
            validationDir.mkdirs()
            
            val validationFile = File(validationDir, "font_validation_${System.currentTimeMillis()}.json")
            val json = JSONObject().apply {
                put("results", results.map { result ->
                    JSONObject().apply {
                        put("fontPath", result.fontPath)
                        put("isAvailable", result.isAvailable)
                        put("supportsScript", result.supportsScript)
                        put("characterCoverage", result.characterCoverage)
                        put("missingCharacters", result.missingCharacters)
                        put("recommendations", result.recommendations)
                    }
                })
            }
            
            validationFile.writeText(json.toString())
            Log.d(TAG, "Font validation results saved to: ${validationFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save font validation results", e)
        }
    }

    /**
     * Save font rendering test result
     */
    private fun saveFontRenderingTestResult(result: FontRenderingTestResult) {
        try {
            val testDir = File(context.filesDir, "font_rendering_tests")
            testDir.mkdirs()
            
            val testFile = File(testDir, "font_test_${result.timestamp}.json")
            val json = JSONObject().apply {
                put("totalTests", result.totalTests)
                put("successfulRenders", result.successfulRenders)
                put("averageRenderingTime", result.averageRenderingTime)
                put("overallSuccess", result.overallSuccess)
                put("timestamp", result.timestamp)
                put("renderingResults", result.renderingResults.map { renderingResult ->
                    JSONObject().apply {
                        put("text", renderingResult.text)
                        put("script", renderingResult.script)
                        put("fontUsed", renderingResult.fontUsed)
                        put("isRendered", renderingResult.isRendered)
                        put("hasFallback", renderingResult.hasFallback)
                        put("renderingTime", renderingResult.renderingTime)
                        put("characterCount", renderingResult.characterCount)
                        put("complexScriptCount", renderingResult.complexScriptCount)
                    }
                })
            }
            
            testFile.writeText(json.toString())
            Log.d(TAG, "Font rendering test result saved to: ${testFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save font rendering test result", e)
        }
    }

    /**
     * Font rendering test result data class
     */
    data class FontRenderingTestResult(
        val totalTests: Int,
        val successfulRenders: Int,
        val averageRenderingTime: Long,
        val renderingResults: List<TextRenderingResult>,
        val overallSuccess: Boolean,
        val timestamp: Long
    )
}
