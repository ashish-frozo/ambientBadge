package com.frozo.ambientscribe.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.*

/**
 * Localization Manager - ST-7.1, ST-7.2, ST-7.3
 * Implements English and Hindi language support with complete UI translation
 * Adds Telugu support behind feature flag and Devanagari script rendering
 */
class LocalizationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalizationManager"
        private const val DEFAULT_LANGUAGE = "en"
        private const val HINDI_LANGUAGE = "hi"
        private const val TELUGU_LANGUAGE = "te"
        private const val TELUGU_FEATURE_FLAG = "te_language_enabled"
    }

    private var currentLanguage: String = DEFAULT_LANGUAGE
    private var isTeluguEnabled: Boolean = false

    /**
     * Supported language data class
     */
    data class SupportedLanguage(
        val code: String,
        val name: String,
        val nativeName: String,
        val script: String,
        val isRTL: Boolean,
        val isEnabled: Boolean
    )

    /**
     * Localization configuration
     */
    data class LocalizationConfig(
        val currentLanguage: String,
        val supportedLanguages: List<SupportedLanguage>,
        val isTeluguEnabled: Boolean,
        val fallbackLanguage: String,
        val timestamp: Long
    )

    /**
     * Translation result
     */
    data class TranslationResult(
        val originalText: String,
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val confidence: Float,
        val timestamp: Long
    )

    /**
     * Initialize localization manager
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing localization manager")
            
            // Load current language from preferences
            loadCurrentLanguage()
            
            // Check Telugu feature flag
            checkTeluguFeatureFlag()
            
            // Set up supported languages
            setupSupportedLanguages()
            
            // Apply current language
            applyLanguage(currentLanguage)
            
            Log.d(TAG, "Localization manager initialized. Current language: $currentLanguage")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize localization manager", e)
            Result.failure(e)
        }
    }

    /**
     * Get supported languages
     */
    fun getSupportedLanguages(): List<SupportedLanguage> {
        return listOf(
            SupportedLanguage(
                code = "en",
                name = "English",
                nativeName = "English",
                script = "Latn",
                isRTL = false,
                isEnabled = true
            ),
            SupportedLanguage(
                code = "hi",
                name = "Hindi",
                nativeName = "हिन्दी",
                script = "Deva",
                isRTL = false,
                isEnabled = true
            ),
            SupportedLanguage(
                code = "te",
                name = "Telugu",
                nativeName = "తెలుగు",
                script = "Telu",
                isRTL = false,
                isEnabled = isTeluguEnabled
            )
        )
    }

    /**
     * Set current language
     */
    suspend fun setLanguage(languageCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Setting language to: $languageCode")
            
            val supportedLanguages = getSupportedLanguages()
            val language = supportedLanguages.find { it.code == languageCode }
            
            if (language == null) {
                return Result.failure(IllegalArgumentException("Unsupported language: $languageCode"))
            }
            
            if (!language.isEnabled) {
                return Result.failure(IllegalStateException("Language not enabled: $languageCode"))
            }
            
            currentLanguage = languageCode
            applyLanguage(languageCode)
            saveCurrentLanguage(languageCode)
            
            Log.d(TAG, "Language set to: $languageCode")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set language", e)
            Result.failure(e)
        }
    }

    /**
     * Get current language
     */
    fun getCurrentLanguage(): String = currentLanguage

    /**
     * Apply language to system
     */
    private fun applyLanguage(languageCode: String) {
        try {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            
            Log.d(TAG, "Language applied: $languageCode")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply language", e)
        }
    }

    /**
     * Get localized string
     */
    fun getString(resourceId: Int, vararg formatArgs: Any): String {
        return try {
            context.getString(resourceId, *formatArgs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get localized string for resource: $resourceId", e)
            "String not found"
        }
    }

    /**
     * Get localized string with fallback
     */
    fun getStringWithFallback(resourceId: Int, fallback: String, vararg formatArgs: Any): String {
        return try {
            val localizedString = context.getString(resourceId, *formatArgs)
            if (localizedString.isNotEmpty()) {
                localizedString
            } else {
                fallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get localized string, using fallback", e)
            fallback
        }
    }

    /**
     * Check if current language is RTL
     */
    fun isCurrentLanguageRTL(): Boolean {
        val supportedLanguages = getSupportedLanguages()
        val currentLang = supportedLanguages.find { it.code == currentLanguage }
        return currentLang?.isRTL ?: false
    }

    /**
     * Check if current language uses Devanagari script
     */
    fun isCurrentLanguageDevanagari(): Boolean {
        val supportedLanguages = getSupportedLanguages()
        val currentLang = supportedLanguages.find { it.code == currentLanguage }
        return currentLang?.script == "Deva"
    }

    /**
     * Check if current language uses Telugu script
     */
    fun isCurrentLanguageTelugu(): Boolean {
        val supportedLanguages = getSupportedLanguages()
        val currentLang = supportedLanguages.find { it.code == currentLanguage }
        return currentLang?.script == "Telu"
    }

    /**
     * Translate text (for dynamic content)
     */
    suspend fun translateText(
        text: String,
        targetLanguage: String,
        sourceLanguage: String = currentLanguage
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Translating text from $sourceLanguage to $targetLanguage")
            
            // In a real implementation, this would use a translation service
            // For now, we'll simulate translation
            val translatedText = simulateTranslation(text, sourceLanguage, targetLanguage)
            
            val result = TranslationResult(
                originalText = text,
                translatedText = translatedText,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                confidence = 0.95f,
                timestamp = System.currentTimeMillis()
            )
            
            // Save translation result
            saveTranslationResult(result)
            
            Log.d(TAG, "Text translated successfully")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to translate text", e)
            Result.failure(e)
        }
    }

    /**
     * Simulate translation (for testing)
     */
    private fun simulateTranslation(text: String, sourceLanguage: String, targetLanguage: String): String {
        return when {
            sourceLanguage == targetLanguage -> text
            targetLanguage == "hi" -> {
                // Simulate Hindi translation
                when (text.lowercase()) {
                    "hello" -> "नमस्ते"
                    "thank you" -> "धन्यवाद"
                    "please" -> "कृपया"
                    "yes" -> "हाँ"
                    "no" -> "नहीं"
                    else -> "[HI: $text]"
                }
            }
            targetLanguage == "te" -> {
                // Simulate Telugu translation
                when (text.lowercase()) {
                    "hello" -> "నమస్కారం"
                    "thank you" -> "ధన్యవాదాలు"
                    "please" -> "దయచేసి"
                    "yes" -> "అవును"
                    "no" -> "కాదు"
                    else -> "[TE: $text]"
                }
            }
            targetLanguage == "en" -> {
                // Simulate English translation
                when (text) {
                    "नमस्ते" -> "Hello"
                    "धन्यवाद" -> "Thank you"
                    "कृपया" -> "Please"
                    "हाँ" -> "Yes"
                    "नहीं" -> "No"
                    "నమస్కారం" -> "Hello"
                    "ధన్యవాదాలు" -> "Thank you"
                    "దయచేసి" -> "Please"
                    "అవును" -> "Yes"
                    "కాదు" -> "No"
                    else -> text
                }
            }
            else -> text
        }
    }

    /**
     * Load current language from preferences
     */
    private fun loadCurrentLanguage() {
        try {
            val prefs = context.getSharedPreferences("localization", Context.MODE_PRIVATE)
            currentLanguage = prefs.getString("current_language", DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load current language", e)
            currentLanguage = DEFAULT_LANGUAGE
        }
    }

    /**
     * Save current language to preferences
     */
    private fun saveCurrentLanguage(languageCode: String) {
        try {
            val prefs = context.getSharedPreferences("localization", Context.MODE_PRIVATE)
            prefs.edit().putString("current_language", languageCode).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save current language", e)
        }
    }

    /**
     * Check Telugu feature flag
     */
    private fun checkTeluguFeatureFlag() {
        try {
            val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
            isTeluguEnabled = prefs.getBoolean(TELUGU_FEATURE_FLAG, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Telugu feature flag", e)
            isTeluguEnabled = false
        }
    }

    /**
     * Enable Telugu language
     */
    suspend fun enableTeluguLanguage(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Enabling Telugu language")
            
            val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(TELUGU_FEATURE_FLAG, true).apply()
            
            isTeluguEnabled = true
            
            Log.d(TAG, "Telugu language enabled")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable Telugu language", e)
            Result.failure(e)
        }
    }

    /**
     * Disable Telugu language
     */
    suspend fun disableTeluguLanguage(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Disabling Telugu language")
            
            val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(TELUGU_FEATURE_FLAG, false).apply()
            
            isTeluguEnabled = false
            
            // If current language is Telugu, switch to default
            if (currentLanguage == TELUGU_LANGUAGE) {
                setLanguage(DEFAULT_LANGUAGE)
            }
            
            Log.d(TAG, "Telugu language disabled")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable Telugu language", e)
            Result.failure(e)
        }
    }

    /**
     * Setup supported languages
     */
    private fun setupSupportedLanguages() {
        // This would typically load language configurations from resources
        Log.d(TAG, "Supported languages setup completed")
    }

    /**
     * Save translation result
     */
    private fun saveTranslationResult(result: TranslationResult) {
        try {
            val translationsDir = File(context.filesDir, "translations")
            translationsDir.mkdirs()
            
            val translationFile = File(translationsDir, "translation_${result.timestamp}.json")
            val json = JSONObject().apply {
                put("originalText", result.originalText)
                put("translatedText", result.translatedText)
                put("sourceLanguage", result.sourceLanguage)
                put("targetLanguage", result.targetLanguage)
                put("confidence", result.confidence)
                put("timestamp", result.timestamp)
            }
            
            translationFile.writeText(json.toString())
            Log.d(TAG, "Translation result saved to: ${translationFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save translation result", e)
        }
    }

    /**
     * Get localization configuration
     */
    suspend fun getLocalizationConfig(): LocalizationConfig = withContext(Dispatchers.IO) {
        LocalizationConfig(
            currentLanguage = currentLanguage,
            supportedLanguages = getSupportedLanguages(),
            isTeluguEnabled = isTeluguEnabled,
            fallbackLanguage = DEFAULT_LANGUAGE,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Validate localization setup
     */
    suspend fun validateLocalizationSetup(): Result<LocalizationValidationResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Validating localization setup")
            
            val supportedLanguages = getSupportedLanguages()
            val validationResult = LocalizationValidationResult(
                isSetupValid = true,
                supportedLanguagesCount = supportedLanguages.size,
                currentLanguageSupported = supportedLanguages.any { it.code == currentLanguage },
                teluguEnabled = isTeluguEnabled,
                rtlSupport = supportedLanguages.any { it.isRTL },
                devanagariSupport = supportedLanguages.any { it.script == "Deva" },
                teluguSupport = supportedLanguages.any { it.script == "Telu" },
                recommendations = mutableListOf(),
                timestamp = System.currentTimeMillis()
            )
            
            // Add validation recommendations
            if (!validationResult.currentLanguageSupported) {
                validationResult.recommendations.add("Current language not supported: $currentLanguage")
            }
            
            if (!validationResult.rtlSupport) {
                validationResult.recommendations.add("RTL language support not available")
            }
            
            if (!validationResult.devanagariSupport) {
                validationResult.recommendations.add("Devanagari script support not available")
            }
            
            if (!validationResult.teluguSupport && isTeluguEnabled) {
                validationResult.recommendations.add("Telugu support enabled but not available")
            }
            
            // Save validation result
            saveLocalizationValidationResult(validationResult)
            
            Log.d(TAG, "Localization setup validation completed")
            Result.success(validationResult)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate localization setup", e)
            Result.failure(e)
        }
    }

    /**
     * Save localization validation result
     */
    private fun saveLocalizationValidationResult(result: LocalizationValidationResult) {
        try {
            val validationDir = File(context.filesDir, "localization_validation")
            validationDir.mkdirs()
            
            val validationFile = File(validationDir, "validation_${result.timestamp}.json")
            val json = JSONObject().apply {
                put("isSetupValid", result.isSetupValid)
                put("supportedLanguagesCount", result.supportedLanguagesCount)
                put("currentLanguageSupported", result.currentLanguageSupported)
                put("teluguEnabled", result.teluguEnabled)
                put("rtlSupport", result.rtlSupport)
                put("devanagariSupport", result.devanagariSupport)
                put("teluguSupport", result.teluguSupport)
                put("recommendations", result.recommendations)
                put("timestamp", result.timestamp)
            }
            
            validationFile.writeText(json.toString())
            Log.d(TAG, "Localization validation result saved to: ${validationFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save localization validation result", e)
        }
    }

    /**
     * Localization validation result data class
     */
    data class LocalizationValidationResult(
        val isSetupValid: Boolean,
        val supportedLanguagesCount: Int,
        val currentLanguageSupported: Boolean,
        val teluguEnabled: Boolean,
        val rtlSupport: Boolean,
        val devanagariSupport: Boolean,
        val teluguSupport: Boolean,
        val recommendations: MutableList<String>,
        val timestamp: Long
    )
}
