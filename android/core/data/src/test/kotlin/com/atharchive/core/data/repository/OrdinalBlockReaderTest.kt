package com.atharchive.core.data.repository

import com.atharchive.core.data.db.content.ContentBlockEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdinalBlockReaderTest {
    @Test
    fun uncachedJumpUsesSourceOrdinalsAndFetchesEveryMissingFrame() = runBlocking {
        val rows = mutableMapOf<Int, ContentBlockEntity>()
        val fetched = mutableListOf<Int>()
        val reader = OrdinalBlockReader(
            count = { 1000 },
            read = { first, last -> (first..last).mapNotNull(rows::get) },
            fetch = { ordinal ->
                fetched += ordinal
                for (i in ordinal..ordinal + 9) rows[i] = block(i)
            },
        )
        assertEquals((500..519).toList(), reader.range(500, 519).map { it.ordinal })
        assertEquals(listOf(500, 510), fetched)
        assertEquals((490..499).toList(), reader.range(490, 499).map { it.ordinal })
    }

    @Test
    fun unavailableFrameIsAnErrorInsteadOfSkippingText() = runBlocking {
        val reader = OrdinalBlockReader(count = { 100 }, read = { _, _ -> emptyList() }, fetch = {})
        assertTrue(runCatching { reader.range(50, 59) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun frameTraversalAdvancesWithoutCachedHistoryAndStopsEarly() = runBlocking {
        val visited = mutableListOf<IntRange>()
        visitBookFrames(35, frameEnd = { minOf(it + 9, 34) }) {
            visited += it
            visited.size == 3
        }
        assertEquals(listOf(0..9, 10..19, 20..29), visited)
        var failed = false
        try { visitBookFrames(10, frameEnd = { -1 }, visit = { false }) }
        catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
    }

    private fun block(ordinal: Int) = ContentBlockEntity(
        entityId = "book", ordinal = ordinal, blockIdHi = 0, blockIdLo = ordinal.toLong(), fp64 = 0,
        chapterAnchor = "chapter", type = "p", printedPage = null, vol = null,
        text = "نص $ordinal", attrs = byteArrayOf(), inlineSpans = byteArrayOf(),
    )
}
