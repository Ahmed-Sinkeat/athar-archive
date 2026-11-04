package com.atharchive.feature.search

import com.atharchive.core.data.repository.isTashkeel
import com.atharchive.ui.components.atharControl
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import com.atharchive.ui.components.arabicDigits
import com.atharchive.data.arabicCount
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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
    presets: List<SearchPreset> = emptyList(),
    onSavePreset: (String) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onResultsDrawn: (String) -> Unit,
    inputQuery: String = state.query,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    loadPassage: suspend (SearchHitUi) -> String? = { null },
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
    // The last results stay up while the next ones load: nothing blanks between keystrokes.
    val hits = if (!hasQuery) emptyList() else state.hits
    val directMatches = if (hasQuery) state.directMatches else emptyList()
    var allTitles by rememberSaveable(state.query) { mutableStateOf(false) }
    // «معجم»: the query looked up in the lexicons instead of the texts.
    var lexicon by rememberSaveable { mutableStateOf(false) }
    // groupBy keeps first-seen order, so each book sits where its best passage ranked.
    // The same passage twice (an index row left over by an update) shows once.
    val bookGroups = remember(hits) { hits.distinctBy { it.entityId to it.excerpt }.groupBy { it.entityId }.values.toList() }
    var openBooks by remember(state.query) { mutableStateOf(emptySet<String>()) }

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
                    trailing = {
                        // «الأقسام» in the field, as in every section: one at a time, out of the crowded filter.
                        com.atharchive.ui.components.AtharSectionButton(
                            filters.sections.singleOrNull() ?: com.atharchive.ui.components.SectionAll,
                            { changeFilters(filters.copy(sections = if (it == com.atharchive.ui.components.SectionAll) emptySet() else setOf(it))) },
                            emptyMap(), "search_sections",
                        )
                        SearchFilterButton(filters, state.availableAuthors, state.availableBooks, presets, onSavePreset, onDeletePreset, ::changeFilters)
                    },
                )
                SearchActiveFilters(filters, ::changeFilters, Modifier.padding(horizontal = horizontalPadding))
                SearchTypeTabs(filters, lexicon, { lexicon = it }, ::changeFilters, Modifier.padding(horizontal = horizontalPadding - 8.dp))
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
                    itemsIndexed(state.recentQueries, key = { _, q -> "recent-$q"  }) { index, recent -> com.atharchive.ui.components.AtharRowItem {
                        RecentRow(
                            query = recent,
                            horizontalPadding = horizontalPadding,
                            onClick = {
                                onQueryChange(recent)
                            },
                        )
                        if (index != state.recentQueries.lastIndex) {
                            com.atharchive.ui.components.AtharRowDivider(
                                modifier = Modifier.padding(
                                    start = horizontalPadding + 34.dp,
                                    end = horizontalPadding,
                                ),
                                thickness = 0.7.dp,
                                color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                            )
                        }
                    }}
                }
                return@LazyColumn
            }

            if (lexicon) {
                item(key = "lexicon-${state.query.trim()}") { LexiconResults(state.query.trim(), horizontalPadding) }
                return@LazyColumn
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

            if (searching && hits.isEmpty()) {
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
                ResultsHeading(if (searching) "في النصوص · جارٍ البحث في المكتبة كلها…" else "في النصوص", horizontalPadding)
            }
            // One card per book at its best hit's rank; the book's other passages open under it.
            itemsIndexed(bookGroups, key = { _, group -> group.first().id }) { index, group ->
                val entityId = group.first().entityId
                val open = entityId in openBooks
                @Composable fun card(hit: SearchHitUi) = SearchHitCard(
                    hit = hit,
                    horizontalPadding = horizontalPadding,
                    plain = filters.plain,
                    onClick = { onOpenResult(hit) },
                    loadPassage = loadPassage,
                )
                card(group.first())
                // The toggle stays under the best hit, so closing the book's other passages needs no scroll down.
                if (group.size > 1) TextButton(
                    onClick = { openBooks = if (open) openBooks - entityId else openBooks + entityId },
                    modifier = Modifier.padding(horizontal = horizontalPadding - 12.dp).testTag("search_more_in_book"),
                ) {
                    Text(
                        if (open) "إخفاء المواضع الأخرى"
                        else arabicCount(group.size - 1, "", "موضع آخر", "موضعان آخران", "مواضع أخرى", "موضعًا آخر") + " في هذا الكتاب",
                        color = AtharTheme.colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (open) group.drop(1).forEach { card(it) }
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

/** الكل · كتب · شعر · مقالات · مسائل · معجم — one tap narrows the results to one kind, or to the lexicons. */
@Composable
private fun SearchTypeTabs(
    filters: SearchFilters, lexicon: Boolean, onLexicon: (Boolean) -> Unit,
    onChange: (SearchFilters) -> Unit, modifier: Modifier = Modifier,
) {
    val tabs = listOf(com.atharchive.ui.components.AtharTab("all", "الكل")) +
        SearchResultType.entries.map { com.atharchive.ui.components.AtharTab(it.key, it.plural) } +
        com.atharchive.ui.components.AtharTab("lexicon", "معجم")
    // Several kinds picked in the filter menu: no single tab is the selection.
    val selected = if (lexicon) "lexicon" else when (filters.types.size) { 0 -> "all"; 1 -> filters.types.first().key; else -> "" }
    com.atharchive.ui.components.AtharTabRow(tabs, selected, { key ->
        onLexicon(key == "lexicon")
        if (key != "lexicon") onChange(filters.copy(types = SearchResultType.entries.filter { it.key == key }.toSet()))
    }, tagPrefix = "search_type", modifier = modifier)
}

/**
 * The «معجم» tab: every lexicon's entry for the word, in the lexicons' order, each opening its book
 * there. Offers the one-time download when the lexicons aren't on the phone yet.
 */
@Composable
private fun LexiconResults(query: String, horizontalPadding: Dp) {
    val dict = com.atharchive.feature.reader.LocalDictionary.current ?: return
    val open = com.atharchive.feature.reader.LocalOpenBookAt.current
    val installed by dict.installed.collectAsState()
    val progress by dict.progress.collectAsState()
    val failed by dict.failed.collectAsState()
    // ponytail: the first word only; the lexicons hold single words, a phrase has no entry.
    // An emptied box (or only spaces) recomposes this once more before the tab goes: no word, nothing shown.
    val word = query.split(' ').firstOrNull { it.isNotBlank() } ?: return
    val found by androidx.compose.runtime.produceState<Pair<Boolean, com.atharchive.data.Meaning?>>(false to null, word, installed) {
        value = if (installed) true to runCatching { dict.meaning(word) }.getOrNull() else false to null
    }
    val pad = Modifier.padding(horizontal = horizontalPadding)
    Column(Modifier.fillMaxWidth().testTag("search_lexicon")) {
        if (!installed) {
            val p = progress
            AtharEmptyState(
                title = "معاجم اللغة",
                body = "معاني الكلمات من أمّات المعاجم؛ تُنزَّل مرة واحدة وتعمل دون اتصال.",
                tag = "search_lexicon_offer",
                horizontalPadding = horizontalPadding,
            )
            TextButton(onClick = { dict.startDownload() }, enabled = p == null, modifier = pad.testTag("search_lexicon_download")) {
                Text(when {
                    p != null -> "جارٍ التنزيل… " + (if (p.second > 0) "${p.first * 100 / p.second}٪" else "${p.first shr 20} م.ب")
                    failed -> "تعذّر التنزيل؛ اضغط لإعادة المحاولة"
                    else -> "تنزيل المعجم"
                }, color = AtharTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
            return@Column
        }
        val m = found.second
        if (m == null) {
            if (found.first) AtharEmptyState(title = "لا توجد الكلمة في المعاجم", body = "جرّب الكلمة دون زوائد، أو جذرها.",
                tag = "search_lexicon_empty", horizontalPadding = horizontalPadding)
            return@Column
        }
        ResultsHeading(m.word + "  ·  " + m.roots.joinToString("  ،  ") { it.toList().joinToString(" ") }, horizontalPadding)
        m.entries.forEach { e ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable(enabled = open != null, role = androidx.compose.ui.semantics.Role.Button) { open?.invoke(e.bookId, e.ordinal) }
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
            ) {
                Text(e.source, color = AtharTheme.colors.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(e.snippet, Modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodyMedium)
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
    } + when {
        state.roots == null -> ""
        state.roots.isEmpty() -> " · لم يُعرف الجذر (المعاجم تلزم للبحث بالجذر)"
        else -> " · الجذر: " + state.roots.joinToString("، ") { it.toList().joinToString(" ") }
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
                    .then(if (on) Modifier.background(AtharTheme.colors.accent.copy(alpha = 0.18f)) else Modifier.atharControl(AtharTheme.colors.divider.copy(alpha = 0.28f), AtharCorners.Control))
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = match.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = AtharTextSize.Ui,
                    lineHeight = 23.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeChip(match.kindLabel, match.type.tint())
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
    loadPassage: suspend (SearchHitUi) -> String?,
    plain: Boolean = false,
) {
    // The source first (which book, whose, where), then the passage with every match marked.
    val accent = AtharTheme.colors.accent
    var expanded by rememberSaveable(hit.id) { mutableStateOf(false) }
    var passage by remember(hit.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded) {
        if (expanded && passage == null && hit.url != null) passage = loadPassage(hit)?.let { around(it, hit) }
    }
    val poem = hit.type == SearchResultType.Poem
    val text = (passage ?: hit.excerpt).let { if (poem) verseLines(it) else it }
    val highlighted = remember(text, hit, accent, plain) {
        val marked = if (passage == null) hit.matches else wordRanges(text, hit)
        val (shown, ranges) = if (plain) withoutTashkeel(text, marked) else text to marked
        buildAnnotatedString {
            append(shown)
            ranges.forEach { r ->
                if (r.first >= 0 && r.last < shown.length && r.first <= r.last)
                    addStyle(SpanStyle(background = accent.copy(alpha = 0.22f), color = accent, fontWeight = FontWeight.Bold), r.first, r.last + 1)
            }
        }
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val tint = hit.type.tint()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 5.dp)
            .clip(AtharCorners.Card)
            .background(AtharTheme.colors.raisedSurface)
            // the type's colour down the card's leading edge
            .drawBehind {
                val bar = 3.5.dp.toPx()
                drawRect(tint, topLeft = androidx.compose.ui.geometry.Offset(if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - bar else 0f, 0f),
                    size = androidx.compose.ui.geometry.Size(bar, size.height))
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)
            .testTag("search_hit_${hit.id}"),
    ) {
        // Title, then what it is (كتاب · author · place) right under it, then two lines of the passage.
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (hit.url != null) IconButton(onClick = { expanded = !expanded }, Modifier.size(36.dp)) {
                Icon(AtharIcons.ChevronDown, contentDescription = if (expanded) "أقل" else "المزيد", tint = AtharTheme.colors.secondaryText,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f })
            }
            IconButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(
                    "«${text.trim('…', ' ')}»\n— ${hit.sourceTitle}" + (hit.sourceAuthor.ifBlank { null }?.let { "، $it" } ?: ""),
                ))
                android.widget.Toast.makeText(context, "نُسخ النص مع مصدره", android.widget.Toast.LENGTH_SHORT).show()
            }, Modifier.size(36.dp)) {
                Icon(AtharIcons.Copy, contentDescription = "نسخ النص مع مصدره", tint = AtharTheme.colors.secondaryText, modifier = Modifier.size(16.dp))
            }
        }
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TypeChip(hit.type.label, tint)
            Text(
                text = listOfNotNull(
                    hit.sourceAuthor.ifBlank { null }?.let { name -> hit.died?.let { "$name (ت ${arabicDigits(it)}هـ)" } ?: name },
                    hit.locationLabel,
                ).joinToString(" · "),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = highlighted,
            modifier = Modifier.padding(top = 5.dp, end = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = AtharEditorialFontFamily,
            fontSize = AtharTextSize.Ui,
            lineHeight = 25.sp,
            maxLines = if (expanded) Int.MAX_VALUE else if (poem) 4 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (poem) androidx.compose.ui.text.style.TextAlign.Center else null,
        )
    }
}

// Verse numbers are Arabic-Indic digits, written as escapes: never Arabic characters in a class.
private val VerseStart = Regex(" (?=[\u0660-\u0669]+ - )")

/**
 * A poem's passage («١ - صدر … عجز ٢ - …») as one hemistich to a line. Same length as [text],
 * so the match offsets still fall on the right words.
 */
internal fun verseLines(text: String) = text.replace(" … ", " \n ").replace(" --- ", "  \n  ").replace(VerseStart, "\n")

/**
 * About 900 characters of [full] around this hit: where its own snippet sits in the passage (not
 * the first time its word appears, which every hit in the chapter shares), else its first word.
 */
internal fun around(full: String, hit: SearchHitUi): String {
    val snippet = hit.excerpt.trim('…', ' ')
    val word = hit.matches.firstOrNull()?.let { hit.excerpt.substring(it.first, it.last + 1) }
    val at = full.indexOf(snippet).takeIf { it >= 0 && snippet.isNotEmpty() }?.let { it + snippet.length / 2 }
        ?: word?.let { full.indexOf(it) }?.takeIf { it >= 0 } ?: 0
    val start = (at - 400).coerceAtLeast(0)
    val end = (at + 500).coerceAtMost(full.length)
    return (if (start > 0) "…" else "") + full.substring(start, end) + (if (end < full.length) "…" else "")
}

/** [text] without its tashkeel, with [ranges] moved onto the same letters. */
internal fun withoutTashkeel(text: String, ranges: List<IntRange>): Pair<String, List<IntRange>> {
    val out = StringBuilder(text.length)
    val at = IntArray(text.length + 1)
    text.forEachIndexed { i, c -> at[i] = out.length; if (!isTashkeel(c)) out.append(c) }
    at[text.length] = out.length
    return out.toString() to ranges.filter { it.first >= 0 && it.last < text.length }.map { at[it.first] until at[it.last + 1] }
}

/** Every occurrence, in [text], of the words the snippet marked. */
private fun wordRanges(text: String, hit: SearchHitUi): List<IntRange> =
    hit.matches.map { hit.excerpt.substring(it.first, it.last + 1) }.filter { it.length >= 2 }.distinct().flatMap { word ->
        generateSequence(text.indexOf(word).takeIf { it >= 0 }) { from -> text.indexOf(word, from + word.length).takeIf { it >= 0 } }
            .map { it until it + word.length }.toList()
    }

/** One colour per kind of source, readable on paper and on the dark theme alike. */
@Composable
private fun SearchResultType.tint(): Color = when (this) {
    SearchResultType.Book -> AtharTheme.colors.accent
    SearchResultType.Poem -> Color(0xFF2E8B74)
    SearchResultType.Article -> Color(0xFFB7832B)
    SearchResultType.Issue -> Color(0xFF4A7DB5)
}

@Composable
private fun TypeChip(label: String, tint: Color = AtharTheme.colors.secondaryText) {
    Surface(
        shape = AtharCorners.Tag,
        color = tint.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = tint,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** A section's own full-text search: passages shown under its title matches. */
class SectionTextSearch(
    val search: suspend (String) -> List<SearchHitUi>,
    val loadPassage: suspend (SearchHitUi) -> String?,
    /** The hit and the query that found it (to mark the match in the reader). */
    val onOpen: (SearchHitUi, String) -> Unit,
)

/** The passages found so far, and whether the search behind them is still running. */
class SectionTextHits(val hits: List<SearchHitUi>, val searching: Boolean) {
    fun isEmpty() = hits.isEmpty()
}

@Composable
fun rememberSectionTextHits(query: String, textSearch: SectionTextSearch?): SectionTextHits {
    val term = query.trim()
    return androidx.compose.runtime.produceState(SectionTextHits(emptyList(), false), term, textSearch) {
        // Searching until answered, so the list never claims «لا توجد نتائج» while the server (≈1.5 s) works.
        val active = textSearch != null && term.length >= 2
        value = SectionTextHits(emptyList(), active)
        if (!active) return@produceState
        kotlinx.coroutines.delay(300) // typing pause; the next keystroke restarts this
        value = SectionTextHits(runCatching { textSearch!!.search(term) }.getOrDefault(emptyList()), false)
    }.value
}

fun androidx.compose.foundation.lazy.LazyListScope.sectionTextHits(found: SectionTextHits, query: String, textSearch: SectionTextSearch?, horizontalPadding: Dp) {
    if (textSearch == null) return
    if (found.searching) {
        item(key = "text-hits-searching") {
            Text(
                "جارٍ البحث في النصوص…",
                Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp).testTag("text_hits_searching"),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    val hits = found.hits
    if (hits.isEmpty()) return
    item(key = "text-hits-heading") { ResultsHeading("في النصوص", horizontalPadding) }
    items(hits, key = { "text-${it.id}" }) { hit ->
        SearchHitCard(hit, horizontalPadding, onClick = { textSearch.onOpen(hit, query.trim()) }, loadPassage = textSearch.loadPassage)
    }
}

