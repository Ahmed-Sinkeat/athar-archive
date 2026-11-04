package com.atharchive.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.focusRequester
import com.atharchive.ui.theme.AtharCorners
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.flow.first
import com.atharchive.ui.theme.AtharTextSize
import com.atharchive.ui.components.arabicDigits
import com.atharchive.ui.components.AtharAppMenuEntry
import com.atharchive.ui.components.LocalAtharAppMenu
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.atharchive.feature.articles.ArticlesScreen
import com.atharchive.feature.downloads.CacheBudget
import com.atharchive.feature.downloads.DownloadsScreen
import com.atharchive.feature.downloads.DownloadsListScreen
import com.atharchive.feature.adhkar.AdhkarContent
import com.atharchive.feature.adhkar.AdhkarScreen
import com.atharchive.feature.downloads.StorageUi
import com.atharchive.feature.downloads.DownloadsUiState
import com.atharchive.feature.issues.IssuesScreen
import com.atharchive.feature.audio.AudioProgressBar
import com.atharchive.feature.audio.AudioUi
import com.atharchive.feature.audio.NowPlayingUi
import com.atharchive.feature.audio.PlayerScreen
import com.atharchive.feature.books.BooksScreen
import com.atharchive.feature.books.BookDownloadUi
import com.atharchive.feature.kannashah.ExcerptUi
import com.atharchive.feature.kannashah.KannashahScreen
import com.atharchive.feature.reader.ReaderScreen
import com.atharchive.feature.poemreader.PoemReaderScreen
import com.atharchive.feature.poetry.PoetryScreen
import com.atharchive.feature.reader.ReaderSearchTarget
import com.atharchive.feature.sections.AtharSection
import com.atharchive.feature.search.SearchScreen
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.components.AtharTailPopover
import com.atharchive.ui.components.LocalAtharActionAnchor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import com.atharchive.ui.components.AtharBottomBar
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.navigation.AtharDestination
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.feature.reader.ReaderSettings
import com.atharchive.ui.settings.AppearanceScreen
import com.atharchive.ui.theme.AtharTheme
import com.atharchive.ui.theme.ThemeMode
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.atharchive.data.AtharContentViewModel
import androidx.core.content.ContextCompat

@Serializable
private data object BooksCatalogRoute : NavKey

@Serializable
private data class SectionRoute(val destination: String) : NavKey

@Serializable
private data object SettingsRoute : NavKey

@Serializable
private data object PlayerRoute : NavKey

@Serializable
private data class PoemReaderRoute(val poemId: String, val targetOrdinal: Int? = null, val match: String? = null) : NavKey

@Serializable
private data class ReaderRoute(
    val bookId: String,
    val targetOrdinal: Int? = null,
    val matchStart: Int? = null,
    val matchEnd: Int? = null,
) : NavKey

@Serializable
private data class SubSectionRoute(val section: String) : NavKey

@Serializable
private data object DownloadsRoute : NavKey
@Serializable
private data class DownloadsListRoute(val targetId: String? = null) : NavKey

@Serializable
private data object AdhkarRoute : NavKey

@Serializable
private data class PersonsRoute(val personId: String? = null) : NavKey

@Serializable
private data object AppearanceRoute : NavKey

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AtharApp(
    downloadTarget: String? = null,
    onDownloadTargetHandled: () -> Unit = {},
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    style: com.atharchive.ui.theme.AtharStyle = com.atharchive.ui.theme.AtharStyle.Paper,
    onStyleChange: (com.atharchive.ui.theme.AtharStyle) -> Unit = {},
    accent: com.atharchive.ui.theme.AccentChoice = com.atharchive.ui.theme.AccentChoice(),
    onAccentChange: (com.atharchive.ui.theme.AccentChoice) -> Unit = {},
    startPlace: com.atharchive.data.StartPlace = com.atharchive.data.StartPlace.Books,
    onStartPlaceChange: (com.atharchive.data.StartPlace) -> Unit = {},
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    contentViewModel: AtharContentViewModel = hiltViewModel(),
) {
    com.atharchive.ui.components.ProvideAtharTextToolbar {
    val context = LocalContext.current
    // Only the first composition matters: later changes of the setting apply at next launch.
    val backStack = rememberNavBackStack(
        *(startPlace.route?.takeIf { it != "books" }?.let { arrayOf<NavKey>(BooksCatalogRoute, SectionRoute(it)) }
            ?: arrayOf<NavKey>(BooksCatalogRoute)),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var scrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }
    val audioState by contentViewModel.audio.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        // One-time fold: old adhkar prefs ids become favourites, then the key is dropped.
        val prefs = context.getSharedPreferences("adhkar", Context.MODE_PRIVATE)
        val old = prefs.getStringSet("saved", null).orEmpty().map { "dhikr:$it" }
        if (contentViewModel.foldFavourites(old)) prefs.edit().remove("saved").apply()
    }
    val playbackState by contentViewModel.playback.state.collectAsStateWithLifecycle()
    val syncState by contentViewModel.syncState.collectAsStateWithLifecycle()
    // Playback ticks twice a second: these are read where they are drawn, or through derived
    // states that change only when the value does, so the whole app no longer recomposes per tick.
    val nowPlaying by remember { derivedStateOf { audioState.nowPlaying } }
    val hasRecordings by remember { derivedStateOf { audioState.recordings.isNotEmpty() } }
    val playbackError by remember { derivedStateOf { playbackState.error } }
    LaunchedEffect(hasRecordings) {
        if (hasRecordings) contentViewModel.foldAudioFavourites(audioState.recordings)
    }
    val actionAnchor = remember { mutableStateOf(Rect.Zero) }
    var editorAnchor by remember { mutableStateOf(Rect.Zero) }
    var editingExcerpt by remember { mutableStateOf<ExcerptUi?>(null) }
    // An excerpt for الكناشة to scroll to once it opens (from a note's «في الكناشة»).
    var kannashahFocus by remember { mutableStateOf<String?>(null) }
    // One's own dhikr being added or edited (from الأذكار, or from a selection in a reader).
    val userAdhkar = remember { com.atharchive.data.UserAdhkarStore.get(context) }
    var adhkarReminders by remember { mutableStateOf(com.atharchive.data.AdhkarReminders.all(context)) }
    var adhkarOpen by remember { mutableStateOf<String?>(null) }
    val userAdhkarState by userAdhkar.state.collectAsStateWithLifecycle()
    var editingDhikr by remember { mutableStateOf<com.atharchive.data.UserDhikr?>(null) }
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    // From a selection there is no tapped control to point at: the popover sits high in the middle.
    fun addDhikr(text: String, source: String, lists: Set<String> = emptySet(), anchor: Rect? = null) {
        editorAnchor = anchor ?: Rect(androidx.compose.ui.geometry.Offset(windowSize.width / 2f, windowSize.height * 0.22f), Size(1f, 1f))
        editingDhikr = com.atharchive.data.UserDhikr(com.atharchive.data.UserAdhkarStore.newId(), text, 1, source.ifBlank { null }, lists)
    }
    var deletingExcerpt by remember { mutableStateOf<ExcerptUi?>(null) }
    // Each screen collects its own state inside its entry: a flow collected here runs from
    // launch, and every write to `entity` re-reads the whole catalog once per running flow.
    val booksState by contentViewModel.books.collectAsStateWithLifecycle()
    LaunchedEffect(contentViewModel) {
        contentViewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(playbackError) { playbackError?.let { snackbarHostState.showSnackbar(it) } }
    val catalogUpdate by contentViewModel.catalogUpdate.collectAsStateWithLifecycle()
    val updatingLibrary = catalogUpdate != null
    LaunchedEffect(updatingLibrary) {
        // Shown for as long as the update runs; leaving this effect (update done) dismisses it.
        if (updatingLibrary && snackbarHostState.showSnackbar(LibraryUpdatingMessage, "إلغاء",
                duration = androidx.compose.material3.SnackbarDuration.Indefinite) == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            contentViewModel.cancelSync()
        }
    }
    LaunchedEffect(syncState) {
        // Launch checks fail quietly (Settings shows the state); only a check the reader asked for speaks up.
        val failed = syncState as? com.atharchive.data.ContentSyncUiState.Failed
        if (failed?.manual == true &&
            snackbarHostState.showSnackbar("تعذّر تحديث المحتوى: ${failed.message}", "إعادة المحاولة", withDismissAction = true,
                // With an action it would stay until dismissed, over the readers' bottom bars.
                duration = androidx.compose.material3.SnackbarDuration.Long) == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            contentViewModel.sync(manual = true)
        }
    }
    val storageStatus by contentViewModel.storageStatus.collectAsStateWithLifecycle()
    val retainedIds by contentViewModel.retainedEntityIds.collectAsStateWithLifecycle()
    val recentQueries by contentViewModel.recentQueries.collectAsStateWithLifecycle()
    val cacheBytes by contentViewModel.cacheBytes.collectAsStateWithLifecycle()
    val cacheBudgetBytes by contentViewModel.cacheBudgetBytes.collectAsStateWithLifecycle()
    val pinnedCount by contentViewModel.pinnedCount.collectAsStateWithLifecycle()

    fun announce(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // The كناشة list only runs while something watches it (it was empty in the readers, so the
    // note tap did nothing): wait briefly for this excerpt to arrive.
    fun openExcerptNote(id: String?) {
        val anchor = actionAnchor.value
        scope.launch {
            val excerpt = id?.let {
                kotlinx.coroutines.withTimeoutOrNull(3_000) {
                    contentViewModel.kannashahState.first { s -> s.excerpts.any { it.id == id } }
                }?.excerpts?.firstOrNull { it.id == id }
            }
            if (excerpt == null) announce("تعذّر فتح التعليق") else { editorAnchor = anchor; editingExcerpt = excerpt }
        }
    }

    // StartupTimingMetric can only measure "interactive library" when Compose tells the
    // platform that the asynchronous Room-backed catalogue has reached the screen.
    ReportDrawnWhen { booksState.loaded }
    // Once, on first opening; the same words stay in الإعدادات › حقوق النشر (main settings).
    var copyrightSeen by com.atharchive.ui.components.rememberPersisted("copyright.notice.seen", "")
    if (copyrightSeen.isEmpty()) {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { copyrightSeen = "1" },
            title = { Text("حقوق النشر") },
            text = { Text(com.atharchive.data.CopyrightNotice + "\n\n" + com.atharchive.data.CopyrightEmail) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { copyrightSeen = "1" }) { Text("حسنًا") } },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { copyrightSeen = "1"; com.atharchive.data.emailCopyright(context) }) { Text("مراسلة") }
            },
            modifier = Modifier.testTag("copyright_notice"),
        )
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val exportUserData = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                    contentViewModel.exportUserData(output, mapOf("adhkar" to userAdhkar.exportJson()))
                }
            }.onSuccess {
                announce("اكتمل تصدير بياناتك")
            }.onFailure {
                announce("تعذّر تصدير البيانات")
            }
        }
    }
    val importUserData = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    contentViewModel.importUserData(input)
                }
            }.mapCatching { report ->
                report.imported + (report.extras["adhkar"]?.let(userAdhkar::merge) ?: 0)
            }.onSuccess { imported ->
                announce("استُورد ${arabicDigits(imported)} سجلًا")
            }.onFailure {
                announce("الملف ليس تصديرًا صالحًا من أثر")
            }
        }
    }

    // Saved books to another phone with no internet: one file through the share sheet
    // (Quick Share, Bluetooth). The receiver checks every book against its own signed catalog.
    fun sendBooks(ids: List<String>) {
        scope.launch {
            runCatching {
                val dir = java.io.File(context.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
                val file = java.io.File(dir, "athar-books.zip")
                val count = file.outputStream().use { contentViewModel.exportSharedBooks(ids, it) }
                if (count == 0) { file.delete(); return@runCatching false }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "إرسال ${arabicDigits(count)} من المحفوظ",
                    ),
                )
                true
            }.onSuccess { sent ->
                if (!sent) announce("التسجيلات لا تُرسل؛ اختر كتبًا أو قصائد أو مقالات محفوظة")
            }.onFailure {
                announce("تعذّر تجهيز الملف للإرسال")
            }
        }
    }
    val receiveBooks = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { contentViewModel.importSharedBooks(it) }
            }.onSuccess { count ->
                announce(if (count == 0) "لا شيء في الملف يطابق نسخ أثر على هذا الجهاز" else "يُستورد ${arabicDigits(count)} من المحفوظ")
            }.onFailure {
                announce("الملف ليس ملف كتب من أثر")
            }
        }
    }

    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    // Asked after the transfer is queued and once per session: the download never waits on it.
    fun requestNotificationPermission() {
        if (askedNotifications) return
        askedNotifications = true
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // One path for every row. A finished download asks first (it deletes the offline copy);
    // a running one cancels (a second tap on the same button); a paused/failed one resumes; only a fresh start asks for notifications.
    var pendingRemoval by remember { mutableStateOf<DownloadRemoval?>(null) }
    fun onDownloadTap(id: String, title: String, sizeLabel: String, downloaded: Boolean, transfer: com.atharchive.feature.downloads.TransferUi?) {
        if (downloaded) {
            pendingRemoval = DownloadRemoval(id, title, sizeLabel)
            return
        }
        if (transfer is com.atharchive.feature.downloads.TransferUi.Running) return contentViewModel.removeDownload(id)
        contentViewModel.toggleDownload(id)
        if (transfer == null) requestNotificationPermission()
    }
    pendingRemoval?.let { removal ->
        AtharMenu(expanded = true, anchor = actionAnchor.value, onDismiss = { pendingRemoval = null }) {
            Text("إزالة التنزيل؟", Modifier.padding(14.dp))
            Text("«${removal.title}» يمكن تنزيله مجددًا. ${removal.sizeLabel}", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodySmall)
            AtharMenuItem("إزالة", destructive = true, onClick = { contentViewModel.removeDownload(removal.id); pendingRemoval = null })
            AtharMenuItem("إلغاء", onClick = { pendingRemoval = null })
        }
    }

    // «آخر ما قرأت»: once per launch (the flag survives recreation), reopen the work read last.
    var startHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startHandled) return@LaunchedEffect
        startHandled = true
        if (startPlace == com.atharchive.data.StartPlace.LastRead && downloadTarget == null) {
            contentViewModel.lastReadWork()?.let { (id, collection) ->
                backStack.add(if (collection == "poem") PoemReaderRoute(id) else ReaderRoute(id))
            }
        }
    }

    LaunchedEffect(downloadTarget) {
        // ponytail: "book:<id>[:<ordinal>]" opens the reader, for adb phone checks
        // (`am start -e athar_download book:<id>:<n>`); a real deep link replaces it if one is ever needed.
        val book = downloadTarget?.removePrefix("book:")?.takeIf { it != downloadTarget }?.split(":")
        if (book != null) { backStack.add(ReaderRoute(book[0], targetOrdinal = book.getOrNull(1)?.toIntOrNull())); onDownloadTargetHandled() }
        else if (downloadTarget?.startsWith("poem:") == true) {
            val poem = downloadTarget.removePrefix("poem:").split(":")
            backStack.add(PoemReaderRoute(poem[0], poem.getOrNull(1)?.toIntOrNull()))
            onDownloadTargetHandled()
        }
        else if (downloadTarget?.startsWith("adhkar:") == true) {
            adhkarOpen = downloadTarget.removePrefix("adhkar:")
            if (backStack.lastOrNull() != AdhkarRoute) backStack.add(AdhkarRoute)
            onDownloadTargetHandled()
        }
        else if (downloadTarget != null) { backStack.add(DownloadsListRoute(downloadTarget)); onDownloadTargetHandled() }
    }
    val tabState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val inReader = backStack.lastOrNull() is ReaderRoute
    // The player owns the whole screen, like the reader: no bottom chrome under it.
    val inPlayer = backStack.lastOrNull() is PlayerRoute
    // Poetry reads full-screen too: the poem is the only thing on it.
    val inPoemReader = backStack.lastOrNull() is PoemReaderRoute
    val currentDestination = when (val current = backStack.lastOrNull()) {
        is SectionRoute -> AtharDestination.fromRoute(current.destination)
        BooksCatalogRoute -> AtharDestination.Books
        else -> null
    }

    // A recited poem opens in the poem reader, never as prose.
    fun openAudioSource(audio: com.atharchive.feature.audio.AudioUi) {
        backStack.add(
            if (audio.sourceKind == com.atharchive.feature.audio.AudioSourceKind.Poem) PoemReaderRoute(audio.sourceEntityId)
            else ReaderRoute(audio.sourceEntityId),
        )
    }

    fun openSettings() {
        backStack.add(SettingsRoute)
    }

    fun goBack() {
        // NavDisplay throws on an empty stack; a double-tap on a back arrow pops twice before recomposition.
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    /** Opens a passage from any search: local hits know their paragraph, online ones are located first. */
    fun openHit(hit: com.atharchive.feature.search.SearchHitUi, query: String) {
        val poem = hit.type == com.atharchive.feature.search.SearchResultType.Poem
        if (hit.url != null) scope.launch {
            val (ordinal, start, end) = contentViewModel.locateOnlineHit(hit, query)
            val found = ordinal != null && end > start
            backStack.add(if (poem) PoemReaderRoute(hit.entityId, ordinal, query) else ReaderRoute(hit.entityId, ordinal, start.takeIf { found }, end.takeIf { found }))
        } else if (poem) {
            backStack.add(PoemReaderRoute(hit.entityId, hit.ordinal, query))
        } else {
            backStack.add(ReaderRoute(hit.entityId, hit.ordinal, hit.sourceMatchStart, hit.sourceMatchEnd))
        }
    }

    fun sectionSearch(type: com.atharchive.feature.search.SearchResultType) = com.atharchive.feature.search.SectionTextSearch(
        search = { contentViewModel.searchSection(type, it) },
        loadPassage = contentViewModel::onlinePassage,
        onOpen = ::openHit,
    )

    /** Swaps [current] for [next] where it sits, so back leaves the book instead of stepping through editions. */
    fun replaceRoute(current: NavKey, next: NavKey) {
        val index = backStack.lastIndexOf(current)
        if (index >= 0) backStack[index] = next else backStack.add(next)
    }

    fun navigateToBooksRoot() {
        while (backStack.size > 1) backStack.removeLastOrNull()
    }

    fun navigateTo(destination: AtharDestination) {
        // Tapping the section you're on scrolls it to the top (the pill wobbles) instead of rebuilding it.
        if (destination == currentDestination) {
            scrollToTopRequest++
        } else if (destination == AtharDestination.Books) {
            navigateToBooksRoot()
        } else {
            navigateToBooksRoot()
            backStack.add(SectionRoute(destination.route))
        }
    }

    LaunchedEffect(storageStatus?.lowStorage, storageStatus?.availableBytes) {
        if (storageStatus?.lowStorage == true) {
            announce("المساحة منخفضة؛ أوقفنا التخزين المؤقت وحافظنا على التنزيلات المثبتة")
        }
    }

    Scaffold(
        // One imePadding for the whole app. Applied per-screen it lifted the content but
        // not the bottom bar, leaving a canvas-coloured band above the keyboard.
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = AtharTheme.colors.canvas,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                if (data.visuals.message != LibraryUpdatingMessage) androidx.compose.material3.Snackbar(data)
                else androidx.compose.material3.Snackbar(
                    action = { androidx.compose.material3.TextButton(onClick = data::performAction) { Text("إلغاء") } },
                ) {
                    androidx.compose.foundation.layout.Column {
                        Text(LibraryUpdatingMessage)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { catalogUpdate ?: 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (inPlayer || inPoemReader) return@Scaffold
            if (inReader) {
                // A book has its own strip for its own audio; anything else still playing
                // (a poem, another book's lesson) keeps its mini player here, without the tabs.
                val reading = (backStack.lastOrNull() as? ReaderRoute)?.bookId
                nowPlaying?.takeIf { it.audio.sourceEntityId != reading }?.let { playing ->
                    MiniPlayer(
                        audio = playing.audio,
                        playing = playing.playing,
                        progress = playing.progress,
                        onToggle = contentViewModel.playback::toggle,
                        onOpen = { backStack.add(PlayerRoute) },
                        onClose = contentViewModel.playback::stop,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
                return@Scaffold
            }
            Column {
                // The mini player sits above the bar and below everything else: it must
                // survive navigation, so it lives here rather than inside الصوتيات.
                nowPlaying?.let { playing ->
                    MiniPlayer(
                        audio = playing.audio,
                        playing = playing.playing,
                        progress = playing.progress,
                        onToggle = contentViewModel.playback::toggle,
                        onOpen = { backStack.add(PlayerRoute) },
                        onClose = contentViewModel.playback::stop,
                    )
                }
                AtharBottomBar(
                    selectedDestination = currentDestination,
                    onDestinationClick = ::navigateTo,
                )
            }
        },
    ) { contentPadding ->
        val appMenu = listOf(
            AtharAppMenuEntry(AtharSection.Issues.label, AtharSection.Issues.icon) { backStack.add(SubSectionRoute(AtharSection.Issues.route)) },
            AtharAppMenuEntry("الأذكار", AtharIcons.Adhkar) { backStack.add(AdhkarRoute) },
            AtharAppMenuEntry("الأعلام", AtharIcons.People) { backStack.add(PersonsRoute()) },
            AtharAppMenuEntry("الإعدادات", AtharIcons.Settings) { openSettings() },
        )
        androidx.compose.runtime.CompositionLocalProvider(LocalAtharAppMenu provides appMenu, LocalAtharActionAnchor provides actionAnchor,
            com.atharchive.feature.reader.LocalAddToAdhkar provides { text, source -> addDhikr(text, source) },
            com.atharchive.feature.reader.LocalDictionary provides contentViewModel.dictionary,
            com.atharchive.feature.reader.LocalOpenBookAt provides { id, ordinal -> backStack.add(ReaderRoute(id, targetOrdinal = ordinal)) }) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<BooksCatalogRoute> {
                    BooksScreen(
                        state = booksState,
                        onSettings = { openSettings() },
                        onBookClick = { backStack.add(ReaderRoute(it.id)) },
                        onDownloadClick = {
                            onDownloadTap(
                                it.id, it.title, it.download.sizeLabel,
                                it.download is BookDownloadUi.Downloaded,
                                (it.download as? BookDownloadUi.Downloading)?.transfer,
                            )
                        },
                        onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                        scrollToTopRequest = scrollToTopRequest,
                    )
                }
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
                        onBack = ::goBack,
                    )
                }
                // The reader owns the whole screen: no bottom navigation inside a book.
                entry<ReaderRoute> { route ->
                    val readerEmitted = collectEmitted(route.bookId) { contentViewModel.readerState(route.bookId) }
                    val readerState = readerEmitted?.value
                    val readerWindow = remember(route.bookId) { contentViewModel.readerWindow(route.bookId) }
                    val openProgress by contentViewModel.openProgress.collectAsStateWithLifecycle()
                    val loadProgress = openProgress?.takeIf { it.first == route.bookId }?.second
                    LaunchedEffect(route.bookId, route.targetOrdinal) {
                        // Any path that opens a poem as a book (links, meanings, lists) lands in the poem reader instead.
                        if (contentViewModel.isPoem(route.bookId)) return@LaunchedEffect replaceRoute(route, PoemReaderRoute(route.bookId, route.targetOrdinal))
                        // A work opens in the edition last chosen for it; a jump to a place opens as asked.
                        val edition = if (route.targetOrdinal == null) contentViewModel.editionToOpen(route.bookId) else route.bookId
                        if (edition != route.bookId) replaceRoute(route, ReaderRoute(edition))
                        else contentViewModel.openReader(route.bookId, route.targetOrdinal)
                    }
                    DisposableEffect(route.bookId) {
                        onDispose { contentViewModel.closeReader(route.bookId) }
                    }
                    // "Not emitted yet" is loading, not missing: the flow's first value can wait
                    // behind a write, and «غير متاح» used to flash on every slow open.
                    if (readerEmitted == null) Column(Modifier.fillMaxSize()) {
                        IconButton(onClick = ::goBack) { Icon(AtharIcons.Back, "رجوع") }
                        com.atharchive.ui.components.AtharLoading(
                            "جارٍ تحميل النص…", Modifier.padding(top = 120.dp), tag = "reader_loading", progress = loadProgress,
                        )
                    } else if (readerState == null) Column(Modifier.fillMaxSize()) {
                        IconButton(onClick = ::goBack) { Icon(AtharIcons.Back, "رجوع") }
                        com.atharchive.ui.components.AtharEmptyState(
                            "المصدر غير متاح بعد", "جارٍ تحميل الفهرس؛ إن استمر ذلك فتحقّق من الاتصال وحدّث المحتوى.",
                            "reader_source_unavailable", 24.dp,
                        )
                        TextButton(onClick = { contentViewModel.sync(); contentViewModel.openReader(route.bookId, route.targetOrdinal) }) { Text("إعادة المحاولة") }
                    }
                    val authorId by androidx.compose.runtime.produceState<String?>(null, route.bookId) { value = contentViewModel.authorOf(route.bookId) }
                    readerState?.let { state ->
                      androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        ReaderScreen(
                            state = state,
                            window = readerWindow,
                            loadProgress = loadProgress,
                            initialOrdinal = route.targetOrdinal,
                            onSearch = { query, mode -> contentViewModel.searchInBook(route.bookId, query, mode) },
                            bookSaved = route.bookId in retainedIds,
                            onDownloadForSearch = { contentViewModel.downloadForSearch(route.bookId) },
                            onOpenAuthor = authorId?.let { id -> { backStack.add(PersonsRoute(id)) } },
                            onShareBook = { shareText(context, listOf(state.bookTitle, state.author, state.sourceUrl).filter(String::isNotBlank).joinToString("\n")) },
                            onLocateOnline = { url, q -> contentViewModel.locateOnline(route.bookId, url, q) },
                            recentQueries = recentQueries,
                            onRememberSearch = contentViewModel::rememberSearch,
                            onPageJump = { page, volume -> contentViewModel.findPrintedPage(route.bookId, page, volume) },
                            onAnchorJump = { contentViewModel.findHeadingAnchor(route.bookId, it) },
                            onLoadSelectionBlocks = { contentViewModel.selectionBlocks(route.bookId, it) },
                            onRemoveHighlight = contentViewModel::deleteAnnotation,
                            onEditHighlightNote = { openExcerptNote(it) },
                            onOpenInKannashah = { kannashahFocus = it; navigateTo(AtharDestination.Kannashah) },
                            onCopyCited = { selection ->
                                val (vol, page) = contentViewModel.printedPlace(route.bookId, minOf(selection.start.ordinal, selection.end.ordinal))
                                copyText(context, citedText(selection.quotedText, state.bookTitle, state.author,
                                    vol.takeIf { state.volumeStarts.isNotEmpty() }, page, state.sourceUrl, state.volumeUnit))
                            },
                            onPlayAudio = { contentViewModel.playWorkAudio(route.bookId, it.id) },
                            playback = { playbackState },
                            onToggleAudio = contentViewModel.playback::toggle,
                            onSpeed = contentViewModel.playback::speed,
                            onRepeat = contentViewModel.playback::repeat,
                            onSeekMs = contentViewModel.playback::seekTo,
                            onOpenEntity = { backStack.add(ReaderRoute(it)) },
                            onEdition = { id ->
                                state.editions.firstOrNull { it.primary }?.let { contentViewModel.chooseEdition(it.id, id) }
                                replaceRoute(route, ReaderRoute(id))
                            },
                            onBack = ::goBack,
                            onCopySelection = { selection ->
                                copyText(context, selection.quotedText)
                            },
                            onShareSelection = { selection ->
                                // Same source line as «نسخ مع التذييل»: the volume and page say where in the book it is.
                                val (vol, page) = contentViewModel.printedPlace(route.bookId, minOf(selection.start.ordinal, selection.end.ordinal))
                                shareText(context, citedText(selection.quotedText, state.bookTitle, state.author,
                                    vol.takeIf { state.volumeStarts.isNotEmpty() }, page, state.sourceUrl, state.volumeUnit))
                            },
                            onSaveSelection = { selection ->
                                contentViewModel.saveSelection(route.bookId, selection)
                            },
                            onToggleBookmark = { ordinal ->
                                contentViewModel.toggleBookmark(route.bookId, ordinal)
                            },
                            onPositionChange = { contentViewModel.onReaderPosition(route.bookId, it) },
                            onPositionWithOffset = { ordinal, offset -> contentViewModel.onReaderPosition(route.bookId, ordinal, offset) },
                            settings = readerSettings,
                            onSettingsChange = onReaderSettingsChange,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            initialSearchTarget = if (
                                route.targetOrdinal != null && route.matchStart != null && route.matchEnd != null
                            ) {
                                ReaderSearchTarget(route.targetOrdinal, route.matchStart, route.matchEnd)
                            } else {
                                null
                            },
                        )
                      }
                    }
                }
                entry<PoemReaderRoute> { route ->
                    val poemEmitted = collectEmitted(route.poemId) { contentViewModel.poemReaderState(route.poemId) }
                    val openProgress by contentViewModel.openProgress.collectAsStateWithLifecycle()
                    val loadProgress = openProgress?.takeIf { it.first == route.poemId }?.second
                    val poemState = poemEmitted?.value
                    LaunchedEffect(route.poemId) { contentViewModel.openReader(route.poemId, route.targetOrdinal) }
                    DisposableEffect(route.poemId) {
                        onDispose { contentViewModel.closeReader(route.poemId) }
                    }
                    if (poemEmitted == null) Column(Modifier.fillMaxSize()) {
                        IconButton(onClick = ::goBack) { Icon(AtharIcons.Back, "رجوع") }
                        com.atharchive.ui.components.AtharLoading("جارٍ تحميل القصيدة…", Modifier.padding(top = 120.dp), tag = "poem_loading", progress = loadProgress)
                    } else if (poemState == null) UnavailableRoute("القصيدة غير متاحة بعد", ::goBack) { contentViewModel.sync(); contentViewModel.openReader(route.poemId, route.targetOrdinal) }
                    val poetId by androidx.compose.runtime.produceState<String?>(null, route.poemId) { value = contentViewModel.authorOf(route.poemId) }
                    poemState?.let { state -> PoemReaderScreen(
                            state = state,
                            loadProgress = loadProgress,
                            onBack = ::goBack,
                            onOpenAuthor = poetId?.let { id -> { backStack.add(PersonsRoute(id)) } },
                            onDownload = { contentViewModel.downloadForSearch(route.poemId) }.takeUnless { route.poemId in retainedIds },
                            settings = readerSettings,
                            onSettingsChange = onReaderSettingsChange,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            playback = playbackState,
                            onPlayAudio = { contentViewModel.playWorkAudio(route.poemId, it.id) },
                            onToggleAudio = contentViewModel.playback::toggle,
                            onSpeed = contentViewModel.playback::speed,
                            onRepeat = contentViewModel.playback::repeat,
                            onSeekMs = contentViewModel.playback::seekTo,
                            onToggleBookmark = { contentViewModel.toggleBookmark(route.poemId, it) },
                            onSaveVerse = { contentViewModel.saveBlock(route.poemId, it) },
                            onRemoveVerse = { contentViewModel.removeSavedBlock(route.poemId, it) },
                            onVerseNote = { scope.launch { openExcerptNote(contentViewModel.savedBlockId(route.poemId, it)) } },
                            onVerseInKannashah = { scope.launch {
                                contentViewModel.savedBlockId(route.poemId, it)?.let { id -> kannashahFocus = id; navigateTo(AtharDestination.Kannashah) }
                            } },
                            initialOrdinal = route.targetOrdinal,
                            match = route.match,
                            onPositionChange = { contentViewModel.onReaderPosition(route.poemId, it) },
                            onRetry = { contentViewModel.openReader(route.poemId, route.targetOrdinal) },
                        ) }
                }
                entry<PlayerRoute> {
                    if (nowPlaying == null) UnavailableRoute("لا يوجد تسجيل متاح في المشغّل", ::goBack) { contentViewModel.sync() }
                    val recordings = audioState.recordings
                    val byId = remember(recordings) { recordings.associateBy { it.id } }
                    val queue = remember(playbackState.queueIds, byId) { playbackState.queueIds.mapNotNull(byId::get) }
                    nowPlaying?.let { playing ->
                        PlayerScreen(
                            nowPlaying = playing,
                            queue = queue,
                            onCollapse = ::goBack,
                            onToggle = contentViewModel.playback::toggle,
                            onSelect = { contentViewModel.playAudio(it.id, queue) },
                            onSkipPrevious = contentViewModel.playback::previous,
                            onSkipNext = contentViewModel.playback::next,
                            onSeek = contentViewModel.playback::seekBy,
                            onSeekFraction = { contentViewModel.playback.seekTo((it * playbackState.durationMs).toLong()) },
                            speed = playbackState.speed,
                            repeat = playbackState.repeat,
                            onSpeed = contentViewModel.playback::speed,
                            onRepeat = contentViewModel.playback::repeat,
                            onSleep = contentViewModel.playback::sleep,
                            sleepMinutes = playbackState.sleepMinutes,
                            onMore = { openAudioSource(playing.audio) },
                            onSave = { contentViewModel.toggleFavourite(playing.audio.sourceEntityId) },
                        )
                    }
                }
                entry<SubSectionRoute> { route ->
                    when (AtharSection.entries.first { it.route == route.section }) {
                        AtharSection.Articles -> ArticlesScreen(
                            state = contentViewModel.articles.collectAsStateWithLifecycle().value,
                            onSettings = ::openSettings,
                            onArticleClick = { backStack.add(ReaderRoute(it.id)) },
                            textSearch = remember { sectionSearch(com.atharchive.feature.search.SearchResultType.Article) },
                            onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                            onDownloadClick = { onDownloadTap(it.id, it.title, it.sizeLabel, it.downloaded, it.transfer) },
                            onBack = ::goBack,
                        )

                        AtharSection.Issues -> IssuesScreen(
                            state = contentViewModel.issues.collectAsStateWithLifecycle().value,
                            onSettings = ::openSettings,
                            onIssueClick = { backStack.add(ReaderRoute(it.id)) },
                            textSearch = remember { sectionSearch(com.atharchive.feature.search.SearchResultType.Issue) },
                            onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                            onDownloadClick = { onDownloadTap(it.id, it.question, it.sizeLabel, it.downloaded, it.transfer) },
                            onBack = ::goBack,
                        )


                    }
                }
                entry<PersonsRoute> { route ->
                    val persons by contentViewModel.persons.collectAsStateWithLifecycle()
                    val works by contentViewModel.personWorks.collectAsStateWithLifecycle()
                    val person = persons.firstOrNull { it.id == route.personId }
                    if (route.personId == null) com.atharchive.feature.persons.PersonsScreen(persons, works, { backStack.add(PersonsRoute(it.id)) }, ::openSettings, ::goBack)
                    else if (person != null) com.atharchive.feature.persons.PersonScreen(person, works, { work ->
                        backStack.add(if (work.coll == "poem") PoemReaderRoute(work.id) else ReaderRoute(work.id))
                    }, ::openSettings, ::goBack)
                    else UnavailableRoute("العَلَم غير متاح", ::goBack) { contentViewModel.sync() }
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
                        onBack = ::goBack,
                    )
                }
                entry<AdhkarRoute> {
                    val favourites by contentViewModel.favouriteIds.collectAsStateWithLifecycle()
                    val adhkar = remember(favourites, userAdhkarState) {
                        val own = userAdhkarState.items.map { com.atharchive.feature.adhkar.DhikrUi(it.id, it.text, it.repeat, it.source, custom = true) }
                        val inList = { list: String -> userAdhkarState.items.zip(own).filter { list in it.first.lists }.map { it.second } }
                        AdhkarContent.copy(
                            babs = AdhkarContent.babs + userAdhkarState.lists.map { com.atharchive.feature.adhkar.AdhkarBabUi(it.id, it.label) },
                            entries = (AdhkarContent.entries.mapValues { (bab, list) -> list + inList(bab) } +
                                userAdhkarState.lists.associate { it.id to inList(it.id) })
                                .mapValues { (_, list) -> list.map { it.copy(saved = "dhikr:${it.id}" in favourites) } },
                        )
                    }
                    AdhkarScreen(
                        state = adhkar,
                        onSettings = ::openSettings,
                        onOpenBabs = {},
                        onAdd = { list -> addDhikr("", "", setOf(list), actionAnchor.value) },
                        onEdit = { dhikr -> userAdhkarState.items.firstOrNull { it.id == dhikr.id }?.let { editorAnchor = actionAnchor.value; editingDhikr = it } },
                        onDeleteList = { id ->
                            com.atharchive.data.AdhkarReminders.set(context, id, "", null)
                            adhkarReminders = com.atharchive.data.AdhkarReminders.all(context)
                            userAdhkar.deleteList(id)
                        },
                        reminders = adhkarReminders,
                        onReminder = { id, label, minutes ->
                            if (minutes != null && android.os.Build.VERSION.SDK_INT >= 33 &&
                                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            com.atharchive.data.AdhkarReminders.set(context, id, label, minutes)
                            adhkarReminders = com.atharchive.data.AdhkarReminders.all(context)
                            announce(if (minutes == null) "أُوقف التذكير" else "سيُذكّرك كل يوم")
                        },
                        openList = adhkarOpen,
                        onOpened = { adhkarOpen = null },
                        onCopy = { copyText(context, it.text); announce("نُسخ الذكر") },
                        onSave = { contentViewModel.toggleFavourite("dhikr:${it.id}") },
                        onBack = ::goBack,
                        readerSettings = readerSettings,
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
                        onBack = ::goBack,
                        onBudgetChange = { contentViewModel.setCacheBudget(it.bytes) },
                        onClearCache = {
                            scope.launch {
                                runCatching { contentViewModel.clearCache() }
                                    .onSuccess { announce("مُسح المؤقّت وبقيت التنزيلات المثبّتة") }
                                    .onFailure { announce("تعذّر مسح المؤقّت") }
                            }
                        },
                        onDownloadAll = { contentViewModel.downloadRemaining(it) },
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
                        onBack = ::goBack,
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
                entry<SectionRoute> { route ->
                    tabState.SaveableStateProvider(route.destination) {
                    when (AtharDestination.fromRoute(route.destination)) {
                        AtharDestination.Poetry -> PoetryScreen(
                            state = contentViewModel.poetry.collectAsStateWithLifecycle().value,
                            onSettings = ::openSettings,
                            onPoemClick = { backStack.add(PoemReaderRoute(it.id)) },
                            textSearch = remember { sectionSearch(com.atharchive.feature.search.SearchResultType.Poem) },
                            onDownloadClick = { onDownloadTap(it.id, it.title, it.sizeLabel, it.downloaded, it.transfer) },
                            onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                            scrollToTopRequest = scrollToTopRequest,
                        )

                        AtharDestination.Articles -> ArticlesScreen(
                            state = contentViewModel.articles.collectAsStateWithLifecycle().value,
                            onSettings = ::openSettings,
                            onArticleClick = { backStack.add(ReaderRoute(it.id)) },
                            onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                            onDownloadClick = { onDownloadTap(it.id, it.title, it.sizeLabel, it.downloaded, it.transfer) },
                            scrollToTopRequest = scrollToTopRequest,
                            textSearch = remember { sectionSearch(com.atharchive.feature.search.SearchResultType.Article) },
                        )

                        AtharDestination.Search -> SearchScreen(
                            state = contentViewModel.search.collectAsStateWithLifecycle().value,
                            onSettings = ::openSettings,
                            onQueryChange = contentViewModel::setSearchQuery,
                            onResultsDrawn = contentViewModel::reportSearchResultsDrawn,
                            inputQuery = contentViewModel.searchQuery.collectAsStateWithLifecycle().value,
                            onLoadMore = contentViewModel::loadMoreSearch,
                            onFiltersChange = contentViewModel::setSearchFilters,
                            loadPassage = contentViewModel::onlinePassage,
                            onOpenResult = { hit ->
                                contentViewModel.rememberCurrentSearch()
                                openHit(hit, contentViewModel.searchQuery.value.trim())
                            },
                            onOpenDirectMatch = { match ->
                                contentViewModel.rememberCurrentSearch()
                                if (match.type == com.atharchive.feature.search.SearchResultType.Poem) {
                                    backStack.add(PoemReaderRoute(match.id))
                                } else {
                                    backStack.add(ReaderRoute(match.id))
                                }
                            },
                            onClearRecent = contentViewModel::clearRecentSearches,
                            presets = contentViewModel.searchPresets.collectAsStateWithLifecycle().value,
                            onSavePreset = contentViewModel::saveSearchPreset,
                            onDeletePreset = contentViewModel::deleteSearchPreset,
                        )

                        AtharDestination.Kannashah -> KannashahScreen(
                            state = contentViewModel.kannashahState.collectAsStateWithLifecycle().value,
                            onSettings = ::openSettings,
                            onCopy = {
                                copyText(context, it.text)
                                announce("نُسخ المقتطف")
                            },
                            onShare = {
                                shareText(context, it.shareText())
                            },
                            onMore = { editorAnchor = actionAnchor.value; editingExcerpt = it },
                            onDelete = { deletingExcerpt = it },
                            focusId = kannashahFocus,
                            onFocused = { kannashahFocus = null },
                            onOpenSource = { excerpt ->
                                if (excerpt.sourceColl == "poem") scope.launch {
                                    try {
                                        val ordinal = contentViewModel.savedPoemOrdinal(excerpt.entityId, excerpt.id)
                                        backStack.add(PoemReaderRoute(excerpt.entityId, ordinal))
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                    catch (_: Exception) { announce("تعذّر فتح مصدر البيت") }
                                } else backStack.add(ReaderRoute(excerpt.entityId, targetOrdinal = excerpt.sourceOrdinal))
                            },
                        )

                        // Books is the root entry, never pushed as a section.
                        AtharDestination.Books -> Unit
                    }
                }
                }
            },
        )
        }
    }

    editingExcerpt?.let { excerpt ->
        var note by rememberSaveable(excerpt.id) { mutableStateOf(excerpt.comment.orEmpty()) }
        var deleteBounds by remember { mutableStateOf(Rect.Zero) }
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        // A new note opens ready to type; an existing one waits for a tap so it can be read first.
        if (excerpt.comment.isNullOrBlank()) LaunchedEffect(excerpt.id) { runCatching { focus.requestFocus() } }
        fun save() = scope.launch {
            runCatching { contentViewModel.editAnnotation(excerpt.id, note) }
                .onSuccess { editingExcerpt = null; announce("حُفظ التعليق") }
                .onFailure { announce("تعذّر الحفظ؛ بقي التعليق مفتوحًا") }
        }
        AtharTailPopover(anchor = { editorAnchor }, ground = AtharTheme.colors.raisedSurface,
            onDismiss = { editingExcerpt = null }, maxWidth = 340.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                // The excerpt, as a quotation: an accent rule, the reader's type, and where it is from.
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(AtharTheme.colors.accent, AtharCorners.Tag))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            text = excerpt.text,
                            fontFamily = AtharEditorialFontFamily,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = listOfNotNull(excerpt.sourceTitle, excerpt.locationLabel).joinToString(" · "),
                            modifier = Modifier.padding(top = 4.dp),
                            color = AtharTheme.colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                // The writing area: a quiet page, no box-in-a-box outline.
                androidx.compose.foundation.text.BasicTextField(
                    value = note,
                    onValueChange = { note = it.take(2_000) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .heightIn(min = 112.dp, max = 220.dp)
                        .background(AtharTheme.colors.inputSurface, AtharCorners.Card)
                        .focusRequester(focus)
                        .testTag("kannashah_note_editor"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = AtharEditorialFontFamily),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AtharTheme.colors.accent),
                    decorationBox = { field ->
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            if (note.isEmpty()) Text("اكتب تعليقك…", color = AtharTheme.colors.placeholderText, style = MaterialTheme.typography.bodyLarge)
                            field()
                        }
                    },
                )
                if (note.length > 1_800) Text(
                    "${arabicDigits(note.length)} / ${arabicDigits(2_000)}",
                    Modifier.align(Alignment.End).padding(top = 4.dp),
                    color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.labelSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { actionAnchor.value = deleteBounds; deletingExcerpt = excerpt },
                        modifier = Modifier.onGloballyPositioned { deleteBounds = Rect(it.positionOnScreen(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }.testTag("kannashah_delete_annotation"),
                    ) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                    if (currentDestination != AtharDestination.Kannashah) TextButton(
                        onClick = { editingExcerpt = null; kannashahFocus = excerpt.id; navigateTo(AtharDestination.Kannashah) },
                        modifier = Modifier.testTag("kannashah_go_to_excerpt"),
                    ) {
                        Text("ذهاب إلى الكناشة", color = AtharTheme.colors.accent)
                    }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Button(
                        onClick = { save() },
                        enabled = note.trim() != excerpt.comment.orEmpty().trim(),
                        shape = AtharCorners.Control,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = AtharTheme.colors.primaryAction, contentColor = AtharTheme.colors.onPrimaryAction,
                        ),
                        modifier = Modifier.testTag("kannashah_save_note"),
                    ) {
                        Text("حفظ")
                    }
                }
            }
        }
    }

    editingDhikr?.let { draft ->
        val editing = userAdhkarState.items.any { it.id == draft.id }
        com.atharchive.feature.adhkar.DhikrEditorPopover(
            draft = draft,
            editing = editing,
            lists = com.atharchive.feature.adhkar.BuiltInAdhkarLists + userAdhkarState.lists,
            anchor = { editorAnchor },
            onSave = { dhikr, newList ->
                val lists = dhikr.lists + listOfNotNull(newList?.let(userAdhkar::addList))
                if (userAdhkar.save(dhikr.copy(lists = lists))) { editingDhikr = null; announce(if (editing) "حُفظ الذكر" else "أُضيف إلى الأذكار") }
                else announce("اكتب الذكر واختر قائمة")
            },
            onDelete = { userAdhkar.delete(draft.id); editingDhikr = null; announce("حُذف الذكر") },
            onDismiss = { editingDhikr = null },
        )
    }

    // Deleting an excerpt can't be undone, so both ways to it (the menu, the note editor) ask first.
    deletingExcerpt?.let { excerpt ->
        AtharMenu(expanded = true, anchor = actionAnchor.value, onDismiss = { deletingExcerpt = null }) {
            Text("حذف المقتطف؟", Modifier.padding(14.dp))
            Text(excerpt.text, Modifier.padding(horizontal = 14.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            AtharMenuItem("حذف", destructive = true, modifier = Modifier.testTag("kannashah_confirm_delete"), onClick = {
                scope.launch {
                    runCatching { contentViewModel.deleteAnnotation(excerpt.id) }
                        .onSuccess { deletingExcerpt = null; editingExcerpt = null; announce("حُذف المقتطف") }
                        .onFailure { deletingExcerpt = null; announce("تعذّر حذف المقتطف") }
                }
            })
            AtharMenuItem("إلغاء", onClick = { deletingExcerpt = null })
        }
    }

    }
}

private data class DownloadRemoval(val id: String, val title: String, val sizeLabel: String)

/** A flow's first value, told apart from "nothing yet": [Emitted] wraps it so a null value still counts. */
private class Emitted<T>(val value: T)

@Composable
private fun <T> collectEmitted(key: Any, flow: () -> kotlinx.coroutines.flow.Flow<T>): Emitted<T>? {
    val wrapped = remember(key) { flow().map { Emitted(it) } }
    return wrapped.collectAsStateWithLifecycle(initialValue = null).value
}

@Composable
private fun UnavailableRoute(title: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        IconButton(onClick = onBack) { Icon(AtharIcons.Back, "رجوع") }
        Text(title)
        TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
    }
}

@Composable
private fun SectionPlaceholder(
    title: String,
    route: String,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AtharTheme.colors.canvas),
    ) {
        AtharTopBar(
            title = title,
            onSettings = onSettings,
            showAppIcon = true,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("section_$route")
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = title,
                fontFamily = AtharEditorialFontFamily,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "هذه الوجهة متصلة بالتنقل الآن، وسيأتي محتواها في الشاشة التالية.",
                modifier = Modifier.padding(top = 8.dp),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MiniPlayer(
    audio: AudioUi,
    playing: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Same ground as the page: the top divider and the progress line are
            // what separate it, not a slab of a different colour.
            .background(AtharTheme.colors.canvas)
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("mini_player"),
    ) {
        HorizontalDivider(
            thickness = 0.7.dp,
            color = AtharTheme.colors.divider.copy(alpha = 0.42f),
        )
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(48.dp).testTag("mini_player_toggle"),
            ) {
                Icon(
                    imageVector = if (playing) AtharIcons.Pause else AtharIcons.Play,
                    contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل",
                    tint = AtharTheme.colors.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = audio.sourceTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = AtharTextSize.Body,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (audio.title != audio.sourceTitle) Text(
                    text = audio.title,
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp).testTag("mini_player_close"),
            ) {
                Icon(
                    imageVector = AtharIcons.Close,
                    contentDescription = "إغلاق المشغّل",
                    tint = AtharTheme.colors.secondaryText,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        AudioProgressBar(
            progress = progress,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SettingsScreen(
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
            HorizontalDivider(color = AtharTheme.colors.divider)
            // Storage lives on its own screen now: the budget, the transfers in flight
            // and what is pinned are one subject, and a sheet could only hold the first.
            SettingsRow(
                AtharIcons.Download,
                "التنزيلات والتخزين",
                "المحفوظ: ${arabicDigits(pinnedCount)} · المؤقّت: ${com.atharchive.data.byteSize(cacheBytes)} · الحد: ${cacheBudgetLabel(cacheBudgetBytes)}",
                onClick = onDownloads,
            )
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(
                AtharIcons.Share,
                "تصدير بياناتي",
                "المقتطفات ومواضع القراءة والمفضّلة",
                onClick = onExportUserData,
                tag = "settings_export_user_data",
            )
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(AtharIcons.Repeat, "تحديث المحتوى", syncLabel, onClick = onSync, tag = "settings_sync_content")
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(
                AtharIcons.Download,
                "استيراد بياناتي",
                "يدمج الملف ويحافظ على التعديلات المحلية الأحدث",
                onClick = onImportUserData,
                tag = "settings_import_user_data",
            )
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(
                AtharIcons.Download,
                "استقبال كتب",
                "من ملف أرسله جهاز آخر (مشاركة سريعة أو بلوتوث)، بلا إنترنت",
                onClick = onReceiveBooks,
                tag = "settings_receive_books",
            )
            HorizontalDivider(color = AtharTheme.colors.divider)
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
internal fun citedText(
    text: String, title: String, author: String, volume: Int?, page: Int?, url: String,
    volumeUnit: String? = null,
    /** A poem's verse numbers: «البيت ١٢», «الأبيات ١٢–١٥». */
    verses: IntRange? = null,
): String {
    val digits = { n: Int -> com.atharchive.ui.components.arabicDigits(n) }
    val source = listOfNotNull(
        title.ifBlank { null }, author.ifBlank { null },
        volume?.let { com.atharchive.feature.reader.volumeLabel(it, volumeUnit) },
        page?.let { "ص${digits(it)}" },
        verses?.let { if (it.first == it.last) "البيت ${digits(it.first)}" else "الأبيات ${digits(it.first)}–${digits(it.last)}" },
    ).joinToString("، ")
    // a blank line between the passage and its source, as in مشاركة
    val tail = listOfNotNull(source.ifBlank { null }?.let { "— $it" }, url.ifBlank { null }).joinToString("\n")
    return if (tail.isEmpty()) text else "$text\n\n$tail"
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Athar", text))
}

private fun shareText(context: Context, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "مشاركة النص",
        ),
    )
}

private fun ExcerptUi.shareText(): String = buildString {
    append(text)
    append("\n\n— ")
    append(sourceTitle)
    if (sourceAuthor.isNotBlank()) {
        append("، ")
        append(sourceAuthor)
    }
    locationLabel?.let {
        append("، ")
        append(it)
    }
    if (sourceUrl.isNotBlank()) { append("\n"); append(sourceUrl) }
}

/** A tapped recording becomes the now-playing item at position zero. */

private const val LibraryUpdatingMessage = "جارٍ تحديث المكتبة بالمحتوى الجديد…"
