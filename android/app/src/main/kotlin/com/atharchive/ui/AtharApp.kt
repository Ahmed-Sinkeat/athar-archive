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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.atharchive.feature.audio.AudioProgressBar
import com.atharchive.feature.audio.AudioUi
import com.atharchive.feature.kannashah.ExcerptUi
import com.atharchive.feature.sections.AtharSection
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
import com.atharchive.ui.theme.AtharTheme
import com.atharchive.ui.theme.ThemeMode
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.atharchive.data.AtharContentViewModel
import androidx.core.content.ContextCompat

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
    bar: List<AtharDestination> = AtharDestination.DefaultBar,
    onBarChange: (List<AtharDestination>) -> Unit = {},
    onResetSettings: () -> Unit = {},
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
    val scrollToTopRequestHolder = rememberSaveable { mutableIntStateOf(0) }
    var scrollToTopRequest by scrollToTopRequestHolder
    val audioStateHolder = contentViewModel.audio.collectAsStateWithLifecycle()
    val audioState by audioStateHolder
    LaunchedEffect(Unit) {
        // One-time fold: old adhkar prefs ids become favourites, then the key is dropped.
        val prefs = context.getSharedPreferences("adhkar", Context.MODE_PRIVATE)
        val old = prefs.getStringSet("saved", null).orEmpty().map { "dhikr:$it" }
        if (contentViewModel.foldFavourites(old)) prefs.edit().remove("saved").apply()
    }
    val playbackStateHolder = contentViewModel.playback.state.collectAsStateWithLifecycle()
    val playbackState by playbackStateHolder
    val syncStateHolder = contentViewModel.syncState.collectAsStateWithLifecycle()
    val syncState by syncStateHolder
    // Playback ticks twice a second: these are read where they are drawn, or through derived
    // states that change only when the value does, so the whole app no longer recomposes per tick.
    val nowPlayingHolder = remember { derivedStateOf { audioState.nowPlaying } }
    val nowPlaying by nowPlayingHolder
    val hasRecordings by remember { derivedStateOf { audioState.recordings.isNotEmpty() } }
    val playbackError by remember { derivedStateOf { playbackState.error } }
    LaunchedEffect(hasRecordings) {
        if (hasRecordings) contentViewModel.foldAudioFavourites(audioState.recordings)
    }
    val actionAnchor = remember { mutableStateOf(Rect.Zero) }
    val editorAnchorHolder = remember { mutableStateOf(Rect.Zero) }
    var editorAnchor by editorAnchorHolder
    val editingExcerptHolder = remember { mutableStateOf<ExcerptUi?>(null) }
    var editingExcerpt by editingExcerptHolder
    // An excerpt for الكناشة to scroll to once it opens (from a note's «في الكناشة»).
    val kannashahFocusHolder = remember { mutableStateOf<String?>(null) }
    var kannashahFocus by kannashahFocusHolder
    // One's own dhikr being added or edited (from الأذكار, or from a selection in a reader).
    val userAdhkar = remember { com.atharchive.data.UserAdhkarStore.get(context) }
    val adhkarRemindersHolder = remember { mutableStateOf(com.atharchive.data.AdhkarReminders.all(context)) }
    val adhkarOpenHolder = remember { mutableStateOf<String?>(null) }
    var adhkarOpen by adhkarOpenHolder
    val userAdhkarStateHolder = userAdhkar.state.collectAsStateWithLifecycle()
    val userAdhkarState by userAdhkarStateHolder
    val editingDhikrHolder = remember { mutableStateOf<com.atharchive.data.UserDhikr?>(null) }
    var editingDhikr by editingDhikrHolder
    // Read at the call, not here: route entries keep the first composition's closures (Nav3).
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    // From a selection there is no tapped control to point at: the popover sits high in the middle.
    fun addDhikr(text: String, source: String, lists: Set<String> = emptySet(), anchor: Rect? = null) {
        val windowSize = windowInfo.containerSize
        editorAnchor = anchor ?: Rect(androidx.compose.ui.geometry.Offset(windowSize.width / 2f, windowSize.height * 0.22f), Size(1f, 1f))
        editingDhikr = com.atharchive.data.UserDhikr(com.atharchive.data.UserAdhkarStore.newId(), text, 1, source.ifBlank { null }, lists)
    }
    val deletingExcerptHolder = remember { mutableStateOf<ExcerptUi?>(null) }
    var deletingExcerpt by deletingExcerptHolder
    // Each screen collects its own state inside its entry: a flow collected here runs from
    // launch, and every write to `entity` re-reads the whole catalog once per running flow.
    val booksStateHolder = contentViewModel.books.collectAsStateWithLifecycle()
    val booksState by booksStateHolder
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
    val retainedIdsHolder = contentViewModel.retainedEntityIds.collectAsStateWithLifecycle()
    val recentQueriesHolder = contentViewModel.recentQueries.collectAsStateWithLifecycle()
    val recentQueries by recentQueriesHolder
    val cacheBytesHolder = contentViewModel.cacheBytes.collectAsStateWithLifecycle()
    val cacheBytes by cacheBytesHolder
    val cacheBudgetBytesHolder = contentViewModel.cacheBudgetBytes.collectAsStateWithLifecycle()
    val cacheBudgetBytes by cacheBudgetBytesHolder
    val pinnedCountHolder = contentViewModel.pinnedCount.collectAsStateWithLifecycle()
    val pinnedCount by pinnedCountHolder

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
    // Once, on first opening; the same words stay in الإعدادات › التراخيص وحقوق النشر.
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
    val backupPrefs = remember { context.getSharedPreferences("backup", android.content.Context.MODE_PRIVATE) }
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
                backupPrefs.edit().putLong("exported", System.currentTimeMillis()).apply()
                announce("اكتمل تصدير بياناتك")
            }.onFailure {
                announce("تعذّر تصدير البيانات")
            }
        }
    }
    // 4.13: notes live only on this phone; remind to export them after a month without one (then weekly). no per-note "unsaved" count.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8_000)
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        // The month starts when the reminder first sees notes, not at install.
        val since = backupPrefs.getLong("exported", 0L).takeIf { it > 0 }
            ?: backupPrefs.getLong("seen", 0L).takeIf { it > 0 }
            ?: now.also { backupPrefs.edit().putLong("seen", it).apply() }
        if (now - since < 30 * day || now - backupPrefs.getLong("nagged", 0L) < 7 * day || !contentViewModel.hasNotes()) return@LaunchedEffect
        backupPrefs.edit().putLong("nagged", now).apply()
        if (snackbarHostState.showSnackbar("ملاحظاتك محفوظة على هذا الهاتف فقط. صدّر نسخة منها.", "تصدير", withDismissAction = true,
                duration = androidx.compose.material3.SnackbarDuration.Long) == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            exportUserData.launch("athar-user-data.jsonl")
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
    val pendingRemovalHolder = remember { mutableStateOf<DownloadRemoval?>(null) }
    var pendingRemoval by pendingRemovalHolder
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
        // "book:<id>[:<ordinal>]" opens the reader, for adb phone checks
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
    /** A tab with a screen of its own opens that screen; the others are sections over the books root. */
    fun routeOf(destination: AtharDestination): NavKey = when (destination) {
        AtharDestination.Mutoon -> MutoonRoute
        AtharDestination.Adhkar -> AdhkarRoute
        AtharDestination.Persons -> PersonsRoute()
        AtharDestination.Issues -> SubSectionRoute(AtharSection.Issues.route)
        AtharDestination.Settings -> SettingsRoute
        else -> SectionRoute(destination.route)
    }
    // A function so navigateTo, called from route entries that keep their first closures, reads the live stack.
    fun destinationNow(): AtharDestination? = when (val current = backStack.lastOrNull()) {
        is SectionRoute -> AtharDestination.fromRoute(current.destination)
        BooksCatalogRoute -> AtharDestination.Books
        else -> AtharDestination.entries.firstOrNull { it != AtharDestination.Books && routeOf(it) == current }
    }
    val currentDestination = destinationNow()

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
    var openingHit by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun openHit(hit: com.atharchive.feature.search.SearchHitUi, query: String) {
        val poem = hit.type == com.atharchive.feature.search.SearchResultType.Poem
        // The latest tap wins: a second one used to be ignored while the first was located (a dead tap),
        // and before that opened the book twice.
        openingHit?.cancel()
        openingHit = null
        if (hit.url == null) {
            backStack.add(if (poem) PoemReaderRoute(hit.entityId, hit.ordinal, query) else ReaderRoute(hit.entityId, hit.ordinal, hit.sourceMatchStart, hit.sourceMatchEnd))
            return
        }
        // A book passage the index placed opens at once on its first block; the reader places the match.
        val pgFirst = hit.pg?.substringBefore('-')?.toIntOrNull()
        if (!poem && pgFirst != null) {
            backStack.add(ReaderRoute(hit.entityId, pgFirst, locate = OnlineLocate(hit.url, query, hit.excerpt, hit.pg)))
            return
        }
        val from = backStack.lastOrNull()
        openingHit = scope.launch {
            // A poem that is a work of its own opens at once and the reader finds the verse by its
            // words (the walk here fetched its frames first, then the reader fetched them again).
            if (poem && !contentViewModel.isDiwan(hit.entityId)) {
                backStack.add(PoemReaderRoute(hit.entityId, null, query, hit.excerpt))
                return@launch
            }
            // Locating an online hit fetches the book's frames first: say so.
            val note = launch { snackbarHostState.showSnackbar("جارٍ فتح الموضع…", duration = androidx.compose.material3.SnackbarDuration.Indefinite) }
            val (ordinal, start, end) = try { contentViewModel.locateOnlineHit(hit, query) } finally { note.cancel() }
            // Left meanwhile (back, another section): the book no longer jumps open over what is on screen.
            if (backStack.lastOrNull() != from) return@launch
            val found = ordinal != null && end > start
            backStack.add(if (poem) PoemReaderRoute(hit.entityId, ordinal, query, hit.excerpt) else ReaderRoute(hit.entityId, ordinal, start.takeIf { found }, end.takeIf { found }))
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
        if (destination == destinationNow()) {
            scrollToTopRequest++
        } else if (destination == AtharDestination.Books) {
            navigateToBooksRoot()
        } else {
            navigateToBooksRoot()
            backStack.add(routeOf(destination))
        }
    }

    LaunchedEffect(storageStatus?.lowStorage, storageStatus?.availableBytes) {
        if (storageStatus?.lowStorage == true) {
            announce("المساحة منخفضة؛ أوقفنا التخزين المؤقت وحافظنا على التنزيلات المثبتة")
        }
    }

    val app = AppNav(
        contentViewModel, context, scope, backStack, tabState, actionAnchor, userAdhkar,
        notificationPermission, exportUserData, importUserData, receiveBooks,
        rememberUpdatedState(themeMode), onThemeModeChange, rememberUpdatedState(style), onStyleChange, rememberUpdatedState(accent), onAccentChange,
        rememberUpdatedState(startPlace), onStartPlaceChange, rememberUpdatedState(readerSettings), onReaderSettingsChange, rememberUpdatedState(bar), onBarChange, onResetSettings,
        goBack = ::goBack, openSettings = ::openSettings, replaceRoute = ::replaceRoute, navigateTo = ::navigateTo,
        openHit = ::openHit, sectionSearch = ::sectionSearch, onDownloadTap = ::onDownloadTap, announce = ::announce,
        openExcerptNote = ::openExcerptNote, openAudioSource = ::openAudioSource,
        addDhikr = { text, source, lists, anchor -> addDhikr(text, source, lists, anchor) }, sendBooks = ::sendBooks,
        booksStateHolder, scrollToTopRequestHolder, retainedIdsHolder, recentQueriesHolder, playbackStateHolder, audioStateHolder,
        nowPlayingHolder, cacheBytesHolder, cacheBudgetBytesHolder, pinnedCountHolder, syncStateHolder, userAdhkarStateHolder,
        kannashahFocusHolder, editorAnchorHolder, editingExcerptHolder, editingDhikrHolder, deletingExcerptHolder,
        pendingRemovalHolder, adhkarOpenHolder, adhkarRemindersHolder,
    )

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
                    destinations = bar,
                )
            }
        },
    ) { contentPadding ->
        // Whatever has no tab lives here, so each place has exactly one way in.
        val appMenu = AtharDestination.entries.filter { it != AtharDestination.Books && it !in bar }.map { destination ->
            // A place, not a page: it replaces the current place like a tab instead of stacking on it.
            AtharAppMenuEntry(destination.label, destination.icon, current = destination == currentDestination) { navigateTo(destination) }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalAtharAppMenu provides appMenu, LocalAtharActionAnchor provides actionAnchor,
            com.atharchive.feature.reader.LocalAddToAdhkar provides { text, source -> addDhikr(text, source) },
            com.atharchive.feature.reader.LocalDictionary provides contentViewModel.dictionary,
            com.atharchive.ui.components.LocalCatalogSearch provides contentViewModel::catalogMatches,
            com.atharchive.feature.reader.LocalOpenBookAt provides { id, ordinal -> backStack.add(ReaderRoute(id, targetOrdinal = ordinal)) }) {
        // 4.5: a tablet gets a centred column, not lines running the whole screen. one cap for every screen; per-screen two-pane layouts if tablets matter.
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxSize(),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider {
                readerEntries(app)
                audioEntries(app)
                libraryEntries(app)
                adhkarEntries(app)
                settingsEntries(app)
            },
        )
        }
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

internal data class DownloadRemoval(val id: String, val title: String, val sizeLabel: String)

/** A flow's first value, told apart from "nothing yet": [Emitted] wraps it so a null value still counts. */
internal class Emitted<T>(val value: T)

/**
 * True once when [entityId]'s text is not the one last opened here, then the new one is
 * remembered. The first opening only remembers: there is nothing to compare it with.
 */
internal fun updatedSinceSeen(context: Context, entityId: String, version: String?): Boolean {
    if (version == null) return false
    val prefs = context.getSharedPreferences("seen_versions", Context.MODE_PRIVATE)
    val seen = prefs.getString(entityId, null)
    if (seen != version) prefs.edit().putString(entityId, version).apply()
    return seen != null && seen != version
}

@Composable
internal fun <T> collectEmitted(key: Any, flow: () -> kotlinx.coroutines.flow.Flow<T>): Emitted<T>? {
    val wrapped = remember(key) { flow().map { Emitted(it) } }
    return wrapped.collectAsStateWithLifecycle(initialValue = null).value
}

@Composable
internal fun UnavailableRoute(title: String, onBack: () -> Unit, onRetry: () -> Unit) {
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

/**
 * «نسخ الكل»: the title on its own line, the whole text, then the source line and link as
 * «نسخ مع التذييل» has them. It copied the bare text (articles) or «العنوان — الشاعر» run into the
 * first verse (poems), with no source or link.
 */
internal fun wholeText(text: String, title: String, author: String, url: String): String =
    (if (title.isBlank()) "" else "$title\n\n") + citedText(text, title, author, null, null, url)

internal fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Athar", text))
}

internal fun shareText(context: Context, text: String) {
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

internal fun ExcerptUi.shareText(): String = buildString {
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
