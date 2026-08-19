package com.atharchive.feature.kannashah

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

private data class ExcerptGroup(val title: String, val subtitle: String?, val items: List<ExcerptUi>)

@Composable
fun KannashahScreen(
    state: KannashahUiState,
    onSettings: () -> Unit,
    onCopy: (ExcerptUi) -> Unit,
    onShare: (ExcerptUi) -> Unit,
    onMore: (ExcerptUi) -> Unit,
    onOpenSource: (ExcerptUi) -> Unit,
    modifier: Modifier = Modifier,
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableStateOf(KannashahView.All) }
    var grouping by rememberSaveable { mutableStateOf(KannashahGrouping.Topic) }
    var sort by rememberSaveable { mutableStateOf(KannashahSort.Newest) }
    val listState = rememberLazyListState()

    val matching = remember(state.excerpts, query) {
        val term = query.trim()
        if (term.isBlank()) {
            state.excerpts
        } else {
            state.excerpts.filter {
                it.text.contains(term, ignoreCase = true) ||
                    it.sourceTitle.contains(term, ignoreCase = true) ||
                    it.sourceAuthor.contains(term, ignoreCase = true) ||
                    it.topics.any { topic -> topic.contains(term, ignoreCase = true) }
            }
        }
    }
    val sorted = remember(matching, sort) {
        when (sort) {
            KannashahSort.Newest -> matching.sortedByDescending { it.addedOrder }
            KannashahSort.Oldest -> matching.sortedBy { it.addedOrder }
            KannashahSort.SourceOrder -> matching.sortedBy { it.sourceOrdinal }
        }
    }
    // Grouping is a presentation of the same list, never a different screen or dataset.
    val groups = remember(sorted, view, grouping) {
        if (view == KannashahView.All) {
            emptyList()
        } else {
            when (grouping) {
                KannashahGrouping.Topic ->
                    sorted.flatMap { e -> e.topics.map { it to e } }
                        .groupBy({ it.first }, { it.second })
                        .map { (topic, items) ->
                            ExcerptGroup(topic, "${arabicCount(items.size)} مقتطفًا", items)
                        }
                KannashahGrouping.Source ->
                    sorted.groupBy { it.sourceTitle }
                        .map { (title, items) ->
                            ExcerptGroup(
                                title,
                                "${items.first().sourceAuthor} · ${arabicCount(items.size)} مقتطفًا",
                                items,
                            )
                        }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_kannashah")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "الكناشة",
                onSettings = onSettings,
                showAppIcon = true,
                onLogo = onLogo,
                onBack = onBack,
                horizontalPadding = horizontalPadding,
            )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("kannashah_screen"),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "k-headline") {
                Text(
                    text = state.headline,
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = 6.dp,
                    ),
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item(key = "k-search") {
                AtharSearchField(
                    query = query,
                    placeholder = "ابحث في كناشتك…",
                    onQueryChange = { query = it },
                    tagPrefix = "kannashah",
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                    ),
                )
            }
            item(key = "k-controls") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = horizontalPadding - 4.dp,
                            end = horizontalPadding - 4.dp,
                            top = 8.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ViewToggle(
                        view = view,
                        onSelect = { view = it },
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    if (view == KannashahView.Grouped) {
                        AtharMenuButton(
                            label = grouping.label,
                            options = KannashahGrouping.entries.map { it.label },
                            selected = grouping.label,
                            defaultOption = KannashahGrouping.Topic.label,
                            onSelect = { label ->
                                grouping = KannashahGrouping.entries.first { it.label == label }
                            },
                            tagPrefix = "kannashah_grouping",
                        )
                    } else {
                        AtharMenuButton(
                            label = "الترتيب",
                            options = KannashahSort.entries.map { it.label },
                            selected = sort.label,
                            defaultOption = KannashahSort.Newest.label,
                            onSelect = { label ->
                                sort = KannashahSort.entries.first { it.label == label }
                            },
                            tagPrefix = "kannashah_sort",
                        )
                    }
                }
            }

            if (sorted.isEmpty()) {
                item(key = "k-empty") {
                    AtharEmptyState(
                        title = if (query.isBlank()) "كناشتك فارغة" else "لا توجد نتائج",
                        body = if (query.isBlank()) {
                            "حدّد نصًا في أي كتاب وأضفه إلى الكناشة."
                        } else {
                            "جرّب كلمة أقصر من نص المقتطف أو اسم المصدر."
                        },
                        tag = "kannashah_empty_state",
                        horizontalPadding = horizontalPadding,
                    )
                }
            } else if (view == KannashahView.All) {
                items(sorted, horizontalPadding, onCopy, onShare, onMore, onOpenSource)
            } else {
                groups.forEach { group ->
                    item(key = "group-${group.title}") {
                        GroupHeading(
                            group = group,
                            horizontalPadding = horizontalPadding,
                        )
                    }
                    items(group.items, horizontalPadding, onCopy, onShare, onMore, onOpenSource)
                }
            }
        }
    }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    excerpts: List<ExcerptUi>,
    horizontalPadding: Dp,
    onCopy: (ExcerptUi) -> Unit,
    onShare: (ExcerptUi) -> Unit,
    onMore: (ExcerptUi) -> Unit,
    onOpenSource: (ExcerptUi) -> Unit,
) {
    itemsIndexed(excerpts) { index, excerpt ->
        ExcerptItem(
            excerpt = excerpt,
            horizontalPadding = horizontalPadding,
            onCopy = { onCopy(excerpt) },
            onShare = { onShare(excerpt) },
            onMore = { onMore(excerpt) },
            onOpenSource = { onOpenSource(excerpt) },
        )
        if (index != excerpts.lastIndex) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = horizontalPadding + 16.dp,
                    end = horizontalPadding,
                ),
                thickness = 0.7.dp,
                color = AtharTheme.colors.divider.copy(alpha = 0.2f),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    excerpts: List<ExcerptUi>,
    content: @Composable (Int, ExcerptUi) -> Unit,
) {
    items(count = excerpts.size, key = { excerpts[it].id }) { index ->
        content(index, excerpts[index])
    }
}

@Composable
private fun GroupHeading(group: ExcerptGroup, horizontalPadding: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 4.dp, end = 8.dp)
                .width(2.5.dp)
                .height(18.dp)
                .background(AtharTheme.colors.accent),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = group.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 26.sp,
            )
            if (group.subtitle != null) {
                Text(
                    text = group.subtitle,
                    color = AtharTheme.colors.secondaryText.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ExcerptItem(
    excerpt: ExcerptUi,
    horizontalPadding: Dp,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    onOpenSource: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // IntrinsicSize.Min so the margin rule can run the exact height of the entry
            .height(IntrinsicSize.Min)
            .padding(horizontal = horizontalPadding, vertical = 13.dp)
            .testTag("excerpt_${excerpt.id}"),
    ) {
        // The margin rule — first child, so RTL puts it on the right. This, not a
        // divider, is what gives the page its structure: it reads as an annotation
        // mark beside a passage rather than a border around a card.
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(AtharTheme.colors.accent.copy(alpha = 0.55f)),
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            // The saved text in full, at reading size. No maxLines: الكناشة is where the
            // excerpt is read, not a list of links to somewhere it can be read.
            Text(
                text = excerpt.text,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontSize = 17.5.sp,
                lineHeight = 32.sp,
            )
            // Three ranks in one line: the work in burgundy, then author, then page.
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = AtharTheme.colors.accent,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(excerpt.sourceTitle)
                    }
                    append(" · ")
                    append(excerpt.sourceAuthor)
                    if (excerpt.locationLabel != null) {
                        append(" · ")
                        append(excerpt.locationLabel)
                    }
                },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onOpenSource)
                    .testTag("excerpt_source_${excerpt.id}"),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
            if (excerpt.comment != null) {
                // The reader's own words — a margin note, tinted so it can never be
                // mistaken for the source text or for metadata.
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AtharTheme.colors.accent.copy(alpha = 0.06f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AtharIcons.Pen,
                        contentDescription = null,
                        tint = AtharTheme.colors.accent.copy(alpha = 0.75f),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = excerpt.comment,
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (excerpt.topics.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    excerpt.topics.forEachIndexed { index, topic ->
                        // The first topic is the entry's primary subject and takes a
                        // faint burgundy tint; the rest stay warm neutral.
                        val primary = index == 0
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (primary) {
                                AtharTheme.colors.accent.copy(alpha = 0.08f)
                            } else {
                                AtharTheme.colors.pressedSurface
                            },
                        ) {
                            Text(
                                text = topic,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                color = if (primary) {
                                    AtharTheme.colors.accent
                                } else {
                                    AtharTheme.colors.secondaryText
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ExcerptAction(AtharIcons.Copy, "نسخ ${excerpt.sourceTitle}", "excerpt_copy_${excerpt.id}", onCopy)
                ExcerptAction(AtharIcons.Share, "مشاركة ${excerpt.sourceTitle}", "excerpt_share_${excerpt.id}", onShare)
                ExcerptAction(AtharIcons.More, "خيارات المقتطف", "excerpt_more_${excerpt.id}", onMore)
            }
        }
    }
}

@Composable
private fun ExcerptAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp).testTag(tag)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = AtharTheme.colors.secondaryText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ViewToggle(
    view: KannashahView,
    onSelect: (KannashahView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KannashahView.entries.forEach { entry ->
            val selected = entry == view
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(role = Role.Tab) { onSelect(entry) }
                    .testTag("kannashah_view_${entry.key}"),
                shape = RoundedCornerShape(7.dp),
                color = if (selected) {
                    AtharTheme.colors.accent.copy(alpha = 0.12f)
                } else {
                    AtharTheme.colors.canvas
                },
            ) {
                Text(
                    text = entry.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = if (selected) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun arabicCount(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
