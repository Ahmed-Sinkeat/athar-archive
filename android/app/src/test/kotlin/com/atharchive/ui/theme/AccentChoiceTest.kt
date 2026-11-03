package com.atharchive.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentChoiceTest {
    @Test fun launcherAliasFollowsIconChoice() {
        assertEquals("Burgundy", AccentChoice(AtharAccent.Burgundy, originalIcon = true).launcher)
        assertEquals("Red", AccentChoice(AtharAccent.Burgundy, originalIcon = false).launcher)
        assertEquals("Green", AccentChoice(AtharAccent.Green, originalIcon = false).launcher)
        // a custom colour has no icon of its own
        assertEquals("Burgundy", AccentChoice(AtharAccent.Custom, custom = 0xFFFFEE00.toInt(), originalIcon = false).launcher)
    }

    @Test fun customColourIsDarkenedUntilWhiteTextReads() {
        // pale yellow: white on it would be unreadable; contrast (1.05)/(L+0.05) must reach 4.5
        val base = AccentChoice(AtharAccent.Custom, custom = 0xFFFFEE00.toInt()).tones.base
        assertTrue(1.05f / (base.luminance() + 0.05f) >= 4.5f)
        // an already dark colour is kept as picked
        assertEquals(Color(0xFF1B3858), customTones(Color(0xFF1B3858)).base)
    }
}
