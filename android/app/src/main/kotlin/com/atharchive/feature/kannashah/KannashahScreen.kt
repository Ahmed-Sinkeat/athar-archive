package com.atharchive.feature.kannashah

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.atharchive.ui.components.AtharCollapsible
import com.atharchive.ui.components.rememberAtharCollapsingHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.atharchive.ui.components.AtharChipGroup
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharFilterButton
import com.atharchive.ui.components.enumValueOrNull
import com.atharchive.ui.components.rememberPersisted
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharTab
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

// Lines an excerpt shows before it folds behind «عرض الكل».
private const val COLLAPSED_LINES = 8

@Composable
fun KannashahScreen(
    state: KannashahUiState,
    onSettings: () -> Unit,
    onCopy: (ExcerptUi) -> Unit,
    onShare: (ExcerptUi) -> Unit,
    onMore: (ExcerptUi) -> Unit,
    onDelete: (ExcerptUi) -> Unit,
    onOpenSource: (ExcerptUi) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberPersisted("kannashah.sort", KannashahSort.Newest.name)
    val sort = enumValueOrNull<KannashahSort>(sortName) ?: KannashahSort.Newest
    var filterName by rememberPersisted("kannashah.filter", KannashahFilter.All.name)
    val filter = enumValueOrNull<KannashahFilter>(filterName) ?: KannashahFilter.All
    val listState = rememberLazyListState()

    val matching = remember(state.excerpts, query, filter) {
        val term = query.trim()
        state.excerpts.filter { excerpt ->
            val included = when (filter) {
                KannashahFilter.All -> true
                KannashahFilter.Highlights -> excerpt.kind == "highlight"
                KannashahFilter.Notes -> excerpt.kind == "note"
                KannashahFilter.Lost -> excerpt.attachmentState == "orphaned"
            }
            included && (term.isBlank() || with(excerpt) {
                text.contains(term, ignoreCase = true) ||
                    sourceTitle.contains(term, ignoreCase = true) ||
                    sourceAuthor.contains(term, ignoreCase = true) ||
                    topics.any { topic -> topic.contains(term, ignoreCase = true) }
            })
        }
    }
    val sorted = remember(matching, sort) {
        when (sort) {
            KannashahSort.Newest -> matching.sortedByDescending { it.addedOrder }
            KannashahSort.Oldest -> matching.sortedBy { it.addedOrder }
            KannashahSort.SourceOrder -> matching.sortedBy { it.sourceOrdinal }
        }
    }
    // A new excerpt lands above the one the list kept its place on, half under the header.
    // Seen from the top, show it whole.
    val newestId = state.excerpts.maxByOrNull { it.addedOrder }?.id
    var shownNewestId by rememberSaveable { mutableStateOf(newestId) }
    LaunchedEffect(newestId) {
        if (newestId != shownNewestId && sort == KannashahSort.Newest && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
        shownNewestId = newestId
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
                onBack = onBack,
                horizontalPadding = horizontalPadding,
            )
        val header = rememberAtharCollapsingHeader()
        if (state.headline.isNotBlank()) {
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
        AtharCollapsible(header) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding - 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AtharFilterButton(
                        groups = listOf(
                            AtharChipGroup("الترتيب", KannashahSort.entries.map { it.label }, sort.label) { label ->
                                sortName = KannashahSort.entries.first { it.label == label }.name
                            },
                            AtharChipGroup("النوع", KannashahFilter.entries.map { it.label }, filter.label) { label ->
                                filterName = KannashahFilter.entries.first { it.label == label }.name
                            },
                        ),
                        tagPrefix = "kannashah_filter",
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(header.connection)
                .testTag("kannashah_screen"),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
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
            } else {
                items(sorted, horizontalPadding, onCopy, onShare, onMore, onDelete, onOpenSource)
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
    onDelete: (ExcerptUi) -> Unit,
    onOpenSource: (ExcerptUi) -> Unit,
    keyPrefix: String = "",
) {
    itemsIndexed(excerpts, keyPrefix) { index, excerpt ->
        ExcerptItem(
            excerpt = excerpt,
            horizontalPadding = horizontalPadding,
            onCopy = { onCopy(excerpt) },
            onShare = { onShare(excerpt) },
            onMore = { onMore(excerpt) },
            onDelete = { onDelete(excerpt) },
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
    keyPrefix: String,
    content: @Composable (Int, ExcerptUi) -> Unit,
) {
    items(count = excerpts.size, key = { "$keyPrefix${excerpts[it].id}" }) { index ->
        content(index, excerpts[index])
    }
}

@Composable
private fun ExcerptItem(
    excerpt: ExcerptUi,
    horizontalPadding: Dp,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    onDelete: () -> Unit,
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
                .clip(CircleShape)
                .background(AtharTheme.colors.accent.copy(alpha = 0.55f)),
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            // The saved text at reading size. A long one folds to its first lines so one
            // excerpt can't fill the page; «عرض الكل» opens it in place.
            var expanded by rememberSaveable(excerpt.id) { mutableStateOf(false) }
            var overflows by remember(excerpt.id, excerpt.text) { mutableStateOf(false) }
            Text(
                text = excerpt.text,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontSize = AtharTextSize.Title,
                lineHeight = 32.sp,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            )
            if (overflows || expanded) {
                Text(
                    text = if (expanded) "عرض أقل" else "عرض الكل",
                    modifier = Modifier
                        .clip(AtharCorners.Control)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(vertical = 6.dp)
                        .testTag("excerpt_expand_${excerpt.id}"),
                    color = AtharTheme.colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
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
                    .clip(AtharCorners.Tag)
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
                        .clip(AtharCorners.Control)
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
                            shape = AtharCorners.Control,
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
                Box {
                    var menu by remember { mutableStateOf(false) }
                    ExcerptAction(AtharIcons.More, "خيارات المقتطف", "excerpt_more_${excerpt.id}") { menu = true }
                    AtharMenu(expanded = menu, onDismiss = { menu = false }, modifier = Modifier.testTag("excerpt_menu_${excerpt.id}")) {
                        AtharMenuItem(
                            label = if (excerpt.comment.isNullOrBlank()) "إضافة تعليق" else "تعديل التعليق",
                            icon = AtharIcons.Pen,
                            onClick = { menu = false; onMore() },
                            modifier = Modifier.testTag("excerpt_comment_${excerpt.id}"),
                        )
                        AtharMenuItem(
                            label = "فتح المصدر",
                            icon = AtharIcons.Books,
                            onClick = { menu = false; onOpenSource() },
                            modifier = Modifier.testTag("excerpt_source_${excerpt.id}"),
                        )
                        AtharMenuItem(
                            label = "حذف المقتطف",
                            icon = AtharIcons.Close,
                            onClick = { menu = false; onDelete() },
                            modifier = Modifier.testTag("excerpt_delete_${excerpt.id}"),
                        )
                    }
                }
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
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag(tag)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = AtharTheme.colors.secondaryText,
            modifier = Modifier.size(18.dp),
        )
    }
}


private fun arabicCount(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
