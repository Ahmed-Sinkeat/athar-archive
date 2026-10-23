package com.atharchive.ui.theme

import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.scale

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

// One palette set, shared by the app and the reader (the reader's fixed palettes are built
// from these same values): فاتح is the reader's white page, دافئ its cream page, داكن its night.
private val Paper = Color(0xFFFFFFFF)
private val PaperRaised = Color(0xFFF6F4F1)
private val PaperPressed = Color(0xFFEEEAE5)
internal val LightInk = Color(0xFF1A1A18)
private val InkSecondary = Color(0xFF6C6762)
private val Burgundy = Color(0xFF8F3537)
private val BurgundyPressed = Color(0xFF71272A)
private val WarmDivider = Color(0xFFE4DFDA)
private val PaperGlass = Color(0xF2FFFFFF)
private val PaperGlassBorder = Color(0xB8DAD4CE)

private val Cream = Color(0xFFFAF6EE)
private val CreamRaised = Color(0xFFF3EDE0)
private val CreamPressed = Color(0xFFEAE2D2)
internal val CreamInk = Color(0xFF241F19)
private val CreamInkSecondary = Color(0xFF6E655A)
private val CreamDivider = Color(0xFFE2D9CA)
private val CreamGlass = Color(0xF2FAF6EE)
private val CreamGlassBorder = Color(0xB8D9CFBC)
private val PaperScrim = Color(0x660F0D0B)
private val Success = Color(0xFF5E6B45)       // muted olive, not a bright green

// Warm near-black, never #000. Surfaces separate by a few points of luminance
// rather than by shadow, so depth reads as tone and not as Material elevation.
private val NightPaper = Color(0xFF11100F)
private val NightRaised = Color(0xFF1D1A18)
private val NightPressed = Color(0xFF24211E)
internal val NightInk = Color(0xFFF1EDEA)
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

/** Prose in the book reader — the font Turath uses (SIL OFL). Poems and chrome keep Amiri. */
val KitabFontFamily = FontFamily(
    Font(R.font.kitab_regular, FontWeight.Normal),
    Font(R.font.kitab_bold, FontWeight.Bold),
)

/** ﴿…﴾ ranges in the reader (SIL OFL, from the Amiri project). */
val QuranFontFamily = FontFamily(Font(R.font.amiri_quran, FontWeight.Normal))

/** Every corner in the app; pills and dots use CircleShape. */
object AtharCorners {
    /** Marks inside text, small badges. */
    val Tag = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    /** Chips, toggles, small buttons and fields. */
    val Control = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    /** Rows, cards and panels. */
    val Card = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    /** Menus, dialogs and sheets. */
    val Surface = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    /** The player's cover. */
    val Artwork = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
}

/** Text sizes used outside [MaterialTheme.typography]; the reader's text follows its own setting. */
object AtharTextSize {
    val Caption = 11.sp
    val Small = 12.sp
    val Body = 14.sp
    val Ui = 15.sp
    val Title = 17.sp
    val Large = 20.sp
    val Headline = 22.sp
    val Hero = 26.sp
}

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

/**
 * What the reader chose, not what the system did.
 *
 * [System] follows the device; the other two override it, so a reader who forces the app
 * light gets a light app at night — including inside a book.
 */
enum class ThemeMode(val label: String) {
    System("نظام الجهاز"),
    Light("فاتح"),
    Warm("دافئ"),
    Dark("داكن"),
    ;

    /** Whether the resolved theme is dark. [Warm] is a light theme. */
    @Composable
    fun resolve(): Boolean = when (this) {
        System -> isSystemInDarkTheme()
        Light, Warm -> false
        Dark -> true
    }
}

/**
 * The app's resolved theme, for surfaces that paint their own colours.
 *
 * The reader keeps a palette of its own, and it has to be able to ask what the app
 * settled on without asking the system a second question and disagreeing with it.
 */
val LocalAtharDarkTheme = staticCompositionLocalOf { false }

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

internal val LightExtraColors = AtharExtraColors(
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

internal val WarmExtraColors = LightExtraColors.copy(
    canvas = Cream,
    raisedSurface = CreamRaised,
    pressedSurface = CreamPressed,
    divider = CreamDivider,
    secondaryText = CreamInkSecondary,
    glassSurface = CreamGlass,
    glassBorder = CreamGlassBorder,
    inputSurface = CreamRaised,
    inputBorder = Color(0x73E2D9CA),
    placeholderText = CreamInkSecondary,
)

internal val DarkExtraColors = AtharExtraColors(
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
    /** The cream light theme; ignored when [darkTheme] is true. */
    warm: Boolean = false,
    content: @Composable () -> Unit,
) {
    val warmLight = warm && !darkTheme
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
            background = if (warmLight) Cream else Paper,
            onBackground = if (warmLight) CreamInk else LightInk,
            surface = if (warmLight) Cream else Paper,
            onSurface = if (warmLight) CreamInk else LightInk,
            surfaceVariant = if (warmLight) CreamRaised else PaperRaised,
            onSurfaceVariant = if (warmLight) CreamInkSecondary else InkSecondary,
            outline = if (warmLight) CreamDivider else WarmDivider,
            outlineVariant = if (warmLight) CreamDivider else WarmDivider,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAtharExtraColors provides when {
            darkTheme -> DarkExtraColors
            warmLight -> WarmExtraColors
            else -> LightExtraColors
        },
        LocalAtharDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AtharTypography,
            // Dialogs and sheets use extraLarge (28dp by default); 14dp matches AtharMenu's
            // card so every floating surface has the same corner.
            shapes = androidx.compose.material3.Shapes(
                extraLarge = AtharCorners.Surface,
            ),
        ) {
            // No ripple anywhere: the dark translucent slab read as a smudge on the cream.
            // A press is a small squish instead; selectors keep their soap pill.
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.LocalIndication provides PressSquish,
                androidx.compose.material3.LocalRippleConfiguration provides null,
                content = content,
            )
        }
    }
}

/** Scales the pressed element to 0.97 and springs it back; draws nothing over it. */
private object PressSquish : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        PressSquishNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7
}

private class PressSquishNode(
    private val source: androidx.compose.foundation.interaction.InteractionSource,
) : androidx.compose.ui.Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
    private val squish = androidx.compose.animation.core.Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = 0
            source.interactions.collect {
                when (it) {
                    is androidx.compose.foundation.interaction.PressInteraction.Press -> pressed++
                    is androidx.compose.foundation.interaction.PressInteraction.Release,
                    is androidx.compose.foundation.interaction.PressInteraction.Cancel -> pressed--
                    else -> return@collect
                }
                launch {
                    squish.animateTo(
                        if (pressed > 0) 0.97f else 1f,
                        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 700f),
                    )
                }
            }
        }
    }

    override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
        val k = squish.value
        scale(k) { this@draw.drawContent() }
    }
}
