package com.atharchive.feature.search

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

@Composable
fun SearchScreen(
    state: SearchUiState,
    onSettings: () -> Unit,
    onOpenResult: (SearchHitUi) -> Unit,
    onOpenDirectMatch: (DirectMatchUi) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(SearchFilters()) }
    var filtersVisible by rememberSaveable { mutableStateOf(false) }
    var scopedSource by rememberSaveable { mutableStateOf(state.scopedToSource) }
    val listState = rememberLazyListState()

    val term = query.trim()
    val hasQuery = term.isNotBlank()

    val hits = remember(state.hits, term, filters, scopedSource) {
        if (!hasQuery) {
            emptyList()
        } else {
            state.hits
                .filter { hit ->
                    (filters.types.isEmpty() || hit.type in filters.types) &&
                        (filters.sources.isEmpty() || hit.sourceTitle in filters.sources) &&
                        (filters.authors.isEmpty() || hit.sourceAuthor in filters.authors) &&
                        (scopedSource == null || hit.sourceTitle == scopedSource)
                }
                .let { list ->
                    // Relevance is the engine's order, so the default is the order given.
                    if (filters.sort == SearchSort.Newest) list.reversed() else list
                }
        }
    }
    val directMatches = if (hasQuery && scopedSource == null) state.directMatches else emptyList()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_search")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "البحث",
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
                .testTag("search_screen"),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "search-field") {
                AtharSearchField(
                    query = query,
                    placeholder = "ابحث في أهل الأثر…",
                    onQueryChange = { query = it },
                    tagPrefix = "search",
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 4.dp,
                    ),
                )
            }

            // Scope and filter chips: shown only when something is actually narrowing
            // the search. An empty chip row is furniture.
            if (scopedSource != null || filters.isActive) {
                item(key = "search-chips") {
                    ActiveChips(
                        scopedSource = scopedSource,
                        filters = filters,
                        horizontalPadding = horizontalPadding,
                        onClearScope = { scopedSource = null },
                        onClearType = { filters = filters.copy(types = filters.types - it) },
                        onClearSource = { filters = filters.copy(sources = filters.sources - it) },
                        onClearAuthor = { filters = filters.copy(authors = filters.authors - it) },
                        onResetField = { filters = filters.copy(field = SearchField.FullText) },
                    )
                }
            }

            if (!hasQuery) {
                if (state.recentQueries.isNotEmpty()) {
                    item(key = "recent-heading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = horizontalPadding,
                                    end = horizontalPadding - 8.dp,
                                    top = 18.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "عمليات البحث الأخيرة",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontFamily = AtharEditorialFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            TextButton(
                                onClick = onClearRecent,
                                modifier = Modifier.testTag("search_clear_recent"),
                            ) {
                                Text(
                                    "مسح",
                                    color = AtharTheme.colors.accent,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    itemsIndexed(state.recentQueries, key = { _, q -> "recent-$q" }) { index, recent ->
                        RecentRow(
                            query = recent,
                            horizontalPadding = horizontalPadding,
                            onClick = { query = recent },
                        )
                        if (index != state.recentQueries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    start = horizontalPadding + 34.dp,
                                    end = horizontalPadding,
                                ),
                                thickness = 0.7.dp,
                                color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                            )
                        }
                    }
                }
                return@LazyColumn
            }

            if (!state.online) {
                item(key = "offline-note") {
                    OfflineNote(horizontalPadding = horizontalPadding)
                }
            }

            if (hits.isEmpty() && directMatches.isEmpty()) {
                item(key = "search-empty") {
                    // Offline "no results" must not claim the archive is empty — it only
                    // ever saw what is on the device.
                    AtharEmptyState(
                        title = if (state.online) {
                            "لا توجد نتائج"
                        } else {
                            "لم نجد نتائج في المحتوى المحمّل"
                        },
                        body = if (state.online) {
                            "جرّب كلمة أقصر، أو امسح بعض المرشحات."
                        } else {
                            "اتصل بالإنترنت للبحث في الأرشيف كاملًا."
                        },
                        tag = "search_empty_state",
                        horizontalPadding = horizontalPadding,
                    )
                }
                return@LazyColumn
            }

            item(key = "results-bar") {
                ResultsBar(
                    count = hits.size,
                    filtersActive = filters.isActive,
                    sort = filters.sort,
                    horizontalPadding = horizontalPadding,
                    onFilters = { filtersVisible = true },
                    onSortChange = { filters = filters.copy(sort = it) },
                )
            }

            if (directMatches.isNotEmpty()) {
                item(key = "direct-heading") {
                    ResultsHeading("مطابقات مباشرة", horizontalPadding)
                }
                items(directMatches, key = { "direct-${it.id}" }) { match ->
                    DirectMatchRow(
                        match = match,
                        horizontalPadding = horizontalPadding,
                        onClick = { onOpenDirectMatch(match) },
                    )
                }
            }

            if (hits.isNotEmpty()) {
                item(key = "hits-heading") {
                    ResultsHeading("أفضل النتائج", horizontalPadding)
                }
                itemsIndexed(hits, key = { _, hit -> hit.id }) { index, hit ->
                    HitRow(
                        hit = hit,
                        query = term,
                        horizontalPadding = horizontalPadding,
                        onClick = { onOpenResult(hit) },
                    )
                    if (index != hits.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = horizontalPadding),
                            thickness = 0.7.dp,
                            color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                        )
                    }
                }
            }

            if (!state.online) {
                item(key = "offline-footer") {
                    Text(
                        text = "اتصل بالإنترنت للبحث في الأرشيف كاملًا",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 20.dp)
                            .testTag("search_offline_footer"),
                        color = AtharTheme.colors.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    }

    if (filtersVisible) {
        SearchFilterSheet(
            filters = filters,
            sources = state.availableSources,
            authors = state.availableAuthors,
            onChange = { filters = it },
            onClear = { filters = SearchFilters() },
            onDismiss = { filtersVisible = false },
        )
    }
}

@Composable
private fun ResultsHeading(text: String, horizontalPadding: Dp) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = horizontalPadding,
            end = horizontalPadding,
            top = 18.dp,
            bottom = 4.dp,
        ),
        color = AtharTheme.colors.secondaryText,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ResultsBar(
    count: Int,
    filtersActive: Boolean,
    sort: SearchSort,
    horizontalPadding: Dp,
    onFilters: () -> Unit,
    onSortChange: (SearchSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding - 4.dp, end = horizontalPadding - 4.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${arabicNumber(count)} نتيجة",
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f)
                .testTag("search_result_count"),
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onFilters)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("search_filters"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AtharIcons.Filter,
                contentDescription = null,
                tint = if (filtersActive) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "تصفية",
                modifier = Modifier.padding(start = 5.dp),
                color = if (filtersActive) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (filtersActive) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        AtharMenuButton(
            label = SearchSort.Relevance.label,
            options = SearchSort.entries.map { it.label },
            selected = sort.label,
            defaultOption = SearchSort.Relevance.label,
            onSelect = { label -> onSortChange(SearchSort.entries.first { it.label == label }) },
            tagPrefix = "search_sort",
        )
    }
}

@Composable
private fun OfflineNote(horizontalPadding: Dp) {
    // Informational, never alarming: no red, no banner, no retry button.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 10.dp)
            .testTag("search_offline_note"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = AtharIcons.Info,
            contentDescription = null,
            tint = AtharTheme.colors.secondaryText.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "البحث في المحتوى المحمّل فقط",
            modifier = Modifier.weight(1f),
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "دون اتصال",
            color = AtharTheme.colors.secondaryText.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun RecentRow(query: String, horizontalPadding: Dp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 6.dp)
            .testTag("search_recent_$query"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = AtharIcons.Recent,
            contentDescription = null,
            tint = AtharTheme.colors.secondaryText.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = query,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DirectMatchRow(
    match: DirectMatchUi,
    horizontalPadding: Dp,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("search_direct_${match.id}"),
        shape = RoundedCornerShape(12.dp),
        color = AtharTheme.colors.raisedSurface,
        border = BorderStroke(1.dp, AtharTheme.colors.divider.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = match.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                )
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeChip(match.kindLabel)
                    Text(
                        text = match.contextLabel,
                        color = AtharTheme.colors.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HitRow(
    hit: SearchHitUi,
    query: String,
    horizontalPadding: Dp,
    onClick: () -> Unit,
) {
    // The excerpt is the result. Source, location and type support it, in that order,
    // and each is a clear step quieter than the one above.
    val accent = AtharTheme.colors.accent
    val highlighted = remember(hit, query, accent) {
        highlightMatches(hit.excerpt, query, accent.copy(alpha = 0.18f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 12.dp)
            .testTag("search_hit_${hit.id}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = highlighted,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontSize = 16.sp,
                lineHeight = 29.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${hit.sourceTitle} · ${hit.sourceAuthor}",
                modifier = Modifier.padding(top = 4.dp),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.locationLabel != null) {
                Text(
                    text = hit.locationLabel,
                    modifier = Modifier.padding(top = 1.dp),
                    color = AtharTheme.colors.secondaryText.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        TypeChip(hit.type.label)
    }
}

@Composable
private fun TypeChip(label: String) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = AtharTheme.colors.raisedSurface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            color = AtharTheme.colors.secondaryText.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActiveChips(
    scopedSource: String?,
    filters: SearchFilters,
    horizontalPadding: Dp,
    onClearScope: () -> Unit,
    onClearType: (SearchResultType) -> Unit,
    onClearSource: (String) -> Unit,
    onClearAuthor: (String) -> Unit,
    onResetField: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp)
            .testTag("search_active_chips"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (scopedSource != null) RemovableChip(scopedSource, onClearScope)
        filters.types.forEach { RemovableChip(it.label) { onClearType(it) } }
        filters.sources.forEach { RemovableChip(it) { onClearSource(it) } }
        filters.authors.forEach { RemovableChip(it) { onClearAuthor(it) } }
        if (filters.field != SearchField.FullText) {
            RemovableChip(filters.field.label, onResetField)
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = AtharTheme.colors.accent.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onRemove)
                .padding(start = 9.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = AtharTheme.colors.accent,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Icon(
                imageVector = AtharIcons.Close,
                contentDescription = "إزالة $label",
                tint = AtharTheme.colors.accent,
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(12.dp),
            )
        }
    }
}

private fun arabicNumber(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
