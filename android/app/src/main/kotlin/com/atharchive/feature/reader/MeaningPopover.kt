package com.atharchive.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.data.Meaning
import com.atharchive.ui.components.AtharTailPopover
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTextSize

/** «معنى»: the lexicons; provided by the app shell, null hides it. */
val LocalDictionary = staticCompositionLocalOf<com.atharchive.data.Dictionary?> { null }

/** Opens a book at a block ordinal — a lexicon entry's source line. */
val LocalOpenBookAt = staticCompositionLocalOf<((String, Int) -> Unit)?> { null }

/**
 * «معنى» for [word]: the meaning once the lexicons are on the phone; until then, the offer to
 * download them («تنزيل المعجم»), its progress, and then the meaning itself.
 */
@Composable
internal fun MeaningCallout(word: String, anchor: (IntRect) -> Rect, colors: ReaderColors, onDismiss: () -> Unit) {
    val dict = LocalDictionary.current ?: return
    val installed by dict.installed.collectAsState()
    val progress by dict.progress.collectAsState()
    val failed by dict.failed.collectAsState()
    // (looked up yet, what was found)
    val found by produceState<Pair<Boolean, Meaning?>>(false to null, word, installed) {
        value = if (installed) true to runCatching { dict.meaning(word) }.getOrNull() else false to null
    }
    found.second?.let { return MeaningPopover(it, anchor, colors, onDismiss) }
    val ink = colors.page
    AtharTailPopover(anchor = anchor, ground = colors.text.copy(alpha = 0.94f), onDismiss = onDismiss,
        modifier = Modifier.testTag("meaning_popover"), maxWidth = 300.dp) {
        Text(word.trim { !it.isLetter() && it !in '\u064B'..'\u0652' }, color = ink, fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold, fontSize = AtharTextSize.Ui)
        val note = when {
            installed && found.first -> "لا توجد الكلمة في المعاجم"
            installed -> "\u2026"
            else -> "معاني الكلمات من معاجم اللغة؛ تُنزَّل مرة واحدة وتعمل دون اتصال."
        }
        Text(note, Modifier.padding(top = 4.dp), color = ink.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
        if (!installed) {
            val p = progress
            Text(
                text = when {
                    p != null -> "جارٍ التنزيل\u2026 " + (if (p.second > 0) "${p.first * 100 / p.second}\u066A" else "${p.first shr 20} م.ب")
                    failed -> "تعذّر التنزيل؛ اضغط لإعادة المحاولة"
                    else -> "تنزيل المعجم"
                },
                modifier = Modifier.padding(top = 8.dp)
                    .clickable(enabled = p == null, role = Role.Button) { dict.startDownload() }
                    .padding(vertical = 6.dp).testTag("meaning_download"),
                color = ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The meaning callout, pinned to the word (same ground as the poem glossary's).
 * Two entries in full clauses, the rest by name; every source name opens its book there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeaningPopover(meaning: Meaning, anchor: (IntRect) -> Rect, colors: ReaderColors, onDismiss: () -> Unit) {
    val open = LocalOpenBookAt.current
    val ink = colors.page
    @Composable
    fun SourceName(title: String, bookId: String, ordinal: Int, modifier: Modifier = Modifier) = Text(
        text = title,
        modifier = modifier.clickable(enabled = open != null, role = Role.Button) { onDismiss(); open?.invoke(bookId, ordinal) }
            .padding(vertical = 4.dp),
        color = ink.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
    AtharTailPopover(
        anchor = anchor,
        ground = colors.text.copy(alpha = 0.94f),
        onDismiss = onDismiss,
        modifier = Modifier.testTag("meaning_popover"),
        maxWidth = 300.dp,
    ) {
        Text(
            text = meaning.word + "  ·  " + meaning.roots.joinToString("  ،  ") { it.toList().joinToString(" ") },
            color = ink,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = AtharTextSize.Ui,
        )
        meaning.entries.take(2).forEach { e ->
            Text(
                text = e.snippet,
                modifier = Modifier.padding(top = 6.dp),
                color = ink.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            SourceName(e.source, e.bookId, e.ordinal)
        }
        val rest = meaning.entries.drop(2)
        if (rest.isNotEmpty()) FlowRow(Modifier.padding(top = 2.dp)) {
            Text("وفي: ", color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
            rest.forEachIndexed { i, e ->
                SourceName(e.source + if (i < rest.lastIndex) "، " else "", e.bookId, e.ordinal)
            }
        }
    }
}
