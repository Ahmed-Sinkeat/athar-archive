package com.atharchive.feature.downloads

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextDirection
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

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onBack: () -> Unit,
    onBudgetChange: (CacheBudget) -> Unit,
    onClearCache: () -> Unit,
    onPauseResume: (DownloadUi) -> Unit,
    onCancel: (DownloadUi) -> Unit,
    onRemove: (DownloadUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_downloads")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "التنزيلات",
                onSettings = {},
                showAppIcon = true,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("downloads_screen"),
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

                if (state.transfers.isNotEmpty()) {
                    item(key = "transfers-heading") {
                        AtharSectionHeading("جارٍ التنزيل", horizontalPadding, top = 22.dp)
                    }
                    itemsIndexed(state.transfers, key = { _, d -> d.id }) { index, item ->
                        TransferRow(
                            item = item,
                            horizontalPadding = horizontalPadding,
                            onPauseResume = { onPauseResume(item) },
                            onCancel = { onCancel(item) },
                        )
                        if (index != state.transfers.lastIndex) RowDivider(horizontalPadding)
                    }
                }

                item(key = "downloaded-heading") {
                    AtharSectionHeading(
                        text = "المحمّلة",
                        horizontalPadding = horizontalPadding,
                        top = if (state.transfers.isEmpty()) 22.dp else 26.dp,
                    )
                }
                if (state.downloaded.isEmpty()) {
                    item(key = "downloads-empty") {
                        AtharEmptyState(
                            title = "لا توجد تنزيلات",
                            body = "نزّل كتابًا أو تسجيلًا ليبقى معك دون اتصال.",
                            tag = "downloads_empty_state",
                            horizontalPadding = horizontalPadding,
                        )
                    }
                } else {
                    itemsIndexed(state.downloaded, key = { _, d -> d.id }) { index, item ->
                        DownloadedRow(
                            item = item,
                            horizontalPadding = horizontalPadding,
                            onRemove = { onRemove(item) },
                        )
                        if (index != state.downloaded.lastIndex) RowDivider(horizontalPadding)
                    }
                }
            }
        }
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
                    .clip(RoundedCornerShape(6.dp))
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
            text = "مسح المؤقّت لا يحذف تنزيلاتك المثبّتة.",
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
                val factColor = AtharTheme.colors.secondaryText.copy(alpha = 0.72f)
                Text(item.kindLabel, color = factColor, style = MaterialTheme.typography.labelSmall)
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = item.sizeLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall
                        .copy(textDirection = TextDirection.Ltr),
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
                .size(36.dp)
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
                .size(36.dp)
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

@Composable
private fun DownloadedRow(
    item: DownloadUi,
    horizontalPadding: Dp,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 11.dp)
            .testTag("download_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
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
                val factColor = AtharTheme.colors.secondaryText.copy(alpha = 0.72f)
                Text(item.kindLabel, color = factColor, style = MaterialTheme.typography.labelSmall)
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = item.sizeLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall
                        .copy(textDirection = TextDirection.Ltr),
                )
            }
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("download_more_${item.id}"),
            ) {
                Icon(
                    imageVector = AtharIcons.More,
                    contentDescription = "خيارات ${item.title}",
                    tint = AtharTheme.colors.secondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = AtharTheme.colors.canvas,
                shape = RoundedCornerShape(10.dp),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "إزالة التنزيل",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = { onRemove(); menuOpen = false },
                    modifier = Modifier.testTag("download_remove_${item.id}"),
                )
            }
        }
    }
}
