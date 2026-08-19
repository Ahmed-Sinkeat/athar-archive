package com.atharchive.feature.poemreader

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.atharchive.feature.reader.ReaderColors
import com.atharchive.feature.reader.ReaderSettings
import com.atharchive.feature.reader.ReaderSettingsPopover
import com.atharchive.feature.reader.rememberReaderColors
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
    modifier: Modifier = Modifier,
) {
    var settings by remember { mutableStateOf(ReaderSettings()) }
    var settingsOpen by remember { mutableStateOf(false) }
    val colors = rememberReaderColors(settings.palette)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var panelMode by remember { mutableStateOf<PanelMode?>(null) }
    var sharhVerse by remember { mutableStateOf<VerseUi?>(null) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    // audio
    var track by remember { mutableStateOf<PoemAudioUi?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var speed by remember { mutableStateOf(1.0f) }
    var repeat by remember { mutableStateOf(false) }
    var followScroll by remember { mutableStateOf(true) }

    LaunchedEffect(toast) {
        if (toast != null) { delay(1700); toast = null }
    }

    // ponytail: a 1s ticker stands in for the media engine so verse-following is
    // reviewable. Replace with media3 position callbacks; nothing else changes.
    LaunchedEffect(playing, track, speed) {
        val current = track ?: return@LaunchedEffect
        while (playing) {
            delay((1000L / speed).toLong())
            position = if (position >= current.durationSeconds) {
                if (repeat) 0 else { playing = false; current.durationSeconds }
            } else {
                position + 1
            }
        }
    }

    // Which verse the recording is on. Verse-level only: `poem-timing` carries {v,t}
    // and nothing finer, so no word is ever highlighted.
    val currentVerse by remember {
        derivedStateOf {
            val cues = track?.cues.orEmpty()
            if (cues.isEmpty()) null
            else cues.lastOrNull { it.atSeconds <= position }?.verseNumber
        }
    }

    // Following stops the moment the reader takes over, and offers its way back.
    // Drag interactions specifically, not isScrollInProgress: the auto-scroll below is
    // itself a scroll, so watching that flag would switch following off on the first cue.
    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followScroll = false
        }
    }
    LaunchedEffect(currentVerse, followScroll) {
        val verse = currentVerse
        if (verse != null && followScroll) {
            val index = state.verses.indexOfFirst { it.number == verse }
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }

    fun openSharh(verse: VerseUi) {
        sharhVerse = verse
        panelMode = PanelMode.Sharh
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .testTag("poem_reader_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PoemTopBar(
                title = state.title,
                poet = state.poet,
                colors = colors,
                onBack = onBack,
                onSearch = { panelMode = PanelMode.Search },
                onMore = { toast = "قائمة إضافية: نسخ القصيدة، معلومات، مشاركة" },
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Ordinary selection: press and hold drags a normal selection across
                // the poem, with the platform's own copy toolbar.
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 30.dp),
                    ) {
                        itemsIndexed(state.verses, key = { _, v -> v.id }) { index, verse ->
                            VerseRow(
                                verse = verse,
                                colors = colors,
                                settings = settings,
                                state = state,
                                isCurrent = verse.number == currentVerse,
                                onSharh = { openSharh(verse) },
                            )
                            if (index != state.verses.lastIndex) {
                                VerseOrnament(colors)
                            }
                        }
                    }
                }

                // Return-to-position: only while following is off and audio is running.
                if (track != null && playing && !followScroll) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(99.dp))
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
                    PoemSidePanel(
                        mode = mode,
                        onMode = { panelMode = it },
                        colors = colors,
                        state = state,
                        selectedVerseId = sharhVerse?.id,
                        onSelectVerse = { sharhVerse = it },
                        query = query,
                        onQuery = { query = it },
                        onJump = { index ->
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                        onDismiss = { panelMode = null },
                    )
                }

                toast?.let { message ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.text.copy(alpha = 0.92f))
                            .padding(horizontal = 15.dp, vertical = 8.dp)
                            .testTag("poem_toast"),
                    ) {
                        Text(message, color = colors.page, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            track?.let { current ->
                PoemAudioBar(
                    track = current,
                    recordings = state.recordings,
                    playing = playing,
                    position = position,
                    speed = speed,
                    repeat = repeat,
                    colors = colors,
                    onToggle = { playing = !playing },
                    onSelect = {
                        track = it
                        position = 0
                        playing = true
                        followScroll = true
                    },
                    onSpeed = { speed = it },
                    onRepeat = { repeat = !repeat },
                )
            }

            PoemBottomBar(
                colors = colors,
                audioOn = track != null,
                panelOpen = panelMode != null,
                onMore = { panelMode = if (panelMode != null) null else PanelMode.Sharh },
                onAudio = {
                    if (track == null) {
                        track = state.recordings.first()
                        position = 0
                        playing = true
                        followScroll = true
                    } else {
                        track = null
                        playing = false
                    }
                },
                onFont = { settingsOpen = true },
            )
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
}

/* ── verse ─────────────────────────────────────────────────────────────── */

/**
 * Places the popover just below the tapped line, centred on it and kept inside the
 * window — flipping above when there is no room beneath.
 *
 * Popup's `alignment` can only position the content *within* the anchor's bounds, so it
 * always overlapped the verse; and `offset` is mirrored under RTL while the tap
 * coordinate from detectTapGestures is not, so combining the two fought itself.
 */
private class BelowAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - 16).coerceAtLeast(16)
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(16, maxX)
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(16)
        }
        return IntOffset(x, y)
    }
}

/** A glossary word and where it sits in the rendered line. */
private data class WordSpan(val start: Int, val end: Int, val word: String, val meaning: String)

@Composable
private fun VerseRow(
    verse: VerseUi,
    colors: ReaderColors,
    settings: ReaderSettings,
    state: PoemReaderUiState,
    isCurrent: Boolean,
    onSharh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) colors.accent.copy(alpha = 0.10f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .padding(start = 8.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
            .testTag("verse_${verse.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        // Outer edge (right, under RTL). The only mark a verse ever carries, and the
        // fastest way into its commentary.
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 40.dp)
                .then(
                    if (verse.commentary.isNotEmpty()) {
                        Modifier.clickable(role = Role.Button, onClick = onSharh)
                    } else {
                        Modifier
                    },
                )
                .testTag("verse_indicator_${verse.id}"),
            contentAlignment = Alignment.Center,
        ) {
            if (verse.commentary.isNotEmpty()) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Hemistich(verse.sadr, state, colors, settings, TextAlign.Start)
            Spacer(Modifier.height(2.dp))
            Hemistich(verse.ajz, state, colors, settings, TextAlign.End)
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
    line: String,
    state: PoemReaderUiState,
    colors: ReaderColors,
    settings: ReaderSettings,
    align: TextAlign,
) {
    val spans = remember(line, state) {
        val out = mutableListOf<WordSpan>()
        var index = 0
        line.split(' ').forEach { word ->
            if (word.isNotBlank()) {
                val meaning = state.meaningOf(word)
                if (meaning != null) out += WordSpan(index, index + word.length, word, meaning)
            }
            index += word.length + 1
        }
        out
    }
    val annotated = remember(spans, line, colors) {
        buildAnnotatedString {
            append(line)
            spans.forEach { span ->
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    span.start,
                    span.end,
                )
            }
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var open by remember { mutableStateOf<WordSpan?>(null) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = annotated,
            modifier = Modifier
                .fillMaxWidth()
                // Tap only. Long-press is deliberately left alone so the surrounding
                // SelectionContainer can start an ordinary text selection.
                .pointerInput(annotated) {
                    detectTapGestures { pos ->
                        val result = layout ?: return@detectTapGestures
                        val offset = result.getOffsetForPosition(pos)
                        open = spans.firstOrNull { offset >= it.start && offset < it.end }
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
        open?.let { span ->
            WordPopover(span.word, span.meaning, colors) { open = null }
        }
    }
}

/** Small enough to read over the poem without displacing it. */
@Composable
private fun WordPopover(
    word: String,
    meaning: String,
    colors: ReaderColors,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = BelowAnchor(gapPx = 10),
        onDismissRequest = onDismiss,
        // focusable, or an outside tap never reaches onDismissRequest and it sticks open
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.page)
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("word_popover"),
        ) {
            Text(
                text = word,
                color = colors.accent,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = meaning,
                modifier = Modifier.padding(top = 3.dp),
                color = colors.text.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun VerseOrnament(colors: ReaderColors) {
    Text(
        text = "\u25C8",
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        textAlign = TextAlign.Center,
        color = colors.accent.copy(alpha = 0.28f),
        fontSize = 11.sp,
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
                    fontSize = 17.sp,
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
            BarIcon(AtharIcons.More, "المزيد", colors, "poem_more", onMore)
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
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = colors.secondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PoemBottomBar(
    colors: ReaderColors,
    audioOn: Boolean,
    panelOpen: Boolean,
    onMore: () -> Unit,
    onAudio: () -> Unit,
    onFont: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(thickness = 0.7.dp, color = colors.divider)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlSlot(AtharIcons.Sidebar, "المزيد", colors, panelOpen, Modifier.weight(1f), "poem_ctl_more", onMore)
            ControlSlot(AtharIcons.Audio, "الصوت", colors, audioOn, Modifier.weight(1f), "poem_ctl_audio", onAudio)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onFont)
                    .padding(vertical = 5.dp)
                    .testTag("poem_ctl_font"),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aa", color = colors.secondary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("الخط", color = colors.secondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ControlSlot(
    icon: ImageVector,
    label: String,
    colors: ReaderColors,
    active: Boolean,
    modifier: Modifier,
    tag: String,
    onClick: () -> Unit,
) {
    val tint = if (active) colors.accent else colors.secondary
    Box(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 5.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
        }
    }
}
