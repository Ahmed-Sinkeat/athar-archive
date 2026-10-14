package com.atharchive.feature.reader

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionDocumentTest {
    @Test
    fun aParagraphCutByAPageRunsOnButAFinishedOneKeepsItsBreak() {
        val doc = SelectionDocument.create(listOf(
            ReaderBlock.Paragraph("a", "one two", ordinal = 1),
            ReaderBlock.PageBreak("p1", 9, ordinal = 2),
            ReaderBlock.Paragraph("b", "three.", ordinal = 3),
            ReaderBlock.PageBreak("p2", 10, ordinal = 4),
            ReaderBlock.Paragraph("c", "four", ordinal = 5),
            ReaderBlock.Paragraph("d", "five", ordinal = 6),
        ))
        assertEquals("one two three.\n\nfour\n\nfive", doc.selection(0, doc.text.length)?.quotedText)
    }

    @Test
    fun aPressOnPunctuationOrPastTheLineEndPicksTheWordBeforeIt() {
        val text = "amma baad: then"
        // Letters group into words; anything else is a one-character "word", like ICU.
        val boundary = { i: Int ->
            if (!text[i].isLetterOrDigit()) androidx.compose.ui.text.TextRange(i, i + 1) else {
                var s = i; var e = i
                while (s > 0 && text[s - 1].isLetterOrDigit()) s--
                while (e < text.length && text[e].isLetterOrDigit()) e++
                androidx.compose.ui.text.TextRange(s, e)
            }
        }
        assertEquals(androidx.compose.ui.text.TextRange(5, 9), wordNear(text, 9, boundary))   // the colon
        assertEquals(androidx.compose.ui.text.TextRange(11, 15), wordNear(text, 99, boundary)) // past the end
        assertEquals(androidx.compose.ui.text.TextRange(0, 4), wordNear(text, 2, boundary))
        assertNull(wordNear(":: ", 1, { androidx.compose.ui.text.TextRange(it, it + 1) }))
        // A boundary that swallows touching punctuation is trimmed back to the letters; marks stay.
        assertEquals(androidx.compose.ui.text.TextRange(2, 6), wordNear("((bad\u064E): x", 3, { androidx.compose.ui.text.TextRange(0, 8) }))
        assertEquals(5, nearestBoundary(6, androidx.compose.ui.text.TextRange(5, 9)))
        assertEquals(9, nearestBoundary(8, androidx.compose.ui.text.TextRange(5, 9)))
    }

    @Test
    fun heldHandlesScrollAtBothEdgesAndStopInTheMiddle() {
        val bottom = selectionEdgeStep(990f, 1000, 48, 12)
        val top = selectionEdgeStep(10f, 1000, 48, 12)
        assertTrue(bottom > 0)
        assertEquals(-bottom, top)
        assertEquals(0, selectionEdgeStep(500f, 1000, 48, 12))
        assertEquals(12, selectionEdgeStep(1200f, 1000, 48, 12))
        assertEquals(-12, selectionEdgeStep(-200f, 1000, 48, 12))
        assertEquals(0, selectionEdgeStep(Float.NaN, 1000, 48, 12))
        assertEquals(0, selectionEdgeStep(0f, 0, 48, 12))
        // Repeated stationary edge ticks continue moving instead of stopping at the last MOVE.
        assertEquals(bottom * 90, (1..90).sumOf { selectionEdgeStep(990f, 1000, 48, 12) })
    }

    @Test
    fun followUsesExactAppTokensAndMigratesOnlyTheLegacyPalette() {
        for (app in listOf(com.atharchive.ui.theme.LightExtraColors, com.atharchive.ui.theme.DarkExtraColors)) {
            val text = androidx.compose.ui.graphics.Color(0xFF123456)
            val reader = followedReaderColors(app, text)
            assertEquals(app.canvas, reader.page)
            assertEquals(text, reader.text)
            assertEquals(app.secondaryText, reader.secondary)
            assertEquals(app.accent, reader.accent)
        }
        assertEquals(ReaderPalette.Follow, com.atharchive.data.savedReaderPalette("Dark", false))
        assertEquals(ReaderPalette.Follow, com.atharchive.data.savedReaderPalette(null, true))
        assertEquals(ReaderPalette.Follow, com.atharchive.data.savedReaderPalette("invalid", true))
        assertEquals(ReaderPalette.Warm, com.atharchive.data.savedReaderPalette("Warm", true))
    }

    private fun blocks(first: Int, last: Int) = (first..last).map {
        ReaderBlock.Paragraph("block-$it", "block $it alpha beta gamma", ordinal = it)
    }

    @Test
    fun copyingAcrossAnInvisibleBlockStillIncludesItsExactSource() {
        val document = SelectionDocument.create(listOf(
            ReaderBlock.Paragraph("before", "before", ordinal = 0),
            ReaderBlock.Paragraph("marks", "َُ", ordinal = 1),
            ReaderBlock.Paragraph("after", "after", ordinal = 2),
        ), showTashkeel = false)
        assertEquals("before\n\nَُ\n\nafter", document.selection(0, document.text.length)?.quotedText)
    }

    @Test
    fun collapsedEndpointsAndDocumentEdgesRemainAddressableDuringGrowth() {
        val old = SelectionDocument.create(blocks(20, 40))
        val cursor = old.endpoint(old.text.indexOf("block 30") + 15)!!
        val grown = SelectionDocument.create(blocks(0, 59))
        assertEquals("block-30", grown.endpoint(grown.offset(cursor)!!)!!.blockId)
        assertEquals(15, grown.endpoint(grown.offset(cursor)!!)!!.offset)
        assertEquals("block-20", old.endpoint(0, end = true)?.blockId)
        assertEquals("block-40", old.endpoint(old.text.length)?.blockId)
        assertNull(grown.selection(grown.offset(cursor)!!, grown.offset(cursor)!!))
    }

    @Test
    fun hiddenTashkeelKeepsExactSourceOffsetsAndCopyIncludingMarks() {
        val block = ReaderBlock.Paragraph("arabic", "قَبْل عِلْم بَعْد 😀", ordinal = 0)
        val document = SelectionDocument.create(listOf(block), showTashkeel = false)
        assertEquals("قبل علم بعد 😀", document.text)
        val first = document.text.indexOf("علم")
        val range = document.selection(first, first + 3)!!
        assertEquals("عِلْم", range.quotedText)
        assertEquals(6, range.start.offset)
        assertEquals(11, range.end.offset)
        val shown = SelectionDocument.create(listOf(block))
        val start = shown.offset(range.start)!!
        val end = shown.offset(range.end)!!
        assertEquals(range, shown.selection(start, end))
        val emoji = document.text.indexOf("😀")
        assertEquals("😀", document.selection(emoji, emoji + 2)?.quotedText)
    }

    @Test
    fun partialVersesUseSourceOffsetsAndTheExistingCopySeparator() {
        val verse = ReaderBlock.Verse("verse", "first", "second", ordinal = 0)
        val paragraph = ReaderBlock.Paragraph("paragraph", "tail", ordinal = 1)
        val document = SelectionDocument.create(listOf(verse, paragraph))
        val selected = document.selection(2, document.text.length - 2)!!
        assertEquals("rst … second\n\nta", selected.quotedText)
        assertEquals(2, selected.start.offset)
        assertEquals(2, selected.end.offset)
    }

    @Test
    fun touchedWordIsTheInitialRange() {
        val initial = ReaderSelection(ReaderSelectionEndpoint("block-30", 30, 15),
            ReaderSelectionEndpoint("block-30", 30, 19), "beta")
        val document = SelectionDocument.create(blocks(20, 40))
        assertEquals("beta", document.selection(document.offset(initial.start)!!, document.offset(initial.end)!!)?.quotedText)
    }

    @Test
    fun loadedTextIsNotCappedAtTwentyFiveBlocks() {
        val document = SelectionDocument.create(blocks(0, 59))
        assertEquals(60, document.segments.size)
        val selected = document.selection(0, document.text.length)!!
        assertEquals(0, selected.start.ordinal)
        assertEquals(59, selected.end.ordinal)
    }

    @Test
    fun growingBothEdgesPreservesAPartialRangeAndItsDirectionIndependentText() {
        val old = SelectionDocument.create(blocks(20, 40))
        val beta = old.text.indexOf("block 30 alpha beta") + 15
        val range = old.selection(beta + 4, beta)!!
        val grown = SelectionDocument.create(blocks(0, 59))
        val start = requireNotNull(grown.offset(range.start))
        val end = requireNotNull(grown.offset(range.end))
        assertEquals("beta", grown.selection(start, end)?.quotedText)
        assertEquals(range, grown.selection(end, start))
    }

    @Test
    fun missingEndpointNeverMapsToAnotherParagraph() {
        val range = ReaderSelection(ReaderSelectionEndpoint("missing", 30, 0),
            ReaderSelectionEndpoint("block-31", 31, 4), "missing")
        assertNull(SelectionDocument.create(blocks(20, 40)).offset(range.start))
    }
}
