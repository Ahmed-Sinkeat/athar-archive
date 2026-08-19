package com.atharchive.feature.audio

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

@Composable
fun AudioScreen(
    state: AudioUiState,
    onSettings: () -> Unit,
    onPlay: (AudioUi) -> Unit,
    onMore: (AudioUi) -> Unit,
    onOpenPlaylist: (PlaylistUi) -> Unit,
    modifier: Modifier = Modifier,
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AudioTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AudioType.All) }
    var sort by rememberSaveable { mutableStateOf(AudioSort.Newest) }
    val listState = rememberLazyListState()

    val hasActiveFilters = type != AudioType.All || sort != AudioSort.Newest

    val visible = remember(state.recordings, selectedTab, query, type, sort) {
        val filtered = state.recordings.filter { audio ->
            val matchesTab = when (selectedTab) {
                AudioTab.All -> true
                AudioTab.Downloaded -> audio.downloaded
                AudioTab.MyList -> audio.saved
            }
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
        when (sort) {
            AudioSort.Newest -> filtered
            AudioSort.Longest -> filtered.sortedByDescending { it.durationSeconds }
            AudioSort.Shortest -> filtered.sortedBy { it.durationSeconds }
            AudioSort.Title -> filtered.sortedBy { it.title }
        }
    }

    // Playlists are the point of قائمتي; the saved recordings sit underneath them.
    val showPlaylists = selectedTab == AudioTab.MyList &&
        query.isBlank() &&
        state.playlists.isNotEmpty()

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
                onLogo = onLogo,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("audio_screen"),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item(key = "audio-search") {
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
                }
                item(key = "audio-tabs") {
                    AtharTabRow(
                        tabs = AudioTab.tabs,
                        selectedKey = selectedTab.key,
                        onSelect = { selectedTab = AudioTab.fromKey(it) },
                        tagPrefix = "audio",
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
                item(key = "audio-menus") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding - 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AtharMenuButton(
                            label = "النوع",
                            options = AudioType.entries.map { it.label },
                            selected = type.label,
                            defaultOption = AudioType.All.label,
                            onSelect = { label ->
                                type = AudioType.entries.first { it.label == label }
                            },
                            tagPrefix = "audio_type",
                        )
                        AtharMenuButton(
                            label = "الترتيب",
                            options = AudioSort.entries.map { it.label },
                            selected = sort.label,
                            defaultOption = AudioSort.Newest.label,
                            onSelect = { label ->
                                sort = AudioSort.entries.first { it.label == label }
                            },
                            tagPrefix = "audio_sort",
                        )
                    }
                }
                item(key = "audio-list-divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        color = AtharTheme.colors.divider.copy(alpha = 0.72f),
                    )
                }

                if (showPlaylists) {
                    item(key = "audio-playlists-heading") {
                        SectionHeading("قوائمي", horizontalPadding, top = 14.dp)
                    }
                    items(state.playlists, key = { "pl-${it.id}" }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            horizontalPadding = horizontalPadding,
                            onClick = { onOpenPlaylist(playlist) },
                        )
                    }
                    item(key = "audio-playlists-divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                top = 10.dp,
                            ),
                            color = AtharTheme.colors.divider.copy(alpha = 0.72f),
                        )
                    }
                }

                item(key = "audio-list-heading") {
                    SectionHeading(
                        text = if (showPlaylists) "التسجيلات المحفوظة" else "الصوتيات",
                        horizontalPadding = horizontalPadding,
                        top = if (showPlaylists) 16.dp else 12.dp,
                    )
                }

                if (visible.isEmpty()) {
                    item(key = "audio-empty") {
                        val (title, body) = when {
                            query.isNotBlank() ->
                                "لا توجد نتائج" to "جرّب اسم درس أو مصدر أقصر."
                            hasActiveFilters ->
                                "لا توجد تسجيلات بهذه التصفية" to
                                    "امسح بعض المرشحات لعرض مزيد من التسجيلات."
                            selectedTab == AudioTab.Downloaded ->
                                "لا توجد تسجيلات محمّلة" to "نزّل تسجيلًا لتستمع إليه دون اتصال."
                            selectedTab == AudioTab.MyList ->
                                "قائمتك فارغة" to "ستظهر هنا التسجيلات المحفوظة في قائمتك."
                            else ->
                                "لا توجد تسجيلات" to "لم تتوفر تسجيلات في هذا القسم بعد."
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
private fun SectionHeading(text: String, horizontalPadding: Dp, top: Dp) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = horizontalPadding,
            end = horizontalPadding,
            top = top,
            bottom = 4.dp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = AtharEditorialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistUi,
    horizontalPadding: Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 11.dp)
            .testTag("audio_playlist_${playlist.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = AtharIcons.Bookmark,
            contentDescription = null,
            tint = AtharTheme.colors.accent,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = playlist.name,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = playlist.countLabel,
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.labelSmall,
        )
        Icon(
            imageVector = AtharIcons.Forward,
            contentDescription = null,
            tint = AtharTheme.colors.secondaryText.copy(alpha = 0.7f),
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun AudioRow(
    audio: AudioUi,
    horizontalPadding: Dp,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .padding(horizontal = horizontalPadding, vertical = 9.dp)
            .testTag("audio_row_${audio.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AudioPlayButton(playing = false, size = 34.dp, onClick = onPlay)
        Column(Modifier.weight(1f)) {
            Text(
                text = audio.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
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
                text = "${audio.sourceKind.label} · ${audio.sourceTitle}",
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
                val factColor = AtharTheme.colors.secondaryText.copy(alpha = 0.72f)
                Text(
                    text = audio.durationLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = audio.sizeLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall
                        .copy(textDirection = TextDirection.Ltr),
                )
                if (audio.downloaded) {
                    DownloadedPill()
                }
            }
        }
        IconButton(
            onClick = onMore,
            modifier = Modifier
                .size(32.dp)
                .testTag("audio_more_${audio.id}"),
        ) {
            Icon(
                imageVector = AtharIcons.More,
                contentDescription = "خيارات ${audio.title}",
                tint = AtharTheme.colors.secondaryText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Muted olive on a whisper of beige — a state, not an award. */
@Composable
internal fun DownloadedPill(modifier: Modifier = Modifier) {
    Text(
        text = "محمّل",
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
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
            .background(AtharTheme.colors.accent)
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
