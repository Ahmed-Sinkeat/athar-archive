package com.atharchive.feature.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWindowTest {
    @Test
    fun opensOnOneChunkDropsFarOnesKeepsIdentityAndNeverShowsAGap() = runBlocking {
        val loads = mutableListOf<IntRange>()
        var failAt: Int? = null
        var edition = "a"
        val window = ReaderWindow(this, total = { 1000 }, dispatcher = Dispatchers.Unconfined, load = { range ->
            loads += range
            check(failAt?.let { it !in range } ?: true) { "offline" }
            range.map { ReaderBlock.Paragraph("b$it", "$edition$it", ordinal = it) }
        })

        window.start(510)
        assertTrue(window.ready)
        assertEquals(1000, window.count)
        assertEquals(listOf(500..549), loads) // the count and the opening chunk, nothing from the start of the book

        val first = window[510]
        assertNotNull(first)
        assertEquals(listOf(500..549, 550..599, 450..499), loads) // neighbours follow the one being read

        window[2000.coerceAtMost(999)] // jump to the end
        assertNull("far chunks are dropped", window.peek(510))
        assertTrue(window.held().all { it.ordinal!! >= 800 })

        window[505]
        val again = window[505]
        edition = "a" // unchanged content: invalidating keeps the very same objects
        window.invalidate()
        assertSame(again, window.peek(505))
        edition = "b"
        window.invalidate()
        assertEquals("b505", (window.peek(505) as ReaderBlock.Paragraph).text)

        failAt = 300
        assertNull(window[300])
        assertEquals("offline", window.error)
        assertNull("a failed chunk is never shown partly", window.peek(300))
        failAt = null
        window.retry()
        assertEquals("b300", (window.peek(300) as ReaderBlock.Paragraph).text)
    }
}
