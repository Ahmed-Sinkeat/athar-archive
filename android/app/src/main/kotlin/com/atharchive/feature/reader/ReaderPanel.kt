package com.atharchive.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily

enum class ReaderPanelTab(val label: String) {
    Contents("المحتويات"),
    Search("البحث"),
    Benefits("فوائدك"),
    Bookmarks("المواضع"),
}

/**
 * A right-side panel covering ~78% of the width, so the book stays visible behind it and
 * the reader never loses the sense of being inside it.
 *
 * In RTL the start edge is the right one, so the panel is the **first** child of the Row
 * and the scrim follows it; reversing them puts the panel on the wrong side.
 *
 * Tapping an entry jumps the book behind the panel and leaves the panel open, so a reader
 * comparing several places does not have to reopen it each time.
 */
@Composable
fun ReaderPanel(
    state: ReaderUiState,
    colors: ReaderColors,
    currentBlockIndex: Int,
    query: String,
    hits: List<InBookHit>,
    onQueryChange: (String) -> Unit,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: ReaderPanelTab = ReaderPanelTab.Contents,
) {
    var tab by remember { mutableStateOf(initialTab) }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.78f)
                .fillMaxHeight()
                .background(colors.page)
                .testTag("reader_panel"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.bookTitle,
                        color = colors.text,
                        fontFamily = AtharEditorialFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.author,
                        color = colors.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .testTag("reader_panel_close"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AtharIcons.Close,
                        contentDescription = "إغلاق",
                        tint = colors.secondary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                ReaderPanelTab.entries.forEach { entry ->
                    val selected = entry == tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.Tab) { tab = entry }
                            .padding(top = 8.dp)
                            .testTag("reader_panel_tab_${entry.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = entry.label,
                            color = if (selected) colors.accent else colors.secondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .width(38.dp)
                                .height(1.5.dp)
                                .background(if (selected) colors.accent else Color.Transparent),
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.7.dp, color = colors.divider)

            when (tab) {
                ReaderPanelTab.Contents -> ContentsList(state, colors, currentBlockIndex, onJump)
                ReaderPanelTab.Search -> PanelSearch(query, hits, colors, onQueryChange, onJump)
                ReaderPanelTab.Benefits -> BenefitsList(state, colors, onJump)
                ReaderPanelTab.Bookmarks -> BookmarksList(state, colors, onJump)
            }
        }
        // Scrim over the sliver of page still showing; tapping it closes the panel.
        Box(
            modifier = Modifier
                .weight(0.22f)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(role = Role.Button, onClick = onDismiss)
                .testTag("reader_panel_scrim"),
        )
    }
}

@Composable
private fun PanelSearch(
    query: String,
    hits: List<InBookHit>,
    colors: ReaderColors,
    onQueryChange: (String) -> Unit,
    onJump: (Int) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.divider.copy(alpha = 0.28f))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AtharIcons.Search,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(17.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester)
                    .testTag("reader_panel_search_field"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = "ابحث في الكتاب…",
                                color = colors.secondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(role = Role.Button) { onQueryChange("") }
                        .testTag("reader_panel_search_clear"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AtharIcons.Close,
                        contentDescription = "مسح",
                        tint = colors.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        if (query.isNotBlank()) {
            Text(
                text = if (hits.isEmpty()) {
                    "لا توجد نتائج"
                } else {
                    "${arabic(hits.size)} نتيجة"
                },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                color = colors.secondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(hits, key = { it.id }) { hit ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onJump(hit.blockIndex) }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                        .testTag("reader_panel_hit_${hit.id}"),
                ) {
                    Text(
                        text = hit.chapterTitle,
                        color = colors.secondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    // Highlighted live from the query, so the match is visible as it is typed.
                    Text(
                        text = highlightMatches(hit.excerpt, query, colors.highlight),
                        modifier = Modifier.padding(top = 3.dp),
                        color = colors.text,
                        fontFamily = AtharEditorialFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 27.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    thickness = 0.7.dp,
                    color = colors.divider.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ContentsList(
    state: ReaderUiState,
    colors: ReaderColors,
    currentBlockIndex: Int,
    onJump: (Int) -> Unit,
) {
    // "Current" is the last heading at or before the reading position, so a bab deep in a
    // kitab still marks its own row rather than the kitab above it.
    val activeAnchor = remember(state.toc, currentBlockIndex) {
        state.toc.lastOrNull { it.blockIndex <= currentBlockIndex }?.anchor
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.toc, key = { it.anchor }) { entry ->
            val active = entry.anchor == activeAnchor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 46.dp)
                    .clickable(role = Role.Button) { onJump(entry.blockIndex) }
                    .padding(
                        start = 14.dp,
                        end = if (entry.level >= 3) 30.dp else 14.dp,
                        top = 10.dp,
                        bottom = 10.dp,
                    )
                    .testTag("reader_toc_${entry.anchor}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .width(2.5.dp)
                            .height(16.dp)
                            .background(colors.accent),
                    )
                }
                Text(
                    text = entry.title,
                    color = if (active) colors.accent else colors.text,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = if (entry.level <= 2) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (entry.level <= 2) 16.sp else 15.sp,
                    lineHeight = 25.sp,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                thickness = 0.7.dp,
                color = colors.divider.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun BenefitsList(state: ReaderUiState, colors: ReaderColors, onJump: (Int) -> Unit) {
    if (state.benefits.isEmpty()) {
        PanelEmpty("لا توجد فوائد من هذا الكتاب", "ظلّل نصًا لتحفظه في كناشتك.", colors)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.benefits, key = { it.id }) { benefit ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onJump(benefit.blockIndex) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("reader_benefit_${benefit.id}"),
            ) {
                // Enough text to recognise the passage — never a one-line stub.
                Text(
                    text = benefit.text,
                    color = colors.text,
                    fontFamily = AtharEditorialFontFamily,
                    fontSize = 15.sp,
                    lineHeight = 27.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(benefit.chapterTitle, benefit.pageLabel).joinToString(" · "),
                    modifier = Modifier.padding(top = 5.dp),
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                thickness = 0.7.dp,
                color = colors.divider.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun BookmarksList(state: ReaderUiState, colors: ReaderColors, onJump: (Int) -> Unit) {
    if (state.bookmarks.isEmpty()) {
        PanelEmpty("لا توجد مواضع محفوظة", "اضغط الإشارة لحفظ موضعك الحالي.", colors)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.bookmarks, key = { it.id }) { bookmark ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onJump(bookmark.blockIndex) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("reader_bookmark_${bookmark.id}"),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = AtharIcons.Bookmark,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.padding(top = 2.dp, end = 9.dp).size(15.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = bookmark.preview,
                        color = colors.text,
                        fontFamily = AtharEditorialFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(bookmark.chapterTitle, bookmark.pageLabel).joinToString(" · "),
                        modifier = Modifier.padding(top = 4.dp),
                        color = colors.secondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                thickness = 0.7.dp,
                color = colors.divider.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PanelEmpty(title: String, body: String, colors: ReaderColors) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = colors.text,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = 4.dp),
            color = colors.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
