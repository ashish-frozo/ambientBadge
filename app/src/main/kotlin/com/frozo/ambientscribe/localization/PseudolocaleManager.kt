package com.frozo.ambientscribe.localization

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.Log
import java.util.*

/**
 * Manages pseudolocale support for testing
 * Implements ST-4.16: Pseudolocale print proof
 */
class PseudolocaleManager(private val context: Context) {

    companion object {
        private const val TAG = "PseudolocaleManager"
        private const val PSEUDO_LOCALE_XA = "en-XA"  // Accented English
        private const val PSEUDO_LOCALE_XB = "ar-XB"  // Pseudo-bidi
    }

    /**
     * Pseudolocale configuration
     */
    data class PseudoConfig(
        val enabled: Boolean = false,
        val locale: String = PSEUDO_LOCALE_XA,
        val expandLength: Float = 1.3f,
        val preserveTags: Boolean = true
    )

    /**
     * Enable pseudolocale for testing
     */
    fun enablePseudolocale(config: PseudoConfig): Configuration {
        try {
            Log.d(TAG, "Enabling pseudolocale: ${config.locale}")

            // Create locale
            val locale = when (config.locale) {
                PSEUDO_LOCALE_XA -> Locale("en", "XA")
                PSEUDO_LOCALE_XB -> Locale("ar", "XB")
                else -> throw IllegalArgumentException("Unsupported pseudo locale")
            }

            // Create configuration
            val newConfig = Configuration(context.resources.configuration)
            newConfig.setLocales(LocaleList(locale))

            Log.i(TAG, "Pseudolocale enabled: $locale")
            return newConfig

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable pseudolocale", e)
            throw e
        }
    }

    /**
     * Disable pseudolocale
     */
    fun disablePseudolocale(): Configuration {
        try {
            Log.d(TAG, "Disabling pseudolocale")

            // Restore default locale
            val newConfig = Configuration(context.resources.configuration)
            newConfig.setLocales(LocaleList(Locale.getDefault()))

            Log.i(TAG, "Pseudolocale disabled")
            return newConfig

        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable pseudolocale", e)
            throw e
        }
    }

    /**
     * Transform text for pseudolocale testing
     */
    fun transformText(
        text: String,
        config: PseudoConfig
    ): String {
        if (!config.enabled) return text

        return try {
            when (config.locale) {
                PSEUDO_LOCALE_XA -> transformAccented(text, config)
                PSEUDO_LOCALE_XB -> transformBidi(text, config)
                else -> text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transform text", e)
            text
        }
    }

    /**
     * Transform text to accented English
     */
    private fun transformAccented(
        text: String,
        config: PseudoConfig
    ): String {
        val builder = StringBuilder()
        var inTag = false

        text.forEach { char ->
            if (config.preserveTags) {
                if (char == '<') inTag = true
                if (char == '>') inTag = false
            }

            if (!inTag) {
                // Map characters to accented versions
                builder.append(
                    when (char) {
                        'a' -> 'á'
                        'e' -> 'é'
                        'i' -> 'í'
                        'o' -> 'ó'
                        'u' -> 'ú'
                        'n' -> 'ñ'
                        'A' -> 'Á'
                        'E' -> 'É'
                        'I' -> 'Í'
                        'O' -> 'Ó'
                        'U' -> 'Ú'
                        'N' -> 'Ñ'
                        else -> char
                    }
                )

                // Add expansion characters
                if (config.expandLength > 1.0f && char.isLetterOrDigit()) {
                    val expandCount = ((config.expandLength - 1.0f) * 2).toInt()
                    repeat(expandCount) {
                        builder.append('·')
                    }
                }
            } else {
                builder.append(char)
            }
        }

        return builder.toString()
    }

    /**
     * Transform text for RTL testing
     */
    private fun transformBidi(
        text: String,
        config: PseudoConfig
    ): String {
        // Add RTL markers
        val rtl = "\u200F"  // RTL mark
        val ltr = "\u200E"  // LTR mark
        
        return buildString {
            append(rtl)
            
            // Process text
            var inTag = false
            text.forEach { char ->
                if (config.preserveTags) {
                    if (char == '<') inTag = true
                    if (char == '>') inTag = false
                }

                if (!inTag) {
                    // Add directional marks around numbers
                    if (char.isDigit()) {
                        append(ltr)
                        append(char)
                        append(rtl)
                    } else {
                        append(char)
                    }

                    // Add expansion for testing
                    if (config.expandLength > 1.0f && char.isLetterOrDigit()) {
                        val expandCount = ((config.expandLength - 1.0f) * 2).toInt()
                        repeat(expandCount) {
                            append('·')
                        }
                    }
                } else {
                    append(char)
                }
            }
        }
    }

    /**
     * Check if text contains RTL content
     */
    fun containsRTL(text: String): Boolean {
        return text.any { char ->
            Character.getDirectionality(char) in arrayOf(
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
            )
        }
    }

    /**
     * Get current pseudolocale status
     */
    fun getCurrentPseudolocale(): String? {
        val locale = context.resources.configuration.locales[0]
        return when {
            locale.language == "en" && locale.country == "XA" -> PSEUDO_LOCALE_XA
            locale.language == "ar" && locale.country == "XB" -> PSEUDO_LOCALE_XB
            else -> null
        }
    }
}
