package com.atharchive.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidenedPoemRangeTest {
    @Test fun widensBothSidesNotBelowZero() {
        assertEquals("84-216", widenedPoemRange("100-200"))
        assertEquals("0-21", widenedPoemRange("3-5"))
        assertNull(widenedPoemRange("x"))
    }
}
