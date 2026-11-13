package com.atharchive.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A dhikr the reader wrote or took from a book, in one or more lists (built-in «morning»/«evening» or their own). */
data class UserDhikr(val id: String, val text: String, val repeat: Int, val source: String?, val lists: Set<String>)

/** A list the reader made, shown as a tab beside الصباح and المساء. */
data class UserAdhkarList(val id: String, val label: String)

data class UserAdhkarState(val lists: List<UserAdhkarList> = emptyList(), val items: List<UserDhikr> = emptyList())

/**
 * The reader's own adhkar, kept on the phone only.
 * one JSON string in SharedPreferences, rewritten whole on every change; fine for
 * hundreds of entries. Move to the app database if people keep thousands or want sync.
 */
class UserAdhkarStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("adhkar_user", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(runCatching { decode(prefs.getString(Key, null)) }.getOrDefault(UserAdhkarState()))
    val state: StateFlow<UserAdhkarState> = _state

    /** Adds or replaces [dhikr] (matched by id); a blank text or a dhikr in no list is refused. */
    fun save(dhikr: UserDhikr): Boolean {
        val clean = dhikr.copy(text = dhikr.text.trim(), repeat = dhikr.repeat.coerceIn(1, 100_000), source = dhikr.source?.trim()?.ifBlank { null })
        if (clean.text.isEmpty() || clean.lists.isEmpty()) return false
        val items = _state.value.items
        write(_state.value.copy(items = if (items.any { it.id == clean.id }) items.map { if (it.id == clean.id) clean else it } else items + clean))
        return true
    }

    fun delete(id: String) = write(_state.value.copy(items = _state.value.items.filterNot { it.id == id }))

    /** A new list named [label]; returns its id (an existing list of the same name is reused). */
    fun addList(label: String): String {
        val name = label.trim()
        _state.value.lists.firstOrNull { it.label == name }?.let { return it.id }
        val list = UserAdhkarList("u-" + java.util.UUID.randomUUID().toString().take(8), name)
        write(_state.value.copy(lists = _state.value.lists + list))
        return list.id
    }

    /** Removes the list and takes it off its adhkar; an entry left in no list goes with it. */
    fun deleteList(id: String) {
        val s = _state.value
        write(UserAdhkarState(
            s.lists.filterNot { it.id == id },
            s.items.map { it.copy(lists = it.lists - id) }.filter { it.lists.isNotEmpty() },
        ))
    }

    /** Everything, as text for the user-data export file. */
    fun exportJson(): String = encode(_state.value)

    /**
     * Folds an exported copy in: lists match by name, entries by id; an entry already here is
     * kept as it is (the phone's own edit wins). Returns how many entries were added.
     */
    fun merge(json: String): Int {
        val (next, added) = merged(_state.value, decode(json))
        write(next)
        return added
    }

    private fun write(next: UserAdhkarState) {
        _state.value = next
        prefs.edit().putString(Key, encode(next)).apply()
    }

    companion object {
        private const val Key = "v1"
        @Volatile private var instance: UserAdhkarStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: UserAdhkarStore(context.applicationContext).also { instance = it } }
        fun newId() = "u-" + java.util.UUID.randomUUID().toString()

        internal fun merged(s: UserAdhkarState, incoming: UserAdhkarState): Pair<UserAdhkarState, Int> {
            val lists = s.lists.toMutableList()
            val listId = incoming.lists.associate { list ->
                list.id to (lists.firstOrNull { it.label == list.label.trim() }?.id ?: list.also { lists += it }.id)
            }
            val known = s.items.mapTo(hashSetOf()) { it.id }
            val added = incoming.items.filter { it.id !in known && it.text.isNotBlank() }
                .map { it.copy(repeat = it.repeat.coerceIn(1, 100_000), lists = it.lists.mapTo(linkedSetOf()) { id -> listId[id] ?: id }) }
                .filter { it.lists.isNotEmpty() }
            return UserAdhkarState(lists, s.items + added) to added.size
        }

        internal fun encode(s: UserAdhkarState): String = JSONObject()
            .put("lists", JSONArray(s.lists.map { JSONObject().put("id", it.id).put("label", it.label) }))
            .put("items", JSONArray(s.items.map {
                JSONObject().put("id", it.id).put("text", it.text).put("repeat", it.repeat)
                    .put("source", it.source ?: JSONObject.NULL).put("lists", JSONArray(it.lists.toList()))
            }))
            .toString()

        internal fun decode(json: String?): UserAdhkarState {
            if (json.isNullOrBlank()) return UserAdhkarState()
            val o = JSONObject(json)
            fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
            return UserAdhkarState(
                lists = o.optJSONArray("lists")?.objects().orEmpty().map { UserAdhkarList(it.getString("id"), it.getString("label")) },
                items = o.optJSONArray("items")?.objects().orEmpty().map { j ->
                    val lists = j.optJSONArray("lists")
                    UserDhikr(
                        j.getString("id"), j.getString("text"), j.optInt("repeat", 1),
                        j.optString("source").takeIf { !j.isNull("source") && it.isNotBlank() },
                        (0 until (lists?.length() ?: 0)).mapTo(linkedSetOf()) { lists!!.getString(it) },
                    )
                },
            )
        }
    }
}
