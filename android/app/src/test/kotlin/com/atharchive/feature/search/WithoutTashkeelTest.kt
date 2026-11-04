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

    // «المزيد» in the reader's search: the passage centres on this hit's own snippet and paints the words it matched.
    @Test fun expandedPassageKeepsTheHitAndItsWords() {
        val excerpt = "…باب ما جاء في كتب العلم…"
        val word = excerpt.indexOf("كتب")
        val full = "مقدمة " + "ح".repeat(1200) + " باب ما جاء في كتب العلم وغيره"
        val shown = around(full, excerpt, listOf(word until word + 3, 900 until 905))
        val ranges = wordRanges(shown, excerpt, listOf(word until word + 3, 900 until 905))
        assertEquals(listOf("كتب"), ranges.map { shown.substring(it.first, it.last + 1) })
    }
}
