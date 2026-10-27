package com.atharchive.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.atharchive.ui.components.RemovableChip
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
}

/**
 * The filter button inside the search field: one popup with every choice, and a badge with how
 * many are set. Always there, before anything is typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFilterButton(filters: SearchFilters, authors: List<String>, onChange: (SearchFilters) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val count = filters.active().size
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
        AtharMenu(expanded = open, onDismiss = { open = false }, width = 330.dp, maxHeight = 560.dp, modifier = Modifier.testTag("search_filter_popup")) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                AtharFilterRow("الترتيب") { SearchSort.entries.forEach { AtharFilterChip(it.label, it == filters.sort) { onChange(filters.copy(sort = it)) } } }
                // Relevance has one direction; the death year has two.
                if (filters.sort == SearchSort.Died) AtharFilterRow("الاتجاه") {
                    listOf(false, true).forEach { AtharFilterChip(if (it) "الأحدث وفاة" else "الأقدم وفاة", it == filters.descending) { onChange(filters.copy(descending = it)) } }
                }
                AtharMenuDivider()
                AtharFilterRow("الدقة") { SearchMode.entries.forEach { AtharFilterChip(it.label, it == filters.mode) { onChange(filters.copy(mode = it)) } } }
                AtharMenuDivider()
                AtharFilterRow("المجال") { SearchField.entries.forEach { AtharFilterChip(it.label, it == filters.field) { onChange(filters.copy(field = it)) } } }
                AtharMenuDivider()
                AtharFilterRow("النطاق") {
                    listOf(false, true).forEach { AtharFilterChip(if (it) "المحمّلة فقط" else "كل المكتبة", it == filters.downloadedOnly) { onChange(filters.copy(downloadedOnly = it)) } }
                }
                AtharMenuDivider()
                // Several types at once; none ticked means all of them.
                AtharFilterRow("النوع") {
                    SearchResultType.entries.forEach { type ->
                        AtharFilterChip(type.label, type in filters.types) {
                            onChange(filters.copy(types = if (type in filters.types) filters.types - type else filters.types + type))
                        }
                    }
                }
                AtharMenuDivider()
                AtharFilterPicker("المؤلف", authors, filters.authors, { null }, "search_filter_author") { name ->
                    onChange(filters.copy(authors = if (name in filters.authors) filters.authors - name else filters.authors + name))
                }
            }
        }
    }
}

/** What is set, under the field, each removable; «مسح الكل» once there are two. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchActiveFilters(filters: SearchFilters, onChange: (SearchFilters) -> Unit, modifier: Modifier = Modifier) {
    val active = filters.active()
    if (active.isEmpty()) return
    FlowRow(
        modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        active.forEach { (label, without) -> RemovableChip(label) { onChange(without) } }
        if (active.size >= 2) Text(
            "مسح الكل",
            Modifier
                .clip(AtharCorners.Control)
                .clickable(role = Role.Button) { onChange(SearchFilters()) }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("search_filter_clear"),
            color = AtharTheme.colors.accent,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
