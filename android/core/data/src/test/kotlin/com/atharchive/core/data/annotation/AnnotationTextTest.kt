package com.atharchive.core.data.annotation

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationTextTest {
    @Test
    fun verseCopyUsesAnEllipsisWhileOffsetsRemainInStoredText() {
        val id = AnnotationBlockId(7, 77)
        val capture = AnnotationText.capture(
            blocks = listOf(
                AnnotationTextBlock(
                    id = id,
                    ordinal = 1,
                    semanticText = "صدر البيت\nعجز البيت",
                    copyStyle = AnnotationCopyStyle.Verse,
                ),
            ),
            start = AnnotationEndpoint(id, ordinalHint = 1, offset = 0),
            end = AnnotationEndpoint(id, ordinalHint = 1, offset = "صدر البيت\nعجز البيت".length),
        )

        assertEquals("صدر البيت … عجز البيت", capture.quotedText)
    }

    @Test
    fun captureCarriesReattachmentMetadataAndBoundsContextTo48Utf16Units() {
        val startId = AnnotationBlockId(4, 44)
        val endId = AnnotationBlockId(5, 55)
        val startText = "م".repeat(60) + "البداية"
        val endText = "النهاية" + "ن".repeat(60)

        val capture = AnnotationText.capture(
            blocks = listOf(
                AnnotationTextBlock(
                    id = startId,
                    ordinal = 40,
                    semanticText = startText,
                    fingerprint = 404,
                    chapterAnchor = "chapter-2",
                    printedPage = 17,
                ),
                AnnotationTextBlock(
                    id = endId,
                    ordinal = 41,
                    semanticText = endText,
                    fingerprint = 505,
                    chapterAnchor = "chapter-2",
                    printedPage = 18,
                ),
            ),
            start = AnnotationEndpoint(startId, ordinalHint = 40, offset = 60),
            end = AnnotationEndpoint(endId, ordinalHint = 41, offset = 7),
        )

        assertEquals(404, capture.startFingerprint)
        assertEquals(505, capture.endFingerprint)
        assertEquals("م".repeat(48), capture.prefixContext)
        assertEquals("ن".repeat(48), capture.suffixContext)
        assertEquals("chapter-2", capture.chapterAnchor)
        assertEquals(17, capture.printedPage)
    }

    @Test
    fun selectionPreservesUtf16TextAndUsesOneSeparatorAcrossRenderableBlocks() {
        val startBlock = AnnotationBlockId(hi = 1, lo = 11)
        val pageBreak = AnnotationBlockId(hi = 2, lo = 22)
        val endBlock = AnnotationBlockId(hi = 3, lo = 33)

        val capture = AnnotationText.capture(
            blocks = listOf(
                AnnotationTextBlock(startBlock, ordinal = 7, semanticText = "أَب😀"),
                AnnotationTextBlock(pageBreak, ordinal = 8, semanticText = null),
                AnnotationTextBlock(endBlock, ordinal = 9, semanticText = "قال الحق"),
            ),
            start = AnnotationEndpoint(startBlock, ordinalHint = 7, offset = 1),
            end = AnnotationEndpoint(endBlock, ordinalHint = 9, offset = 2),
        )

        assertEquals("َب😀\n\nقا", capture.quotedText)
    }

    @Test
    fun conciseShareAppendsCatalogAttributionWithoutChangingTheQuotation() {
        val endpoint = AnnotationEndpoint(AnnotationBlockId(1, 11), ordinalHint = 7, offset = 0)
        val capture = AnnotationCapture(endpoint, endpoint, "إنَّما الأعمال بالنيات")

        val shared = AnnotationText.share(
            capture = capture,
            source = AnnotationSource(
                title = "صحيح البخاري",
                author = "محمد بن إسماعيل البخاري",
                volume = 1,
                printedPage = 6,
                stableUrl = "https://athar.example/books/bukhari#opening",
            ),
            style = AnnotationShareStyle.Concise,
        )

        assertEquals(
            "إنَّما الأعمال بالنيات\n\n" +
                "— صحيح البخاري، محمد بن إسماعيل البخاري، ج١، ص٦\n" +
                "https://athar.example/books/bukhari#opening",
            shared,
        )
    }
}
