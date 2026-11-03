package com.atharchive.core.data.repository

import com.atharchive.core.ArNormalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactLettersTest {
    @Test
    fun keepsHamzaAndTaMarbutaDropsTashkeelAndStaysAlignedWithNormalized() {
        val source = "قَالَ أَبُو هُرَيْرَةَ: إِنَّمَا"
        val letters = exactLetters(source)
        assertTrue(letters.contains("أبو هريرة"))
        assertFalse(letters.contains("ابو هريره"))
        assertTrue(letters.contains("إنما"))
        // Offsets found in the exact letters must index the normalized text, which maps back to the source.
        assertEquals(ArNormalize.normalizeWithMap(source).text.length, letters.length)
    }

    @Test
    fun typedTashkeelMustMatchBareLettersTakeAny() {
        val text = "طَلَبُ العِلْمِ وَرَفْعُ العَلَمِ"
        val kasra = exactMatch(text, "العِلم")!!
        assertEquals("العلم", exactLetters(text).substring(kasra.first, kasra.last + 1))
        assertEquals(exactLetters(text).indexOf("العلم"), kasra.first)
        assertEquals(exactLetters(text).lastIndexOf("العلم"), exactMatch(text, "العَلَم")!!.first)
        assertEquals(exactLetters(text).indexOf("العلم"), exactMatch(text, "العلم")!!.first)
        assertEquals(null, exactMatch(text, "العُلم"))
    }
}
