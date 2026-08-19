package com.atharchive.m0

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import android.view.FrameMetrics
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun R7Screen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<R7Session?>(null) }
    var restoredOrdinal by rememberSaveable { mutableStateOf<Int?>(null) }
    var restorationExact by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking for an existing R7 database…") }

    LaunchedEffect(Unit) {
        val existing = M0EndToEndExperiment.openExisting(context)
        if (existing == null) {
            status = "Ready. The full import + FTS run uses the real 81.98 MiB book."
        } else {
            val restored = M0EndToEndExperiment.restoredPosition(context, existing)
            restoredOrdinal = restored.first
            restorationExact = restored.second
            session = existing
        }
    }

    val current = session
    if (current == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("R7 — end-to-end workload", style = MaterialTheme.typography.titleLarge)
            Text(
                "No INTERNET permission is present in this harness, so the complete run is OS-enforced offline.",
            )
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    status = "Importing 75,464 blocks and building FTS…"
                    scope.launch {
                        runCatching { M0EndToEndExperiment.build(context) }
                            .onSuccess {
                                session = it
                                restoredOrdinal = it.setup.rows / 2
                                status = it.setup.report()
                            }
                            .onFailure { status = "FAIL\n${it.stackTraceToString()}" }
                        running = false
                    }
                },
            ) { Text(if (running) "Running…" else "Build R7 database") }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        R7Reader(
            session = current,
            initialOrdinal = restoredOrdinal ?: current.setup.rows / 2,
            restorationExact = restorationExact,
        )
    }
}

@Composable
private fun R7Reader(session: R7Session, initialOrdinal: Int, restorationExact: Boolean) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val pager = remember(session) {
        Pager(
            config = PagingConfig(
                pageSize = 30,
                initialLoadSize = 90,
                prefetchDistance = 20,
                enablePlaceholders = true,
            ),
            initialKey = initialOrdinal,
            pagingSourceFactory = session.database.dao()::pagingSource,
        ).flow
    }
    val blocks = pager.collectAsLazyPagingItems()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialOrdinal)
    var previousOrdinal by rememberSaveable { mutableStateOf(initialOrdinal) }
    var highlight by remember { mutableStateOf<R7SearchResult?>(null) }
    var selectionBlocks by remember { mutableStateOf<List<R7Block>?>(null) }
    var running by remember { mutableStateOf(false) }
    var report by remember {
        mutableStateOf(
            session.setup.report() +
                "\nrestored logical block: $initialOrdinal · exact saved ID: $restorationExact",
        )
    }

    LaunchedEffect(listState, blocks) {
        snapshotFlow { listState.firstVisibleItemIndex to blocks.itemCount }
            .distinctUntilChanged()
            .collectLatest { (ordinal, itemCount) ->
                delay(300)
                if (ordinal in 0 until itemCount) {
                    blocks.peek(ordinal)?.let { M0EndToEndExperiment.savePosition(context, it) }
                }
            }
    }

    val selection = selectionBlocks
    if (selection != null) {
        BackHandler { selectionBlocks = null }
        R7SelectionWindow(selection, onClose = { selectionBlocks = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text("R7 · real-book reader", style = MaterialTheme.typography.titleMedium)
        Text(
            "block ${listState.firstVisibleItemIndex} / ${session.setup.rows - 1}",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                enabled = !running,
                onClick = {
                    scope.launch {
                        listState.scrollToItem(session.setup.rows / 2)
                    }
                },
            ) { Text("Middle") }
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    previousOrdinal = listState.firstVisibleItemIndex
                    scope.launch {
                        runCatching {
                            val result = M0EndToEndExperiment.search(session)
                            val jumpStart = SystemClock.elapsedRealtimeNanos()
                            listState.scrollToItem(result.block.ordinal)
                            withFrameNanos { }
                            val jumpMs = (SystemClock.elapsedRealtimeNanos() - jumpStart) / 1_000_000.0
                            highlight = result
                            M0EndToEndExperiment.savePosition(context, result.block)
                            report = "phrase search: ${result.searchMs.r7UiOneDecimal()} ms\n" +
                                "jump + first frame: ${jumpMs.r7UiOneDecimal()} ms\n" +
                                "exact vocalised source range: ${result.exactVocalizedHighlight}\n" +
                                "range UTF-16: ${result.sourceRange.first}..${result.sourceRange.last}\n" +
                                "zero network: enforced (no INTERNET permission)"
                        }.onFailure {
                            report = "FAIL · search + jump\n${it.stackTraceToString()}"
                        }
                        running = false
                    }
                },
            ) { Text("Search + jump") }
            Button(
                enabled = !running,
                onClick = { scope.launch { listState.scrollToItem(previousOrdinal) } },
            ) { Text("Return") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    scope.launch {
                        val recorder = R7FrameRecorder(activity)
                        val peakRss = AtomicLong(M0EndToEndExperiment.currentRssKb())
                        val rssSampler = launch(Dispatchers.IO) {
                            while (isActive) {
                                peakRss.accumulateAndGet(M0EndToEndExperiment.currentRssKb(), ::maxOf)
                                delay(20)
                            }
                        }
                        recorder.start()
                        val endAt = SystemClock.elapsedRealtime() + 10_000
                        while (SystemClock.elapsedRealtime() < endAt) {
                            listState.scrollBy(180f)
                            withFrameNanos { }
                        }
                        rssSampler.cancelAndJoin()
                        val frames = recorder.stop()
                        report = "10 s driven fling: ${frames.frames} measured frames\n" +
                            "dropped vsync intervals: ${frames.dropped}\n" +
                            "slowest frame: ${frames.slowestMs.r7UiOneDecimal()} ms\n" +
                            "peak reading RSS: ${(peakRss.get() / 1024.0).r7UiOneDecimal()} MiB"
                        running = false
                    }
                },
            ) { Text("10 s fling") }
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    scope.launch {
                        selectionBlocks = M0EndToEndExperiment.selectionWindow(
                            session,
                            listState.firstVisibleItemIndex,
                        )
                        running = false
                    }
                },
            ) { Text("Selection ±60") }
        }
        Text(report, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(
                count = blocks.itemCount,
                key = blocks.itemKey { it.blockId },
            ) { ordinal ->
                val block = blocks[ordinal]
                if (block == null) {
                    Text("Loading block $ordinal…", modifier = Modifier.padding(16.dp))
                } else {
                    val activeHighlight = highlight?.takeIf { it.block.blockId == block.blockId }
                    val text = if (activeHighlight == null) {
                        buildAnnotatedString { append(block.body) }
                    } else {
                        buildAnnotatedString {
                            append(block.body)
                            addStyle(
                                SpanStyle(background = Color(0xFFFFD54F)),
                                activeHighlight.sourceRange.first,
                                activeHighlight.sourceRange.last + 1,
                            )
                        }
                    }
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun R7SelectionWindow(blocks: List<R7Block>, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onClose) { Text("Close") }
            Text(
                "${blocks.size} composed blocks · long-press and copy across blocks",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SelectionContainer(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                blocks.forEach { block ->
                    Text(
                        block.body,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private data class R7FrameResult(val frames: Int, val dropped: Int, val slowestMs: Double)

private class R7FrameRecorder(private val activity: Activity) {
    private val thread = HandlerThread("r7-frame-metrics")
    private val frameCount = AtomicInteger()
    private val droppedCount = AtomicInteger()
    private val slowestNanos = AtomicLong()
    private val frameBudgetNanos = (1_000_000_000.0 / activity.display.refreshRate).toLong()
    private val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        frameCount.incrementAndGet()
        slowestNanos.accumulateAndGet(duration, ::maxOf)
        droppedCount.addAndGet(maxOf(0, (duration / frameBudgetNanos).toInt() - 1))
    }

    fun start() {
        thread.start()
        activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
    }

    fun stop(): R7FrameResult {
        activity.window.removeOnFrameMetricsAvailableListener(listener)
        thread.quitSafely()
        return R7FrameResult(
            frames = frameCount.get(),
            dropped = droppedCount.get(),
            slowestMs = slowestNanos.get() / 1_000_000.0,
        )
    }
}

private fun Double.r7UiOneDecimal(): String = "%.1f".format(this)
