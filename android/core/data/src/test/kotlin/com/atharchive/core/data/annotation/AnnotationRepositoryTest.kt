package com.atharchive.core.data.annotation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationRepositoryTest {
    @Test
    fun recoveryPreservesAnEditMadeDuringContentLookupEvenWithTheSameTimestamp() = runBlocking {
        for (single in listOf(false, true)) {
            val store = MemoryAnnotationStore()
            val started = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val repository = AnnotationRepository(store, content = AnnotationContentSource {
                started.complete(Unit)
                resume.await()
                listOf(AnnotationTextBlock(AnnotationBlockId(1, 2), 30, "قبلنص محفوظبعد", 10, "opening", 7))
            }, nowMillis = { 100L }, newId = { "note-1" })
            repository.save("book-1", AnnotationKind.Note, capture("نص محفوظ"), noteText = "old")
            val recovery = async {
                if (single) listOf(repository.reattach("note-1")) else repository.reattachEntity("book-1")
            }
            started.await()
            repository.edit("note-1", "new", 3)
            resume.complete(Unit)
            assertEquals("new", recovery.await().single().noteText)
            assertEquals("new", store.find("note-1")?.noteText)
            assertEquals(3, store.find("note-1")?.colorId)
        }
    }

    @Test
    fun recoveryDoesNotResurrectANoteDeletedDuringContentLookup() = runBlocking {
        for (single in listOf(false, true)) {
            val store = MemoryAnnotationStore()
            val repository = AnnotationRepository(store, content = AnnotationContentSource {
                store.delete("note-1")
                listOf(AnnotationTextBlock(AnnotationBlockId(1, 2), 30, "قبلنص محفوظبعد", 10, "opening", 7))
            }, newId = { "note-1" })
            repository.save("book-1", AnnotationKind.Note, capture("نص محفوظ"))
            if (single) {
                try {
                    repository.reattach("note-1")
                    org.junit.Assert.fail("deleted annotation must be unavailable")
                } catch (_: IllegalArgumentException) { }
            } else {
                assertEquals(emptyList<Annotation>(), repository.reattachEntity("book-1"))
            }
            assertEquals(null, store.find("note-1"))
        }
    }

    @Test
    fun stableIdWithShiftedTextRecoversOffsetsInsteadOfHighlightingTheWrongCharacters() = runBlocking {
        val store = MemoryAnnotationStore()
        val repository = AnnotationRepository(store, content = AnnotationContentSource {
            listOf(AnnotationTextBlock(AnnotationBlockId(1, 2), 3, "جديدقبلنص محفوظبعد", 10, "opening", 7))
        }, newId = { "note-1" })
        repository.save("book-1", AnnotationKind.Highlight, capture("نص محفوظ"))
        val attached = repository.reattach("note-1")
        assertEquals(7, attached.start.offset)
        assertEquals(15, attached.end.offset)
        assertEquals(AttachmentState.Exact, attached.state)
    }

    @Test
    fun openingWithoutAnnotationsDoesNotReadTheBook() = runBlocking {
        val repository = AnnotationRepository(MemoryAnnotationStore(), content = AnnotationContentSource {
            error("an unannotated book must not be materialized")
        })
        assertEquals(emptyList<Annotation>(), repository.reattachEntity("book-1"))
    }

    @Test
    fun reattachFallsBackToPrintedPageAndMarksTheLocationApproximate() = runBlocking {
        val store = MemoryAnnotationStore()
        val pageBlock = AnnotationTextBlock(
            id = AnnotationBlockId(70, 71),
            ordinal = 80,
            semanticText = "نص الصفحة بعد تحديث كبير",
            fingerprint = 999,
            chapterAnchor = "opening",
            printedPage = 7,
        )
        val repository = AnnotationRepository(
            store = store,
            content = AnnotationContentSource { listOf(pageBlock) },
            nowMillis = { 200L },
            newId = { "note-1" },
        )
        val original = repository.save("book-1", AnnotationKind.Note, capture("نص محفوظ"))

        val attached = repository.reattach("note-1")

        assertEquals(AttachmentState.Approximate, attached.state)
        assertEquals(original.start.blockId, attached.start.blockId)
        assertEquals(original.start.offset, attached.start.offset)
        assertEquals(pageBlock.ordinal, attached.start.ordinalHint)
        assertEquals("نص محفوظ", attached.quotedText)
    }

    @Test
    fun reattachNeverDeletesAnAnnotationThatLostItsPlace() = runBlocking {
        val store = MemoryAnnotationStore()
        val repository = AnnotationRepository(
            store = store,
            content = AnnotationContentSource {
                listOf(
                    AnnotationTextBlock(
                        id = AnnotationBlockId(70, 71),
                        ordinal = 80,
                        semanticText = "فصل ما زال موجودًا",
                        fingerprint = 999,
                        chapterAnchor = "opening",
                        printedPage = 99,
                    ),
                )
            },
            nowMillis = { 200L },
            newId = { "note-1" },
        )
        val original = repository.save("book-1", AnnotationKind.Note, capture("نص محفوظ"))

        val attached = repository.reattach("note-1")

        assertEquals(AttachmentState.Orphaned, attached.state)
        assertEquals(original.start, attached.start)
        assertEquals(listOf("note-1"), repository.observe().first().map(Annotation::id))
    }

    @Test
    fun reattachRecoversChangedBlockIdsFromFingerprintAndContext() = runBlocking {
        val store = MemoryAnnotationStore()
        val replacementId = AnnotationBlockId(90, 91)
        val repository = AnnotationRepository(
            store = store,
            content = AnnotationContentSource {
                listOf(
                    AnnotationTextBlock(
                        id = replacementId,
                        ordinal = 45,
                        semanticText = "جديدقبلنص محفوظبعد",
                        fingerprint = 10,
                        chapterAnchor = "opening",
                        printedPage = 7,
                    ),
                )
            },
            nowMillis = { 200L },
            newId = { "note-1" },
        )
        repository.save("book-1", AnnotationKind.Note, capture("نص محفوظ"))

        val attached = repository.reattach("note-1")

        assertEquals(AttachmentState.Exact, attached.state)
        assertEquals(replacementId, attached.start.blockId)
        assertEquals(7, attached.start.offset)
        assertEquals(15, attached.end.offset)
        assertEquals("نص محفوظ", attached.quotedText)
    }

    @Test
    fun reattachUsesExactBlockIdsAndRefreshesOrdinalHints() = runBlocking {
        val store = MemoryAnnotationStore()
        val blocks = listOf(
            AnnotationTextBlock(
                id = AnnotationBlockId(1, 2),
                ordinal = 30,
                semanticText = "قبلنص محفوظبعد",
                fingerprint = 10,
                chapterAnchor = "opening",
                printedPage = 7,
            ),
        )
        val repository = AnnotationRepository(
            store = store,
            content = AnnotationContentSource { blocks },
            nowMillis = { 200L },
            newId = { "note-1" },
        )
        repository.save("book-1", AnnotationKind.Note, capture("نص محفوظ"))

        val attached = repository.reattach("note-1")

        assertEquals(AttachmentState.Exact, attached.state)
        assertEquals(30, attached.start.ordinalHint)
        assertEquals(30, attached.end.ordinalHint)
        assertEquals(3, attached.start.offset)
    }

    @Test
    fun editPreservesCreationAndDeleteRemovesTheAnnotation() = runBlocking {
        var now = 100L
        val store = MemoryAnnotationStore()
        val repository = AnnotationRepository(store, nowMillis = { now }, newId = { "note-1" })
        val original = repository.save("book-1", AnnotationKind.Note, capture("فائدة"))

        now = 200L
        val edited = repository.edit(original.id, noteText = "شرح جديد", colorId = 3)

        assertEquals(100L, edited.createdAt)
        assertEquals(200L, edited.updatedAt)
        assertEquals("شرح جديد", edited.noteText)
        assertEquals(3, edited.colorId)

        repository.delete(original.id)
        assertEquals(emptyList<Annotation>(), repository.observe().first())
    }

    @Test
    fun saveIsObservableAndCanBeFilteredByKind() = runBlocking {
        val store = MemoryAnnotationStore()
        val ids = ArrayDeque(listOf("highlight-1", "note-1"))
        val repository = AnnotationRepository(
            store = store,
            nowMillis = { 123L },
            newId = { ids.removeFirst() },
        )
        val capture = capture("نص محفوظ")

        repository.save("book-1", AnnotationKind.Highlight, capture)
        repository.save("book-1", AnnotationKind.Note, capture, noteText = "تعليقي")

        val notes = repository.observe(AnnotationFilter(kind = AnnotationKind.Note)).first()
        assertEquals(listOf("note-1"), notes.map(Annotation::id))
        assertEquals("تعليقي", notes.single().noteText)
        assertEquals(AttachmentState.Exact, notes.single().state)
    }

    private fun capture(text: String): AnnotationCapture {
        val endpoint = AnnotationEndpoint(AnnotationBlockId(1, 2), ordinalHint = 3, offset = 3)
        return AnnotationCapture(
            start = endpoint,
            end = endpoint.copy(offset = endpoint.offset + text.length),
            quotedText = text,
            startFingerprint = 10,
            endFingerprint = 10,
            prefixContext = "قبل",
            suffixContext = "بعد",
            chapterAnchor = "opening",
            printedPage = 7,
        )
    }
}

private class MemoryAnnotationStore : AnnotationStore {
    private val annotations = MutableStateFlow<List<Annotation>>(emptyList())

    override fun observe(): Flow<List<Annotation>> = annotations

    override suspend fun find(id: String): Annotation? = annotations.value.find { it.id == id }

    override suspend fun upsert(annotation: Annotation) {
        annotations.value = annotations.value.filterNot { it.id == annotation.id } + annotation
    }

    override suspend fun delete(id: String) {
        annotations.value = annotations.value.filterNot { it.id == id }
    }

    override suspend fun updateIfUnchanged(expected: Annotation, recovered: Annotation): Annotation? {
        val current = find(expected.id)
        return if (current == expected) recovered.also { upsert(it) } else current
    }
}
