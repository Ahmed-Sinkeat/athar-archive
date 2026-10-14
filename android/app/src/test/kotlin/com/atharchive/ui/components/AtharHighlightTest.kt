package com.atharchive.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AtharHighlightTest {
    @Test fun arabicVariantsHighlightTheWholeOriginalWord() {
        val text = "إِيمان و إيمان"
        val result = highlightMatches(text, "ايمان", Color.Yellow)
        assertEquals(text, result.text)
        assertEquals(listOf(0 to 6, 9 to 14), result.spanStyles.map { it.start to it.end })
        assertEquals(emptyList<Any>(), highlightMatches(text, "َ", Color.Yellow).spanStyles)
    }
}
