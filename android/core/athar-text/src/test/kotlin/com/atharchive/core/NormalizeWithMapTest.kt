package com.atharchive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeWithMapTest {
    @Test
    fun `match absorbs vocalisation and Quranic marks`() {
        val source = "الصَّلَاةِۖ نور"
        val normalized = ArNormalize.normalizeWithMap(source)
        val start = normalized.text.indexOf("الصلاه")
        val range = normalized.sourceRange(start, start + "الصلاه".length)

        assertEquals("الصلاه نور", normalized.text)
        assertEquals("الصَّلَاةِۖ", source.substring(range))
    }

    @Test
    fun `leading deleted marks attach to following character`() {
        val source = "َِأَمَانَةٌ"
        val normalized = ArNormalize.normalizeWithMap(source)
        val range = normalized.sourceRange(0, 1)

        assertEquals("امانه", normalized.text)
        assertEquals("َِأَ", source.substring(range))
    }

    @Test
    fun `decomposed hamza maps back across both source units`() {
        val source = "ا\u0654مَان"
        val normalized = ArNormalize.normalizeWithMap(source)
        val range = normalized.sourceRange(0, 1)

        assertEquals("امان", normalized.text)
        assertEquals("ا\u0654", source.substring(range))
    }

    @Test
    fun `tatweel and superscript alef are absorbed`() {
        val source = "هٰـذا"
        val normalized = ArNormalize.normalizeWithMap(source)
        val range = normalized.sourceRange(0, 1)

        assertEquals("هذا", normalized.text)
        assertEquals("هٰـ", source.substring(range))
    }

    @Test
    fun `empty and entirely deleted inputs stay empty`() {
        assertEquals("", ArNormalize.normalizeWithMap("").text)
        val deleted = ArNormalize.normalizeWithMap("َِۖـ")
        assertEquals("", deleted.text)
        assertTrue(deleted.sourceRange(0, 0).isEmpty())
    }
}
