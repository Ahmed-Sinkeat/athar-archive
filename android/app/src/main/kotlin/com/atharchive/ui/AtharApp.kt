package com.atharchive.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.atharchive.feature.articles.ArticlesScreen
import com.atharchive.feature.audio.AudioFixture
import com.atharchive.feature.audio.AudioScreen
import com.atharchive.feature.audio.AudioProgressBar
import com.atharchive.feature.audio.AudioUi
import com.atharchive.feature.audio.NowPlayingUi
import com.atharchive.feature.audio.PlayerScreen
import com.atharchive.feature.books.BooksScreen
import com.atharchive.feature.kannashah.KannashahFixture
import com.atharchive.feature.kannashah.KannashahScreen
import com.atharchive.feature.reader.ReaderScreen
import com.atharchive.feature.poemreader.PoemReaderScreen
import com.atharchive.feature.poetry.PoetryScreen
import com.atharchive.feature.search.SearchFixture
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

@Serializable
private data object BooksCatalogRoute : NavKey

@Serializable
private data class SectionRoute(val destination: String) : NavKey

@Serializable
private data object SettingsRoute : NavKey

@Serializable
private data object SectionsRoute : NavKey

@Serializable
private data object PlayerRoute : NavKey

@Serializable
private data class PoemReaderRoute(val poemId: String) : NavKey

@Serializable
private data class ReaderRoute(val bookId: String) : NavKey

@Serializable
private data class SubSectionRoute(val section: String) : NavKey

@Composable
fun AtharApp(contentViewModel: AtharContentViewModel = hiltViewModel()) {
    val backStack = rememberNavBackStack(BooksCatalogRoute)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var booksScrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }
    var nowPlaying by remember { mutableStateOf(AudioFixture.nowPlaying) }
    val booksState by contentViewModel.books.collectAsStateWithLifecycle()
    val articlesState by contentViewModel.articles.collectAsStateWithLifecycle()
    val poetryState by contentViewModel.poetry.collectAsStateWithLifecycle()

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
                            announce("سيُربط تنزيل ${it.title} بمدير التنزيل في مرحلة البيانات")
                        },
                        onSaveClick = { announce("سيُحفظ ${it.title} في قائمتي مع طبقة البيانات") },
                        scrollToTopRequest = booksScrollToTopRequest,
                    )
                }
                entry<SettingsRoute> {
                    SettingsScreen(onBack = ::goBack)
                }
                // The reader owns the whole screen: no bottom navigation inside a book.
                entry<ReaderRoute> { route ->
                    val readerState by contentViewModel.readerState(route.bookId)
                        .collectAsStateWithLifecycle(initialValue = null)
                    val pagedBlocks = remember(route.bookId) {
                        contentViewModel.pagedReaderBlocks(route.bookId)
                    }.collectAsLazyPagingItems()
                    LaunchedEffect(route.bookId) { contentViewModel.openReader(route.bookId) }
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
                            onSaveClick = {
                                announce("سيُحفظ ${it.title} في قائمتي مع طبقة البيانات")
                            },
                            onDownloadClick = {
                                announce("سيُربط تنزيل ${it.title} بمدير التنزيل في مرحلة البيانات")
                            },
                            onBack = ::goBack,
                        )

                        AtharSection.Issues -> SectionPlaceholder(
                            title = AtharSection.Issues.label,
                            route = AtharSection.Issues.route,
                            onSettings = ::openSettings,
                        )

                        AtharSection.Adhkar -> SectionPlaceholder(
                            title = AtharSection.Adhkar.label,
                            route = AtharSection.Adhkar.route,
                            onSettings = ::openSettings,
                        )
                    }
                }
                entry<SectionRoute> { route ->
                    when (AtharDestination.fromRoute(route.destination)) {
                        AtharDestination.Poetry -> PoetryScreen(
                            onLogo = ::openSections,
                            state = poetryState,
                            onSettings = ::openSettings,
                            onPoemClick = { backStack.add(PoemReaderRoute(it.id)) },
                            onDownloadClick = {
                                announce("سيُربط تنزيل ${it.title} بمدير التنزيل في مرحلة البيانات")
                            },
                            onSaveClick = {
                                announce("سيُحفظ ${it.title} في قائمتي مع طبقة البيانات")
                            },
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
                            state = SearchFixture,
                            onSettings = ::openSettings,
                            onOpenResult = {
                                announce("فتح ${it.sourceTitle} عند ${it.locationLabel ?: "الموضع"}")
                            },
                            onOpenDirectMatch = { announce("فتح ${it.title}") },
                            onClearRecent = { announce("مُسحت عمليات البحث الأخيرة") },
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
private fun SettingsScreen(onBack: () -> Unit) {
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
            SettingsRow(AtharIcons.Download, "التنزيلات والتخزين")
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AtharTheme.colors.canvas,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 15.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(
                AtharIcons.Forward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = AtharTheme.colors.secondaryText,
            )
        }
    }
}

/** A tapped recording becomes the now-playing item at position zero. */
private fun startPlaying(audio: AudioUi) = NowPlayingUi(
    audio = audio,
    positionLabel = "\u0660\u0660:\u0660\u0660",
    durationLabel = audio.durationLabel,
    progress = 0f,
    playing = true,
)
