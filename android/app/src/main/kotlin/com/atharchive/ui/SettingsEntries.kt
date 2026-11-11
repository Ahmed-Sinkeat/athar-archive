package com.atharchive.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.flow.first
import com.atharchive.ui.theme.AtharTextSize
import com.atharchive.ui.components.arabicDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.atharchive.feature.downloads.CacheBudget
import com.atharchive.feature.downloads.DownloadsScreen
import com.atharchive.feature.downloads.DownloadsListScreen
import com.atharchive.feature.downloads.StorageUi
import com.atharchive.feature.downloads.DownloadsUiState
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import androidx.compose.ui.text.font.FontWeight
import com.atharchive.ui.settings.AppearanceScreen
import com.atharchive.ui.theme.AtharTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.navigation3.runtime.EntryProviderScope

/** Settings, appearance and downloads. */
internal fun EntryProviderScope<NavKey>.settingsEntries(app: AppNav) = with(app) {
        entry<SettingsRoute> {
            LaunchedEffect(Unit) { contentViewModel.refreshCacheBytes() }
            SettingsScreen(
                cacheBytes = cacheBytes,
                cacheBudgetBytes = cacheBudgetBytes,
                pinnedCount = pinnedCount,
                themeModeLabel = themeMode.label,
                onAppearance = { backStack.add(AppearanceRoute) },
                onDownloads = { backStack.add(DownloadsRoute) },
                onSync = { contentViewModel.sync(manual = true) },
                syncLabel = when (val sync = syncState) {
                    com.atharchive.data.ContentSyncUiState.Checking -> "جارٍ التحديث…"
                    com.atharchive.data.ContentSyncUiState.UpToDate -> "المحتوى محدّث"
                    is com.atharchive.data.ContentSyncUiState.Failed -> "${sync.message}؛ اضغط لإعادة المحاولة"
                    else -> "تحقّق من المحتوى الجديد"
                },
                onExportUserData = { exportUserData.launch("athar-user-data.jsonl") },
                onImportUserData = { importUserData.launch(arrayOf("application/json", "text/plain")) },
                onReceiveBooks = { receiveBooks.launch(arrayOf("application/zip", "application/octet-stream")) },
                onResetSettings = {
                    onResetSettings()
                    com.atharchive.ui.components.resetListState(context)
                    announce("أُعيدت الإعدادات الافتراضية")
                },
                onBack = goBack,
            )
        }
        entry<AppearanceRoute> {
            AppearanceScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                style = style,
                onStyleChange = onStyleChange,
                accent = accent,
                onAccentChange = onAccentChange,
                readerSettings = readerSettings,
                onReaderSettingsChange = onReaderSettingsChange,
                startPlace = startPlace,
                onStartPlaceChange = onStartPlaceChange,
                bar = bar,
                onBarChange = onBarChange,
                onBack = goBack,
            )
        }
        entry<DownloadsRoute> {
            LaunchedEffect(Unit) { contentViewModel.refreshCacheBytes() }
            val downloadItems by contentViewModel.downloads.collectAsStateWithLifecycle()
            val bulkRows by contentViewModel.bulkDownloads.collectAsStateWithLifecycle()
            DownloadsScreen(
                state = DownloadsUiState(
                    transfers = downloadItems.filter { it.transfer != null },
                    downloaded = downloadItems.filter { it.transfer == null },
                    bulk = bulkRows,
                    storage = StorageUi(
                        pinnedCountLabel = com.atharchive.data.arabicCount(pinnedCount, "لا عناصر", "عنصر واحد", "عنصران", "عناصر", "عنصرًا"),
                        cacheLabel = com.atharchive.data.byteSize(cacheBytes),
                        budget = CacheBudget.fromBytes(cacheBudgetBytes),
                        cacheFraction = if (cacheBudgetBytes <= 0 ||
                            cacheBudgetBytes == Long.MAX_VALUE
                        ) {
                            0f
                        } else {
                            (cacheBytes.toFloat() / cacheBudgetBytes).coerceIn(0f, 1f)
                        },
                    ),
                ),
                onBack = goBack,
                onBudgetChange = { contentViewModel.setCacheBudget(it.bytes) },
                onClearCache = {
                    scope.launch {
                        runCatching { contentViewModel.clearCache() }
                            .onSuccess { announce("مُسح المؤقّت وبقيت التنزيلات المثبّتة") }
                            .onFailure { announce("تعذّر مسح المؤقّت") }
                    }
                },
                onDownloadAll = { row, mobileData -> contentViewModel.downloadRemaining(row, mobileData) },
                onPauseAll = { contentViewModel.pauseResumeMany(it.running, pause = true) },
                onManage = { backStack.add(DownloadsListRoute()) },
            )
        }
        entry<DownloadsListRoute> { route ->
            val downloadItems by contentViewModel.downloads.collectAsStateWithLifecycle()
            val state = remember(downloadItems) {
                DownloadsUiState(
                    transfers = downloadItems.filter { it.transfer != null },
                    downloaded = downloadItems.filter { it.transfer == null },
                    storage = StorageUi("", "", CacheBudget.Gb2, 0f),
                )
            }
            DownloadsListScreen(
                targetId = route.targetId,
                state = state,
                onBack = goBack,
                onPauseResume = { contentViewModel.pauseResumeDownload(it.id) },
                onCancel = { contentViewModel.removeDownload(it.id) },
                onRemove = {
                    // A finished download asks first; cancelling a running one loses nothing.
                    if (it.transfer == null) pendingRemoval = DownloadRemoval(it.id, it.title, it.sizeLabel)
                    else contentViewModel.removeDownload(it.id)
                },
                onRemoveMany = { items -> contentViewModel.removeDownloads(items.map { it.id }) },
                onSend = { items -> sendBooks(items.map { it.id }) },
            )
        }
}

@Composable
internal fun SettingsScreen(
    cacheBytes: Long,
    cacheBudgetBytes: Long,
    pinnedCount: Int,
    themeModeLabel: String,
    onAppearance: () -> Unit,
    onDownloads: () -> Unit,
    onSync: () -> Unit,
    syncLabel: String,
    onExportUserData: () -> Unit,
    onImportUserData: () -> Unit,
    onReceiveBooks: () -> Unit,
    onResetSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AtharTheme.colors.canvas)
            .testTag("section_settings"),
    ) {
        AtharTopBar(
            title = "الإعدادات",
            onSettings = {},
            showAppIcon = true,
            onBack = onBack,
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // One row: the page palette follows the app theme now, so appearance and
            // reading are one subject rather than two dead entries.
            SettingsRow(
                AtharIcons.Palette,
                "المظهر والقراءة",
                themeModeLabel,
                onClick = onAppearance,
                tag = "settings_appearance",
            )
            // Content: what is on the phone, how it comes and goes.
            SettingsGroupTitle("المحتوى")
            // Storage lives on its own screen now: the budget, the transfers in flight
            // and what is pinned are one subject, and a sheet could only hold the first.
            SettingsRow(
                AtharIcons.Download,
                "التنزيلات والتخزين",
                "المحفوظ: ${arabicDigits(pinnedCount)} · المؤقّت: ${com.atharchive.data.byteSize(cacheBytes)} · الحد: ${cacheBudgetLabel(cacheBudgetBytes)}",
                onClick = onDownloads,
                tag = "settings_downloads",
            )
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(AtharIcons.Repeat, "تحديث المحتوى", syncLabel, onClick = onSync, tag = "settings_sync_content")
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(
                AtharIcons.Download,
                "استقبال كتب",
                "من ملف أرسله جهاز آخر (مشاركة سريعة أو بلوتوث)، بلا إنترنت",
                onClick = onReceiveBooks,
                tag = "settings_receive_books",
            )
            SettingsGroupTitle("بياناتي")
            // Export and import are one subject: one row, its two ways in a small popup.
            Box {
                var open by remember { mutableStateOf(false) }
                SettingsRow(
                    AtharIcons.Share,
                    "تصدير واستيراد",
                    "المقتطفات ومواضع القراءة والمفضّلة، في ملف",
                    onClick = { open = true },
                    tag = "settings_user_data",
                )
                AtharMenu(expanded = open, onDismiss = { open = false }, width = 260.dp) {
                    AtharMenuItem("تصدير بياناتي", icon = AtharIcons.Share, onClick = { open = false; onExportUserData() }, modifier = Modifier.testTag("settings_export_user_data"))
                    AtharMenuItem("استيراد بياناتي", icon = AtharIcons.Download, subtitle = "يدمج الملف ويحافظ على التعديلات المحلية الأحدث",
                        onClick = { open = false; onImportUserData() }, modifier = Modifier.testTag("settings_import_user_data"))
                }
            }
            SettingsGroupTitle("")
            Box {
                var confirm by remember { mutableStateOf(false) }
                SettingsRow(
                    AtharIcons.Repeat,
                    "إعادة الإعدادات الافتراضية",
                    "المظهر والقراءة والشريط السفلي وترتيب القوائم؛ لا تُمسّ المقتطفات والتنزيلات والأذكار",
                    onClick = { confirm = true },
                    tag = "settings_reset",
                )
                AtharMenu(expanded = confirm, onDismiss = { confirm = false }, width = 260.dp) {
                    Text("إعادة كل الإعدادات إلى الافتراضي؟", Modifier.padding(14.dp))
                    AtharMenuItem("إعادة", destructive = true, onClick = { confirm = false; onResetSettings() })
                    AtharMenuItem("إلغاء", onClick = { confirm = false })
                }
            }
            HorizontalDivider(color = AtharTheme.colors.divider)
            // 4.8: the fonts' licences (full texts ship in assets/licenses). ponytail: a fixed list, not generated from the dependencies.
            Box {
                var open by remember { mutableStateOf(false) }
                SettingsRow(AtharIcons.Info, "الخطوط والتراخيص", "كتاب، أميري، IBM Plex Sans Arabic، خطوط المصحف", onClick = { open = true }, tag = "settings_licenses")
                AtharMenu(expanded = open, onDismiss = { open = false }, width = 300.dp) {
                    Text(
                        "كتاب (Katib) وأميري وIBM Plex Sans Arabic: رخصة الخطوط المفتوحة SIL OFL 1.1.\n" +
                            "خطوط المصحف لأحمد الغريب: بشروطها المرفقة.\n" +
                            "نصوص التراخيص كاملة في ملفات التطبيق (assets/licenses).",
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // Legal contact is an app-wide matter, not a reading preference.
            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsRow(
                AtharIcons.Info,
                "حقوق النشر",
                com.atharchive.data.CopyrightNotice + "\n" + com.atharchive.data.CopyrightEmail,
                onClick = { com.atharchive.data.emailCopyright(context) },
                tag = "settings_copyright",
            )
        }
    }
}

/** A quiet label over a group of settings rows; blank for a plain gap. */
@Composable
private fun SettingsGroupTitle(title: String) {
    Text(title, Modifier.padding(top = 22.dp, bottom = 4.dp), color = AtharTheme.colors.secondaryText,
        fontSize = com.atharchive.ui.theme.AtharTextSize.Small, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    tag: String? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        color = AtharTheme.colors.canvas,
        // canvas is not a scheme colour, so without this the content falls back to black (unreadable in dark).
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 15.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let {
                    Text(
                        text = it,
                        color = AtharTheme.colors.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Icon(
                AtharIcons.Forward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = AtharTheme.colors.secondaryText,
            )
        }
    }
}



private val CacheBudgetOptions = listOf(
    500L * 1024 * 1024,
    2L * 1024 * 1024 * 1024,
    5L * 1024 * 1024 * 1024,
    10L * 1024 * 1024 * 1024,
    20L * 1024 * 1024 * 1024,
    Long.MAX_VALUE,
)

private fun cacheBudgetLabel(bytes: Long): String =
    if (bytes == Long.MAX_VALUE) "بلا حد" else com.atharchive.data.byteSize(bytes)

/** «النص» then «— الكتاب، المؤلف، ج٢، ص٧٨» and the link: what «نسخ مع التذييل» copies. */
