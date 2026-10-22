package com.atharchive.feature.persons

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

private const val AllCenturies = "كل القرون"
private val Gutter = 20.dp

private fun century(died: Int?) = died?.let { "القرن ${arabicDigits((it - 1) / 100 + 1)}" }

@Composable
fun PersonsScreen(persons: List<CatalogPerson>, onPerson: (CatalogPerson) -> Unit, onSettings: () -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCentury by rememberSaveable { mutableStateOf(AllCenturies) }
    val centuries = remember(persons) { persons.mapNotNull { it.died }.sorted().mapNotNull(::century).distinct() }
    val visible = remember(persons, query, selectedCentury) {
        val term = ArNormalize.normalize(query.trim())
        persons.filter {
            (term.isBlank() || ArNormalize.normalize(it.name).contains(term)) &&
                (selectedCentury == AllCenturies || century(it.died) == selectedCentury)
        }.sortedBy { ArNormalize.normalize(it.name) }
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(AtharTheme.colors.canvas).clipToBounds()) {
        val header = rememberAtharCollapsingHeader()
        header.ShowOnChange(listState, query, selectedCentury)
        AtharFloatingHeader(header) {
            AtharTopBar(title = "الأعلام", onSettings = onSettings, showAppIcon = true, onBack = onBack)
            AtharSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "ابحث في ${arabicDigits(persons.size)} علمًا…",
                tagPrefix = "persons",
                modifier = Modifier.padding(start = Gutter, end = Gutter, top = 4.dp),
            )
            AtharFilterButton(
                listOf(AtharChipGroup("القرن", listOf(AllCenturies) + centuries, selectedCentury) { selectedCentury = it }),
                "persons_filter",
                Modifier.padding(horizontal = Gutter - 4.dp, vertical = 2.dp),
            )
            HorizontalDivider(Modifier.padding(horizontal = Gutter, vertical = 2.dp), 0.7.dp, AtharTheme.colors.divider.copy(alpha = 0.5f))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(header.connection),
            contentPadding = PaddingValues(top = header.height, bottom = 28.dp),
        ) {
            if (visible.isEmpty()) item(key = "persons-empty") {
                val (title, body) = when {
                    persons.isEmpty() -> "لا أعلام بعد" to "تظهر الأعلام بعد تحديث الفهرس من الإنترنت."
                    query.isNotBlank() -> "لا توجد نتائج" to "جرّب جزءًا من الاسم."
                    else -> "لا أعلام في $selectedCentury" to "اختر «$AllCenturies» لعرض الجميع."
                }
                AtharEmptyState(title, body, "persons_empty_state", Gutter)
            }
            itemsIndexed(visible, key = { _, person -> person.id }) { index, person ->
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
            }
        }
    }
}

@Composable
fun PersonScreen(person: CatalogPerson, works: Map<String, ContentEntity>, onWork: (ContentEntity) -> Unit, onSettings: () -> Unit, onBack: () -> Unit) {
    val available = person.works.mapNotNull(works::get)
    Column(Modifier.fillMaxSize().background(AtharTheme.colors.canvas)) {
        AtharTopBar(title = person.name, onSettings = onSettings, showAppIcon = true, onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    person.died?.let { Text("ت ${arabicDigits(it)} هـ", color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.bodyMedium) }
                    if (!person.bio.isNullOrBlank()) Text(person.bio.orEmpty(), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { AtharSectionHeading("المؤلفات", Gutter) }
            if (available.isEmpty()) item { AtharEmptyState("لا مؤلفات له هنا بعد", "لم تُضف أعماله إلى الأرشيف.", "person_works_empty", Gutter) }
            itemsIndexed(available, key = { _, work -> work.id }) { index, work ->
                PersonRow(title = work.title, detail = "", query = "", onClick = { onWork(work) })
                if (index != available.lastIndex) RowDivider()
            }
        }
    }
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
private fun RowDivider() = HorizontalDivider(Modifier.padding(horizontal = Gutter), 0.7.dp, AtharTheme.colors.divider.copy(alpha = 0.42f))
