package com.atharchive.core.data.repository

import com.atharchive.core.data.db.content.ContentBlockEntity

/** Reads blocks by source ordinal, fetching every frame a range needs that is not cached yet. */
class OrdinalBlockReader(
    private val count: suspend () -> Int,
    private val read: suspend (Int, Int) -> List<ContentBlockEntity>,
    private val fetch: suspend (Int) -> Unit,
) {
    suspend fun count(): Int = count.invoke()

    /** Exactly [first]..[last], in order, or an error — never a range with a hole in it. */
    suspend fun range(first: Int, last: Int): List<ContentBlockEntity> {
        require(first in 0..last) { "invalid block range" }
        var rows = read(first, last)
        while (rows.size != last - first + 1) {
            val present = rows.mapTo(hashSetOf()) { it.ordinal }
            val missing = (first..last).first { it !in present }
            fetch(missing)
            val refreshed = read(first, last)
            check(refreshed.size > rows.size) { "لم يكتمل تحميل النص؛ أعد المحاولة" }
            rows = refreshed
        }
        check(rows.map { it.ordinal } == (first..last).toList()) { "invalid content block order" }
        return rows
    }
}

/** Advances by source ordinals even when earlier frames leave the disposable cache. */
suspend fun visitBookFrames(total: Int, frameEnd: suspend (Int) -> Int, visit: suspend (IntRange) -> Boolean) {
    var next = 0
    while (next < total) {
        val last = frameEnd(next)
        check(last in next until total) { "invalid book frame range" }
        if (visit(next..last)) return
        next = last + 1
    }
}
