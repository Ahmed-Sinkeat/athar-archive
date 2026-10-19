package com.atharchive.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistedChoiceTest {
    private enum class Sort { Newest, Title }

    @Test fun staleStoredValuesFallBack() {
        assertEquals(Sort.Title, enumValueOrNull<Sort>("Title"))
        assertNull(enumValueOrNull<Sort>("Author")) // a sort name from an older build
        assertEquals("كل القرون", "القرن 99".orFirstIn(listOf("كل القرون", "القرن 5")))
        assertEquals("القرن 5", "القرن 5".orFirstIn(listOf("كل القرون", "القرن 5")))
    }
}
