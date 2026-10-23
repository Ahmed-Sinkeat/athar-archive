package com.atharchive.feature.audio

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import com.atharchive.ui.components.arabicDigits
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.components.AtharMenu
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
    onCollapse: () -> Unit,
    onToggle: () -> Unit,
    onSelect: (AudioUi) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onMore: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    repeat: Boolean = false,
    onSpeed: (Float) -> Unit = {},
    onRepeat: (Boolean) -> Unit = {},
    onSleep: (Int) -> Unit = {},
    sleepMinutes: Int = 0,
    onSeekFraction: (Float) -> Unit = {},
) {
    val audio = nowPlaying.audio
    val remaining = remember(audio.durationSeconds, nowPlaying.progress) {
        ((audio.durationSeconds * (1f - nowPlaying.progress)).toInt()).coerceAtLeast(0)
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
                // The only full-screen route that was not doing this: the queue ran
                // under the system nav buttons and its last row could not be tapped.
                .navigationBarsPadding()
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
                    modifier = Modifier.size(48.dp).testTag("player_collapse"),
                ) {
                    Icon(
                        imageVector = AtharIcons.ChevronDown,
                        contentDescription = "إغلاق المشغّل",
                        tint = AtharTheme.colors.secondaryText,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    text = "المشغّل",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = AtharTextSize.Title,
                )
                Box {
                    var moreOpen by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { moreOpen = true },
                        modifier = Modifier.size(48.dp).testTag("player_more"),
                    ) {
                        Icon(
                            imageVector = AtharIcons.More,
                            contentDescription = "خيارات",
                            tint = AtharTheme.colors.secondaryText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    AtharMenu(expanded = moreOpen, onDismiss = { moreOpen = false }, modifier = Modifier.testTag("player_more_menu")) {
                        val poem = audio.sourceKind == AudioSourceKind.Poem
                        AtharMenuItem(
                            label = if (poem) "فتح القصيدة" else "فتح النص",
                            icon = if (poem) AtharIcons.Poetry else AtharIcons.Books,
                            onClick = { moreOpen = false; onMore() },
                            modifier = Modifier.testTag("player_open_source"),
                        )
                    }
                }
            }

            // ── artwork ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(148.dp)
                    .clip(AtharCorners.Artwork)
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
                fontSize = AtharTextSize.Headline,
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
            if (audio.title != audio.sourceTitle) Text(
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
                    onSeek = onSeekFraction,
                    modifier = Modifier.weight(1f),
                )
                TimeLabel("\u2066−${formatClock(remaining)}\u2069")
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
                    SeekButton(AtharIcons.SeekBack, 15, "رجوع ١٥ ثانية") { onSeek(-15) }
                    BigPlayButton(playing = nowPlaying.playing, onClick = onToggle)
                    SeekButton(AtharIcons.SeekForward, 15, "تقدّم ١٥ ثانية") { onSeek(15) }
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
                ActionTile(
                    label = if (nowPlaying.audio.saved) "في المفضّلة" else "المفضّلة",
                    active = nowPlaying.audio.saved,
                    onClick = onSave,
                    tag = "player_favourite",
                    modifier = Modifier.weight(1f),
                ) { tint ->
                    Icon(if (nowPlaying.audio.saved) AtharIcons.StarFilled else AtharIcons.Star, null, tint = tint, modifier = Modifier.size(21.dp))
                }
                SleepTile(
                    minutes = sleepMinutes,
                    onSelect = onSleep,
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    label = "تكرار",
                    active = repeat,
                    onClick = { onRepeat(!repeat) },
                    tag = "player_repeat",
                    modifier = Modifier.weight(1f),
                ) { tint ->
                    Icon(AtharIcons.Repeat, null, tint = tint, modifier = Modifier.size(21.dp))
                }
                SpeedTile(
                    speed = speed,
                    onSelect = onSpeed,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── queue ────────────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 22.dp),
                shape = AtharCorners.Surface,
                color = AtharTheme.colors.raisedSurface,
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "قائمة التشغيل",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = AtharEditorialFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = AtharTextSize.Title,
                    )
                    queue.forEachIndexed { index, item ->
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
internal fun SeekBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = AtharTheme.colors.accent,
    track: androidx.compose.ui.graphics.Color = AtharTheme.colors.divider,
    onSeek: (Float) -> Unit,
) {
    // The same thin bar as the mini player and reader, with a small dot; tap or drag.
    // Fills from the start edge, so under RTL playback travels right → left.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The gesture blocks are keyed on rtl only, so they must read the latest onSeek: the first
    // one closes over durationMs = 0 and every seek landed on 0:00.
    val seek by androidx.compose.runtime.rememberUpdatedState(onSeek)
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: progress).coerceIn(0f, 1f)
    fun fraction(x: Float, width: Int) = (x / width).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }
    Canvas(
        modifier = modifier
            .height(28.dp)
            .testTag("player_seek_bar")
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f) }
            .pointerInput(rtl) {
                detectTapGestures { seek(fraction(it.x, size.width)) }
            }
            .pointerInput(rtl) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = fraction(it.x, size.width) },
                    onDragEnd = { drag?.let(seek); drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = fraction(change.position.x, size.width) }
            },
    ) {
        val y = size.height / 2
        val h = 3.dp.toPx()
        val split = if (rtl) size.width * (1f - shown) else size.width * shown
        drawLine(track, Offset(0f, y), Offset(size.width, y), h, StrokeCap.Round)
        if (rtl) drawLine(accent, Offset(split, y), Offset(size.width, y), h, StrokeCap.Round)
        else drawLine(accent, Offset(0f, y), Offset(split, y), h, StrokeCap.Round)
        drawCircle(accent, radius = (if (drag != null) 7 else 5).dp.toPx(), center = Offset(split, y))
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
            text = arabicDigits(seconds),
            modifier = Modifier.padding(top = 5.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 9.sp, // the digit drawn inside the skip icon; off the text scale on purpose
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
            .clip(AtharCorners.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        shape = AtharCorners.Card,
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
private fun SleepTile(minutes: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        ActionTile(
            label = if (minutes == 0) "مؤقّت النوم" else "${arabicDigits(minutes)} دقيقة",
            active = minutes != 0,
            onClick = { open = true },
            tag = "player_sleep",
            modifier = Modifier.fillMaxWidth(),
        ) { tint ->
            Icon(AtharIcons.SleepTimer, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        AtharMenu(expanded = open, onDismiss = { open = false }, width = 200.dp) {
            SleepOptions.forEach { option ->
                AtharMenuItem(
                    label = if (option == 0) "إيقاف" else "${arabicDigits(option)} دقيقة",
                    checked = option == minutes,
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
            modifier = Modifier.fillMaxWidth(),
        ) { tint ->
            Text(
                text = speedLabel(speed),
                color = tint,
                fontWeight = FontWeight.SemiBold,
                fontSize = AtharTextSize.Ui,
            )
        }
        AtharMenu(expanded = open, onDismiss = { open = false }, width = 180.dp) {
            Speeds.forEach { option ->
                AtharMenuItem(speedLabel(option), checked = option == speed, onClick = { onSelect(option); open = false })
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
                fontSize = AtharTextSize.Ui,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val factColor = AtharTheme.colors.secondaryText
                val source = item.speaker ?: item.sourceTitle.takeIf { it != item.title }
                if (source != null) {
                    Text(
                        text = source,
                        color = factColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                }
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
    return "${arabicDigits(text)}×"
}

/** "٤٢:١٨" from seconds. Latin digits are mapped to Arabic-Indic by code point. */
private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${arabicDigits(minutes)}:${arabicDigits(seconds.toString().padStart(2, '0'))}"
}

