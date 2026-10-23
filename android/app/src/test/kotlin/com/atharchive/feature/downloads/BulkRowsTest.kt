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
            ),
            rows,
        )
    }
}
