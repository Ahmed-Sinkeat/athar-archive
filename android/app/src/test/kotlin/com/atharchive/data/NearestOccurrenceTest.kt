package com.atharchive.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NearestOccurrenceTest {
    private val text = "قال العلم نور وقال غيره كلاما طويلا هنا " + "حشو ".repeat(60) + "ثم ذكر العلم في باب الصلاة والزكاة"

    @Test fun picksTheOccurrenceTheSnippetSurrounds() {
        val first = text.indexOf("العلم")
        val second = text.lastIndexOf("العلم")
        assertEquals(second to second + 5, nearestOccurrence(text, first, first + 5, setOf("باب", "الصلاة", "والزكاة")))
    }

    @Test fun keepsTheFirstWithoutABetterOne() {
        val first = text.indexOf("العلم")
        assertEquals(first to first + 5, nearestOccurrence(text, first, first + 5, setOf("نور")))
        assertEquals(first to first + 5, nearestOccurrence(text, first, first + 5, emptySet()))
    }

    @Test fun mapsBackThroughTashkeel() {
        val voweled = "العِلْمُ نور " + "حشو ".repeat(60) + "باب العِلْمِ والعمل"
        val at = voweled.indexOf("العِلْمُ")
        val (start, end) = nearestOccurrence(voweled, at, at + "العِلْمُ".length, setOf("والعمل"))
        assertEquals("العِلْمِ", voweled.substring(start, end))
    }
}
