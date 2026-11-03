package com.atharchive.ui.components

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharTheme

/** The menu's palette. App screens take the theme; reader pages pass their own page colours. */
data class AtharMenuColors(val card: Color, val ink: Color, val secondary: Color, val accent: Color)

@Composable
fun atharMenuColors() = AtharMenuColors(
    card = AtharTheme.colors.raisedSurface,
    ink = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    secondary = AtharTheme.colors.secondaryText,
    accent = AtharTheme.colors.accent,
)

val LocalAtharActionAnchor = staticCompositionLocalOf<MutableState<Rect>?> { null }

private val LocalMenuColors = staticCompositionLocalOf<AtharMenuColors?> { null }

/** Sits on the anchor's end edge, above it when it fits, otherwise below; always on screen. */
internal fun atharMenuOffset(anchor: IntRect, window: IntSize, direction: LayoutDirection, menu: IntSize, gapPx: Int, marginPx: Int): IntOffset {
    val x = if (direction == LayoutDirection.Ltr) anchor.right - menu.width else anchor.left
    val above = anchor.top - gapPx - menu.height
    val y = if (above >= marginPx) above else anchor.bottom + gapPx
    return IntOffset(
        x.coerceIn(marginPx, (window.width - menu.width - marginPx).coerceAtLeast(marginPx)),
        y.coerceIn(marginPx, (window.height - menu.height - marginPx).coerceAtLeast(marginPx)),
    )
}

/** Whether the menu opened above its button: it grows from that edge. */
private class Pointer {
    var above by mutableStateOf(false)
}

/**
 * A small card that opens beside the button it sits with — put it in the same Box as that
 * button. Tap outside or back closes it. Rounded, softly shadowed, rows with
 * the icon at the end: the iOS context menu, as in Sada. Nothing scrolls inside it: a menu
 * holds what fits, and a long list is a grid or a typed number instead.
 */
@Composable
fun AtharMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    colors: AtharMenuColors = atharMenuColors(),
    anchor: Rect? = null,
    focusable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val pointer = remember { Pointer() }
    val position = remember(density, anchor) {
        val gap = with(density) { 2.dp.roundToPx() }
        val margin = with(density) { 8.dp.roundToPx() }
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val bounds = anchor?.let { IntRect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) } ?: anchorBounds
                val offset = atharMenuOffset(bounds, windowSize, layoutDirection, popupContentSize, gap, margin)
                pointer.above = offset.y + popupContentSize.height <= bounds.top + gap
                return offset
            }
        }
    }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = focusable)) {
        ProvideAtharTextToolbar {
        AnimatedVisibility(
            visibleState = visible,
            enter = if (com.atharchive.ui.theme.LocalAtharStyle.current == com.atharchive.ui.theme.AtharStyle.Ink) androidx.compose.animation.EnterTransition.None
            else fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.9f,
                transformOrigin = TransformOrigin(0.5f, if (pointer.above) 1f else 0f)),
        ) {
            val shape = AtharCorners.Surface
            // A hairline only where a soft shadow can't separate the card from a dark page.
            val edge = colors.ink.copy(alpha = 0.08f)
            val menuWidth = minOf(width, configuration.screenWidthDp.dp - 16.dp)
            CompositionLocalProvider(LocalMenuColors provides colors) {
                // Room around the card for its shadow; no pointer: the menu opens at its button.
                Box(Modifier.padding(6.dp)) {
                    Column(
                        modifier
                            .width(menuWidth)
                            .atharChrome(colors.card, shape, elevation = 8.dp, edge = edge, haze = null)
                            .padding(vertical = 3.dp),
                        content = content,
                    )
                }
            }
        }
        }
    }
}

/**
 * One row: the label first, the icon at the row's end, iOS style. [checked] non-null makes it a
 * choice with a leading check; [subtitle] is a second, quieter line.
 */
@Composable
fun AtharMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    checked: Boolean? = null,
    subtitle: String? = null,
    destructive: Boolean = false,
    /** A colour swatch at the row's end, for colour choices. */
    dot: Color? = null,
) {
    val colors = LocalMenuColors.current ?: atharMenuColors()
    val actionAnchor = LocalAtharActionAnchor.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val tint = if (destructive) Color(0xFFD9534F) else colors.ink
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .then(if (checked != null) Modifier.semantics { selected = checked } else Modifier)
            .onGloballyPositioned { bounds = Rect(it.positionOnScreen(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }
            .clickable(role = if (checked != null) Role.RadioButton else Role.Button) { actionAnchor?.value = bounds; onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (checked != null) Box(Modifier.size(16.dp)) {
            if (checked) Icon(AtharIcons.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = tint, fontSize = AtharTextSize.Body, fontWeight = if (checked == true) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.secondary, fontSize = AtharTextSize.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (icon != null) Icon(icon, null, tint = colors.secondary.takeUnless { destructive } ?: tint, modifier = Modifier.size(16.dp))
        if (dot != null) Box(Modifier.size(14.dp).clip(CircleShape).background(dot))
    }
}

/** Between menu groups: a hairline, not a band. */
@Composable
fun AtharMenuDivider() {
    val colors = LocalMenuColors.current ?: atharMenuColors()
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.5.dp).background(colors.ink.copy(alpha = 0.14f)))
}
