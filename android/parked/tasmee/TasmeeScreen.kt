package com.atharchive.feature.tasmee

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.atharchive.ui.components.AtharLoading
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * تسميع: the text stays hidden and each word appears once it is said. The words are heard by
 * [TasmeeEngine], a recitation model on the phone, and followed by [TasmeeTracker]. It checks
 * the letters, not the harakat.
 */
@Composable
fun TasmeeScreen(title: String, text: TasmeeText?, onBack: () -> Unit) {
    val context = LocalContext.current
    // Opened with the screen and closed on leaving: the model holds ~400 MB while open.
    val engine by produceState<Result<TasmeeEngine?>?>(null) {
        val opened = withContext(Dispatchers.Default) { runCatching { TasmeeEngine.open(context) } }
        value = opened
        awaitDispose { opened.getOrNull()?.close() }
    }
    Column(Modifier.fillMaxSize().background(AtharTheme.colors.canvas)) {
        AtharTopBar(title = "تسميع: $title", onSettings = {}, showAppIcon = true, onBack = onBack)
        val ready = engine?.getOrNull()
        when {
            text == null || engine == null -> AtharLoading("جارٍ تحميل التسميع…", Modifier.padding(top = 120.dp), tag = "tasmee_loading")
            text.lines.isEmpty() -> Message("لا نص للتسميع هنا.")
            ready == null -> Message(engine?.exceptionOrNull()?.let { "تعذّر فتح نموذج التسميع: ${it.message}" } ?: "نموذج التسميع غير موجود على هذا الجهاز.")
            else -> Recital(text, ready)
        }
    }
}

@Composable
private fun Message(text: String) = Text(text, Modifier.padding(24.dp), color = AtharTheme.colors.secondaryText)

@Composable
private fun Recital(text: TasmeeText, engine: TasmeeEngine) {
    val context = LocalContext.current
    val lines = text.lines
    val words = remember(lines) { lines.flatten() }
    val lineStarts = remember(lines) { lines.runningFold(0) { at, line -> at + line.size } }
    val tracker = remember(lines) { TasmeeTracker(engine.vocab, words).apply { restart(TasmeeProgress(lineStarts[text.start])) } }
    var from by remember(lines) { mutableIntStateOf(text.start) }
    var shown by remember(lines) { mutableStateOf(tracker.progress) }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    val done = tracker.done

    val view = LocalView.current
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        runCatching {
            engine.listen { frames ->
                // The tracker is touched only on the main thread; the model runs off it.
                withContext(Dispatchers.Main) {
                    if (tracker.feed(frames)) shown = tracker.progress
                    // tuning log, drop once تسميع is judged on the phone
                    android.util.Log.i("Tasmee", "heard «${engine.vocab.greedy(frames)}» → word ${tracker.progress.cursor}/${words.size}, expected «${words.drop(tracker.progress.cursor).take(5).joinToString(" ")}»")
                    if (tracker.done) listening = false
                }
            }
        }.onFailure { if (it !is kotlinx.coroutines.CancellationException) { status = "توقّف الاستماع: ${it.message}"; listening = false } else throw it }
    }
    // Android silences the mic in the background anyway; stop instead of feeding the tracker silence.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { listening = false }
    fun restartAt(line: Int) {
        from = line.coerceIn(0, lines.size - 1)
        tracker.restart(TasmeeProgress(lineStarts[from]))
        shown = tracker.progress
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { status = null; listening = true } else status = "التسميع يحتاج إذن الميكروفون."
    }

    val list = rememberLazyListState()
    val currentLine = (lineStarts.indexOfLast { it <= shown.cursor }).coerceIn(from, lines.size - 1)
    LaunchedEffect(currentLine) { list.animateScrollToItem(currentLine - from) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(20.dp, 12.dp)) {
            itemsIndexed(lines.subList(from, currentLine + 1)) { offset, line ->
                val index = from + offset
                val start = lineStarts[index]
                val missed = MaterialTheme.colorScheme.error
                val hinted = AtharTheme.colors.accent
                Text(
                    buildAnnotatedString {
                        line.forEachIndexed { k, word ->
                            val at = start + k
                            if (at >= shown.cursor) return@forEachIndexed
                            if (k > 0) append(' ')
                            when (shown.marks[at]) {
                                TasmeeMark.Missed -> withStyle(SpanStyle(color = missed, textDecoration = TextDecoration.Underline)) { append(word) }
                                TasmeeMark.Hinted -> withStyle(SpanStyle(color = hinted)) { append(word) }
                                null -> append(word)
                            }
                        }
                        if (index == currentLine && !done) append(" …")
                    },
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    fontFamily = AtharEditorialFontFamily,
                    fontSize = 22.sp,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().background(AtharTheme.colors.raisedSurface).navigationBarsPadding().padding(16.dp, 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val missedCount = shown.marks.count { it.value == TasmeeMark.Missed }
            val hintedCount = shown.marks.count { it.value == TasmeeMark.Hinted }
            Text(
                (if (done) "اكتمل التسميع · " else "") + "فائت $missedCount · تلميح $hintedCount",
                color = AtharTheme.colors.secondaryText, fontSize = 13.sp,
            )
            status?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box {
                    TextButton(onClick = { choosing = true }) { Text("من أين؟") }
                    StartMenu(choosing, lines.size, text.start, { choosing = false }) { line -> choosing = false; restartAt(line) }
                }
                FilledIconButton(
                    onClick = {
                        when {
                            listening -> listening = false
                            done -> Unit
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> { status = null; listening = true }
                            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (listening) MaterialTheme.colorScheme.error else AtharTheme.colors.primaryAction,
                        contentColor = AtharTheme.colors.onPrimaryAction,
                    ),
                ) { Icon(if (listening) AtharIcons.Pause else AtharIcons.Mic, if (listening) "إيقاف" else "ابدأ التسميع", Modifier.size(30.dp)) }
                TextButton(onClick = { tracker.hint(); shown = tracker.progress }, enabled = !done) { Text("تلميح") }
            }
        }
    }
}

/** Where to start: the beginning, where the reader stood, or a typed line number. */
@Composable
private fun StartMenu(expanded: Boolean, total: Int, reading: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AtharMenu(expanded, onDismiss, width = 240.dp) {
        AtharMenuItem("من البداية", { onPick(0) })
        if (reading > 0) AtharMenuItem("من موضع القراءة", { onPick(reading) }, subtitle = "السطر ${reading + 1}")
        var typed by remember { mutableStateOf("") }
        val ink = MaterialTheme.colorScheme.onSurface
        Row(Modifier.padding(12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("من السطر", color = ink, fontSize = 15.sp)
            BasicTextField(
                typed,
                { typed = it.filter(Char::isDigit).take(4) },
                Modifier.width(56.dp).border(1.dp, AtharTheme.colors.placeholderText, AtharCorners.Surface).padding(6.dp, 4.dp),
                textStyle = TextStyle(color = ink, fontSize = 15.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(ink),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions { typed.toIntOrNull()?.let { onPick(it - 1) } },
            )
            Text("من $total", color = AtharTheme.colors.secondaryText, fontSize = 13.sp)
            TextButton(onClick = { typed.toIntOrNull()?.let { onPick(it - 1) } }, enabled = typed.isNotEmpty()) { Text("ابدأ") }
        }
    }
}
