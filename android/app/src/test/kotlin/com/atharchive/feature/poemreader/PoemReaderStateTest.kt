package com.atharchive.feature.poemreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoemReaderStateTest {
    private fun state(last: Int, complete: Boolean = false) = PoemReaderUiState(
        title = "Poem", poet = "Author", recordings = emptyList(), bodyLoaded = complete,
        verses = (0..last).map { VerseUi("verse-$it", it + 1, "First", "Second", ordinal = it) },
    )

    @Test fun aLoadedPrefixDoesNotPretendToContainALaterTarget() {
        assertNull(state(249).restoreIndex(500))
        assertEquals(500, state(749, complete = true).restoreIndex(500))
    }

    @Test fun movedFrontMatterWaitsForTheStableReadingPosition() {
        val partial = state(249).copy(readingPositionReady = false)
        assertNull(partial.restoreIndex(120))
        assertEquals(42, partial.copy(readingPositionReady = true).restoreIndex(42))
    }

    @Test fun anOldTargetBeyondTheNewEndFallsBackOnlyAfterLoadingFinishes() {
        assertNull(state(99).restoreIndex(200))
        assertEquals(99, state(99, complete = true).restoreIndex(200))
    }
}
