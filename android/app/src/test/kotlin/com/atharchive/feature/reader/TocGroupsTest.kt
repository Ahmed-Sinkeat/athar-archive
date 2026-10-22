package com.atharchive.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class TocGroupsTest {
    @Test
    fun abwabSitUnderTheirKitabAndAnUntitledOpeningHasNoRow() {
        fun e(a: String, title: String, level: Int) = TocEntry(a, title, level, 0)
        val groups = tocGroups(listOf(e("main", "", 2), e("b0", "مقدمة", 3), e("k1", "كتاب الطهارة", 2), e("b1", "باب المياه", 3), e("b2", "باب الآنية", 3), e("k2", "كتاب الصلاة", 2), e("empty", "", 2)))
        assertEquals(listOf(null, "k1", "k2"), groups.map { it.kitab?.anchor })
        assertEquals(listOf(listOf("b0"), listOf("b1", "b2"), emptyList()), groups.map { g -> g.abwab.map { it.anchor } })
    }
}
