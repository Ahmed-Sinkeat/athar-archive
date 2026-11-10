package com.atharchive.ui

import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import com.atharchive.feature.audio.PlayerScreen
import androidx.navigation3.runtime.EntryProviderScope

/** The full-screen player. */
internal fun EntryProviderScope<NavKey>.audioEntries(app: AppNav) = with(app) {
        entry<PlayerRoute> {
            if (nowPlaying == null) UnavailableRoute("لا يوجد تسجيل متاح في المشغّل", goBack) { contentViewModel.sync() }
            val recordings = audioState.recordings
            val byId = remember(recordings) { recordings.associateBy { it.id } }
            val queue = remember(playbackState.queueIds, byId) { playbackState.queueIds.mapNotNull(byId::get) }
            nowPlaying?.let { playing ->
                PlayerScreen(
                    nowPlaying = playing,
                    queue = queue,
                    onCollapse = goBack,
                    onToggle = contentViewModel.playback::toggle,
                    onSelect = { contentViewModel.playAudio(it.id, queue) },
                    onSkipPrevious = contentViewModel.playback::previous,
                    onSkipNext = contentViewModel.playback::next,
                    onSeek = contentViewModel.playback::seekBy,
                    onSeekFraction = { contentViewModel.playback.seekTo((it * playbackState.durationMs).toLong()) },
                    speed = playbackState.speed,
                    repeat = playbackState.repeat,
                    onSpeed = contentViewModel.playback::speed,
                    onRepeat = contentViewModel.playback::repeat,
                    onSleep = contentViewModel.playback::sleep,
                    sleepMinutes = playbackState.sleepMinutes,
                    onMore = { openAudioSource(playing.audio) },
                    onSave = { contentViewModel.toggleFavourite(playing.audio.sourceEntityId) },
                )
            }
        }
}
