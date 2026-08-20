package com.atharchive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.paging.compose.collectAsLazyPagingItems
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
import com.atharchive.feature.adhkar.AdhkarBabsScreen
import com.atharchive.feature.adhkar.AdhkarFixture
import com.atharchive.feature.adhkar.AdhkarScreen
import com.atharchive.feature.downloads.CacheBudget
import com.atharchive.feature.downloads.DownloadsScreen
import com.atharchive.feature.downloads.StorageUi
import com.atharchive.feature.downloads.downloadsUiState
import com.atharchive.feature.issues.IssuesFixture
import com.atharchive.feature.issues.IssuesScreen
import com.atharchive.feature.audio.AudioFixture
import com.atharchive.feature.audio.AudioScreen
import com.atharchive.feature.audio.AudioProgressBar
import com.atharchive.feature.audio.AudioUi
import com.atharchive.feature.audio.NowPlayingUi
import com.atharchive.feature.audio.PlayerScreen
import com.atharchive.feature.books.BooksScreen
import com.atharchive.feature.books.BookDownloadUi
import com.atharchive.feature.kannashah.KannashahFixture
import com.atharchive.feature.kannashah.KannashahScreen
import com.atharchive.feature.reader.ReaderScreen
import com.atharchive.feature.poemreader.PoemReaderScreen
import com.atharchive.feature.poetry.PoetryScreen
import com.atharchive.feature.library.LibraryScreen
import com.atharchive.feature.reader.ReaderSearchTarget
import com.atharchive.feature.sections.AtharSection
import com.atharchive.feature.sections.SectionsScreen
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
import com.atharchive.ui.theme.AtharTheme
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
private data object SectionsRoute : NavKey

@Serializable
private data object PlayerRoute : NavKey

@Serializable
private data class PoemReaderRoute(val poemId: String) : NavKey

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
private data object AdhkarBabsRoute : NavKey

@Serializable
private data class AdhkarBabRoute(val babId: String) : NavKey

@Serializable
private data object DownloadsRoute : NavKey

@Composable
fun AtharApp(contentViewModel: AtharContentViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(BooksCatalogRoute)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var booksScrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }
    var nowPlaying by remember { mutableStateOf(AudioFixture.nowPlaying) }
    val booksState by contentViewModel.books.collectAsStateWithLifecycle()
    val articlesState by contentViewModel.articles.collectAsStateWithLifecycle()
    val poetryState by contentViewModel.poetry.collectAsStateWithLifecycle()
    val libraryState by contentViewModel.personalLibraryState.collectAsStateWithLifecycle()
    val searchState by contentViewModel.search.collectAsStateWithLifecycle()
    val storageStatus by contentViewModel.storageStatus.collectAsStateWithLifecycle()
    val cacheBytes by contentViewModel.cacheBytes.collectAsStateWithLifecycle()
    val cacheBudgetBytes by contentViewModel.cacheBudgetBytes.collectAsStateWithLifecycle()
    val pinnedCount by contentViewModel.pinnedCount.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val inReader = backStack.lastOrNull() is ReaderRoute
    // The player owns the whole screen, like the reader: no bottom chrome under it.
    val inPlayer = backStack.lastOrNull() is PlayerRoute
    // Poetry reads full-screen too: the poem is the only thing on it.
    val inPoemReader = backStack.lastOrNull() is PoemReaderRoute
    val currentDestination = when (val current = backStack.lastOrNull()) {
        is SectionRoute -> AtharDestination.fromRoute(current.destination)
        else -> AtharDestination.Books
    }

    fun openSettings() {
        backStack.add(SettingsRoute)
    }

    fun openSections() {
        backStack.add(SectionsRoute)
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

    fun announce(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
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
                        onToggle = { nowPlaying = playing.copy(playing = !playing.playing) },
                        onOpen = { backStack.add(PlayerRoute) },
                        onClose = { nowPlaying = null },
                    )
                }
                AtharBottomBar(
                    selectedDestination = currentDestination,
                    onDestinationClick = ::navigateTo,
                )
            }
        },
    ) { contentPadding ->
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
                        onLogo = ::openSections,
                        state = booksState,
                        onSettings = { openSettings() },
                        onBookClick = { backStack.add(ReaderRoute(it.id)) },
                        onDownloadClick = {
                            if (it.download is BookDownloadUi.Available) requestNotificationPermission()
                            contentViewModel.toggleDownload(it.id)
                        },
                        onSaveClick = { contentViewModel.toggleReadLater(it.id) },
                        onLibrary = { backStack.add(LibraryRoute) },
                        scrollToTopRequest = booksScrollToTopRequest,
                    )
                }
                entry<LibraryRoute> {
                    LibraryScreen(
                        state = libraryState,
                        onBack = ::goBack,
                        onOpenWork = { work ->
                            if (work.collection == "poem") {
                                backStack.add(PoemReaderRoute(work.id))
                            } else {
                                backStack.add(ReaderRoute(work.id))
                            }
                        },
                        onStatusChange = { work, shelf ->
                            contentViewModel.setLibraryStatus(work.id, shelf)
                        },
                        onCreateCollection = contentViewModel::createCollection,
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
                        onDownloads = { backStack.add(DownloadsRoute) },
                        onBack = ::goBack,
                    )
                }
                // The reader owns the whole screen: no bottom navigation inside a book.
                entry<ReaderRoute> { route ->
                    val readerState by contentViewModel.readerState(route.bookId)
                        .collectAsStateWithLifecycle(initialValue = null)
                    val pagedBlocks = remember(route.bookId) {
                        contentViewModel.pagedReaderBlocks(route.bookId)
                    }.collectAsLazyPagingItems()
                    LaunchedEffect(route.bookId, route.targetOrdinal) {
                        contentViewModel.openReader(route.bookId, route.targetOrdinal)
                    }
                    DisposableEffect(route.bookId) {
                        onDispose { contentViewModel.closeReader(route.bookId) }
                    }
                    readerState?.let { state ->
                        ReaderScreen(
                            state = state,
                            pagedBlocks = pagedBlocks,
                            onBack = ::goBack,
                            onSaveBenefit = { },
                            onPositionChange = { contentViewModel.onReaderPosition(route.bookId, it) },
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
                entry<PoemReaderRoute> { route ->
                    val poemState by contentViewModel.poemReaderState(route.poemId)
                        .collectAsStateWithLifecycle(initialValue = null)
                    LaunchedEffect(route.poemId) { contentViewModel.openReader(route.poemId) }
                    DisposableEffect(route.poemId) {
                        onDispose { contentViewModel.closeReader(route.poemId) }
                    }
                    poemState?.let { state -> PoemReaderScreen(state = state, onBack = ::goBack) }
                }
                entry<PlayerRoute> {
                    nowPlaying?.let { playing ->
                        PlayerScreen(
                            nowPlaying = playing,
                            queue = AudioFixture.recordings,
                            playlists = AudioFixture.playlists,
                            onCollapse = ::goBack,
                            onToggle = { nowPlaying = playing.copy(playing = !playing.playing) },
                            onSelect = { nowPlaying = startPlaying(it) },
                            onSkipPrevious = { announce("التسجيل السابق") },
                            onSkipNext = { announce("التسجيل التالي") },
                            onSeek = { announce("سيتحرّك الموضع مع محرّك التشغيل") },
                            onMore = { announce("خيارات ${playing.audio.title}") },
                            onAddToPlaylist = { announce("أُضيف إلى ${it.name}") },
                        )
                    }
                }
                entry<SectionsRoute> {
                    SectionsScreen(
                        onBack = ::goBack,
                        onSectionClick = { backStack.add(SubSectionRoute(it.route)) },
                    )
                }
                entry<SubSectionRoute> { route ->
                    when (AtharSection.entries.first { it.route == route.section }) {
                        AtharSection.Articles -> ArticlesScreen(
                            state = articlesState,
                            onSettings = ::openSettings,
                            onArticleClick = { backStack.add(ReaderRoute(it.id)) },
                            onSaveClick = { contentViewModel.toggleReadLater(it.id) },
                            onDownloadClick = {
                                if (!it.downloaded && !it.downloading) requestNotificationPermission()
                                contentViewModel.toggleDownload(it.id)
                            },
                            onBack = ::goBack,
                        )

                        AtharSection.Issues -> IssuesScreen(
                            state = IssuesFixture,
                            onSettings = ::openSettings,
                            onIssueClick = { backStack.add(ReaderRoute(it.id)) },
                            onSaveClick = {
                                announce("سيُحفظ ${it.question} في قائمتي مع طبقة البيانات")
                            },
                            onDownloadClick = {
                                announce("سيُربط تنزيل المسألة بمدير التنزيل في مرحلة البيانات")
                            },
                            onBack = ::goBack,
                        )

                        AtharSection.Adhkar -> AdhkarScreen(
                            state = AdhkarFixture,
                            onSettings = ::openSettings,
                            onOpenBabs = { backStack.add(AdhkarBabsRoute) },
                            onCopy = { announce("نُسخ الذكر") },
                            onSave = { announce("ستُحفظ الأذكار في المفضّلة مع طبقة البيانات") },
                            onBack = ::goBack,
                        )
                    }
                }
                entry<AdhkarBabsRoute> {
                    AdhkarBabsScreen(
                        state = AdhkarFixture,
                        onBack = ::goBack,
                        onOpenBab = { backStack.add(AdhkarBabRoute(it.id)) },
                    )
                }
                entry<AdhkarBabRoute> { route ->
                    AdhkarScreen(
                        state = AdhkarFixture,
                        bab = AdhkarFixture.bab(route.babId),
                        onSettings = ::openSettings,
                        onOpenBabs = { backStack.add(AdhkarBabsRoute) },
                        onCopy = { announce("نُسخ الذكر") },
                        onSave = { announce("ستُحفظ الأذكار في المفضّلة مع طبقة البيانات") },
                        onBack = ::goBack,
                    )
                }
                entry<DownloadsRoute> {
                    LaunchedEffect(Unit) { contentViewModel.refreshCacheBytes() }
                    DownloadsScreen(
                        state = downloadsUiState(
                            books = booksState.books,
                            articles = articlesState.articles,
                            poems = poetryState.poems,
                            storage = StorageUi(
                                pinnedCountLabel = "${arabicDigits(pinnedCount)} عنصرًا",
                                cacheLabel = storageSize(cacheBytes),
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
                            contentViewModel.clearCache()
                            announce("مُسح المؤقّت وبقيت التنزيلات المثبّتة")
                        },
                        onPauseResume = { announce("سيتحكّم مدير التنزيل في ${it.title}") },
                        onCancel = { contentViewModel.toggleDownload(it.id) },
                        onRemove = { contentViewModel.toggleDownload(it.id) },
                    )
                }
                entry<SectionRoute> { route ->
                    when (AtharDestination.fromRoute(route.destination)) {
                        AtharDestination.Poetry -> PoetryScreen(
                            onLogo = ::openSections,
                            state = poetryState,
                            onSettings = ::openSettings,
                            onPoemClick = { backStack.add(PoemReaderRoute(it.id)) },
                            onDownloadClick = {
                                if (!it.downloaded && !it.downloading) requestNotificationPermission()
                                contentViewModel.toggleDownload(it.id)
                            },
                            onSaveClick = { contentViewModel.toggleReadLater(it.id) },
                        )

                        AtharDestination.Audio -> AudioScreen(
                            onLogo = ::openSections,
                            state = AudioFixture,
                            onSettings = ::openSettings,
                            onPlay = { nowPlaying = startPlaying(it) },
                            onMore = { announce("خيارات ${it.title}") },
                            onOpenPlaylist = { announce("فتح قائمة ${it.name}") },
                        )

                        AtharDestination.Search -> SearchScreen(
                            onLogo = ::openSections,
                            state = searchState,
                            onSettings = ::openSettings,
                            onQueryChange = contentViewModel::setSearchQuery,
                            onFiltersChange = contentViewModel::setSearchFilters,
                            onOpenResult = { hit ->
                                contentViewModel.rememberCurrentSearch()
                                if (hit.type == com.atharchive.feature.search.SearchResultType.Poem) {
                                    backStack.add(PoemReaderRoute(hit.entityId))
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
                            onLogo = ::openSections,
                            state = KannashahFixture,
                            onSettings = ::openSettings,
                            onCopy = { announce("نُسخ المقتطف") },
                            onShare = { announce("مشاركة المقتطف") },
                            onMore = { announce("خيارات المقتطف") },
                            onOpenSource = { announce("فتح ${it.sourceTitle}") },
                        )

                        // Books is the root entry, never pushed as a section.
                        AtharDestination.Books -> Unit
                    }
                }
            },
        )
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
                modifier = Modifier.size(36.dp).testTag("mini_player_toggle"),
            ) {
                Icon(
                    imageVector = if (playing) AtharIcons.Pause else AtharIcons.Play,
                    contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل",
                    tint = AtharTheme.colors.accent,
                    modifier = Modifier.size(18.dp),
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
                Text(
                    text = audio.title,
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp).testTag("mini_player_close"),
            ) {
                Icon(
                    imageVector = AtharIcons.Close,
                    contentDescription = "إغلاق المشغّل",
                    tint = AtharTheme.colors.secondaryText,
                    modifier = Modifier.size(16.dp),
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
    onDownloads: () -> Unit,
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
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsRow(AtharIcons.Palette, "المظهر")
            HorizontalDivider(color = AtharTheme.colors.divider)
            SettingsRow(AtharIcons.TextSize, "إعدادات القراءة والخط")
            HorizontalDivider(color = AtharTheme.colors.divider)
            // Storage lives on its own screen now: the budget, the transfers in flight
            // and what is pinned are one subject, and a sheet could only hold the first.
            SettingsRow(
                AtharIcons.Download,
                "التنزيلات والتخزين",
                "${arabicDigits(pinnedCount)} مثبّت · ${storageSize(cacheBytes)} مؤقّت · حد ${cacheBudgetLabel(cacheBudgetBytes)}",
                onClick = onDownloads,
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
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
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

private fun arabicDigits(value: Int): String = value.toString().map { digit ->
    if (digit in '0'..'9') ('٠'.code + (digit - '0')).toChar() else digit
}.joinToString("")

private fun storageSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f غ.ب".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f م.ب".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f ك.ب".format(bytes / 1024.0)
    else -> "$bytes ب"
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
    if (bytes == Long.MAX_VALUE) "بلا حد" else storageSize(bytes)

/** A tapped recording becomes the now-playing item at position zero. */
private fun startPlaying(audio: AudioUi) = NowPlayingUi(
    audio = audio,
    positionLabel = "\u0660\u0660:\u0660\u0660",
    durationLabel = audio.durationLabel,
    progress = 0f,
    playing = true,
)
