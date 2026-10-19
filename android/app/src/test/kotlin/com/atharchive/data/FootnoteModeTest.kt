package com.atharchive.data

import com.atharchive.feature.reader.FootnoteMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FootnoteModeTest {
    @Test fun legacyAndStoredValuesMap() {
        assertEquals(FootnoteMode.Footer, footnoteModeOf(stored = null, legacyShow = null))
        assertEquals(FootnoteMode.Footer, footnoteModeOf(stored = null, legacyShow = true))
        assertEquals(FootnoteMode.Tap, footnoteModeOf(stored = null, legacyShow = false))
        assertEquals(FootnoteMode.Tap, footnoteModeOf(stored = "Tap", legacyShow = true))
        assertEquals(FootnoteMode.Footer, footnoteModeOf(stored = "Bogus", legacyShow = null))
    }
}
