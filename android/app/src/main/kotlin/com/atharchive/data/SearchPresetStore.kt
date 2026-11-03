package com.atharchive.data

import android.content.Context
import com.atharchive.feature.search.SearchField
import com.atharchive.feature.search.SearchFilters
import com.atharchive.feature.search.SearchMode
import com.atharchive.feature.search.SearchPreset
import com.atharchive.feature.search.SearchResultType
import com.atharchive.feature.search.SearchSort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** «الخيارات المثبتة»: saved search filters, a short JSON list in SharedPreferences (as AppearanceStore). */
@Singleton
class SearchPresetStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("search_presets", Context.MODE_PRIVATE)
    private val _presets = MutableStateFlow(read())
    val presets: StateFlow<List<SearchPreset>> = _presets

    /** Saving under an existing name replaces it. */
    fun save(name: String, filters: SearchFilters) = write(_presets.value.filter { it.name != name } + SearchPreset(name, filters))

    fun delete(name: String) = write(_presets.value.filter { it.name != name })

    private fun write(list: List<SearchPreset>) {
        _presets.value = list
        prefs.edit().putString("list", JSONArray(list.map { encode(it) }).toString()).apply()
    }

    private fun read(): List<SearchPreset> = runCatching {
        val array = JSONArray(prefs.getString("list", null) ?: return emptyList())
        (0 until array.length()).map { decode(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun encode(p: SearchPreset) = JSONObject().apply {
        val f = p.filters
        put("name", p.name)
        put("types", JSONArray(f.types.map { it.name }))
        put("field", f.field.name); put("sort", f.sort.name); put("descending", f.descending)
        put("mode", f.mode.name); put("downloadedOnly", f.downloadedOnly)
        put("authors", JSONArray(f.authors.toList())); put("books", JSONArray(f.books.toList()))
        put("centuries", JSONArray(f.centuries.toList())); put("sections", JSONArray(f.sections.toList())); put("plain", f.plain)
    }

    private fun decode(o: JSONObject): SearchPreset {
        fun set(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).mapTo(linkedSetOf()) { a.getString(it) } }.orEmpty()
        return SearchPreset(o.getString("name"), SearchFilters(
            types = set("types").mapNotNullTo(linkedSetOf()) { k -> SearchResultType.entries.find { it.name == k } },
            field = SearchField.entries.find { it.name == o.optString("field") } ?: SearchField.FullText,
            authors = set("authors"),
            sort = SearchSort.entries.find { it.name == o.optString("sort") } ?: SearchSort.Relevance,
            descending = o.optBoolean("descending"),
            mode = SearchMode.entries.find { it.name == o.optString("mode") } ?: SearchMode.All,
            downloadedOnly = o.optBoolean("downloadedOnly"),
            books = set("books"), centuries = set("centuries"), sections = set("sections"), plain = o.optBoolean("plain"),
        ))
    }
}
