package com.atharchive.ui

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
import androidx.compose.material3.ModalBottomSheet
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
import com.atharchive.feature.adhkar.AdhkarContent
import com.atharchive.feature.adhkar.AdhkarScreen
import com.atharchive.feature.downloads.StorageUi
import com.atharchive.feature.downloads.DownloadsUiState
import com.atharchive.feature.issues.IssuesScreen
import com.atharchive.feature.audio.AudioScreen
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
import com.atharchive.feature.library.LibraryScreen
import com.atharchive.feature.reader.ReaderSearchTarget
import com.atharchive.feature.sections.AtharSection
import com.atharchive.feature.search.SearchScreen
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
private data object LibraryRoute : NavKey

@Serializable
private data object PlayerRoute : NavKey

@Serializable
private data class PoemReaderRoute(val poemId: String, val targetOrdinal: Int? = null) : NavKey

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
private data class DownloadsRoute(val targetId: String? = null) : NavKey

@Serializable
private data object AdhkarRoute : NavKey

@Serializable
private data object AppearanceRoute : NavKey

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AtharApp(
    downloadTarget: String? = null,
    onDownloadTargetHandled: () -> Unit = {},
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    startPlace: com.atharchive.data.StartPlace = com.atharchive.data.StartPlace.Books,
    onStartPlaceChange: (com.atharchive.data.StartPlace) -> Unit = {},
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    contentViewModel: AtharContentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // Only the first composition matters: later changes of the setting apply at next launch.
    val backStack = rememberNavBackStack(
        *(startPlace.route?.takeIf { it != "books" }?.let { arrayOf<NavKey>(BooksCatalogRoute, SectionRoute(it)) }
            ?: arrayOf<NavKey>(BooksCatalogRoute)),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var booksScrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }
    val audioState by contentViewModel.audio.collectAsStateWithLifecycle()
    val playbackState by contentViewModel.playback.state.collectAsStateWithLifecycle()
    val syncState by contentViewModel.syncState.collectAsStateWithLifecycle()
    val nowPlaying = audioState.nowPlaying
    var editingExcerpt by remember { mutableStateOf<ExcerptUi?>(null) }
    val booksState by contentViewModel.books.collectAsStateWithLifecycle()
    val articlesState by contentViewModel.articles.collectAsStateWithLifecycle()
    val issuesState by contentViewModel.issues.collectAsStateWithLifecycle()
    val poetryState by contentViewModel.poetry.collectAsStateWithLifecycle()
    val libraryState by contentViewModel.personalLibraryState.collectAsStateWithLifecycle()
    val kannashahState by contentViewModel.kannashahState.collectAsStateWithLifecycle()
    val searchState by contentViewModel.search.collectAsStateWithLifecycle()
    val downloadItems by contentViewModel.downloads.collectAsStateWithLifecycle()
    LaunchedEffect(contentViewModel) {
        contentViewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(playbackState.error) { playbackState.error?.let { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(syncState) {
        if (syncState is com.atharchive.data.ContentSyncUiState.Failed &&
            snackbarHostState.showSnackbar("تعذّر تحديث المحتوى؛ النصوص المحفوظة متاحة", "إعادة المحاولة", withDismissAction = true) == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            contentViewModel.sync()
        }
    }
    val storageStatus by contentViewModel.storageStatus.collectAsStateWithLifecycle()
    val cacheBytes by contentViewModel.cacheBytes.collectAsStateWithLifecycle()
    val cacheBudgetBytes by contentViewModel.cacheBudgetBytes.collectAsStateWithLifecycle()
    val pinnedCount by contentViewModel.pinnedCount.collectAsStateWithLifecycle()

    fun announce(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // StartupTimingMetric can only measure "interactive library" when Compose tells the
    // platform that the asynchronous Room-backed catalogue has reached the screen.
    ReportDrawnWhen { booksState.loaded }
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
                    contentViewModel.exportUserData(output)
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
            }.onSuccess { report ->
                announce("استُورد ${arabicDigits(report.imported)} سجلًا")
            }.onFailure {
                announce("الملف ليس تصديرًا صالحًا من أثر")
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
    // anything else starts, pauses or resumes, and only a fresh start asks for notifications.
    var pendingRemoval by remember { mutableStateOf<DownloadRemoval?>(null) }
    fun onDownloadTap(id: String, title: String, sizeLabel: String, downloaded: Boolean, transfer: com.atharchive.feature.downloads.TransferUi?) {
        if (downloaded) {
            pendingRemoval = DownloadRemoval(id, title, sizeLabel)
            return
        }
        contentViewModel.toggleDownload(id)
        if (transfer == null) requestNotificationPermission()
    }
    pendingRemoval?.let { removal ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("إزالة التنزيل؟") },
            text = {
                Text(
                    "«${removal.title}» يبقى في الأرشيف ويمكنك تنزيله مجددًا." +
                        if (removal.sizeLabel.isBlank()) "" else " يُحرَّر ${removal.sizeLabel}.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    contentViewModel.removeDownload(removal.id)
                    pendingRemoval = null
                }) { Text("إزالة") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("إلغاء") } },
        )
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
        if (downloadTarget != null) { backStack.add(DownloadsRoute(downloadTarget)); onDownloadTargetHandled() }
    }
    val tabState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val inReader = backStack.lastOrNull() is ReaderRoute
    // The player owns the whole screen, like the reader: no bottom chrome under it.
    val inPlayer = backStack.lastOrNull() is PlayerRoute
    // Poetry reads full-screen too: the poem is the only thing on it.
    val inPoemReader = backStack.lastOrNull() is PoemReaderRoute
    val currentDestination = when (val current = backStack.lastOrNull { it is SectionRoute || it == BooksCatalogRoute }) {
        is SectionRoute -> AtharDestination.fromRoute(current.destination)
        else -> AtharDestination.Books
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
        backStack.removeLastOrNull()
    }

    fun navigateToBooksRoot() {
        while (backStack.size > 1) backStack.removeLastOrNull()
    }

    fun navigateTo(destination: AtharDestination) {
        if (destination == AtharDestination.Books) {
            val booksAlreadySelected = currentDestination == AtharDestination.Books
            navigateToBooksRoot()
            if (booksAlreadySelected) booksScrollToTopRequest++
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (inReader || inPlayer || inPoemReader) return@Scaffold
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
            AtharAppMenuEntry("محفوظاتي", AtharIcons.Bookmark) { backStack.add(LibraryRoute) },
            AtharAppMenuEntry(AtharSection.Articles.label, AtharSection.Articles.icon) { backStack.add(SubSectionRoute(AtharSection.Articles.route)) },
            AtharAppMenuEntry(AtharSection.Issues.label, AtharSection.Issues.icon) { backStack.add(SubSectionRoute(AtharSection.Issues.route)) },
            AtharAppMenuEntry("الأذكار", AtharIcons.Adhkar) { backStack.add(AdhkarRoute) },
            AtharAppMenuEntry("التنزيلات", AtharIcons.Download) { backStack.add(DownloadsRoute()) },
            AtharAppMenuEntry("الإعدادات", AtharIcons.Settings) { openSettings() },
        )
        androidx.compose.runtime.CompositionLocalProvider(LocalAtharAppMenu provides appMenu) {
        com.atharchive.ui.components.ProvideAtharTextToolbar {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
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
                        onSaveClick = { contentViewModel.toggleReadLater(it.id) },
                        scrollToTopRequest = booksScrollToTopRequest,
                    )
                }
                entry<LibraryRoute> {
                    LibraryScreen(
                        state = libraryState,
                        onBack = ::goBack,
                        onOpenWork = { work ->
                            if (work.collection == "audio") { contentViewModel.playAudio(work.id.removePrefix("audio:")); backStack.add(PlayerRoute) }
                            else if (work.collection == "poem") {
                                backStack.add(PoemReaderRoute(work.id))
                            } else {
                                backStack.add(ReaderRoute(work.id))
                            }
                        },
                        onStatusChange = { work, shelf ->
                            contentViewModel.setLibraryStatus(work.id, shelf)
                        },
                        onCreateCollection = contentViewModel::createCollection,
                        onPlayCollection = { contentViewModel.playAudioPlaylist(it.id); backStack.add(PlayerRoute) },
                        onDeleteCollection = { contentViewModel.deleteCollection(it.id) },
                        onAddToCollection = { work, collection ->
                            contentViewModel.addToCollection(collection.id, work.id)
                        },
                        onRemoveFromCollection = { work, collection ->
                            contentViewModel.removeFromCollection(collection.id, work.id)
                        },
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
                        onDownloads = { backStack.add(DownloadsRoute()) },
                        onSync = contentViewModel::sync,
                        syncLabel = when (syncState) {
                            com.atharchive.data.ContentSyncUiState.Checking -> "جارٍ التحديث…"
                            com.atharchive.data.ContentSyncUiState.UpToDate -> "المحتوى محدّث"
                            is com.atharchive.data.ContentSyncUiState.Failed -> "تعذّر التحديث؛ أعد المحاولة"
                            else -> "تحقّق من المحتوى الجديد"
                        },
                        onExportUserData = { exportUserData.launch("athar-user-data.jsonl") },
                        onImportUserData = { importUserData.launch(arrayOf("application/json", "text/plain")) },
                        onBack = ::goBack,
                    )
                }
                // The reader owns the whole screen: no bottom navigation inside a book.
                entry<ReaderRoute> { route ->
                    val readerEmitted = collectEmitted(route.bookId) { contentViewModel.readerState(route.bookId) }
                    val readerState = readerEmitted?.value
                    val readerWindow = remember(route.bookId) { contentViewModel.readerWindow(route.bookId) }
                    LaunchedEffect(route.bookId, route.targetOrdinal) {
                        contentViewModel.openReader(route.bookId, route.targetOrdinal)
                    }
                    DisposableEffect(route.bookId) {
                        onDispose { contentViewModel.closeReader(route.bookId) }
                    }
                    // "Not emitted yet" is loading, not missing: the flow's first value can wait
                    // behind a write, and «غير متاح» used to flash on every slow open.
                    if (readerEmitted == null) Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = ::goBack) { Text("رجوع") }
                        com.atharchive.ui.components.AtharLoading(
                            "جارٍ تحميل النص…", Modifier.padding(top = 120.dp), tag = "reader_loading",
                        )
                    } else if (readerState == null) Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = ::goBack) { Text("رجوع") }
                        com.atharchive.ui.components.AtharEmptyState(
                            "المصدر غير متاح بعد", "جارٍ تحميل الفهرس؛ إن استمر ذلك فتحقّق من الاتصال وحدّث المحتوى.",
                            "reader_source_unavailable", 24.dp,
                        )
                        TextButton(onClick = { contentViewModel.sync(); contentViewModel.openReader(route.bookId, route.targetOrdinal) }) { Text("إعادة المحاولة") }
                    }
                    readerState?.let { state ->
                      androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        ReaderScreen(
                            state = state,
                            window = readerWindow,
                            initialOrdinal = route.targetOrdinal,
                            onSearch = { contentViewModel.searchInBook(route.bookId, it) },
                            onPageJump = { page, volume -> contentViewModel.findPrintedPage(route.bookId, page, volume) },
                            onAnchorJump = { contentViewModel.findHeadingAnchor(route.bookId, it) },
                            onLoadSelectionBlocks = { contentViewModel.selectionBlocks(route.bookId, it) },
                            onPlayAudio = { contentViewModel.playWorkAudio(route.bookId, it.id) },
                            playingAudioId = playbackState.id,
                            audioPlayIntent = playbackState.playWhenReady,
                            onOpenEntity = { backStack.add(ReaderRoute(it)) },
                            onBack = ::goBack,
                            onCopySelection = { selection ->
                                copyText(context, selection.quotedText)
                            },
                            onShareSelection = { selection ->
                                shareText(
                                    context,
                                    selection.quotedText + "\n\n— ${state.bookTitle}، ${state.author}\n" +
                                        state.sourceUrl,
                                )
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
                    val poemState = poemEmitted?.value
                    LaunchedEffect(route.poemId) { contentViewModel.openReader(route.poemId, route.targetOrdinal) }
                    DisposableEffect(route.poemId) {
                        onDispose { contentViewModel.closeReader(route.poemId) }
                    }
                    if (poemEmitted == null) Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = ::goBack) { Text("رجوع") }
                        com.atharchive.ui.components.AtharLoading("جارٍ تحميل القصيدة…", Modifier.padding(top = 120.dp), tag = "poem_loading")
                    } else if (poemState == null) UnavailableRoute("القصيدة غير متاحة بعد", ::goBack) { contentViewModel.sync(); contentViewModel.openReader(route.poemId, route.targetOrdinal) }
                    poemState?.let { state -> PoemReaderScreen(
                            state = state,
                            onBack = ::goBack,
                            settings = readerSettings,
                            onSettingsChange = onReaderSettingsChange,
                            playback = playbackState,
                            onPlayAudio = { contentViewModel.playWorkAudio(route.poemId, it.id) },
                            onToggleAudio = contentViewModel.playback::toggle,
                            onSpeed = contentViewModel.playback::speed,
                            onRepeat = contentViewModel.playback::repeat,
                            onOpenReader = { backStack.add(ReaderRoute(route.poemId)) },
                            initialOrdinal = route.targetOrdinal,
                            onPositionChange = { contentViewModel.onReaderPosition(route.poemId, it) },
                            onRetry = { contentViewModel.openReader(route.poemId, route.targetOrdinal) },
                        ) }
                }
                entry<PlayerRoute> {
                    if (nowPlaying == null) UnavailableRoute("لا يوجد تسجيل متاح في المشغّل", ::goBack) { contentViewModel.sync() }
                    nowPlaying?.let { playing ->
                        PlayerScreen(
                            nowPlaying = playing,
                            queue = playbackState.queueIds.mapNotNull { id -> audioState.recordings.firstOrNull { it.id == id } },
                            playlists = audioState.playlists,
                            collections = audioState.collections,
                            onCreateCollection = {
                                contentViewModel.createCollectionAndAdd(it, "audio:${playing.audio.id}")
                                announce("أُضيف إلى «$it»")
                            },
                            onCollapse = ::goBack,
                            onToggle = contentViewModel.playback::toggle,
                            onSelect = { contentViewModel.playAudio(it.id,
                                playbackState.queueIds.mapNotNull { id -> audioState.recordings.firstOrNull { it.id == id } }) },
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
                            onAddToPlaylist = {
                                contentViewModel.addToCollection(it.id, "audio:${playing.audio.id}")
                                announce("أُضيف إلى «${it.name}»")
                            },
                        )
                    }
                }
                entry<SubSectionRoute> { route ->
                    when (AtharSection.entries.first { it.route == route.section }) {
                        AtharSection.Articles -> ArticlesScreen(
                            state = articlesState,
                            onSettings = ::openSettings,
                            onArticleClick = { backStack.add(ReaderRoute(it.id)) },
                            onSaveClick = { contentViewModel.toggleReadLater(it.id) },
                            onDownloadClick = { onDownloadTap(it.id, it.title, it.sizeLabel, it.downloaded, it.transfer) },
                            onBack = ::goBack,
                        )

                        AtharSection.Issues -> IssuesScreen(
                            state = issuesState,
                            onSettings = ::openSettings,
                            onIssueClick = { backStack.add(ReaderRoute(it.id)) },
                            onSaveClick = { contentViewModel.toggleReadLater(it.id) },
                            onDownloadClick = { onDownloadTap(it.id, it.question, it.sizeLabel, it.downloaded, it.transfer) },
                            onBack = ::goBack,
                        )


                    }
                }
                entry<AppearanceRoute> {
                    AppearanceScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        startPlace = startPlace,
                        onStartPlaceChange = onStartPlaceChange,
                        reader = readerSettings,
                        onReaderChange = onReaderSettingsChange,
                        onBack = ::goBack,
                    )
                }
                entry<AdhkarRoute> {
                    // ponytail: saved أذكار are ids in SharedPreferences; move to Room if they need sync/backup.
                    val prefs = remember { context.getSharedPreferences("adhkar", Context.MODE_PRIVATE) }
                    var savedIds by remember { mutableStateOf(prefs.getStringSet("saved", null).orEmpty()) }
                    val adhkar = remember(savedIds) {
                        AdhkarContent.copy(entries = AdhkarContent.entries.mapValues { (_, list) ->
                            list.map { it.copy(saved = it.id in savedIds) }
                        })
                    }
                    AdhkarScreen(
                        state = adhkar,
                        onSettings = ::openSettings,
                        onOpenBabs = {},
                        onCopy = { copyText(context, it.text); announce("نُسخ الذكر") },
                        onSave = { dhikr ->
                            savedIds = if (dhikr.id in savedIds) savedIds - dhikr.id else savedIds + dhikr.id
                            prefs.edit().putStringSet("saved", savedIds).apply()
                        },
                        onBack = ::goBack,
                    )
                }
                entry<DownloadsRoute> { route ->
                    LaunchedEffect(Unit) { contentViewModel.refreshCacheBytes() }
                    DownloadsScreen(
                        targetId = route.targetId,
                        state = DownloadsUiState(
                            transfers = downloadItems.filter { it.transfer != null },
                            downloaded = downloadItems.filter { it.transfer == null },
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
                        onPauseResume = { contentViewModel.pauseResumeDownload(it.id) },
                        onCancel = { contentViewModel.removeDownload(it.id) },
                        onRemove = {
                            // A finished download asks first; cancelling a running one loses nothing.
                            if (it.transfer == null) pendingRemoval = DownloadRemoval(it.id, it.title, it.sizeLabel)
                            else contentViewModel.removeDownload(it.id)
                        },
                    )
                }
                entry<SectionRoute> { route ->
                    tabState.SaveableStateProvider(route.destination) {
                    when (AtharDestination.fromRoute(route.destination)) {
                        AtharDestination.Poetry -> PoetryScreen(
                            state = poetryState,
                            onSettings = ::openSettings,
                            onPoemClick = { backStack.add(PoemReaderRoute(it.id)) },
                            onDownloadClick = { onDownloadTap(it.id, it.title, it.sizeLabel, it.downloaded, it.transfer) },
                            onSaveClick = { contentViewModel.toggleReadLater(it.id) },
                        )

                        AtharDestination.Audio -> AudioScreen(
                            state = audioState,
                            onSettings = ::openSettings,
                            onPlay = { contentViewModel.playAudio(it.id) },
                            onMore = { openAudioSource(it) },
                            onSave = { contentViewModel.toggleReadLater("audio:${it.id}") },
                            onDownload = {
                                // Same rule as text rows: a finished download asks before it is deleted.
                                if (it.downloaded) pendingRemoval = DownloadRemoval("audio:${it.id}", it.title, it.sizeLabel)
                                else {
                                    contentViewModel.toggleAudioDownload(it.id)
                                    requestNotificationPermission()
                                }
                            },
                        )

                        AtharDestination.Search -> SearchScreen(
                            state = searchState,
                            onSettings = ::openSettings,
                            onQueryChange = contentViewModel::setSearchQuery,
                            onResultsDrawn = contentViewModel::reportSearchResultsDrawn,
                            inputQuery = contentViewModel.searchQuery.collectAsStateWithLifecycle().value,
                            onLoadMore = contentViewModel::loadMoreSearch,
                            onFiltersChange = contentViewModel::setSearchFilters,
                            onOpenResult = { hit ->
                                contentViewModel.rememberCurrentSearch()
                                if (hit.type == com.atharchive.feature.search.SearchResultType.Poem) {
                                    backStack.add(PoemReaderRoute(hit.entityId, hit.ordinal))
                                } else {
                                    backStack.add(
                                        ReaderRoute(
                                            bookId = hit.entityId,
                                            targetOrdinal = hit.ordinal,
                                            matchStart = hit.sourceMatchStart,
                                            matchEnd = hit.sourceMatchEnd,
                                        ),
                                    )
                                }
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
                        )

                        AtharDestination.Kannashah -> KannashahScreen(
                            state = kannashahState,
                            onSettings = ::openSettings,
                            onCopy = {
                                copyText(context, it.text)
                                announce("نُسخ المقتطف")
                            },
                            onShare = {
                                shareText(context, it.shareText())
                            },
                            onMore = { editingExcerpt = it },
                            onOpenSource = {
                                backStack.add(ReaderRoute(it.entityId, targetOrdinal = it.sourceOrdinal))
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
    }

    editingExcerpt?.let { excerpt ->
        var note by rememberSaveable(excerpt.id) { mutableStateOf(excerpt.comment.orEmpty()) }
        ModalBottomSheet(
            onDismissRequest = { editingExcerpt = null },
            containerColor = AtharTheme.colors.canvas,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "تعليق على المقتطف",
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = excerpt.text,
                    modifier = Modifier.padding(top = 8.dp),
                    color = AtharTheme.colors.secondaryText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(2_000) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .testTag("kannashah_note_editor"),
                    label = { Text("تعليقي") },
                    minLines = 3,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { contentViewModel.deleteAnnotation(excerpt.id) }
                                    .onSuccess { editingExcerpt = null; announce("حُذف المقتطف") }
                                    .onFailure { announce("تعذّر الحفظ؛ بقي التعليق مفتوحًا") }
                            }
                        },
                        modifier = Modifier.testTag("kannashah_delete_annotation"),
                    ) {
                        Text("حذف")
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { contentViewModel.editAnnotation(excerpt.id, note) }
                                    .onSuccess { editingExcerpt = null; announce("حُفظ التعليق") }
                                    .onFailure { announce("تعذّر الحفظ؛ بقي التعليق مفتوحًا") }
                            }
                        },
                        modifier = Modifier.testTag("kannashah_save_note"),
                    ) {
                        Text("حفظ")
                    }
                }
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
        TextButton(onClick = onBack) { Text("رجوع") }
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
) {
    Column(
        modifier = Modifier
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
                    fontSize = 14.sp,
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
                "المقتطفات ومواضع القراءة والرفوف والمجموعات",
                onClick = onExportUserData,
                tag = "settings_export_user_data",
            )
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(AtharIcons.Download, "تحديث المحتوى", syncLabel, onClick = onSync, tag = "settings_sync_content")
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(
                AtharIcons.Download,
                "استيراد بياناتي",
                "يدمج الملف ويحافظ على التعديلات المحلية الأحدث",
                onClick = onImportUserData,
                tag = "settings_import_user_data",
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
