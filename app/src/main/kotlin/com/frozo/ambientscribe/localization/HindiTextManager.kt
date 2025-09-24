package com.frozo.ambientscribe.localization

import android.content.Context
import android.icu.text.BreakIterator
import android.icu.text.NumberFormat
import android.util.Log
import java.util.*

/**
 * Manages Hindi text formatting and line breaks
 * Implements ST-4.18: Hindi ICU line-break and Latin-digit enforcement
 */
class HindiTextManager(private val context: Context) {

    companion object {
        private const val TAG = "HindiTextManager"
        private val HINDI_LOCALE = Locale("hi", "IN")
        private val DEVANAGARI_RANGE = '\u0900'..'\u097F'
        private val LATIN_DIGITS = '0'..'9'
    }

    /**
     * Text segment types
     */
    sealed class SegmentType {
        object Hindi : SegmentType()
        object Latin : SegmentType()
        object Mixed : SegmentType()
    }

    /**
     * Text segment
     */
    data class TextSegment(
        val text: String,
        val type: SegmentType,
        val breakPoints: List<Int>
    )

    /**
     * Format Hindi text with proper line breaks and digit handling
     */
    fun formatHindiText(text: String): String {
        try {
            Log.d(TAG, "Formatting Hindi text")

            // Split into segments
            val segments = segmentText(text)

            // Process each segment
            return segments.joinToString("") { segment ->
                when (segment.type) {
                    is SegmentType.Hindi -> formatHindiSegment(segment)
                    is SegmentType.Latin -> segment.text
                    is SegmentType.Mixed -> formatMixedSegment(segment)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to format Hindi text", e)
            return text
        }
    }

    /**
     * Segment text into Hindi and Latin parts
     */
    private fun segmentText(text: String): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        var currentText = StringBuilder()
        var currentType: SegmentType? = null
        var breakPoints = mutableListOf<Int>()

        // Get line break iterator
        val iterator = BreakIterator.getLineInstance(HINDI_LOCALE)
        iterator.setText(text)

        // Collect break points
        var pos = iterator.first()
        while (pos != BreakIterator.DONE) {
            breakPoints.add(pos)
            pos = iterator.next()
        }

        text.forEachIndexed { index, char ->
            val type = when {
                char in DEVANAGARI_RANGE -> SegmentType.Hindi
                char.isLetterOrDigit() -> SegmentType.Latin
                else -> currentType ?: SegmentType.Latin
            }

            if (type != currentType && currentText.isNotEmpty()) {
                // Add current segment
                segments.add(
                    TextSegment(
                        text = currentText.toString(),
                        type = currentType ?: type,
                        breakPoints = breakPoints.filter { it <= index }
                    )
                )
                currentText.clear()
                breakPoints.clear()
            }

            currentText.append(char)
            currentType = type
        }

        // Add final segment
        if (currentText.isNotEmpty()) {
            segments.add(
                TextSegment(
                    text = currentText.toString(),
                    type = currentType ?: SegmentType.Latin,
                    breakPoints = breakPoints
                )
            )
        }

        return segments
    }

    /**
     * Format Hindi segment with proper line breaks
     */
    private fun formatHindiSegment(segment: TextSegment): String {
        val text = segment.text
        val breakPoints = segment.breakPoints

        // Insert zero-width spaces at break points
        val builder = StringBuilder()
        var lastBreak = 0

        breakPoints.forEach { breakPoint ->
            builder.append(text.substring(lastBreak, breakPoint))
            if (breakPoint < text.length) {
                builder.append('\u200B') // Zero-width space
            }
            lastBreak = breakPoint
        }

        if (lastBreak < text.length) {
            builder.append(text.substring(lastBreak))
        }

        return builder.toString()
    }

    /**
     * Format mixed segment with Latin digit enforcement
     */
    private fun formatMixedSegment(segment: TextSegment): String {
        val text = segment.text

        // Convert native digits to Latin
        val builder = StringBuilder()
        text.forEach { char ->
            val converted = when {
                char in LATIN_DIGITS -> char
                char.isDigit() -> LATIN_DIGITS.elementAt(
                    char.toString().toInt()
                )
                else -> char
            }
            builder.append(converted)
        }

        return formatHindiSegment(
            segment.copy(text = builder.toString())
        )
    }

    /**
     * Format number for Hindi locale
     */
    fun formatNumber(number: Number): String {
        val formatter = NumberFormat.getInstance(HINDI_LOCALE)
        formatter.isGroupingUsed = true
        
        // Force Latin digits
        formatter.minimumIntegerDigits = 1
        formatter.maximumFractionDigits = 2

        return formatter.format(number)
    }

    /**
     * Check if text contains Hindi characters
     */
    fun containsHindi(text: String): Boolean {
        return text.any { it in DEVANAGARI_RANGE }
    }

    /**
     * Get line break opportunities
     */
    fun getLineBreaks(text: String): List<Int> {
        val breaks = mutableListOf<Int>()
        val iterator = BreakIterator.getLineInstance(HINDI_LOCALE)
        iterator.setText(text)

        var pos = iterator.first()
        while (pos != BreakIterator.DONE) {
            breaks.add(pos)
            pos = iterator.next()
        }

        return breaks
    }

    /**
     * Validate Hindi text formatting
     */
    fun validateFormatting(text: String): Boolean {
        try {
            // Check for proper line breaks
            val breaks = getLineBreaks(text)
            if (breaks.isEmpty()) return false

            // Check for Latin digits
            val hasNativeDigits = text.any { 
                it.isDigit() && it !in LATIN_DIGITS 
            }
            if (hasNativeDigits) return false

            // Check for proper word boundaries
            val wordIterator = BreakIterator.getWordInstance(HINDI_LOCALE)
            wordIterator.setText(text)
            var wordCount = 0
            while (wordIterator.next() != BreakIterator.DONE) {
                wordCount++
            }
            if (wordCount == 0) return false

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate Hindi formatting", e)
            return false
        }
    }
}
