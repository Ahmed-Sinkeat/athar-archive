package com.atharchive.data

import android.content.Context
import android.content.SharedPreferences
import com.atharchive.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Anonymous usage counts (workers/search/index.ts → Analytics Engine): an event name, a short
 * detail (a slug, never search words) and the app version. No id, nothing stored on the phone.
 * Events are batched (one POST of «event\tdetail\tversion» lines every [FlushMs], or at [FlushAt]) on the app's
 * shared client. the queue is in memory and offline or killed-process events are dropped; persist it
 * if the counts look thin.
 */
object Analytics {
    private val Endpoint = "https://search.arthurarchive.com/api/hit".toHttpUrl()
    private const val Key = "analytics_on"
    private const val FlushMs = 30_000L
    private const val FlushAt = 20
    private var http: dagger.Lazy<OkHttpClient>? = null
    private val queue = ArrayList<String>()
    private val flusher = android.os.Handler(android.os.Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    fun init(context: Context, client: dagger.Lazy<OkHttpClient>) {
        http = client
        prefs = context.getSharedPreferences("analytics", Context.MODE_PRIVATE).also {
            _enabled.value = it.getBoolean(Key, true)
        }
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs?.edit()?.putBoolean(Key, on)?.apply()
    }

    /** [event]: open | book | search | download | audio (anything else is refused server-side). */
    fun hit(event: String, detail: String = "") {
        if (!_enabled.value || prefs == null) return
        // Tabs and newlines would break the line format; a detail is a slug anyway.
        val line = listOf(event, detail.take(100).replace(Regex("[\\t\\r\\n]"), " "), BuildConfig.VERSION_NAME).joinToString("\t")
        val size = synchronized(queue) { queue.add(line); queue.size }
        if (size >= FlushAt) flush() else if (size == 1) flusher.postDelayed(flushTask, FlushMs)
    }

    private val flushTask = Runnable { flush() }

    private fun flush() {
        flusher.removeCallbacks(flushTask)
        val lines = synchronized(queue) { queue.toList().also { queue.clear() } }
        val client = http?.get() ?: return
        if (lines.isEmpty()) return
        val body = lines.joinToString("\n").toRequestBody("text/plain".toMediaType())
        client.newCall(Request.Builder().url(Endpoint).post(body).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) = response.close()
        })
    }
}
