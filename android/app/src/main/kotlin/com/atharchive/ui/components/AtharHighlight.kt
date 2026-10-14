package com.atharchive.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Matches normalized Arabic while painting the original source offsets. */
fun highlightMatches(
    text: String,
    query: String,
    background: Color,
    bold: Boolean = true,
): AnnotatedString {
    val term = com.atharchive.core.ArNormalize.normalize(query.trim())
    if (term.isEmpty()) return AnnotatedString(text)
    val normalized = com.atharchive.core.ArNormalize.normalizeWithMap(text)
    return buildAnnotatedString {
        append(text)
        var from = 0
        while (true) {
            val at = normalized.text.indexOf(term, from, ignoreCase = true)
            if (at < 0) break
            val range = normalized.sourceRange(at, at + term.length)
            addStyle(SpanStyle(background = background, fontWeight = if (bold) FontWeight.Bold else null),
                range.first, range.last + 1)
            from = at + term.length
        }
    }
}
