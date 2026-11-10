package com.atharchive.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.atharchive.R
import com.atharchive.ui.theme.AtharTheme

/**
 * The Athar mark, drawn as two tinted layers.
 *
 * The artwork is a flat two-colour PNG, so it was split into an ink layer and an accent
 * layer that each carry only alpha; the colour comes from the tint at draw time. That is
 * what makes it theme-aware — a single-image mark cannot recolour, and a charcoal mark on
 * the `#151311` dark canvas is a contrast ratio of about 1.05:1, i.e. invisible.
 *
 * Replace with a real [androidx.compose.ui.graphics.vector.ImageVector] if a vector of this
 * mark ever exists: same call site, sharper at large sizes, one file instead of two.
 */
@Composable
fun AtharMark(
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.onBackground,
    accent: Color = AtharTheme.colors.accent,
    contentDescription: String? = null,
    /** 0 → 1: the ink is written in from the right, then the accent drops on with a small bounce. */
    reveal: () -> Float = { 1f },
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.ic_athar_mark_ink),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().drawWithContent {
                val written = (reveal() / 0.65f).coerceIn(0f, 1f)
                if (written >= 1f) drawContent() else clipRect(left = size.width * (1f - written)) { this@drawWithContent.drawContent() }
            },
            colorFilter = ColorFilter.tint(ink),
        )
        Image(
            painter = painterResource(R.drawable.ic_athar_mark_accent),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val a = ((reveal() - 0.55f) / 0.45f).coerceIn(0f, 1f)
                alpha = a
                val s = 0.7f + 0.3f * a + 0.14f * kotlin.math.sin(kotlin.math.PI.toFloat() * a)
                scaleX = s; scaleY = s
            },
            colorFilter = ColorFilter.tint(accent),
        )
    }
}

/** The mark's writing-in, the length of every play of it. */
val AtharMarkMotion = androidx.compose.animation.core.tween<Float>(700, easing = androidx.compose.animation.core.LinearOutSlowInEasing)

/**
 * A fresh start from the launcher: the mark writes itself in over the screen that is already
 * composing underneath, then fades away; any touch ends it at once.
 */
@Composable
fun AtharLaunchIntro(launcher: String, onDone: () -> Unit) {
    val done by androidx.compose.runtime.rememberUpdatedState(onDone)
    val reveal = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val cover = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // A cold start is busy composing the screen underneath: wait (up to 0.7 s) for two
        // frames in a row on time, or the writing-in plays in frames nobody sees.
        var last = 0L; var smooth = 0
        val deadline = System.nanoTime() + 700_000_000L
        while (smooth < 2 && System.nanoTime() < deadline) androidx.compose.runtime.withFrameNanos { now ->
            smooth = if (last != 0L && now - last < 20_000_000L) smooth + 1 else 0
            last = now
        }
        reveal.animateTo(1f, AtharMarkMotion)
        kotlinx.coroutines.delay(120)
        cover.animateTo(0f, androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing))
        done()
    }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = cover.value }
            .then(LauncherFields[launcher]?.let { (centre, edge) -> Modifier.background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(centre, edge))) }
                ?: Modifier.background(AtharTheme.colors.canvas))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); done() } },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        val field = launcher in LauncherFields
        if (field) AtharMark(Modifier.size(132.dp), ink = LauncherParchment, accent = LauncherParchment, reveal = { reveal.value })
        else AtharMark(Modifier.size(132.dp), reveal = { reveal.value })
    }
}

/**
 * A coloured icon's field (res/drawable/ic_athar_launcher_background_*.xml: centre, edge), so the
 * opening screen is the icon the reader just tapped. The original icon keeps the page colour.
 */
private val LauncherFields = mapOf(
    "Red" to (Color(0xFF8F3537) to Color(0xFF5A1F21)),
    "Green" to (Color(0xFF2F6B4F) to Color(0xFF1C4231)),
    "Blue" to (Color(0xFF2E5A88) to Color(0xFF1B3858)),
    "Brown" to (Color(0xFF8A5A2B) to Color(0xFF593818)),
    "Purple" to (Color(0xFF5E4485) to Color(0xFF3A2958)),
    "Navy" to (Color(0xFF1D4670) to Color(0xFF0B2036)),
)
private val LauncherParchment = Color(0xFFDDCFB4)
