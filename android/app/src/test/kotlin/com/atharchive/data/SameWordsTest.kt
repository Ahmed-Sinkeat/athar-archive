package com.atharchive.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameWordsTest {
    @Test
    fun aSentenceOverAPageSavesHoweverItsPartsAreJoined() {
        assertTrue(sameWords("قال رسول\n\nالله", "قال رسول الله"))
        assertTrue(sameWords("صدر\n\nعجز", "صدر … عجز"))
    }

    @Test
    fun changedWordsStillStopTheSave() {
        assertFalse(sameWords("قال رسول الله", "قال رسول"))
    }
}
