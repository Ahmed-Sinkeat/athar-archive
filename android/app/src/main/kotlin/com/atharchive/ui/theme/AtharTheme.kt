package com.atharchive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.atharchive.R

private val Paper = Color(0xFFFAF9F6)
private val PaperRaised = Color(0xFFF4F0EC)
private val PaperPressed = Color(0xFFECE6E1)
private val Ink = Color(0xFF241E19)          // warm charcoal, never pure black
private val InkSecondary = Color(0xFF7B6B5C)  // warm taupe, replaces the cold grey
private val Burgundy = Color(0xFF8F3537)
private val BurgundyPressed = Color(0xFF71272A)
private val WarmDivider = Color(0xFFD8D2CC)
private val PaperGlass = Color(0xF2FAF9F6)
private val PaperGlassBorder = Color(0xB8CFC7C0)
private val PaperScrim = Color(0x660F0D0B)
private val Success = Color(0xFF5E6B45)       // muted olive, not a bright green

// Warm near-black, never #000. Surfaces separate by a few points of luminance
// rather than by shadow, so depth reads as tone and not as Material elevation.
private val NightPaper = Color(0xFF11100F)
private val NightRaised = Color(0xFF1D1A18)
private val NightPressed = Color(0xFF24211E)
private val NightInk = Color(0xFFF1EDEA)
private val NightInkSecondary = Color(0xFFB8B0AA)
// The bright accent stays for small marks — icons, labels, indicators, progress.
private val NightBurgundy = Color(0xFFD1787D)
private val NightBurgundyPressed = Color(0xFFE1979B)
private val NightDivider = Color(0xFF292624)
private val NightInputSurface = Color(0xFF191715)
private val NightInputBorder = Color(0xFF302C29)
private val NightPlaceholder = Color(0xFF99918C)
// Large filled areas — the selected nav ground, tile actives — take a deeper, less
// saturated burgundy so they sit under the accent rather than competing with it.
private val NightAccentSurface = Color(0x4D8F3537)
private val NightGlass = Color(0xF2141211)
private val NightGlassBorder = Color(0xB8332F2C)
private val NightScrim = Color(0x99000000)
private val NightSuccess = Color(0xFFA3AE81)  // muted olive, dark theme

val AtharUiFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.Bold),
)

val AtharEditorialFontFamily = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

private val AtharTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = AtharEditorialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 46.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AtharEditorialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 35.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AtharEditorialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AtharUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AtharUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AtharUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 27.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AtharUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AtharUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AtharUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AtharUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AtharUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

@Immutable
data class AtharExtraColors(
    val canvas: Color,
    val raisedSurface: Color,
    val pressedSurface: Color,
    val divider: Color,
    val accent: Color,
    val accentPressed: Color,
    val primaryAction: Color,
    val onPrimaryAction: Color,
    val secondaryText: Color,
    /** Near-opaque chrome. Content surfaces remain solid paper. */
    val glassSurface: Color,
    val glassBorder: Color,
    val scrim: Color,
    val success: Color,
    /** Search and text-entry ground; sits nearly flush with [canvas]. */
    val inputSurface: Color,
    val inputBorder: Color,
    val placeholderText: Color,
    /** Ground for large accent fills. Deeper than [accent] on purpose. */
    val accentSurface: Color,
)

private val LightExtraColors = AtharExtraColors(
    canvas = Paper,
    raisedSurface = PaperRaised,
    pressedSurface = PaperPressed,
    divider = WarmDivider,
    accent = Burgundy,
    accentPressed = BurgundyPressed,
    primaryAction = Burgundy,
    onPrimaryAction = Color.White,
    secondaryText = InkSecondary,
    glassSurface = PaperGlass,
    glassBorder = PaperGlassBorder,
    scrim = PaperScrim,
    success = Success,
    inputSurface = PaperRaised,
    inputBorder = Color(0x73D8D2CC),
    placeholderText = InkSecondary,
    accentSurface = Color(0x1F8F3537),
)

private val DarkExtraColors = AtharExtraColors(
    canvas = NightPaper,
    raisedSurface = NightRaised,
    pressedSurface = NightPressed,
    divider = NightDivider,
    accent = NightBurgundy,
    accentPressed = NightBurgundyPressed,
    primaryAction = Burgundy,
    onPrimaryAction = Color.White,
    secondaryText = NightInkSecondary,
    glassSurface = NightGlass,
    glassBorder = NightGlassBorder,
    scrim = NightScrim,
    success = NightSuccess,
    inputSurface = NightInputSurface,
    inputBorder = NightInputBorder,
    placeholderText = NightPlaceholder,
    accentSurface = NightAccentSurface,
)

private val LocalAtharExtraColors = staticCompositionLocalOf { LightExtraColors }

object AtharTheme {
    val colors: AtharExtraColors
        @Composable get() = LocalAtharExtraColors.current
}

@Composable
fun AtharTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = NightBurgundy,
            onPrimary = NightPaper,
            primaryContainer = Color(0xFF54292B),
            onPrimaryContainer = NightInk,
            background = NightPaper,
            onBackground = NightInk,
            surface = NightPaper,
            onSurface = NightInk,
            surfaceVariant = NightRaised,
            onSurfaceVariant = NightInkSecondary,
            outline = NightDivider,
            outlineVariant = NightDivider,
        )
    } else {
        lightColorScheme(
            primary = Burgundy,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF1DFDF),
            onPrimaryContainer = Color(0xFF441517),
            background = Paper,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            surfaceVariant = PaperRaised,
            onSurfaceVariant = InkSecondary,
            outline = WarmDivider,
            outlineVariant = WarmDivider,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAtharExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AtharTypography,
            content = content,
        )
    }
}
