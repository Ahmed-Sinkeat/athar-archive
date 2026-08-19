package com.atharchive.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Marks every occurrence of [query] in [text] as the user types, so a result visibly
 * explains itself: you can see which letters matched, not just that something did.
 *
 * Matching is literal for now. When the data layer lands this becomes the normalized
 * comparison from `core/athar-text` — a reader searching «الصلاة» must match «الصَّلَاةِ»,
 * and the highlight has to land on the vocalised span including its diacritics
 * (main-plan.md §10.2), which a raw `indexOf` cannot do.
 */
fun highlightMatches(
    text: String,
    query: String,
    background: Color,
    bold: Boolean = true,
): AnnotatedString {
    val term = query.trim()
    if (term.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var from = 0
        while (true) {
            val at = text.indexOf(term, from, ignoreCase = true)
            if (at < 0) {
                append(text.substring(from))
                return@buildAnnotatedString
            }
            append(text.substring(from, at))
            withStyle(
                SpanStyle(
                    background = background,
                    fontWeight = if (bold) FontWeight.Bold else null,
                ),
            ) {
                append(text.substring(at, at + term.length))
            }
            from = at + term.length
        }
    }
}
