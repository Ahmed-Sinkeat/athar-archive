package com.atharchive.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class WithoutTashkeelTest {
    @Test fun marksDroppedHighlightStaysOnSameWord() {
        val text = "قَالَ أَبُو هُرَيْرَةَ"
        val start = text.indexOf("هُرَيْرَةَ")
        val (shown, ranges) = withoutTashkeel(text, listOf(start until text.length))
        assertEquals("قال أبو هريرة", shown)
        assertEquals("هريرة", shown.substring(ranges.single().first, ranges.single().last + 1))
    }
}
