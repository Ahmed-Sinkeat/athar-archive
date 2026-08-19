package com.atharchive.feature.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
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
    onResumeContinue: () -> Unit,
    onMore: (AudioUi) -> Unit,
    modifier: Modifier = Modifier,
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AudioTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val visible = remember(state.recordings, selectedTab, query) {
        state.recordings.filter { audio ->
            val matchesTab = when (selectedTab) {
                AudioTab.All -> true
                AudioTab.Books -> audio.sourceKind == AudioSourceKind.Book
                AudioTab.Poetry -> audio.sourceKind == AudioSourceKind.Poem
                AudioTab.Issues -> audio.sourceKind == AudioSourceKind.Question
                AudioTab.MyList -> audio.saved
            }
            val term = query.trim()
            val matchesQuery = term.isBlank() ||
                audio.title.contains(term, ignoreCase = true) ||
                audio.sourceTitle.contains(term, ignoreCase = true) ||
                audio.speaker?.contains(term, ignoreCase = true) == true
            matchesTab && matchesQuery
        }
    }
    val showContinue = state.continueListening != null &&
        selectedTab == AudioTab.All && query.isBlank()

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
            if (showContinue) {
                item(key = "audio-continue") {
                    ContinueListening(
                        nowPlaying = state.continueListening!!,
                        horizontalPadding = horizontalPadding,
                        onResume = onResumeContinue,
                    )
                }
            }
            item(key = "audio-list-heading") {
                Text(
                    text = if (showContinue) "جديد الصوتيات" else "الصوتيات",
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = if (showContinue) 20.dp else 12.dp,
                        bottom = 4.dp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            if (visible.isEmpty()) {
                item(key = "audio-empty") {
                    val (title, body) = when {
                        query.isNotBlank() ->
                            "لا توجد نتائج" to "جرّب اسم درس أو مصدر أقصر."
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
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = horizontalPadding + 44.dp,
                                end = horizontalPadding,
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
private fun ContinueListening(
    nowPlaying: NowPlayingUi,
    horizontalPadding: Dp,
    onResume: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(
            text = "تابع الاستماع",
            modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding),
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        // The one raised surface in the app: it is the strongest interactive element on
        // this screen by design, and nothing else competes with it.
        Surface(
            modifier = Modifier
                .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onResume)
                .testTag("audio_continue"),
            shape = RoundedCornerShape(14.dp),
            color = AtharTheme.colors.raisedSurface,
            border = BorderStroke(1.dp, AtharTheme.colors.divider.copy(alpha = 0.4f)),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = nowPlaying.audio.sourceTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nowPlaying.audio.speaker != null) {
                    Text(
                        text = nowPlaying.audio.speaker,
                        modifier = Modifier.padding(top = 2.dp),
                        color = AtharTheme.colors.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                Text(
                    text = nowPlaying.audio.title,
                    modifier = Modifier.padding(top = 3.dp),
                    color = AtharTheme.colors.accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlayButton(playing = nowPlaying.playing, size = 38.dp, onClick = onResume)
                    Column(Modifier.weight(1f)) {
                        AudioProgressBar(progress = nowPlaying.progress)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${nowPlaying.positionLabel} / ${nowPlaying.durationLabel}",
                                color = AtharTheme.colors.secondaryText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = "٪${(nowPlaying.progress * 100).toInt()}",
                                color = AtharTheme.colors.secondaryText,
                                style = MaterialTheme.typography.labelSmall,
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .padding(horizontal = horizontalPadding, vertical = 9.dp)
            .testTag("audio_row_${audio.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayButton(playing = false, size = 34.dp, onClick = onPlay)
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
                text = "${audio.sourceKind.label}: ${audio.sourceTitle}",
                modifier = Modifier.padding(top = 2.dp),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                    Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "محمّل",
                        color = AtharTheme.colors.success,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        IconButton(
            onClick = onMore,
            modifier = Modifier
                .size(36.dp)
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

@Composable
private fun PlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
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
