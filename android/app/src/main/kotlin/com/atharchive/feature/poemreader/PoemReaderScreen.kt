package com.atharchive.feature.poemreader

import com.atharchive.feature.reader.drawHighlightLine
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.animateScrollBy
import com.atharchive.ui.components.arabicDigits
import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuColors
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.data.AudioPlaybackState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import com.atharchive.feature.reader.rememberCollapsingBars
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharTailPopover
import com.atharchive.feature.reader.ReaderColors
import com.atharchive.feature.reader.ReaderSettings
import com.atharchive.feature.reader.ReaderSettingsPopover
import com.atharchive.feature.reader.rememberReaderColors
import com.atharchive.feature.reader.selectionEdgeScroll
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A poetry reader, not a book page.
 *
 * The interaction model the whole screen is built around:
 *   tap a word          → its meaning, in a popover no bigger than the word needs
 *   long-press a verse  → شرح البيت / نسخ, next to the thumb
 *   margin dot          → this verse has commentary
 *   شرح and البحث       → the side panel, never inline between verses
 *   audio               → a compact strip above the controls
 *
 * Everything else stays out of the way: no permanent search field, no card around any
 * verse, no repeated icons down the margin.
 */
@Composable
fun PoemReaderScreen(
    state: PoemReaderUiState,
    onBack: () -> Unit,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    themeMode: com.atharchive.ui.theme.ThemeMode,
    onThemeModeChange: (com.atharchive.ui.theme.ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    playback: AudioPlaybackState = AudioPlaybackState(),
    onPlayAudio: (PoemAudioUi) -> Unit = {},
    onToggleAudio: () -> Unit = {},
    onSpeed: (Float) -> Unit = {},
    onRepeat: (Boolean) -> Unit = {},
    onSeekMs: (Long) -> Unit = {},
    onToggleBookmark: suspend (Int) -> Unit = {},
    onSaveVerse: suspend (Int) -> Unit = {},
    onRemoveVerse: suspend (Int) -> Unit = {},
    initialOrdinal: Int? = null,
    onPositionChange: (Int) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var settingsOpen by remember { mutableStateOf(false) }
    val colors = rememberReaderColors()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var panelMode by remember { mutableStateOf<PanelMode?>(null) }
    var sharhVerse by remember { mutableStateOf<VerseUi?>(null) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    var introOpen by remember { mutableStateOf(false) }
    if (introOpen) {
        androidx.activity.compose.BackHandler { introOpen = false }
        com.atharchive.feature.reader.IntroPage(state.card, state.title, state.poet, settings, colors, onDismiss = { introOpen = false })
        return
    }

    val track = state.recordings.firstOrNull { it.id == playback.id }
    val playing = track != null && playback.playWhenReady
    val position = if (track != null) (playback.positionMs / 1000).toInt() else 0
    val speed = playback.speed
    val repeat = playback.repeat
    var audioVisible by remember { mutableStateOf(true) }
    var followScroll by remember { mutableStateOf(initialOrdinal == null) }
    LaunchedEffect(toast) {
        if (toast != null) { delay(1700); toast = null }
    }
    val currentVerse = track?.cues?.lastOrNull { it.atSeconds <= playback.positionMs / 1000.0 }?.verseNumber

    // Following stops the moment the reader takes over, and offers its way back.
    // Drag interactions specifically, not isScrollInProgress: the auto-scroll below is
    // itself a scroll, so watching that flag would switch following off on the first cue.
    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followScroll = false
        }
    }
    val following = followScroll && settings.poemFollow
    LaunchedEffect(currentVerse, following) {
        val verse = currentVerse
        if (verse != null && following) {
            val index = state.verses.indexOfFirst { it.number == verse }
            if (index >= 0) listState.centreOn(index)
        }
    }

    androidx.activity.compose.BackHandler(panelMode != null || settingsOpen) {
        panelMode = null
        settingsOpen = false
    }
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(state.verses, state.readingPositionReady, state.bodyLoaded) {
        if (!restored) state.restoreIndex(initialOrdinal ?: state.readingOrdinal)?.let { index ->
            listState.scrollToItem(index)
            restored = true
        }
    }
    LaunchedEffect(restored, listState.isScrollInProgress) {
        if (restored && !listState.isScrollInProgress) state.verses.getOrNull(listState.firstVisibleItemIndex)?.let { onPositionChange(it.ordinal) }
    }

    fun openSharh(verse: VerseUi) {
        sharhVerse = verse
        panelMode = PanelMode.Sharh
    }
    val tabs = remember(state.verses) { state.panelTabs() }
    val context = LocalContext.current
    fun act(done: String, block: suspend () -> Unit) {
        scope.launch {
            toast = try { block(); done }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { "تعذّر الحفظ: ${error.message.orEmpty()}" }
        }
    }
    fun copy(text: String) {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("أثر", text))
    }

    // The book reader's bars: they slide away as the poem scrolls and come back on the way up
    // or with a tap on the text. They float over the list, so the verses never move.
    val bars = rememberCollapsingBars()
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    var bottomOverlayHeight by remember { mutableStateOf(0.dp) }
    val hidden = if (panelMode != null) 0f else bars.hidden
    val toggleBars: () -> Unit = { scope.launch { bars.toggle() } }
    // Compose offers no call to clear a SelectionContainer; a new one after a save drops it.
    var selectionEpoch by remember { mutableIntStateOf(0) }
    // Read through derived state: the first visible index changes verse by verse, and reading
    // it in the body recomposed the whole screen each time.
    val topVerse by remember { derivedStateOf { state.verses.getOrNull(listState.firstVisibleItemIndex) } }
    val saved = remember(state.saved) { state.saved.toHashSet() }
    val bookmarks = remember(state.bookmarks) { state.bookmarks.toHashSet() }
    fun progress(): Float = when {
        state.verses.size <= 1 -> 0f
        !listState.canScrollForward -> 1f
        else -> {
            val size = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val within = if (size <= 0) 0f else (listState.firstVisibleItemScrollOffset.toFloat() / size).coerceIn(0f, 1f)
            ((listState.firstVisibleItemIndex + within) / (state.verses.size - 1)).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .testTag("poem_reader_screen"),
    ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Ordinary selection: press and hold drags a normal selection across the poem,
                // with the book reader's own bar (نسخ · مشاركة · إلى الكناشة).
                com.atharchive.feature.reader.ProvideReaderSelectionBar(
                    colors = colors,
                    onCopied = { toast = "نُسخ النص" },
                    onShare = { text ->
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text),
                            "مشاركة النص"))
                    },
                    onSave = { top, bottom ->
                        // Poems save whole verses to الكناشة: every verse the selection touches.
                        // ponytail: only verses on screen; a selection dragged past the viewport keeps its visible part.
                        val info = listState.layoutInfo
                        val from = top.toInt() - info.beforeContentPadding
                        val to = bottom.toInt() - info.beforeContentPadding
                        val keys = info.visibleItemsInfo.filter { it.offset < to && it.offset + it.size > from }.map { it.key }.toSet()
                        val chosen = state.verses.filter { it.id in keys }
                        if (chosen.isEmpty()) toast = "تعذّر تحديد البيت"
                        else act(if (chosen.size == 1) "حُفظ في الكناشة" else "حُفظت ${arabicDigits(chosen.size)} أبيات في الكناشة") {
                            chosen.filter { it.ordinal !in saved }.forEach { onSaveVerse(it.ordinal) }
                        }
                        selectionEpoch++
                    },
                ) {
                androidx.compose.runtime.key(selectionEpoch) {
                SelectionContainer(Modifier.selectionEdgeScroll(listState)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().nestedScroll(bars.connection),
                        contentPadding = PaddingValues(top = 10.dp + topBarHeight, bottom = 30.dp + bottomBarHeight),
                    ) {
                        if (state.verses.isEmpty()) item {
                            if (!state.bodyLoaded) {
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(top = 96.dp)) {
                                    com.atharchive.ui.components.AtharLoading("جارٍ تحميل القصيدة…", Modifier.align(Alignment.Center))
                                }
                            } else Column(Modifier.padding(24.dp)) {
                                Text("لا توجد أبيات", color = colors.text)
                                androidx.compose.material3.TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
                            }
                        }
                        itemsIndexed(state.verses, key = { _, v -> v.id }) { index, verse ->
                            verse.heading?.let { PoemHeading(it, colors, settings) }
                            val isSaved = verse.ordinal in saved
                            VerseRow(
                                verse = verse,
                                colors = colors,
                                settings = settings,
                                glossary = state.glossary,
                                isCurrent = verse.number == currentVerse,
                                saved = isSaved,
                                bookmarked = verse.ordinal in bookmarks,
                                onTapText = toggleBars,
                                onSharh = { openSharh(verse) },
                                onSave = {
                                    if (isSaved) act("أُزيل من الكناشة") { onRemoveVerse(verse.ordinal) }
                                    else act("حُفظ في الكناشة") { onSaveVerse(verse.ordinal) }
                                },
                                onCopy = { copy(verse.sadr + "\n" + verse.ajz); toast = "نُسخ البيت" },
                            )
                            if (!settings.poemProse && index != state.verses.lastIndex) {
                                VerseOrnament(colors)
                            }
                        }
                        if (state.verses.isNotEmpty() && !state.bodyLoaded) item {
                            androidx.compose.material3.TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                                Text("تحميل بقية القصيدة", color = colors.accent)
                            }
                        }
                    }
                }
                }
                }

                // Return-to-position: only while following is off and audio is running.
                if (track != null && playing && settings.poemFollow && !followScroll) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomOverlayHeight + 14.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                            .clickable(role = Role.Button) { followScroll = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("poem_return_to_position"),
                    ) {
                        Text(
                            text = "العودة إلى موضع الاستماع",
                            color = colors.page,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                panelMode?.let { mode ->
                    Box(Modifier.fillMaxSize().padding(top = topBarHeight, bottom = bottomOverlayHeight)) {
                    PoemSidePanel(
                        tabs = tabs,
                        mode = mode,
                        onMode = { panelMode = it },
                        colors = colors,
                        state = state,
                        selectedVerseId = sharhVerse?.id,
                        onSelectVerse = { sharhVerse = it },
                        query = query,
                        onQuery = { query = it },
                        onJump = { index ->
                            followScroll = false
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                        onDismiss = { panelMode = null },
                    )
                    }
                }

                toast?.let { message ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomOverlayHeight + 16.dp)
                            .clip(AtharCorners.Card)
                            .background(colors.text.copy(alpha = 0.92f))
                            .padding(horizontal = 15.dp, vertical = 8.dp)
                            .testTag("poem_toast"),
                    ) {
                        Text(message, color = colors.page, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Box(
                Modifier.align(Alignment.TopCenter)
                    .background(colors.page)
                    .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } }
                    .graphicsLayer { translationY = -hidden * size.height; alpha = 1f - hidden },
            ) {
            PoemTopBar(
                title = state.title,
                poet = state.poet,
                colors = colors,
                onBack = onBack,
                onSearch = { panelMode = if (panelMode == PanelMode.Search) null else PanelMode.Search },
                onMore = { moreOpen = true },
                menu = {
                    // The prose view is Aa «العرض: متصل», not a second screen.
                    val poemText = { buildString {
                        append(state.title)
                        if (state.poet.isNotBlank()) append(" — ").append(state.poet)
                        state.verses.forEach { append("\n\n").append(it.sadr).append("\n").append(it.ajz) }
                    } }
                    AtharMenu(
                        expanded = moreOpen,
                        onDismiss = { moreOpen = false },
                        colors = AtharMenuColors(card = lerp(colors.page, colors.text, 0.07f), ink = colors.text,
                            secondary = colors.secondary, accent = colors.accent),
                        modifier = Modifier.testTag("poem_more_menu"),
                    ) {
                        if (state.card.isNotEmpty()) AtharMenuItem("مقدمة المحقق ومعلومات عن الكتاب", icon = AtharIcons.Info, onClick = { moreOpen = false; introOpen = true })
                        AtharMenuItem("نسخ القصيدة", icon = AtharIcons.Copy, onClick = {
                            moreOpen = false
                            copy(poemText())
                            toast = "نُسخت القصيدة"
                        })
                        AtharMenuItem("مشاركة القصيدة", icon = AtharIcons.Share, onClick = {
                            moreOpen = false
                            context.startActivity(android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, poemText()),
                                "مشاركة القصيدة"))
                        })
                    }
                },
            )
            }

            // Progress, the audio strip and the bottom bar; the first two ride down to the
            // screen's foot when the bar hides — as in the book reader.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { bottomOverlayHeight = with(density) { it.height.toDp() } }
                    .graphicsLayer { translationY = hidden * bottomBarHeight.toPx() },
            ) {
            com.atharchive.feature.reader.ReaderProgressStrip(
                count = state.verses.size,
                colors = colors,
                progress = ::progress,
                jumpLabel = "الانتقال في القصيدة",
                onJump = { index -> followScroll = false; scope.launch { listState.scrollToItem(index) } },
            ) { index, percent ->
                val verse = state.verses.getOrNull(index)
                val heading = state.verses.subList(0, (index + 1).coerceAtMost(state.verses.size)).lastOrNull { it.heading != null }?.heading
                (if (verse == null) percent else "البيت ${arabicDigits(verse.number)} / ${arabicDigits(state.verses.last().number)}") to
                    listOfNotNull(heading, percent.takeIf { verse != null }).joinToString(" · ")
            }

            track?.takeIf { audioVisible }?.let { current ->
                PoemAudioBar(
                    track = current,
                    recordings = state.recordings,
                    playing = playing,
                    position = position,
                    speed = speed,
                    repeat = repeat,
                    colors = colors,
                    onToggle = onToggleAudio,
                    onSelect = { onPlayAudio(it); followScroll = true },
                    onSpeed = onSpeed,
                    onRepeat = { onRepeat(!repeat) },
                    onSeek = { onSeekMs((it * current.durationSeconds * 1000).toLong()) },
                )
            }

            // The book reader's own bar: one set of controls for every text.
            val bookmarked = topVerse?.let { it.ordinal in bookmarks } == true
            Box(
                Modifier.onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }
                    .graphicsLayer { alpha = 1f - hidden },
            ) {
            com.atharchive.feature.reader.ReaderBottomBar(
                colors = colors,
                bookmarked = bookmarked,
                hasAudio = state.recordings.isNotEmpty(),
                audioOpen = track != null && audioVisible,
                contentsOpen = panelMode != null,
                onContents = { panelMode = if (panelMode != null) null else tabs.first() },
                onBookmark = {
                    topVerse?.let { verse -> act(if (bookmarked) "أُزيلت العلامة" else "✓ أُضيفت العلامة") { onToggleBookmark(verse.ordinal) } }
                },
                onAudio = {
                    if (track == null) {
                        state.recordings.firstOrNull()?.let(onPlayAudio)
                        audioVisible = true
                        followScroll = true
                    } else audioVisible = !audioVisible
                },
                settingsOpen = settingsOpen,
                onSettings = { settingsOpen = true },
                settingsMenu = {
                    ReaderSettingsPopover(settingsOpen, settings, colors, onSettingsChange, onDismiss = { settingsOpen = false }, themeMode, onThemeModeChange, forPoem = true)
                },
            )
            }
            }

    }
}

/* ── verse ─────────────────────────────────────────────────────────────── */

/** A glossary word and where it sits in the rendered line. */
private data class WordSpan(val start: Int, val end: Int, val word: String, val meaning: String)

/** The open word, plus the rect it occupies in its own Text's coordinate space. */
private data class OpenWord(val span: WordSpan, val bounds: Rect)

/**
 * The rect a span occupies, in the text's own coordinates.
 *
 * Under RTL the first character sits to the right of the last, so the horizontal
 * extent is a min/max rather than first.left..last.right. A word broken across two
 * lines has no single rect; the first line's box is the honest anchor.
 */
private fun TextLayoutResult.boundsOf(span: WordSpan): Rect {
    val first = getBoundingBox(span.start)
    val last = getBoundingBox((span.end - 1).coerceAtLeast(span.start))
    if (first.top != last.top) return first
    return Rect(
        minOf(first.left, last.left),
        first.top,
        maxOf(first.right, last.right),
        first.bottom,
    )
}

@Composable
private fun VerseRow(
    verse: VerseUi,
    colors: ReaderColors,
    settings: ReaderSettings,
    glossary: Map<String, String>,
    isCurrent: Boolean,
    saved: Boolean,
    bookmarked: Boolean,
    onTapText: () -> Unit,
    onSharh: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // A saved verse answers a tap with its menu (إزالة من الكناشة), as a highlight does in a book.
    val tapText = if (saved) ({ menuOpen = true }) else onTapText
    // «التظليل: خط» draws a saved verse as a line under its words instead of a tinted row.
    val underline = saved && settings.underlineHighlights
    Box(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isCurrent -> colors.accent.copy(alpha = 0.10f)
                    saved && !settings.underlineHighlights -> colors.saved
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .padding(horizontal = settings.margin.dp, vertical = 10.dp)
            .testTag("verse_${verse.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        // Outer edge (right, under RTL): the number when shown, a dot for شرح, a mark for a
        // bookmark. Nothing to show takes no room, so the verse sits on the margin.
        if (settings.verseNumbers || verse.commentary.isNotEmpty() || bookmarked) Column(
            modifier = Modifier
                .size(width = if (settings.verseNumbers) 28.dp else 16.dp, height = 40.dp)
                .clickable(role = Role.Button, onClickLabel = "خيارات البيت") { menuOpen = true }
                .semantics { contentDescription = "البيت ${arabicDigits(verse.number)}" }
                .testTag("verse_indicator_${verse.id}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            if (settings.verseNumbers) {
                Text(
                    text = arabicDigits(verse.number),
                    color = if (verse.commentary.isNotEmpty()) colors.accent else colors.secondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            } else if (verse.commentary.isNotEmpty()) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
            if (bookmarked) Icon(AtharIcons.BookmarkFilled, contentDescription = "عليه علامة", tint = colors.accent,
                modifier = Modifier.padding(top = 2.dp).size(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            if (settings.poemProse) {
                // متصل: one running line per verse, as a book prints a quoted verse.
                Hemistich(if (verse.ajz.isBlank()) verse.sadr else "${verse.sadr} \u2026 ${verse.ajz}", glossary, colors, settings, TextAlign.Start, tapText, underline)
            } else {
                Hemistich(verse.sadr, glossary, colors, settings, TextAlign.Start, tapText, underline)
                Spacer(Modifier.height(2.dp))
                Hemistich(verse.ajz, glossary, colors, settings, TextAlign.End, tapText, underline)
            }
        }
    }
        AtharMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            colors = AtharMenuColors(card = lerp(colors.page, colors.text, 0.07f), ink = colors.text,
                secondary = colors.secondary, accent = colors.accent),
            modifier = Modifier.testTag("verse_menu"),
        ) {
            if (verse.commentary.isNotEmpty()) AtharMenuItem("شرح البيت", icon = AtharIcons.Sidebar, onClick = { menuOpen = false; onSharh() })
            if (saved) AtharMenuItem("إزالة من الكناشة", icon = AtharIcons.Close, destructive = true, onClick = { menuOpen = false; onSave() })
            else AtharMenuItem("حفظ في الكناشة", icon = AtharIcons.Kannashah, onClick = { menuOpen = false; onSave() })
            AtharMenuItem("نسخ البيت", icon = AtharIcons.Copy, onClick = { menuOpen = false; onCopy() })
        }
    }
}

/**
 * One hemistich as a single [Text].
 *
 * It was a FlowRow of per-word Texts, which gave clean tap targets but broke ordinary
 * text selection — you could not drag across a line to copy it. One Text restores
 * selection and correct Arabic shaping; a tap is mapped back to a word through the
 * layout result, and glossary words are underlined so it is visible which ones respond.
 */
@Composable
private fun Hemistich(
    sourceLine: String,
    glossary: Map<String, String>,
    colors: ReaderColors,
    settings: ReaderSettings,
    align: TextAlign,
    onTapText: () -> Unit,
    underline: Boolean = false,
) {
    val tapText by androidx.compose.runtime.rememberUpdatedState(onTapText)
    val line = if (settings.showTashkeel) sourceLine else com.atharchive.feature.reader.SelectionText(sourceLine, false).text
    // Keyed on the glossary, not the whole state: comparing the state compared every verse.
    val spans = remember(line, glossary) {
        val out = mutableListOf<WordSpan>()
        var index = 0
        line.split(' ').forEach { word ->
            if (word.isNotBlank()) {
                val meaning = glossary[stripHarakat(word)]
                if (meaning != null) out += WordSpan(index, index + word.length, word, meaning)
            }
            index += word.length + 1
        }
        out
    }
    // A faint accent ground, not an underline.
    //
    // An underline under vocalised Amiri runs straight through the descenders and the
    // lower harakat, and two glossary words side by side produced one continuous rule
    // across both. A background span is drawn from the font's own ascent to descent,
    // so it clears the tashkeel, and because the space between two words belongs to
    // neither span they always read as two separate marks. It is also the treatment
    // the app already uses for search matches, so the meaning of the mark carries over.
    val annotated = remember(spans, line, colors) {
        buildAnnotatedString {
            append(line)
            spans.forEach { span ->
                addStyle(
                    SpanStyle(background = colors.accent.copy(alpha = 0.12f)),
                    span.start,
                    span.end,
                )
            }
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var open by remember { mutableStateOf<OpenWord?>(null) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = annotated,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!underline) Modifier else Modifier.drawBehind {
                    layout?.let { drawHighlightLine(it, 0, it.layoutInput.text.length, colors.accent.copy(alpha = 0.8f)) }
                })
                // Tap only. Long-press is deliberately left alone so the surrounding
                // SelectionContainer can start an ordinary text selection.
                .pointerInput(annotated) {
                    detectTapGestures { pos ->
                        val result = layout ?: return@detectTapGestures
                        val offset = result.getOffsetForPosition(pos)
                        val hit = spans.firstOrNull { offset >= it.start && offset < it.end }
                        if (hit == null) tapText() else open = OpenWord(hit, result.boundsOf(hit))
                    }
                },
            onTextLayout = { layout = it },
            color = colors.text,
            fontFamily = AtharEditorialFontFamily,
            // Verses run a shade larger than prose, and take the reader's own spacing.
            fontSize = (settings.fontSize + 1).sp,
            lineHeight = ((settings.fontSize + 1) * settings.spacing.multiplier).sp,
            textAlign = align,
        )
        open?.let { word ->
            WordCallout(word, colors) { open = null }
        }
    }
}

/**
 * The meaning, as a callout pinned to the word itself (see [AtharTailPopover]).
 *
 * Inverted ground, like the reader's own toast — a tooltip reads as ephemeral, and an
 * inverted pill needs no border to separate itself from the page in any palette.
 */
@Composable
private fun WordCallout(
    word: OpenWord,
    colors: ReaderColors,
    onDismiss: () -> Unit,
) {
    AtharTailPopover(
        anchor = { bounds -> word.bounds.translate(bounds.left.toFloat(), bounds.top.toFloat()) },
        ground = colors.text.copy(alpha = 0.94f),
        onDismiss = onDismiss,
        modifier = Modifier.testTag("word_popover"),
    ) {
        Text(
            text = word.span.word,
            color = colors.page,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = AtharTextSize.Ui,
        )
        Text(
            text = word.span.meaning,
            modifier = Modifier.padding(top = 2.dp),
            color = colors.page.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 20.sp,
        )
    }
}

/** A باب heading, in the flow where the poem prints it. */
@Composable
private fun PoemHeading(text: String, colors: ReaderColors, settings: ReaderSettings) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = settings.margin.dp).padding(top = 18.dp, bottom = 6.dp),
        textAlign = TextAlign.Center,
        color = colors.accent,
        fontFamily = AtharEditorialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (settings.fontSize + 1).sp,
    )
}

@Composable
private fun VerseOrnament(colors: ReaderColors) {
    Text(
        text = "\u25C8",
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        textAlign = TextAlign.Center,
        color = colors.accent.copy(alpha = 0.28f),
        fontSize = AtharTextSize.Caption,
    )
}

/* ── chrome ────────────────────────────────────────────────────────────── */

@Composable
private fun PoemTopBar(
    title: String,
    poet: String,
    colors: ReaderColors,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    menu: @Composable () -> Unit = {},
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(AtharIcons.Close, "إغلاق", colors, "poem_close", onBack)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    color = colors.text,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = AtharTextSize.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = poet,
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            BarIcon(AtharIcons.Search, "البحث في القصيدة", colors, "poem_search", onSearch)
            Box {
                BarIcon(AtharIcons.More, "المزيد", colors, "poem_more", onMore)
                menu()
            }
        }
        HorizontalDivider(thickness = 0.7.dp, color = colors.divider)
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    label: String,
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
        Icon(icon, contentDescription = label, tint = colors.secondary, modifier = Modifier.size(24.dp))
    }
}

/**
 * Glides [index] to the middle of the list, so the recited verse sits clear of both bars.
 * One eased scroll by the exact distance when the verse is on screen (it already is, verse
 * after verse), instead of animateScrollToItem's snap-to-top that jumped on every cue.
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.centreOn(index: Int) {
    fun offsetToCentre(): Float? {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        val viewport = info.viewportEndOffset - info.viewportStartOffset - info.beforeContentPadding - info.afterContentPadding
        return item.offset - (viewport - item.size) / 2f // offsets start after the top padding
    }
    if (offsetToCentre() == null) scrollToItem(index) // far away: land first, then glide
    val delta = offsetToCentre() ?: return
    if (kotlin.math.abs(delta) < 4f) return
    animateScrollBy(delta, androidx.compose.animation.core.tween(durationMillis = 650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
}
