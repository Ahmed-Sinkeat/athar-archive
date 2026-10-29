package com.atharchive.feature.adhkar

import com.atharchive.ui.components.normKey
import kotlinx.coroutines.launch
import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import com.atharchive.ui.components.arabicDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharProgressBar
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

/**
 * One screen for both shapes of الأذكار: the daily tabs, and a single باب opened from
 * the directory. The difference is [bab] — with it, the tabs and the headline go away
 * and the باب's name becomes the page title. Nothing else changes, because nothing else
 * should: the counter, the progress and the card are the same object either way.
 */
@Composable
fun AdhkarScreen(
    state: AdhkarUiState,
    onSettings: () -> Unit,
    onOpenBabs: () -> Unit,
    onCopy: (DhikrUi) -> Unit,
    onSave: (DhikrUi) -> Unit,
    modifier: Modifier = Modifier,
    bab: AdhkarBabUi? = null,
    onBack: (() -> Unit)? = null,
    /** The reader's font, size and text colours, so a dhikr reads like the books. */
    readerSettings: com.atharchive.feature.reader.ReaderSettings = com.atharchive.feature.reader.ReaderSettings(),
) {
    // ponytail: المساء from 15:00 until 04:00, near enough to العصر and الفجر without prayer times; real times when the app has them.
    var selectedTab by rememberSaveable {
        mutableStateOf(java.time.LocalTime.now().hour.let { if (it >= 15 || it < 4) AdhkarTab.Evening else AdhkarTab.Morning })
    }
    var query by rememberSaveable { mutableStateOf("") }
    // Counts last the day: closing the app mid-way keeps your place; a new day starts clean.
    val context = androidx.compose.ui.platform.LocalContext.current
    val counts = remember { DayCounts(context) }
    val remaining = counts.remaining
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val readerColors = com.atharchive.feature.reader.rememberReaderColors(readerSettings)
    var advancedAt by remember { mutableStateOf(0L) }
    val listState = rememberLazyListState()
    val reselectScope = androidx.compose.runtime.rememberCoroutineScope()

    val entries = when {
        bab != null -> state.of(bab.id)
        selectedTab == AdhkarTab.Saved -> state.saved
        else -> state.of(selectedTab.key)
    }
    // A search looks in every tab (not just the open one) and ignores tashkeel and letter forms.
    val visible = remember(entries, query, state) {
        val term = com.atharchive.core.ArNormalize.normalize(query.trim())
        if (term.isBlank()) entries
        else (if (bab != null) entries else state.entries.values.flatten().distinctBy { it.id })
            .filter { normKey(it.text).contains(term) }
    }

    fun left(dhikr: DhikrUi) = remaining[dhikr.id] ?: dhikr.repeat
    val done = entries.count { left(it) == 0 }
    // المفضّلة is a shelf, not a session: counting through it would be counting the same
    // dhikr twice, so it shows no progress bar.
    val counting = bab != null || selectedTab != AdhkarTab.Saved

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_adhkar")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = bab?.label ?: "الأذكار",
                onSettings = onSettings,
                showAppIcon = true,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
                actionIcon = if (bab == null && state.babs.size > 2) AtharIcons.Sidebar else null,
                actionLabel = "أبواب الأذكار",
                onAction = if (bab == null && state.babs.size > 2) onOpenBabs else null,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("adhkar_screen"),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                if (bab == null) {
                    item(key = "adhkar-headline") {
                        Text(
                            text = state.headline,
                            modifier = Modifier.padding(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                bottom = 6.dp,
                            ),
                            color = AtharTheme.colors.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                item(key = "adhkar-search") {
                    AtharSearchField(
                        query = query,
                        placeholder = "ابحث في الأذكار…",
                        onQueryChange = { query = it },
                        tagPrefix = "adhkar",
                        modifier = Modifier.padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = if (bab == null) 0.dp else 4.dp,
                        ),
                    )
                }
                if (bab == null) {
                    item(key = "adhkar-tabs") {
                        AtharTabRow(
                            tabs = AdhkarTab.tabs,
                            selectedKey = selectedTab.key,
                            onSelect = { selectedTab = AdhkarTab.fromKey(it) },
                            tagPrefix = "adhkar",
                            onReselect = { reselectScope.launch { listState.animateScrollToItem(0) } },
                            modifier = Modifier.padding(horizontal = horizontalPadding),
                        )
                    }
                }
                if (counting && entries.isNotEmpty()) {
                    item(key = "adhkar-progress") {
                        AdhkarProgress(
                            done = done,
                            total = entries.size,
                            horizontalPadding = horizontalPadding,
                            onReset = { counts.reset(entries.map { it.id }) },
                        )
                    }
                }
                item(key = "adhkar-divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = 10.dp,
                        ),
                        thickness = 0.7.dp,
                        color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                    )
                }

                if (visible.isEmpty()) {
                    item(key = "adhkar-empty") {
                        val (title, body) = when {
                            query.isNotBlank() ->
                                "لا توجد نتائج" to "جرّب كلمة أقصر من نص الذكر."
                            selectedTab == AdhkarTab.Saved && bab == null ->
                                "لا أذكار في المفضّلة" to "احفظ ذكرًا ليظهر هنا."
                            else ->
                                "لا أذكار في هذا الباب" to "سيأتي محتوى هذا الباب مع طبقة البيانات."
                        }
                        AtharEmptyState(
                            title = title,
                            body = body,
                            tag = "adhkar_empty_state",
                            horizontalPadding = horizontalPadding,
                        )
                    }
                } else {
                    // Items above the first card, to scroll to the next one when a dhikr is finished.
                    val above = (if (bab == null) 2 else 0) + 1 + (if (counting && entries.isNotEmpty()) 1 else 0) + 1
                    items(visible, key = { it.id }) { dhikr ->
                        DhikrCard(
                            dhikr = dhikr,
                            remaining = left(dhikr),
                            horizontalPadding = horizontalPadding,
                            settings = readerSettings,
                            readerColors = readerColors,
                            onCount = {
                                val now = left(dhikr)
                                // A tap landing just as the list moves on belongs to the dhikr that left, not the one arriving.
                                if (now > 0 && android.os.SystemClock.uptimeMillis() - advancedAt > 450) {
                                    counts.set(dhikr.id, now - 1)
                                    haptics.performHapticFeedback(
                                        if (now == 1) androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm
                                        else androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                                    )
                                    // Done: bring the next one still to say up under the bar.
                                    if (now == 1) visible.drop(visible.indexOf(dhikr) + 1).firstOrNull { left(it) > 0 }?.let { next ->
                                        advancedAt = android.os.SystemClock.uptimeMillis()
                                        reselectScope.launch { listState.animateScrollToItem(above + visible.indexOf(next)) }
                                    }
                                }
                            },
                            onCopy = { onCopy(dhikr) },
                            onSave = { onSave(dhikr) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdhkarProgress(
    done: Int,
    total: Int,
    horizontalPadding: Dp,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (done == total) "تمّت ✓" else "${arabicDigits(done)} من ${arabicDigits(total)}",
            color = if (done == total) AtharTheme.colors.success else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("adhkar_progress_label"),
        )
        AtharProgressBar(
            progress = if (total == 0) 0f else done.toFloat() / total,
            modifier = Modifier
                .weight(1f)
                .testTag("adhkar_progress"),
        )
        // Only offered once there is something to undo — the باب is meant to be walked
        // again tomorrow, not reset by accident today.
        if (done > 0) {
            Text(
                text = "ابدأ من جديد",
                modifier = Modifier
                    .clip(AtharCorners.Control)
                    .clickable(role = Role.Button, onClick = onReset)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("adhkar_reset"),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * One dhikr, set like the books (the reader's font, size, ﴿﴾ and «» colours) across the
 * whole card. The card is the counter: a tap anywhere spends one repetition. Once said it
 * folds to a single line with a check; a tap opens it again to read.
 */
@Composable
private fun DhikrCard(
    dhikr: DhikrUi,
    remaining: Int,
    horizontalPadding: Dp,
    settings: com.atharchive.feature.reader.ReaderSettings,
    readerColors: com.atharchive.feature.reader.ReaderColors,
    onCount: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    val finished = remaining == 0
    var reopened by remember(dhikr.id) { mutableStateOf(false) }
    var showSource by remember(dhikr.id) { mutableStateOf(false) }
    val folded = finished && !reopened
    val text = remember(dhikr.text, readerColors) { marked(dhikr.text, readerColors) }
    com.atharchive.ui.components.AtharRowItem {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding - 10.dp, end = horizontalPadding - 10.dp)
            .clip(AtharCorners.Card)
            .clickable(
                role = Role.Button,
                onClickLabel = if (finished) "عرض الذكر" else "عدّ مرة",
                onClick = { if (finished) reopened = !reopened else onCount() },
            )
            .semantics {
                contentDescription = if (finished) "اكتمل الذكر" else "بقي ${arabicDigits(remaining)} من ${arabicDigits(dhikr.repeat)}"
            }
            .animateContentSize()
            .padding(horizontal = 14.dp, vertical = if (folded) 10.dp else 14.dp)
            .testTag("dhikr_${dhikr.id}"),
    ) {
        if (folded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AtharIcons.Check, contentDescription = null, tint = AtharTheme.colors.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = dhikr.text,
                    color = AtharTheme.colors.secondaryText,
                    fontFamily = com.atharchive.ui.theme.LocalReaderFont.current,
                    fontSize = AtharTextSize.Ui,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            return@Column
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (finished) 0.55f else 1f),
            fontFamily = com.atharchive.ui.theme.LocalReaderFont.current,
            fontSize = (settings.fontSize + 1).sp,
            lineHeight = ((settings.fontSize + 1) * settings.spacing.multiplier).sp,
        )
        if (showSource && dhikr.source != null) Text(
            text = dhikr.source,
            modifier = Modifier.padding(top = 10.dp),
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 20.sp,
        )
        // A quiet foot: what you do to it on one side, the count on the other.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp).testTag("dhikr_copy_${dhikr.id}")) {
                Icon(AtharIcons.Copy, contentDescription = "نسخ الذكر", tint = AtharTheme.colors.secondaryText, modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onSave, modifier = Modifier.size(36.dp).testTag("dhikr_save_${dhikr.id}")) {
                Icon(
                    if (dhikr.saved) AtharIcons.BookmarkFilled else AtharIcons.Bookmark,
                    contentDescription = if (dhikr.saved) "إزالة من المفضّلة" else "حفظ في المفضّلة",
                    tint = if (dhikr.saved) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
                    modifier = Modifier.size(17.dp),
                )
            }
            if (dhikr.source != null) Text(
                text = if (showSource) "إخفاء التخريج" else "التخريج",
                modifier = Modifier.clip(AtharCorners.Control).clickable(role = Role.Button) { showSource = !showSource }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            CountRing(remaining = remaining, repeat = dhikr.repeat)
        }
    }
    }
}

/** How many are left, inside a ring that fills as they are said; a check when none are. */
@Composable
private fun CountRing(remaining: Int, repeat: Int) {
    val said by androidx.compose.animation.core.animateFloatAsState(
        if (repeat == 0) 1f else (repeat - remaining).toFloat() / repeat, label = "dhikr_ring",
    )
    val accent = AtharTheme.colors.accent
    val track = AtharTheme.colors.divider
    val finished = remaining == 0
    Box(
        Modifier.size(46.dp).drawBehind {
            val stroke = 3.dp.toPx()
            drawCircle(track, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke), radius = size.minDimension / 2 - stroke)
            drawArc(if (finished) Color(0xFF5E8B4F) else accent, -90f, 360f * said, false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke, stroke),
                size = androidx.compose.ui.geometry.Size(size.width - 2 * stroke, size.height - 2 * stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        },
        contentAlignment = Alignment.Center,
    ) {
        if (finished) Icon(AtharIcons.Check, contentDescription = null, tint = AtharTheme.colors.success, modifier = Modifier.size(18.dp))
        else Text(arabicDigits(remaining), color = accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** ﴿…﴾ in the Quran colour and «…» in the hadith colour, as in the reader. */
private fun marked(text: String, colors: com.atharchive.feature.reader.ReaderColors) = androidx.compose.ui.text.buildAnnotatedString {
    append(text)
    for ((open, close, color) in listOf(Triple('\uFD3F', '\uFD3E', colors.ayah), Triple('\u00AB', '\u00BB', colors.quote))) {
        var from = 0
        while (true) {
            val a = text.indexOf(open, from).takeIf { it >= 0 } ?: break
            val b = text.indexOf(close, a + 1).takeIf { it >= 0 } ?: break
            addStyle(androidx.compose.ui.text.SpanStyle(color = color), a, b + 1)
            from = b + 1
        }
    }
}

/** Today's counts, kept on the phone: id → how many are left. Yesterday's are dropped on first read. */
private class DayCounts(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("adhkar_counts", android.content.Context.MODE_PRIVATE)
    private val today = java.time.LocalDate.now().toString()
    val remaining = mutableStateMapOf<String, Int>().apply {
        if (prefs.getString("day", null) == today) prefs.all.forEach { (k, v) -> if (k != "day" && v is Int) put(k, v) }
        else prefs.edit().clear().putString("day", today).apply()
    }
    fun set(id: String, left: Int) { remaining[id] = left; prefs.edit().putString("day", today).putInt(id, left).apply() }
    fun reset(ids: List<String>) { ids.forEach(remaining::remove); prefs.edit().apply { ids.forEach(::remove) }.apply() }
}
