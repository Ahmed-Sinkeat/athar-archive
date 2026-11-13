package com.atharchive.feature.reader

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import com.atharchive.ui.components.AtharTailPopover
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.atharchive.core.data.content.InlineSpan
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.KitabFontFamily

/**
 * One block, rendered by kind. The hierarchy is carried by size, weight and space —
 * never by a card around the text.
 *
 * Selection is drawn on this same text (see [PageSelection]); the page owns the gestures.
 */
@Composable
internal fun BlockView(
    block: ReaderBlock,
    settings: ReaderSettings,
    colors: ReaderColors,
    highlighted: Boolean,
    isSearchTarget: Boolean,
    searchMatch: IntRange? = null,
    selection: PageSelection? = null,
    highlights: List<ReaderHighlight> = emptyList(),
    onLink: (String) -> Unit = {},
    /** A tapped note number: its note ids and the number's rect in window coordinates. */
    onFootnotes: (List<String>, Rect) -> Unit = { _, _ -> },
    /** The book's [ReaderUiState.volumeUnit], for page-break labels. */
    volumeUnit: String? = null,
) {
    val ranges = highlights.filter { block.ordinal in it.startOrdinal..it.endOrdinal }.map {
        (if (block.ordinal == it.startOrdinal) it.startOffset else 0) until
            (if (block.ordinal == it.endOrdinal) it.endOffset else Int.MAX_VALUE)
    }
    val body = settings.fontSize.sp
    val lineHeight = (settings.fontSize * settings.spacing.multiplier).sp
    // Turath sets prose at 1.75; the spacing setting still scales it (Normal 1.85 → 1.75).
    val proseLineHeight = (settings.fontSize * settings.spacing.multiplier * (1.75f / 1.85f)).sp

    val background = when {
        highlighted -> colors.highlight
        isSearchTarget -> colors.accent.copy(alpha = 0.10f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }

    val source = block.selectionText()
    val value = remember(source, settings.showTashkeel) { source?.let { SelectionText(it, settings.showTashkeel) } }
    val ordinal = block.ordinal
    val placed = remember(block.id) { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    var textLayout by remember(block.id) { mutableStateOf<TextLayoutResult?>(null) }
    fun register() {
        val layout = textLayout ?: return
        val coords = placed[0] ?: return
        if (selection == null || ordinal == null || value == null) return
        // Positioning fires every scroll frame; the coordinates object itself stays the same.
        val known = selection.geometry[ordinal]
        if (known != null && known.layout === layout && known.coords === coords && known.block === block && known.value === value) return
        selection.geometry[ordinal] =TextGeometry(block, ordinal, layout, value, coords)
    }
    fun onLayout(layout: TextLayoutResult) { textLayout = layout; register() }
    // A block leaving the page takes its geometry with it: no stale hit target, no layout kept for the whole book.
    if (selection != null && ordinal != null) androidx.compose.runtime.DisposableEffect(selection, ordinal) {
        onDispose { if (selection.geometry[ordinal]?.block === block) selection.geometry.remove(ordinal) }
    }
    // Highlights, drawn behind the text: «لون» a highlighter-pen stroke, «خط» a rounded line under
    // each line, below the kasra and descenders.
    val underline = if (ranges.isEmpty() || value == null) Modifier else Modifier.drawBehind {
        val layout = textLayout ?: return@drawBehind
        val line = settings.underlineHighlights
        ranges.forEach {
            drawHighlightLine(layout, value.displayOffset(it.first), value.displayOffset(if (it.last == Int.MAX_VALUE) Int.MAX_VALUE else it.last + 1),
                if (line) colors.accent.copy(alpha = 0.8f) else colors.saved, marker = !line)
        }
    }
    // Without selection the text still records where it sits, so a note number can anchor its popover.
    val selectable = underline.then(if (selection == null || ordinal == null || value == null || value.text.isBlank()) Modifier.onPlaced { placed[0] = it } else Modifier
        .onGloballyPositioned { placed[0] = it; register() }
        .semantics {
            onLongClick(label = "تحديد النص") { selection.geometry[ordinal]?.let(selection::beginFirstWord) ?: false }
        }
        .drawBehind {
            val range = selection.rangeIn(ordinal, value.source.length) ?: return@drawBehind
            val geometry = selection.geometry[ordinal] ?: return@drawBehind
            drawSelectionBand(geometry, range, colors.selection)
        })
    val pressable = Modifier.fillMaxWidth()
    // Remembered with a link handler that never changes identity: a fresh lambda made the text
    // unequal on every recomposition, and Compose laid the whole paragraph out again.
    val onLinkState by rememberUpdatedState(onLink)
    val onFootnotesState by rememberUpdatedState(onFootnotes)
    val blockState by rememberUpdatedState(block)
    val valueState by rememberUpdatedState(value)
    val link = remember(block.id) {
        { target: String ->
            if (!target.startsWith(NOTE_LINK)) onLinkState(target) else {
                val id = target.removePrefix(NOTE_LINK)
                // a ref inside a page note has no main text here; its popover opens at the top.
                val rect = noteRect(textLayout, placed[0], valueState, blockState.spans.firstOrNull { it.k == "sup" && it.target == id })
                onFootnotesState(listOf(id), rect)
            }
        }
    }
    @Composable
    fun marked(text: String, match: IntRange?) =
        remember(text, match, colors, block.spans, settings.showTashkeel, settings.footnoteMode) {
            markedText(text, match, colors, block.spans, settings.showTashkeel, link)
        }

    when (block) {
        is ReaderBlock.Heading -> {
            val isKitab = block.level <= 2
            Column(
                modifier = pressable
                    .padding(top = if (isKitab) 34.dp else 24.dp, bottom = 10.dp)
                    .testTag("block_${block.id}"),
                horizontalAlignment = if (isKitab) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                // A printed-book flourish between sections; none above the book's first heading.
                if (isKitab && (ordinal ?: 0) > 0) Text("❁", color = colors.secondary.copy(alpha = 0.7f),
                    fontSize = (settings.fontSize + 2).sp, modifier = Modifier.padding(bottom = 18.dp))
                Text(
                    text = marked(block.text, searchMatch),
                    modifier = selectable,
                    onTextLayout = ::onLayout,
                    color = colors.heading,
                    fontFamily = com.atharchive.ui.theme.LocalReaderFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isKitab) (settings.fontSize + 5).sp else (settings.fontSize + 1).sp,
                    lineHeight = if (isKitab) (settings.fontSize + 14).sp else (settings.fontSize + 10).sp,
                    textAlign = if (isKitab) TextAlign.Center else TextAlign.Start,
                )
                if (isKitab) {
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .width(52.dp)
                            .height(1.dp)
                            .background(colors.divider),
                    )
                }
            }
        }

        is ReaderBlock.Paragraph -> {
            Box(
                modifier = pressable
                    .clip(AtharCorners.Tag)
                    .background(background)
                    .padding(vertical = 7.dp)
                    .testTag("block_${block.id}"),
            ) {
              Row {
                if (block.quoted) Box(Modifier.padding(end = 12.dp, top = 4.dp).width(2.dp)
                    .height((settings.fontSize * 1.6f).dp).background(colors.accent.copy(alpha = 0.55f)))
                block.listMarker?.let { Text(it, color = colors.secondary, modifier = Modifier.padding(end = 8.dp), fontSize = body) }
              Column(Modifier.weight(1f)) {
                Text(
                    text = marked(block.text, searchMatch),
                    modifier = selectable,
                    onTextLayout = ::onLayout,
                    color = colors.text,
                    fontFamily = com.atharchive.ui.theme.LocalReaderFont.current,
                    fontSize = body,
                    lineHeight = proseLineHeight,
                    textAlign = TextAlign.Justify,
                    // first line starts indented like Turath's "تحسين النص"; sp (not em) so PageSelection.stretch can read it
                    style = if (block.quoted || block.listMarker != null) androidx.compose.ui.text.TextStyle.Default
                        else androidx.compose.ui.text.TextStyle(textIndent = TextIndent(firstLine = (settings.fontSize * 1.2f).sp)),
                )
              }
              }
            }
        }

        is ReaderBlock.Quote -> {
            Row(
                modifier = pressable
                    .clip(AtharCorners.Tag)
                    .background(background)
                    .padding(vertical = 10.dp)
                    .testTag("block_${block.id}"),
            ) {
                Box(
                    Modifier
                        .padding(end = 12.dp, top = 4.dp)
                        .width(2.dp)
                        .height((settings.fontSize * 1.6f).dp)
                        .background(colors.accent.copy(alpha = 0.55f)),
                )
                Text(
                    text = marked(block.text, searchMatch),
                    modifier = selectable,
                    onTextLayout = ::onLayout,
                    color = colors.text.copy(alpha = 0.92f),
                    fontFamily = com.atharchive.ui.theme.LocalReaderFont.current,
                    fontSize = (settings.fontSize - 1).sp,
                    lineHeight = proseLineHeight,
                )
            }
        }

        is ReaderBlock.Verse -> {
            Column(
                modifier = pressable
                    .clip(AtharCorners.Tag)
                    .background(background)
                    .padding(vertical = 12.dp)
                    .testTag("block_${block.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val verse = marked(listOfNotNull(block.sadr, block.ajuz).joinToString("\n"), searchMatch)
                // صدر at the reading start, عجز at the far end: the two halves are staggered, as on the poem page.
                // The text is unchanged (a paragraph style only), so selection and search offsets still map.
                val staggered = remember(verse) {
                    val nl = verse.text.indexOf('\n')
                    if (nl < 0) verse else androidx.compose.ui.text.buildAnnotatedString {
                        append(verse)
                        addStyle(androidx.compose.ui.text.ParagraphStyle(textAlign = TextAlign.Start), 0, nl + 1)
                        addStyle(androidx.compose.ui.text.ParagraphStyle(textAlign = TextAlign.End), nl + 1, verse.text.length)
                    }
                }
                Text(
                    text = staggered,
                    modifier = selectable.fillMaxWidth().padding(horizontal = 12.dp), color = colors.poetry,
                    onTextLayout = ::onLayout,
                    fontFamily = colors.poetryFont ?: if (settings.font == com.atharchive.ui.theme.ReaderFont.Kitab) AtharEditorialFontFamily else com.atharchive.ui.theme.LocalReaderFont.current,
                    fontSize = body,
                    lineHeight = lineHeight, textAlign = TextAlign.Center,
                )
            }
        }

        is ReaderBlock.PageBreak -> {
            Column {
                // «أفقي» shows each printed page as a sheet with its number at the foot: no rule in the text.
                if (!settings.continuous) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("block_${block.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (settings.pageMark == PageMark.Line) HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 0.7.dp,
                        color = colors.divider,
                    )
                    if (settings.pageMark == PageMark.Ornament) Text("۞ ", color = colors.accent.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (block.volume != null) {
                            "${volumeLabel(block.volume, volumeUnit)} · ص${arabic(block.page)}"
                        } else {
                            "ص${arabic(block.page)}"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp),
                        color = colors.secondary.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (settings.pageMark == PageMark.Ornament) Text(" ۞", color = colors.accent.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    if (settings.pageMark == PageMark.Line) HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 0.7.dp,
                        color = colors.divider,
                    )
                }
            }
        }

        is ReaderBlock.Divider -> Box(
            Modifier.fillMaxWidth().padding(vertical = 18.dp).testTag("block_${block.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(52.dp).height(1.dp).background(colors.divider))
        }

        is ReaderBlock.Image -> ReaderImage(block.src, colors, Modifier.padding(vertical = 12.dp).testTag("block_${block.id}"))

        // Printed-book style: a short rule, then the page's notes in small type.
        is ReaderBlock.PageNotes -> if (!settings.continuous && settings.footnoteMode == FootnoteMode.Footer && block.notes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 2.dp)
                    .testTag("block_${block.id}"),
            ) {
                HorizontalDivider(Modifier.width(72.dp), thickness = 0.7.dp, color = colors.divider)
                block.notes.forEach { note ->
                    key(note.id) {
                        val text = remember(note, colors, settings.showTashkeel) {
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.accent)) { append("(${note.marker.arabicDigits()}) ") }
                                append(markedText(note.text, null, colors, note.spans, settings.showTashkeel, link))
                            }
                        }
                        Text(
                            text = text,
                            modifier = Modifier.padding(top = 6.dp),
                            color = colors.secondary,
                            fontFamily = com.atharchive.ui.theme.LocalReaderFont.current,
                            fontSize = (settings.fontSize - 4).sp,
                            lineHeight = ((settings.fontSize - 4) * 1.6f).sp,
                        )
                    }
                }
            }
        }

        is ReaderBlock.FootnoteBody -> if (!settings.continuous && settings.footnoteMode == FootnoteMode.Footer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .testTag("block_${block.id}"),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "(${block.marker.arabicDigits()})",
                    color = colors.accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 6.dp, top = 2.dp),
                )
                Text(
                    text = marked(block.text, null),
                    modifier = selectable,
                    onTextLayout = ::onLayout,
                    color = colors.secondary,
                    fontFamily = com.atharchive.ui.theme.LocalReaderFont.current,
                    fontSize = (settings.fontSize - 4).sp,
                    lineHeight = (settings.fontSize * 1.5f).sp,
                )
            }
        }
    }
}

/**
 * Per line of [start, end) (display offsets): a rounded line just above the line's bottom, or with
 * [marker] a highlighter stroke over the letters — chisel-slanted ends and edges that waver a little,
 * as a pen leaves them (the same wave every frame, so nothing shimmers while scrolling).
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHighlightLine(layout: TextLayoutResult, start: Int, end: Int, color: Color, marker: Boolean = false) {
    val to = end.coerceAtMost(layout.layoutInput.text.length)
    if (start >= to) return
    val fontPx = layout.layoutInput.style.fontSize.toPx()
    for (line in layout.getLineForOffset(start)..layout.getLineForOffset(to - 1)) {
        val from = maxOf(start, layout.getLineStart(line))
        val until = minOf(to, layout.getLineEnd(line, visibleEnd = true))
        if (from >= until) continue
        // The drawn glyphs' extent, justification included (see boxAt); right for RTL and mid-run ends.
        val a = layout.boxAt(from)
        val b = layout.boxAt(until - 1)
        val left = minOf(a.left, b.left)
        val right = maxOf(a.right, b.right)
        if (!marker) {
            val y = layout.getLineBottom(line) - 2.dp.toPx()
            drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            continue
        }
        val baseline = layout.getLineBaseline(line)
        drawPath(markerStroke(left - 2.dp.toPx(), right + 2.dp.toPx(), baseline - fontPx * 0.85f, baseline + fontPx * 0.4f, from), color)
    }
}

private fun markerStroke(left: Float, right: Float, top: Float, bottom: Float, seed: Int): androidx.compose.ui.graphics.Path {
    val height = bottom - top
    val slant = height * 0.2f
    val wave = height * 0.04f
    fun edge(x: Float, side: Int) = wave * kotlin.math.sin(x * 0.05f + seed * 1.3f + side * 2.1f)
    val step = height * 0.5f
    return androidx.compose.ui.graphics.Path().apply {
        moveTo(left + slant, top + edge(left, 0))
        var x = left + slant
        while (x < right) { x = minOf(x + step, right); lineTo(x, top + edge(x, 0)) }
        lineTo(right - slant, bottom + edge(right, 1))
        x = right - slant
        while (x > left) { x = maxOf(x - step, left); lineTo(x, bottom + edge(x, 1)) }
        close()
    }
}

/** Prefix that routes a tapped footnote number to the note popover instead of [onLink]. */
private const val NOTE_LINK = "\u0000note:"

/** ﴿ayah﴾, «quote» and ((hadith)). length caps keep a stray opener from colouring a page. */
private val CitationRe = Regex("\uFD3F[^\uFD3E]{1,1500}\uFD3E|\u00AB[^\u00BB]{1,1500}\u00BB|\\(\\([^)]{1,300}\\)\\)")

private fun markedText(
    text: String, match: IntRange?, colors: ReaderColors,
    spans: List<InlineSpan>, showTashkeel: Boolean,
    onLink: (String) -> Unit,
): AnnotatedString {
    val value = SelectionText(text, showTashkeel)
    // Note numbers in Arabic-Indic digits, one char for one, so every offset still holds.
    val shown = value.text.toCharArray()
    spans.filter { it.k == "sup" }.forEach { span ->
        for (i in value.displayOffset(span.s) until value.displayOffset(span.e).coerceAtMost(shown.size)) {
            if (shown[i] in '0'..'9') shown[i] = '\u0660' + (shown[i] - '0')
        }
    }
    return buildAnnotatedString {
        append(String(shown))
        fun style(style: SpanStyle, start: Int, end: Int) {
            val from = value.displayOffset(start)
            val to = value.displayOffset(end)
            if (from < to) addStyle(style, from, to)
        }
        // Like the site: colour from the punctuation already in the text, no markup needed.
        CitationRe.findAll(value.text).forEach {
            val ayah = it.value[0] == '\uFD3F'
            // Styling only: offsets, selection and highlights are unchanged.
            addStyle(
                if (ayah) SpanStyle(color = colors.ayah, fontFamily = colors.ayahFont) else SpanStyle(color = colors.quote),
                it.range.first, it.range.last + 1,
            )
        }
        spans.forEach { span ->
            when (span.k) {
                "strong" -> style(SpanStyle(fontWeight = FontWeight.Bold), span.s, span.e)
                "emphasis" -> style(SpanStyle(fontStyle = FontStyle.Italic), span.s, span.e)
                // Numbers are pressable in every mode: a tap opens the note above the number.
                "sup" -> style(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = AtharTextSize.Small), span.s, span.e)
            }
            val target = span.target?.let { if (span.k == "sup") NOTE_LINK + it else it }
            if (span.k in listOf("link", "entityRef", "sup") && !target.isNullOrBlank()) {
                val start = value.displayOffset(span.s)
                val end = value.displayOffset(span.e)
                if (start < end) addLink(
                    LinkAnnotation.Clickable(target, TextLinkStyles(SpanStyle(color = colors.accent))) { onLink(target) },
                    start, end,
                )
            }
        }
        match?.let { style(SpanStyle(background = colors.accent.copy(alpha = 0.24f), fontWeight = FontWeight.Bold), it.first, it.last + 1) }
    }
}

private fun String.arabicDigits() = map { if (it in '0'..'9') '\u0660' + (it - '0') else it }.joinToString("")

/** The window rect of a note number, from its text's layout; [Rect.Zero] when it isn't laid out. */
private fun noteRect(layout: TextLayoutResult?, coords: LayoutCoordinates?, value: SelectionText?, span: InlineSpan?): Rect {
    if (layout == null || coords == null || !coords.isAttached || value == null || span == null) return Rect.Zero
    val start = value.displayOffset(span.s).coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
    val end = (value.displayOffset(span.e) - 1).coerceIn(start, (layout.layoutInput.text.length - 1).coerceAtLeast(start))
    val a = layout.boxAt(start)
    val b = layout.boxAt(end)
    // RTL: the first digit can sit to the right of the last, so take the min/max.
    val topLeft = coords.localToWindow(Offset(minOf(a.left, b.left), minOf(a.top, b.top)))
    val bottomRight = coords.localToWindow(Offset(maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)))
    return Rect(topLeft, bottomRight)
}

/** The tapped notes, in a small callout above the number (see [AtharTailPopover]). */
@Composable
internal fun FootnoteCallout(
    ids: List<String>,
    at: Rect,
    footnotes: Map<String, ReaderBlock.FootnoteBody>,
    settings: ReaderSettings,
    colors: ReaderColors,
    onLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AtharTailPopover(
        anchor = { at },
        ground = colors.text.copy(alpha = 0.94f),
        onDismiss = onDismiss,
        maxWidth = 320.dp,
        modifier = Modifier.testTag("footnote_popover"),
    ) {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            ids.forEach { id ->
                val note = footnotes[id]
                Text(
                    text = if (note == null) AnnotatedString("الحاشية لم تُحمّل بعد") else buildAnnotatedString {
                        append("(${note.marker.arabicDigits()}) ")
                        append(markedText(note.text, null, colors, note.spans, settings.showTashkeel, onLink))
                    },
                    color = colors.page,
                    fontFamily = com.atharchive.ui.theme.LocalReaderFont.current,
                    fontSize = (settings.fontSize - 3).sp,
                    lineHeight = ((settings.fontSize - 3) * 1.6f).sp,
                )
            }
        }
    }
}
