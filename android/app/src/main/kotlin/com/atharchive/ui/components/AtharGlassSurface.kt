package com.atharchive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.atharchive.ui.theme.gilt
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atharchive.ui.theme.AtharStyle
import com.atharchive.ui.theme.LocalAtharDarkTheme
import com.atharchive.ui.theme.LocalAtharStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** What the reader's bars blur under «زجاج»: the page they float over. Null: nothing to blur. */
val LocalAtharHaze = staticCompositionLocalOf<HazeState?> { null }

/** Marks the content that scrolls under the bars. Recorded only under «زجاج». */
@Composable
fun Modifier.atharBackdrop(haze: HazeState?): Modifier =
    if (haze != null && LocalAtharStyle.current == AtharStyle.Glass) hazeSource(haze) else this

/**
 * The ground of whatever floats over content — bars, headers, menus, toolbars — built the way
 * «النمط» says: ورق a flat [fill] (with the caller's own [elevation] and [edge]); زجاج a frosted
 * pane over [haze] (a denser tint without one, or below Android 12); بارز a soft shadow; حبر an
 * ink outline and nothing else. A full-width bar ([RectangleShape]) takes no outline.
 */
@Composable
fun Modifier.atharChrome(
    fill: Color,
    shape: Shape = RectangleShape,
    elevation: Dp = 0.dp,
    edge: Color = Color.Unspecified,
    haze: HazeState? = LocalAtharHaze.current,
): Modifier {
    val outlined = shape != RectangleShape
    val shade = Color.Black.copy(alpha = 0.22f)
    val clip = if (outlined) Modifier.clip(shape) else Modifier
    return when (LocalAtharStyle.current) {
        AtharStyle.Paper -> shadow(elevation, shape, clip = false, ambientColor = shade, spotColor = shade)
            .then(clip)
            .background(fill, shape)
            .then(if (outlined && edge.isSpecified) Modifier.border(0.7.dp, edge, shape) else Modifier)
        AtharStyle.Glass -> shadow(elevation, shape, clip = false, ambientColor = shade, spotColor = shade)
            .then(clip)
            .then(
                if (haze != null) Modifier.hazeEffect(haze, HazeStyle(backgroundColor = fill, tint = HazeTint(fill.copy(alpha = 0.82f)), blurRadius = 24.dp, noiseFactor = 0f))
                else Modifier.background(fill.copy(alpha = 0.9f), shape),
            )
            // The bright rim a pane of glass catches; on a light page the caller's own edge, which a white rim would lose.
            .then(if (outlined) Modifier.border(0.7.dp, rim(edge), shape) else Modifier)
        // The ground is a tone under «بارز»; whatever floats takes the bright paper instead.
        AtharStyle.Raised -> shadow(elevation + 10.dp, shape, clip = false, ambientColor = shade.copy(alpha = 0.3f), spotColor = shade.copy(alpha = 0.3f))
            .then(clip)
            .background(com.atharchive.ui.theme.AtharTheme.colors.let { if (fill == it.canvas) it.raisedSurface else fill }, shape)
        AtharStyle.Ink -> then(clip).background(fill, shape)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground, shape) else Modifier)
        AtharStyle.Manuscript -> shadow(elevation, shape, clip = false, ambientColor = shade, spotColor = shade)
            .then(clip).background(fill, shape)
            .then(if (outlined) Modifier.doubleRule(gilt(LocalAtharDarkTheme.current), shape) else Modifier)
        // Deep: a long soft shadow by day; by night, where shadow is lost, a faint lit rim.
        AtharStyle.Deep -> shadow(elevation + 14.dp, shape, clip = false, ambientColor = shade.copy(alpha = 0.4f), spotColor = shade.copy(alpha = 0.4f))
            .then(clip)
            .background(com.atharchive.ui.theme.AtharTheme.colors.let { if (fill == it.canvas) it.raisedSurface else fill }, shape)
            .then(if (outlined && LocalAtharDarkTheme.current) Modifier.border(0.7.dp, Color.White.copy(alpha = 0.10f), shape) else Modifier)
        AtharStyle.Bound -> shadow(elevation, shape, clip = false, ambientColor = shade, spotColor = shade)
            .then(clip).background(fill, shape)
            .then(if (outlined) Modifier.border(1.4.dp, com.atharchive.ui.theme.AtharTheme.colors.accent.copy(alpha = 0.75f), shape) else Modifier)
    }
}

/** «مخطوط»: an outer rule and a finer one just inside it, like a manuscript page's frame. */
private fun Modifier.doubleRule(color: Color, shape: Shape) = drawWithContent {
    drawContent()
    val outer = 1.dp.toPx(); val gap = 3.dp.toPx()
    drawOutline(shape.createOutline(size, layoutDirection, this), color, style = Stroke(outer))
    val inner = androidx.compose.ui.geometry.Size(size.width - 2 * gap, size.height - 2 * gap)
    translate(gap, gap) { drawOutline(shape.createOutline(inner, layoutDirection, this), color.copy(alpha = color.alpha * 0.7f), style = Stroke(0.6.dp.toPx())) }
}

@Composable
private fun rim(edge: Color) = when {
    LocalAtharDarkTheme.current -> Color.White.copy(alpha = 0.14f)
    edge.isSpecified -> edge
    else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
}

/** An unselected chip's ground: a tone [fill], lifted under «بارز», only an outline under «حبر». */
@Composable
fun Modifier.atharControl(fill: Color, shape: Shape): Modifier = when (LocalAtharStyle.current) {
    AtharStyle.Manuscript -> background(fill, shape).border(0.7.dp, gilt(LocalAtharDarkTheme.current), shape)
    AtharStyle.Deep -> shadow(4.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
        .background(com.atharchive.ui.theme.AtharTheme.colors.raisedSurface, shape)
    AtharStyle.Raised -> shadow(3.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
        .background(com.atharchive.ui.theme.AtharTheme.colors.raisedSurface, shape)
    AtharStyle.Ink -> border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), shape)
    else -> background(fill, shape)
}

/**
 * One list entry, built the way «النمط» says: ورق and حبر leave it on the ground between
 * [AtharRowDivider]s; بارز lifts it into a card of its own; زجاج makes it a frosted pane over the tinted ground.
 */
@Composable
fun AtharRowItem(content: @Composable () -> Unit) {
    val colors = com.atharchive.ui.theme.AtharTheme.colors
    val shape = com.atharchive.ui.theme.AtharCorners.Card
    val card = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    androidx.compose.foundation.layout.Column(
        when (LocalAtharStyle.current) {
            AtharStyle.Raised -> card
                .shadow(5.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                .clip(shape).background(colors.raisedSurface, shape)
            // Kept faint on purpose: a gloss or a bright rim reads as dated, not as glass.
            AtharStyle.Glass -> card.clip(shape).background(colors.raisedSurface.copy(alpha = 0.86f), shape)
                .border(0.6.dp, Color.White.copy(alpha = if (LocalAtharDarkTheme.current) 0.09f else 0.5f), shape)
            AtharStyle.Deep -> card.padding(vertical = 2.dp)
                .shadow(10.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(shape).background(colors.raisedSurface, shape)
                .then(if (LocalAtharDarkTheme.current) Modifier.border(0.7.dp, Color.White.copy(alpha = 0.08f), shape) else Modifier)
            AtharStyle.Manuscript -> card.clip(shape).background(colors.raisedSurface, shape).doubleRule(gilt(LocalAtharDarkTheme.current), shape)
            // A book on the shelf: a cover in the accent's shade with a solid spine on its leading edge.
            AtharStyle.Bound -> card.clip(shape)
                .background(androidx.compose.ui.graphics.lerp(colors.raisedSurface, colors.accent, if (LocalAtharDarkTheme.current) 0.16f else 0.08f), shape)
                .drawWithContent {
                    drawContent()
                    val spine = 5.dp.toPx()
                    val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - spine else 0f
                    drawRect(colors.accent, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(spine, size.height))
                }
                .padding(start = 5.dp)
            else -> Modifier
        },
    ) { content() }
}

/** The hairline between [AtharRowItem]s; cards (بارز، زجاج) need none. */
@Composable
fun AtharRowDivider(modifier: Modifier = Modifier, thickness: Dp = 0.7.dp, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    when (LocalAtharStyle.current) {
        AtharStyle.Raised, AtharStyle.Glass, AtharStyle.Deep, AtharStyle.Manuscript, AtharStyle.Bound -> Unit
        // حبر: a ruled line in ink, not a faint gray.
        AtharStyle.Ink -> androidx.compose.material3.HorizontalDivider(modifier, 0.8.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
        else -> androidx.compose.material3.HorizontalDivider(modifier, thickness, color)
    }
}
