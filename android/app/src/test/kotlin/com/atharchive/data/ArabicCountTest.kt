package com.atharchive.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicCountTest {
    private fun books(n: Int) = arabicCount(n, "الكتب", "كتاب واحد", "كتابين", "كتب", "كتابًا")

    @Test
    fun nounAgreesWithNumber() {
        assertEquals("الكتب", books(0))
        assertEquals("كتاب واحد", books(1))
        assertEquals("كتابين", books(2))
        assertEquals("٣ كتب", books(3))
        assertEquals("١٠ كتب", books(10))
        assertEquals("١١ كتابًا", books(11))
        assertEquals("٩٩ كتابًا", books(99))
        assertEquals("١٠٠ كتاب", books(100))
        assertEquals("١٠٢ كتاب", books(102))
        assertEquals("١٠٣ كتب", books(103))
        assertEquals("١١٩٥ كتابًا", books(1195))
    }
}
