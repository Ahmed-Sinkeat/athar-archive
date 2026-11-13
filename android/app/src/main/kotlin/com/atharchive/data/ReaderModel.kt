package com.atharchive.data

import com.atharchive.ui.components.arabicDigits
import androidx.tracing.Trace
import com.atharchive.core.data.annotation.Annotation
import com.atharchive.core.data.annotation.AnnotationBlockId
import com.atharchive.core.data.annotation.AnnotationCopyStyle
import com.atharchive.core.data.annotation.AnnotationEndpoint
import com.atharchive.core.data.annotation.AnnotationFilter
import com.atharchive.core.data.annotation.AnnotationKind
import com.atharchive.core.data.annotation.AnnotationRepository
import com.atharchive.core.data.annotation.AnnotationText
import com.atharchive.core.data.annotation.AnnotationTextBlock
import com.atharchive.core.data.annotation.AttachmentState
import com.atharchive.core.data.repository.visitBookFrames
import com.atharchive.core.data.repository.spans
import com.atharchive.feature.reader.ReaderHighlight
import com.atharchive.core.data.content.AudioReference
import com.atharchive.core.data.db.content.ChapterEntity
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.db.user.ReadingPositionEntity
import com.atharchive.core.data.repository.ReaderPrefetchSession
import com.atharchive.core.data.repository.PersonalLibraryRepository
import com.atharchive.core.data.repository.StoredBlockAttributes
import com.atharchive.core.data.repository.attributes
import com.atharchive.core.data.repository.toCatalogEntry
import com.atharchive.feature.poemreader.PoemAudioUi
import com.atharchive.feature.poemreader.PoemReaderUiState
import com.atharchive.feature.poemreader.VerseCue
import com.atharchive.feature.poemreader.VerseUi
import com.atharchive.feature.reader.BookAudioUi
import com.atharchive.feature.reader.ReaderBlock
import com.atharchive.feature.reader.ReaderBenefit
import com.atharchive.feature.reader.ReaderBookmark
import com.atharchive.feature.reader.ReaderSelection
import com.atharchive.feature.reader.ReaderUiState
import com.atharchive.feature.reader.TocEntry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop

/**
 * Reader (3.1): a book's or poem's state, its text window and footnotes, opening and closing,
 * the saved reading position and prefetch, finding a printed page or heading, editions, and what
 * the reader saves (a selection, a verse, a bookmark). Built by [AtharContentViewModel] on its
 * scope; the screens reach it through there.
 */
internal class ReaderModel(
    private val scope: CoroutineScope,
    private val content: ContentAccess,
    private val personalLibrary: PersonalLibraryRepository,
    private val annotationRepository: AnnotationRepository,
    private val errorHandler: CoroutineExceptionHandler,
    private val errors: MutableSharedFlow<String>,
    /** After a book opens: the cache may have grown. */
    private val onOpened: () -> Unit,
) {
    private val userDao = content.userDatabase.userDataDao()
    private val catalogDao = content.contentDatabase.catalogDao()
    private val importDao = content.contentDatabase.importDao()
    private val readerFlows = ConcurrentHashMap<String, Flow<ReaderUiState?>>()
    private val readerJobs = ConcurrentHashMap<String, Job>()
    private val readerWindows = ConcurrentHashMap<String, com.atharchive.feature.reader.ReaderWindow>()
    private val poemFlows = ConcurrentHashMap<String, Flow<PoemReaderUiState?>>()
    private val prefetchSessions = ConcurrentHashMap<String, ReaderPrefetchSession>()
    private val prefetchedFrames = ConcurrentHashMap<String, Int>()
    /**
     * The book (entity, contents, editions) is built once per change of those; a saved reading position
     * or a new annotation only copies it. The text comes through [readerWindow] and footnotes by id.
     */
    fun readerState(entityId: String): Flow<ReaderUiState?> = readerFlows.getOrPut(entityId) {
        val book = combine(catalogDao.observeEntity(entityId), importDao.observeChapters(entityId)) { entity, chapters ->
            entity?.toReaderUi(chapters)?.copy(
                editions = catalogDao.editionsFor(entity.editionOf ?: entity.id).takeIf { it.size > 1 }.orEmpty()
                    .sortedBy { it.editionOf != null }.map {
                        com.atharchive.feature.reader.ReaderEdition(
                            it.id, it.edition?.takeIf(String::isNotBlank) ?: it.title,
                            current = it.id == entity.id, primary = it.editionOf == null,
                        )
                    },
            )
        }
        combine(
            book,
            userDao.observeReadingPosition(entityId).map { it?.ordinalHint to it?.offsetInBlock }.distinctUntilChanged(),
            annotationRepository.observe(AnnotationFilter(entityId = entityId)),
        ) { state, (ordinal, offset), saved ->
            state?.copy(readingPositionIndex = ordinal ?: 0, readingOffset = offset ?: 0)
                ?.withAnnotations(saved, saved.filter { it.kind == AnnotationKind.Bookmark && it.quotedText.isBlank() }
                    .associate { it.id to importDao.blockAt(entityId, it.start.ordinalHint)?.text.orEmpty() })
        }
    }

    /** Footnote bodies by id, for a page's notes block and the marker callout. */
    suspend fun footnoteBodies(entityId: String, ids: List<String>): Map<String, ReaderBlock.FootnoteBody> =
        ids.distinct().chunked(500).flatMap { importDao.footnotes(entityId, it) }.sortedBy { ids.indexOf(it.fnId) }
            .mapIndexed { index, note ->
                note.fnId to ReaderBlock.FootnoteBody("fn-${note.fnId}", note.marker ?: arabicNumber(index + 1), note.text, spans = note.spans())
            }.toMap()

    fun readerWindow(entityId: String): com.atharchive.feature.reader.ReaderWindow =
        readerWindows.getOrPut(entityId) {
            val job = SupervisorJob(scope.coroutineContext[Job])
            readerJobs[entityId] = job
            val scope = CoroutineScope(scope.coroutineContext + job)
            com.atharchive.feature.reader.ReaderWindow(
                scope = scope,
                total = { content.blockCount(entityId) },
                load = { range ->
                    val blocks = content.blockRange(entityId, range).map(ContentBlockEntity::toReaderBlock)
                    // A page footer gets its note bodies here, only for the pages loaded.
                    val notes = footnoteBodies(entityId, blocks.filterIsInstance<ReaderBlock.PageNotes>().flatMap { it.footnotes })
                    blocks.map { if (it is ReaderBlock.PageNotes) it.copy(notes = it.footnotes.mapNotNull(notes::get)) else it }
                },
            ).also { window ->
                // A finished or replaced download swaps the text under the reader.
                scope.launch { content.observePackage(entityId).drop(1).collect { window.invalidate() } }
            }
        }

    suspend fun selectionBlocks(entityId: String, range: IntRange): List<ReaderBlock> =
        content.blockRange(entityId, range).map(ContentBlockEntity::toReaderBlock)

    suspend fun savedPoemOrdinal(entityId: String, annotationId: String): Int {
        visitTextFrames(entityId) { false }
        return annotationRepository.reattach(annotationId).start.ordinalHint
    }

    fun poemReaderState(entityId: String, targetOrdinal: Int? = null): Flow<PoemReaderUiState?> = poemFlows.getOrPut("$entityId#$targetOrdinal") {
        combine(catalogDao.observeEntity(entityId), importDao.observeBlocks(entityId), userDao.observeReadingPosition(entityId),
            annotationRepository.observe(AnnotationFilter(entityId = entityId))) { entity, blocks, position, saved ->
            val (marks, benefits) = saved.partition { it.kind == AnnotationKind.Bookmark }
            val ordinals = blocks.associate { AnnotationBlockId(it.blockIdHi, it.blockIdLo) to it.ordinal }
            val reading = position?.let { ordinals[AnnotationBlockId(it.blockIdHi, it.blockIdLo)] }
            val readingOrdinal = reading ?: position?.ordinalHint ?: 0
            // A ديوان opens on one poem only: the one holding the target, else the reading position.
            val poem = entity?.diwanPoem(targetOrdinal ?: readingOrdinal)
            val shown = poem?.let { (range, _) -> blocks.filter { it.ordinal in range } } ?: blocks
            entity?.toPoemReaderUi(shown, poem?.second ?: entity.title, poem?.first?.count() ?: entity.pkgBlocks)?.copy(
                readingOrdinal = readingOrdinal,
                readingPositionReady = position == null || reading != null || blocks.size >= entity.pkgBlocks,
                bookmarks = marks.mapNotNull { ordinals[it.start.blockId] },
                saved = benefits.mapNotNull { ordinals[it.start.blockId] },
            )
        }
    }

    fun openReader(entityId: String, targetOrdinal: Int? = null) {
        // Ended by the reader when its first page shows (the bookOpen benchmark reads it).
        Trace.beginAsyncSection(BookOpenTraceName, 0)
        scope.launch(errorHandler) {
            personalLibrary.recordOpened(entityId)
            val saved = userDao.readingPosition(entityId)
            val stable = saved?.let { importDao.blockByStableId(entityId, it.blockIdHi, it.blockIdLo) }
            val entity = catalogDao.entity(entityId) ?: return@launch
            Analytics.hit("book", entityId)
            val position = (targetOrdinal ?: stable?.ordinal ?: saved?.ordinalHint ?: 0)
                .coerceIn(0, (entity.pkgBlocks - 1).coerceAtLeast(0))
            if (targetOrdinal == null && saved != null && position != saved.ordinalHint) {
                userDao.saveReadingPosition(saved.copy(ordinalHint = position, chapterAnchor = stable?.chapterAnchor ?: saved.chapterAnchor))
            }
            try {
                content.open(entityId, position)
                if (entity.coll == "poem") visitTextFrames(entityId) { false }
            }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { errors.tryEmit(error.message ?: "تعذّر تحميل النص") }
            runCatching { annotationRepository.reattachEntity(entityId) }
            onOpened()
        }
    }

    private suspend fun visitTextFrames(entityId: String, visit: suspend (IntRange) -> Boolean) {
        val total = requireNotNull(catalogDao.entity(entityId)?.pkgBlocks) { "تعذّر العثور على النص" }
        visitBookFrames(total, frameEnd = { ordinal ->
            content.open(entityId, ordinal)
            val frame = requireNotNull(importDao.frames(entityId).firstOrNull {
                ordinal in it.firstBlockOrdinal until it.firstBlockOrdinal + it.blockCount
            }) { "تعذّر تحميل جزء من النص" }
            frame.firstBlockOrdinal + frame.blockCount - 1
        }, visit = visit)
    }

    /** The text a reader would open now: the downloaded package, else the catalog's (read-through pages follow it). */
    suspend fun textVersion(entityId: String): String? =
        importDao.state(entityId)?.localPackageHash ?: catalogDao.entity(entityId)?.pkgHash

    suspend fun findPrintedPage(entityId: String, page: Int, volume: Int?): Int? {
        importDao.printedPageOrdinal(entityId, page, volume)?.let { return it }
        var found: Int? = null
        visitTextFrames(entityId) {
            found = importDao.printedPageOrdinal(entityId, page, volume)
            found != null
        }
        return found
    }

    /** (volume, printed page) of a block, for «نسخ مع التذييل». */
    suspend fun printedPlace(entityId: String, ordinal: Int): Pair<Int?, Int?> =
        importDao.blockAt(entityId, ordinal).let { it?.vol to it?.printedPage }

    suspend fun findHeadingAnchor(entityId: String, anchor: String): Int? {
        importDao.headingAnchorOrdinal(entityId, anchor)?.let { return it }
        var found: Int? = null
        visitTextFrames(entityId) {
            found = importDao.headingAnchorOrdinal(entityId, anchor)
            found != null
        }
        return found
    }
    suspend fun saveSelection(entityId: String, selection: ReaderSelection, kind: AnnotationKind = AnnotationKind.Highlight) {
            val first = minOf(selection.start.ordinal, selection.end.ordinal)
            val last = maxOf(selection.start.ordinal, selection.end.ordinal)
            val blocks = importDao.blockWindow(entityId, first, last)
            val startBlock = requireNotNull(blocks.find { it.stableId() == selection.start.blockId }) { "موضع بداية التحديد غير متاح" }
            val endBlock = requireNotNull(blocks.find { it.stableId() == selection.end.blockId }) { "موضع نهاية التحديد غير متاح" }
            val capture = AnnotationText.capture(
                blocks = blocks.map(ContentBlockEntity::toAnnotationTextBlock),
                start = AnnotationEndpoint(
                    AnnotationBlockId(startBlock.blockIdHi, startBlock.blockIdLo),
                    startBlock.ordinal,
                    selection.start.offset,
                ),
                end = AnnotationEndpoint(
                    AnnotationBlockId(endBlock.blockIdHi, endBlock.blockIdLo),
                    endBlock.ordinal,
                    selection.end.offset,
                ),
            )
            // The reader joins a sentence running over a page with a space, the capture with a blank
            // line (and a verse's halves with « … »): compare the words, not how parts are joined.
            require(sameWords(capture.quotedText, selection.quotedText)) { "تغيّر النص أو لم يكتمل تحميله؛ أعد تحديد المقطع قبل حفظه" }
            annotationRepository.save(entityId, kind, capture)
    }

    /** A whole block into الكناشة: a poem saves a verse at a time, not a dragged selection. */
    suspend fun saveBlock(entityId: String, ordinal: Int) {
        val block = requireNotNull(importDao.blockAt(entityId, ordinal)) { "النص لم يُحمّل بعد" }
        val id = AnnotationBlockId(block.blockIdHi, block.blockIdLo)
        annotationRepository.save(entityId, AnnotationKind.Highlight, AnnotationText.capture(
            blocks = listOf(block.toAnnotationTextBlock()),
            start = AnnotationEndpoint(id, block.ordinal, 0),
            end = AnnotationEndpoint(id, block.ordinal, block.text.length),
        ))
    }

    /** The highlight a saved verse is kept under, for its note. */
    suspend fun savedBlockId(entityId: String, ordinal: Int): String? {
        val block = importDao.blockAt(entityId, ordinal) ?: return null
        val blockId = AnnotationBlockId(block.blockIdHi, block.blockIdLo)
        return annotationRepository.observe(AnnotationFilter(entityId = entityId)).first()
            .firstOrNull { it.kind != AnnotationKind.Bookmark && it.start.blockId == blockId }?.id
    }

    /** Takes a verse out of الكناشة: every highlight that starts in it. */
    suspend fun removeSavedBlock(entityId: String, ordinal: Int) {
        val block = requireNotNull(importDao.blockAt(entityId, ordinal)) { "النص لم يُحمّل بعد" }
        val blockId = AnnotationBlockId(block.blockIdHi, block.blockIdLo)
        annotationRepository.observe(AnnotationFilter(entityId = entityId)).first()
            .filter { it.kind != AnnotationKind.Bookmark && it.start.blockId == blockId }
            .forEach { annotationRepository.delete(it.id) }
    }

    suspend fun toggleBookmark(entityId: String, ordinal: Int) {
        // The top of the screen can be a page divider or a page's notes: mark the text after it.
        val block = importDao.blockWindow(entityId, ordinal, ordinal + BookmarkLookahead).firstOrNull { it.text.isNotBlank() }
            ?: requireNotNull(importDao.blockAt(entityId, ordinal)) { "النص لم يُحمّل بعد" }
        val blockId = AnnotationBlockId(block.blockIdHi, block.blockIdLo)
        val existing = annotationRepository.observe(
            AnnotationFilter(entityId = entityId, kind = AnnotationKind.Bookmark),
        ).first().find { it.start.blockId == blockId || (it.state != AttachmentState.Exact && it.start.ordinalHint == block.ordinal) }
        if (existing != null) {
            annotationRepository.delete(existing.id)
            return
        }
        val endpoint = AnnotationEndpoint(
            blockId = blockId,
            ordinalHint = block.ordinal,
            offset = 0,
        )
        val capture = AnnotationText.capture(listOf(block.toAnnotationTextBlock()), endpoint, endpoint.copy(offset = block.text.length.coerceAtMost(120)))
        annotationRepository.save(entityId, AnnotationKind.Bookmark, capture)
    }
    fun onReaderPosition(entityId: String, ordinal: Int, offset: Int = 0) {
        scope.launch(errorHandler) {
            val entity = catalogDao.entity(entityId) ?: return@launch
            val block = importDao.blockAt(entityId, ordinal) ?: return@launch
            userDao.saveReadingPosition(
                ReadingPositionEntity(
                    entityId = entityId,
                    chapterAnchor = block.chapterAnchor,
                    blockIdHi = block.blockIdHi,
                    blockIdLo = block.blockIdLo,
                    ordinalHint = ordinal,
                    offsetInBlock = offset.coerceIn(0, block.text.length),
                    progressPct = if (entity.pkgBlocks <= 1) 0.0 else ordinal.toDouble() / (entity.pkgBlocks - 1),
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            val frame = importDao.frames(entityId).firstOrNull {
                ordinal in it.firstBlockOrdinal until (it.firstBlockOrdinal + it.blockCount)
            } ?: return@launch
            // First call comes only after the first page is drawn and the reader is idle, so
            // prefetch never competes with the first open. It then re-runs on each new frame.
            if (prefetchedFrames.put(entityId, frame.frameOrdinal) != frame.frameOrdinal) {
                prefetchSessions.remove(entityId)?.close()
                content.prefetchAdjacent(scope, entityId, ordinal)?.let {
                    prefetchSessions[entityId] = it
                }
            }
        }
    }

    /**
     * The edition to open for [entityId]. A work opened by its default edition opens the edition
     * last chosen in «الطبعات», so a reader who switched never has to switch again; a link to a
     * specific edition (a search hit, a non-default id) opens as given.
     */
    suspend fun editionToOpen(entityId: String): String {
        val entity = catalogDao.entity(entityId) ?: return entityId
        if (entity.editionOf != null) return entityId
        val preferred = content.preferredEdition(entityId) ?: return entityId
        val edition = catalogDao.entity(preferred)
        return if (edition != null && edition.catalogPresent && edition.editionOf == entityId) preferred else entityId
    }

    /**
     * Remembers [editionId] as the edition the work [primaryId] opens with from now on. Synchronous
     * on purpose: the reader that replaces this one asks [editionToOpen] straight away.
     */
    fun chooseEdition(primaryId: String, editionId: String) = content.setPreferredEdition(primaryId, editionId)

    fun closeReader(entityId: String) {
        prefetchSessions.remove(entityId)?.close()
        prefetchedFrames.remove(entityId)
        readerJobs.remove(entityId)?.cancel()
        readerWindows.remove(entityId)
        readerFlows.remove(entityId)
        poemFlows.keys.removeIf { it.startsWith("$entityId#") }
    }
}

private fun ContentEntity.toReaderUi(chapters: List<ChapterEntity>): ReaderUiState {
    return ReaderUiState(
        bookTitle = title,
        isBook = coll == "book",
        author = personName.orEmpty(),
        blocks = emptyList(),
        hasPages = pageTo != null,
        sourceUrl = PlayStoreUrl,
        card = runCatching { toCatalogEntry().card }.getOrDefault(emptyList()),
        review = review,
        toc = chapters.multiVolume().let { vols ->
            chapters.map { chapter ->
                val page = chapter.page?.let { "ص ${com.atharchive.ui.components.arabicDigits(it)}" }.orEmpty()
                val label = chapter.vol?.takeIf { vols && page.isNotEmpty() }?.let { "${com.atharchive.feature.reader.volumeLabel(it, volumeUnit)} · $page" } ?: page
                TocEntry(chapter.anchor, chapter.title, chapter.level, chapter.firstOrdinal, label)
            }
        },
        bookmarks = emptyList(),
        benefits = emptyList(),
        audio = audioReferences().map { BookAudioUi(it.id, it.title ?: title, "", "", duration(it.seconds), it.seconds) },
        blockCount = pkgBlocks,
        // volumes come from the contents' chapters; a book whose chapters carry no vol shows no volume menu.
        volumeStarts = chapters.filter { it.vol != null }.groupBy { it.vol!! }
            .map { (vol, list) -> vol to list.minOf { it.firstOrdinal } }.sortedBy { it.first }.takeIf { it.size > 1 }.orEmpty(),
        volumeUnit = volumeUnit,
        pageRange = if (chapters.multiVolume() || (volumes ?: 1) > 1) null else pageTo?.let { (pageFrom ?: 1)..it },
    )
}

private fun ReaderUiState.withAnnotations(annotations: List<Annotation>, previews: Map<String, String> = emptyMap()): ReaderUiState {
    fun chapterFor(annotation: Annotation): String =
        toc.lastOrNull { it.blockIndex <= annotation.start.ordinalHint }?.title.orEmpty()
    fun pageFor(annotation: Annotation): String? = annotation.printedPage?.let { "ص ${arabicNumber(it)}" }
    val bookmarks = annotations.filter { it.kind == AnnotationKind.Bookmark }.map { annotation ->
        val preview = annotation.quotedText.ifBlank { previews[annotation.id].orEmpty().ifBlank { blocks.firstOrNull { it.ordinal == annotation.start.ordinalHint }.readableText().orEmpty() } }
        ReaderBookmark(
            id = annotation.id,
            blockIndex = annotation.start.ordinalHint,
            chapterTitle = chapterFor(annotation),
            preview = preview.take(120),
            pageLabel = pageFor(annotation),
        )
    }
    val benefits = annotations.filter { it.kind != AnnotationKind.Bookmark }.map { annotation ->
        ReaderBenefit(
            id = annotation.id,
            blockIndex = annotation.start.ordinalHint,
            text = annotation.quotedText,
            chapterTitle = chapterFor(annotation),
            pageLabel = pageFor(annotation),
        )
    }
    val highlights = annotations.filter { it.kind != AnnotationKind.Bookmark && it.state == AttachmentState.Exact }
        .map { ReaderHighlight(it.start.ordinalHint, it.end.ordinalHint, it.start.offset, it.end.offset, it.id) }
    return copy(bookmarks = bookmarks, benefits = benefits, highlights = highlights)
}

/** The parser's «…» / «...» / «---» between a verse's halves (src/lib/app-v2/parser.ts HEMISTICH_RE). */
private val HemistichSeparator = Regex("""\s*(?:---|\.\.\.|…)\s*""")

/** The poem of a ديوان holding [ordinal]: its block range and title; null for any other work. */
private fun ContentEntity.diwanPoem(ordinal: Int): Pair<IntRange, String>? {
    if (poemsJson == null) return null
    val poems = toCatalogEntry().poems
    val index = poems.indexOfLast { it.o <= ordinal }.coerceAtLeast(0)
    val poem = poems.getOrNull(index) ?: return null
    return (poem.o until (poems.getOrNull(index + 1)?.o ?: pkgBlocks)) to poem.t
}

private fun ContentEntity.toPoemReaderUi(blocks: List<ContentBlockEntity>, title: String = this.title, expectedBlocks: Int = pkgBlocks): PoemReaderUiState {
    val prose = blocks.none { it.type == "verse" }
    // A باب heading rides on the verse after it, so the list stays one row per verse
    // (indices are what the panel and audio jump to).
    var heading: String? = null
    var headingNote: String? = null
    val verses = mutableListOf<VerseUi>()
    blocks.forEach { block ->
        if (block.type.startsWith('h')) { heading = listOfNotNull(heading, block.text).joinToString(" — "); return@forEach }
        if (!prose && block.type == "p" && (heading != null || verses.isEmpty())) { headingNote = listOfNotNull(headingNote, block.text).joinToString("\n"); return@forEach }
        if (block.type != "verse" && !(prose && block.type == "p")) return@forEach
        val attrs = block.safeAttributes()
        // A verse carrying a footnote mark or emphasis comes as one line (x), separator and all.
        val halves = if (attrs.s == null && block.type == "verse") block.text.split(HemistichSeparator, limit = 2) else null
        verses += VerseUi(
            id = block.stableId(),
            number = attrs.n ?: verses.size + 1,
            sadr = attrs.s ?: halves?.first() ?: block.text,
            ajz = attrs.j ?: halves?.getOrNull(1).orEmpty(),
            ordinal = block.ordinal,
            commentary = attrs.commentary.map { com.atharchive.feature.poemreader.CommentaryUi(it.sourceLabel, it.text) },
            heading = heading,
            headingNote = headingNote,
        )
        heading = null
        headingNote = null
    }
    return PoemReaderUiState(
        card = toCatalogEntry().card,
        title = title,
        poet = personName.orEmpty(),
        sourceUrl = PlayStoreUrl,
        bodyLoaded = blocks.size >= expectedBlocks,
        verses = verses,
        recordings = audioReferences().map { audio ->
            PoemAudioUi(
                id = audio.id,
                title = audio.title ?: title,
                collection = "المنظومات",
                reciter = "",
                durationLabel = duration(audio.seconds),
                durationSeconds = audio.seconds,
                cues = audio.cues.map { VerseCue(it.v, it.t) },
            )
        },
    )
}

internal fun ContentEntity.audioReferences(): List<AudioReference> =
    runCatching { toCatalogEntry().audio }.getOrDefault(emptyList())

private fun ContentBlockEntity.toReaderBlock(): ReaderBlock {
    val id = stableId()
    val attrs = safeAttributes()
    val spans = runCatching { spans() }.getOrDefault(emptyList())
    return when {
        type.startsWith('h') -> ReaderBlock.Heading(
            id = id,
            level = type.drop(1).toIntOrNull() ?: 2,
            text = text,
            anchor = attrs.ha ?: chapterAnchor,
            ordinal = ordinal,
            spans = spans,
        )
        type == "quote" && attrs.footnotes.isEmpty() -> ReaderBlock.Quote(id, text, ordinal, spans)
        type == "verse" -> ReaderBlock.Verse(id, attrs.s ?: text, attrs.j, ordinal, spans, attrs.footnotes)
        type == "notes" -> ReaderBlock.PageNotes(id, attrs.footnotes, ordinal)
        type == "break" -> ReaderBlock.Divider(id, ordinal)
        type == "img" && attrs.src != null -> ReaderBlock.Image(id, attrs.src!!, ordinal)
        type == "page" -> ReaderBlock.PageBreak(id, printedPage ?: 0, vol, ordinal, footnotes = attrs.footnotes)
        else -> ReaderBlock.Paragraph(id, text, attrs.footnotes, ordinal, spans,
            if (type == "li" && attrs.continuation != true) if (attrs.ordered == true) "${attrs.start ?: 1}." else "•" else null,
            quoted = attrs.quoted == true || type == "quote")
    }
}

private fun ContentBlockEntity.toAnnotationTextBlock(): AnnotationTextBlock = AnnotationTextBlock(
    id = AnnotationBlockId(blockIdHi, blockIdLo),
    ordinal = ordinal,
    // The reader leaves page markers, rules and the page-foot notes out of a selection; so does this.
    semanticText = text.takeUnless { type == "page" || type == "break" || type == "notes" || type == "img" },
    fingerprint = fp64,
    chapterAnchor = chapterAnchor,
    printedPage = printedPage,
    copyStyle = if (type == "verse") AnnotationCopyStyle.Verse else AnnotationCopyStyle.Prose,
)

private fun ReaderBlock?.readableText(): String? = when (this) {
    is ReaderBlock.Heading -> text
    is ReaderBlock.Paragraph -> text
    is ReaderBlock.Quote -> text
    is ReaderBlock.Verse -> listOfNotNull(sadr, ajuz).joinToString(" … ")
    is ReaderBlock.FootnoteBody -> text
    is ReaderBlock.PageBreak, is ReaderBlock.PageNotes, is ReaderBlock.Divider, is ReaderBlock.Image, null -> null
}

internal fun ContentBlockEntity.safeAttributes(): StoredBlockAttributes =
    runCatching { attributes() }.getOrDefault(StoredBlockAttributes())

private fun ContentBlockEntity.stableId(): String =
    blockIdHi.toULong().toString(16).padStart(16, '0') +
        blockIdLo.toULong().toString(16).padStart(16, '0')

/** Blocks looked past for a bookmark's text: a divider and a notes footer, with room to spare. */
internal const val BookmarkLookahead = 4

private fun List<ChapterEntity>.multiVolume() = mapNotNull { it.vol }.distinct().size > 1

/** Same text once spacing and the « … » between a verse's halves are set aside. */
internal fun sameWords(a: String, b: String): Boolean {
    fun words(s: String) = s.filterNot { it.isWhitespace() || it == '…' }
    return words(a) == words(b)
}
