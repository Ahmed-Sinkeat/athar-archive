package com.atharchive.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnmarkTest {
    @Test
    fun everyMarkBecomesARangeInThePlainText() {
        val (text, ranges) = unmark("…ان <mark>الصبر</mark> علي <mark>الطاعه</mark>…")
        assertEquals("…ان الصبر علي الطاعه…", text)
        assertEquals(listOf("الصبر", "الطاعه"), ranges.map { text.substring(it.first, it.last + 1) })
    }

    @Test
    fun anUnclosedMarkStaysAsText() {
        assertEquals("a <mark>b" to emptyList<IntRange>(), unmark("a <mark>b"))
    }
}
