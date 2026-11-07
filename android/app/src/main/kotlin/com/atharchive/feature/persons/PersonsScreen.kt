package com.atharchive.feature.persons

import com.atharchive.ui.components.AtharFastScroller
import com.atharchive.ui.components.normKey
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.core.ArNormalize
import com.atharchive.core.data.content.CatalogPerson
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.ui.components.*
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

private val Gutter = 20.dp

private fun century(died: Int?) = died?.let { com.atharchive.data.centuryOf(it) }

/** «له»: what kind of work a person has here. */
private val WorkKinds = listOf("book" to "كتب", "poem" to "قصائد", "article" to "مقالات", "question" to "مسائل")

@Composable
fun PersonsScreen(persons: List<CatalogPerson>, works: Map<String, ContentEntity>, onPerson: (CatalogPerson) -> Unit, onSettings: () -> Unit, onBack: (() -> Unit)?) {
    var query by rememberSaveable { mutableStateOf("") }
    var centuryStored by rememberPersisted("persons.centuries", "")
    var kindStored by rememberPersisted("persons.kinds", "")
    var section by rememberPersisted("persons.section", SectionAll)
    var byName by rememberPersisted("persons.byName", "")
    var descendingStored by rememberPersisted("persons.desc", "")
    val selectedCentury = centuryStored.asChoiceSet()
    val kind = kindStored.asChoiceSet()
    val descending = descendingStored.isNotEmpty()
    // Per person: the collections and topics of their works here, once per catalog change.
    val facts = remember(persons, works) {
        persons.associate { p ->
            val own = p.works.mapNotNull(works::get)
            p.id to (own.mapTo(hashSetOf()) { it.coll } to own.flatMap { it.topicsCsv.split(',') }.map { com.atharchive.data.topicName(it) })
        }
    }
    val visible = remember(persons, query, selectedCentury, kind, section, byName, descending, facts) {
        val term = ArNormalize.normalize(query.trim())
        persons.filter {
            val (colls, topics) = facts.getValue(it.id)
            (term.isBlank() || com.atharchive.ui.components.matchesWords(term, normKey(it.name))) &&
                (selectedCentury.isEmpty() || century(it.died) in selectedCentury) &&
                (kind.isEmpty() || WorkKinds.any { (coll, label) -> label in kind && coll in colls }) &&
                inSection(section, topics)
        }.sortedWith(
            if (byName.isNotEmpty()) compareBy<CatalogPerson> { normKey(it.name) }.directed(descending)
            else compareBy<CatalogPerson, Int?>(nullsLast(if (descending) reverseOrder() else naturalOrder())) { it.died }.thenBy { normKey(it.name) },
        )
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(AtharTheme.colors.canvas).clipToBounds()) {
        val header = rememberAtharCollapsingHeader()
        header.ShowOnChange(listState, query, selectedCentury, kind, section, byName)
        AtharFloatingHeader(header) {
            AtharTopBar(title = "الأعلام", onSettings = onSettings, showAppIcon = true, onBack = onBack)
            val filterGroups = listOf(
                AtharChipGroup("الترتيب", listOf("الوفاة", "الاسم"), if (byName.isEmpty()) "الوفاة" else "الاسم") { byName = if (it == "الاسم") "1" else "" },
                AtharDirectionGroup(descending) { descendingStored = if (it) "1" else "" },
            )
            val filterFacets = listOf(
                centuryFacet(selectedCentury) { centuryStored = it.asStoredChoices() },
                AtharFacet("له", WorkKinds.map { it.second }, kind, { kindStored = it.asStoredChoices() }),
            )
            AtharSearchField(
                query = query,
                resultCount = visible.size,
                onQueryChange = { query = it },
                placeholder = "ابحث في ${arabicDigits(persons.size)} علمًا…",
                tagPrefix = "persons",
                trailing = {
                        com.atharchive.ui.components.AtharSectionButton(section, { section = it }, remember(persons, facts) { com.atharchive.ui.components.sectionCounts(persons) { facts.getValue(it.id).second } }, "persons_sections")
                        AtharFilterIcon(filterGroups, filterFacets, "persons_filter")
                    },
                modifier = Modifier.padding(start = Gutter, end = Gutter, top = 4.dp),
            )
            HorizontalDivider(Modifier.padding(horizontal = Gutter, vertical = 2.dp), 0.7.dp, AtharTheme.colors.divider.copy(alpha = 0.5f))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(header.connection).atharBackdrop(header.haze),
            contentPadding = PaddingValues(top = header.height, bottom = 28.dp),
        ) {
            if (visible.isEmpty()) item(key = "persons-empty") {
                val (title, body) = when {
                    persons.isEmpty() -> "لا أعلام بعد" to "تظهر الأعلام بعد تحديث الفهرس من الإنترنت."
                    query.isNotBlank() -> "لا توجد نتائج" to "جرّب جزءًا من الاسم."
                    else -> "لا أعلام بهذه التصفية" to "امسح بعض المرشحات لعرض الجميع."
                }
                AtharEmptyState(title, body, "persons_empty_state", Gutter)
            }
            itemsIndexed(visible, key = { _, person -> person.id  }) { index, person -> com.atharchive.ui.components.AtharRowItem {
                PersonRow(
                    title = person.name,
                    detail = listOfNotNull(
                        person.died?.let { "ت ${arabicDigits(it)} هـ" },
                        person.works.size.takeIf { it > 0 }?.let { when (it) { 1 -> "مؤلَّف واحد"; 2 -> "مؤلَّفان"; in 3..10 -> "${arabicDigits(it)} مؤلَّفات"; else -> "${arabicDigits(it)} مؤلَّفًا" } },
                    ).joinToString(" · "),
                    query = query,
                    onClick = { onPerson(person) },
                )
                if (index != visible.lastIndex) RowDivider()
            }}
        }
        AtharFastScroller(listState, top = header.height)
    }
}

/**
 * An author's page: the same list as الكتب (search, sort, filters, tabs, download and save),
 * over everything of theirs here, with their dates, bio and «تنزيل الكل» above it.
 */
@Composable
fun PersonScreen(
    person: CatalogPerson,
    works: List<com.atharchive.feature.books.BookUi>,
    loaded: Boolean,
    downloadAll: com.atharchive.feature.downloads.BulkRowUi?,
    onDownloadAll: (com.atharchive.feature.downloads.BulkRowUi, mobileData: Boolean) -> Unit,
    onPauseAll: (com.atharchive.feature.downloads.BulkRowUi) -> Unit,
    onWork: (com.atharchive.feature.books.BookUi) -> Unit,
    onDownload: (com.atharchive.feature.books.BookUi) -> Unit,
    onSave: (com.atharchive.feature.books.BookUi) -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    com.atharchive.feature.books.BooksScreen(
        state = com.atharchive.feature.books.BooksUiState(
            com.atharchive.data.arabicCount(works.size, "مؤلفاته", "مؤلَّف واحد", "مؤلَّفين", "مؤلَّفات", "مؤلَّفًا"), works, loaded,
        ),
        onSettings = onSettings,
        onBookClick = onWork,
        onDownloadClick = onDownload,
        onSaveClick = onSave,
        onBack = onBack,
        title = person.name,
        keyPrefix = "person",
        byKind = true,
        listHeader = {
            Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                person.died?.let { Text("ت ${arabicDigits(it)} هـ", color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.bodyMedium) }
                if (!person.bio.isNullOrBlank()) Text(person.bio.orEmpty(), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
            if (downloadAll != null && downloadAll.items.size > 1) {
                com.atharchive.feature.downloads.BulkRow(downloadAll, Gutter, { onDownloadAll(downloadAll, it) }, { onPauseAll(downloadAll) })
                RowDivider()
            }
        },
    )
}

/** Same type and spacing as the other sections' rows, without the download/favourite controls a person has no use for. */
@Composable
private fun PersonRow(title: String, detail: String, query: String, onClick: () -> Unit) {
    val hl = AtharTheme.colors.accent.copy(alpha = 0.18f)
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = highlightMatches(title, query, hl),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 29.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotEmpty()) Text(detail, Modifier.padding(top = 2.dp), color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun RowDivider() = com.atharchive.ui.components.AtharRowDivider(Modifier.padding(horizontal = Gutter), 0.7.dp, AtharTheme.colors.divider.copy(alpha = 0.42f))
