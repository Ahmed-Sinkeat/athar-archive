package com.atharchive.ui

import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.atharchive.feature.reader.ReaderScreen
import com.atharchive.feature.poemreader.PoemReaderScreen
import com.atharchive.feature.reader.ReaderSearchTarget
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.navigation.AtharDestination
import kotlinx.coroutines.launch
import androidx.navigation3.runtime.EntryProviderScope

/** The readers: a book and a poem, each full-screen. */
internal fun EntryProviderScope<NavKey>.readerEntries(app: AppNav) = with(app) {
        // The reader owns the whole screen: no bottom navigation inside a book.
        entry<ReaderRoute> { route ->
            val quoteColors = com.atharchive.feature.reader.rememberReaderColors()
            val readerEmitted = collectEmitted(route.bookId) { contentViewModel.readerState(route.bookId) }
            val readerState = readerEmitted?.value
            val readerWindow = remember(route.bookId) { contentViewModel.readerWindow(route.bookId) }
            val openProgress by contentViewModel.openProgress.collectAsStateWithLifecycle()
            val loadProgress = openProgress?.takeIf { it.first == route.bookId }?.second
            LaunchedEffect(route.bookId, route.targetOrdinal) {
                // Any path that opens a poem as a book (links, meanings, lists) lands in the poem reader instead.
                if (contentViewModel.isPoem(route.bookId)) return@LaunchedEffect replaceRoute(route, PoemReaderRoute(route.bookId, route.targetOrdinal))
                // A work opens in the edition last chosen for it; a jump to a place opens as asked.
                val edition = if (route.targetOrdinal == null) contentViewModel.editionToOpen(route.bookId) else route.bookId
                if (edition != route.bookId) replaceRoute(route, ReaderRoute(edition))
                else contentViewModel.openReader(route.bookId, route.targetOrdinal)
            }
            DisposableEffect(route.bookId) {
                onDispose { contentViewModel.closeReader(route.bookId) }
            }
            // "Not emitted yet" is loading, not missing: the flow's first value can wait
            // behind a write, and «غير متاح» used to flash on every slow open.
            if (readerEmitted == null) Column(Modifier.fillMaxSize()) {
                IconButton(onClick = goBack) { Icon(AtharIcons.Back, "رجوع") }
                com.atharchive.ui.components.AtharLoading(
                    "جارٍ تحميل النص…", Modifier.padding(top = 120.dp), tag = "reader_loading", progress = loadProgress,
                )
            } else if (readerState == null) Column(Modifier.fillMaxSize()) {
                IconButton(onClick = goBack) { Icon(AtharIcons.Back, "رجوع") }
                com.atharchive.ui.components.AtharEmptyState(
                    "المصدر غير متاح بعد", "جارٍ تحميل الفهرس؛ إن استمر ذلك فتحقّق من الاتصال وحدّث المحتوى.",
                    "reader_source_unavailable", 24.dp,
                )
                TextButton(onClick = { contentViewModel.sync(); contentViewModel.openReader(route.bookId, route.targetOrdinal) }) { Text("إعادة المحاولة") }
            }
            val authorId by androidx.compose.runtime.produceState<String?>(null, route.bookId) { value = contentViewModel.authorOf(route.bookId) }
            val updated by androidx.compose.runtime.produceState(false, route.bookId) { value = updatedSinceSeen(context, route.bookId, contentViewModel.textVersion(route.bookId)) }
            val downloadTransfer by remember(route.bookId) { contentViewModel.transferOf(route.bookId) }.collectAsStateWithLifecycle(null)
            readerState?.let { state ->
              androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                ReaderScreen(
                    state = state,
                    window = readerWindow,
                    loadProgress = loadProgress,
                    initialOrdinal = route.targetOrdinal,
                    onSearch = { query, mode, partial -> contentViewModel.searchInBook(route.bookId, query, mode, partial) },
                    bookSaved = route.bookId in retainedIds,
                    onDownloadForSearch = { contentViewModel.downloadForSearch(route.bookId) },
                    downloadTransfer = downloadTransfer,
                    updatedSinceLastRead = updated,
                    onOpenAuthor = authorId?.let { id -> { backStack.add(PersonsRoute(id)) } },
                    onShareBook = { shareText(context, listOf(state.bookTitle, state.author, state.sourceUrl).filter(String::isNotBlank).joinToString("\n")) },
                    onLocateOnline = { hit, q -> contentViewModel.locateOnline(route.bookId, hit.url, q, hit.excerpt, hit.pg) },
                    onLoadPassage = { hit -> contentViewModel.inBookPassage(route.bookId, hit) },
                    onLoadFootnotes = { ids -> contentViewModel.footnoteBodies(route.bookId, ids) },
                    recentQueries = recentQueries,
                    onRememberSearch = contentViewModel::rememberSearch,
                    onPageJump = { page, volume -> contentViewModel.findPrintedPage(route.bookId, page, volume) },
                    onAnchorJump = { contentViewModel.findHeadingAnchor(route.bookId, it) },
                    onLoadSelectionBlocks = { contentViewModel.selectionBlocks(route.bookId, it) },
                    onRemoveHighlight = contentViewModel::deleteAnnotation,
                    onEditHighlightNote = { openExcerptNote(it) },
                    onOpenInKannashah = { kannashahFocus = it; navigateTo(AtharDestination.Kannashah) },
                    onCopyCited = { selection ->
                        val (vol, page) = contentViewModel.printedPlace(route.bookId, minOf(selection.start.ordinal, selection.end.ordinal))
                        copyText(context, citedText(selection.quotedText, state.bookTitle, state.author,
                            vol.takeIf { state.volumeStarts.isNotEmpty() }, page, state.sourceUrl, state.volumeUnit))
                    },
                    onPlayAudio = { contentViewModel.playWorkAudio(route.bookId, it.id) },
                    playback = { playbackState },
                    onToggleAudio = contentViewModel.playback::toggle,
                    onSpeed = contentViewModel.playback::speed,
                    onRepeat = contentViewModel.playback::repeat,
                    onSeekMs = contentViewModel.playback::seekTo,
                    onOpenEntity = { backStack.add(ReaderRoute(it)) },
                    onEdition = { id ->
                        state.editions.firstOrNull { it.primary }?.let { contentViewModel.chooseEdition(it.id, id) }
                        replaceRoute(route, ReaderRoute(id))
                    },
                    onBack = goBack,
                    onCopySelection = { selection ->
                        copyText(context, selection.quotedText)
                    },
                    onShareSelection = { selection, asImage ->
                        // Same source line as «نسخ مع التذييل»: the volume and page say where in the book it is.
                        val (vol, page) = contentViewModel.printedPlace(route.bookId, minOf(selection.start.ordinal, selection.end.ordinal))
                        val cited = citedText(selection.quotedText, state.bookTitle, state.author,
                            vol.takeIf { state.volumeStarts.isNotEmpty() }, page, state.sourceUrl, state.volumeUnit)
                        if (asImage) shareQuoteImage(context, selection.quotedText, cited, quoteColors, selection.verses) else shareText(context, cited)
                    },
                    onSaveSelection = { selection ->
                        contentViewModel.saveSelection(route.bookId, selection)
                    },
                    onToggleBookmark = { ordinal ->
                        contentViewModel.toggleBookmark(route.bookId, ordinal)
                    },
                    onPositionChange = { contentViewModel.onReaderPosition(route.bookId, it) },
                    onPositionWithOffset = { ordinal, offset -> contentViewModel.onReaderPosition(route.bookId, ordinal, offset) },
                    settings = readerSettings,
                    onSettingsChange = onReaderSettingsChange,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    initialSearchTarget = if (
                        route.targetOrdinal != null && route.matchStart != null && route.matchEnd != null
                    ) {
                        ReaderSearchTarget(route.targetOrdinal, route.matchStart, route.matchEnd)
                    } else {
                        null
                    },
                )
              }
            }
        }
        entry<PoemReaderRoute> { route ->
            val poemEmitted = collectEmitted("${route.poemId}#${route.targetOrdinal}") { contentViewModel.poemReaderState(route.poemId, route.targetOrdinal) }
            val openProgress by contentViewModel.openProgress.collectAsStateWithLifecycle()
            val loadProgress = openProgress?.takeIf { it.first == route.poemId }?.second
            val poemState = poemEmitted?.value
            LaunchedEffect(route.poemId) { contentViewModel.openReader(route.poemId, route.targetOrdinal) }
            DisposableEffect(route.poemId) {
                onDispose { contentViewModel.closeReader(route.poemId) }
            }
            if (poemEmitted == null) Column(Modifier.fillMaxSize()) {
                IconButton(onClick = goBack) { Icon(AtharIcons.Back, "رجوع") }
                com.atharchive.ui.components.AtharLoading("جارٍ تحميل القصيدة…", Modifier.padding(top = 120.dp), tag = "poem_loading", progress = loadProgress)
            } else if (poemState == null) UnavailableRoute("القصيدة غير متاحة بعد", goBack) { contentViewModel.sync(); contentViewModel.openReader(route.poemId, route.targetOrdinal) }
            val poetId by androidx.compose.runtime.produceState<String?>(null, route.poemId) { value = contentViewModel.authorOf(route.poemId) }
            val updated by androidx.compose.runtime.produceState(false, route.poemId) { value = updatedSinceSeen(context, route.poemId, contentViewModel.textVersion(route.poemId)) }
            val downloadTransfer by remember(route.poemId) { contentViewModel.transferOf(route.poemId) }.collectAsStateWithLifecycle(null)
            poemState?.let { state -> PoemReaderScreen(
                    state = state,
                    loadProgress = loadProgress,
                    onBack = goBack,
                    onOpenAuthor = poetId?.let { id -> { backStack.add(PersonsRoute(id)) } },
                    onDownload = { contentViewModel.downloadForSearch(route.poemId) }.takeUnless { route.poemId in retainedIds },
                    downloadTransfer = downloadTransfer,
                    updatedSinceLastRead = updated,
                    settings = readerSettings,
                    onSettingsChange = onReaderSettingsChange,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    playback = playbackState,
                    onPlayAudio = { contentViewModel.playWorkAudio(route.poemId, it.id) },
                    onToggleAudio = contentViewModel.playback::toggle,
                    onSpeed = contentViewModel.playback::speed,
                    onRepeat = contentViewModel.playback::repeat,
                    onSeekMs = contentViewModel.playback::seekTo,
                    onToggleBookmark = { contentViewModel.toggleBookmark(route.poemId, it) },
                    onSaveVerse = { contentViewModel.saveBlock(route.poemId, it) },
                    onRemoveVerse = { contentViewModel.removeSavedBlock(route.poemId, it) },
                    onVerseNote = { scope.launch { openExcerptNote(contentViewModel.savedBlockId(route.poemId, it)) } },
                    onVerseInKannashah = { scope.launch {
                        contentViewModel.savedBlockId(route.poemId, it)?.let { id -> kannashahFocus = id; navigateTo(AtharDestination.Kannashah) }
                    } },
                    initialOrdinal = route.targetOrdinal,
                    match = route.match,
                    matchHint = route.hint,
                    onPositionChange = { contentViewModel.onReaderPosition(route.poemId, it) },
                    onRetry = { contentViewModel.openReader(route.poemId, route.targetOrdinal) },
                ) }
        }
}
