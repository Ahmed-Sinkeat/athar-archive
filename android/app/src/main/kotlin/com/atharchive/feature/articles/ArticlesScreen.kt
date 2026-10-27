package com.atharchive.feature.articles

import com.atharchive.ui.components.directed
import com.atharchive.ui.components.AtharDirectionGroup
import com.atharchive.ui.components.normKey
import com.atharchive.feature.search.sectionTextHits
import kotlinx.coroutines.launch
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.atharchive.core.ArNormalize
import com.atharchive.feature.downloads.TransferUi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.atharchive.ui.components.AtharFloatingHeader
import com.atharchive.ui.components.ShowOnChange
import com.atharchive.ui.components.rememberAtharCollapsingHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharFavouriteButton
import com.atharchive.ui.components.AtharDownloadButton
import com.atharchive.ui.components.AtharChipGroup
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharFilterButton
import com.atharchive.ui.components.enumValueOrNull
import com.atharchive.ui.components.orFirstIn
import com.atharchive.ui.components.rememberPersisted
import com.atharchive.ui.components.SectionTab
import com.atharchive.ui.components.emptyCopy
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

@Composable
fun ArticlesScreen(
    state: ArticlesUiState,
    onSettings: () -> Unit,
    onArticleClick: (ArticleUi) -> Unit,
    onSaveClick: (ArticleUi) -> Unit,
    onDownloadClick: (ArticleUi) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    onBack: (() -> Unit)? = null,
    textSearch: com.atharchive.feature.search.SectionTextSearch? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SectionTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    val textHits = com.atharchive.feature.search.rememberSectionTextHits(query, textSearch)
    var sortName by rememberPersisted("articles.order", ArticleSort.Title.name)
    var descendingStored by rememberPersisted("articles.order.desc", "")
    val descending = descendingStored.isNotEmpty()
    val sort = enumValueOrNull<ArticleSort>(sortName) ?: ArticleSort.Title
    val listState = rememberLazyListState()
    val reselectScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    // No author filter: 2,363 articles carry hundreds of distinct authors, and a flat
    // radio list does not scale to that. Sorting by author covers the same intent.
    val hasActiveFilters = sort != ArticleSort.Newest

    // Normalized once per catalog change, not per filter pass or per sort comparison.
    val normalized = remember(state.articles) {
        state.articles.associate { it.id to (normKey(it.title) to normKey(it.author)) }
    }
    val visibleArticles = remember(state.articles, selectedTab, query, sort, descending) {
        val term = ArNormalize.normalize(query.trim())
        state.articles.filter { article ->
            val matchesTab = selectedTab.matches(article.recentRank != null, article.downloaded, article.saved)
            val (title, author) = normalized.getValue(article.id)
            val matchesQuery = term.isBlank() ||
                title.contains(term, ignoreCase = true) ||
                author.contains(term, ignoreCase = true)
            matchesTab && matchesQuery
        }.sortedWith(
            if (selectedTab == SectionTab.Recent) compareBy { it.recentRank ?: Int.MAX_VALUE } else (when (sort) {
                ArticleSort.Title -> compareBy<ArticleUi> { normalized.getValue(it.id).first }
                ArticleSort.Author -> compareBy<ArticleUi> { normalized.getValue(it.id).second }
                ArticleSort.Newest -> compareBy<ArticleUi> { it.title }
            }).directed(descending),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_articles")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp
        val compactTitle = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            val header = rememberAtharCollapsingHeader()
            header.ShowOnChange(listState, selectedTab, query, sort)
            AtharFloatingHeader(header) {
                Column {
                    AtharTopBar(
                        title = "المقالات",
                        onSettings = onSettings,
                        showAppIcon = true,
                        onBack = onBack,
                        horizontalPadding = horizontalPadding,
                    )
                AtharSearchField(
                    query = query,
                    placeholder = "ابحث في ${state.countLabel}…",
                    onQueryChange = { query = it },
                    tagPrefix = "articles",
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 4.dp,
                    ),
                )
                }
            
        
            Column {
                AtharTabRow(
                    tabs = SectionTab.tabs,
                    selectedKey = selectedTab.key,
                    onSelect = { selectedTab = SectionTab.fromKey(it) },
                    tagPrefix = "articles",
                    onReselect = { reselectScope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding - 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AtharFilterButton(
                        groups = listOfNotNull(
                            if (selectedTab == SectionTab.Recent) null else AtharChipGroup("الترتيب", ArticleSort.entries.map { it.label }, sort.label) { label -> sortName = ArticleSort.entries.first { it.label == label }.name },
                            if (selectedTab == SectionTab.Recent) null else AtharDirectionGroup(descending) { descendingStored = if (it) "1" else "" },
                        ),
                        tagPrefix = "articles_filter",
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    color = AtharTheme.colors.divider.copy(alpha = 0.72f),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .nestedScroll(header.connection)
                .testTag("articles_screen"),
            contentPadding = PaddingValues(top = header.height, bottom = 28.dp),
        ) {
            if (!state.loaded) {
                // first catalog read not back yet: show nothing rather than a false "no articles"
            } else if (visibleArticles.isEmpty() && textHits.isEmpty()) {
                item(key = "articles-empty") {
                    val (title, body) = when {
                        query.isNotBlank() ->
                            "لا توجد نتائج" to "جرّب عنوانًا أو اسم مؤلف أقصر."
                        else -> selectedTab.emptyCopy("مقالات", "نزّل مقالًا لتقرأه دون اتصال.")
                    }
                    AtharEmptyState(
                        title = title,
                        body = body,
                        tag = "articles_empty_state",
                        horizontalPadding = horizontalPadding,
                    )
                }
            } else {
                itemsIndexed(visibleArticles, key = { _, a -> a.id }) { index, article ->
                    ArticleRow(
                        article = article,
                        query = query,
                        compactTitle = compactTitle,
                        horizontalPadding = horizontalPadding,
                        onClick = { onArticleClick(article) },
                        onSave = { onSaveClick(article) },
                        onDownload = { onDownloadClick(article) },
                    )
                    if (index != visibleArticles.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = horizontalPadding),
                            color = AtharTheme.colors.divider.copy(alpha = 0.64f),
                        )
                    }
                }
            }
            sectionTextHits(textHits, query, textSearch, horizontalPadding)
        }
    }
    }
}

@Composable
private fun ArticleRow(
    article: ArticleUi,
    query: String,
    compactTitle: Boolean,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 11.dp)
            .testTag("article_row_${article.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val hl = AtharTheme.colors.accent.copy(alpha = 0.18f)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = highlightMatches(article.title, query, hl),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compactTitle) 18.sp else 20.sp,
                    lineHeight = if (compactTitle) 27.sp else 29.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = highlightMatches(article.author, query, hl),
                    modifier = Modifier.weight(1f, fill = false),
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "·",
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = article.dateLabel,
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
            Text(
                text = article.excerpt,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .testTag("article_excerpt_${article.id}"),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 23.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AtharDownloadButton(
            title = article.title,
            sizeLabel = article.sizeLabel,
            downloaded = article.downloaded,
            transfer = article.transfer,
            onClick = onDownload,
            tag = "article_download_${article.id}",
        )
        AtharFavouriteButton(
            saved = article.saved,
            onToggle = onSave,
            itemTitle = article.title,
            tag = "article_save_${article.id}",
        )
    }
}
