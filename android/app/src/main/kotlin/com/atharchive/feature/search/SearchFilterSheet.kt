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
 * Note what is deliberately absent: any "downloaded only" switch. Scope follows the
 * network — online searches the archive, offline searches what is on the device — and a
 * manual toggle would ask the user to manage something the app already knows.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option in selected
            Surface(
                modifier = Modifier
                    .clickable(role = Role.Button) { onToggle(option) }
                    .testTag("${tagPrefix}_$index"),
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    AtharTheme.colors.accentSurface
                } else {
                    AtharTheme.colors.raisedSurface
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = AtharIcons.Check,
                            contentDescription = null,
                            tint = AtharTheme.colors.accent,
                            modifier = Modifier
                                .padding(end = 5.dp)
                                .size(13.dp),
                        )
                    }
                    Text(
                        text = option,
                        color = if (isSelected) {
                            AtharTheme.colors.accent
                        } else {
                            AtharTheme.colors.secondaryText
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
