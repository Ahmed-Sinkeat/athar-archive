package com.atharchive.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atharchive.ui.components.AtharFilterMenu

@Composable
fun SearchFilterMenus(filters: SearchFilters, sources: List<String>, authors: List<String>, onChange: (SearchFilters) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        AtharFilterMenu("النوع", SearchResultType.entries.map { it.label }, filters.types.map { it.label }.toSet(), "filter_type") { label ->
            val type = SearchResultType.entries.first { it.label == label }
            onChange(filters.copy(types = if (type in filters.types) filters.types - type else filters.types + type))
        }
        AtharFilterMenu("المجال", SearchField.entries.map { it.label }, setOf(filters.field.label), "filter_field") { label ->
            onChange(filters.copy(field = SearchField.entries.first { it.label == label }))
        }
        if (sources.isNotEmpty()) AtharFilterMenu("المصدر", sources, filters.sources, "filter_source") { value ->
            onChange(filters.copy(sources = if (value in filters.sources) filters.sources - value else filters.sources + value))
        }
        if (authors.isNotEmpty()) AtharFilterMenu("المؤلف", authors, filters.authors, "filter_author") { value ->
            onChange(filters.copy(authors = if (value in filters.authors) filters.authors - value else filters.authors + value))
        }
        AtharFilterMenu("الترتيب", SearchSort.entries.map { it.label }, setOf(filters.sort.label), "search_sort") { label ->
            onChange(filters.copy(sort = SearchSort.entries.first { it.label == label }))
        }
        if (filters.isActive) TextButton(onClick = { onChange(SearchFilters()) }) { Text("مسح") }
    }
}
