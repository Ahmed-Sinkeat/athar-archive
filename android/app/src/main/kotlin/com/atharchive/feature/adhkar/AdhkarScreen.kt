package com.atharchive.feature.adhkar

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AdhkarTab.Morning) }
    var query by rememberSaveable { mutableStateOf("") }
    // Counts survive rotation. A dhikr you are 60 repetitions into is not something to
    // lose because the phone turned.
    val remaining = rememberSaveable(saver = RemainingSaver) { mutableStateMapOf<String, Int>() }
    val listState = rememberLazyListState()

    val entries = when {
        bab != null -> state.of(bab.id)
        selectedTab == AdhkarTab.Saved -> state.saved
        else -> state.of(selectedTab.key)
    }
    val visible = remember(entries, query) {
        val term = query.trim()
        if (term.isBlank()) entries else entries.filter { it.text.contains(term) }
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
                onLogo = onLogo,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
                actionIcon = if (bab == null) AtharIcons.Sidebar else null,
                actionLabel = "أبواب الأذكار",
                onAction = if (bab == null) onOpenBabs else null,
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
                            onReset = { entries.forEach { remaining.remove(it.id) } },
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
                    items(visible, key = { it.id }) { dhikr ->
                        DhikrCard(
                            dhikr = dhikr,
                            remaining = left(dhikr),
                            horizontalPadding = horizontalPadding,
                            onCount = {
                                val now = left(dhikr)
                                if (now > 0) remaining[dhikr.id] = now - 1
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
            text = "${arabicDigits(done)} من ${arabicDigits(total)}",
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.labelSmall,
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
                text = "إعادة",
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
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
 * The dhikr, its التخريج, and a narrow rail carrying the two things you do to it.
 *
 * Tapping anywhere on the card spends one repetition — the counter is the point of the
 * screen, so it gets the whole surface rather than a small button.
 */
@Composable
private fun DhikrCard(
    dhikr: DhikrUi,
    remaining: Int,
    horizontalPadding: Dp,
    onCount: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    val finished = remaining == 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = !finished,
                role = Role.Button,
                onClickLabel = "عدّ مرة",
                onClick = onCount,
            )
            .semantics {
                contentDescription = if (finished) {
                    "اكتمل الذكر"
                } else {
                    "بقي ${arabicDigits(remaining)} من ${arabicDigits(dhikr.repeat)}"
                }
            }
            .testTag("dhikr_${dhikr.id}"),
        shape = RoundedCornerShape(12.dp),
        color = AtharTheme.colors.raisedSurface,
        border = BorderStroke(1.dp, AtharTheme.colors.divider.copy(alpha = 0.4f)),
    ) {
        Row(modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = dhikr.text,
                    color = MaterialTheme.colorScheme.onSurface
                        .copy(alpha = if (finished) 0.45f else 1f),
                    fontFamily = AtharEditorialFontFamily,
                    fontSize = 17.sp,
                    lineHeight = 32.sp,
                )
                if (dhikr.source != null) {
                    Text(
                        text = dhikr.source,
                        modifier = Modifier.padding(top = 8.dp),
                        color = AtharTheme.colors.secondaryText
                            .copy(alpha = if (finished) 0.5f else 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("dhikr_copy_${dhikr.id}"),
                ) {
                    Icon(
                        imageVector = AtharIcons.Copy,
                        contentDescription = "نسخ الذكر",
                        tint = AtharTheme.colors.secondaryText,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                CountBadge(remaining = remaining, repeat = dhikr.repeat)
                Spacer(Modifier.height(4.dp))
                IconButton(
                    onClick = onSave,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("dhikr_save_${dhikr.id}"),
                ) {
                    Icon(
                        imageVector = if (dhikr.saved) {
                            AtharIcons.BookmarkFilled
                        } else {
                            AtharIcons.Bookmark
                        },
                        contentDescription = if (dhikr.saved) "إزالة من المفضّلة" else "حفظ في المفضّلة",
                        tint = if (dhikr.saved) {
                            AtharTheme.colors.accent
                        } else {
                            AtharTheme.colors.secondaryText.copy(alpha = 0.75f)
                        },
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

/** How many are left. A check, not a zero, when there are none. */
@Composable
private fun CountBadge(remaining: Int, repeat: Int) {
    val finished = remaining == 0
    val started = remaining < repeat
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    finished -> AtharTheme.colors.success.copy(alpha = 0.13f)
                    started -> AtharTheme.colors.accentSurface
                    else -> AtharTheme.colors.pressedSurface
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (finished) {
            Icon(
                imageVector = AtharIcons.Check,
                contentDescription = null,
                tint = AtharTheme.colors.success,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(
                text = arabicDigits(remaining),
                color = if (started) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** id=remaining pairs; dhikr ids never contain '='. */
private val RemainingSaver = listSaver<SnapshotStateMap<String, Int>, String>(
    save = { map -> map.entries.map { "${it.key}=${it.value}" } },
    restore = { saved ->
        mutableStateMapOf<String, Int>().apply {
            saved.forEach { put(it.substringBefore('='), it.substringAfter('=').toInt()) }
        }
    },
)
