package com.atharchive.feature.adhkar

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharSectionHeading
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

/**
 * A directory over the same أذكار, in the shape الأقسام already uses: a row, a fact, a
 * chevron. الصباح and المساء appear here as well as in the tabs — the tabs are the
 * shortcut, this is the whole set.
 *
 * ponytail: no per-باب pictogram yet. Fifteen new icons is its own review pass, and a
 * rushed set would be worse than none; the count carries the scanning weight until then.
 */
@Composable
fun AdhkarBabsScreen(
    state: AdhkarUiState,
    onBack: () -> Unit,
    onOpenBab: (AdhkarBabUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val term = query.trim()
    val matching = remember(state.babs, term) {
        if (term.isBlank()) state.babs else state.babs.filter { it.label.contains(term) }
    }
    val frequent = matching.filter { it.frequent }
    val rest = matching.filterNot { it.frequent }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_adhkar_babs")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "أبواب الأذكار",
                onSettings = {},
                showAppIcon = true,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("adhkar_babs_screen"),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item(key = "babs-search") {
                    AtharSearchField(
                        query = query,
                        placeholder = "ابحث في الأبواب…",
                        onQueryChange = { query = it },
                        tagPrefix = "adhkar_babs",
                        modifier = Modifier.padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = 4.dp,
                        ),
                    )
                }

                if (matching.isEmpty()) {
                    item(key = "babs-empty") {
                        AtharEmptyState(
                            title = "لا توجد أبواب",
                            body = "جرّب اسمًا أقصر.",
                            tag = "adhkar_babs_empty_state",
                            horizontalPadding = horizontalPadding,
                        )
                    }
                    return@LazyColumn
                }

                // While searching the two groups are noise: there is one answer, not a
                // directory to browse.
                if (term.isBlank()) {
                    if (frequent.isNotEmpty()) {
                        item(key = "babs-frequent-heading") {
                            AtharSectionHeading("الأكثر استخدامًا", horizontalPadding, top = 18.dp)
                        }
                        babRows(frequent, state, horizontalPadding, onOpenBab)
                    }
                    if (rest.isNotEmpty()) {
                        item(key = "babs-all-heading") {
                            AtharSectionHeading("جميع الأبواب", horizontalPadding, top = 22.dp)
                        }
                        babRows(rest, state, horizontalPadding, onOpenBab)
                    }
                } else {
                    babRows(matching, state, horizontalPadding, onOpenBab)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.babRows(
    babs: List<AdhkarBabUi>,
    state: AdhkarUiState,
    horizontalPadding: Dp,
    onOpenBab: (AdhkarBabUi) -> Unit,
) {
    itemsIndexed(babs, key = { _, bab -> bab.id }) { index, bab ->
        BabRow(
            bab = bab,
            count = state.count(bab.id),
            horizontalPadding = horizontalPadding,
            onClick = { onOpenBab(bab) },
        )
        if (index != babs.lastIndex) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                thickness = 0.7.dp,
                color = AtharTheme.colors.divider.copy(alpha = 0.42f),
            )
        }
    }
}

@Composable
private fun BabRow(
    bab: AdhkarBabUi,
    count: Int,
    horizontalPadding: Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 10.dp)
            .testTag("adhkar_bab_${bab.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = bab.label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Only when there is something to count. A bare «٠» would read as an error
        // rather than as content that has not arrived.
        if (count > 0) {
            Text(
                text = "${arabicDigits(count)} أذكار",
                color = AtharTheme.colors.secondaryText.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Icon(
            imageVector = AtharIcons.Forward,
            contentDescription = null,
            tint = AtharTheme.colors.secondaryText.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}
