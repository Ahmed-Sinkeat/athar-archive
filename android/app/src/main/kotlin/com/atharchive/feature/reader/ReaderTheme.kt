package com.atharchive.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import com.atharchive.ui.theme.AtharTheme
import com.atharchive.ui.theme.AtharExtraColors
import com.atharchive.ui.theme.CreamInk
import com.atharchive.ui.theme.DarkExtraColors
import com.atharchive.ui.theme.LightExtraColors
import com.atharchive.ui.theme.LightInk
import com.atharchive.ui.theme.NightInk
import com.atharchive.ui.theme.WarmExtraColors

/**
 * Reading follows the app exactly. Explicit page palettes remain available for readers
 * who choose a different page appearance.
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
    /** Text on [accentFill] — selected segments. White on burgundy is 7.7:1 in every palette. */
    val onAccent: Color,
    val highlight: Color,
    val selection: Color,
    /**
     * Text kept in the notebook. A different hue from [selection] (burgundy), so what is
     * saved never reads as a selection still in progress.
     */
    val saved: Color,
    /** ﴿…﴾ and «…» in the text: the site's gold and blue, lighter at night. */
    val ayah: Color = PaperAyah,
    val quote: Color = PaperQuote,
)

// Saved text: amber on paper, a lighter amber at night. Burgundy stays the selection.
private val PaperSaved = Color(0x66D9A73A)
private val NightSaved = Color(0x52E3B955)
private val PaperAyah = Color(0xFF8E6A1C)
private val NightAyah = Color(0xFFD4A72C)
private val PaperQuote = Color(0xFF2A4C8C)
private val NightQuote = Color(0xFF7FA6E8)

@Composable
fun rememberReaderColors(palette: ReaderPalette): ReaderColors {
    return when (palette) {
        ReaderPalette.Follow -> followedReaderColors(AtharTheme.colors, MaterialTheme.colorScheme.onBackground)
        // The same palettes the app uses, so a fixed reader page and the app can never drift.
        ReaderPalette.White -> followedReaderColors(LightExtraColors, LightInk)
        ReaderPalette.Warm -> followedReaderColors(WarmExtraColors, CreamInk)
        ReaderPalette.Dark -> followedReaderColors(DarkExtraColors, NightInk)
    }
}

internal fun followedReaderColors(app: AtharExtraColors, text: Color) = ReaderColors(
    page = app.canvas, text = text, secondary = app.secondaryText, divider = app.divider,
    accent = app.accent, accentFill = app.primaryAction, onAccent = app.onPrimaryAction,
    highlight = app.accent.copy(alpha = 0.20f), selection = app.accent.copy(alpha = 0.32f),
    saved = if (app.canvas.luminance() < 0.5f) NightSaved else PaperSaved,
    ayah = if (app.canvas.luminance() < 0.5f) NightAyah else PaperAyah,
    quote = if (app.canvas.luminance() < 0.5f) NightQuote else PaperQuote,
)
