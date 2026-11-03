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
import androidx.compose.ui.geometry.Rect
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
    var pointer: Offset? by mutableStateOf(null)
        private set

    /** What the loupe magnifies while a handle moves: the text at that end. */
    fun loupeSource(): Offset {
        val moving = movingStart ?: return Offset.Unspecified
        if (!dragging) return Offset.Unspecified
        return handlePoint(moving)?.let { it - Offset(0f, glyphHeightAt(moving) / 2f) } ?: Offset.Unspecified
    }

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
        return Hit(g, g.layout.offsetAt(Offset(bestLocal.x, y)))
    }

    /** The ordinal and source offset of the text exactly under [point], for every placed block there; empty in a gap. */
    // ponytail: every match, not the first — a block scrolled off can keep stale coordinates over the tapped one.
    fun sourcesAt(point: Offset): List<Pair<Int, Int>> {
        val page = page?.takeIf { it.isAttached } ?: return emptyList()
        return geometry.values.filter { g ->
            g.coords.isAttached && g.coords.localPositionOf(page, point).y in 0f..g.coords.size.height.toFloat()
        }.map { g -> g.ordinal to g.value.sourceOffset(g.layout.offsetAt(g.coords.localPositionOf(page, point))) }
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

    /** The handle under [point], if any: true = start, false = end. The whole stem and disc count, padded by [radius]. */
    fun handleAt(point: Offset, radius: Float, drop: Float): Boolean? {
        if (!active) return null
        val candidates = listOf(true, false).mapNotNull { isStart ->
            handlePoint(isStart)?.let { isStart to (Offset(it.x, point.y.coerceIn(it.y, it.y + drop)) - point).getDistance() }
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
        val x = g.layout.boxAt(display.coerceIn(0, (g.layout.layoutInput.text.length - 1).coerceAtLeast(0))).center.x
        val target = when {
            line < 0 -> geometry[g.ordinal - 1]?.takeIf { it.coords.isAttached }?.let { it to it.layout.lineCount - 1 }
            line >= g.layout.lineCount -> geometry[g.ordinal + 1]?.takeIf { it.coords.isAttached }?.let { it to 0 }
            else -> g to line
        } ?: return false
        val (tg, tl) = target
        val offset = tg.layout.offsetAt(Offset(x, (tg.layout.getLineTop(tl) + tg.layout.getLineBottom(tl)) / 2f))
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
        val box = g.layout.boxAt(char)
        val rtl = g.layout.getBidiRunDirection(char) == ResolvedTextDirection.Rtl
        val x = if (isStart == rtl) box.right else box.left
        return page.localPositionOf(g.coords, Offset(x, g.glyphBottom(g.layout.getLineForOffset(char))))
    }

    /** The selection when it is one word inside one laid-out block; «معنى» shows only then. */
    fun singleWord(): String? {
        val s = start ?: return null
        val e = end ?: return null
        if (s.ordinal != e.ordinal) return null
        val source = geometry[s.ordinal]?.value?.source ?: return null
        if (s.offset < 0 || e.offset > source.length || s.offset >= e.offset) return null
        return source.substring(s.offset, e.offset).trim().takeIf { w -> w.isNotEmpty() && w.none(Char::isWhitespace) }
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
    val first = g.layout.getLineForOffset(from)
    val last = g.layout.getLineForOffset((to - 1).coerceAtLeast(from))
    for (line in first..last) {
        val (left, right) = selectionBandSpan(g.layout, line, from, to, first, last, size.width)
        if (right > left) drawRect(color, Offset(left, g.glyphTop(line)), androidx.compose.ui.geometry.Size(right - left, g.glyphBottom(line) - g.glyphTop(line)))
    }
}

/**
 * One line's band, measured from the glyphs (see [boxAt]); not getPathForRange, which on a
 * justified line stopped a word short of the first line's end (أشرف، آله).
 * ponytail: one span per line, so a Latin run inside an Arabic end line is covered whole.
 */
internal fun selectionBandSpan(layout: androidx.compose.ui.text.TextLayoutResult, line: Int, from: Int, to: Int, first: Int, last: Int, width: Float): Pair<Float, Float> {
    val lineStart = layout.getLineStart(line)
    val lineEnd = layout.getLineEnd(line, visibleEnd = true)
    val rtl = layout.getParagraphDirection(lineStart) == ResolvedTextDirection.Rtl
    // The reading-start edge and reading-end edge of the selected part of this line.
    val begin = if (line == first) edgeOf(layout, from, start = true, rtl) else if (rtl) width else 0f
    val finish = when {
        line != last -> if (rtl) 0f else width          // the selection runs on past this line
        to >= lineEnd -> lineEdge(layout, line, rtl)    // ends with the line: its last glyph
        else -> edgeOf(layout, to, start = false, rtl)
    }
    return minOf(begin, finish) to maxOf(begin, finish)
}

private fun edgeOf(layout: androidx.compose.ui.text.TextLayoutResult, offset: Int, start: Boolean, rtl: Boolean): Float {
    val box = layout.boxAt(if (start) offset else offset - 1)
    return if (start == rtl) box.right else box.left
}

/** Where the line's glyphs end in reading order, justification included. */
private fun lineEdge(layout: androidx.compose.ui.text.TextLayoutResult, line: Int, rtl: Boolean): Float {
    val end = layout.getLineEnd(line, visibleEnd = true)
    if (end <= layout.getLineStart(line)) return if (rtl) layout.getLineRight(line) else layout.getLineLeft(line)
    return edgeOf(layout, end, start = false, rtl)
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
            trackDrag(selection, down.id.value, haptics)
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
        trackDrag(selection, down.id.value, haptics)
    }
}

/** Takes every move from the list (Initial pass) until the finger lifts. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.trackDrag(selection: PageSelection, id: Long, haptics: HapticFeedback) {
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change: PointerInputChange = event.changes.firstOrNull { it.id.value == id } ?: break
            change.consume()
            if (!change.pressed) break
            if (change.position != change.previousPosition) {
                val before = selection.start to selection.end
                selection.drag(change.position)
                // A light tick each time an end steps to the next word.
                if (before != (selection.start to selection.end)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
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
 * A justified line's stretch. Android draws such a line spread to the full width, but reports
 * positions (getBoundingBox, getHorizontalPosition, getOffsetForPosition) at the natural spacing —
 * measured on Android 15 (Redmi 12): off by ~65 px at the end of an Arabic line, so a press on
 * its last word took the one before. Every inner space is wider by the same [extra].
 */
private class Stretch(val start: Int, val end: Int, val extra: Float, val rtl: Boolean, val text: String) {
    fun before(offset: Int) = (start until offset).count { text[it] == ' ' } * extra
}

private fun TextLayoutResult.stretch(line: Int): Stretch? {
    if (layoutInput.style.textAlign != androidx.compose.ui.text.style.TextAlign.Justify) return null
    val text = layoutInput.text.text
    val start = getLineStart(line)
    val end = getLineEnd(line, visibleEnd = true)
    // A paragraph's last line is never stretched.
    if (end <= start || line == lineCount - 1 || text.getOrNull(getLineEnd(line) - 1) == '\n') return null
    val spaces = (start until end).count { text[it] == ' ' }
    if (spaces == 0) return null
    val rtl = getParagraphDirection(start) == ResolvedTextDirection.Rtl
    val first = getBoundingBox(start)
    val last = getBoundingBox(end - 1)
    val natural = if (rtl) first.right - last.left else last.right - first.left
    val extra = (size.width - natural) / spaces
    // A platform that already reports stretched positions leaves nothing to add.
    return if (extra > 0.5f) Stretch(start, end, extra, rtl, text) else null
}

/** [offset]'s glyph box where it is actually drawn, a justified line's stretch included. */
internal fun TextLayoutResult.boxAt(offset: Int): Rect {
    val box = getBoundingBox(offset)
    val st = stretch(getLineForOffset(offset))?.takeIf { offset in it.start until it.end } ?: return box
    val shift = st.before(offset)
    val widen = if (st.text[offset] == ' ') st.extra else 0f
    return if (st.rtl) Rect(box.left - shift - widen, box.top, box.right - shift, box.bottom)
    else Rect(box.left + shift, box.top, box.right + shift + widen, box.bottom)
}

/** The text offset at [point], by the drawn (stretched) glyphs. */
internal fun TextLayoutResult.offsetAt(point: Offset): Int {
    val line = getLineForVerticalPosition(point.y)
    val st = stretch(line) ?: return getOffsetForPosition(point)
    // ponytail: a scan of the line's ~60 characters per touch; cache per line if it ever shows in a trace.
    var best = st.start
    var bestDistance = Float.MAX_VALUE
    for (i in st.start until st.end) {
        val b = boxAt(i)
        val d = if (point.x < b.left) b.left - point.x else if (point.x > b.right) point.x - b.right else 0f
        // Past a glyph's middle in reading order means the offset after it.
        if (d < bestDistance) { bestDistance = d; best = if ((point.x < b.center.x) == st.rtl) i + 1 else i }
        if (d == 0f) break
    }
    return best
}
