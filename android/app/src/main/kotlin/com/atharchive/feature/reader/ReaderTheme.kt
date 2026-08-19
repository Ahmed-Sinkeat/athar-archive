package com.atharchive.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.atharchive.ui.theme.AtharTheme

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
    val highlight: Color,
    val selection: Color,
)

@Composable
fun rememberReaderColors(palette: ReaderPalette): ReaderColors {
    val accent = AtharTheme.colors.accent
    return when (palette) {
        ReaderPalette.White -> ReaderColors(
            page = Color(0xFFFFFFFF),
            text = Color(0xFF1A1A18),
            secondary = Color(0xFF6C6762),
            divider = Color(0xFFE4DFDA),
            accent = accent,
            highlight = Color(0x338F3537),
            selection = Color(0x268F3537),
        )
        ReaderPalette.Warm -> ReaderColors(
            page = Color(0xFFFAF6EE),
            text = Color(0xFF241F19),
            secondary = Color(0xFF6E655A),
            divider = Color(0xFFE2D9CA),
            accent = accent,
            highlight = Color(0x2E8F3537),
            selection = Color(0x248F3537),
        )
        ReaderPalette.Dark -> ReaderColors(
            page = Color(0xFF14120F),
            text = Color(0xFFE9E2D8),
            secondary = Color(0xFF9A9086),
            divider = Color(0xFF322C25),
            accent = Color(0xFFD1787D),
            highlight = Color(0x40D1787D),
            selection = Color(0x33D1787D),
        )
    }
}
