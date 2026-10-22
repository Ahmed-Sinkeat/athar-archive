package com.atharchive.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private val TailWidth = 14.dp
private val TailHeight = 7.dp

/**
 * A small callout pinned to a spot in the text: a poem word's meaning, a footnote number's note.
 *
 * It sits above the spot so the finger that opened it is not covering it, and drops below only
 * when there is no room above. The tail is what gives it a place: without it the card floats
 * somewhere near the line and belongs to nothing.
 *
 * [anchor] maps the popup's anchor bounds (window coordinates of the layout it is called in) to
 * the window rect of the spot, so a caller can pass a rect relative to its own layout or one it
 * already has in window coordinates.
 */
@Composable
fun AtharTailPopover(
    anchor: (IntRect) -> Rect,
    ground: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 268.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val margin = with(density) { 12.dp.roundToPx() }

    // ponytail: the provider owns the x, and the tail has to point back at the spot from
    // wherever that landed, so it writes the offset back for the content to read.
    // No cycle: the tail never changes the card's size, so this settles in one frame.
    val tailCentre = remember { mutableFloatStateOf(Float.NaN) }
    var below by remember { mutableStateOf(false) }

    val provider = remember(anchor, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val spot = anchor(anchorBounds)
                val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                val x = (spot.center.x - popupContentSize.width / 2f).toInt().coerceIn(margin, maxX)
                val above = spot.top.toInt() - popupContentSize.height
                val fitsAbove = above >= margin
                below = !fitsAbove
                tailCentre.floatValue = spot.center.x - x
                return IntOffset(x, if (fitsAbove) above else spot.bottom.toInt())
            }
        }
    }

    val radiusPx = with(density) { 10.dp.toPx() }
    val tailWpx = with(density) { TailWidth.toPx() }
    val tailHpx = with(density) { TailHeight.toPx() }

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // focusable, or an outside tap never reaches onDismissRequest and it sticks open
        properties = PopupProperties(focusable = true),
    ) {
        ProvideAtharTextToolbar {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .width(IntrinsicSize.Max)
                .drawBehind {
                    val bodyTop = if (below) tailHpx else 0f
                    val bodyBottom = if (below) size.height else size.height - tailHpx
                    drawRoundRect(
                        color = ground,
                        topLeft = Offset(0f, bodyTop),
                        size = Size(size.width, bodyBottom - bodyTop),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                    )
                    val centre = tailCentre.floatValue
                    if (centre.isNaN()) return@drawBehind
                    val cx = centre.coerceIn(radiusPx + tailWpx / 2f, size.width - radiusPx - tailWpx / 2f)
                    val path = Path().apply {
                        if (below) {
                            moveTo(cx - tailWpx / 2f, bodyTop)
                            lineTo(cx, 0f)
                            lineTo(cx + tailWpx / 2f, bodyTop)
                        } else {
                            moveTo(cx - tailWpx / 2f, bodyBottom)
                            lineTo(cx, size.height)
                            lineTo(cx + tailWpx / 2f, bodyBottom)
                        }
                        close()
                    }
                    drawPath(path, ground)
                }
                .padding(top = if (below) TailHeight else 0.dp, bottom = if (below) 0.dp else TailHeight)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .then(modifier),
            content = content,
        )
        }
    }
}
