package com.atharchive.ui.theme

import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.scale

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

// One palette set, shared by the app and the reader: the reader always paints the app's theme.
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

/**
 * «خطّ القراءة»: the face of a book's prose and a poem's verses. [Custom] is a font file the reader
 * imported (Appearance), kept as `files/fonts/user.ttf`; until one exists it falls back to [Kitab].
 */
enum class ReaderFont(val label: String) {
    Kitab("كتاب"), Amiri("أميري"), Plex("بلكس"), Custom("خطّي");

    companion object {
        fun userFile(context: android.content.Context) = java.io.File(context.filesDir, "fonts/user.ttf")
    }
}

/** The reading face now chosen; provided once at the root (MainActivity). */
val LocalReaderFont = staticCompositionLocalOf { KitabFontFamily }

/** [stamp] changes on every import, so a replaced file is read again rather than served from the old family. */
@Composable
fun ReaderFont.family(stamp: Long): FontFamily {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(this, stamp) {
        when (this) {
            ReaderFont.Kitab -> KitabFontFamily
            ReaderFont.Amiri -> AtharEditorialFontFamily
            ReaderFont.Plex -> AtharUiFontFamily
            ReaderFont.Custom -> ReaderFont.userFile(context).takeIf { it.isFile }
                ?.let { FontFamily(androidx.compose.ui.text.font.Font(it)) } ?: KitabFontFamily
        }
    }
}

/** ﴿…﴾ ranges in the reader (SIL OFL, from the Amiri project). */
val QuranFontFamily = FontFamily(Font(R.font.amiri_quran, FontWeight.Normal))

/** Every corner in the app, rounder or squarer with [AtharStyle]; pills and dots use CircleShape. */
object AtharCorners {
    /** Marks inside text, small badges. */
    val Tag: RoundedCornerShape @Composable get() = corner(4)
    /** Chips, toggles, small buttons and fields. */
    val Control: RoundedCornerShape @Composable get() = corner(8)
    /** Rows, cards and panels. */
    val Card: RoundedCornerShape @Composable get() = corner(12)
    /** Menus, dialogs and sheets. */
    val Surface: RoundedCornerShape @Composable get() = corner(14)
    /** The player's cover. */
    val Artwork: RoundedCornerShape @Composable get() = corner(24)

    @Composable
    private fun corner(dp: Int) = RoundedCornerShape((dp * LocalAtharStyle.current.round).dp)
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
 * «النمط»: how surfaces are built, apart from their colours ([ThemeMode]). [round] scales
 * every [AtharCorners] corner.
 */
enum class AtharStyle(val label: String, val hint: String, internal val round: Float) {
    Paper("ورق", "سطوح مسطّحة بدرجات لونية هادئة", 1f),
    Glass("زجاج", "أشرطة شفّافة يظهر ما يمرّ تحتها مغبّشًا", 1.45f),
    Raised("بارز", "أشرطة وقوائم مرفوعة بظلال ناعمة وزوايا أكثر استدارة", 1.35f),
    Ink("حبر", "خطوط بالحبر وحده: بلا تعبئة ولا ظلال ولا حركة، وتباين أعلى", 0.3f),
    Manuscript("مخطوط", "رقّ عتيق تحيط بأشرطته وبطاقاته خطوط مزدوجة مذهّبة", 0.55f),
    Deep("عميق", "أرضية غائرة بلونك وبطاقات طافية فوقها طبقةً فوق طبقة", 1.6f),
    Bound("مجلّد", "كل بطاقة كجلد كتاب بكعبٍ بلونك، والقوائم بإطار بلونك", 0.7f),
    ;
    /** Bars and cards float off the ground (بارز، عميق). */
    val floats get() = this == Raised || this == Deep
}

/** «مخطوط»'s gilt rule. */
internal fun gilt(dark: Boolean) = if (dark) Color(0xFFC9A45C).copy(alpha = 0.55f) else Color(0xFFA9844A).copy(alpha = 0.7f)

val LocalAtharStyle = staticCompositionLocalOf { AtharStyle.Paper }

/**
 * «اللون»: the one accent every selection, fill, mark and the launcher icon take. [base] is the
 * light accent and every fill (white on it is at least 5:1); [night] is the small-mark accent at night.
 */
enum class AtharAccent(val label: String, val base: Color, val pressed: Color, val night: Color, val nightPressed: Color) {
    Burgundy("عنّابي", Color(0xFF8F3537), Color(0xFF71272A), Color(0xFFD1787D), Color(0xFFE1979B)),
    Green("أخضر", Color(0xFF2F6B4F), Color(0xFF24533D), Color(0xFF7EC3A0), Color(0xFF9FD4B8)),
    Blue("أزرق", Color(0xFF2E5A88), Color(0xFF23466B), Color(0xFF8DB4E2), Color(0xFFAECBEC)),
    Brown("بنّي", Color(0xFF8A5A2B), Color(0xFF6C4621), Color(0xFFD2A06C), Color(0xFFDFB78C)),
    Purple("بنفسجي", Color(0xFF5E4485), Color(0xFF4A3569), Color(0xFFB39ADB), Color(0xFFC8B5E5)),
}

private fun AtharExtraColors.accented(a: AtharAccent, dark: Boolean) = copy(
    accent = if (dark) a.night else a.base,
    accentPressed = if (dark) a.nightPressed else a.pressed,
    primaryAction = a.base,
    accentSurface = a.base.copy(alpha = if (dark) 0.30f else 0.12f),
)

/**
 * What «النمط» changes in the palette. «بارز»: the ground sinks to a tone and what floats on it
 * (bars, menus, chips — [raisedSurface]) is the bright paper, so lift reads as light, not only shadow.
 * «زجاج»: an accent-tinted ground. «حبر»: tone fills give way to the page, rules and quiet text darken.
 */
private fun AtharExtraColors.styled(style: AtharStyle, ink: Color, dark: Boolean) = when (style) {
    // At night the ground sinks below the paper and what floats is a clear step lighter.
    AtharStyle.Raised -> if (dark) copy(canvas = Color(0xFF090807), raisedSurface = Color(0xFF221F1C), inputSurface = Color(0xFF1A1816)) else copy(
        // فاتح sinks to a neutral gray, not the warm paper tone that read as cream; دافئ keeps its cream.
        canvas = if (canvas == Paper) Color(0xFFEEEEF0) else androidx.compose.ui.graphics.lerp(canvas, pressedSurface, 0.75f),
        raisedSurface = canvas,
        inputSurface = canvas,
    )
    // «زجاج»: a ground tinted with the accent, so the frosted panes over it read as glass.
    AtharStyle.Glass -> copy(canvas = androidx.compose.ui.graphics.lerp(canvas, accent, if (dark) 0.22f else 0.14f))
    AtharStyle.Ink -> inked(ink)
    AtharStyle.Manuscript -> if (dark) copy(
        canvas = Color(0xFF16110B), raisedSurface = Color(0xFF211910), pressedSurface = Color(0xFF2A2015),
        inputSurface = Color(0xFF1D160E), divider = gilt(true), inputBorder = gilt(true),
    ) else copy(
        canvas = Color(0xFFF0E4C8), raisedSurface = Color(0xFFF7EEDA), pressedSurface = Color(0xFFE6D6B4),
        inputSurface = Color(0xFFF7EEDA), divider = gilt(false), inputBorder = gilt(false),
    )
    // A ground sunk in the accent; the cards over it are the brightest thing on screen.
    AtharStyle.Deep -> if (dark) copy(
        canvas = androidx.compose.ui.graphics.lerp(Color(0xFF060507), accent, 0.10f),
        raisedSurface = androidx.compose.ui.graphics.lerp(Color(0xFF1E1C1F), accent, 0.12f),
        inputSurface = androidx.compose.ui.graphics.lerp(Color(0xFF161417), accent, 0.10f),
    ) else copy(
        canvas = androidx.compose.ui.graphics.lerp(Color(0xFFE2DED9), accent, 0.10f),
        raisedSurface = Color.White,
        inputSurface = Color.White,
    )
    AtharStyle.Bound -> this
    else -> this
}

private fun AtharExtraColors.inked(ink: Color) = copy(
    raisedSurface = canvas,
    pressedSurface = canvas,
    inputSurface = canvas,
    divider = ink.copy(alpha = 0.32f),
    inputBorder = ink.copy(alpha = 0.6f),
    secondaryText = ink.copy(alpha = 0.82f),
    placeholderText = ink.copy(alpha = 0.66f),
)

/**
 * The app's resolved theme, for surfaces that paint their own colours.
 *
 * Surfaces that paint their own colours ask this rather than the system, so they never
 * disagree with the app's choice.
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
    style: AtharStyle = AtharStyle.Paper,
    accent: AtharAccent = AtharAccent.Burgundy,
    content: @Composable () -> Unit,
) {
    val warmLight = warm && !darkTheme
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent.night,
            onPrimary = NightPaper,
            primaryContainer = androidx.compose.ui.graphics.lerp(NightPaper, accent.base, 0.45f),
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
            primary = accent.base,
            onPrimary = Color.White,
            primaryContainer = androidx.compose.ui.graphics.lerp(Color.White, accent.base, 0.15f),
            onPrimaryContainer = androidx.compose.ui.graphics.lerp(accent.base, Color.Black, 0.5f),
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
        }.accented(accent, darkTheme).styled(style, if (darkTheme) NightInk else if (warmLight) CreamInk else LightInk, darkTheme),
        LocalAtharDarkTheme provides darkTheme,
        LocalAtharStyle provides style,
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
                androidx.compose.foundation.LocalIndication provides if (style == AtharStyle.Ink) NoPress else PressSquish,
                androidx.compose.material3.LocalRippleConfiguration provides null,
                content = content,
            )
        }
    }
}

/** «حبر»: a press neither draws nor moves anything. */
private object NoPress : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        object : androidx.compose.ui.Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 8
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
