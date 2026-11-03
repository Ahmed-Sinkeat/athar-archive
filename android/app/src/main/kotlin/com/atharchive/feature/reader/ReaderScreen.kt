package com.atharchive.feature.reader

import com.atharchive.ui.components.SoapTravelIndicator
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.magnifier
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.systemBarsPadding
import com.atharchive.core.data.content.CardField
import com.atharchive.core.data.content.InlineSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.atharchive.ui.components.atharBackdrop
import com.atharchive.ui.components.atharChrome
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import kotlinx.coroutines.async
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    window: ReaderWindow? = null,
    onBack: () -> Unit,    /** How far the text being fetched has come (0..1); null when nothing is in flight. */
    loadProgress: Float? = null,

    onCopySelection: (ReaderSelection) -> Unit,
    onShareSelection: suspend (ReaderSelection) -> Unit,
    onSaveSelection: suspend (ReaderSelection) -> Unit,
    onToggleBookmark: suspend (Int) -> Unit,
    onPositionChange: (Int) -> Unit,
    onPositionWithOffset: ((Int, Int) -> Unit)? = null,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    themeMode: com.atharchive.ui.theme.ThemeMode,
    onThemeModeChange: (com.atharchive.ui.theme.ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    initialSearchTarget: ReaderSearchTarget? = null,
    initialOrdinal: Int? = null,
    onSearch: (suspend (String, com.atharchive.feature.search.SearchMode) -> InBookSearch)? = null,
    /** Whole book on the phone: in-book search then covers all of it. Changes re-run the search. */
    bookSaved: Boolean = true,
    onDownloadForSearch: (() -> Unit)? = null,
    /** ⋮ menu: the author's page (null when the catalog has none) and sharing the work. */
    onOpenAuthor: (() -> Unit)? = null,
    onShareBook: (() -> Unit)? = null,
    /** An online in-book hit's url → its block and match range in the book (null block: not found). */
    onLocateOnline: (suspend (String, String) -> Triple<Int?, Int, Int>)? = null,
    /** Shared with the main search: shown while the book search box is empty. */
    recentQueries: List<String> = emptyList(),
    onRememberSearch: (String) -> Unit = {},
    onPageJump: (suspend (Int, Int?) -> Int?)? = null,
    onPlayAudio: (BookAudioUi) -> Unit = {},
    /** Read only inside the audio bar, so a playing recording's ticks redraw the bar, not the page. */
    playback: () -> com.atharchive.data.AudioPlaybackState = { com.atharchive.data.AudioPlaybackState() },
    onToggleAudio: () -> Unit = {},
    onSpeed: (Float) -> Unit = {},
    onRepeat: (Boolean) -> Unit = {},
    onSeekMs: (Long) -> Unit = {},
    onOpenEntity: (String) -> Unit = {},
    /** Switches to another edition of this work in place: it replaces this reader, never stacks on it. */
    onEdition: (String) -> Unit = onOpenEntity,
    onAnchorJump: (suspend (String) -> Int?)? = null,
    onLoadSelectionBlocks: (suspend (IntRange) -> List<ReaderBlock>)? = null,
    onRemoveHighlight: suspend (String) -> Unit = {},
    /** Opens the comment editor for a saved highlight (the الكناشة one). */
    onEditHighlightNote: ((String) -> Unit)? = null,
    onOpenInKannashah: ((String) -> Unit)? = null,
    /** «نسخ مع التذييل»: the text with its source line (book, author, volume, page) and link. */
    onCopyCited: (suspend (ReaderSelection) -> Unit)? = null,
) {
    var introOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var panelTab by remember { mutableStateOf(ReaderPanelTab.Contents) }
    var audioOpen by remember { mutableStateOf(false) }
    val selection = remember { PageSelection() }
    var toast by remember { mutableStateOf<String?>(null) }
    var jumpedTo by remember { mutableIntStateOf(-1) }
    // Where the last jump (contents, search, a link) left from: «رجوع» takes the reader back there.
    var returnTo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var searchTarget by remember(initialSearchTarget) { mutableStateOf(initialSearchTarget) }
    var toolbarHeight by remember { mutableIntStateOf(0) }
    var meaning by remember { mutableStateOf<Pair<String, androidx.compose.ui.geometry.Rect>?>(null) }

    var shownFootnotes by remember { mutableStateOf<Pair<List<String>, androidx.compose.ui.geometry.Rect>?>(null) }
    val uriHandler = LocalUriHandler.current
    val colors = rememberReaderColors(settings)
    // Index 0 belongs to the title item. A partial cache can start at any semantic
    // ordinal, so translate the durable ordinal to its position in the local window.
    val initialDisplayIndex = if (window != null) initialOrdinal ?: state.readingPositionIndex
        else state.blocks.indexOfFirst { it.ordinal == state.readingPositionIndex }.takeIf { it >= 0 } ?: state.readingPositionIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialDisplayIndex + 1)
    VolumeKeyPaging(listState, settings.volumeKeys)
    val scope = rememberCoroutineScopeCompat()
    var restored by remember { mutableStateOf(window == null) }
    var positionReady by remember { mutableStateOf(false) }
    var viewportTop by remember { mutableStateOf(0f) }
    val textPositions = selection.geometry
    LaunchedEffect(window) { window?.start(initialOrdinal ?: state.readingPositionIndex) }
    LaunchedEffect(window?.ready, window?.count) {
        if (!restored && window != null && window.ready && window.count > 0) {
            listState.scrollToItem((initialOrdinal ?: state.readingPositionIndex).coerceIn(0, (state.blockCount - 1).coerceAtLeast(0)) + 1)
            restored = true
        }
    }

    // Sada's bars (audio-editor CollapsingHeader): they follow the finger, sliding away as the
    // page scrolls down and back as it scrolls up, and settle open or closed when it lets go.
    // They float over the page, so the text never moves. Driven by the gesture (nested scroll),
    // so the jump to the saved place or a search jump never moves them; a tap shows or hides them.
    val bars = rememberCollapsingBars()
    val density = LocalDensity.current
    // A tap on the page dismisses a selection, offers to remove a highlight under it,
    // otherwise toggles the bars.
    // The tapped highlight and the tap's y on the page, where its bar sits.
    var tappedHighlight by remember { mutableStateOf<Pair<String, Float>?>(null) }
    // Read through a state, not captured: the compiler memoises the function reference below, so a
    // highlight made after opening the book was drawn but never tappable.
    val highlights by androidx.compose.runtime.rememberUpdatedState(state.highlights)
    fun onPageTap(point: Offset) {
        if (selection.active) { selection.clear(); return }
        if (tappedHighlight != null) { tappedHighlight = null; return }
        val hit = selection.sourcesAt(point).firstNotNullOfOrNull { (ordinal, offset) ->
            highlights.firstOrNull { it.id.isNotEmpty() && it.contains(ordinal, offset) }
        }
        val page = selection.page
        if (hit != null && page?.isAttached == true) {
            tappedHighlight = hit.id to point.y
        } else scope.launch { bars.toggle() }
    }
    // The page's gesture detector is built once; through this it always sees the current
    // highlights — captured directly, it kept the first frame's (none) and a tap never offered the menu.
    val pageTap = androidx.compose.runtime.rememberUpdatedState<(Offset) -> Unit>(::onPageTap)

    // Reading position is written continuously. There is no "save position" control,
    // and this is not a bookmark.
    val currentDisplayIndex by remember {
        derivedStateOf { (listState.firstVisibleItemIndex - 1).coerceAtLeast(0) }
    }
    val currentIndex = if (window != null) currentDisplayIndex else
        state.blocks.getOrNull(currentDisplayIndex)?.ordinal ?: currentDisplayIndex
    // A bookmark marks text: past a page divider or notes footer at the top, to the block after it.
    val bookmarkIndex = (currentDisplayIndex..currentDisplayIndex + com.atharchive.data.BookmarkLookahead).firstNotNullOfOrNull { index ->
        val block = if (window != null) window.peek(index) else state.blocks.getOrNull(index)
        val text = block is ReaderBlock.Heading || block is ReaderBlock.Paragraph || block is ReaderBlock.Quote || block is ReaderBlock.Verse
        if (!text) null else if (window != null) index else block!!.ordinal ?: index
    } ?: currentIndex
    val bookmarkedHere = state.bookmarks.any { it.blockIndex == bookmarkIndex }
    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        if (initialOrdinal == null && state.readingOffset > 0) {
            repeat(10) {
                androidx.compose.runtime.withFrameNanos { }
                val geometry = textPositions[state.readingPositionIndex]
                if (geometry != null) {
                    val layout = geometry.layout
                    val text = geometry.value
                    val top = geometry.top()
                    val line = layout.getLineForOffset(text.displayOffset(state.readingOffset).coerceAtMost(layout.layoutInput.text.length))
                    listState.scrollBy(top + layout.getLineTop(line) - viewportTop)
                    positionReady = true
                    return@LaunchedEffect
                }
            }
        }
        positionReady = true
    }
    LaunchedEffect(restored, positionReady, listState) {
        snapshotFlow { if (listState.isScrollInProgress) null else listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { location ->
                if (location == null || !restored || !positionReady) return@collect
                val displayIndex = (location.first - 1).coerceAtLeast(0)
                val ordinal = if (window != null) displayIndex else state.blocks.getOrNull(displayIndex)?.ordinal ?: displayIndex
                if (window != null && (ordinal >= window.count || window.peek(ordinal) == null)) return@collect
                val visibleOrdinals = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    val index = info.index - 1
                    if (window != null) index.takeIf { it >= 0 } else state.blocks.getOrNull(index)?.ordinal
                }.toSet()
                textPositions.keys.retainAll(visibleOrdinals)
                val offset = textPositions[ordinal]?.let { g ->
                    g.value.sourceOffset(g.layout.getLineStart(g.layout.getLineForVerticalPosition((viewportTop - g.top()).coerceAtLeast(0f))))
                } ?: 0
                if (onPositionWithOffset == null) onPositionChange(ordinal) else onPositionWithOffset(ordinal, offset)
            }
    }
    // An empty book has nothing to restore; otherwise wait for the saved place.
    val contentReady = positionReady ||
        (window != null && window.ready && window.count == 0)
    BackHandler(selection.active || panelOpen || tappedHighlight != null || settingsOpen) {
        selection.clear()
        panelOpen = false
        tappedHighlight = null
        settingsOpen = false
    }
    var hits by remember { mutableStateOf(emptyList<InBookHit>()) }
    var searchStatus by remember { mutableStateOf<String?>(null) }
    // Set while part of the book is not on the phone: the search covered only the saved part.
    var partialSearch by remember { mutableStateOf<Int?>(null) }
    var downloadAsked by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(com.atharchive.feature.search.SearchMode.All) }
    LaunchedEffect(query, bookSaved, searchMode) {
        hits = emptyList()
        searchStatus = null
        partialSearch = null
        if (query.trim().length >= 2) {
            kotlinx.coroutines.delay(150)
            searchStatus = "جارٍ البحث…"
            try {
                val result = onSearch?.invoke(query, searchMode) ?: InBookSearch(findHits(state, query))
                hits = result.hits
                partialSearch = result.savedPercent.takeUnless { result.complete }
                searchStatus = if (hits.size == 200) "أول ٢٠٠ نتيجة؛ حدّد البحث لنتائج أدق" else null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                searchStatus = "تعذّر البحث في النص الكامل: ${error.message.orEmpty()}"
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1800)
            toast = null
        }
    }
    LaunchedEffect(initialSearchTarget, restored) {
        if (!restored) return@LaunchedEffect
        val target = initialSearchTarget ?: return@LaunchedEffect
        searchTarget = target
        val displayIndex = if (window != null) target.ordinal else
            state.blocks.indexOfFirst { it.ordinal == target.ordinal }.takeIf { it >= 0 } ?: target.ordinal
        listState.scrollToItem(displayIndex.coerceIn(0, (state.blockCount - 1).coerceAtLeast(0)) + 1)
        kotlinx.coroutines.delay(2200)
        searchTarget = null
    }

    fun jumpTo(index: Int, target: ReaderSearchTarget? = hits.firstOrNull { it.blockIndex == index }?.let {
        ReaderSearchTarget(index, it.sourceMatchStart, it.sourceMatchEnd)
    }) {
        jumpedTo = index
        searchTarget = target
        returnTo = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        scope.launch {
            listState.scrollToItem(index.coerceIn(0, (state.blockCount - 1).coerceAtLeast(0)) + 1)
        }
    }

    fun openLink(target: String) {
        when {
            target.startsWith("#") -> scope.launch {
                try {
                    val anchor = target.drop(1)
                    val ordinal = state.toc.firstOrNull { it.anchor == anchor }?.blockIndex ?: onAnchorJump?.invoke(anchor)
                    if (ordinal != null) jumpTo(ordinal) else toast = "موضع الرابط غير موجود"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { toast = "تعذّر فتح موضع الرابط" }
            }
            target.startsWith("https://") || target.startsWith("http://") ->
                runCatching { uriHandler.openUri(target) }.onFailure { toast = "تعذّر فتح الرابط" }
            Regex("^(?:(?:book|article|question|poem):)?[\\p{L}\\p{N}_-]+$").matches(target) -> onOpenEntity(target.substringAfter(':'))
            else -> toast = "هذا الرابط غير مدعوم"
        }
    }

    val chapterTitle = remember(state.toc, currentIndex) {
        state.toc.lastOrNull { it.level == 2 && it.blockIndex <= currentIndex }?.title.orEmpty()
    }
    // Fractional within the top block (long paragraphs used to freeze the bar), and full at
    // the true end — the first visible block never reaches the last one.
    val blockFraction by remember {
        derivedStateOf {
            val size = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            if (size <= 0) 0f else (listState.firstVisibleItemScrollOffset.toFloat() / size).coerceIn(0f, 1f)
        }
    }
    // Read only while drawing the bar: computed in the body, it recomposed the whole reader
    // on every scroll frame.
    fun progress(): Float = when {
        state.blockCount <= 1 -> 0f
        restored && !listState.canScrollForward -> 1f
        else -> ((currentIndex + blockFraction) / (state.blockCount - 1)).coerceIn(0f, 1f)
    }

    if (introOpen) {
        androidx.activity.compose.BackHandler { introOpen = false }
        IntroPage(state.card, state.bookTitle, state.author, settings, colors, onLink = ::openLink, onDismiss = { introOpen = false })
        return
    }
    shownFootnotes?.let { (ids, at) ->
        FootnoteCallout(ids, at, state.footnotes, settings, colors, onLink = ::openLink, onDismiss = { shownFootnotes = null })
    }

    val haptics = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // A held end near the top or bottom edge scrolls the page under it, faster the closer it gets.
    LaunchedEffect(selection.dragging) {
        if (!selection.dragging) return@LaunchedEffect
        val edge = with(density) { 96.dp.toPx() }
        val maxSpeed = with(density) { 1600.dp.toPx() } // px per second, at or past the edge
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val point = selection.pointer
                val height = selection.page?.size?.height ?: 0
                // Clamp so a dropped frame doesn't become one big jump.
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(1f / 30f)
                last = now
                val speed = if (point == null) 0f else selectionEdgeSpeed(point.y, height, edge)
                if (speed != 0f && dt > 0f) {
                    listState.dispatchRawDelta(speed * maxSpeed * dt)
                    selection.drag(point!!)
                }
            }
        }
    }

    // «زجاج»: the bars blur the page that scrolls under them.
    val haze = remember { dev.chrisbanes.haze.HazeState() }
    androidx.compose.runtime.CompositionLocalProvider(com.atharchive.ui.components.LocalAtharHaze provides haze) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .testTag("reader_screen"),
    ) {
      val wide = maxWidth >= 840.dp
      LaunchedEffect(wide) { if (wide) panelOpen = true }
      val panelWidth = if (wide && panelOpen) 360.dp else 0.dp
      // A narrow panel sits between the bars, so they stay while it is open.
      val hidden = if (panelOpen && !wide) 0f else bars.hidden
      val barsShown = hidden < 0.5f
      // The list's padding never changes, so the text doesn't move when the bars come and go.
      var topBarHeight by remember { mutableStateOf(0.dp) }
      var bottomBarHeight by remember { mutableStateOf(0.dp) }
      var bottomOverlayHeight by remember { mutableStateOf(0.dp) }
      // Item 0 is the title; block i is item i + 1.
      fun blockAtItem(item: Int): ReaderBlock? = if (item < 1) null else window?.peek(item - 1) ?: state.blocks.getOrNull(item - 1)
      fun isBreak(item: Int) = blockAtItem(item) is ReaderBlock.PageBreak
      // ponytail: scans back at most 2000 blocks for the page's marker; a longer unmarked run is one "page".
      fun pageStartAtOrBefore(item: Int): Int? = (item downTo maxOf(1, item - 2000)).firstOrNull(::isBreak)
      // The printed page in view: the marker at or before the top line.
      val folio by remember(window, state.blocks) {
          derivedStateOf { pageStartAtOrBefore(listState.firstVisibleItemIndex)?.let { blockAtItem(it) as? ReaderBlock.PageBreak } }
      }
      Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(start = panelWidth)
                .onGloballyPositioned { viewportTop = it.positionInRoot().y; selection.page = it }
                // The platform loupe over the end being dragged: the finger hides the letters it moves across.
                .magnifier(sourceCenter = { selection.loupeSource() }, zoom = 1.4f)
                .pageSelectionGestures(
                    selection = selection,
                    longPressMillis = SelectionLongPressMillis,
                    touchSlop = { touchSlop },
                    handleRadius = with(density) { 32.dp.toPx() },
                    handleDrop = with(density) { HandleDrop.toPx() },
                    haptics = haptics,
                    onTap = { pageTap.value(it) },
                )
                .semantics {
                    if (selection.active) customActions = listOf(true to -1, true to 1, false to -1, false to 1).map { (isStart, direction) ->
                        CustomAccessibilityAction(
                            "نقل ${if (isStart) "بداية" else "نهاية"} التحديد سطرًا ${if (direction < 0) "للأعلى" else "للأسفل"}",
                        ) { selection.moveByLine(isStart, direction) }
                    }
                }
                .drawWithContent {
                    drawContent()
                    // Read so the handles redraw as the page scrolls under them.
                    listState.firstVisibleItemIndex
                    listState.firstVisibleItemScrollOffset
                    if (selection.active) drawSelectionHandles(selection, colors.accent,
                        drop = HandleDrop.toPx(), radius = 8.dp.toPx(), stem = 2.dp.toPx())
                }
                .nestedScroll(bars.connection)
                .atharBackdrop(haze)
                // Laid out but invisible until the saved place is restored, so the title page
                // is never drawn and then scrolled away from.
                .alpha(if (contentReady) 1f else 0f)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = 12.dp + topBarHeight,
                bottom = 40.dp + bottomBarHeight,
                start = settings.margin.dp,
                end = settings.margin.dp,
            ),
        ) {
            item(key = "reader-title") {
                Column(modifier = Modifier.padding(bottom = 18.dp)) {
                    Text(
                        text = state.bookTitle,
                        color = colors.text,
                        fontFamily = AtharEditorialFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (settings.fontSize + 8).sp,
                        lineHeight = (settings.fontSize + 16).sp,
                    )
                    Text(
                        text = state.author,
                        modifier = Modifier.padding(top = 4.dp),
                        color = colors.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (window != null) {
                items(
                    count = window.count,
                    key = { index -> window.peek(index)?.id ?: "reader-placeholder-$index" },
                ) { index ->
                    val raw = window[index]
                    // The window loads blocks alone; a page footer gets its note bodies here.
                    val block = remember(raw, state.footnotes) {
                        if (raw is ReaderBlock.PageNotes) raw.copy(notes = raw.footnotes.mapNotNull(state.footnotes::get)) else raw
                    }
                    if (block == null) {
                        Box(Modifier.fillMaxWidth().height(80.dp))
                    } else {
                        BlockView(
                            block = block,
                            selection = selection,
                            settings = settings,
                            colors = colors,
                            highlighted = false,
                            highlights = state.highlights,
                            volumeUnit = state.volumeUnit,
                            onFootnotes = { ids, at -> if (ids.any { it in state.footnotes }) shownFootnotes = ids to at },
                            onLink = ::openLink,
                            isSearchTarget = searchTarget?.ordinal == block.ordinal,
                            searchMatch = searchTarget?.takeIf { it.ordinal == block.ordinal }?.let {
                                it.matchStart until it.matchEnd
                            },
                        )
                    }
                }
            } else {
                itemsIndexed(state.blocks, key = { _, b -> b.id }) { index, block ->
                    BlockView(
                        block = block,
                        selection = selection,
                        settings = settings,
                        colors = colors,
                        highlighted = false,
                        highlights = state.highlights,
                        volumeUnit = state.volumeUnit,
                        onFootnotes = { ids, at -> if (ids.any { it in state.footnotes }) shownFootnotes = ids to at },
                        onLink = ::openLink,
                        isSearchTarget = query.isNotBlank() && jumpedTo == index,
                        searchMatch = searchTarget?.takeIf { it.ordinal == (block.ordinal ?: index) }?.let {
                            it.matchStart until it.matchEnd
                        },
                    )
                }
            }
        }

        if (!contentReady && window?.error == null) {
            com.atharchive.ui.components.AtharLoading(
                "جارٍ تحميل النص…",
                Modifier.align(Alignment.Center),
                tag = "reader_loading",
                progress = loadProgress,
            )
        }

        // The folio: the printed page in view, at the foot of the page as in a printed book.
        folio?.let { page ->
            Text(
                text = arabic(page.page),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                    .graphicsLayer { alpha = hidden },
                color = colors.secondary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        val loadError = window?.error
        if (loadError != null) {
            Text(
                text = "تعذّر تحميل النص · اضغط لإعادة المحاولة",
                modifier = Modifier.align(Alignment.TopCenter).background(colors.page)
                    .clickable { window?.retry() }.padding(16.dp),
                color = colors.accent,
            )
        }

        if (selection.active && !selection.dragging) {
            fun act(done: String, action: suspend (ReaderSelection) -> Unit) {
                scope.launch {
                    try {
                        val chosen = resolveSelection(selection, window, state.blocks, settings.showTashkeel, onLoadSelectionBlocks)
                        if (chosen == null) { toast = "تعذّر قراءة النص المحدد"; return@launch }
                        action(chosen)
                        selection.clear()
                        toast = done.ifEmpty { null }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) {
                        android.util.Log.w("AtharReader", "selection action failed", e)
                        // The save's own reason (text still loading, text changed) says what to do next.
                        toast = (e as? IllegalArgumentException)?.message ?: "تعذّر ذلك؛ التحديد ما زال متاحًا"
                    }
                }
            }
            SelectionToolbar(
                colors = colors,
                // Just above the selected lines; below them (clear of the handles) when there
                // is no room above. Placement reads the scroll, so it follows the page.
                modifier = Modifier.align(Alignment.TopCenter).offset {
                    listState.firstVisibleItemScrollOffset
                    val (top, bottom) = selection.bounds() ?: (Float.NEGATIVE_INFINITY to Float.POSITIVE_INFINITY)
                    val boxHeight = selection.page?.size?.height ?: 0
                    val above = top - toolbarHeight - 10.dp.toPx()
                    val y = if (above >= 0) above else bottom + HandleDrop.toPx() + 20.dp.toPx()
                    IntOffset(0, y.coerceIn(0f, (boxHeight - toolbarHeight).coerceAtLeast(0).toFloat()).roundToInt())
                }.onSizeChanged { toolbarHeight = it.height },
                onCopy = { act("نُسخ النص") { onCopySelection(it) } },
                onCopyCited = onCopyCited?.let { copy -> { act("نُسخ مع التذييل") { copy(it) } } },
                onShare = { act("مشاركة النص") { onShareSelection(it) } },
                onHighlight = { act("حُفظ في الكناشة") { onSaveSelection(it) } },
                onAddDhikr = LocalAddToAdhkar.current?.let { add ->
                    { act("") { add(it.quotedText, listOf(state.bookTitle, state.author).filter(String::isNotBlank).joinToString(" — ")) } }
                },
                onMeaning = LocalDictionary.current?.let { selection.singleWord()?.let { word -> {
                    // Where the word sits, taken now: the selection is cleared before the callout opens.
                    val (top, bottom) = selection.bounds() ?: (0f to 0f)
                    val x = listOfNotNull(selection.handlePoint(true)?.x, selection.handlePoint(false)?.x).average().toFloat()
                    val spot = androidx.compose.ui.geometry.Rect(x - 1f, top, x + 1f, bottom)
                    selection.clear()
                    meaning = word to spot
                } } },
            )
        }

        meaning?.let { (word, spot) ->
            MeaningCallout(word, { bounds -> spot.translate(bounds.left.toFloat(), bounds.top.toFloat()) }, colors) { meaning = null }
        }

        tappedHighlight?.let { (id, tapY) ->
            // The selection bar's look, just above the tapped line (below it near the top); a tap or scroll closes it.
            LaunchedEffect(id) { snapshotFlow { listState.isScrollInProgress }.first { it }; tappedHighlight = null }
            var barHeight by remember { mutableIntStateOf(0) }
            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset {
                        val above = tapY - barHeight - 28.dp.toPx()
                        IntOffset(0, (if (above >= 0) above else tapY + 28.dp.toPx()).roundToInt())
                    }
                    .onSizeChanged { barHeight = it.height }
                    .atharChrome(colors.page, AtharCorners.Card, edge = colors.divider)
                    .padding(horizontal = 2.dp)
                    .testTag("reader_highlight_menu"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onEditHighlightNote != null) {
                    SelectionAction(AtharIcons.Pen, "تعليق", colors, false, "reader_hl_note", onClick = {
                        tappedHighlight = null
                        onEditHighlightNote(id)
                    })
                    ToolbarSeparator(colors)
                }
                if (onOpenInKannashah != null) {
                    SelectionAction(AtharIcons.Kannashah, "في الكناشة", colors, false, "reader_hl_kannashah", onClick = {
                        tappedHighlight = null
                        onOpenInKannashah(id)
                    })
                    ToolbarSeparator(colors)
                }
                SelectionAction(AtharIcons.Close, "إزالة من الكناشة", colors, true, "reader_hl_remove", onClick = {
                    tappedHighlight = null
                    scope.launch {
                        toast = try { onRemoveHighlight(id); "أُزيل التظليل" }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { "تعذّرت إزالة التظليل" }
                    }
                })
            }
        }

        // Between the bars, and above the audio strip.
        if (panelOpen) {
            ReaderPanel(
                modifier = (if (wide) Modifier.width(360.dp) else Modifier)
                    .padding(top = if (barsShown) topBarHeight else 0.dp, bottom = bottomOverlayHeight),
                docked = wide,
                state = state,
                colors = colors,
                currentBlockIndex = currentIndex,
                query = query,
                hits = hits,
                searchStatus = searchStatus,
                partialSearch = partialSearch,
                searchMode = searchMode.takeIf { onSearch != null },
                onSearchMode = { searchMode = it },
                downloadAsked = downloadAsked,
                onDownloadForSearch = onDownloadForSearch?.let { download -> { downloadAsked = true; download() } },
                onPageJump = { page, volume ->
                    scope.launch {
                        try {
                            val ordinal = onPageJump?.invoke(page, volume)
                            if (ordinal != null) jumpTo(ordinal) else toast = "الصفحة غير موجودة"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { toast = "تعذّر تحميل الصفحة" }
                    }
                },
                onQueryChange = { query = it },
                // The panel stays open: a reader comparing places should not have to
                // reopen it after every jump.
                onJump = { jumpTo(it) },
                recentQueries = recentQueries,
                onHit = { hit ->
                    onRememberSearch(query)
                    val url = hit.url
                    if (url == null) jumpTo(hit.blockIndex) else scope.launch {
                        toast = "جارٍ فتح الموضع…"
                        val (ordinal, start, end) = onLocateOnline?.invoke(url, query) ?: Triple(null, 0, 0)
                        toast = null
                        if (ordinal == null) toast = "تعذّر فتح الموضع"
                        else jumpTo(ordinal, if (end > start) ReaderSearchTarget(ordinal, start, end) else null)
                    }
                },
                onDismiss = { panelOpen = false },
                initialTab = panelTab,
            )
        }

        // ---- the bars float over the page ----
        Box(
            Modifier.align(Alignment.TopCenter)
                .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } }
                .graphicsLayer { translationY = -hidden * size.height; alpha = 1f - hidden },
        ) {
            ReaderTopBar(
                bookTitle = state.bookTitle,
                chapterTitle = chapterTitle,
                colors = colors,
                onBack = onBack,
                onSearch = {
                    // A second tap closes it, like the poem reader's panel.
                    if (panelOpen && panelTab == ReaderPanelTab.Search) panelOpen = false
                    else { panelTab = ReaderPanelTab.Search; panelOpen = true }
                },
                onMore = { introOpen = true },
                onOpenAuthor = onOpenAuthor,
                onDownload = onDownloadForSearch.takeUnless { bookSaved },
                onShare = onShareBook,
                hasIntro = state.card.isNotEmpty(),
                editions = state.editions,
                onEdition = onEdition,
                // ponytail: loads the whole text; fine for articles and مسائل, never offered in books.
                onCopyAll = if (state.isBook) null else ({
                    scope.launch {
                        toast = try {
                            val all = onLoadSelectionBlocks?.invoke(0 until state.blockCount) ?: state.blocks
                            val texts = all.filter { it.ordinal != null && it.selectionText() != null }
                            val first = texts.first()
                            val last = texts.last()
                            selection.select(
                                ReaderSelectionEndpoint(first.id, first.ordinal!!, 0),
                                ReaderSelectionEndpoint(last.id, last.ordinal!!, last.selectionText()!!.length),
                            )
                            val chosen = resolveSelection(selection, window, state.blocks, settings.showTashkeel, onLoadSelectionBlocks)
                            selection.clear()
                            if (chosen == null) "تعذّر نسخ النص" else { onCopySelection(chosen); "نُسخ النص كله" }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { selection.clear(); "تعذّر نسخ النص" }
                    }
                }),
            )
        }
        // Progress, the audio strip and the bottom bar; the first two ride down to the page's
        // foot when the bar hides.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { bottomOverlayHeight = with(density) { it.height.toDp() } }
                .graphicsLayer { translationY = hidden * bottomBarHeight.toPx() },
        ) {
            ReaderProgressStrip(
                count = state.blockCount,
                colors = colors,
                progress = ::progress,
                jumpLabel = "الانتقال في الكتاب",
                onJump = ::jumpTo,
            ) { index, percent ->
                val kitabs = state.toc.filter { it.level == 2 && it.title.isNotBlank() }
                val at = kitabs.indexOfLast { it.blockIndex <= index }
                val bab = state.toc.lastOrNull { it.blockIndex <= index }?.takeIf { it.level > 2 }
                (if (at >= 0) "${com.atharchive.ui.components.arabicDigits(at + 1)} / ${com.atharchive.ui.components.arabicDigits(kitabs.size)} · ${kitabs[at].title}" else percent) to
                    listOfNotNull(bab?.title, percent.takeIf { at >= 0 }).joinToString(" · ")
            }
            if (audioOpen) ReaderAudioBar(state.audio, playback, colors, onPlayAudio, onToggleAudio, onSpeed, onRepeat, onSeekMs)
            Box(
                Modifier.onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }
                    .graphicsLayer { alpha = 1f - hidden },
            ) {
            ReaderBottomBar(
                colors = colors,
                bookmarked = bookmarkedHere,
                hasAudio = state.audio.isNotEmpty(),
                audioOpen = audioOpen,
                contentsOpen = panelOpen && panelTab == ReaderPanelTab.Contents,
                onContents = {
                    if (panelOpen && panelTab == ReaderPanelTab.Contents) panelOpen = false
                    else { panelTab = ReaderPanelTab.Contents; panelOpen = true }
                },
                // As the poem reader: the first tap starts the first recording, later taps show/hide the bar.
                onAudio = {
                    if (state.audio.none { it.id == playback().id }) { state.audio.firstOrNull()?.let(onPlayAudio); audioOpen = true }
                    else audioOpen = !audioOpen
                },
                settingsOpen = settingsOpen,
                onSettings = { settingsOpen = true },
                settingsMenu = {
                    ReaderSettingsPopover(settingsOpen, settings, colors, onSettingsChange, onDismiss = { settingsOpen = false }, themeMode, onThemeModeChange)
                },
                onBookmark = {
                    val alreadyBookmarked = bookmarkedHere
                    scope.launch {
                        try {
                            onToggleBookmark(bookmarkIndex)
                            toast = if (alreadyBookmarked) "أُزيلت العلامة" else "✓ أُضيفت العلامة"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { toast = "تعذّر حفظ العلامة: ${error.message.orEmpty()}" }
                    }
                },
            )
            }
        }
      }

        returnTo?.let { (item, offset) ->
            ReturnChip(colors, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = bottomBarHeight * (1f - hidden) + 12.dp)) {
                returnTo = null
                scope.launch { listState.scrollToItem(item, offset) }
            }
        }
        toast?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Above the bar: the toast answers a bar button and must not cover it.
                    .padding(bottom = bottomBarHeight * (1f - hidden) + 12.dp)
                    .clip(AtharCorners.Card)
                    .background(colors.text.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .testTag("reader_toast"),
            ) {
                Text(
                    text = message,
                    color = colors.page,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    }
}

@Composable
private fun ReaderTopBar(
    bookTitle: String,
    hasIntro: Boolean,
    editions: List<ReaderEdition>,
    onEdition: (String) -> Unit,
    chapterTitle: String,
    colors: ReaderColors,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    /** «نسخ الكل»; null in books. */
    onCopyAll: (() -> Unit)? = null,
    onOpenAuthor: (() -> Unit)? = null,
    /** null once the whole work is on the phone. */
    onDownload: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .atharChrome(colors.page)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("reader_top_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSlot(AtharIcons.Back, "رجوع", colors, "reader_back", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = bookTitle,
                color = colors.text,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = AtharTextSize.Ui,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A single-chapter work names its one chapter after itself; printing it
            // twice, six points apart, reads as a rendering fault. A work with several
            // editions always says which one is open.
            val subtitle = listOfNotNull(
                editions.firstOrNull { it.current }?.label,
                chapterTitle.takeIf { it.isNotBlank() && it != bookTitle },
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconSlot(AtharIcons.Search, "البحث في الكتاب", colors, "reader_search", onSearch)
        Box {
            var menu by remember { mutableStateOf(false) }
            var editionsOpen by remember { mutableStateOf(false) }
            IconSlot(AtharIcons.More, "المزيد", colors, "reader_more") { menu = true }
            AtharMenu(expanded = menu, onDismiss = { menu = false }, width = 260.dp, colors = com.atharchive.ui.components.AtharMenuColors(colors.page, colors.text, colors.secondary, colors.accent), modifier = Modifier.testTag("reader_more_menu")) {
                if (hasIntro) AtharMenuItem(
                    label = "مقدمة المحقق ومعلومات عن الكتاب",
                    icon = AtharIcons.Info,
                    onClick = { menu = false; onMore() },
                    modifier = Modifier.testTag("reader_source"),
                )
                if (onCopyAll != null) AtharMenuItem("نسخ الكل", icon = AtharIcons.Copy, onClick = { menu = false; onCopyAll() })
                if (editions.isNotEmpty()) AtharMenuItem("الطبعات", onClick = { menu = false; editionsOpen = true })
                if (onOpenAuthor != null) AtharMenuItem("صفحة المؤلف ومؤلفاته", icon = AtharIcons.People, onClick = { menu = false; onOpenAuthor() })
                if (onDownload != null) AtharMenuItem("تحميل الكتاب كاملًا", icon = AtharIcons.Download, onClick = { menu = false; onDownload() })
                if (onShare != null) AtharMenuItem("مشاركة الكتاب", icon = AtharIcons.Share, onClick = { menu = false; onShare() })
            }
            AtharMenu(expanded = editionsOpen, onDismiss = { editionsOpen = false }, colors = com.atharchive.ui.components.AtharMenuColors(colors.page, colors.text, colors.secondary, colors.accent)) {
                editions.forEach { edition ->
                    AtharMenuItem(
                        edition.label,
                        onClick = { editionsOpen = false; if (!edition.current) onEdition(edition.id) },
                        checked = edition.current,
                        subtitle = listOfNotNull("المفتوحة الآن".takeIf { edition.current }, "الافتراضية".takeIf { edition.primary }).joinToString(" · "),
                    )
                }
            }
        }
    }
}

@Composable
private fun IconSlot(
    icon: ImageVector,
    description: String,
    colors: ReaderColors,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.secondary,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
internal fun ReaderBottomBar(
    colors: ReaderColors,
    bookmarked: Boolean,
    hasAudio: Boolean,
    audioOpen: Boolean,
    contentsOpen: Boolean,
    onContents: () -> Unit,
    onAudio: () -> Unit,
    settingsOpen: Boolean,
    onSettings: () -> Unit,
    settingsMenu: @Composable () -> Unit,
    onBookmark: () -> Unit,
) {
    var lastControl by remember { mutableIntStateOf(-1) }
    val centres = remember(hasAudio) { androidx.compose.runtime.mutableStateListOf(*Array(if (hasAudio) 4 else 3) { Offset.Zero }) }
    val active = listOf(contentsOpen) + (if (hasAudio) listOf(audioOpen) else emptyList()) + listOf(bookmarked, settingsOpen)
    val selected = lastControl.takeIf { active.getOrNull(it) == true } ?: active.indexOfFirst { it }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .atharChrome(colors.page)
            // The app is already lifted by the keyboard, which covers the navigation bar: adding
            // that bar's height again left an empty band above the keyboard.
            .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
            .testTag("reader_controls"),
    ) {
        HorizontalDivider(thickness = 0.7.dp, color = colors.divider)
        Box {
        SoapTravelIndicator(selected, centres, colors.accent.copy(alpha = 0.14f), Modifier.matchParentSize())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bookmark sits in the middle — it is the action taken most often, and with
            // no audio the bar is exactly three items around it.
            ControlButton(if (contentsOpen) AtharIcons.SidebarFilled else AtharIcons.Sidebar, "المحتويات", colors, contentsOpen, "reader_ctl_contents", Modifier.weight(1.0f).onGloballyPositioned { centres[0] = it.positionInParent() + Offset(it.size.width / 2f, it.size.height / 2f) }, { lastControl = 0; onContents() })
            if (hasAudio) {
                ControlButton(
                    if (audioOpen) AtharIcons.AudioFilled else AtharIcons.Audio,
                    "صوتيات الكتاب",
                    colors,
                    audioOpen,
                    "reader_ctl_audio",
                    Modifier.weight(1.0f).onGloballyPositioned { centres[1] = it.positionInParent() + Offset(it.size.width / 2f, it.size.height / 2f) },
                    { lastControl = 1; onAudio() },
                )
            }
            ControlButton(
                if (bookmarked) AtharIcons.BookmarkFilled else AtharIcons.Bookmark,
                if (bookmarked) "إزالة العلامة" else "إضافة علامة",
                colors,
                bookmarked,
                "reader_ctl_bookmark",
                Modifier.weight(1.0f).onGloballyPositioned { centres[if (hasAudio) 2 else 1] = it.positionInParent() + Offset(it.size.width / 2f, it.size.height / 2f) },
                { lastControl = if (hasAudio) 2 else 1; onBookmark() },
            )
            AaButton(colors, settingsOpen, Modifier.weight(1.0f).onGloballyPositioned { centres[if (hasAudio) 3 else 2] = it.positionInParent() + Offset(it.size.width / 2f, it.size.height / 2f) }, { lastControl = if (hasAudio) 3 else 2; onSettings() }, settingsMenu)
        }
    }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    colors: ReaderColors,
    active: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            // No ripple, as on the app's bottom bar: the pill is the feedback.
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) colors.accent else colors.secondary,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun AaButton(colors: ReaderColors, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, menu: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag("reader_ctl_aa"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Aa",
            modifier = Modifier.clearAndSetSemantics { contentDescription = "إعدادات مظهر القراءة" },
            color = if (active) colors.accent else colors.secondary,
            fontSize = AtharTextSize.Large,
            fontWeight = FontWeight.SemiBold,
        )
        menu()
    }
}

private const val SelectionLongPressMillis = 350L
private val HandleDrop = 8.dp

/**
 * The selected text, built when an action runs. Blocks the pager still holds are used as they
 * are; a selection that reaches past them loads the whole range, and a gap is an error, never
 * a silently shorter quote.
 */
private suspend fun resolveSelection(
    selection: PageSelection,
    window: ReaderWindow?,
    blocks: List<ReaderBlock>,
    showTashkeel: Boolean,
    load: (suspend (IntRange) -> List<ReaderBlock>)?,
): ReaderSelection? {
    val start = selection.start ?: return null
    val end = selection.end ?: return null
    val range = start.ordinal..end.ordinal
    val held = ((window?.held() ?: emptyList()) + blocks)
        .filter { it.ordinal in range }.distinctBy { it.id }.sortedBy { it.ordinal }
    val chosen = if (held.map { it.ordinal } == range.toList()) held else {
        val loaded = load?.invoke(range) ?: return null
        require(loaded.map { it.ordinal } == range.toList()) { "لم يكتمل تحميل نطاق التحديد" }
        loaded
    }
    val document = SelectionDocument.create(chosen, showTashkeel)
    return document.selection(document.offset(start) ?: return null, document.offset(end) ?: return null)
}

/** The bars' slide-away state: shared by every reader so they all hide and return alike. */
@androidx.compose.runtime.Stable
internal class CollapsingBars(private val distance: Float) {
    var hidden by mutableFloatStateOf(0f) // 0 shown … 1 hidden
        private set
    suspend fun settle(target: Float) =
        animate(hidden, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> hidden = value }
    suspend fun toggle() = settle(if (hidden < 0.5f) 1f else 0f)
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            hidden = (hidden - available.y / distance).coerceIn(0f, 1f)
            return Offset.Zero
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(if (hidden > 0.5f) 1f else 0f)
            return Velocity.Zero
        }
    }
}

/** 64dp of finger travel fully hides the bars. */
@Composable
internal fun rememberCollapsingBars(): CollapsingBars {
    val density = LocalDensity.current
    return remember(density) { CollapsingBars(with(density) { 64.dp.toPx() }) }
}

/**
 * The reading progress line; hold and slide to pick a place, release jumps. The bubble shows
 * [label]'s two lines for the item under the thumb. Taller than the 4dp line so a thumb can catch it.
 * [progress] is read only while drawing, so scrolling never recomposes the caller.
 */
@Composable
internal fun ReaderProgressStrip(
    count: Int,
    colors: ReaderColors,
    progress: () -> Float,
    jumpLabel: String,
    onJump: (Int) -> Unit,
    label: (index: Int, percent: String) -> Pair<String, String>,
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val haptics = LocalHapticFeedback.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    fun scrubIndex(fraction: Float) = (fraction * (count - 1)).roundToInt().coerceAtLeast(0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(count, rtl) {
                fun at(x: Float) = (if (rtl) 1f - x / size.width else x / size.width).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { scrub = at(it.x); haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                    onDragEnd = { scrub?.let { onJump(scrubIndex(it)) }; scrub = null },
                    onDragCancel = { scrub = null },
                ) { change, _ -> change.consume(); scrub = at(change.position.x) }
            }
            .semantics {
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progress(), 0f..1f)
                setProgress(jumpLabel) { onJump(scrubIndex(it.coerceIn(0f, 1f))); true }
            }
            .drawBehind {
                val line = (if (scrub != null) 6.dp else 4.dp).toPx()
                val thumb = (if (scrub != null) 9.dp else 6.dp).toPx()
                val top = size.height - thumb - line / 2
                drawRect(colors.divider.copy(alpha = 0.5f), topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, line))
                val width = size.width * (scrub ?: progress())
                val left = if (rtl) size.width - width else 0f
                drawRect(colors.accent, topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(width, line))
                // A round thumb at the reading point: shows the bar can be grabbed, and where.
                val x = (if (rtl) size.width - width else width).coerceIn(thumb, size.width - thumb)
                drawCircle(colors.accent, thumb, androidx.compose.ui.geometry.Offset(x, top + line / 2))
            }
            .testTag("reader_progress"),
    ) {
        scrub?.let { fraction ->
            val (title, subtitle) = label(scrubIndex(fraction), "${com.atharchive.ui.components.arabicDigits((fraction * 100).roundToInt())}٪")
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .wrapContentHeight(Alignment.Bottom, unbounded = true)
                    .padding(start = 24.dp, end = 24.dp, bottom = 38.dp)
                    .atharChrome(colors.page, com.atharchive.ui.theme.AtharCorners.Card, edge = colors.divider)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("reader_scrub_bubble"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = colors.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(
                    subtitle,
                    color = colors.secondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** «إلى الأذكار» from a selection: (text, where it is from). Provided by the app shell; null hides it. */
val LocalAddToAdhkar = androidx.compose.runtime.staticCompositionLocalOf<((String, String) -> Unit)?> { null }

/** Copy, share, highlight, and a dhikr of one's own. Nothing else — this toolbar is not a menu. */
@Composable
internal fun SelectionToolbar(
    colors: ReaderColors,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
    /** «نسخ مع التذييل», beside نسخ; null where there is no source line to add. */
    onCopyCited: (() -> Unit)? = null,
    onShare: () -> Unit,
    onHighlight: () -> Unit,
    onAddDhikr: (() -> Unit)? = null,
    /** «معنى»; non-null only while one word is selected. */
    onMeaning: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .atharChrome(colors.page, AtharCorners.Card, edge = colors.divider)
            .padding(horizontal = 2.dp)
            .testTag("reader_selection_toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onMeaning != null) {
            SelectionAction(AtharIcons.Books, "معنى", colors, true, "reader_sel_meaning", onMeaning)
            ToolbarSeparator(colors)
        }
        SelectionAction(AtharIcons.Copy, "نسخ", colors, false, "reader_sel_copy", onCopy)
        ToolbarSeparator(colors)
        // One word has nothing to cite, and the bar has no room for six actions.
        if (onCopyCited != null && onMeaning == null) {
            SelectionAction(AtharIcons.Copy, "نسخ مع التذييل", colors, false, "reader_sel_copy_cited", onCopyCited)
            ToolbarSeparator(colors)
        }
        SelectionAction(AtharIcons.Share, "مشاركة", colors, false, "reader_sel_share", onShare)
        ToolbarSeparator(colors)
        SelectionAction(AtharIcons.Kannashah, "إلى الكناشة", colors, true, "reader_sel_highlight", onHighlight)
        if (onAddDhikr != null) {
            ToolbarSeparator(colors)
            // Icon only: five labels overflow a 360dp phone.
            SelectionAction(AtharIcons.Adhkar, "", colors, false, "reader_sel_dhikr", onAddDhikr, "إلى الأذكار")
        }
    }
}

@Composable
private fun ToolbarSeparator(colors: ReaderColors) {
    Box(
        Modifier
            .width(0.7.dp)
            .height(20.dp)
            .background(colors.divider),
    )
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    colors: ReaderColors,
    emphasised: Boolean,
    tag: String,
    onClick: () -> Unit,
    description: String? = null,
) {
    val tint = if (emphasised) colors.accent else colors.secondary
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(if (label.isEmpty()) 18.dp else 15.dp),
        )
        if (label.isNotEmpty()) Text(
            text = label,
            modifier = Modifier.padding(start = 6.dp),
            color = if (emphasised) colors.accent else colors.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

private fun findHits(state: ReaderUiState, query: String): List<InBookHit> {
    val term = query.trim()
    if (term.length < 2) return emptyList()
    // Book order is the default for in-book search: the reader is moving through a text,
    // not ranking sources against each other.
    return state.blocks.mapIndexedNotNull { index, block ->
        val text = when (block) {
            is ReaderBlock.Paragraph -> block.text
            is ReaderBlock.Quote -> block.text
            is ReaderBlock.Verse -> listOfNotNull(block.sadr, block.ajuz).joinToString(" … ")
            is ReaderBlock.Heading -> block.text
            else -> null
        } ?: return@mapIndexedNotNull null
        val at = text.indexOf(term)
        if (at < 0) return@mapIndexedNotNull null
        val chapter = state.toc.lastOrNull { it.blockIndex <= index }?.title.orEmpty()
        InBookHit(
            id = "${block.id}-$at",
            blockIndex = index,
            chapterTitle = chapter,
            excerpt = text,
            matchStart = at,
            matchEnd = at + term.length,
            pageLabel = null,
        )
    }
}

@Composable
private fun ReaderAudioBar(
    audio: List<BookAudioUi>,
    playback: () -> com.atharchive.data.AudioPlaybackState,
    colors: ReaderColors,
    onPlay: (BookAudioUi) -> Unit,
    onToggle: () -> Unit,
    onSpeed: (Float) -> Unit,
    onRepeat: (Boolean) -> Unit,
    onSeekMs: (Long) -> Unit,
) {
    val now = playback()
    val track = audio.firstOrNull { it.id == now.id } ?: return
    com.atharchive.feature.poemreader.PoemAudioBar(
        track = track,
        recordings = audio,
        playing = now.playWhenReady,
        position = (now.positionMs / 1000).toInt(),
        speed = now.speed,
        repeat = now.repeat,
        colors = colors,
        onToggle = onToggle,
        onSelect = onPlay,
        onSpeed = onSpeed,
        onRepeat = { onRepeat(!now.repeat) },
        onSeek = { onSeekMs((it * track.durationSeconds * 1000).toLong()) },
    )
}

/**
 * The book's front matter (title page, editor's intro, manuscripts) on its own page, out of the
 * reading flow. Drawn by the reader's own [BlockView], so it reads like the book.
 */
@Composable
internal fun IntroPage(card: List<CardField>, title: String, author: String, settings: ReaderSettings, colors: ReaderColors, onLink: (String) -> Unit = {}, onDismiss: () -> Unit) {
    val blocks = remember(card, title, author) {
        listOf(ReaderBlock.Heading("intro_title", 2, title, "")) +
            listOfNotNull(author.takeIf(String::isNotBlank)?.let { ReaderBlock.Paragraph("intro_author", it) }) +
            card.mapIndexed { i, line -> line.toIntroBlock("intro_$i") }
    }
        Column(Modifier.fillMaxSize().background(colors.page).systemBarsPadding().testTag("reader_intro")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مقدمة المحقق ومعلومات عن الكتاب", color = colors.text, fontSize = AtharTextSize.Large, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconSlot(AtharIcons.Close, "إغلاق", colors, "reader_intro_close", onDismiss)
            }
            HorizontalDivider(thickness = 0.7.dp, color = colors.divider)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                items(blocks, key = ReaderBlock::id) { block ->
                    BlockView(block, settings, colors, highlighted = false, isSearchTarget = false, onLink = onLink)
                }
            }
        }
}

private fun CardField.toIntroBlock(id: String): ReaderBlock = when {
    k == "h" -> ReaderBlock.Heading(id, 3, v, "")
    l.isNotEmpty() -> ReaderBlock.Paragraph(id, "$l: $v", spans = listOf(InlineSpan("strong", 0, l.length + 1)), listMarker = if (k == "li") "•" else null)
    else -> ReaderBlock.Paragraph(id, v, listMarker = if (k == "li") "•" else null)
}


/** MainActivity hands the volume keys here while a reader with «أزرار الصوت» on is open. */
object VolumePaging { var turn: ((forward: Boolean) -> Unit)? = null }

/** Volume down a page forward, up a page back; instant, so an e-ink panel redraws once. */
@Composable
internal fun VolumeKeyPaging(listState: androidx.compose.foundation.lazy.LazyListState, enabled: Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(enabled, listState) {
        val turn: (Boolean) -> Unit = { forward ->
            scope.launch {
                // A line short of a full screen, so the last line read stays in view.
                val page = listState.layoutInfo.viewportSize.height * 0.9f
                listState.scrollBy(if (forward) page else -page)
            }
        }
        if (enabled) VolumePaging.turn = turn
        onDispose { if (VolumePaging.turn === turn) VolumePaging.turn = null }
    }
}

/** «رجوع»: back to where the last jump left from, in every reader. */
@Composable
internal fun ReturnChip(colors: ReaderColors, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(AtharCorners.Card)
            .background(colors.page)
            .border(1.dp, colors.divider, AtharCorners.Card)
            .clickable(onClickLabel = "العودة إلى الموضع السابق", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("reader_return"),
    ) {
        Text("↩ رجوع", color = colors.accent, style = MaterialTheme.typography.bodySmall)
    }
}
