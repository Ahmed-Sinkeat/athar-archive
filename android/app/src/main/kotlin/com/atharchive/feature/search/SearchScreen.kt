package com.atharchive.feature.search

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import com.atharchive.ui.components.arabicDigits
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.withFrameNanos
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
    onQueryChange: (String) -> Unit,
    onFiltersChange: (SearchFilters) -> Unit,
    onOpenResult: (SearchHitUi) -> Unit,
    onOpenDirectMatch: (DirectMatchUi) -> Unit,
    onClearRecent: () -> Unit,
    onResultsDrawn: (String) -> Unit,
    inputQuery: String = state.query,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    loadPassage: suspend (String) -> String? = { null },
) {
    val query = inputQuery
    val filters = state.filters
    val searching = state.searching || inputQuery != state.query
    val listState = rememberLazyListState()

    fun changeFilters(next: SearchFilters) {
        onFiltersChange(next)
    }

    val term = query.trim()
    val hasQuery = term.isNotBlank()
    val hits = if (!hasQuery || searching) emptyList() else state.hits
    val directMatches = if (hasQuery) state.directMatches else emptyList()
    var allTitles by rememberSaveable(state.query) { mutableStateOf(false) }

    LaunchedEffect(state.query, state.directMatches, state.hits) {
        if (state.query.trim().length >= 2) {
            // Resume on the frame after these results entered composition, then close the
            // async trace that began at the matching keystroke.
            withFrameNanos { _ -> }
            onResultsDrawn(state.query)
        }
    }

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
                    placeholder = "ابحث في نصوص المكتبة…",
                    onQueryChange = {
                        onQueryChange(it)
                    },
                    tagPrefix = "search",
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 4.dp,
                    ),
                )
            }

            if (!hasQuery) {
                if (state.recentQueries.isEmpty()) {
                    item(key = "search-hint") {
                        AtharEmptyState(
                            title = "ابحث في المكتبة",
                            body = "اكتب كلمة أو عبارة. مع الاتصال يُبحث في نصوص المكتبة كلها، ودونه فيما نزّلته على جهازك.",
                            tag = "search_hint",
                            horizontalPadding = horizontalPadding,
                        )
                    }
                }
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
                                fontSize = AtharTextSize.Title,
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
                            onClick = {
                                onQueryChange(recent)
                            },
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

            item(key = "filter-menus") {
                SearchFilterMenus(filters, state.availableAuthors, ::changeFilters, Modifier.padding(horizontal = horizontalPadding - 4.dp))
            }
            item(key = "scope") {
                ScopeLine(state, searching, searchResultsReadyTag(state.query), horizontalPadding)
            }
            if (!searching && state.topAuthors.size > 1) item(key = "top-authors") {
                TopAuthors(state.topAuthors, filters.authors, horizontalPadding) { name ->
                    changeFilters(filters.copy(authors = if (name in filters.authors) filters.authors - name else filters.authors + name))
                }
            }

            if (directMatches.isNotEmpty()) {
                item(key = "direct-heading") {
                    ResultsHeading("في العناوين", horizontalPadding)
                }
                val shown = if (allTitles) directMatches else directMatches.take(3)
                items(shown, key = { "direct-${it.id}" }) { match ->
                    DirectMatchRow(
                        match = match,
                        horizontalPadding = horizontalPadding,
                        onClick = { onOpenDirectMatch(match) },
                    )
                }
                if (directMatches.size > shown.size) item(key = "direct-all") {
                    TextButton(onClick = { allTitles = true }, Modifier.padding(horizontal = horizontalPadding - 8.dp)) {
                        Text("كل العناوين (${arabicDigits(directMatches.size)})", color = AtharTheme.colors.accent)
                    }
                }
            }

            if (searching) {
                item(key = "search-loading") {
                    Text("جارٍ البحث في النصوص…", Modifier.padding(horizontal = horizontalPadding, vertical = 16.dp), color = AtharTheme.colors.secondaryText)
                }
                return@LazyColumn
            }
            if (hits.isEmpty()) {
                if (directMatches.isEmpty()) item(key = "search-empty") {
                    AtharEmptyState(
                        title = "لا توجد نتائج",
                        body = when (state.scope) {
                            SearchScope.Online -> "جرّب «بعض الكلمات» في الدقة، أو كلمة أقصر، أو امسح بعض المرشحات."
                            // Offline "no results" must not claim the archive is empty: it only saw the phone.
                            else -> "البحث الآن فيما نزّلته فقط. اتصل بالإنترنت للبحث في المكتبة كلها."
                        },
                        tag = searchResultsReadyTag(state.query),
                        horizontalPadding = horizontalPadding,
                    )
                }
                return@LazyColumn
            }

            item(key = "hits-heading") {
                ResultsHeading("في النصوص", horizontalPadding)
            }
            itemsIndexed(hits, key = { _, hit -> hit.id }) { index, hit ->
                SearchHitCard(
                    hit = hit,
                    horizontalPadding = horizontalPadding,
                    onClick = { onOpenResult(hit) },
                    loadPassage = loadPassage,
                )
                if (index != hits.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        thickness = 0.7.dp,
                        color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                    )
                }
            }
            if (state.hasMore) {
                item(key = "search-more") {
                    TextButton(onClick = onLoadMore, Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
                        Text("المزيد من النتائج", color = AtharTheme.colors.accent)
                    }
                }
            }
        }
    }
    }


}

@Composable
fun ResultsHeading(text: String, horizontalPadding: Dp) {
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

/** Says where the passages came from: the whole library online, or only what is on the phone. */
@Composable
private fun ScopeLine(state: SearchUiState, searching: Boolean, readyTag: String, horizontalPadding: Dp) {
    val online = state.scope == SearchScope.Online
    val text = when (state.scope) {
        SearchScope.Online -> "في كل المكتبة" + (state.total?.takeIf { !searching && it > 0 }?.let {
            " · " + (if (state.totalCapped) "أكثر من " else "") +
                com.atharchive.data.arabicCount(it, "", "موضع واحد", "موضعان", "مواضع", "موضعًا")
        } ?: "")
        SearchScope.Offline -> "دون اتصال · في المحمّلة فقط"
        SearchScope.Downloaded -> "في المحمّلة فقط"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 6.dp)
            .testTag(readyTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (online) AtharTheme.colors.accent else AtharTheme.colors.secondaryText.copy(alpha = 0.5f)))
        Text(
            text = text,
            modifier = Modifier.testTag("search_scope"),
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun searchResultsReadyTag(query: String): String =
    "search_results_${(query.hashCode().toLong() and 0xffffffffL).toString(16)}"

/** Who talks about this most: a tap narrows to that author (and shows as a chip in the filters). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TopAuthors(authors: List<AuthorCountUi>, selected: Set<String>, horizontalPadding: Dp, onToggle: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("الأكثر ذكرًا له:", Modifier.padding(vertical = 6.dp), color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.bodySmall)
        authors.forEach { author ->
            val on = author.name in selected
            Text(
                "${author.name} · ${arabicDigits(author.count)}",
                Modifier
                    .clip(AtharCorners.Control)
                    .background(if (on) AtharTheme.colors.accent.copy(alpha = 0.18f) else AtharTheme.colors.divider.copy(alpha = 0.28f))
                    .clickable(role = Role.Button) { onToggle(author.name) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = if (on) AtharTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
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
            .clip(AtharCorners.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("search_direct_${match.id}"),
        shape = AtharCorners.Card,
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
                    fontSize = AtharTextSize.Title,
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
fun SearchHitCard(
    hit: SearchHitUi,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    loadPassage: suspend (String) -> String?,
) {
    // The source first (which book, whose, where), then the passage with every match marked.
    val accent = AtharTheme.colors.accent
    var expanded by rememberSaveable(hit.id) { mutableStateOf(false) }
    var passage by remember(hit.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded) {
        if (expanded && passage == null && hit.url != null) passage = loadPassage(hit.url)?.let { around(it, hit) }
    }
    val text = passage ?: hit.excerpt
    val highlighted = remember(text, hit, accent) {
        val ranges = if (passage == null) hit.matches else wordRanges(text, hit)
        buildAnnotatedString {
            append(text)
            ranges.forEach { r ->
                if (r.first >= 0 && r.last < text.length && r.first <= r.last)
                    addStyle(SpanStyle(background = accent.copy(alpha = 0.18f), fontWeight = FontWeight.Bold), r.first, r.last + 1)
            }
        }
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = horizontalPadding, end = horizontalPadding, top = 12.dp, bottom = 2.dp)
            .testTag("search_hit_${hit.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = hit.sourceTitle,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = AtharTextSize.Ui,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TypeChip(hit.type.label)
        }
        Text(
            text = listOfNotNull(
                hit.sourceAuthor.ifBlank { null }?.let { name -> hit.died?.let { "$name (ت ${arabicDigits(it)}هـ)" } ?: name },
                hit.locationLabel,
            ).joinToString(" · "),
            modifier = Modifier.padding(top = 1.dp),
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = highlighted,
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = AtharEditorialFontFamily,
            fontSize = AtharTextSize.Title,
            lineHeight = 29.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (hit.url != null) TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "أقل" else "المزيد", color = AtharTheme.colors.accent, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(
                    "«${text.trim('…', ' ')}»\n— ${hit.sourceTitle}" + (hit.sourceAuthor.ifBlank { null }?.let { "، $it" } ?: ""),
                ))
            }) {
                Icon(AtharIcons.Copy, contentDescription = "نسخ النص مع مصدره", tint = AtharTheme.colors.secondaryText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** About 900 characters of [full] around the hit's first marked word. */
private fun around(full: String, hit: SearchHitUi): String {
    val word = hit.matches.firstOrNull()?.let { hit.excerpt.substring(it.first, it.last + 1) }
    val at = word?.let { full.indexOf(it) }?.takeIf { it >= 0 } ?: 0
    val start = (at - 400).coerceAtLeast(0)
    val end = (at + 500).coerceAtMost(full.length)
    return (if (start > 0) "…" else "") + full.substring(start, end) + (if (end < full.length) "…" else "")
}

/** Every occurrence, in [text], of the words the snippet marked. */
private fun wordRanges(text: String, hit: SearchHitUi): List<IntRange> =
    hit.matches.map { hit.excerpt.substring(it.first, it.last + 1) }.filter { it.length >= 2 }.distinct().flatMap { word ->
        generateSequence(text.indexOf(word).takeIf { it >= 0 }) { from -> text.indexOf(word, from + word.length).takeIf { it >= 0 } }
            .map { it until it + word.length }.toList()
    }

@Composable
private fun TypeChip(label: String) {
    Surface(
        shape = AtharCorners.Tag,
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

/** A section's own full-text search: passages shown under its title matches. */
class SectionTextSearch(
    val search: suspend (String) -> List<SearchHitUi>,
    val loadPassage: suspend (String) -> String?,
    /** The hit and the query that found it (to mark the match in the reader). */
    val onOpen: (SearchHitUi, String) -> Unit,
)

@Composable
fun rememberSectionTextHits(query: String, textSearch: SectionTextSearch?): List<SearchHitUi> {
    val term = query.trim()
    return androidx.compose.runtime.produceState(emptyList<SearchHitUi>(), term, textSearch) {
        value = emptyList()
        if (textSearch == null || term.length < 2) return@produceState
        kotlinx.coroutines.delay(300) // typing pause; the next keystroke restarts this
        value = runCatching { textSearch.search(term) }.getOrDefault(emptyList())
    }.value
}

fun androidx.compose.foundation.lazy.LazyListScope.sectionTextHits(hits: List<SearchHitUi>, query: String, textSearch: SectionTextSearch?, horizontalPadding: Dp) {
    if (hits.isEmpty() || textSearch == null) return
    item(key = "text-hits-heading") { ResultsHeading("في النصوص", horizontalPadding) }
    items(hits, key = { "text-${it.id}" }) { hit ->
        SearchHitCard(hit, horizontalPadding, onClick = { textSearch.onOpen(hit, query.trim()) }, loadPassage = textSearch.loadPassage)
    }
}
