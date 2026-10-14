package com.atharchive.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

/**
 * The one bottom sheet left in the app, and it earns it: five filter groups is more than
 * an anchored menu can hold. Everything single-choice elsewhere stays a dropdown.
 *
 * A downloaded-only switch is deliberately absent. Catalog metadata is complete and
 * passage search is inherently limited to locally indexed blocks, so the scope already
 * follows the kind of result instead of asking the reader to manage a redundant toggle.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SearchFilterSheet(
    filters: SearchFilters,
    sources: List<String>,
    authors: List<String>,
    onChange: (SearchFilters) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AtharTheme.colors.canvas,
        scrimColor = AtharTheme.colors.scrim,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                .testTag("search_filter_sheet"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "تصفية النتائج",
                    modifier = Modifier.weight(1f),
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    lineHeight = 28.sp,
                )
                TextButton(onClick = onClear) { Text("مسح") }
                TextButton(onClick = onDismiss) { Text("تم") }
            }

            FilterHeading("نوع المحتوى")
            ChipRow(
                options = SearchResultType.entries.map { it.label },
                selected = filters.types.map { it.label }.toSet(),
                tagPrefix = "filter_type",
                onToggle = { label ->
                    val type = SearchResultType.entries.first { it.label == label }
                    onChange(
                        filters.copy(
                            types = if (type in filters.types) {
                                filters.types - type
                            } else {
                                filters.types + type
                            },
                        ),
                    )
                },
            )

            FilterHeading("مجال البحث")
            ChipRow(
                options = SearchField.entries.map { it.label },
                selected = setOf(filters.field.label),
                tagPrefix = "filter_field",
                onToggle = { label ->
                    onChange(filters.copy(field = SearchField.entries.first { it.label == label }))
                },
            )

            if (sources.isNotEmpty()) {
                FilterHeading("المصدر")
                ChipRow(
                    options = sources,
                    selected = filters.sources,
                    tagPrefix = "filter_source",
                    onToggle = { source ->
                        onChange(
                            filters.copy(
                                sources = if (source in filters.sources) {
                                    filters.sources - source
                                } else {
                                    filters.sources + source
                                },
                            ),
                        )
                    },
                )
            }

            if (authors.isNotEmpty()) {
                FilterHeading("المؤلف")
                ChipRow(
                    options = authors,
                    selected = filters.authors,
                    tagPrefix = "filter_author",
                    onToggle = { author ->
                        onChange(
                            filters.copy(
                                authors = if (author in filters.authors) {
                                    filters.authors - author
                                } else {
                                    filters.authors + author
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: Set<String>,
    tagPrefix: String,
    onToggle: (String) -> Unit,
) {
    var query by androidx.compose.runtime.remember(tagPrefix) { androidx.compose.runtime.mutableStateOf("") }
    val matching = androidx.compose.runtime.remember(options, query) {
        val term = com.atharchive.core.ArNormalize.normalize(query)
        options.distinct().filter { com.atharchive.core.ArNormalize.normalize(it).contains(term) }
    }
    @Composable fun option(text: String, index: Int) {
        val chosen = text in selected
        Surface(
            modifier = Modifier
                .then(if (options.size > 10) Modifier.fillMaxWidth() else Modifier)
                .toggleable(value = chosen, role = Role.Checkbox, onValueChange = { onToggle(text) })
                .testTag("${tagPrefix}_$index"),
            shape = RoundedCornerShape(8.dp),
            color = if (chosen) AtharTheme.colors.accentSurface else AtharTheme.colors.raisedSurface,
        ) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (chosen) Icon(AtharIcons.Check, null, Modifier.padding(end = 5.dp).size(20.dp), tint = AtharTheme.colors.accent)
                Text(text, color = if (chosen) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Column {
        if (options.size > 10) {
            androidx.compose.material3.OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("${tagPrefix}_query"), label = { Text("ابحث في الخيارات") }, singleLine = true)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(200.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(matching.size) { index -> option(matching[index], index) }
            }
        } else androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(matching.size) { index -> option(matching[index], index) }
        }
    }
}
