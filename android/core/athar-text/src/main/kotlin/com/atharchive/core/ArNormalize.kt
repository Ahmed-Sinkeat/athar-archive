package com.atharchive.core

import java.text.Normalizer

/**
 * Port of src/lib/ar-normalize.ts — MUST behave identically or the same query
 * returns different results in the app than on the site (docs/android-app.md,
 * "Correctness constraint"). Same pipeline, same order: NFC → strip marks →
 * unify alef variants → alef maqsura → ta marbuta.
 *
 * Verified against golden vectors generated FROM the TS implementation
 * (`pnpm app:vectors` → ArNormalizeTest). Every Arabic codepoint below is a
 * \u escape, never a literal: bidi display reorders pasted Arabic character
 * classes silently (reproduced twice while writing this file).
 */
object ArNormalize {
    // harakat, quranic annotation marks, superscript alef, tatweel
    private val TASHKEEL =
        Regex("[\u0610-\u061A\u0640\u064B-\u065F\u0670\u06D6-\u06ED]")

    // alef with madda/hamza above/hamza below/wasla → bare alef
    private val ALEF_VARIANTS = Regex("[\u0622\u0623\u0625\u0671]")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC)
            .replace(TASHKEEL, "")
            .replace(ALEF_VARIANTS, "\u0627")
            .replace('\u0649', '\u064A') // alef maqsura -> ya
            .replace('\u0629', '\u0647') // ta marbuta -> ha

    /**
     * Normalizes for search while retaining a UTF-16 range back to [source].
     * Deleted Arabic marks are absorbed into the preceding emitted character;
     * leading marks attach to the following one. This makes a normalized FTS
     * match paint the complete vocalised source rather than leaving gaps.
     */
    fun normalizeWithMap(source: String): Normalized {
        if (source.isEmpty()) return Normalized("", IntArray(0), IntArray(0))

        val normalized = StringBuilder(source.length)
        val starts = ArrayList<Int>(source.length)
        val ends = ArrayList<Int>(source.length)

        fun emitSegment(start: Int, end: Int) {
            val segment = Normalizer.normalize(source.substring(start, end), Normalizer.Form.NFC)
            var emitted = false
            var offset = 0
            while (offset < segment.length) {
                val codePoint = segment.codePointAt(offset)
                offset += Character.charCount(codePoint)
                if (isDeleted(codePoint)) continue

                val mapped = when (codePoint) {
                    in 0x0622..0x0625, 0x0671 -> 0x0627
                    0x0649 -> 0x064A
                    0x0629 -> 0x0647
                    else -> codePoint
                }
                val chars = Character.toChars(mapped)
                normalized.append(chars)
                repeat(chars.size) {
                    starts += start
                    ends += end
                }
                emitted = true
            }

            // A trailing all-mark segment belongs to the preceding character.
            if (!emitted && ends.isNotEmpty()) ends[ends.lastIndex] = end
        }

        var segmentStart = 0
        var offset = 0
        var hasStarter = false
        while (offset < source.length) {
            val codePoint = source.codePointAt(offset)
            val combining = when (Character.getType(codePoint)) {
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
                Character.NON_SPACING_MARK.toInt(),
                -> true
                else -> false
            }
            if (!combining) {
                if (hasStarter) {
                    emitSegment(segmentStart, offset)
                    segmentStart = offset
                }
                hasStarter = true
            }
            offset += Character.charCount(codePoint)
        }
        emitSegment(segmentStart, source.length)

        return Normalized(
            text = normalized.toString(),
            srcStart = starts.toIntArray(),
            srcEnd = ends.toIntArray(),
        )
    }

    private fun isDeleted(codePoint: Int): Boolean =
        codePoint in 0x0610..0x061A ||
            codePoint == 0x0640 ||
            codePoint in 0x064B..0x065F ||
            codePoint == 0x0670 ||
            codePoint in 0x06D6..0x06ED
}

class Normalized internal constructor(
    val text: String,
    private val srcStart: IntArray,
    private val srcEnd: IntArray,
) {
    fun sourceRange(normStart: Int, normEndExclusive: Int): IntRange {
        require(normStart in 0..text.length)
        require(normEndExclusive in normStart..text.length)
        if (normStart == normEndExclusive) return IntRange.EMPTY
        return srcStart[normStart] until srcEnd[normEndExclusive - 1]
    }
}
