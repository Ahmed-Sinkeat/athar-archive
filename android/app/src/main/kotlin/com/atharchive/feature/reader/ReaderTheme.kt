package com.atharchive.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.atharchive.ui.theme.LocalAtharDarkTheme

/**
 * Reading appearance is deliberately independent of the app's light/dark theme: a reader
 * may want a warm page at night without recolouring the rest of the app.
 */
@Immutable
data class ReaderColors(
    val page: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
    val accent: Color,
    /** Large filled areas — play discs. Deeper than [accent] in the dark palette. */
    val accentFill: Color,
    val highlight: Color,
    val selection: Color,
)

/**
 * Burgundy is a property of the page, not of the app.
 *
 * This used to read `AtharTheme.colors.accent`, so a cream page under a dark app got the
 * *night* accent — pale pink chapter headings on paper. Each palette owns all of its
 * colours or none of them.
 */
private val PaperAccent = Color(0xFF8F3537)
private val NightAccent = Color(0xFFD1787D)

@Composable
fun rememberReaderColors(palette: ReaderPalette): ReaderColors {
    // Follow the app, not the system: a reader who forced the app light gets a light page
    // at night, which is the whole point of having forced it.
    val resolved = when (palette) {
        ReaderPalette.Follow ->
            if (LocalAtharDarkTheme.current) ReaderPalette.Dark else ReaderPalette.Warm
        else -> palette
    }
    return when (resolved) {
        ReaderPalette.White -> ReaderColors(
            page = Color(0xFFFFFFFF),
            text = Color(0xFF1A1A18),
            secondary = Color(0xFF6C6762),
            divider = Color(0xFFE4DFDA),
            accent = PaperAccent,
            accentFill = PaperAccent,
            highlight = Color(0x338F3537),
            selection = Color(0x268F3537),
        )
        // Follow can no longer reach here, but the branch keeps the when exhaustive
        // without an else that would silently swallow a future palette.
        ReaderPalette.Follow, ReaderPalette.Warm -> ReaderColors(
            page = Color(0xFFFAF6EE),
            text = Color(0xFF241F19),
            secondary = Color(0xFF6E655A),
            divider = Color(0xFFE2D9CA),
            accent = PaperAccent,
            accentFill = PaperAccent,
            highlight = Color(0x2E8F3537),
            selection = Color(0x248F3537),
        )
        ReaderPalette.Dark -> ReaderColors(
            page = Color(0xFF11100F),
            text = Color(0xFFF1EDEA),
            secondary = Color(0xFFB8B0AA),
            divider = Color(0xFF292624),
            accent = NightAccent,
            accentFill = Color(0xFF8F3537),
            highlight = Color(0x40D1787D),
            selection = Color(0x33D1787D),
        )
    }
}
