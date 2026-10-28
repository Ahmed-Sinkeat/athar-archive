package com.atharchive.data

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The service owns playback; screens only observe it and send commands. */
@Singleton
class AudioPlayback @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("audio_playback", Context.MODE_PRIVATE)
    private val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))).buildAsync()
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state
    private var sleepTask: Runnable? = null
    private var sleepEndsAt = 0L

    init {
        future.addListener({
            runCatching { future.get() }.onSuccess { player ->
                controller = player
                player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { publish() }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        _state.value = _state.value.copy(error = playbackMessage(error))
                    }
                })
                handler.post(object : Runnable {
                    override fun run() { if (player.isPlaying || sleepEndsAt != 0L) publish(); handler.postDelayed(this, 500) }
                })
            }.onFailure { _state.value = _state.value.copy(error = "تعذّر الاتصال بالمشغّل") }
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(items: List<MediaItem>, index: Int) {
        val player = controller
        if (player == null) {
            if (!future.isDone) future.addListener({ play(items, index) }, { handler.post(it) })
            else _state.value = _state.value.copy(error = "المشغّل غير متاح؛ أعد فتح التطبيق")
            return
        }
        if (items.isEmpty() || index !in items.indices) return
        Analytics.hit("audio", items[index].mediaId)
        val sameItem = player.currentMediaItem?.mediaId == items[index].mediaId
        val sameQueue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) } == items
        if (sameItem && sameQueue && player.playerError == null) {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        } else {
            val savedPosition = if (sameItem) if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition.coerceAtLeast(0)
                else preferences.getLong("position:${items[index].mediaId}", 0).coerceAtLeast(0)
            player.setMediaItems(items, index, savedPosition)
            player.prepare()
            player.play()
        }
    }

    fun toggle() { controller?.let { if (it.playWhenReady) it.pause() else { it.prepare(); it.play() } } }
    fun stop() { controller?.run { stop(); clearMediaItems() } }
    fun seekBy(seconds: Int) { controller?.let { seekTo(it.currentPosition + seconds * 1000L) } }
    fun seekTo(milliseconds: Long) { controller?.let { it.seekTo(milliseconds.coerceIn(0, it.duration.takeIf { n -> n > 0 } ?: Long.MAX_VALUE)) } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun speed(speed: Float) { controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 3f)) }
    fun repeat(enabled: Boolean) { controller?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    fun sleep(minutes: Int) {
        sleepTask?.let(handler::removeCallbacks)
        sleepEndsAt = if (minutes > 0) android.os.SystemClock.elapsedRealtime() + minutes * 60_000L else 0
        sleepTask = if (minutes > 0) Runnable { sleepEndsAt = 0; controller?.pause(); publish() }.also { handler.postDelayed(it, minutes * 60_000L) } else null
        publish()
    }

    private fun publish() {
        val player = controller ?: return
        _state.value = AudioPlaybackState(player.currentMediaItem?.mediaId, player.currentPosition.coerceAtLeast(0),
            player.duration.takeIf { it > 0 } ?: 0, player.isPlaying, player.playbackParameters.speed,
            player.repeatMode == Player.REPEAT_MODE_ONE, player.playerError?.let(::playbackMessage),
            (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }, player.playWhenReady, player.playbackState == Player.STATE_BUFFERING,
            if (sleepEndsAt == 0L) 0 else ((sleepEndsAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0) + 59_999).div(60_000).toInt())
    }
}

data class AudioPlaybackState(
    val id: String? = null, val positionMs: Long = 0, val durationMs: Long = 0,
    val playing: Boolean = false, val speed: Float = 1f, val repeat: Boolean = false,
    val error: String? = null,
    val queueIds: List<String> = emptyList(),
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val sleepMinutes: Int = 0,
)

/** What a reader can act on, not the player's error constant. */
private fun playbackMessage(error: androidx.media3.common.PlaybackException): String = when (error.errorCode) {
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "تعذّر تشغيل التسجيل: لا يوجد اتصال بالإنترنت"
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "تعذّر تشغيل التسجيل: الملف غير متاح"
    else -> "تعذّر تشغيل التسجيل"
}
