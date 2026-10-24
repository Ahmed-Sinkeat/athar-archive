package com.atharchive.feature.downloads

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.components.AtharMenu
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import com.atharchive.data.byteSize
import com.atharchive.ui.components.arabicDigits
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharProgressBar
import com.atharchive.ui.components.AtharSectionHeading
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharTheme

/** The overview: storage, then one row per type with its progress. Single items live in [DownloadsListScreen]. */
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onBack: () -> Unit,
    onBudgetChange: (CacheBudget) -> Unit,
    onClearCache: () -> Unit,
    onDownloadAll: (BulkRowUi) -> Unit,
    onPauseAll: (BulkRowUi) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DownloadsFrame("التنزيلات", onBack, modifier) { horizontalPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("downloads_screen"),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "storage") {
                StorageSummary(
                    storage = state.storage,
                    horizontalPadding = horizontalPadding,
                    onBudgetChange = onBudgetChange,
                    onClearCache = onClearCache,
                )
            }
            item(key = "manage") {
                ManageRow(state.transfers.size, state.downloaded.size, horizontalPadding, onManage)
            }
            if (state.bulk.isNotEmpty()) {
                item(key = "bulk-heading") {
                    AtharSectionHeading("تنزيل الكل", horizontalPadding, top = 22.dp)
                }
                itemsIndexed(state.bulk, key = { _, row -> "bulk-${row.label}" }) { index, row ->
                    BulkRow(
                        row, horizontalPadding,
                        onDownload = { onDownloadAll(row) },
                        onPause = { onPauseAll(row) },
                    )
                    if (index != state.bulk.lastIndex) RowDivider(horizontalPadding)
                }
            }
        }
    }
}

private val KindFilters = listOf("الكل", "كتاب", "قصيدة", "مقال", "مسألة", "تسجيل")

/** Every single item, split جارٍ / المحمّلة, with a kind filter. */
@Composable
fun DownloadsListScreen(
    state: DownloadsUiState,
    onBack: () -> Unit,
    onPauseResume: (DownloadUi) -> Unit,
    onCancel: (DownloadUi) -> Unit,
    onRemove: (DownloadUi) -> Unit,
    onRemoveMany: (List<DownloadUi>) -> Unit,
    modifier: Modifier = Modifier,
    targetId: String? = null,
) {
    val listState = rememberLazyListState()
    var tab by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(if (state.downloaded.any { it.id == targetId } || (targetId == null && state.transfers.isEmpty())) "done" else "running")
    }
    var kind by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(KindFilters.first()) }
    // Long-press starts selecting; emptying the selection ends it.
    var selected by remember { mutableStateOf(emptySet<String>()) }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    val source = if (tab == "running") state.transfers else state.downloaded
    val items = remember(source, kind) {
        // Book rows carry their own kind (كتاب، متن…); anything that is not one of the others is a book.
        val others = KindFilters.drop(2)
        when (kind) {
            KindFilters[0] -> source
            KindFilters[1] -> source.filter { it.kindLabel !in others }
            else -> source.filter { it.kindLabel == kind }
        }
    }
    var focused by remember(targetId) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(targetId, items) {
        if (focused || targetId == null) return@LaunchedEffect
        val index = items.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: return@LaunchedEffect
        listState.scrollToItem(index + 1)
        focused = true
    }

    DownloadsFrame("إدارة التنزيلات", onBack, modifier) { horizontalPadding ->
        Column(Modifier.fillMaxSize()) {
            com.atharchive.ui.components.AtharTabRow(
                tabs = listOf(
                    com.atharchive.ui.components.AtharTab("running", "جارٍ (${arabicDigits(state.transfers.size)})"),
                    com.atharchive.ui.components.AtharTab("done", "المحمّلة (${arabicDigits(state.downloaded.size)})"),
                ),
                selectedKey = tab,
                onSelect = { tab = it; selected = emptySet() },
                tagPrefix = "downloads_tab",
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("downloads_list"),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item(key = "filter") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding - 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AtharMenuButton(
                            label = KindFilters.first(),
                            options = KindFilters,
                            selected = kind,
                            defaultOption = KindFilters.first(),
                            onSelect = { kind = it; selected = emptySet() },
                            tagPrefix = "downloads_kind",
                        )
                        Spacer(Modifier.weight(1f))
                        if (tab == "done" && selected.isNotEmpty()) {
                            SelectionBar(
                                chosen = items.filter { it.id in selected },
                                allSelected = items.all { it.id in selected },
                                onSelectAll = { selected = items.mapTo(hashSetOf()) { it.id } },
                                onClear = { selected = emptySet() },
                                onDelete = { onRemoveMany(it); selected = emptySet() },
                            )
                        }
                        // The kind filter narrows it, so «a bunch» is one type and «all» is الكل.
                        if (tab == "running" && items.isNotEmpty()) CancelAll(items, onRemoveMany)
                    }
                }
                if (items.isEmpty()) {
                    item(key = "downloads-empty") {
                        AtharEmptyState(
                            title = if (tab == "running") "لا تنزيلات جارية" else "لا توجد تنزيلات",
                            body = "نزّل كتابًا أو تسجيلًا ليبقى معك دون اتصال.",
                            tag = "downloads_empty_state",
                            horizontalPadding = horizontalPadding,
                        )
                    }
                }
                itemsIndexed(items, key = { _, d -> d.id }) { index, item ->
                    if (tab == "running") {
                        TransferRow(
                            item = item,
                            horizontalPadding = horizontalPadding,
                            onPauseResume = { onPauseResume(item) },
                            onCancel = { onCancel(item) },
                        )
                    } else {
                        DownloadedRow(
                            item = item,
                            horizontalPadding = horizontalPadding,
                            onRemove = { onRemove(item) },
                            selected = if (selected.isEmpty()) null else item.id in selected,
                            onToggle = { selected = if (item.id in selected) selected - item.id else selected + item.id },
                        )
                    }
                    if (index != items.lastIndex) RowDivider(horizontalPadding)
                }
            }
        }
    }
}

@Composable
private fun DownloadsFrame(title: String, onBack: () -> Unit, modifier: Modifier, content: @Composable (Dp) -> Unit) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_downloads")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp
        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = title,
                onSettings = {},
                showAppIcon = true,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
            )
            Box(Modifier.weight(1f)) { content(horizontalPadding) }
        }
    }
}

@Composable
private fun ManageRow(running: Int, done: Int, horizontalPadding: Dp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 13.dp)
            .testTag("downloads_manage"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("إدارة التنزيلات", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    if (running > 0) "${arabicDigits(running)} جارٍ" else null,
                    "${arabicDigits(done)} محمّلة",
                ).joinToString(" · "),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Icon(AtharIcons.Forward, null, tint = AtharTheme.colors.secondaryText, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun RowDivider(horizontalPadding: Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        thickness = 0.7.dp,
        color = AtharTheme.colors.divider.copy(alpha = 0.42f),
    )
}

/**
 * What the reader can actually change: the ceiling on the cache, and how close it is.
 *
 * Pins are counted rather than weighed, because their size is not the point — nothing
 * evicts them, so they never compete with the budget the bar is about.
 */
@Composable
private fun StorageSummary(
    storage: StorageUi,
    horizontalPadding: Dp,
    onBudgetChange: (CacheBudget) -> Unit,
    onClearCache: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            start = horizontalPadding,
            end = horizontalPadding,
            top = 10.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "المؤقّت",
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = if (storage.unlimited) {
                        storage.cacheLabel
                    } else {
                        "${storage.cacheLabel} من ${storage.budget.label}"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("downloads_cache_label"),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "مثبّت",
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = storage.pinnedCountLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        // No ceiling, no bar: a progress bar against infinity would be decoration.
        if (!storage.unlimited) {
            AtharProgressBar(
                progress = storage.cacheFraction,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("downloads_cache_bar"),
                height = 6.dp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "حد المؤقّت",
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(2.dp))
            AtharMenuButton(
                label = CacheBudget.Gb2.label,
                options = CacheBudget.entries.map { it.label },
                selected = storage.budget.label,
                defaultOption = CacheBudget.Gb2.label,
                onSelect = { label ->
                    onBudgetChange(CacheBudget.entries.first { it.label == label })
                },
                tagPrefix = "downloads_budget",
            )
            Spacer(Modifier.weight(1f))
            // Not burgundy: the accent means active or selected everywhere else, and
            // this is the one destructive control on the screen.
            Text(
                text = "مسح المؤقّت",
                modifier = Modifier
                    .clip(AtharCorners.Control)
                    .clickable(role = Role.Button, onClick = onClearCache)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .testTag("downloads_clear_cache"),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Pins outlive the cache; say so once, here, rather than in every menu.
        Text(
            text = "ما تفتحه دون تنزيل يُحفظ مؤقتاً ليفتح بسرعة، ويُحذف تلقائياً عند بلوغ الحد. تنزيلاتك لا تُحذف",
            modifier = Modifier.padding(top = 6.dp),
            color = AtharTheme.colors.secondaryText.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun TransferRow(
    item: DownloadUi,
    horizontalPadding: Dp,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val transfer = item.transfer ?: return
    val progress = when (transfer) {
        is TransferUi.Running -> transfer.progress
        is TransferUi.Paused -> transfer.progress
        is TransferUi.Failed -> transfer.progress
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 11.dp)
            .testTag("transfer_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val factColor = AtharTheme.colors.secondaryText
                Text(item.kindLabel, color = factColor, style = MaterialTheme.typography.labelSmall)
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = item.sizeLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = when (transfer) {
                        is TransferUi.Running -> transfer.label
                        is TransferUi.Paused -> "${transfer.label} · متوقّف"
                        // Resume is exact-prefix, so the verified bytes are not lost.
                        is TransferUi.Failed -> "${transfer.reason} · سيُستأنف"
                    },
                    color = if (transfer is TransferUi.Failed) {
                        AtharTheme.colors.secondaryText
                    } else {
                        AtharTheme.colors.accent
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            AtharProgressBar(
                progress = progress,
                modifier = Modifier.padding(top = 7.dp),
                height = 3.dp,
            )
        }
        IconButton(
            onClick = onPauseResume,
            modifier = Modifier
                .size(48.dp)
                .testTag("transfer_toggle_${item.id}"),
        ) {
            Icon(
                imageVector = if (transfer is TransferUi.Running) {
                    AtharIcons.Pause
                } else {
                    AtharIcons.Play
                },
                contentDescription = if (transfer is TransferUi.Running) {
                    "إيقاف تنزيل ${item.title}"
                } else {
                    "استئناف تنزيل ${item.title}"
                },
                tint = AtharTheme.colors.secondaryText,
                modifier = Modifier.size(17.dp),
            )
        }
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(48.dp)
                .testTag("transfer_cancel_${item.id}"),
        ) {
            Icon(
                imageVector = AtharIcons.Close,
                contentDescription = "إلغاء تنزيل ${item.title}",
                tint = AtharTheme.colors.secondaryText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** A whole type in one tap; the popup is the confirmation, since it may be gigabytes. Progress shows under it. */
@Composable
private fun BulkRow(row: BulkRowUi, horizontalPadding: Dp, onDownload: () -> Unit, onPause: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    // Never started and stopped part-way are the same to the reader: both are «المتبقي».
    val remaining = row.idle + row.stopped
    // Only the row the reader started shows motion; the rows its items also belong to just count.
    val running = row.tracked && row.running.isNotEmpty()
    val paused = row.tracked && !running && row.stopped.isNotEmpty()
    val factColor = AtharTheme.colors.secondaryText
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, enabled = !row.complete) { menuOpen = true }
                .padding(horizontal = horizontalPadding, vertical = 12.dp)
                .testTag("bulk_${row.label}"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = row.label,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Icon(
                    imageVector = when {
                        row.complete -> AtharIcons.Check
                        running -> AtharIcons.Pause
                        else -> AtharIcons.Download
                    },
                    contentDescription = null,
                    tint = if (row.complete) AtharTheme.colors.success else factColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = "${arabicDigits(row.done)} من ${arabicDigits(row.items.size)} · ${byteSize(row.doneBytes)} من ${byteSize(row.bytes)}" +
                    when {
                        row.complete -> " · مكتمل"
                        running -> " · ${arabicDigits((row.progress * 100).toInt())}٪"
                        paused -> " · متوقّف"
                        else -> ""
                    },
                modifier = Modifier.padding(top = 2.dp),
                color = if (running) AtharTheme.colors.accent else factColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (running) FontWeight.SemiBold else null,
            )
            if (row.tracked && row.busy) {
                AtharProgressBar(progress = row.progress, modifier = Modifier.padding(top = 7.dp), height = 3.dp)
            }
        }
        AtharMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, width = 240.dp) {
            if (row.running.isNotEmpty()) {
                AtharMenuItem("إيقاف التنزيل", icon = AtharIcons.Pause,
                    onClick = { onPause(); menuOpen = false },
                    modifier = Modifier.testTag("bulk_pause_${row.label}"))
            }
            if (remaining.isNotEmpty()) {
                AtharMenuItem(
                    if (row.done > 0 || row.busy) "تنزيل المتبقي" else "تنزيل الكل",
                    subtitle = "${arabicDigits(remaining.size)} عنصر · ${byteSize(remaining.sumOf { it.bytes - it.partialBytes })}",
                    icon = AtharIcons.Download,
                    onClick = { onDownload(); menuOpen = false },
                    modifier = Modifier.testTag("bulk_confirm_${row.label}"),
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    chosen: List<DownloadUi>,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: (List<DownloadUi>) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BarAction(if (allSelected) "إلغاء التحديد" else "تحديد الكل", if (allSelected) onClear else onSelectAll, "downloads_select_all")
        Box {
            BarAction("حذف (${arabicDigits(chosen.size)})", { menuOpen = true }, "downloads_delete_selected")
            AtharMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, width = 240.dp) {
                AtharMenuItem(
                    "حذف ${arabicDigits(chosen.size)} · يحرّر ${byteSize(chosen.sumOf { it.bytes })}",
                    icon = AtharIcons.Close,
                    destructive = true,
                    onClick = { onDelete(chosen); menuOpen = false },
                    modifier = Modifier.testTag("downloads_delete_confirm"),
                )
            }
        }
    }
}

@Composable
private fun CancelAll(items: List<DownloadUi>, onCancel: (List<DownloadUi>) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        BarAction("إلغاء الكل (${arabicDigits(items.size)})", { menuOpen = true }, "downloads_cancel_all")
        AtharMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, width = 240.dp) {
            AtharMenuItem(
                "إلغاء ${arabicDigits(items.size)} تنزيلات",
                icon = AtharIcons.Close,
                destructive = true,
                onClick = { onCancel(items); menuOpen = false },
                modifier = Modifier.testTag("downloads_cancel_confirm"),
            )
        }
    }
}

@Composable
private fun BarAction(label: String, onClick: () -> Unit, tag: String) {
    Text(
        text = label,
        modifier = Modifier
            .clip(AtharCorners.Control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag(tag),
        color = AtharTheme.colors.secondaryText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
}

/** [selected] is null outside selection mode; long-press enters it, tap then toggles. */
@Composable
private fun DownloadedRow(
    item: DownloadUi,
    horizontalPadding: Dp,
    onRemove: () -> Unit,
    selected: Boolean?,
    onToggle: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selected != null) onToggle() },
                onLongClick = { if (selected == null) onToggle() },
                onLongClickLabel = "تحديد",
            )
            .padding(horizontal = horizontalPadding, vertical = 11.dp)
            .testTag("download_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected != null) {
            Checkbox(
                checked = selected,
                // The whole row toggles; a second touch target would only double-fire.
                onCheckedChange = null,
                modifier = Modifier.testTag("download_check_${item.id}"),
            )
        } else Icon(
            imageVector = AtharIcons.Check,
            contentDescription = null,
            tint = AtharTheme.colors.success,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val factColor = AtharTheme.colors.secondaryText
                Text(item.kindLabel, color = factColor, style = MaterialTheme.typography.labelSmall)
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = item.sizeLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (selected == null) Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("download_more_${item.id}"),
            ) {
                Icon(
                    imageVector = AtharIcons.More,
                    contentDescription = "خيارات ${item.title}",
                    tint = AtharTheme.colors.secondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            AtharMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, width = 200.dp) {
                AtharMenuItem("إزالة التنزيل", icon = AtharIcons.Close, destructive = true,
                    onClick = { onRemove(); menuOpen = false },
                    modifier = Modifier.testTag("download_remove_${item.id}"))
            }
        }
    }
}
