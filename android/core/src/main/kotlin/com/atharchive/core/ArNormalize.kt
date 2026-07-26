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
}
