package com.atharchive.core.data.db.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockTextTest {
    @Test
    fun packedTextComesBackExactlyAndSmaller() {
        // Arabic with tashkeel, a line break, Quranic marks and a pair of guillemets
        val long = "قَالَ اللَّهُ ۖ «العِلْم»\n".repeat(40)
        val rows = listOf(row(0, long), row(1, "قال"), row(2, ""))
        val packed = BlockText.pack(rows)
        assertTrue(packed[0].text.isEmpty() && packed[0].packed!!.size < long.encodeToByteArray().size / 2)
        assertNull(packed[1].packed) // too short to gain anything
        val back = BlockText.unpack(packed)
        assertEquals(rows.map { it.text }, back.map { it.text })
        assertTrue(back.all { it.packed == null })
        assertEquals(long, BlockText.unpack(packed[0].packed!!))
    }

    private fun row(ordinal: Int, text: String) = ContentBlockEntity(
        entityId = "e", ordinal = ordinal, blockIdHi = 0, blockIdLo = ordinal.toLong(), fp64 = 0,
        chapterAnchor = "", type = "p", printedPage = null, vol = null, text = text,
        attrs = ByteArray(0), inlineSpans = ByteArray(0),
    )
}
