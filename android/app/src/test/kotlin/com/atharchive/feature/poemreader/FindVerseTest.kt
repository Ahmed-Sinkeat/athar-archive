package com.atharchive.feature.poemreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindVerseTest {
    private val verses = listOf(
        VerseUi("v1", 1, "لَا يَصْحَبُ الْبِدْعِيَّ إِلَّا مِثْلُهُ", "يَدْعُو إِلَى النِّيرَانِ"),
        VerseUi("v2", 2, "لَا يَصْحَبُ الْبِدْعِيَّ إِلَّا مِثْلُهُ", "تَحْتَ الدُّخَانِ تَأَجُّجُ النِّيرَانِ"),
    )

    @Test fun phraseBeatsScatteredWords() {
        // verse 1 has النيران too; the typed phrase (no tashkeel, as typed) is in verse 2
        assertEquals("v2", findVerse(verses, listOf("تحت", "الدخان", "تاجج", "النيران"))?.id)
    }

    @Test fun phraseAcrossHemistichs() {
        assertEquals("v2", findVerse(verses, listOf("مثله", "تحت"))?.id)
    }

    @Test fun noMatch() {
        assertNull(findVerse(verses, listOf("الصبر")))
    }
}
