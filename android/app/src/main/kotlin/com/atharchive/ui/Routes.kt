package com.atharchive.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.atharchive.feature.audio.AudioUi
import com.atharchive.feature.audio.NowPlayingUi
import com.atharchive.feature.kannashah.ExcerptUi
import androidx.compose.ui.geometry.Rect
import com.atharchive.ui.navigation.AtharDestination
import com.atharchive.feature.reader.ReaderSettings
import com.atharchive.ui.theme.ThemeMode
import kotlinx.serialization.Serializable
import com.atharchive.data.AtharContentViewModel

@Serializable
internal data object BooksCatalogRoute : NavKey

@Serializable
internal data class SectionRoute(val destination: String) : NavKey

@Serializable
internal data object SettingsRoute : NavKey

@Serializable
internal data object PlayerRoute : NavKey

@Serializable
/** [hint]: an online hit's snippet, to tell its verse from others holding the searched words. */
internal data class PoemReaderRoute(val poemId: String, val targetOrdinal: Int? = null, val match: String? = null, val hint: String? = null) : NavKey

@Serializable
internal data class ReaderRoute(
    val bookId: String,
    val targetOrdinal: Int? = null,
    val matchStart: Int? = null,
    val matchEnd: Int? = null,
    /** An online hit opened at its passage's first block and placed there by the reader. */
    val locate: OnlineLocate? = null,
) : NavKey

@Serializable
internal data class OnlineLocate(val url: String?, val query: String, val snippet: String, val pg: String)

@Serializable
internal data class SubSectionRoute(val section: String) : NavKey

@Serializable
internal data object DownloadsRoute : NavKey
@Serializable
internal data class DownloadsListRoute(val targetId: String? = null) : NavKey

@Serializable
internal data object AdhkarRoute : NavKey

@Serializable
internal data class PersonsRoute(val personId: String? = null) : NavKey

@Serializable
internal data object AppearanceRoute : NavKey

@Serializable
internal data object MutoonRoute : NavKey

/**
 * What every route entry shares with the app shell (3.2): the view model, navigation and the
 * app-wide actions, the appearance settings as [AtharApp] received them, and the shell's state.
 * Built by [AtharApp] on each composition. State comes in as [State] and is read through the
 * delegates below, so each value is still read where an entry draws it: a playback tick
 * recomposes the player, not the whole app.
 */
internal class AppNav(
    val contentViewModel: AtharContentViewModel,
    val context: Context,
    val scope: kotlinx.coroutines.CoroutineScope,
    val backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    val tabState: androidx.compose.runtime.saveable.SaveableStateHolder,
    val actionAnchor: androidx.compose.runtime.MutableState<Rect>,
    val userAdhkar: com.atharchive.data.UserAdhkarStore,
    val notificationPermission: androidx.activity.result.ActivityResultLauncher<String>,
    val exportUserData: androidx.activity.result.ActivityResultLauncher<String>,
    val importUserData: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    val receiveBooks: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    themeMode: androidx.compose.runtime.State<ThemeMode>,
    val onThemeModeChange: (ThemeMode) -> Unit,
    style: androidx.compose.runtime.State<com.atharchive.ui.theme.AtharStyle>,
    val onStyleChange: (com.atharchive.ui.theme.AtharStyle) -> Unit,
    accent: androidx.compose.runtime.State<com.atharchive.ui.theme.AccentChoice>,
    val onAccentChange: (com.atharchive.ui.theme.AccentChoice) -> Unit,
    startPlace: androidx.compose.runtime.State<com.atharchive.data.StartPlace>,
    val onStartPlaceChange: (com.atharchive.data.StartPlace) -> Unit,
    readerSettings: androidx.compose.runtime.State<ReaderSettings>,
    val onReaderSettingsChange: (ReaderSettings) -> Unit,
    bar: androidx.compose.runtime.State<List<AtharDestination>>,
    val onBarChange: (List<AtharDestination>) -> Unit,
    val onResetSettings: () -> Unit,
    val goBack: () -> Unit,
    val openSettings: () -> Unit,
    val replaceRoute: (NavKey, NavKey) -> Unit,
    val navigateTo: (AtharDestination) -> Unit,
    val openHit: (com.atharchive.feature.search.SearchHitUi, String) -> Unit,
    val sectionSearch: (com.atharchive.feature.search.SearchResultType) -> com.atharchive.feature.search.SectionTextSearch,
    val onDownloadTap: (String, String, String, Boolean, com.atharchive.feature.downloads.TransferUi?) -> Unit,
    val announce: (String) -> Unit,
    val openExcerptNote: (String?) -> Unit,
    val openAudioSource: (com.atharchive.feature.audio.AudioUi) -> Unit,
    val addDhikr: (String, String, Set<String>, Rect?) -> Unit,
    val sendBooks: (List<String>) -> Unit,
    booksState: androidx.compose.runtime.State<com.atharchive.feature.books.BooksUiState>,
    scrollToTopRequest: androidx.compose.runtime.State<Int>,
    retainedIds: androidx.compose.runtime.State<Set<String>>,
    recentQueries: androidx.compose.runtime.State<List<String>>,
    playbackState: androidx.compose.runtime.State<com.atharchive.data.AudioPlaybackState>,
    audioState: androidx.compose.runtime.State<com.atharchive.feature.audio.AudioUiState>,
    nowPlaying: androidx.compose.runtime.State<com.atharchive.feature.audio.NowPlayingUi?>,
    cacheBytes: androidx.compose.runtime.State<Long>,
    cacheBudgetBytes: androidx.compose.runtime.State<Long>,
    pinnedCount: androidx.compose.runtime.State<Int>,
    syncState: androidx.compose.runtime.State<com.atharchive.data.ContentSyncUiState>,
    userAdhkarState: androidx.compose.runtime.State<com.atharchive.data.UserAdhkarState>,
    kannashahFocus: androidx.compose.runtime.MutableState<String?>,
    editorAnchor: androidx.compose.runtime.MutableState<Rect>,
    editingExcerpt: androidx.compose.runtime.MutableState<ExcerptUi?>,
    editingDhikr: androidx.compose.runtime.MutableState<com.atharchive.data.UserDhikr?>,
    deletingExcerpt: androidx.compose.runtime.MutableState<ExcerptUi?>,
    pendingRemoval: androidx.compose.runtime.MutableState<DownloadRemoval?>,
    adhkarOpen: androidx.compose.runtime.MutableState<String?>,
    adhkarReminders: androidx.compose.runtime.MutableState<Map<String, Int>>,
) {
    // Nav3 keeps each entry's content once built: plain values here froze at the first frame (settings taps did nothing).
    val themeMode by themeMode
    val style by style
    val accent by accent
    val startPlace by startPlace
    val readerSettings by readerSettings
    val bar by bar
    val booksState by booksState
    val scrollToTopRequest by scrollToTopRequest
    val retainedIds by retainedIds
    val recentQueries by recentQueries
    val playbackState by playbackState
    val audioState by audioState
    val nowPlaying by nowPlaying
    val cacheBytes by cacheBytes
    val cacheBudgetBytes by cacheBudgetBytes
    val pinnedCount by pinnedCount
    val syncState by syncState
    val userAdhkarState by userAdhkarState
    var kannashahFocus by kannashahFocus
    var editorAnchor by editorAnchor
    var editingExcerpt by editingExcerpt
    var editingDhikr by editingDhikr
    var deletingExcerpt by deletingExcerpt
    var pendingRemoval by pendingRemoval
    var adhkarOpen by adhkarOpen
    var adhkarReminders by adhkarReminders
}
