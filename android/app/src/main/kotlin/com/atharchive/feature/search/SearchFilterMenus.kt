package com.atharchive.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuDivider
import com.atharchive.ui.components.AtharFilterChip
import com.atharchive.ui.components.AtharFilterPicker
import com.atharchive.ui.components.AtharFilterRow
import com.atharchive.ui.components.arabicDigits
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTheme

/** Every choice that differs from the default, as (chip label, filters without it). */
private fun SearchFilters.active(): List<Pair<String, SearchFilters>> = buildList {
    if (sort != SearchSort.Relevance) add("${sort.label}${if (descending) " · تنازلي" else ""}" to copy(sort = SearchSort.Relevance, descending = false))
    if (mode != SearchMode.All) add(mode.label to copy(mode = SearchMode.All))
    if (field != SearchField.FullText) add("في ${field.label}" to copy(field = SearchField.FullText))
    if (downloadedOnly) add("المحمّلة فقط" to copy(downloadedOnly = false))
    types.forEach { add(it.label to copy(types = types - it)) }
    authors.forEach { add(it to copy(authors = authors - it)) }
    books.forEach { add(it to copy(books = books - it)) }
    sections.forEach { add(it to copy(sections = sections - it)) }
    centuries.forEach { add(it to copy(centuries = centuries - it)) }
}

/**
 * The filter button inside the search field: one popup with every choice, and a badge with how
 * many are set. Always there, before anything is typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFilterButton(
    filters: SearchFilters,
    authors: List<String>,
    books: List<String>,
    presets: List<SearchPreset>,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onChange: (SearchFilters) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val count = filters.active().size
    // The death year's direction is one tap, outside the popup, as in every section.
    if (filters.sort == SearchSort.Died) com.atharchive.ui.components.AtharDirectionButton(
        com.atharchive.ui.components.AtharDirectionGroup(filters.descending) { onChange(filters.copy(descending = it)) })
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp).testTag("search_filter_button")) {
            Box {
                Icon(AtharIcons.Filter, "المرشحات", tint = if (count > 0) AtharTheme.colors.accent else AtharTheme.colors.secondaryText, modifier = Modifier.size(21.dp))
                if (count > 0) Text(
                    arabicDigits(count),
                    Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(15.dp).clip(CircleShape).background(AtharTheme.colors.accent),
                    color = AtharTheme.colors.onPrimaryAction,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        AtharMenu(expanded = open, onDismiss = { open = false }, width = 280.dp, modifier = Modifier.testTag("search_filter_popup")) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                AtharFilterRow("الترتيب") { SearchSort.entries.forEach { AtharFilterChip(it.label, it == filters.sort) { onChange(filters.copy(sort = it)) } } }
                AtharMenuDivider()
                AtharFilterRow("العرض") { AtharFilterChip("إخفاء التشكيل", filters.plain) { onChange(filters.copy(plain = !filters.plain)) } }
                AtharMenuDivider()
                AtharFilterRow("الدقة") { SearchMode.entries.forEach { AtharFilterChip(it.label, it == filters.mode) { onChange(filters.copy(mode = it)) } } }
                AtharMenuDivider()
                AtharFilterRow("المجال") { SearchField.entries.forEach { AtharFilterChip(it.label, it == filters.field) { onChange(filters.copy(field = it)) } } }
                AtharMenuDivider()
                AtharFilterRow("النطاق") {
                    listOf(false, true).forEach { AtharFilterChip(if (it) "المحمّلة فقط" else "كل المكتبة", it == filters.downloadedOnly) { onChange(filters.copy(downloadedOnly = it)) } }
                }
                // The kind of result is the tab row under the field, not a row here.
                AtharMenuDivider()
                AtharFilterPicker("المؤلف", authors, filters.authors, { null }, "search_filter_author") { name ->
                    onChange(filters.copy(authors = if (name in filters.authors) filters.authors - name else filters.authors + name))
                }
                AtharFilterPicker("الكتاب", books, filters.books, { null }, "search_filter_book") { title ->
                    onChange(filters.copy(books = if (title in filters.books) filters.books - title else filters.books + title))
                }
                AtharMenuDivider()
                AtharFilterRow("القرن") {
                    com.atharchive.data.AllCenturies.forEach { c -> AtharFilterChip(c.removePrefix("القرن "), c in filters.centuries) { onChange(filters.copy(centuries = if (c in filters.centuries) filters.centuries - c else filters.centuries + c)) } }
                }
                AtharMenuDivider()
                if (count > 0) {
                    androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 4.dp)) { AtharFilterChip("مسح الكل", false) { onChange(SearchFilters()) } }
                    AtharMenuDivider()
                }
                SearchPresets(filters, presets, onSavePreset, onDeletePreset) { open = false; onChange(it) }
            }
        }
    }
}

/**
 * «الخيارات المثبتة»: each saved set applies in one tap (long-press removes it); the current
 * filters, once any are set, can be saved under a name.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SearchPresets(
    filters: SearchFilters,
    presets: List<SearchPreset>,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    if (presets.isNotEmpty()) AtharFilterRow("المثبتة") {
        presets.forEach { p ->
            Text(
                p.name,
                Modifier.clip(AtharCorners.Control)
                    .combinedClickable(role = Role.Button, onLongClick = { onDelete(p.name) }) { onApply(p.filters) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("search_preset_${p.name}"),
                color = if (p.filters == filters) AtharTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (filters == SearchFilters() || presets.any { it.filters == filters }) return
    if (!naming) Text(
        "تثبيت هذه الخيارات",
        Modifier.clip(AtharCorners.Control).clickable(role = Role.Button) { naming = true }
            .padding(horizontal = 4.dp, vertical = 10.dp).testTag("search_preset_save"),
        color = AtharTheme.colors.accent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
    ) else Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it.take(40) }, Modifier.weight(1f).testTag("search_preset_name"),
            singleLine = true, placeholder = { Text("اسم المجموعة") })
        Text(
            "حفظ",
            Modifier.clip(AtharCorners.Control).clickable(enabled = name.isNotBlank(), role = Role.Button) { onSave(name.trim()); naming = false; name = "" }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            color = AtharTheme.colors.accent, fontWeight = FontWeight.SemiBold,
        )
    }
}
