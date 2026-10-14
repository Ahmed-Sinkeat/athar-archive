package com.atharchive.core.data.annotation

data class AnnotationBlockId(val hi: Long, val lo: Long)

data class AnnotationEndpoint(
    val blockId: AnnotationBlockId,
    val ordinalHint: Int,
    /** UTF-16 code-unit index into the block's semantic text. */
    val offset: Int,
)

enum class AnnotationCopyStyle {
    Prose,
    Verse,
}

data class AnnotationTextBlock(
    val id: AnnotationBlockId,
    val ordinal: Int,
    /** Null for semantic-free blocks such as printed-page markers. */
    val semanticText: String?,
    val fingerprint: Long = 0,
    val chapterAnchor: String = "",
    val printedPage: Int? = null,
    val copyStyle: AnnotationCopyStyle = AnnotationCopyStyle.Prose,
)

data class AnnotationCapture(
    val start: AnnotationEndpoint,
    val end: AnnotationEndpoint,
    val quotedText: String,
    val startFingerprint: Long = 0,
    val endFingerprint: Long = 0,
    val prefixContext: String = "",
    val suffixContext: String = "",
    val chapterAnchor: String = "",
    val printedPage: Int? = null,
)

data class AnnotationSource(
    val title: String,
    val author: String,
    val volume: Int?,
    val printedPage: Int?,
    val stableUrl: String,
)

enum class AnnotationShareStyle {
    Concise,
}

object AnnotationText {
    fun capture(
        blocks: List<AnnotationTextBlock>,
        start: AnnotationEndpoint,
        end: AnnotationEndpoint,
    ): AnnotationCapture {
        val startIndex = blocks.indexOfFirst { it.id == start.blockId }
        val endIndex = blocks.indexOfFirst { it.id == end.blockId }
        require(startIndex >= 0 && endIndex >= startIndex) { "selection endpoints are not in document order" }

        val startBlock = blocks[startIndex]
        val endBlock = blocks[endIndex]
        val startText = requireNotNull(startBlock.semanticText) { "start block has no semantic text" }
        val endText = requireNotNull(endBlock.semanticText) { "end block has no semantic text" }
        require(start.offset in 0..startText.length && end.offset in 0..endText.length) {
            "selection offset is outside its endpoint block"
        }

        val selected = blocks.subList(startIndex, endIndex + 1).mapIndexedNotNull { index, block ->
            val text = block.semanticText ?: return@mapIndexedNotNull null
            val from = if (index == 0) start.offset else 0
            val to = if (index == endIndex - startIndex) end.offset else text.length
            require(from in 0..text.length && to in from..text.length) {
                "selection offset is outside block ${block.ordinal}"
            }
            val exactSlice = text.substring(from, to)
            when (block.copyStyle) {
                AnnotationCopyStyle.Prose -> exactSlice
                AnnotationCopyStyle.Verse -> exactSlice.replace("\n", " … ")
            }
        }
        return AnnotationCapture(
            start = start,
            end = end,
            quotedText = selected.joinToString("\n\n"),
            startFingerprint = startBlock.fingerprint,
            endFingerprint = endBlock.fingerprint,
            prefixContext = startText.substring(0, start.offset).takeLast(CONTEXT_UNITS),
            suffixContext = endText.substring(end.offset).take(CONTEXT_UNITS),
            chapterAnchor = startBlock.chapterAnchor,
            printedPage = startBlock.printedPage,
        )
    }

    fun share(
        capture: AnnotationCapture,
        source: AnnotationSource,
        style: AnnotationShareStyle,
    ): String {
        require(style == AnnotationShareStyle.Concise)
        val location = buildList {
            source.volume?.let { add("ج${it.toArabicIndicDigits()}") }
            source.printedPage?.let { add("ص${it.toArabicIndicDigits()}") }
        }
        val attribution = buildList {
            add(source.title)
            add(source.author)
            addAll(location)
        }.joinToString("، ")
        return "${capture.quotedText}\n\n— $attribution\n${source.stableUrl}"
    }
}

private const val CONTEXT_UNITS = 48

private fun Int.toArabicIndicDigits(): String =
    toString().map { digit ->
        if (digit in '0'..'9') '٠' + (digit - '0') else digit
    }.joinToString("")
