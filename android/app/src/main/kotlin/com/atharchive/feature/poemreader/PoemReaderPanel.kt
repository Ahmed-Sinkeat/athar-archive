package com.atharchive.feature.poemreader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.feature.reader.ReaderColors
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily

/**
 * The same side-panel idiom as the book reader: a panel on the start edge — the right,
 * under RTL — with the poem still visible behind it, so the reader never loses their
 * place. Explanation is never inserted between verses.
 */
@Composable
internal fun PoemSidePanel(
    mode: PanelMode,
    onMode: (PanelMode) -> Unit,
    colors: ReaderColors,
    state: PoemReaderUiState,
    selectedVerseId: String?,
    onSelectVerse: (VerseUi) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.78f)
                .fillMaxHeight()
                .background(colors.page)
                .testTag("poem_panel"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelTab(PanelMode.Sharh, mode, colors, onMode)
                Spacer(Modifier.width(16.dp))
                PanelTab(PanelMode.Search, mode, colors, onMode)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .testTag("poem_panel_close"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AtharIcons.Close, "إغلاق", tint = colors.secondary, modifier = Modifier.size(15.dp))
                }
            }
            HorizontalDivider(thickness = 0.7.dp, color = colors.divider)

            when (mode) {
                PanelMode.Sharh -> SharhBody(state, selectedVerseId, onSelectVerse, onJump, colors)
                PanelMode.Search -> SearchBody(state, query, onQuery, onJump, colors)
            }
        }
        // Scrim second, so it lands on the far edge and the panel keeps the start side.
        Box(
            modifier = Modifier
                .weight(0.22f)
                .fillMaxHeight()
                .background(colors.text.copy(alpha = 0.25f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}

@Composable
private fun PanelTab(
    tab: PanelMode,
    selected: PanelMode,
    colors: ReaderColors,
    onSelect: (PanelMode) -> Unit,
) {
    val active = tab == selected
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
            ) { onSelect(tab) }
            .testTag("poem_panel_tab_${tab.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tab.label,
            color = if (active) colors.accent else colors.secondary,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(26.dp)
                .height(2.dp)
                .background(if (active) colors.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

/**
 * Every explained verse in the poem, in order — not just the one last tapped. Tapping an
 * entry moves the poem behind the panel to that verse, so the panel is a way of walking
 * the commentary rather than a dead end.
 */
@Composable
private fun SharhBody(
    state: PoemReaderUiState,
    selectedVerseId: String?,
    onSelectVerse: (VerseUi) -> Unit,
    onJump: (Int) -> Unit,
    colors: ReaderColors,
) {
    val explained = remember(state.verses) {
        state.verses.withIndex().filter { it.value.commentary.isNotEmpty() }
    }
    if (explained.isEmpty()) {
        PanelEmpty("لا شرح في هذه القصيدة", "ستظهر هنا شروح الأبيات عند توفرها.", colors)
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedVerseId) {
        val at = explained.indexOfFirst { it.value.id == selectedVerseId }
        if (at >= 0) listState.animateScrollToItem(at)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "${arabicNumber(explained.size)} أبيات مشروحة",
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
            color = colors.secondary,
            style = MaterialTheme.typography.labelSmall,
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(explained, key = { it.value.id }) { (index, verse) ->
                val selected = verse.id == selectedVerseId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onSelectVerse(verse); onJump(index) }
                        .background(
                            if (selected) colors.accent.copy(alpha = 0.05f)
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                        .testTag("poem_sharh_${verse.id}"),
                ) {
                    Text(
                        text = "البيت ${arabicNumber(verse.number)}",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.accent.copy(alpha = 0.06f))
                            .padding(10.dp),
                    ) {
                        Text(verse.sadr, color = colors.text, fontFamily = AtharEditorialFontFamily, fontSize = 14.sp, lineHeight = 26.sp)
                        Text(verse.ajz, color = colors.text, fontFamily = AtharEditorialFontFamily, fontSize = 14.sp, lineHeight = 26.sp)
                    }
                    // Every commentator at once: with only one or two per verse a picker
                    // would hide half the content behind a tap.
                    verse.commentary.forEach { source ->
                        Text(
                            text = source.sourceLabel,
                            modifier = Modifier.padding(top = 10.dp),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = source.text,
                            modifier = Modifier.padding(top = 4.dp),
                            color = colors.text.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 26.sp,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    thickness = 0.7.dp,
                    color = colors.divider.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun arabicNumber(n: Int): String = arabicDigits(n.toString())

@Composable
private fun SearchBody(
    state: PoemReaderUiState,
    query: String,
    onQuery: (String) -> Unit,
    onJump: (Int) -> Unit,
    colors: ReaderColors,
) {
    val hits = remember(query, state.verses) {
        val term = query.trim()
        if (term.isBlank()) emptyList()
        else state.verses.withIndex().filter { (_, v) ->
            v.sadr.contains(term) || v.ajz.contains(term) ||
                stripHarakat(v.sadr).contains(stripHarakat(term)) ||
                stripHarakat(v.ajz).contains(stripHarakat(term))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.text.copy(alpha = 0.05f))
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().testTag("poem_panel_search_field"),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("ابحث في القصيدة…", color = colors.secondary, fontSize = 14.sp)
                    }
                    inner()
                },
            )
        }
        if (query.isBlank()) {
            PanelEmpty("ابحث في أبيات القصيدة", "اكتب كلمة أو شطرًا للانتقال إليه.", colors)
        } else if (hits.isEmpty()) {
            PanelEmpty("لا نتائج", "جرّب كلمة أقصر، أو بلا تشكيل.", colors)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(hits, key = { it.value.id }) { (index, verse) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onJump(index) }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .testTag("poem_hit_${verse.id}"),
                    ) {
                        Text(
                            text = verse.sadr,
                            color = colors.text,
                            fontFamily = AtharEditorialFontFamily,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = verse.ajz,
                            color = colors.secondary,
                            fontFamily = AtharEditorialFontFamily,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.7.dp,
                        color = colors.divider.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelEmpty(title: String, body: String, colors: ReaderColors) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, color = colors.text, fontFamily = AtharEditorialFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(body, modifier = Modifier.padding(top = 5.dp), color = colors.secondary, style = MaterialTheme.typography.bodySmall, lineHeight = 22.sp)
    }
}

/* ── audio ─────────────────────────────────────────────────────────────── */

/**
 * Compact strip above the reading controls.
 *
 * It states one fact — who is reciting — because that is the only thing that varies
 * between the recordings of a poem you are already looking at. The name and the
 * collection used to sit here too and ran off the edge of a 393dp screen.
 *
 * The reciter's name *is* the picker: the thing you want to change is the thing you
 * tap, rather than a separate «قائمة الصوتيات» chip beside a label that only reported.
 *
 * The progress bar fills from the start edge, which under RTL is the right: playback
 * begins on the right and travels left, so elapsed sits right and total sits left.
 */
@Composable
internal fun PoemAudioBar(
    track: PoemAudioUi,
    recordings: List<PoemAudioUi>,
    playing: Boolean,
    position: Int,
    speed: Float,
    repeat: Boolean,
    colors: ReaderColors,
    onToggle: () -> Unit,
    onSelect: (PoemAudioUi) -> Unit,
    onSpeed: (Float) -> Unit,
    onRepeat: () -> Unit,
) {
    var listOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    val progress = if (track.durationSeconds == 0) 0f
    else (position.toFloat() / track.durationSeconds).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.page)
            .testTag("poem_audio_bar"),
    ) {
        HorizontalDivider(thickness = 0.7.dp, color = colors.divider)

        // Row one: who, and how fast. Both open a picker; neither can overflow.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                BarPill(colors, "poem_audio_reciter", onClick = { listOpen = true }) {
                    Text(
                        text = "القارئ: ${track.reciter}",
                        modifier = Modifier.weight(1f, fill = false),
                        color = colors.text,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        AtharIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.secondary,
                        modifier = Modifier.padding(start = 4.dp).size(11.dp),
                    )
                }
                ReciterMenu(
                    open = listOpen,
                    recordings = recordings,
                    current = track,
                    colors = colors,
                    onSelect = { onSelect(it); listOpen = false },
                    onDismiss = { listOpen = false },
                )
            }
            Box {
                BarPill(colors, "poem_audio_speed", onClick = { speedOpen = true }) {
                    Text(
                        text = speedLabel(speed),
                        color = if (speed == 1.0f) colors.secondary else colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                SpeedMenu(
                    open = speedOpen,
                    speed = speed,
                    colors = colors,
                    onSelect = { onSpeed(it); speedOpen = false },
                    onDismiss = { speedOpen = false },
                )
            }
        }

        // Row two: transport only.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accentFill)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .testTag("poem_audio_toggle"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) AtharIcons.Pause else AtharIcons.Play,
                    contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل",
                    tint = colors.page,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onRepeat)
                    .testTag("poem_audio_repeat"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AtharIcons.Repeat, "تكرار",
                    tint = if (repeat) colors.accent else colors.secondary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(clock(position), color = colors.secondary, style = MaterialTheme.typography.labelSmall)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(colors.divider),
                )
                Box(
                    modifier = Modifier.fillMaxWidth(progress),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
            }
            Text(track.durationLabel, color = colors.secondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** The bar's one control shape: a quiet capsule that opens a picker. */
@Composable
private fun BarPill(
    colors: ReaderColors,
    tag: String,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(colors.text.copy(alpha = 0.05f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The recordings of this poem, by voice.
 *
 * The collection line carries the weight the removed «المصنف» label used to: a
 * 42-minute شرح is not an alternative reading of the poem, and the reader has to be
 * able to see that before choosing it.
 */
@Composable
private fun ReciterMenu(
    open: Boolean,
    recordings: List<PoemAudioUi>,
    current: PoemAudioUi,
    colors: ReaderColors,
    onSelect: (PoemAudioUi) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        containerColor = colors.page,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.7.dp, colors.divider),
        modifier = Modifier.testTag("poem_audio_reciters"),
    ) {
        recordings.forEach { item ->
            val active = item.id == current.id
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = item.reciter,
                            color = if (active) colors.accent else colors.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${item.collection} · ${item.durationLabel}",
                            color = colors.secondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                onClick = { onSelect(item) },
                trailingIcon = if (active) {
                    {
                        Icon(
                            AtharIcons.Check,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.testTag("poem_audio_opt_${item.id}"),
            )
        }
    }
}

@Composable
private fun SpeedMenu(
    open: Boolean,
    speed: Float,
    colors: ReaderColors,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        containerColor = colors.page,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.7.dp, colors.divider),
        modifier = Modifier.testTag("poem_audio_speeds"),
    ) {
        Speeds.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = speedLabel(option),
                        color = if (option == speed) colors.accent else colors.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (option == speed) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                onClick = { onSelect(option) },
                modifier = Modifier.testTag("poem_speed_${option.toString().replace('.', '_')}"),
            )
        }
    }
}

// Same ladder as the full player, so ×2 means the same thing in both places.
private val Speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

private fun speedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) "${speed.toInt()}.0" else speed.toString()
    return "${arabicDigits(text)}×"
}

/** Seconds to "م:ثث" in Arabic-Indic digits. Escapes, never pasted glyphs. */
private fun clock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "${arabicDigits(m.toString())}:${arabicDigits(s.toString().padStart(2, '0'))}"
}

private fun arabicDigits(text: String): String =
    text.map { ch -> if (ch in '0'..'9') '٠' + (ch - '0') else ch }.joinToString("")
