package com.atharchive.data

import android.content.Context
import android.content.SharedPreferences
import com.atharchive.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Anonymous usage counts (workers/search/index.ts → Analytics Engine): an event name, a short
 * detail (a slug, never search words) and the app version. No id, nothing stored on the phone.
 * ponytail: fire-and-forget, offline events are dropped; queue them if the counts look thin.
 */
object Analytics {
    private val Endpoint = "https://search.arthurarchive.com/api/hit".toHttpUrl()
    private const val Key = "analytics_on"
    private val http by lazy { OkHttpClient() }
    private var prefs: SharedPreferences? = null
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    fun init(context: Context) {
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
        val url = Endpoint.newBuilder()
            .addQueryParameter("e", event)
            .addQueryParameter("d", detail.take(100))
            .addQueryParameter("v", BuildConfig.VERSION_NAME)
            .build()
        http.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) = response.close()
        })
    }
}
