package com.atharchive.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onSaveBenefit: (String) -> Unit,
    onPositionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var settings by remember { mutableStateOf(ReaderSettings()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var panelTab by remember { mutableStateOf(ReaderPanelTab.Contents) }
    var audioOpen by remember { mutableStateOf(false) }
    var selectedBlock by remember { mutableStateOf<String?>(null) }
    var highlighted by remember { mutableStateOf(setOf<String>()) }
    var bookmarkedIndex by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var jumpedTo by remember { mutableIntStateOf(-1) }
    // The toolbar follows the selection to the nearer half of the screen, so it lands
    // close to the thumb instead of always at the bottom.
    var selectionAlignment by remember { mutableStateOf(Alignment.BottomCenter) }

    val colors = rememberReaderColors(settings.palette)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.readingPositionIndex)
    val scope = rememberCoroutineScopeCompat()

    // Top bar hides on downward scroll only, and returns the instant the reader scrolls up.
    // The rest of the interface is never hidden.
    var topBarVisible by remember { mutableStateOf(true) }
    var lastIndex by remember { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var lastOffset by remember { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val delta = if (index != lastIndex) index - lastIndex else 0
                val movedDown = delta > 0 || (delta == 0 && offset > lastOffset + 6)
                val movedUp = delta < 0 || (delta == 0 && offset < lastOffset - 6)
                if (movedDown && index > 0) topBarVisible = false
                if (movedUp) topBarVisible = true
                lastIndex = index
                lastOffset = offset
            }
    }

    // Reading position is written continuously. There is no "save position" control,
    // and this is not a bookmark.
    val currentIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(currentIndex) { onPositionChange(currentIndex) }

    val hits = remember(query, state.blocks) { findHits(state, query) }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1800)
            toast = null
        }
    }

    fun jumpTo(index: Int) {
        jumpedTo = index
        scope.launch { listState.scrollToItem(index.coerceIn(0, state.blocks.lastIndex)) }
    }

    val chapterTitle = remember(state.toc, currentIndex) {
        state.toc.lastOrNull { it.blockIndex <= currentIndex }?.title.orEmpty()
    }
    val progress by remember {
        derivedStateOf {
            if (state.blocks.size <= 1) 0f
            else currentIndex.toFloat() / (state.blocks.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .testTag("reader_screen"),
    ) {
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
                onMore = { toast = "قائمة إضافية: نسخ المصدر، معلومات الكتاب، مشاركة" },
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { selectedBlock = null },
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 40.dp,
                start = 22.dp,
                end = 22.dp,
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
            itemsIndexed(state.blocks, key = { _, b -> b.id }) { index, block ->
                BlockView(
                    block = block,
                    settings = settings,
                    colors = colors,
                    selected = selectedBlock == block.id,
                    highlighted = block.id in highlighted,
                    isSearchTarget = query.isNotBlank() && jumpedTo == index,
                    onLongPress = { fromTop ->
                        selectedBlock = block.id
                        selectionAlignment = if (fromTop) Alignment.BottomCenter else Alignment.TopCenter
                    },
                )
            }
        }

        // ---- progress, kept subtly visible ----
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.divider.copy(alpha = 0.5f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(colors.accent.copy(alpha = 0.65f))
                    .testTag("reader_progress"),
            )
        }

        // ---- selection toolbar ----
        if (selectedBlock != null) {
            SelectionToolbar(
                colors = colors,
                modifier = Modifier
                    .align(selectionAlignment)
                    .padding(vertical = 10.dp),
                onCopy = {
                    selectedBlock = null
                    toast = "نُسخ النص"
                },
                onShare = {
                    selectedBlock = null
                    toast = "مشاركة النص"
                },
                onHighlight = {
                    selectedBlock?.let {
                        highlighted = highlighted + it
                        onSaveBenefit(it)
                    }
                    selectedBlock = null
                    toast = "حُفظ في الكناشة"
                },
            )
        }
        }

        if (audioOpen && state.audio.isNotEmpty()) {
            ReaderAudioStrip(
                audio = state.audio,
                colors = colors,
                onPlay = { toast = "تشغيل ${it.title}" },
                onClose = { audioOpen = false },
            )
        }

        // A normal bottom bar, always visible — the same shape as the one outside the
        // reader, just with the reader's own actions.
        ReaderBottomBar(
            colors = colors,
            bookmarked = bookmarkedIndex == currentIndex,
            hasAudio = state.audio.isNotEmpty(),
            audioOpen = audioOpen,
            onContents = { panelOpen = true },
            onAudio = { audioOpen = !audioOpen },
            onSettings = { settingsOpen = true },
            onBookmark = {
                if (bookmarkedIndex == currentIndex) {
                    bookmarkedIndex = null
                    toast = "أُزيلت الإشارة"
                } else {
                    bookmarkedIndex = currentIndex
                    toast = "✓ أُضيفت الإشارة"
                }
            },
        )
      }

        if (panelOpen) {
            ReaderPanel(
                state = state,
                colors = colors,
                currentBlockIndex = currentIndex,
                query = query,
                hits = hits,
                onQueryChange = { query = it },
                // The panel stays open: a reader comparing places should not have to
                // reopen it after every jump.
                onJump = ::jumpTo,
                onDismiss = { panelOpen = false },
                initialTab = panelTab,
            )
        }

        toast?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(10.dp))
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
            onChange = { settings = it },
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
    onMore: () -> Unit,
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
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chapterTitle.isNotBlank()) {
                Text(
                    text = chapterTitle,
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconSlot(AtharIcons.More, "المزيد", colors, "reader_more", onMore)
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
            .size(42.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.secondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ReaderBottomBar(
    colors: ReaderColors,
    bookmarked: Boolean,
    hasAudio: Boolean,
    audioOpen: Boolean,
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
            ControlButton(AtharIcons.Books, "المحتويات", colors, false, "reader_ctl_contents", Modifier.weight(1f), onContents)
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
                AtharIcons.Bookmark,
                if (bookmarked) "إزالة الإشارة" else "إضافة إشارة",
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
            modifier = Modifier.size(19.dp),
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
            color = colors.secondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
            .clip(RoundedCornerShape(12.dp))
            .background(colors.page)
            .border(0.7.dp, colors.divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 2.dp)
            .testTag("reader_selection_toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionAction(AtharIcons.Copy, "نسخ", colors, false, "reader_sel_copy", onCopy)
        ToolbarSeparator(colors)
        SelectionAction(AtharIcons.Share, "مشاركة", colors, false, "reader_sel_share", onShare)
        ToolbarSeparator(colors)
        SelectionAction(AtharIcons.Bookmark, "تظليل", colors, true, "reader_sel_highlight", onHighlight)
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
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .testTag("reader_audio_close"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AtharIcons.Close,
                    contentDescription = "إغلاق",
                    tint = colors.secondary,
                    modifier = Modifier.size(14.dp),
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
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AtharIcons.Play,
                        contentDescription = null,
                        tint = colors.page,
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
                    fontSize = 14.sp,
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
