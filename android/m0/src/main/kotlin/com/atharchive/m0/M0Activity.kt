package com.atharchive.m0

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * M0 harness entry point. Each prototype gets one screen; results are recorded in
 * docs/android/m0-results.md, not in this app.
 *
 * Deliberately no navigation library — the Nav3 question is itself a prototype
 * (R8) and this harness must not pre-decide it.
 */
class M0Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { M0Root() } } }
    }
}

private data class Prototype(val id: String, val title: String, val question: String)

private val PROTOTYPES = listOf(
    Prototype("R1", "Selection over LazyColumn", "Does multi-block selection survive item disposal?"),
    Prototype("R2", "Room 3 + BundledSQLiteDriver", "APK delta, bm25, prefix indexes, cold open"),
    Prototype("R2b", "FTS: regular vs contentless_delete", "Size, latency, delete, import speed"),
    Prototype("R3", "Framed .athar import, 82 MB", "Frame size, resume after kill, throughput, RSS"),
    Prototype("R6", "Backup rules", "Cloud exclusion and device-transfer inclusion by API tier"),
    Prototype("R7", "End-to-end Athar workload", "The representative benchmark"),
    Prototype("R8", "Navigation 3", "Back-stack state, process death, and adaptive two-pane"),
)

@Composable
private fun M0Root() {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val current = PROTOTYPES.firstOrNull { it.id == openId }

    if (current == null) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(PROTOTYPES) { p ->
                Column(
                    Modifier
                        .clickable { openId = p.id }
                        .padding(16.dp)
                ) {
                    Text("${p.id} — ${p.title}", style = MaterialTheme.typography.titleMedium)
                    Text(p.question, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    } else {
        BackHandler { openId = null }
        when (current.id) {
            "R1" -> R1FallbackScreen()
            "R2" -> R2Screen()
            "R2b" -> R2bScreen()
            "R3" -> R3Screen()
            "R7" -> R7Screen()
            "R8" -> R8Screen()
            else -> NotYetBuilt(current)
        }
    }
}

@Composable
private fun R3Screen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var report by remember {
        mutableStateOf("Real 81.98 MiB markdown · 75,464 blocks · 38 independent gzip members.")
    }

    fun launch(label: String, block: suspend () -> R3Result) {
        running = true
        report = "$label…"
        scope.launch {
            report = runCatching { block().report() }.getOrElse { "FAIL\n${it.stackTraceToString()}" }
            running = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("R3 — framed 82 MB import", style = MaterialTheme.typography.titleMedium)
        Button(enabled = !running, onClick = { launch("Full import") { M0FramedImportExperiment.runFull(context) } }) {
            Text("Run full import")
        }
        Button(
            enabled = !running,
            onClick = { launch("Writing 10-frame checkpoint") { M0FramedImportExperiment.runFirstTenFrames(context) } },
        ) {
            Text("1. Import 10 frames")
        }
        Button(enabled = !running, onClick = { launch("Resuming") { M0FramedImportExperiment.resume(context) } }) {
            Text("2. Resume after force-stop")
        }
        Text(report, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun R2bScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("Ready. A/B tests 5,428 real blocks from 20 books.") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("R2b — FTS storage A/B", style = MaterialTheme.typography.titleMedium)
        Button(
            enabled = !running,
            onClick = {
                running = true
                report = "Running regular, contentless, then offline repair…"
                scope.launch {
                    report = runCatching { M0FtsAbExperiment.run(context).report() }
                        .getOrElse { "FAIL\n${it.stackTraceToString()}" }
                    running = false
                }
            },
        ) {
            Text(if (running) "Running…" else "Run R2b")
        }
        Text(report, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun R2Screen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("Ready. Runs a 20,000-row Room 3 FTS5 round-trip.") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("R2 — Room 3 + bundled SQLite", style = MaterialTheme.typography.titleMedium)
        Button(
            enabled = !running,
            onClick = {
                running = true
                report = "Running…"
                scope.launch {
                    report = runCatching { M0RoomExperiment.run(context).report() }
                        .getOrElse { "FAIL\n${it.stackTraceToString()}" }
                    running = false
                }
            },
        ) {
            Text(if (running) "Running…" else "Run R2")
        }
        Text(report, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NotYetBuilt(p: Prototype) {
    Column(Modifier.padding(16.dp)) {
        Text("${p.id} — not built yet", style = MaterialTheme.typography.titleMedium)
        Text(p.question, style = MaterialTheme.typography.bodyMedium)
    }
}
