package com.atharchive.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A sideways swipe over a section moves to the neighbouring tab, as tapping it would (the soap
 * pill travels the same way). In RTL the next tab sits to the left, so a swipe to the right
 * brings it in. Vertical scrolling and horizontal rows inside keep their own drags.
 */
@Composable
fun Modifier.swipeTabs(keys: List<String>, selectedKey: String, onSelect: (String) -> Unit): Modifier {
    if (keys.size < 2) return this
    // Read live: the detector is keyed on the direction only (a frozen copy switched from a stale tab).
    val tabs by rememberUpdatedState(keys)
    val selected by rememberUpdatedState(selectedKey)
    val select by rememberUpdatedState(onSelect)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(rtl) {
        val threshold = 64.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (abs(total) >= threshold) {
                    val step = if ((total > 0) == rtl) 1 else -1
                    tabs.getOrNull(tabs.indexOf(selected) + step)?.let(select)
                }
            },
        ) { change, dx -> change.consume(); total += dx }
    }
}

/** How far a side panel has been swiped shut, so its scrim can fade as it goes. */
class PanelSwipe internal constructor() {
    internal val shift = Animatable(0f)
    internal var width by mutableFloatStateOf(0f)
    /** 0 = open, 1 = off screen. Read it in a draw or layer block, so following the finger only redraws. */
    val progress: Float get() = if (width <= 0f) 0f else (abs(shift.value) / width).coerceIn(0f, 1f)
}

@Composable
fun rememberPanelSwipe(): PanelSwipe = remember { PanelSwipe() }

/**
 * A side panel at the start edge closes when swiped back toward that edge: it follows the finger,
 * and springs back if let go before a third of the way or without a flick.
 */
@Composable
fun Modifier.swipeToClosePanel(swipe: PanelSwipe, enabled: Boolean = true, onClose: () -> Unit): Modifier {
    if (!enabled) return this
    val close by rememberUpdatedState(onClose)
    val scope = rememberCoroutineScope()
    val shift = swipe.shift
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The detector sits before the offset, so it measures the finger against the panel's resting place.
    return pointerInput(rtl) {
        val toEdge = if (rtl) 1f else -1f // the start edge: right in RTL
        swipe.width = size.width.toFloat()
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragCancel = { scope.launch { shift.animateTo(0f, spring()) } },
            onDragEnd = {
                val flick = tracker.calculateVelocity().x * toEdge
                val far = shift.value * toEdge > size.width / 3f
                scope.launch {
                    if (far || flick > 800f) { shift.animateTo(size.width * toEdge); close() }
                    else shift.animateTo(0f, spring())
                }
            },
        ) { change, dx ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            scope.launch { shift.snapTo(((shift.value + dx) * toEdge).coerceAtLeast(0f) * toEdge) }
        }
    }.absoluteOffset { IntOffset(shift.value.roundToInt(), 0) }
}
