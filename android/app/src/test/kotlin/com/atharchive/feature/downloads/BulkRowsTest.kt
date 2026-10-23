package com.atharchive.feature.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class BulkRowsTest {
    @Test
    fun sizesSumPerRowAndSharedRecordingsCountOnce() {
        val rows = bulkRows(
            listOf(
                BulkCandidate("book", audio = false, id = "b1", bytes = 100),
                BulkCandidate("book", audio = false, id = "b2", bytes = 50),
                BulkCandidate("book", audio = true, id = "a1", bytes = 7),
                BulkCandidate("poem", audio = false, id = "p1", bytes = 3),
                // The same recording attached to a book and a poem is one file.
                BulkCandidate("poem", audio = true, id = "a1", bytes = 7),
                BulkCandidate("question", audio = false, id = "q1", bytes = 1),
            ),
        ).associate { it.label to it.bytes }

        assertEquals(
            mapOf(
                "المكتبة كاملة" to 161L,
                "المكتبة كاملة · نصوص" to 154L,
                "المكتبة كاملة · صوت" to 7L,
                "الكتب · نصوص" to 150L,
                "الكتب · صوت" to 7L,
                "القصائد · نصوص" to 3L,
                "القصائد · صوت" to 7L,
                "المسائل · نصوص" to 1L,
            ),
            rows,
        )
    }

    @Test
    fun progressCountsFinishedAndInFlightBytes() {
        val row = bulkRows(
            listOf(
                BulkCandidate("book", audio = false, id = "b1", bytes = 100, state = BulkState.Done),
                BulkCandidate("book", audio = false, id = "b2", bytes = 100, state = BulkState.Active, partialBytes = 50),
                BulkCandidate("book", audio = false, id = "b3", bytes = 200, state = BulkState.Paused, partialBytes = 10),
                BulkCandidate("book", audio = false, id = "b4", bytes = 100),
            ),
        ).first { it.label == "الكتب · نصوص" }
        assertEquals(1, row.done)
        assertEquals(160L, row.doneBytes)
        assertEquals(0.32f, row.progress, 0.001f)
        assertEquals(listOf("b4"), row.idle.map { it.id })
        assertEquals(listOf("b2"), row.running.map { it.id })
        assertEquals(listOf("b3"), row.stopped.map { it.id })
        assertEquals(true, row.busy)
        assertEquals(false, row.complete)
    }

    @Test
    fun onlyTheStartedRowIsTracked() {
        val rows = bulkRows(
            listOf(
                BulkCandidate("book", audio = false, id = "b1", bytes = 1, state = BulkState.Active),
                BulkCandidate("book", audio = true, id = "a1", bytes = 1),
            ),
            tracked = setOf("الكتب · نصوص"),
        )
        assertEquals(listOf("الكتب · نصوص"), rows.filter { it.tracked }.map { it.label })
    }
}
