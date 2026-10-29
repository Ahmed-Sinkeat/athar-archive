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
