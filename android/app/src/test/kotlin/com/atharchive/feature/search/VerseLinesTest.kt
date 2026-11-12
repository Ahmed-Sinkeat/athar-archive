package com.atharchive.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class VerseLinesTest {
    @Test fun oneHemistichPerLineSameLength() {
        val hit = "…صدر أول … عجز أول ٤ - صدر ثان … عجز ثان…"
        val lines = verseLines(hit)
        assertEquals(hit.length, lines.length)
        assertEquals(listOf("…صدر أول ", " عجز أول", "٤ - صدر ثان ", " عجز ثان…"), lines.split("\n"))
    }
}

class AroundVersesTest {
    private val numbered = (1..9).joinToString(" ") { "${"٠١٢٣٤٥٦٧٨٩"[it]} - صدر$it طويل … عجز$it طويل" }
    private val runOn = (1..9).joinToString(" ") { "صدر$it طويل ... عجز$it طويل" }

    @Test fun numberedKeepsTwoVersesEachSide() {
        val text = aroundVerses(numbered, "صدر5 طويل", listOf(0..3))
        assertEquals(listOf("…٣ - صدر3 طويل … عجز3 طويل", "٤ - صدر4 طويل … عجز4 طويل", "٥ - صدر5 طويل … عجز5 طويل", "٦ - صدر6 طويل … عجز6 طويل", "٧ - صدر7 طويل … عجز7 طويل…"), text.split("\n"))
    }

    @Test fun runOnVersesSplitBetweenHalves() {
        val text = aroundVerses(runOn, "عجز1 طويل", listOf(0..3))
        assertEquals(listOf("صدر1 طويل ... عجز1 طويل", "صدر2 طويل ... عجز2 طويل", "صدر3 طويل ... عجز3 طويل…"), text.split("\n"))
    }
}
