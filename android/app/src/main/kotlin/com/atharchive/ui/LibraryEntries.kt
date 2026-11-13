package com.atharchive.ui

import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.atharchive.feature.articles.ArticlesScreen
import com.atharchive.feature.issues.IssuesScreen
import com.atharchive.feature.books.BooksScreen
import com.atharchive.feature.books.BookDownloadUi
import com.atharchive.feature.kannashah.KannashahScreen
import com.atharchive.feature.poetry.PoetryScreen
import com.atharchive.feature.sections.AtharSection
import com.atharchive.feature.search.SearchScreen
import com.atharchive.ui.navigation.AtharDestination
import com.atharchive.feature.books.asBook
import com.atharchive.ui.theme.AtharTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.navigation3.runtime.EntryProviderScope

/** Books, poems and articles as one list of rows ([ids] only, or all): an author's page and المتون show every kind alike. */
@Composable
internal fun AppNav.rememberWorks(ids: Set<String>?): List<com.atharchive.feature.books.BookUi> {
    val poems = contentViewModel.poetry.collectAsStateWithLifecycle().value.poems
    val articles = contentViewModel.articles.collectAsStateWithLifecycle().value.articles
    val issues = contentViewModel.issues.collectAsStateWithLifecycle().value.issues
    return remember(ids, booksState.books, poems, articles, issues) {
        fun <T> List<T>.mine(id: (T) -> String) = if (ids == null) this else filter { id(it) in ids }
        booksState.books.mine { it.id } + poems.mine { it.id }.map { it.asBook() } + articles.mine { it.id }.map { it.asBook() } +
            issues.mine { it.id }.map { it.asBook() }
    }
}

/** The library: the books root, the section tabs (poetry, articles, search, الكناشة), issues, المتون and the people. */
internal fun EntryProviderScope<NavKey>.libraryEntries(app: AppNav) = with(app) {
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
        entry<SectionRoute> { route ->
            tabState.SaveableStateProvider(route.destination) {
            when (AtharDestination.fromRoute(route.destination)) {
                AtharDestination.Poetry -> PoetryScreen(
                    state = contentViewModel.poetry.collectAsStateWithLifecycle().value,
                    onSettings = openSettings,
                    onPoemClick = { backStack.add(PoemReaderRoute(it.entityId, it.ordinal)) },
                    textSearch = remember { sectionSearch(com.atharchive.feature.search.SearchResultType.Poem) },
                    onDownloadClick = { onDownloadTap(it.entityId, it.title, it.sizeLabel, it.downloaded, it.transfer) },
                    onSaveClick = { contentViewModel.toggleFavourite(it.entityId) },
                    scrollToTopRequest = scrollToTopRequest,
                )

                AtharDestination.Articles -> ArticlesScreen(
                    state = contentViewModel.articles.collectAsStateWithLifecycle().value,
                    onSettings = openSettings,
                    onArticleClick = { backStack.add(ReaderRoute(it.id)) },
                    onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                    onDownloadClick = { onDownloadTap(it.id, it.title, it.sizeLabel, it.downloaded, it.transfer) },
                    scrollToTopRequest = scrollToTopRequest,
                    textSearch = remember { sectionSearch(com.atharchive.feature.search.SearchResultType.Article) },
                )

                AtharDestination.Search -> SearchScreen(
                    state = contentViewModel.search.collectAsStateWithLifecycle().value,
                    onSettings = openSettings,
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
                    onSettings = openSettings,
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

                // Books is the root entry, never pushed as a section; the rest open their own routes.
                else -> Unit
            }
        }
        }
        entry<SubSectionRoute> { route ->
            when (AtharSection.entries.first { it.route == route.section }) {
                AtharSection.Articles -> ArticlesScreen(
                    state = contentViewModel.articles.collectAsStateWithLifecycle().value,
                    onSettings = openSettings,
                    onArticleClick = { backStack.add(ReaderRoute(it.id)) },
                    textSearch = remember { sectionSearch(com.atharchive.feature.search.SearchResultType.Article) },
                    onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                    onDownloadClick = { onDownloadTap(it.id, it.title, it.sizeLabel, it.downloaded, it.transfer) },
                    onBack = null,
                )

                AtharSection.Issues -> IssuesScreen(
                    state = contentViewModel.issues.collectAsStateWithLifecycle().value,
                    onSettings = openSettings,
                    onIssueClick = { backStack.add(ReaderRoute(it.id)) },
                    textSearch = remember { sectionSearch(com.atharchive.feature.search.SearchResultType.Issue) },
                    onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                    onDownloadClick = { onDownloadTap(it.id, it.question, it.sizeLabel, it.downloaded, it.transfer) },
                    onBack = null,
                )


            }
        }
        entry<MutoonRoute> {
            val all = rememberWorks(null)
            val mutoon = remember(all) { all.filter { it.kind == "متن" || it.kind == "منظومة" } }
            val mutoonIds = remember(mutoon) { mutoon.map { it.id }.toSet() }
            val row by remember(mutoonIds) { contentViewModel.worksRow("كل المتون", mutoonIds) }.collectAsStateWithLifecycle(null)
            com.atharchive.feature.books.BooksScreen(
                state = com.atharchive.feature.books.BooksUiState(
                    com.atharchive.data.arabicCount(mutoon.size, "المتون", "متن واحد", "متنين", "متون", "متنًا"), mutoon, booksState.loaded,
                ),
                onSettings = openSettings,
                onBookClick = { backStack.add(ReaderRoute(it.id)) },
                onDownloadClick = { onDownloadTap(it.id, it.title, it.download.sizeLabel, it.download is com.atharchive.feature.books.BookDownloadUi.Downloaded, (it.download as? com.atharchive.feature.books.BookDownloadUi.Downloading)?.transfer) },
                onSaveClick = { contentViewModel.toggleFavourite(it.id) },
                scrollToTopRequest = scrollToTopRequest,
                onBack = null,
                title = "المتون والمنظومات",
                keyPrefix = "mutoon",
                listHeader = {
                    row?.takeIf { it.items.size > 1 }?.let { r ->
                        com.atharchive.feature.downloads.BulkRow(r, 20.dp, { contentViewModel.downloadRemaining(r, it) }, { contentViewModel.pauseResumeMany(r.running, pause = true) })
                        com.atharchive.ui.components.AtharRowDivider(Modifier.padding(horizontal = 20.dp), 0.7.dp, com.atharchive.ui.theme.AtharTheme.colors.divider.copy(alpha = 0.42f))
                    }
                },
            )
        }
        entry<PersonsRoute> { route ->
            val persons by contentViewModel.persons.collectAsStateWithLifecycle()
            val works by contentViewModel.personWorks.collectAsStateWithLifecycle()
            val person = persons.firstOrNull { it.id == route.personId }
            if (route.personId == null) com.atharchive.feature.persons.PersonsScreen(persons, works, { backStack.add(PersonsRoute(it.id)) }, openSettings, null)
            else if (person != null) {
                val ids = remember(person) { person.works.toSet() }
                val label = "مؤلفات ${person.name}"
                val row by remember(person) { contentViewModel.worksRow(label, ids) }.collectAsStateWithLifecycle(null)
                com.atharchive.feature.persons.PersonScreen(
                    person = person,
                    works = rememberWorks(ids),
                    loaded = booksState.loaded,
                    downloadAll = row,
                    onDownloadAll = { r, mobileData -> contentViewModel.downloadRemaining(r, mobileData) },
                    onPauseAll = { contentViewModel.pauseResumeMany(it.running, pause = true) },
                    onWork = { backStack.add(ReaderRoute(it.id)) },
                    onDownload = { onDownloadTap(it.id, it.title, it.download.sizeLabel, it.download is com.atharchive.feature.books.BookDownloadUi.Downloaded, (it.download as? com.atharchive.feature.books.BookDownloadUi.Downloading)?.transfer) },
                    onSave = { contentViewModel.toggleFavourite(it.id) },
                    onSettings = openSettings,
                    onBack = goBack,
                )
            }
            else UnavailableRoute("العَلَم غير متاح", goBack) { contentViewModel.sync() }
        }
}
