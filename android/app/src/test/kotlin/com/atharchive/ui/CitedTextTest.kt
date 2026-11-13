package com.atharchive.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CitedTextTest {
    @Test fun sourceLineWithVolumeAndPage() = assertEquals(
        "نص\n\n— الكتاب، المؤلف، ج٢، ص٧٨\nhttps://x/book/a",
        citedText("نص", "الكتاب", "المؤلف", 2, 78, "https://x/book/a"),
    )

    @Test fun missingPartsAreLeftOut() {
        assertEquals("نص\n\n— الكتاب\nhttps://x", citedText("نص", "الكتاب", "", null, null, "https://x"))
        assertEquals("نص", citedText("نص", "", "", null, null, ""))
    }

    @Test fun poemCitesVerseNumbers() {
        assertEquals("ب\n\n— القصيدة، الشاعر، البيت ١٢\nhttps://x/poem/a",
            citedText("ب", "القصيدة", "الشاعر", null, null, "https://x/poem/a", verses = 12..12))
        assertEquals("ب\n\n— القصيدة، الأبيات ١٢–١٥", citedText("ب", "القصيدة", "", null, null, "", verses = 12..15))
    }

    @Test fun copyAllPutsTheTitleApartAndTheSourceBelow() {
        assertEquals("القصيدة\n\nصدر\nعجز\n\n— القصيدة، الشاعر\nhttps://x",
            wholeText("صدر\nعجز", "القصيدة", "الشاعر", "https://x"))
        assertEquals("نص", wholeText("نص", "", "", ""))
    }
}
