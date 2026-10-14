package com.atharchive.feature.reader

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The reader's blocks, by source ordinal, loaded in fixed chunks around where the reader is.
 *
 * Replaces Paging 3 for the reader. A loaded block stays the same object until the book's
 * content actually changes, so a list item, its text layout and a long press are never
 * restarted by a refresh; chunks far from the reader are dropped to bound memory.
 * Each chunk is its own state, so a load recomposes only the items in that chunk.
 */
@Stable
class ReaderWindow(
    private val scope: CoroutineScope,
    private val total: suspend () -> Int,
    private val load: suspend (IntRange) -> List<ReaderBlock>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    /** Number of blocks in the book; 0 until [ready]. */
    var count by mutableIntStateOf(0)
        private set
    /** True once the count is known and the first chunk the reader opened on is in. */
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val chunks = HashMap<Int, MutableState<List<ReaderBlock>?>>()
    private val inFlight = HashSet<Int>()
    private var focus = -1
    private var startedAt: Int? = null

    private fun chunkState(chunk: Int) = chunks.getOrPut(chunk) { mutableStateOf(null) }

    /** Loads the count and the chunk holding [ordinal] together, so the list never shows a clamped position. */
    fun start(ordinal: Int) {
        if (startedAt != null) return
        startedAt = ordinal
        scope.launch(dispatcher) { begin(ordinal) }
    }

    internal suspend fun begin(ordinal: Int) {
        try {
            val size = total()
            if (size > 0) fill(chunkOf(ordinal.coerceIn(0, size - 1)), size)
            count = size
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "تعذّر تحميل النص" }
    }

    /** The block at [index], or null while its chunk loads. Reading it asks for the chunk and its neighbours. */
    operator fun get(index: Int): ReaderBlock? {
        val chunk = chunkOf(index)
        val blocks = chunkState(chunk).value
        if (chunk != focus) {
            focus = chunk
            // Writes happen in a later frame: the composition that asked never writes state itself.
            scope.launch(dispatcher) { around(chunk) }
        } else if (blocks == null) scope.launch(dispatcher) { request(chunk) }
        return blocks?.getOrNull(index - chunk * Chunk)
    }

    /** The block at [index] if loaded, without asking for anything. */
    fun peek(index: Int): ReaderBlock? = chunks[chunkOf(index)]?.value?.getOrNull(index - chunkOf(index) * Chunk)

    /** Every block currently held, in order. */
    fun held(): List<ReaderBlock> = chunks.keys.sorted().flatMap { chunks[it]?.value.orEmpty() }

    fun retry() {
        error = null
        val ordinal = startedAt ?: return
        scope.launch(dispatcher) { if (!ready) begin(ordinal) else around(focus) }
    }

    /** The content changed (a download finished or replaced it): reload what is held, in place. */
    fun invalidate() {
        if (!ready) return
        scope.launch(dispatcher) {
            try {
                count = total()
                for (chunk in chunks.filterValues { it.value != null }.keys) fill(chunk, count)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "تعذّر تحميل النص" }
        }
    }

    internal suspend fun around(chunk: Int) {
        // Drop far chunks first; the neighbours load after the one being read.
        chunks.keys.filter { kotlin.math.abs(it - chunk) > Keep }.forEach { chunks.remove(it)?.value = null }
        for (next in listOf(chunk, chunk + 1, chunk - 1)) request(next)
    }

    internal suspend fun request(chunk: Int) {
        if (chunk < 0 || chunk * Chunk >= count || chunkState(chunk).value != null || error != null) return
        try { fill(chunk, count) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "تعذّر تحميل النص" }
    }

    private suspend fun fill(chunk: Int, size: Int) {
        if (!inFlight.add(chunk)) return
        try {
            val first = chunk * Chunk
            val last = minOf(first + Chunk, size) - 1
            val rows = load(first..last)
            check(rows.map { it.ordinal } == (first..last).toList()) { "لم يكتمل تحميل النص؛ أعد المحاولة" }
            // A chunk the reader has moved far away from is not kept.
            if (focus < 0 || kotlin.math.abs(chunk - focus) <= Keep || chunk == startedAt?.let(::chunkOf)) {
                val state = chunkState(chunk)
                val old = state.value
                // Unchanged blocks keep their identity, so their items skip recomposition.
                state.value = if (old == null) rows else rows.mapIndexed { i, block -> old.getOrNull(i)?.takeIf { it == block } ?: block }
            }
        } finally {
            inFlight.remove(chunk)
        }
    }

    private fun chunkOf(index: Int) = index / Chunk

    companion object {
        // ponytail: fixed 50-block chunks, ±3 kept (~350 blocks); size them by frame bounds if the catalog ever carries them.
        const val Chunk = 50
        const val Keep = 3
    }
}
