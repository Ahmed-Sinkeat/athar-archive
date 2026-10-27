package com.atharchive.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atharchive.ui.components.AtharChipGroup
import com.atharchive.ui.components.AtharFacet
import com.atharchive.ui.components.AtharFilterButton

private const val Everywhere = "كل المكتبة"
private const val Downloaded = "المحمّلة فقط"

/** The library's own filter row: single choices as menus, authors and types as ticked facets with chips. */
@Composable
fun SearchFilterMenus(filters: SearchFilters, authors: List<String>, onChange: (SearchFilters) -> Unit, modifier: Modifier = Modifier) {
    AtharFilterButton(
        groups = listOf(
            AtharChipGroup("الترتيب", SearchSort.entries.map { it.label }, filters.sort.label) { label ->
                onChange(filters.copy(sort = SearchSort.entries.first { it.label == label }))
            },
            AtharChipGroup("الدقة", SearchMode.entries.map { it.label }, filters.mode.label) { label ->
                onChange(filters.copy(mode = SearchMode.entries.first { it.label == label }))
            },
            AtharChipGroup("المجال", SearchField.entries.map { it.label }, filters.field.label) { label ->
                onChange(filters.copy(field = SearchField.entries.first { it.label == label }))
            },
            AtharChipGroup("النطاق", listOf(Everywhere, Downloaded), if (filters.downloadedOnly) Downloaded else Everywhere) {
                onChange(filters.copy(downloadedOnly = it == Downloaded))
            },
        ),
        tagPrefix = "search_filter",
        modifier = modifier,
        facets = listOf(
            AtharFacet("المؤلف", authors, filters.authors, { onChange(filters.copy(authors = it)) }),
            AtharFacet("النوع", SearchResultType.entries.map { it.label }, filters.types.mapTo(linkedSetOf()) { it.label }, { labels ->
                onChange(filters.copy(types = SearchResultType.entries.filterTo(linkedSetOf()) { it.label in labels }))
            }),
        ),
    )
}
