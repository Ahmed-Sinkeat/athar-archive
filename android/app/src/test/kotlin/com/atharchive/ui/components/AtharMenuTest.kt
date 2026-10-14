package com.atharchive.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class AtharMenuTest {
    private val window = IntSize(1080, 2400)
    private val menu = IntSize(600, 500)

    @Test fun rtlHangsBelowFromTheAnchorsStartEdge() =
        assertEquals(IntOffset(100, 210), atharMenuOffset(IntRect(100, 100, 200, 200), window, LayoutDirection.Rtl, menu, 10, 20))

    @Test fun flipsAboveNearTheBottom() =
        assertEquals(IntOffset(100, 1690), atharMenuOffset(IntRect(100, 2200, 200, 2300), window, LayoutDirection.Rtl, menu, 10, 20))

    @Test fun ltrIsClampedOnScreen() =
        assertEquals(IntOffset(20, 210), atharMenuOffset(IntRect(0, 100, 100, 200), window, LayoutDirection.Ltr, menu, 10, 20))
}
