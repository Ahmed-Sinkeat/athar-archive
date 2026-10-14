package com.atharchive.feature.reader

/** Display hides marks; annotation/copy offsets always address the unchanged source. */
internal class SelectionText(val source: String, showTashkeel: Boolean) {
    private val sourceOffsets = IntArray(source.length + 1)
    val text = buildString {
        source.forEachIndexed { index, char ->
            if (showTashkeel || (char !in '\u064B'..'\u0652' && char != '\u0670')) {
                sourceOffsets[length] = index
                append(char)
            }
        }
        sourceOffsets[length] = source.length
    }

    fun sourceOffset(displayOffset: Int): Int = sourceOffsets[displayOffset.coerceIn(0, text.length)]
    fun displayOffset(sourceOffset: Int): Int = sourceOffsets.binarySearch(sourceOffset.coerceIn(0, source.length), 0, text.length + 1)
        .let { if (it >= 0) it else -it - 1 }
}

/** [pageLabel]: a page break just before this block — drawn above it, never part of the text. */
internal data class SelectionSegment(
    val block: ReaderBlock, val value: SelectionText, val start: Int, val end: Int, val pageLabel: String? = null,
)

internal data class SelectionDocument(
    val text: String,
    val segments: List<SelectionSegment>,
) {
    fun endpoint(offset: Int, end: Boolean = false): ReaderSelectionEndpoint? {
        if (offset !in 0..text.length) return null
        val segment = if (end) segments.lastOrNull { it.start < offset }
            else segments.firstOrNull { it.end > offset }
        val boundarySegment = segment ?: when (offset) {
            0 -> segments.firstOrNull()
            text.length -> segments.lastOrNull()
            else -> null
        } ?: return null
        return ReaderSelectionEndpoint(boundarySegment.block.id, boundarySegment.block.ordinal ?: return null,
            boundarySegment.value.sourceOffset((offset - boundarySegment.start).coerceIn(0, boundarySegment.value.text.length)))
    }

    fun offset(endpoint: ReaderSelectionEndpoint): Int? {
        val segment = segments.find { it.block.id == endpoint.blockId } ?: return null
        if (endpoint.offset !in 0..segment.value.source.length) return null
        return segment.start + segment.value.displayOffset(endpoint.offset)
    }

    fun selection(rawStart: Int, rawEnd: Int): ReaderSelection? {
        val first = minOf(rawStart, rawEnd)
        val last = maxOf(rawStart, rawEnd)
        if (first < 0 || last > text.length || last <= first) return null
        val start = endpoint(first) ?: return null
        val end = endpoint(last, end = true) ?: return null
        val startIndex = segments.indexOfFirst { it.block.id == start.blockId }
        val endIndex = segments.indexOfFirst { it.block.id == end.blockId }
        val picked = segments.subList(startIndex, endIndex + 1)
        val selectedText = buildString {
            picked.forEachIndexed { index, segment ->
                val from = if (index == 0) start.offset else 0
                val to = if (index == picked.lastIndex) end.offset else segment.value.source.length
                val exact = segment.value.source.substring(from, to)
                if (index > 0) append(if (runsOnAcrossPage(picked[index - 1], segment, this)) " " else "\n\n")
                append(if (segment.block is ReaderBlock.Verse) exact.replace("\n", " … ") else exact)
            }
        }
        if (selectedText.isEmpty()) return null
        return ReaderSelection(start, end, selectedText)
    }

    companion object {
        fun create(blocks: List<ReaderBlock>, showTashkeel: Boolean = true): SelectionDocument {
            val text = StringBuilder()
            var pageLabel: String? = null
            val segments = buildList {
                blocks.forEach { block ->
                    if (block is ReaderBlock.PageBreak) pageLabel = block.label()
                    val source = block.selectionText() ?: return@forEach
                    val value = SelectionText(source, showTashkeel)
                    if (isNotEmpty()) text.append('\n')
                    val start = text.length
                    text.append(value.text)
                    add(SelectionSegment(block, value, start, text.length, pageLabel))
                    pageLabel = null
                }
            }
            return SelectionDocument(text.toString(), segments)
        }
    }
}

/**
 * A paragraph the page break cut mid-sentence carries on with a space, not a blank line.
 * ponytail: "ends a sentence" is the last mark only; a closing bracket after a stop reads as mid-sentence.
 */
private fun runsOnAcrossPage(previous: SelectionSegment, next: SelectionSegment, sofar: CharSequence): Boolean =
    next.pageLabel != null &&
        previous.block is ReaderBlock.Paragraph && next.block is ReaderBlock.Paragraph &&
        sofar.trimEnd().lastOrNull()?.let { it !in SENTENCE_ENDS } == true

// . ! ? : ; and the Arabic question mark, full stop and semicolon.
private const val SENTENCE_ENDS = ".!?:;\u061F\u06D4\u061B"

internal fun ReaderBlock.selectionText(): String? = when (this) {
    is ReaderBlock.Heading -> text
    is ReaderBlock.Paragraph -> text
    is ReaderBlock.Quote -> text
    is ReaderBlock.Verse -> listOfNotNull(sadr, ajuz).joinToString("\n")
    is ReaderBlock.PageBreak -> null
    is ReaderBlock.FootnoteBody -> text
}

/** Same wording as the reader's page divider. */
internal fun ReaderBlock.PageBreak.label(): String {
    fun arabic(n: Int) = n.toString().map { '\u0660' + (it - '0') }.joinToString("")
    return if (volume != null) "ج${arabic(volume)} · ص${arabic(page)}" else "ص${arabic(page)}"
}
