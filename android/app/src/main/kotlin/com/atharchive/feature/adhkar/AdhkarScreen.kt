package com.atharchive.feature.adhkar

import com.atharchive.ui.components.swipeTabs
import com.atharchive.ui.components.AtharFastScroller
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clipToBounds
import com.atharchive.ui.components.rememberAtharCollapsingHeader
import com.atharchive.ui.components.ShowOnChange
import com.atharchive.ui.components.atharBackdrop
import com.atharchive.ui.components.AtharFloatingHeader
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
    onCopy: (DhikrUi) -> Unit,
    onSave: (DhikrUi) -> Unit,
    modifier: Modifier = Modifier,
    /** «+»: a new dhikr of one's own, starting in this list. */
    onAdd: (listId: String) -> Unit = {},
    onEdit: (DhikrUi) -> Unit = {},
    onDeleteList: (listId: String) -> Unit = {},
    bab: AdhkarBabUi? = null,
    onBack: (() -> Unit)? = null,
    /** Daily reminders by list id, minutes after midnight; [onReminder] with null turns one off. */
    reminders: Map<String, Int> = emptyMap(),
    onReminder: (listId: String, label: String, minutes: Int?) -> Unit = { _, _, _ -> },
    /** A list to open (a reminder was tapped); [onOpened] once it is showing. */
    openList: String? = null,
    onOpened: () -> Unit = {},
    /** The reader's font, size and text colours, so a dhikr reads like the books. */
    readerSettings: com.atharchive.feature.reader.ReaderSettings = com.atharchive.feature.reader.ReaderSettings(),
) {
    // المساء from 15:00 until 04:00, near enough to العصر and الفجر without prayer times; real times when the app has them.
    var storedKey by rememberSaveable {
        mutableStateOf(java.time.LocalTime.now().hour.let { if (it >= 15 || it < 4) AdhkarTab.Evening else AdhkarTab.Morning }.key)
    }
    // One's own lists share one tab, «قوائمي», with the open list picked from its first line.
    val ownLists = state.babs.filter { it.id != AdhkarTab.Morning.key && it.id != AdhkarTab.Evening.key }
    var ownKey by rememberSaveable { mutableStateOf<String?>(null) }
    val ownList = ownLists.firstOrNull { it.id == ownKey } ?: ownLists.firstOrNull()
    val tabs = listOf(AdhkarTab.Morning, AdhkarTab.Evening).map { com.atharchive.ui.components.AtharTab(it.key, it.label) } +
        listOfNotNull(com.atharchive.ui.components.AtharTab(MineKey, "قوائمي").takeIf { ownList != null }) +
        com.atharchive.ui.components.AtharTab(AdhkarTab.Saved.key, AdhkarTab.Saved.label)
    // A deleted list's tab falls back to الصباح.
    val selectedKey = storedKey.takeIf { key -> tabs.any { it.key == key } } ?: AdhkarTab.Morning.key
    val mine = bab == null && selectedKey == MineKey
    androidx.compose.runtime.LaunchedEffect(openList) {
        openList ?: return@LaunchedEffect
        if (openList == AdhkarTab.Morning.key || openList == AdhkarTab.Evening.key) storedKey = openList
        else if (ownLists.any { it.id == openList }) { storedKey = MineKey; ownKey = openList }
        onOpened()
    }
    // The list open now, for its reminder (المفضّلة has none).
    val reminderList = bab ?: if (mine) ownList else state.babs.firstOrNull { it.id == selectedKey }
    var reminderAt by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // Counts last the day: closing the app mid-way keeps your place; a new day starts clean.
    val context = androidx.compose.ui.platform.LocalContext.current
    val counts = remember { DayCounts(context) }
    val remaining = counts.remaining
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val readerColors = com.atharchive.feature.reader.rememberReaderColors(readerSettings)
    var advancedAt by remember { mutableStateOf(0L) }
    val listState = rememberLazyListState()
    com.atharchive.feature.reader.VolumeKeyPaging(listState, readerSettings.volumeKeys)
    val reselectScope = androidx.compose.runtime.rememberCoroutineScope()

    val entries = when {
        bab != null -> state.of(bab.id)
        selectedKey == AdhkarTab.Saved.key -> state.saved
        mine -> ownList?.let { state.of(it.id) }.orEmpty()
        else -> state.of(selectedKey)
    }
    val addTo = bab?.id ?: if (mine) ownList?.id ?: AdhkarTab.Morning.key else selectedKey.takeIf { it != AdhkarTab.Saved.key } ?: AdhkarTab.Morning.key
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
    val counting = bab != null || selectedKey != AdhkarTab.Saved.key

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_adhkar")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp

        // The same frame as every section: bar, search and tabs float over the list and slide
        // away as it scrolls; what is adhkar-only (own list, progress) heads the list.
        Box(modifier = Modifier.fillMaxSize().clipToBounds()
            .then(if (bab == null) Modifier.swipeTabs(tabs.map { it.key }, selectedKey) { storedKey = it } else Modifier)) {
            val header = rememberAtharCollapsingHeader()
            header.ShowOnChange(listState, selectedKey, query)
            AtharFloatingHeader(header) {
                Column {
                    AtharTopBar(
                        title = bab?.label ?: "الأذكار",
                        onSettings = onSettings,
                        showAppIcon = true,
                        onBack = onBack,
                        horizontalPadding = horizontalPadding,
                        actionIcon = AtharIcons.PlusCircle,
                        actionLabel = "إضافة ذكر",
                        onAction = { onAdd(addTo) },
                    )
                    AtharSearchField(
                        query = query,
                        resultCount = visible.size,
                        placeholder = "ابحث في الأذكار…",
                        onQueryChange = { query = it },
                        tagPrefix = "adhkar",
                        modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp),
                    )
                }
                Column {
                    if (bab == null) AtharTabRow(
                        tabs = tabs,
                        selectedKey = selectedKey,
                        onSelect = { storedKey = it },
                        tagPrefix = "adhkar",
                        onReselect = { reselectScope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
                        thickness = 0.7.dp,
                        color = AtharTheme.colors.divider.copy(alpha = 0.5f),
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .nestedScroll(header.connection).atharBackdrop(header.haze)
                    .testTag("adhkar_screen"),
                contentPadding = PaddingValues(top = header.height, bottom = 28.dp),
            ) {
                if (mine && ownList != null) {
                    item(key = "adhkar-own-list") {
                        OwnListPicker(ownLists, ownList, horizontalPadding, onPick = { ownKey = it }, onDelete = { onDeleteList(ownList.id); ownKey = null })
                    }
                }
                if (counting && entries.isNotEmpty()) {
                    item(key = "adhkar-progress") {
                        AdhkarProgress(
                            done = done,
                            total = entries.size,
                            horizontalPadding = horizontalPadding,
                            onReset = { counts.reset(entries.map { it.id }) },
                            reminder = reminderList?.let { reminders[it.id] },
                            onBell = if (reminderList == null) null else ({ reminderAt = it }),
                        )
                    }
                }

                if (visible.isEmpty()) {
                    item(key = "adhkar-empty") {
                        val (title, body) = when {
                            query.isNotBlank() ->
                                "لا توجد نتائج" to "جرّب كلمة أقصر من نص الذكر."
                            selectedKey == AdhkarTab.Saved.key && bab == null ->
                                "لا أذكار في المفضّلة" to "احفظ ذكرًا ليظهر هنا."
                            mine -> "لا أذكار في هذه القائمة" to "اضغط + لتضيف ذكرًا، أو حدّد نصًا في كتاب واختر «إلى الأذكار»."
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
                    val above = (if (mine && ownList != null) 1 else 0) + (if (counting && entries.isNotEmpty()) 1 else 0)
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
                            onEdit = if (dhikr.custom) ({ onEdit(dhikr) }) else null,
                        )
                    }
                }
            }
            AtharFastScroller(listState, top = header.height)
        }
        val list = reminderList
        val at = reminderAt
        if (list != null && at != null) ReminderPopover(
            label = list.label, minutes = reminders[list.id], anchor = at,
            onSet = { onReminder(list.id, list.label, it); reminderAt = null },
            onDismiss = { reminderAt = null },
        )
    }
}

private const val MineKey = "mine"

/** «HH:MM» in Arabic digits, 24-hour, for minutes after midnight. */
private fun clock(minutes: Int) = arabicDigits("%d:%02d".format(minutes / 60, minutes % 60))

/** The bell's popover: a time for this list's daily reminder, or off. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPopover(label: String, minutes: Int?, anchor: androidx.compose.ui.geometry.Rect, onSet: (Int?) -> Unit, onDismiss: () -> Unit) {
    val default = minutes ?: if (label == AdhkarTab.Evening.label) 17 * 60 else 6 * 60
    val time = androidx.compose.material3.rememberTimePickerState(default / 60, default % 60, is24Hour = true)
    com.atharchive.ui.components.AtharTailPopover(anchor = { anchor }, ground = AtharTheme.colors.raisedSurface, onDismiss = onDismiss, maxWidth = 320.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).testTag("adhkar_reminder")) {
            Text("تذكير يومي بأذكار $label", fontFamily = AtharEditorialFontFamily, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                minutes?.let { "كل يوم الساعة ${clock(it)}" } ?: "لا تذكير الآن",
                Modifier.padding(top = 2.dp, bottom = 10.dp), color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.bodySmall,
            )
            // Hours then minutes, left to right, as a clock is read.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                androidx.compose.material3.TimeInput(time, Modifier.align(Alignment.CenterHorizontally))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                androidx.compose.material3.TextButton(onClick = { onSet(null) }, enabled = minutes != null, modifier = Modifier.testTag("adhkar_reminder_off")) { Text("إيقاف") }
                androidx.compose.material3.TextButton(onClick = { onSet(time.hour * 60 + time.minute) }, modifier = Modifier.testTag("adhkar_reminder_save")) { Text("حفظ") }
            }
        }
    }
}

/** «قوائمي»'s first line: the open list, a tap to switch, and its removal (asked twice: it can take adhkar with it). */
@Composable
private fun OwnListPicker(lists: List<AdhkarBabUi>, current: AdhkarBabUi, horizontalPadding: Dp, onPick: (String) -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 10.dp)) {
        Row(
            Modifier.clip(AtharCorners.Control).clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 6.dp, vertical = 4.dp).testTag("adhkar_own_list"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.label, color = AtharTheme.colors.accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Icon(AtharIcons.ChevronDown, null, Modifier.padding(start = 4.dp).size(16.dp), tint = AtharTheme.colors.secondaryText)
        }
        com.atharchive.ui.components.AtharMenu(expanded = open, onDismiss = { open = false }, width = 220.dp) {
            lists.forEach { list ->
                com.atharchive.ui.components.AtharMenuItem(list.label, checked = list.id == current.id, onClick = { open = false; onPick(list.id) })
            }
            com.atharchive.ui.components.AtharMenuDivider()
            com.atharchive.ui.components.AtharMenuItem("حذف القائمة…", destructive = true, onClick = { open = false; confirm = true })
        }
        com.atharchive.ui.components.AtharMenu(expanded = confirm, onDismiss = { confirm = false }, width = 260.dp) {
            Text("حذف «${current.label}»؟ تُحذف معها أذكارها التي ليست في قائمة أخرى.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            com.atharchive.ui.components.AtharMenuItem("حذف", destructive = true, modifier = Modifier.testTag("adhkar_confirm_delete_list"), onClick = { confirm = false; onDelete() })
            com.atharchive.ui.components.AtharMenuItem("إلغاء", onClick = { confirm = false })
        }
    }
}

@Composable
private fun AdhkarProgress(
    done: Int,
    total: Int,
    horizontalPadding: Dp,
    onReset: () -> Unit,
    reminder: Int? = null,
    onBell: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
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
        // The daily reminder: its time beside the bell when one is set.
        if (onBell != null) {
            var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            Row(
                Modifier.clip(AtharCorners.Control)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .clickable(role = Role.Button, onClickLabel = "تذكير يومي") { onBell(bounds) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("adhkar_reminder_bell"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AtharIcons.Bell, if (reminder == null) "تذكير يومي" else null, Modifier.size(18.dp),
                    tint = if (reminder == null) AtharTheme.colors.secondaryText else AtharTheme.colors.accent)
                if (reminder != null) Text(clock(reminder), Modifier.padding(start = 4.dp), color = AtharTheme.colors.accent,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
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
    onEdit: (() -> Unit)? = null,
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
            if (onEdit != null) IconButton(onClick = onEdit, modifier = Modifier.size(36.dp).testTag("dhikr_edit_${dhikr.id}")) {
                Icon(AtharIcons.Pen, contentDescription = "تعديل الذكر", tint = AtharTheme.colors.secondaryText, modifier = Modifier.size(17.dp))
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
