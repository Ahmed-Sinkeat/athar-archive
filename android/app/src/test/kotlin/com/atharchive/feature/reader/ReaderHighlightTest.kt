package com.atharchive.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHighlightTest {
    @Test
    fun containsRespectsBothEndsAcrossBlocks() {
        val h = ReaderHighlight(startOrdinal = 3, endOrdinal = 5, startOffset = 10, endOffset = 4, id = "a")
        assertFalse(h.contains(3, 9))
        assertTrue(h.contains(3, 10))
        assertTrue(h.contains(4, 0)) // a middle block is wholly inside
        assertTrue(h.contains(5, 3))
        assertFalse(h.contains(5, 4)) // end is exclusive
        assertFalse(h.contains(2, 50))
        assertFalse(h.contains(6, 0))
    }

    @Test
    fun singleBlockHighlight() {
        val h = ReaderHighlight(7, 7, 2, 6)
        assertFalse(h.contains(7, 1))
        assertTrue(h.contains(7, 2))
        assertTrue(h.contains(7, 5))
        assertFalse(h.contains(7, 6))
    }
}
