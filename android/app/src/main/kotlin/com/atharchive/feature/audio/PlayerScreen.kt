package com.atharchive.feature.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

private val Speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val SleepOptions = listOf(0, 15, 30, 45, 60)

/**
 * The full player. Deliberately not a Spotify screen: one restrained artwork tile,
 * the work and the lesson stated plainly, and every secondary control living in a
 * compact menu rather than a bottom sheet.
 *
 * Two directional decisions worth knowing:
 *  - the seek bar is genuinely RTL — it fills from the right and travels left, so
 *    elapsed sits at the start edge (right) and total at the end (left);
 *  - the transport row is pinned to LTR, because previous/rewind/forward/next read
 *    as a tape metaphor that does not mirror.
 */
@Composable
fun PlayerScreen(
    nowPlaying: NowPlayingUi,
    queue: List<AudioUi>,
    playlists: List<PlaylistUi>,
    onCollapse: () -> Unit,
    onToggle: () -> Unit,
    onSelect: (AudioUi) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onMore: () -> Unit,
    onAddToPlaylist: (PlaylistUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var speed by rememberSaveable { mutableStateOf(1.0f) }
    var repeat by rememberSaveable { mutableStateOf(false) }
    var sleepMinutes by rememberSaveable { mutableStateOf(0) }
    var queueType by rememberSaveable { mutableStateOf(AudioType.All) }
    var queueSort by rememberSaveable { mutableStateOf(AudioSort.Newest) }

    val audio = nowPlaying.audio
    val remaining = remember(audio.durationSeconds, nowPlaying.progress) {
        ((audio.durationSeconds * (1f - nowPlaying.progress)).toInt()).coerceAtLeast(0)
    }
    val visibleQueue = remember(queue, queueType, queueSort) {
        val filtered = queue.filter { item ->
            when (queueType) {
                AudioType.All -> true
                AudioType.Books -> item.sourceKind == AudioSourceKind.Book
                AudioType.Poetry -> item.sourceKind == AudioSourceKind.Poem
                AudioType.Issues -> item.sourceKind == AudioSourceKind.Question
            }
        }
        when (queueSort) {
            AudioSort.Newest -> filtered
            AudioSort.Longest -> filtered.sortedByDescending { it.durationSeconds }
            AudioSort.Shortest -> filtered.sortedBy { it.durationSeconds }
            AudioSort.Title -> filtered.sortedBy { it.title }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AtharTheme.colors.canvas)
            .testTag("player_screen"),
    ) {
        val pad = if (maxWidth >= 720.dp) 32.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            // ── header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pad - 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(40.dp).testTag("player_collapse"),
                ) {
                    Icon(
                        imageVector = AtharIcons.ChevronDown,
                        contentDescription = "إغلاق المشغّل",
                        tint = AtharTheme.colors.secondaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "المشغّل",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.size(40.dp).testTag("player_more"),
                ) {
                    Icon(
                        imageVector = AtharIcons.More,
                        contentDescription = "خيارات",
                        tint = AtharTheme.colors.secondaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ── artwork ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(148.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AtharTheme.colors.pressedSurface),
                contentAlignment = Alignment.Center,
            ) {
                Waveform()
            }

            // ── titles ───────────────────────────────────────────────────────
            Text(
                text = audio.sourceTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 18.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 23.sp,
                lineHeight = 34.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (audio.speaker != null) {
                Text(
                    text = audio.speaker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = pad, end = pad, top = 4.dp),
                    textAlign = TextAlign.Center,
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = audio.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 10.dp),
                textAlign = TextAlign.Center,
                color = AtharTheme.colors.accent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // ── seek ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TimeLabel(nowPlaying.positionLabel)
                SeekBar(
                    progress = nowPlaying.progress,
                    modifier = Modifier.weight(1f),
                )
                TimeLabel(nowPlaying.durationLabel)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "٪${(nowPlaying.progress * 100).toInt().arabicDigits()}",
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "الوقت المتبقّي ${formatClock(remaining)}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                )
                // Keeps the remaining-time label optically centred.
                Spacer(Modifier.width(34.dp))
            }

            // ── transport (LTR: the tape metaphor does not mirror) ───────────
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = pad, end = pad, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GlyphButton(AtharIcons.SkipPrevious, "التسجيل السابق", 26.dp, onSkipPrevious)
                    SeekButton(AtharIcons.SeekBack, 10, "رجوع ١٠ ثوان") { onSeek(-10) }
                    BigPlayButton(playing = nowPlaying.playing, onClick = onToggle)
                    SeekButton(AtharIcons.SeekForward, 30, "تقدّم ٣٠ ثانية") { onSeek(30) }
                    GlyphButton(AtharIcons.SkipNext, "التسجيل التالي", 26.dp, onSkipNext)
                }
            }

            // ── secondary actions ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                AddToPlaylistTile(
                    playlists = playlists,
                    onAdd = onAddToPlaylist,
                    modifier = Modifier.weight(1f),
                )
                SleepTile(
                    minutes = sleepMinutes,
                    onSelect = { sleepMinutes = it },
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    label = "تكرار",
                    active = repeat,
                    onClick = { repeat = !repeat },
                    tag = "player_repeat",
                    modifier = Modifier.weight(1f),
                ) { tint ->
                    Icon(AtharIcons.Repeat, null, tint = tint, modifier = Modifier.size(21.dp))
                }
                SpeedTile(
                    speed = speed,
                    onSelect = { speed = it },
                    modifier = Modifier.weight(1f),
                )
            }

            // ── queue ────────────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 22.dp),
                shape = RoundedCornerShape(14.dp),
                color = AtharTheme.colors.raisedSurface,
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "قائمة التشغيل",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = AtharEditorialFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AtharMenuButton(
                            label = "الترتيب",
                            options = AudioSort.entries.map { it.label },
                            selected = queueSort.label,
                            defaultOption = AudioSort.Newest.label,
                            onSelect = { l -> queueSort = AudioSort.entries.first { it.label == l } },
                            tagPrefix = "queue_sort",
                        )
                        AtharMenuButton(
                            label = "النوع",
                            options = AudioType.entries.map { it.label },
                            selected = queueType.label,
                            defaultOption = AudioType.All.label,
                            onSelect = { l -> queueType = AudioType.entries.first { it.label == l } },
                            tagPrefix = "queue_type",
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${playlists.size.arabicDigits()} قوائم",
                            color = AtharTheme.colors.secondaryText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    visibleQueue.forEachIndexed { index, item ->
                        if (index != 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.7.dp,
                                color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                            )
                        }
                        QueueRow(item = item, onPlay = { onSelect(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(
        text = text,
        color = AtharTheme.colors.secondaryText,
        style = MaterialTheme.typography.labelSmall,
    )
}

/**
 * Fills from the start edge — under RTL that is the right, so playback begins on the
 * right and travels left. The thumb rides the end of the filled span.
 */
@Composable
private fun SeekBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(AtharTheme.colors.divider.copy(alpha = 0.75f)),
        )
        Box(
            modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(AtharTheme.colors.accent),
            )
            Box(
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(AtharTheme.colors.accent),
            )
        }
    }
}

@Composable
private fun BigPlayButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(66.dp)
            .clip(CircleShape)
            .background(AtharTheme.colors.primaryAction)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("player_toggle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) AtharIcons.Pause else AtharIcons.Play,
            contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل",
            tint = AtharTheme.colors.onPrimaryAction,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun GlyphButton(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(46.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size),
        )
    }
}

/** Circular arrow with the interval written inside it, so one glyph serves any amount. */
@Composable
private fun SeekButton(icon: ImageVector, seconds: Int, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = seconds.arabicDigits(),
            modifier = Modifier.padding(top = 5.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActionTile(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable (tint: androidx.compose.ui.graphics.Color) -> Unit,
) {
    val tint = if (active) AtharTheme.colors.accent else AtharTheme.colors.secondaryText
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(11.dp),
        color = if (active) {
            AtharTheme.colors.accentSurface
        } else {
            AtharTheme.colors.raisedSurface
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 11.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content(tint)
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddToPlaylistTile(
    playlists: List<PlaylistUi>,
    onAdd: (PlaylistUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        ActionTile(
            label = "إضافة إلى قائمة",
            active = false,
            onClick = { open = true },
            tag = "player_add_playlist",
        ) { tint ->
            Icon(AtharIcons.PlusCircle, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = AtharTheme.colors.canvas,
            shape = RoundedCornerShape(10.dp),
        ) {
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = playlist.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = { onAdd(playlist); open = false },
                )
            }
        }
    }
}

@Composable
private fun SleepTile(minutes: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        ActionTile(
            label = if (minutes == 0) "مؤقّت النوم" else "${minutes.arabicDigits()} دقيقة",
            active = minutes != 0,
            onClick = { open = true },
            tag = "player_sleep",
        ) { tint ->
            Icon(AtharIcons.SleepTimer, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = AtharTheme.colors.canvas,
            shape = RoundedCornerShape(10.dp),
        ) {
            SleepOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (option == 0) "إيقاف" else "${option.arabicDigits()} دقيقة",
                            color = if (option == minutes) {
                                AtharTheme.colors.accent
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = { onSelect(option); open = false },
                )
            }
        }
    }
}

@Composable
private fun SpeedTile(speed: Float, onSelect: (Float) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val active = speed != 1.0f
    Box(modifier = modifier) {
        ActionTile(
            label = "السرعة",
            active = active,
            onClick = { open = true },
            tag = "player_speed",
        ) { tint ->
            Text(
                text = speedLabel(speed),
                color = tint,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = AtharTheme.colors.canvas,
            shape = RoundedCornerShape(10.dp),
        ) {
            Speeds.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = speedLabel(option),
                            color = if (option == speed) {
                                AtharTheme.colors.accent
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = { onSelect(option); open = false },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(item: AudioUi, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("queue_row_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AudioPlayButton(playing = false, size = 30.dp, onClick = onPlay)
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val factColor = AtharTheme.colors.secondaryText
                Text(
                    text = item.speaker ?: item.sourceTitle,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = item.durationLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall
                        .copy(textDirection = TextDirection.Ltr),
                )
                if (item.downloaded) DownloadedPill()
            }
        }
        Icon(
            imageVector = AtharIcons.DragHandle,
            contentDescription = null,
            tint = AtharTheme.colors.secondaryText.copy(alpha = 0.55f),
            modifier = Modifier.size(17.dp),
        )
    }
}

/** Static mark, not a level meter — there is no decoded audio behind it to animate. */
@Composable
private fun Waveform() {
    val heights = listOf(14.dp, 30.dp, 46.dp, 62.dp, 44.dp, 26.dp, 12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(CircleShape)
                    .background(AtharTheme.colors.accent),
            )
        }
    }
}

private fun speedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) "${speed.toInt()}.0" else speed.toString()
    return "${text.arabicDigits()}×"
}

/** "٤٢:١٨" from seconds. Latin digits are mapped to Arabic-Indic by code point. */
private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.arabicDigits()}:${seconds.toString().padStart(2, '0').arabicDigits()}"
}

private fun Int.arabicDigits(): String = toString().arabicDigits()

private fun String.arabicDigits(): String =
    map { ch -> if (ch in '0'..'9') '\u0660' + (ch - '0') else ch }.joinToString("")
