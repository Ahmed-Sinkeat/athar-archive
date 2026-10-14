package com.atharchive.feature.reader

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.atharchive.ui.components.rememberAtharCollapsingHeader
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    window: ReaderWindow? = null,
    onBack: () -> Unit,
    onCopySelection: (ReaderSelection) -> Unit,
    onShareSelection: (ReaderSelection) -> Unit,
    onSaveSelection: suspend (ReaderSelection) -> Unit,
    onToggleBookmark: suspend (Int) -> Unit,
    onPositionChange: (Int) -> Unit,
    onPositionWithOffset: ((Int, Int) -> Unit)? = null,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    modifier: Modifier = Modifier,
    initialSearchTarget: ReaderSearchTarget? = null,
    initialOrdinal: Int? = null,
    onSearch: (suspend (String) -> List<InBookHit>)? = null,
    onPageJump: (suspend (Int, Int?) -> Int?)? = null,
    onPlayAudio: (BookAudioUi) -> Unit = {},
    playingAudioId: String? = null,
    audioPlayIntent: Boolean = false,
    onOpenEntity: (String) -> Unit = {},
    onAnchorJump: (suspend (String) -> Int?)? = null,
    onLoadSelectionBlocks: (suspend (IntRange) -> List<ReaderBlock>)? = null,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var panelTab by remember { mutableStateOf(ReaderPanelTab.Contents) }
    var audioOpen by remember { mutableStateOf(false) }
    val selection = remember { PageSelection() }
    var toast by remember { mutableStateOf<String?>(null) }
    var jumpedTo by remember { mutableIntStateOf(-1) }
    var searchTarget by remember(initialSearchTarget) { mutableStateOf(initialSearchTarget) }
    var toolbarHeight by remember { mutableIntStateOf(0) }

    var shownFootnotes by remember { mutableStateOf(emptyList<String>()) }
    val uriHandler = LocalUriHandler.current
    val colors = rememberReaderColors(settings.palette)
    // Index 0 belongs to the title item. A partial cache can start at any semantic
    // ordinal, so translate the durable ordinal to its position in the local window.
    val initialDisplayIndex = if (window != null) initialOrdinal ?: state.readingPositionIndex
        else state.blocks.indexOfFirst { it.ordinal == state.readingPositionIndex }.takeIf { it >= 0 } ?: state.readingPositionIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialDisplayIndex + 1)
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

    // The bars hide when the reader scrolls down and return on the first scroll up. Driven by
    // the gesture (nested scroll), so the jump to the saved place or a search jump never hides
    // them; a tap on the page brings them back or hides them.
    val chrome = rememberAtharCollapsingHeader()
    val topBarVisible = chrome.visible
    // A tap on the page dismisses a selection, otherwise toggles the bars.
    fun onPageTap() {
        if (selection.active) selection.clear() else chrome.toggle()
    }

    // Reading position is written continuously. There is no "save position" control,
    // and this is not a bookmark.
    val currentDisplayIndex by remember {
        derivedStateOf { (listState.firstVisibleItemIndex - 1).coerceAtLeast(0) }
    }
    val currentIndex = if (window != null) currentDisplayIndex else
        state.blocks.getOrNull(currentDisplayIndex)?.ordinal ?: currentDisplayIndex
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
    BackHandler(selection.active || panelOpen) {
        selection.clear()
        panelOpen = false
    }
    var hits by remember { mutableStateOf(emptyList<InBookHit>()) }
    var searchStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        hits = emptyList()
        searchStatus = null
        if (query.trim().length >= 2) {
            kotlinx.coroutines.delay(150)
            searchStatus = "جارٍ البحث في النص الكامل؛ قد يلزم تحميله…"
            try {
                hits = onSearch?.invoke(query) ?: findHits(state, query)
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

    fun jumpTo(index: Int) {
        jumpedTo = index
        searchTarget = hits.firstOrNull { it.blockIndex == index }?.let {
            ReaderSearchTarget(index, it.sourceMatchStart, it.sourceMatchEnd)
        }
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
        state.toc.lastOrNull { it.blockIndex <= currentIndex }?.title.orEmpty()
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

    if (shownFootnotes.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { shownFootnotes = emptyList() },
            containerColor = colors.page, textContentColor = colors.text, titleContentColor = colors.text,
            title = { Text(if (shownFootnotes == listOf("source")) "المصدر" else "الحواشي") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (shownFootnotes == listOf("source")) Text("${state.bookTitle}\n${state.author}\n${state.sourceUrl}")
                    else shownFootnotes.forEach { id ->
                        val block = state.footnotes[id]
                        if (block == null) Text("الحاشية لم تُحمّل بعد")
                        else BlockView(block, settings, colors, highlighted = false,
                            isSearchTarget = false, onLink = ::openLink)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { shownFootnotes = emptyList() }) { Text("إغلاق") } },
        )
    }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // A held end near the top or bottom edge scrolls the page under it, at a steady pace.
    LaunchedEffect(selection.dragging) {
        if (!selection.dragging) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val point = selection.pointer
                val height = selection.page?.size?.height ?: 0
                val edge = with(density) { 56.dp.toPx() }
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val direction = if (point == null) 0 else selectionEdgeStep(point.y, height, edge.roundToInt(), 1)
                if (direction != 0 && dt > 0f) {
                    listState.dispatchRawDelta(direction * with(density) { 700.dp.toPx() } * dt)
                    selection.drag(point!!)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .testTag("reader_screen"),
    ) {
      val wide = maxWidth >= 840.dp
      LaunchedEffect(wide) { if (wide) panelOpen = true }
      val panelWidth = if (wide && panelOpen) 360.dp else 0.dp
      Column(modifier = Modifier.fillMaxSize()) {
        // A normal top bar that occupies layout space, not a floating overlay. It is the
        // only chrome that hides, and only while scrolling down.
        AnimatedVisibility(
            visible = topBarVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
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
                onMore = { shownFootnotes = listOf("source") },
                sourceUrl = state.sourceUrl,
                onOpenSite = { openLink(state.sourceUrl) },
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(start = panelWidth)
                .onGloballyPositioned { viewportTop = it.positionInRoot().y; selection.page = it }
                .pageSelectionGestures(
                    selection = selection,
                    longPressMillis = SelectionLongPressMillis,
                    touchSlop = { touchSlop },
                    handleRadius = with(density) { 24.dp.toPx() },
                    handleDrop = with(density) { HandleDrop.toPx() },
                    haptics = haptics,
                    onTap = ::onPageTap,
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
                        drop = HandleDrop.toPx(), radius = 6.dp.toPx(), stem = 2.dp.toPx())
                }
                .nestedScroll(chrome.connection)
                // Laid out but invisible until the saved place is restored, so the title page
                // is never drawn and then scrolled away from.
                .alpha(if (contentReady) 1f else 0f)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 40.dp,
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
                    val block = window[index]
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
                            onFootnotes = { shownFootnotes = it },
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
                        onFootnotes = { shownFootnotes = it },
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

        // ---- progress, kept subtly visible ----
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(colors.divider.copy(alpha = 0.5f)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val width = size.width * progress()
                        val left = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - width else 0f
                        drawRect(colors.accent, topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                            size = androidx.compose.ui.geometry.Size(width, size.height))
                    }
                    .testTag("reader_progress"),
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
                        toast = done
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { toast = "تعذّر ذلك؛ التحديد ما زال متاحًا" }
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
                onShare = { act("مشاركة النص") { onShareSelection(it) } },
                onHighlight = { act("حُفظ في الكناشة") { onSaveSelection(it) } },
            )
        }

        // Rendered inside the content Box so it stops above the bottom bar instead of
        // covering it — the poem reader's panel behaves this way and it reads better.
        if (panelOpen) {
            ReaderPanel(
                modifier = if (wide) Modifier.width(360.dp) else Modifier,
                docked = wide,
                state = state,
                colors = colors,
                currentBlockIndex = currentIndex,
                query = query,
                hits = hits,
                searchStatus = searchStatus,
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
                onJump = ::jumpTo,
                onDismiss = { panelOpen = false },
                initialTab = panelTab,
            )
        }
        }

        if (audioOpen && state.audio.isNotEmpty()) {
            ReaderAudioStrip(
                audio = state.audio,
                colors = colors,
                onPlay = onPlayAudio,
                onClose = { audioOpen = false },
                playingId = playingAudioId,
                playIntent = audioPlayIntent,
            )
        }

        // A normal bottom bar, always visible — the same shape as the one outside the
        // reader, just with the reader's own actions.
        AnimatedVisibility(
            visible = topBarVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
        ReaderBottomBar(
            colors = colors,
            bookmarked = state.bookmarks.any { it.blockIndex == currentIndex },
            hasAudio = state.audio.isNotEmpty(),
            audioOpen = audioOpen,
            contentsOpen = panelOpen && panelTab == ReaderPanelTab.Contents,
            onContents = {
                if (panelOpen && panelTab == ReaderPanelTab.Contents) panelOpen = false
                else { panelTab = ReaderPanelTab.Contents; panelOpen = true }
            },
            onAudio = { audioOpen = !audioOpen },
            onSettings = { settingsOpen = true },
            onBookmark = {
                val alreadyBookmarked = state.bookmarks.any { it.blockIndex == currentIndex }
                scope.launch {
                    try {
                        onToggleBookmark(currentIndex)
                        toast = if (alreadyBookmarked) "أُزيلت العلامة" else "✓ أُضيفت العلامة"
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { toast = "تعذّر حفظ العلامة: ${error.message.orEmpty()}" }
                }
            },
        )
        }
      }

        toast?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
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

    if (settingsOpen) {
        ReaderSettingsPopover(
            settings = settings,
            colors = colors,
            onChange = onSettingsChange,
            onDismiss = { settingsOpen = false },
        )
    }
}

@Composable
private fun ReaderTopBar(
    bookTitle: String,
    chapterTitle: String,
    colors: ReaderColors,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    sourceUrl: String = "",
    onOpenSite: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.page)
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
            // twice, six points apart, reads as a rendering fault.
            if (chapterTitle.isNotBlank() && chapterTitle != bookTitle) {
                Text(
                    text = chapterTitle,
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
            IconSlot(AtharIcons.More, "المزيد", colors, "reader_more") { menu = true }
            AtharMenu(expanded = menu, onDismiss = { menu = false }, modifier = Modifier.testTag("reader_more_menu")) {
                AtharMenuItem(
                    label = "عن المصدر",
                    icon = AtharIcons.Info,
                    onClick = { menu = false; onMore() },
                    modifier = Modifier.testTag("reader_source"),
                )
                if (sourceUrl.isNotBlank()) AtharMenuItem(
                    label = "فتح في الموقع",
                    icon = AtharIcons.Share,
                    onClick = { menu = false; onOpenSite() },
                    modifier = Modifier.testTag("reader_open_site"),
                )
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
private fun ReaderBottomBar(
    colors: ReaderColors,
    bookmarked: Boolean,
    hasAudio: Boolean,
    audioOpen: Boolean,
    contentsOpen: Boolean,
    onContents: () -> Unit,
    onAudio: () -> Unit,
    onSettings: () -> Unit,
    onBookmark: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.page)
            .navigationBarsPadding()
            .testTag("reader_controls"),
    ) {
        HorizontalDivider(thickness = 0.7.dp, color = colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bookmark sits in the middle — it is the action taken most often, and with
            // no audio the bar is exactly three items around it.
            ControlButton(AtharIcons.Sidebar, "المحتويات", colors, contentsOpen, "reader_ctl_contents", Modifier.weight(1f), onContents)
            if (hasAudio) {
                ControlButton(
                    AtharIcons.Audio,
                    "صوتيات الكتاب",
                    colors,
                    audioOpen,
                    "reader_ctl_audio",
                    Modifier.weight(1f),
                    onAudio,
                )
            }
            ControlButton(
                if (bookmarked) AtharIcons.BookmarkFilled else AtharIcons.Bookmark,
                if (bookmarked) "إزالة العلامة" else "إضافة علامة",
                colors,
                bookmarked,
                "reader_ctl_bookmark",
                Modifier.weight(1f),
                onBookmark,
            )
            AaButton(colors, Modifier.weight(1f), onSettings)
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
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
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
private fun AaButton(colors: ReaderColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("reader_ctl_aa"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Aa",
            modifier = Modifier.clearAndSetSemantics { contentDescription = "إعدادات مظهر القراءة" },
            color = colors.secondary,
            fontSize = AtharTextSize.Large,
            fontWeight = FontWeight.SemiBold,
        )
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

/** Copy, share, highlight. Nothing else — this toolbar is not a menu. */
@Composable
private fun SelectionToolbar(
    colors: ReaderColors,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onHighlight: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(AtharCorners.Card)
            .background(colors.page)
            .border(0.7.dp, colors.divider, AtharCorners.Card)
            .padding(horizontal = 2.dp)
            .testTag("reader_selection_toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionAction(AtharIcons.Copy, "نسخ", colors, false, "reader_sel_copy", onCopy)
        ToolbarSeparator(colors)
        SelectionAction(AtharIcons.Share, "مشاركة", colors, false, "reader_sel_share", onShare)
        ToolbarSeparator(colors)
        SelectionAction(AtharIcons.Kannashah, "إلى الكناشة", colors, true, "reader_sel_highlight", onHighlight)
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
) {
    val tint = if (emphasised) colors.accent else colors.secondary
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Text(
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

/** Only present when the book actually has recordings; opens above the bottom bar. */
@Composable
private fun ReaderAudioStrip(
    audio: List<BookAudioUi>,
    colors: ReaderColors,
    onPlay: (BookAudioUi) -> Unit,
    onClose: () -> Unit,
    playingId: String? = null,
    playIntent: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.page)
            .testTag("reader_audio_strip"),
    ) {
        HorizontalDivider(thickness = 0.7.dp, color = colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "صوتيات الكتاب",
                modifier = Modifier.weight(1f),
                color = colors.secondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .testTag("reader_audio_close"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AtharIcons.Close,
                    contentDescription = "إغلاق",
                    tint = colors.secondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        audio.forEach { track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onPlay(track) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .testTag("reader_audio_${track.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.accentFill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (track.id == playingId && playIntent) AtharIcons.Pause else AtharIcons.Play,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = track.title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    color = colors.text,
                    fontFamily = AtharEditorialFontFamily,
                    fontSize = AtharTextSize.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.durationLabel,
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
