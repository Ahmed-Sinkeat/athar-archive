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
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.components.RemovableChip
import com.atharchive.ui.components.arabicDigits
import com.atharchive.ui.components.normKey
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
        AtharMenu(expanded = open, onDismiss = { open = false }, width = 340.dp, maxHeight = 600.dp, modifier = Modifier.testTag("search_filter_popup")) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                // Label beside its choices, one line per setting (as the reader's Aa popover), so the
                // whole popup fits on screen without scrolling.
                @Composable fun <T> group(title: String, options: List<T>, label: (T) -> String, isOn: (T) -> Boolean, onTap: (T) -> Unit) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(0.22f), color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.labelSmall)
                        FlowRow(Modifier.weight(0.78f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            options.forEach { option -> SmallChip(label(option), isOn(option)) { onTap(option) } }
                        }
                    }
                }
                group("الترتيب", SearchSort.entries, { it.label }, { it == filters.sort }) { onChange(filters.copy(sort = it)) }
                // Relevance has one direction; the death year has two.
                if (filters.sort == SearchSort.Died) group("الاتجاه", listOf(false, true), { if (it) "الأحدث وفاة" else "الأقدم وفاة" }, { it == filters.descending }) {
                    onChange(filters.copy(descending = it))
                }
                group("الدقة", SearchMode.entries, { it.label }, { it == filters.mode }) { onChange(filters.copy(mode = it)) }
                group("المجال", SearchField.entries, { it.label }, { it == filters.field }) { onChange(filters.copy(field = it)) }
                group("النطاق", listOf(false, true), { if (it) "المحمّلة فقط" else "كل المكتبة" }, { it == filters.downloadedOnly }) {
                    onChange(filters.copy(downloadedOnly = it))
                }
                // Several types at once; none ticked means all of them.
                group("النوع", SearchResultType.entries, { it.label }, { it in filters.types }) {
                    onChange(filters.copy(types = if (it in filters.types) filters.types - it else filters.types + it))
                }
                AuthorPicker(filters, authors, onChange)
            }
        }
    }
}

/** Authors are hundreds: typed, not listed, so the popup needs no list of its own to scroll. */
@Composable
private fun AuthorPicker(filters: SearchFilters, authors: List<String>, onChange: (SearchFilters) -> Unit) {
    var query by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("المؤلف", Modifier.weight(0.22f), color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(query, { query = it }, placeholder = { Text("اكتب اسمًا…") }, singleLine = true,
            modifier = Modifier.weight(0.78f).testTag("search_filter_author_query"))
    }
    val term = com.atharchive.core.ArNormalize.normalize(query.trim())
    // ponytail: first 5 matches; a longer name narrows it
    val matches = if (term.isEmpty()) filters.authors.toList() else authors.filter { normKey(it).contains(term) }.take(5)
    matches.forEach { name ->
        val ticked = name in filters.authors
        AtharMenuItem(name, checked = ticked, onClick = {
            onChange(filters.copy(authors = if (ticked) filters.authors - name else filters.authors + name))
        })
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

/** A compact choice pill: solid accent when on, like [com.atharchive.ui.components.AtharChip] but sized for a popup. */
@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .heightIn(min = 36.dp)
            .clip(AtharCorners.Control)
            .background(if (selected) AtharTheme.colors.primaryAction else AtharTheme.colors.divider.copy(alpha = 0.28f))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) AtharTheme.colors.onPrimaryAction else AtharTheme.colors.secondaryText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}
