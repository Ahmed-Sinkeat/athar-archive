package com.atharchive.feature.articles

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun ArticlesScreen(
    state: ArticlesUiState,
    onSettings: () -> Unit,
    onArticleClick: (ArticleUi) -> Unit,
    onSaveClick: (ArticleUi) -> Unit,
    onDownloadClick: (ArticleUi) -> Unit,
    modifier: Modifier = Modifier,
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ArticlesTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ArticleSort.Newest) }
    val listState = rememberLazyListState()

    // No author filter: 2,363 articles carry hundreds of distinct authors, and a flat
    // radio list does not scale to that. Sorting by author covers the same intent.
    val hasActiveFilters = sort != ArticleSort.Newest

    val visibleArticles = remember(state.articles, selectedTab, query, sort) {
        state.articles.filter { article ->
            val matchesTab = when (selectedTab) {
                ArticlesTab.All -> true
                ArticlesTab.Downloaded -> article.downloaded
                ArticlesTab.MyList -> article.saved
            }
            val term = query.trim()
            val matchesQuery = term.isBlank() ||
                article.title.contains(term, ignoreCase = true) ||
                article.author.contains(term, ignoreCase = true)
            matchesTab && matchesQuery
        }.sortedWith(
            when (sort) {
                ArticleSort.Title -> compareBy { it.title }
                ArticleSort.Author -> compareBy { it.author }
                ArticleSort.Newest -> compareBy { state.articles.indexOf(it) }
            },
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

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "المقالات",
                onSettings = onSettings,
                showAppIcon = true,
                onLogo = onLogo,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
            )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("articles_screen"),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "articles-search") {
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
            item(key = "articles-tabs") {
                AtharTabRow(
                    tabs = ArticlesTab.tabs,
                    selectedKey = selectedTab.key,
                    onSelect = { selectedTab = ArticlesTab.fromKey(it) },
                    tagPrefix = "articles",
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }
            item(key = "articles-menus") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding - 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AtharMenuButton(
                        label = "الترتيب",
                        options = ArticleSort.entries.map { it.label },
                        selected = sort.label,
                        defaultOption = ArticleSort.Newest.label,
                        onSelect = { label ->
                            sort = ArticleSort.entries.first { it.label == label }
                        },
                        tagPrefix = "articles_sort",
                    )
                }
            }
            item(key = "articles-list-divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    color = AtharTheme.colors.divider.copy(alpha = 0.72f),
                )
            }
            if (visibleArticles.isEmpty()) {
                item(key = "articles-empty") {
                    val (title, body) = when {
                        query.isNotBlank() ->
                            "لا توجد نتائج" to "جرّب عنوانًا أو اسم مؤلف أقصر."
                        selectedTab == ArticlesTab.Downloaded ->
                            "لا توجد مقالات محمّلة" to "نزّل مقالًا لتقرأه دون اتصال."
                        else ->
                            "قائمتك فارغة" to "ستظهر هنا المقالات المحفوظة في قائمتك."
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
        // Articles carry a plain downloaded flag, not Books' progress state machine —
        // one icon that flips to a check is the whole control.
        IconButton(
            onClick = onDownload,
            modifier = Modifier
                .size(38.dp)
                .testTag("article_download_${article.id}"),
        ) {
            Icon(
                imageVector = if (article.downloaded) AtharIcons.Check else AtharIcons.Download,
                contentDescription = if (article.downloaded) {
                    "محمّل: ${article.title}"
                } else {
                    "تنزيل ${article.title}"
                },
                tint = if (article.downloaded) {
                    AtharTheme.colors.success
                } else {
                    AtharTheme.colors.secondaryText
                },
                modifier = Modifier.size(19.dp),
            )
        }
        AtharBookmarkButton(
            saved = article.saved,
            onToggle = onSave,
            itemTitle = article.title,
            tag = "article_save_${article.id}",
        )
    }
}
