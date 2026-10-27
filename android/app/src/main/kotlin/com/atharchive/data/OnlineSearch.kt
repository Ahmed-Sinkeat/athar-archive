package com.atharchive.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The site's full-text index (src/pages/api/search.ts, D1 FTS5): the whole library, not only
 * what is on the phone. Any failure throws [IOException]; the caller falls back to local search.
 */
internal class OnlineSearch(private val http: OkHttpClient) {
    data class Hit(val type: String, val url: String, val title: String, val book: String, val snippet: String, val row: Long = 0)
    data class Person(val slug: String, val count: Int)
    data class Facets(val total: Int, val capped: Boolean, val persons: List<Person>)
    data class Query(
        val q: String,
        /** all | any | phrase */
        val mode: String,
        /** all | title | text */
        val field: String,
        val types: Set<String>,
        val persons: Set<String>,
        /** relevance | death */
        val sort: String,
    )

    /** Pages of 20 until [limit] hits or the end; each page is edge-cached for 30 minutes. */
    suspend fun hits(query: Query, limit: Int): Pair<List<Hit>, Boolean> {
        val hits = mutableListOf<Hit>()
        var more = true
        while (more && hits.size < limit) {
            val body = get(url(query).addQueryParameter("offset", hits.size.toString()).addQueryParameter("snip", "48").build())
            val page = body.getJSONArray("hits")
            for (i in 0 until page.length()) page.getJSONObject(i).run {
                hits += Hit(optString("type"), optString("url"), optString("title"), optString("book"), optString("snippet"), optLong("row"))
            }
            more = body.optBoolean("hasMore") && page.length() > 0
        }
        return hits to more
    }

    /** Null from a site that predates the facets call (it answers with hits instead). */
    suspend fun facets(query: Query): Facets? {
        val facets = get(url(query).addQueryParameter("facets", "1").build()).optJSONObject("facets") ?: return null
        val persons = facets.getJSONArray("persons")
        return Facets(
            total = facets.optInt("total"),
            capped = facets.optBoolean("capped"),
            persons = (0 until persons.length()).map { persons.getJSONObject(it).run { Person(optString("slug"), optInt("n")) } },
        )
    }

    /** The whole passage behind a hit, as written, for «المزيد»: by its row (instant), else by url. */
    suspend fun fullText(url: String, row: Long): String =
        get(Base.newBuilder().apply { if (row > 0) addQueryParameter("row", row.toString()) else addQueryParameter("url", url) }.build()).optString("text")

    private fun url(query: Query) = Base.newBuilder()
        .addQueryParameter("q", query.q)
        .addQueryParameter("mode", query.mode)
        .addQueryParameter("field", query.field)
        .addQueryParameter("type", query.types.joinToString(","))
        .addQueryParameter("sort", query.sort)
        .apply { if (query.persons.isNotEmpty()) addQueryParameter("person", query.persons.joinToString(",")) }

    private suspend fun get(url: HttpUrl): JSONObject = suspendCancellableCoroutine { cont ->
        val call = http.newCall(Request.Builder().url(url).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) = response.use {
                val text = it.body?.string()
                if (!it.isSuccessful || text == null) cont.resumeWithException(IOException("search HTTP ${it.code}"))
                else runCatching { JSONObject(text) }.fold(cont::resume) { e -> cont.resumeWithException(IOException(e)) }
            }
        })
    }

    private companion object {
        val Base = "https://athar.arthurarchive.com/api/search".toHttpUrl()
    }
}

/** «…a <mark>b</mark> c» → the plain text and every marked range in it. */
internal fun unmark(snippet: String): Pair<String, List<IntRange>> {
    val text = StringBuilder()
    val ranges = mutableListOf<IntRange>()
    var rest = snippet
    while (true) {
        val open = rest.indexOf("<mark>")
        if (open < 0) break
        val close = rest.indexOf("</mark>", open)
        if (close < 0) break
        text.append(rest, 0, open)
        val start = text.length
        text.append(rest, open + 6, close)
        ranges += start until text.length
        rest = rest.substring(close + 7)
    }
    text.append(rest)
    return text.toString() to ranges
}
