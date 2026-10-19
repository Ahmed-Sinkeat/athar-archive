package com.atharchive.feature.audio

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.atharchive.ui.components.AtharFavouriteButton
import com.atharchive.ui.components.AtharCollapsible
import com.atharchive.ui.components.rememberAtharCollapsingHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharChipGroup
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharFilterButton
import com.atharchive.ui.components.enumValueOrNull
import com.atharchive.ui.components.orFirstIn
import com.atharchive.ui.components.rememberPersisted
import com.atharchive.ui.components.SectionTab
import com.atharchive.ui.components.emptyCopy
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.AtharFavouriteButton
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.feature.downloads.TransferUi
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

@Composable
fun AudioScreen(
    state: AudioUiState,
    onSettings: () -> Unit,
    onPlay: (AudioUi) -> Unit,
    onMore: (AudioUi) -> Unit,
    onSave: (AudioUi) -> Unit,
    onDownload: (AudioUi) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SectionTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeName by rememberPersisted("audio.type", AudioType.All.name)
    val type = enumValueOrNull<AudioType>(typeName) ?: AudioType.All
    var sortName by rememberPersisted("audio.sort", AudioSort.Newest.name)
    val sort = enumValueOrNull<AudioSort>(sortName) ?: AudioSort.Newest
    val listState = rememberLazyListState()

    val hasActiveFilters = type != AudioType.All || sort != AudioSort.Newest

    val visible = remember(state.recordings, selectedTab, query, type, sort) {
        val filtered = state.recordings.filter { audio ->
            val matchesTab = selectedTab.matches(audio.recentRank != null, audio.downloaded, audio.saved)
            val matchesType = when (type) {
                AudioType.All -> true
                AudioType.Books -> audio.sourceKind == AudioSourceKind.Book
                AudioType.Poetry -> audio.sourceKind == AudioSourceKind.Poem
                AudioType.Issues -> audio.sourceKind == AudioSourceKind.Question
            }
            val term = query.trim()
            val matchesQuery = term.isBlank() ||
                audio.title.contains(term, ignoreCase = true) ||
                audio.sourceTitle.contains(term, ignoreCase = true) ||
                audio.speaker?.contains(term, ignoreCase = true) == true
            matchesTab && matchesType && matchesQuery
        }
        // Newest keeps the authored order — the content has no date field to sort on.
        if (selectedTab == SectionTab.Recent) filtered.sortedBy { it.recentRank ?: Int.MAX_VALUE } else when (sort) {
            AudioSort.Newest -> filtered
            AudioSort.Longest -> filtered.sortedByDescending { it.durationSeconds }
            AudioSort.Shortest -> filtered.sortedBy { it.durationSeconds }
            AudioSort.Title -> filtered.sortedBy { it.title }
        }
    }


    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_audio")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "الصوتيات",
                onSettings = onSettings,
                showAppIcon = true,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
            )
            val header = rememberAtharCollapsingHeader()
            AtharSearchField(
                query = query,
                placeholder = "ابحث عن درس، سلسلة، شيخ أو موضوع…",
                onQueryChange = { query = it },
                tagPrefix = "audio",
                modifier = Modifier.padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 4.dp,
                ),
            )
            AtharCollapsible(header) {
                Column {
                    AtharTabRow(
                        tabs = SectionTab.tabs,
                        selectedKey = selectedTab.key,
                        onSelect = { selectedTab = SectionTab.fromKey(it) },
                        tagPrefix = "audio",
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding - 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AtharFilterButton(
                            groups = listOfNotNull(
                                AtharChipGroup("النوع", AudioType.entries.map { it.label }, type.label) { label -> typeName = AudioType.entries.first { it.label == label }.name },
                                if (selectedTab == SectionTab.Recent) null else AtharChipGroup("الترتيب", AudioSort.entries.map { it.label }, sort.label) { label -> sortName = AudioSort.entries.first { it.label == label }.name },
                            ),
                            tagPrefix = "audio_filter",
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        color = AtharTheme.colors.divider.copy(alpha = 0.72f),
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(header.connection)
                    .testTag("audio_screen"),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {

                if (visible.isEmpty()) {
                    item(key = "audio-empty") {
                        val (title, body) = when {
                            query.isNotBlank() ->
                                "لا توجد نتائج" to "جرّب اسم درس أو مصدر أقصر."
                            hasActiveFilters ->
                                "لا توجد تسجيلات بهذه التصفية" to
                                    "امسح بعض المرشحات لعرض مزيد من التسجيلات."
                            else -> selectedTab.emptyCopy("تسجيلات", "نزّل تسجيلًا لتستمع إليه دون اتصال.")
                        }
                        AtharEmptyState(
                            title = title,
                            body = body,
                            tag = "audio_empty_state",
                            horizontalPadding = horizontalPadding,
                        )
                    }
                } else {
                    itemsIndexed(visible, key = { _, a -> a.id }) { index, audio ->
                        AudioRow(
                            audio = audio,
                            horizontalPadding = horizontalPadding,
                            onPlay = { onPlay(audio) },
                            onMore = { onMore(audio) },
                            onSave = { onSave(audio) },
                            onDownload = { onDownload(audio) },
                            playing = state.nowPlaying?.let { it.audio.id == audio.id && it.playing } == true,
                        )
                        if (index != visible.lastIndex) {
                            // Inset on both sides so the rule reads as belonging to the
                            // row rather than cutting the page in half.
                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    start = horizontalPadding + 46.dp,
                                    end = horizontalPadding + 46.dp,
                                ),
                                thickness = 0.7.dp,
                                color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun AudioRow(
    audio: AudioUi,
    horizontalPadding: Dp,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    playing: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            // The row plays. Tapping the title was the obvious gesture and it did
            // nothing at all — the only list in the app that ignored it.
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(horizontal = horizontalPadding, vertical = 9.dp)
            .testTag("audio_row_${audio.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AudioPlayButton(playing = playing, size = 48.dp, onClick = onPlay)
        Column(Modifier.weight(1f)) {
            Text(
                text = audio.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = AtharTextSize.Title,
                lineHeight = 25.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Speaker is absent for all but a handful of recordings; the line simply
            // does not exist rather than showing a placeholder.
            if (audio.speaker != null) {
                Text(
                    text = audio.speaker,
                    modifier = Modifier.padding(top = 2.dp),
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (audio.sourceTitle == audio.title) audio.sourceKind.label else "${audio.sourceKind.label} · ${audio.sourceTitle}",
                modifier = Modifier.padding(top = 2.dp),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val factColor = AtharTheme.colors.secondaryText
                Text(
                    text = audio.durationLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (audio.downloaded) {
                    DownloadedPill()
                }
            }
        }
        AtharFavouriteButton(
            saved = audio.saved,
            onToggle = onSave,
            itemTitle = audio.title,
            tag = "audio_save_${audio.id}",
        )
        Box {
            var menu by remember { mutableStateOf(false) }
            IconButton(
                onClick = { menu = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("audio_more_${audio.id}"),
            ) {
                Icon(
                    imageVector = AtharIcons.More,
                    contentDescription = "خيارات ${audio.title}",
                    tint = AtharTheme.colors.secondaryText,
                    modifier = Modifier.size(22.dp),
                )
            }
            AtharMenu(expanded = menu, onDismiss = { menu = false }, modifier = Modifier.testTag("audio_menu_${audio.id}")) {
                val transfer = audio.transfer
                AtharMenuItem(
                    label = when {
                        transfer is TransferUi.Running -> "إيقاف التنزيل مؤقتًا"
                        transfer != null -> "استئناف التنزيل"
                        audio.downloaded -> "محمّل"
                        else -> "تنزيل"
                    },
                    icon = when {
                        transfer is TransferUi.Running -> AtharIcons.Pause
                        audio.downloaded -> AtharIcons.Check
                        else -> AtharIcons.Download
                    },
                    onClick = { menu = false; if (!audio.downloaded || transfer != null) onDownload() },
                    modifier = Modifier.testTag("audio_download_${audio.id}"),
                )
                if (audio.sourceEntityId.isNotBlank()) {
                    AtharMenuItem(
                        label = if (audio.sourceKind == AudioSourceKind.Poem) "فتح القصيدة" else "فتح النص",
                        icon = if (audio.sourceKind == AudioSourceKind.Poem) AtharIcons.Poetry else AtharIcons.Books,
                        onClick = { menu = false; onMore() },
                        modifier = Modifier.testTag("audio_open_${audio.id}"),
                    )
                }
            }
        }
    }
}

/** Muted olive on a whisper of beige — a state, not an award. */
@Composable
internal fun DownloadedPill(modifier: Modifier = Modifier) {
    Text(
        text = "محمّل",
        modifier = modifier
            .clip(AtharCorners.Tag)
            .background(AtharTheme.colors.success.copy(alpha = 0.11f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = AtharTheme.colors.success,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun AudioPlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            // primaryAction, not accent: a big filled disc in the bright accent
            // dominates the row in dark mode.
            .background(AtharTheme.colors.primaryAction)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) AtharIcons.Pause else AtharIcons.Play,
            contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل",
            tint = AtharTheme.colors.onPrimaryAction,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/**
 * Fills from the start edge, which under RTL is the right — playback begins on the
 * right and travels left. Nothing here mirrors manually; the layout direction does it.
 */
@Composable
fun AudioProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(AtharTheme.colors.divider.copy(alpha = 0.75f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(AtharTheme.colors.accent),
        )
    }
}
