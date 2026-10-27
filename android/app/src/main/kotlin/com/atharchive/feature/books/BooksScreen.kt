package com.atharchive.feature.books

import com.atharchive.ui.components.directed
import com.atharchive.ui.components.AtharDirectionGroup
import com.atharchive.ui.components.normKey
import kotlinx.coroutines.launch
import com.atharchive.core.ArNormalize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.atharchive.ui.components.AtharFloatingHeader
import com.atharchive.ui.components.ShowOnChange
import com.atharchive.ui.components.rememberAtharCollapsingHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharFavouriteButton
import com.atharchive.ui.components.AtharDownloadButton
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.SectionTab
import com.atharchive.ui.components.emptyCopy
import com.atharchive.ui.components.AtharChipGroup
import com.atharchive.ui.components.AtharFilterIcon
import com.atharchive.ui.components.AtharActiveFilters
import com.atharchive.ui.components.enumValueOrNull
import com.atharchive.ui.components.AtharFacet
import com.atharchive.ui.components.arabicDigits
import com.atharchive.ui.components.asChoiceSet
import com.atharchive.ui.components.asStoredChoices
import com.atharchive.ui.components.rememberPersisted
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

private fun centuryOf(died: Int) = "القرن ${arabicDigits((died - 1) / 100 + 1)}"

/** «ت ٧٢٨هـ» */
internal fun diedLabel(died: Int) = "ت ${arabicDigits(died)}هـ"

/** «٤٤٣ ص», or «٤ مج» for a work in several volumes. */
internal fun BookUi.sizeLabel(): String? = when {
    (volumes ?: 1) > 1 -> "${arabicDigits(volumes!!)} مج"
    pages != null -> "${arabicDigits(pages)} ص"
    else -> null
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BooksScreen(
    state: BooksUiState,
    onSettings: () -> Unit,
    onBookClick: (BookUi) -> Unit,
    onDownloadClick: (BookUi) -> Unit,
    onSaveClick: (BookUi) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SectionTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var kindStored by rememberPersisted("books.kinds", "")
    var topicStored by rememberPersisted("books.topics", "")
    var centuryStored by rememberPersisted("books.centuries", "")
    var authorStored by rememberPersisted("books.authors", "")
    var sortName by rememberPersisted("books.order", BookSort.Title.name)
    val sort = enumValueOrNull<BookSort>(sortName) ?: BookSort.Title
    var descendingStored by rememberPersisted("books.order.desc", "")
    val descending = descendingStored.isNotEmpty()
    val kinds = remember(state.books) { state.books.map { it.kind }.distinct() }
    val topics = remember(state.books) { state.books.flatMap { it.topics }.distinct().sorted() }
    val centuries = remember(state.books) {
        state.books.mapNotNull { it.died }.distinct().sorted().map(::centuryOf).distinct()
    }
    // Author → (death year, book count), for the author filter's second line.
    val authorFacts = remember(state.books) {
        state.books.filter { it.author.isNotBlank() }.groupBy { it.author }.mapValues { (_, books) -> books.firstNotNullOfOrNull { it.died } to books.size }
    }
    val authors = remember(authorFacts) { authorFacts.keys.sortedBy { normKey(it) } }
    // Values that no longer exist (catalog changed) drop out instead of hiding every book.
    val kind = kindStored.asChoiceSet() intersect kinds.toSet()
    val topic = topicStored.asChoiceSet() intersect topics.toSet()
    val century = centuryStored.asChoiceSet() intersect centuries.toSet()
    val author = authorStored.asChoiceSet() intersect authorFacts.keys
    val listState = rememberLazyListState()
    val reselectScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    // Normalized once per catalog change, not per filter pass or per sort comparison.
    val normalized = remember(state.books) {
        state.books.associate { it.id to (normKey(it.title) to normKey(it.author)) }
    }
    val visibleBooks = remember(state.books, selectedTab, query, kind, sort, descending, topic, century, author) {
        val term = ArNormalize.normalize(query.trim())
        state.books
            .asSequence()
            .filter { book ->
                selectedTab.matches(book.recentRank != null, book.download is BookDownloadUi.Downloaded, book.saved)
            }
            .filter { book ->
                val (title, author) = normalized.getValue(book.id)
                term.isBlank() || title.contains(term, ignoreCase = true) || author.contains(term, ignoreCase = true)
            }
            .filter { kind.isEmpty() || it.kind in kind }
            .filter { topic.isEmpty() || it.topics.any(topic::contains) }
            .filter { century.isEmpty() || it.died?.let(::centuryOf) in century }
            .filter { author.isEmpty() || it.author in author }
            .sortedWith(
                when {
                    selectedTab == SectionTab.Recent -> compareBy { it.recentRank ?: Int.MAX_VALUE }
                    sort == BookSort.Title -> compareBy<BookUi> { normalized.getValue(it.id).first }.directed(descending)
                    sort == BookSort.Author -> compareBy<BookUi> { normalized.getValue(it.id).second }.directed(descending)
                    // Unknown years and sizes stay last in both directions.
                    sort == BookSort.Died -> compareBy(nullsLast(if (descending) reverseOrder() else naturalOrder())) { it.died }
                    sort == BookSort.Pages -> compareBy<BookUi, Int?>(nullsLast(if (descending) reverseOrder() else naturalOrder())) { it.volumes?.takeIf { v -> v > 1 }?.let { v -> 100_000 * v } ?: it.pages }
                    else -> compareByDescending<BookUi> { it.publishedAt }.directed(descending)
                },
            )
            .toList()
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_books")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp
        val compactTitles = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            val header = rememberAtharCollapsingHeader()
            header.ShowOnChange(listState, selectedTab, query, kind, topic, century, author, sort)
            AtharFloatingHeader(header) {
                Column {
                    AtharTopBar(
                        title = "الكتب",
                        onSettings = onSettings,
                        showAppIcon = true,
                        onBack = onBack,
                        horizontalPadding = horizontalPadding,
                    )
                val filterGroups = listOfNotNull(
                            if (selectedTab == SectionTab.Recent) null
                            else AtharChipGroup("الترتيب", BookSort.entries.map { it.label }, sort.label) { label ->
                                sortName = BookSort.entries.first { it.label == label }.name
                            },
                            if (selectedTab == SectionTab.Recent) null else AtharDirectionGroup(descending) { descendingStored = if (it) "1" else "" },
                        )
                val filterFacets = listOf(
                            AtharFacet("المؤلف", authors, author, { authorStored = it.asStoredChoices() }) { name ->
                                val (died, count) = authorFacts.getValue(name)
                                listOfNotNull(died?.let(::diedLabel), com.atharchive.data.arabicCount(count, "", "كتاب واحد", "كتابان", "كتب", "كتابًا")).joinToString(" · ")
                            },
                            AtharFacet("الموضوع", topics, topic, { topicStored = it.asStoredChoices() }),
                            AtharFacet("القرن", centuries, century, { centuryStored = it.asStoredChoices() }),
                            AtharFacet("النوع", kinds, kind, { kindStored = it.asStoredChoices() }),
                        )
                AtharSearchField(
                    query = query,
                    placeholder = "ابحث في ${state.archiveCountLabel}…",
                    onQueryChange = { query = it },
                    tagPrefix = "books",
                    trailing = { AtharFilterIcon(filterGroups, filterFacets, "books_filter") },
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 4.dp,
                    ),
                )
                AtharActiveFilters(filterGroups, filterFacets, "books_filter", Modifier.padding(horizontal = horizontalPadding))
                }
            
        
            Column {
                AtharTabRow(
                    tabs = SectionTab.tabs,
                    selectedKey = selectedTab.key,
                    onSelect = { selectedTab = SectionTab.fromKey(it) },
                    tagPrefix = "books",
                    onReselect = { reselectScope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
                    thickness = 0.7.dp,
                    color = AtharTheme.colors.divider.copy(alpha = 0.5f),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .nestedScroll(header.connection)
                .testTag("books_screen"),
            contentPadding = PaddingValues(top = header.height, bottom = 28.dp),
        ) {
            if (!state.loaded) {
                // first catalog read not back yet: show nothing rather than a false "no books"
            } else if (visibleBooks.isEmpty()) {
                item(key = "books-empty") {
                    val (emptyTitle, emptyBody) = booksEmptyCopy(
                        selectedTab = selectedTab,
                        hasQuery = query.isNotBlank(),
                        hasFilters = (kind + topic + century + author).isNotEmpty(),
                    )
                    AtharEmptyState(
                        title = emptyTitle,
                        body = emptyBody,
                        tag = "books_empty_state",
                        horizontalPadding = horizontalPadding,
                    )
                }
            } else {
                itemsIndexed(visibleBooks, key = { _, book -> book.id }) { index, book ->
                    BookRow(
                        book = book,
                        query = query,
                        showReadingProgress = selectedTab == SectionTab.Recent,
                        compactTitle = compactTitles,
                        horizontalPadding = horizontalPadding,
                        onClick = { onBookClick(book) },
                        onDownload = { onDownloadClick(book) },
                        onSave = { onSaveClick(book) },
                    )
                    if (index != visibleBooks.lastIndex) {
                        // Hairline, and inset past the title margin: enough to separate
                        // two books, not enough to draw a table.
                        HorizontalDivider(
                            modifier = Modifier.padding(start = horizontalPadding + 44.dp, end = horizontalPadding),
                            thickness = 0.7.dp,
                            color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                        )
                    }
                }
            }
        }
    }
    }

}

@Composable
private fun BookRow(
    book: BookUi,
    query: String,
    showReadingProgress: Boolean,
    compactTitle: Boolean,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 80.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 7.dp)
            .testTag("book_row_${book.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val hl = AtharTheme.colors.accent.copy(alpha = 0.18f)
        // Three tiers, each a clear step down: title (editorial, near-black),
        // author (UI face, warm gray), then catalog facts (smaller, lighter still).
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = highlightMatches(book.title, query, hl),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compactTitle) 17.sp else 18.sp,
                    lineHeight = if (compactTitle) 26.sp else 27.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = highlightMatches(book.author + (book.died?.let { " (${diedLabel(it)})" } ?: ""), query, hl),
                modifier = Modifier.padding(top = 2.dp),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val factColor = AtharTheme.colors.secondaryText.copy(alpha = 0.72f)
                val facts = listOfNotNull(book.discipline.takeIf(String::isNotBlank), book.sizeLabel())
                if (facts.isNotEmpty()) Text(
                    text = facts.joinToString(" · "),
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            if (showReadingProgress) {
                ReadingProgress(book)
            }
        }
        AtharDownloadButton(
            title = book.title,
            sizeLabel = book.download.sizeLabel,
            downloaded = book.download is BookDownloadUi.Downloaded,
            transfer = (book.download as? BookDownloadUi.Downloading)?.transfer,
            onClick = onDownload,
            tag = "book_download_${book.id}",
        )
        AtharFavouriteButton(
            saved = book.saved,
            onToggle = onSave,
            itemTitle = book.title,
            tag = "book_save_${book.id}",
        )
    }
}

@Composable
private fun ReadingProgress(book: BookUi) {
    val position = book.readingPosition ?: return
    val progress = book.readingProgress ?: return
    val label = book.readingProgressLabel ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = position,
                modifier = Modifier.weight(1f),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        RtlProgressBar(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .testTag("reading_progress_${book.id}"),
        )
    }
}

@Composable
private fun RtlProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val track = AtharTheme.colors.divider
    val accent = AtharTheme.colors.accent
    Canvas(
        modifier = modifier
            .height(3.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = track,
            size = size,
            cornerRadius = CornerRadius(radius, radius),
        )
        val progressWidth = size.width * progress.coerceIn(0f, 1f)
        drawRoundRect(
            color = accent,
            topLeft = Offset(size.width - progressWidth, 0f),
            size = Size(progressWidth, size.height),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

private fun booksEmptyCopy(
    selectedTab: SectionTab,
    hasQuery: Boolean,
    hasFilters: Boolean,
): Pair<String, String> = when {
    hasQuery -> "لا توجد نتائج" to "جرّب اسم كتاب أو مؤلف أقصر."
    hasFilters -> "لا توجد كتب بهذه الاختيارات" to "أزل بعض الاختيارات لعرض مزيد من الكتب."
    else -> selectedTab.emptyCopy("كتب", "نزّل كتابًا لتقرأه دون اتصال.")
}

@Preview(name = "Books", widthDp = 393, heightDp = 852, locale = "ar")
@Composable
private fun BooksPreview() {
    AtharTheme(darkTheme = false) {
        BooksScreen(
            state = BooksFixture,
            onSettings = {},
            onBookClick = {},
            onDownloadClick = {},
            onSaveClick = {},
        )
    }
}

@Preview(name = "Books 200%", widthDp = 393, heightDp = 852, locale = "ar", fontScale = 2f)
@Composable
private fun BooksLargeTextPreview() {
    AtharTheme(darkTheme = false) {
        BooksScreen(
            state = BooksFixture,
            onSettings = {},
            onBookClick = {},
            onDownloadClick = {},
            onSaveClick = {},
        )
    }
}
