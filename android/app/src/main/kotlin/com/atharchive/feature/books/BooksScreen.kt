package com.atharchive.feature.books

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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharBookmarkButton
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

private const val AllKinds = "كل الأنواع"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BooksScreen(
    state: BooksUiState,
    onSettings: () -> Unit,
    onBookClick: (BookUi) -> Unit,
    onDownloadClick: (BookUi) -> Unit,
    onSaveClick: (BookUi) -> Unit,
    onLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(BooksTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(AllKinds) }
    var sort by rememberSaveable { mutableStateOf(BookSort.Newest) }
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    val visibleBooks = remember(state.books, selectedTab, query, kind, sort) {
        state.books
            .asSequence()
            .filter { book ->
                when (selectedTab) {
                    BooksTab.All -> true
                    BooksTab.Recent -> book.recentRank != null
                    BooksTab.Downloaded -> book.download is BookDownloadUi.Downloaded
                    BooksTab.MyList -> book.saved
                }
            }
            .filter { book ->
                val term = query.trim()
                term.isBlank() || book.title.contains(term, ignoreCase = true) ||
                    book.author.contains(term, ignoreCase = true)
            }
            .filter { kind == AllKinds || it.kind == kind }
            .sortedWith(
                when {
                    selectedTab == BooksTab.Recent -> compareBy { it.recentRank ?: Int.MAX_VALUE }
                    sort == BookSort.Title -> compareBy { it.title }
                    sort == BookSort.Author -> compareBy { it.author }
                    else -> compareBy { state.books.indexOf(it) }
                },
            )
            .toList()
    }
    val kinds = remember(state.books) {
        listOf(AllKinds) + state.books.map { it.kind }.distinct()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_books")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp
        val compactTitles = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "الكتب",
                onSettings = onSettings,
                showAppIcon = true,
                onLogo = onLogo,
                onBack = onBack,
                actionIcon = AtharIcons.Archive,
                actionLabel = "مكتبتي",
                onAction = onLibrary,
                horizontalPadding = horizontalPadding,
            )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("books_screen"),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "books-search") {
                AtharSearchField(
                    query = query,
                    placeholder = "ابحث في ${state.archiveCountLabel}…",
                    onQueryChange = { query = it },
                    tagPrefix = "books",
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 4.dp,
                    ),
                )
            }
            item(key = "books-tabs") {
                AtharTabRow(
                    tabs = BooksTab.tabs,
                    selectedKey = selectedTab.key,
                    onSelect = { selectedTab = BooksTab.fromKey(it) },
                    tagPrefix = "books",
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }
            item(key = "books-menus") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding - 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AtharMenuButton(
                        label = "النوع",
                        options = kinds,
                        selected = kind,
                        defaultOption = AllKinds,
                        onSelect = { kind = it },
                        tagPrefix = "books_kind",
                    )
                    if (selectedTab != BooksTab.Recent) {
                        AtharMenuButton(
                            label = "الترتيب",
                            options = BookSort.entries.map { it.label },
                            selected = sort.label,
                            defaultOption = BookSort.Newest.label,
                            onSelect = { label ->
                                sort = BookSort.entries.first { it.label == label }
                            },
                            tagPrefix = "books_sort",
                        )
                    }
                }
            }
            item(key = "books-list-divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
                    thickness = 0.7.dp,
                    color = AtharTheme.colors.divider.copy(alpha = 0.5f),
                )
            }
            if (visibleBooks.isEmpty()) {
                item(key = "books-empty") {
                    val (emptyTitle, emptyBody) = booksEmptyCopy(
                        selectedTab = selectedTab,
                        hasQuery = query.isNotBlank(),
                        hasFilters = kind != AllKinds,
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
                        showReadingProgress = selectedTab == BooksTab.Recent,
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
                text = highlightMatches(book.author, query, hl),
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
                Text(
                    text = book.discipline,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = book.download.sizeLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall
                        .copy(textDirection = TextDirection.Ltr),
                    maxLines = 1,
                )
            }
            if (showReadingProgress) {
                ReadingProgress(book)
            }
        }
        AtharBookmarkButton(
            saved = book.saved,
            onToggle = onSave,
            itemTitle = book.title,
            tag = "book_save_${book.id}",
        )
        BookDownloadControl(book = book, onClick = onDownload)
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

@Composable
private fun BookDownloadControl(book: BookUi, onClick: () -> Unit) {
    val state = book.download
    val statusDescription = when (state) {
        is BookDownloadUi.Available -> "تنزيل ${book.title}، الحجم ${state.sizeLabel}"
        is BookDownloadUi.Downloading ->
            "جار تنزيل ${book.title}، ${state.progressLabel} من ${state.sizeLabel}"
        is BookDownloadUi.Downloaded -> "${book.title} محمّل، الحجم ${state.sizeLabel}"
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = statusDescription
                role = Role.Button
                if (state is BookDownloadUi.Downloading) {
                    progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
                }
            }
            .testTag("book_download_${book.id}"),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is BookDownloadUi.Available -> Icon(
                imageVector = AtharIcons.Download,
                contentDescription = null,
                tint = AtharTheme.colors.secondaryText,
                modifier = Modifier.size(19.dp),
            )
            // Progress replaces the icon rather than sitting beside it, so the control
            // never changes width mid-download.
            is BookDownloadUi.Downloading -> Text(
                text = state.progressLabel,
                color = AtharTheme.colors.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            is BookDownloadUi.Downloaded -> Icon(
                imageVector = AtharIcons.Check,
                contentDescription = null,
                tint = AtharTheme.colors.success,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun booksEmptyCopy(
    selectedTab: BooksTab,
    hasQuery: Boolean,
    hasFilters: Boolean,
): Pair<String, String> = when {
    hasQuery -> "لا توجد نتائج" to "جرّب اسم كتاب أو مؤلف أقصر."
    hasFilters -> "لا توجد كتب بهذا النوع" to "اختر «كل الأنواع» لعرض مزيد من الكتب."
    selectedTab == BooksTab.Recent -> "لا توجد كتب أخيرة" to "ستظهر هنا الكتب التي فتحتها مؤخرًا."
    selectedTab == BooksTab.Downloaded -> "لا توجد كتب محمّلة" to "نزّل كتابًا لتقرأه دون اتصال."
    selectedTab == BooksTab.MyList -> "قائمتك فارغة" to "ستظهر هنا الكتب المحفوظة في قائمتك."
    else -> "لا توجد كتب" to "لم تتوفر كتب في هذا القسم بعد."
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
