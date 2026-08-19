package com.atharchive.m0

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Selection
import android.text.Spannable
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

private data class R1Block(val id: String, val text: String, val start: Int, val end: Int)
private data class R1Anchor(val blockId: String, val offset: Int)

private class R1Fixture(blockCount: Int = 260) {
    val text: String
    val blocks: List<R1Block>

    init {
        val source = List(blockCount) { index ->
            "المقطع $index — الْعِلْمُ نُورٌ ۝ وَقَالَ عَبْدُ اللَّهِ: إِنَّ النَّصَّ أَمَانَةٌ."
        }
        var cursor = 0
        blocks = source.mapIndexed { index, body ->
            R1Block("block-$index", body, cursor, cursor + body.length).also {
                cursor += body.length + if (index == source.lastIndex) 0 else 2
            }
        }
        text = source.joinToString("\n\n")
    }

    fun startAnchor(offset: Int): R1Anchor {
        val clamped = offset.coerceIn(0, text.length)
        val block = blocks.firstOrNull { clamped <= it.end } ?: blocks.last()
        return R1Anchor(block.id, (clamped - block.start).coerceIn(0, block.text.length))
    }

    fun endAnchor(endExclusive: Int): R1Anchor {
        val clamped = endExclusive.coerceIn(0, text.length)
        val block = blocks.lastOrNull { clamped >= it.start } ?: blocks.first()
        return R1Anchor(block.id, (clamped - block.start).coerceIn(0, block.text.length))
    }
}

@Composable
internal fun R1FallbackScreen() {
    val fixture = remember { R1Fixture() }
    var textView by remember { mutableStateOf<TextView?>(null) }
    var report by remember {
        mutableStateOf(
            "Compose SelectionContainer exposes 0 public endpoint callbacks. " +
                "This fallback is one selectable native text surface over a bounded reader window.",
        )
    }

    fun inspectAndCopy(view: TextView, startValue: Int, endValue: Int) {
        val start = min(startValue, endValue).coerceAtLeast(0)
        val end = max(startValue, endValue).coerceAtMost(fixture.text.length)
        if (start == end) return
        val selected = fixture.text.substring(start, end)
        val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Athar selection", selected))
        val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(view.context)?.toString()
        val startAnchor = fixture.startAnchor(start)
        val endAnchor = fixture.endAnchor(end)
        report = "start ${startAnchor.blockId}:${startAnchor.offset} · " +
            "end ${endAnchor.blockId}:${endAnchor.offset}\n" +
            "UTF-16 selected: ${end - start} · exact clipboard: ${copied == selected}\n" +
            "separator rule: source uses exactly two LF characters between blocks"
    }

    Column(Modifier.fillMaxSize()) {
        Text("R1 — selectable reader fallback", style = MaterialTheme.typography.titleMedium)
        Text(report, style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    textView?.let { view ->
                        val start = fixture.blocks[10].start + 3
                        val end = fixture.blocks[230].start + 17
                        Selection.setSelection(view.text as Spannable, start, end)
                        inspectAndCopy(view, start, end)
                        view.layout?.let { layout ->
                            val line = layout.getLineForOffset(fixture.blocks[230].start)
                            view.scrollTo(0, layout.getLineTop(line))
                        }
                    }
                },
            ) { Text("Check 220-block range") }
            Button(
                onClick = {
                    textView?.scrollTo(0, 0)
                },
            ) { Text("Top") }
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                TextView(context).apply {
                    text = fixture.text
                    textSize = 19f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    gravity = Gravity.START
                    textDirection = TextView.TEXT_DIRECTION_FIRST_STRONG_RTL
                    setPadding(32, 24, 32, 48)
                    setTextIsSelectable(true)
                    isVerticalScrollBarEnabled = true
                    setCustomSelectionActionModeCallback(
                        object : ActionMode.Callback {
                            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = true
                            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                                if (item.itemId != android.R.id.copy) return false
                                inspectAndCopy(this@apply, selectionStart, selectionEnd)
                                mode.finish()
                                return true
                            }

                            override fun onDestroyActionMode(mode: ActionMode) = Unit
                        },
                    )
                    textView = this
                }
            },
            update = { textView = it },
        )
    }
}
