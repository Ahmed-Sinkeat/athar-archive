package com.atharchive.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp

// Amiri's typo ascent/descent (1124/-634 per 1000). The band is drawn to the glyphs, not
// the airy line box, and handles hang from the glyph bottom.
private const val GlyphAscent = 1.124f * 0.92f
private const val GlyphDescent = 0.634f

/** A block's laid-out text where the page put it. Read live, so selection follows scrolling. */
internal class TextGeometry(
    val block: ReaderBlock,
    val ordinal: Int,
    val layout: TextLayoutResult,
    val value: SelectionText,
    val coords: LayoutCoordinates,
) {
    fun top(): Float = if (coords.isAttached) coords.positionInRoot().y else 0f

    private val fontPx get() = with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() }
    fun glyphTop(line: Int) = maxOf(layout.getLineTop(line), layout.getLineBaseline(line) - fontPx * GlyphAscent)
    fun glyphBottom(line: Int) = minOf(layout.getLineBottom(line), layout.getLineBaseline(line) + fontPx * GlyphDescent)
}

private fun ReaderSelectionEndpoint.compareTo(other: ReaderSelectionEndpoint) =
    compareValuesBy(this, other, { it.ordinal }, { it.offset })

/**
 * Selection drawn on the page itself — no second text layer, so nothing re-wraps or jumps.
 * Endpoints are durable (block id, ordinal, source offset); the text to copy is built only
 * when an action runs, so a selection can cross blocks the pager has already dropped.
 */
@Stable
internal class PageSelection {
    var start by mutableStateOf<ReaderSelectionEndpoint?>(null)
        private set
    var end by mutableStateOf<ReaderSelectionEndpoint?>(null)
        private set
    /** True while a finger is moving an endpoint; the toolbar steps aside. */
    var dragging by mutableStateOf(false)
        private set
    val active get() = start != null && end != null

    /** ordinal → laid-out text, for every block the page has placed. */
    val geometry = HashMap<Int, TextGeometry>()
    /** The page's own coordinates; pointer and drawing positions are in this space. */
    var page: LayoutCoordinates? = null

    // Drag state: a long-press drag grows from [anchor]; a handle drag moves one end.
    private var anchor: Pair<ReaderSelectionEndpoint, ReaderSelectionEndpoint>? = null
    private var movingStart: Boolean? = null
    private var grab = Offset.Zero
    var pointer: Offset? = null
        private set

    fun clear() { start = null; end = null; dragging = false; anchor = null; movingStart = null }

    fun select(from: ReaderSelectionEndpoint, to: ReaderSelectionEndpoint) {
        if (from.compareTo(to) < 0) { start = from; end = to } else { start = to; end = from }
    }

    /** Source offsets of the selection inside [ordinal]'s text, or null when it is not selected. */
    fun rangeIn(ordinal: Int, length: Int): IntRange? {
        val s = start ?: return null
        val e = end ?: return null
        if (ordinal < s.ordinal || ordinal > e.ordinal) return null
        val from = if (ordinal == s.ordinal) s.offset else 0
        val to = if (ordinal == e.ordinal) e.offset else length
        return if (to > from) from until to else null
    }

    private class Hit(val geometry: TextGeometry, val offset: Int)

    /** The text under [point], or the nearest line of the nearest block when the press is in a gap. */
    private fun hit(point: Offset): Hit? {
        val page = page?.takeIf { it.isAttached } ?: return null
        var best: TextGeometry? = null
        var bestDistance = Float.MAX_VALUE
        var bestLocal = Offset.Zero
        // ponytail: linear scan over the ~10 placed blocks; index by top if the page ever holds hundreds.
        for (g in geometry.values) {
            if (!g.coords.isAttached || g.layout.layoutInput.text.isEmpty()) continue
            val local = g.coords.localPositionOf(page, point)
            val height = g.coords.size.height
            val distance = when {
                local.y < 0 -> -local.y
                local.y > height -> local.y - height
                else -> 0f
            }
            if (distance < bestDistance) { best = g; bestDistance = distance; bestLocal = local }
        }
        val g = best ?: return null
        val y = bestLocal.y.coerceIn(0f, (g.layout.size.height - 1).coerceAtLeast(0).toFloat())
        return Hit(g, g.layout.getOffsetForPosition(Offset(bestLocal.x, y)))
    }

    /** The ordinal and source offset of the text exactly under [point]; null in a gap. */
    fun sourceAt(point: Offset): Pair<Int, Int>? {
        val page = page?.takeIf { it.isAttached } ?: return null
        val g = geometry.values.firstOrNull { g ->
            g.coords.isAttached && g.coords.localPositionOf(page, point).y in 0f..g.coords.size.height.toFloat()
        } ?: return null
        val local = g.coords.localPositionOf(page, point)
        return g.ordinal to g.value.sourceOffset(g.layout.getOffsetForPosition(local))
    }

    private fun endpoint(g: TextGeometry, display: Int) =
        ReaderSelectionEndpoint(g.block.id, g.ordinal, g.value.sourceOffset(display))

    private fun wordAt(point: Offset): Pair<ReaderSelectionEndpoint, ReaderSelectionEndpoint>? {
        val hit = hit(point) ?: return null
        val word = wordNear(hit.geometry.value.text, hit.offset, hit.geometry.layout::getWordBoundary) ?: return null
        return endpoint(hit.geometry, word.start) to endpoint(hit.geometry, word.end)
    }

    /** Long press: select the word under the finger; keep dragging to grow from it. */
    fun beginWord(point: Offset): Boolean {
        val word = wordAt(point) ?: return false
        select(word.first, word.second)
        anchor = word
        movingStart = null
        pointer = point
        dragging = true
        return true
    }

    /** Selects the first word of a block — the accessibility long-click path. */
    fun beginFirstWord(g: TextGeometry): Boolean {
        val text = g.value.text
        val first = text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return false
        val word = wordNear(text, first, g.layout::getWordBoundary) ?: return false
        select(endpoint(g, word.start), endpoint(g, word.end))
        return true
    }

    /** The handle under [point], if any: true = start, false = end. */
    fun handleAt(point: Offset, radius: Float, drop: Float): Boolean? {
        if (!active) return null
        val candidates = listOf(true, false).mapNotNull { isStart ->
            handlePoint(isStart)?.let { isStart to (it + Offset(0f, drop) - point).getDistance() }
        }.filter { it.second <= radius }
        return candidates.minByOrNull { it.second }?.first
    }

    fun beginHandle(isStart: Boolean, point: Offset) {
        val handle = handlePoint(isStart) ?: return
        // The finger sits below the text; keep the text point under it where it was grabbed.
        grab = point - handle + Offset(0f, glyphHeightAt(isStart) / 2f)
        movingStart = isStart
        anchor = null
        pointer = point
        dragging = true
    }

    /** Applies the drag at [point]. Also called each frame while edge-scrolling, with the same point. */
    fun drag(point: Offset) {
        pointer = point
        val moving = movingStart
        if (moving == null) {
            val (aStart, aEnd) = anchor ?: return
            val (wStart, wEnd) = wordAt(point) ?: return
            select(if (wStart.compareTo(aStart) < 0) wStart else aStart, if (wEnd.compareTo(aEnd) > 0) wEnd else aEnd)
            return
        }
        val hit = hit(point - grab) ?: return
        val word = hit.geometry.layout.getWordBoundary(hit.offset.coerceAtMost((hit.geometry.value.text.length - 1).coerceAtLeast(0)))
        val next = endpoint(hit.geometry, nearestBoundary(hit.offset, word))
        val s = start ?: return
        val e = end ?: return
        // Endpoints never cross: a handle stops one boundary short of the other.
        if (moving && next.compareTo(e) < 0) start = next
        if (!moving && next.compareTo(s) > 0) end = next
    }

    fun endDrag() { dragging = false; anchor = null; movingStart = null; pointer = null }

    /** Moves an endpoint one line up or down — the accessibility path for adjusting a selection. */
    fun moveByLine(isStart: Boolean, direction: Int): Boolean {
        val e = (if (isStart) start else end) ?: return false
        val g = geometry[e.ordinal]?.takeIf { it.coords.isAttached } ?: return false
        val display = g.value.displayOffset(e.offset)
        val line = g.layout.getLineForOffset(display) + direction
        val x = g.layout.getHorizontalPosition(display, usePrimaryDirection = true)
        val target = when {
            line < 0 -> geometry[g.ordinal - 1]?.takeIf { it.coords.isAttached }?.let { it to it.layout.lineCount - 1 }
            line >= g.layout.lineCount -> geometry[g.ordinal + 1]?.takeIf { it.coords.isAttached }?.let { it to 0 }
            else -> g to line
        } ?: return false
        val (tg, tl) = target
        val offset = tg.layout.getOffsetForPosition(Offset(x, (tg.layout.getLineTop(tl) + tg.layout.getLineBottom(tl)) / 2f))
        val next = endpoint(tg, offset)
        val s = start ?: return false
        val en = end ?: return false
        if (isStart && next.compareTo(en) < 0) start = next
        else if (!isStart && next.compareTo(s) > 0) end = next
        else return false
        return true
    }

    private fun glyphHeightAt(isStart: Boolean): Float {
        val e = (if (isStart) start else end) ?: return 0f
        val g = geometry[e.ordinal] ?: return 0f
        val line = g.layout.getLineForOffset(edgeChar(g, e, isStart))
        return g.glyphBottom(line) - g.glyphTop(line)
    }

    private fun edgeChar(g: TextGeometry, e: ReaderSelectionEndpoint, isStart: Boolean): Int {
        val length = g.layout.layoutInput.text.length
        val display = g.value.displayOffset(e.offset)
        return (if (isStart) display else display - 1).coerceIn(0, (length - 1).coerceAtLeast(0))
    }

    /** Where an endpoint's handle hangs from (glyph bottom at the endpoint), in page coordinates. */
    fun handlePoint(isStart: Boolean): Offset? {
        val e = (if (isStart) start else end) ?: return null
        val page = page?.takeIf { it.isAttached } ?: return null
        val g = geometry[e.ordinal]?.takeIf { it.coords.isAttached } ?: return null
        if (g.layout.layoutInput.text.isEmpty()) return null
        val char = edgeChar(g, e, isStart)
        val box = g.layout.getBoundingBox(char)
        val rtl = g.layout.getBidiRunDirection(char) == ResolvedTextDirection.Rtl
        val x = if (isStart == rtl) box.right else box.left
        return page.localPositionOf(g.coords, Offset(x, g.glyphBottom(g.layout.getLineForOffset(char))))
    }

    /** Glyph top of the first selected line and glyph bottom of the last, in page coordinates. */
    fun bounds(): Pair<Float, Float>? {
        val s = start ?: return null
        val e = end ?: return null
        val page = page?.takeIf { it.isAttached } ?: return null
        val top = geometry[s.ordinal]?.takeIf { it.coords.isAttached }?.let { g ->
            page.localPositionOf(g.coords, Offset(0f, g.glyphTop(g.layout.getLineForOffset(edgeChar(g, s, true))))).y
        } ?: Float.NEGATIVE_INFINITY
        val bottom = handlePoint(false)?.y ?: Float.POSITIVE_INFINITY
        return top to bottom
    }
}

/**
 * The word at [offset], skipping punctuation and spaces: a press past the end of a short
 * line («أما بعد:») lands on the colon, and the reader meant the word before it.
 */
internal fun wordNear(text: String, offset: Int, boundary: (Int) -> TextRange): TextRange? {
    if (text.isEmpty()) return null
    val at = offset.coerceIn(0, text.lastIndex)
    for (step in 0..3) for (candidate in listOf(at - step, at + step).distinct()) {
        if (candidate !in text.indices) continue
        val word = boundary(candidate)
        if (word.end <= word.start || (word.start until word.end).none { text[it].isLetterOrDigit() }) continue
        // Compose's word keeps touching punctuation («بعد:», «((صحيح»); trim it, never the marks.
        fun edge(c: Char) = !c.isLetterOrDigit() && c.category != CharCategory.NON_SPACING_MARK
        var start = word.start
        var end = word.end
        while (edge(text[start])) start++
        while (edge(text[end - 1])) end--
        return TextRange(start, end)
    }
    return null
}

/** The word edge nearer to [offset]. Handles move by word, so a selection never splits a letter from its marks. */
internal fun nearestBoundary(offset: Int, word: TextRange): Int =
    if (offset - word.start <= word.end - offset) word.start else word.end

/** Draws the selection band behind one block's text, trimmed per line to the glyphs. */
internal fun DrawScope.drawSelectionBand(g: TextGeometry, range: IntRange, color: Color) {
    val length = g.layout.layoutInput.text.length
    val from = g.value.displayOffset(range.first).coerceIn(0, length)
    val to = g.value.displayOffset(range.last + 1).coerceIn(0, length)
    if (to <= from) return
    val path: Path = g.layout.getPathForRange(from, to)
    for (line in g.layout.getLineForOffset(from)..g.layout.getLineForOffset((to - 1).coerceAtLeast(from))) {
        clipRect(0f, g.glyphTop(line), size.width, g.glyphBottom(line)) { drawPath(path, color) }
    }
}

/** Both handles: a 2dp stem from the glyph bottom and a small disc. */
internal fun DrawScope.drawSelectionHandles(selection: PageSelection, color: Color, drop: Float, radius: Float, stem: Float) {
    for (isStart in listOf(true, false)) {
        val point = selection.handlePoint(isStart) ?: continue
        if (point.y < -drop || point.y > size.height + drop) continue
        drawRect(color, topLeft = Offset(point.x - stem / 2, point.y), size = androidx.compose.ui.geometry.Size(stem, drop))
        drawCircle(color, radius, center = point + Offset(0f, drop))
    }
}

/**
 * One gesture detector for the whole page.
 * - Tap: [onTap] (links and buttons inside the text consume theirs first).
 * - Hold still for [longPressMillis]: select the word, then drag to grow the selection.
 * - Touch a handle: move that end.
 * The list keeps every scroll; only a press that became a selection takes the drag from it.
 */
internal fun Modifier.pageSelectionGestures(
    selection: PageSelection,
    longPressMillis: Long,
    touchSlop: () -> Float,
    handleRadius: Float,
    handleDrop: Float,
    haptics: HapticFeedback,
    onTap: (Offset) -> Unit,
): Modifier = pointerInput(selection) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val handle = selection.handleAt(down.position, handleRadius, handleDrop)
        if (handle != null) {
            down.consume()
            selection.beginHandle(handle, down.position)
            trackDrag(selection, down.id.value)
            return@awaitEachGesture
        }
        var outcome = 0 // 0 = still deciding, 1 = tap, 2 = scroll or consumed by a child
        val held = withTimeoutOrNull(longPressMillis) {
            while (outcome == 0) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                when {
                    change == null -> outcome = 2
                    !change.pressed -> outcome = if (change.isConsumed) 2 else 1
                    change.isConsumed || (change.position - down.position).getDistance() > touchSlop() -> outcome = 2
                }
            }
        }
        if (outcome == 1) { onTap(down.position); return@awaitEachGesture }
        if (outcome == 2 || held != null) return@awaitEachGesture
        if (!selection.beginWord(currentEvent.changes.firstOrNull { it.id == down.id }?.position ?: down.position)) return@awaitEachGesture
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        trackDrag(selection, down.id.value)
    }
}

/** Takes every move from the list (Initial pass) until the finger lifts. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.trackDrag(selection: PageSelection, id: Long) {
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change: PointerInputChange = event.changes.firstOrNull { it.id.value == id } ?: break
            change.consume()
            if (!change.pressed) break
            if (change.position != change.previousPosition) selection.drag(change.position)
        }
    } finally {
        selection.endDrag()
    }
}

/**
 * Edge-scroll speed for a held end, -1..1: zero outside the edge bands, easing in with depth
 * (squared) so the page starts gently instead of jumping to full speed; full past the edge.
 */
internal fun selectionEdgeSpeed(y: Float, height: Int, edge: Float): Float {
    if (height <= 0 || edge <= 0f || !y.isFinite()) return 0f
    val band = minOf(edge, height / 2f)
    val depth = when {
        y < band -> -(band - y) / band
        y > height - band -> (y - (height - band)) / band
        else -> return 0f
    }.coerceIn(-1f, 1f)
    return depth * kotlin.math.abs(depth)
}

/**
 * The same edge scroll for Compose's own SelectionContainer (poems): a finger held past a
 * long press, or dragging while the list itself isn't scrolling, is a handle being dragged,
 * so near the top or bottom edge the list scrolls under it like the book page does.
 */
@androidx.compose.runtime.Composable
internal fun Modifier.selectionEdgeScroll(list: androidx.compose.foundation.lazy.LazyListState): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var held by androidx.compose.runtime.remember { mutableStateOf<Offset?>(null) }
    var height by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(held != null) {
        if (held == null) return@LaunchedEffect
        val edge = with(density) { 96.dp.toPx() }
        val maxSpeed = with(density) { 1600.dp.toPx() }
        var last = 0L
        while (true) androidx.compose.runtime.withFrameNanos { now ->
            val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(1f / 30f)
            last = now
            val speed = held?.let { selectionEdgeSpeed(it.y, height, edge) } ?: 0f
            if (speed != 0f && dt > 0f) list.dispatchRawDelta(speed * maxSpeed * dt)
        }
    }
    return onSizeChanged { height = it.height }.pointerInput(list) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val longPressAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
            var dragged = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                dragged = dragged || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                held = change.position.takeIf { (dragged || change.uptimeMillis >= longPressAt) && !list.isScrollInProgress }
            }
            held = null
        }
    }
}
