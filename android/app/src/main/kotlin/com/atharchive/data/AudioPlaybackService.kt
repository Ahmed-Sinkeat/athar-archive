package com.atharchive.data

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.atharchive.MainActivity
import org.json.JSONArray
import org.json.JSONObject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@dagger.hilt.android.AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {
    @javax.inject.Inject lateinit var content: ContentAccess
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val savePosition = object : Runnable {
        override fun run() {
            if (session?.player?.isPlaying == true) persist()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val source = androidx.media3.datasource.ResolvingDataSource.Factory(
            androidx.media3.datasource.DefaultDataSource.Factory(this),
        ) { spec ->
            val id = spec.key
            if (id == null) spec else spec.withUri(android.net.Uri.parse(kotlinx.coroutines.runBlocking { content.audioUri(id) }))
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(source))
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(15_000)
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
        val prefs = getSharedPreferences("audio_playback", MODE_PRIVATE)
        runCatching {
            val queue = JSONArray(getSharedPreferences("audio_queue", MODE_PRIVATE).getString("queue", prefs.getString("queue", "[]")))
            val items = (0 until queue.length()).map { index ->
                val row = queue.getJSONObject(index)
                MediaItem.Builder().setMediaId(row.getString("id")).setCustomCacheKey(row.getString("id")).setUri(row.getString("uri"))
                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(row.optString("title")).setArtist(row.optString("artist")).build()).build()
            }
            if (items.isNotEmpty()) {
                val currentId = prefs.getString("current_id", null)
                val index = if (currentId == null) prefs.getInt("index", 0).coerceIn(items.indices)
                    else items.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
                val position = if (currentId == null || items[index].mediaId == currentId) prefs.getLong("position", 0)
                    else prefs.getLong("position:${items[index].mediaId}", 0)
                player.setMediaItems(items, index, position.coerceAtLeast(0))
                player.repeatMode = if (prefs.getBoolean("repeat", false)) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                player.setPlaybackSpeed(prefs.getFloat("speed", 1f).coerceIn(0.5f, 3f))
                player.prepare()
            }
        }
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                oldPosition.mediaItem?.mediaId?.let { id ->
                    getSharedPreferences("audio_playback", MODE_PRIVATE).edit().putLong("position:$id", oldPosition.positionMs.coerceAtLeast(0)).apply()
                }
            }
            override fun onEvents(player: Player, events: Player.Events) { persist() }
        })
        handler.post(savePosition)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session.takeIf { controllerInfo.packageName == packageName || controllerInfo.isTrusted }

    private fun persist() {
        val player = session?.player ?: return
        val queue = JSONArray()
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            queue.put(JSONObject().put("id", item.mediaId).put("uri", item.localConfiguration?.uri.toString())
                .put("title", item.mediaMetadata.title?.toString().orEmpty()).put("artist", item.mediaMetadata.artist?.toString().orEmpty()))
        }
        val editor = getSharedPreferences("audio_playback", MODE_PRIVATE).edit()
        // Queue changes are rare; keep it out of the file rewritten for position ticks.
        val queuePrefs = getSharedPreferences("audio_queue", MODE_PRIVATE)
        val serialized = queue.toString()
        if (queuePrefs.getString("queue", null) != serialized) queuePrefs.edit().putString("queue", serialized).apply()
        val position = if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition.coerceAtLeast(0)
        player.currentMediaItem?.mediaId?.let { editor.putLong("position:$it", position) }
        editor.remove("queue").putString("current_id", player.currentMediaItem?.mediaId)
            .putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("position", position).putFloat("speed", player.playbackParameters.speed).putBoolean("repeat", player.repeatMode == Player.REPEAT_MODE_ONE).apply()
    }

    override fun onDestroy() {
        persist()
        handler.removeCallbacksAndMessages(null)
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
