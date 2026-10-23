package com.atharchive.feature.reader

import com.atharchive.ui.components.SoapLayer
import com.atharchive.ui.components.rememberSoapTargets
import com.atharchive.ui.components.soapTarget
import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily

enum class ReaderPanelTab(val label: String) {
    Contents("المحتويات"),
    Search("البحث"),
    Benefits("الكناشة"),
    Bookmarks("العلامات"),
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
    searchStatus: String? = null,
    onPageJump: (Int, Int?) -> Unit = { _, _ -> },
    docked: Boolean = false,
) {
    var tab by remember(initialTab) { mutableStateOf(initialTab) }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(if (docked) 1f else 0.78f)
                .fillMaxHeight()
                .background(colors.page)
                .testTag("reader_panel"),
        ) {
            // No header and no ✕: the panel sits under the top bar, and the scrim or back closes it.
            val tabs = ReaderPanelTab.entries.filter { it != ReaderPanelTab.Search }
            val soap = rememberSoapTargets()
            Box(Modifier.fillMaxWidth()) {
                SoapLayer(soap, tabs.indexOf(tab), colors.accent, Modifier.matchParentSize(), corner = 1.dp)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    // البحث is reached from the top search icon, not a second tab.
                    tabs.forEachIndexed { index, entry ->
                        val selected = entry == tab
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(interactionSource = null, indication = null, role = Role.Tab) { if (selected) soap.reselect() else tab = entry }
                                .padding(top = 12.dp)
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
                            Box(Modifier.width(38.dp).height(1.5.dp).soapTarget(soap, index)) // the soap underline lands here
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 0.7.dp, color = colors.divider)

            // ponytail: scans the loaded blocks; fine while a book's blocks are all in memory.
            val pageBreaks = remember(state.blocks) { state.blocks.filterIsInstance<ReaderBlock.PageBreak>() }
            val multiVolume = remember(pageBreaks) { pageBreaks.mapNotNull { it.volume }.distinct().size > 1 }
            if (tab == ReaderPanelTab.Contents && pageBreaks.isNotEmpty()) {
                var page by remember { mutableStateOf("") }
                var volume by remember { mutableStateOf("") }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(page, { page = it.take(6) }, Modifier.weight(1f), label = { Text("الصفحة") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedContainerColor = colors.page, unfocusedContainerColor = colors.page,
                        focusedLabelColor = colors.accent, unfocusedLabelColor = colors.secondary,
                        cursorColor = colors.accent, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.divider))
                    if (multiVolume) OutlinedTextField(volume, { volume = it.take(4) }, Modifier.weight(1f), label = { Text("الجزء") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedContainerColor = colors.page, unfocusedContainerColor = colors.page,
                        focusedLabelColor = colors.accent, unfocusedLabelColor = colors.secondary,
                        cursorColor = colors.accent, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.divider))
                    TextButton(onClick = { page.toIntOrNull()?.let { onPageJump(it, volume.toIntOrNull()) } },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.accent, disabledContentColor = colors.secondary),
                        enabled = (page.toIntOrNull() ?: 0) > 0 && (volume.isBlank() || (volume.toIntOrNull() ?: 0) > 0)) { Text("انتقل", fontWeight = FontWeight.SemiBold) }
                }
            }
            when (tab) {
                ReaderPanelTab.Contents -> ContentsList(state, colors, currentBlockIndex, onJump)
                ReaderPanelTab.Search -> PanelSearch(query, hits, colors, onQueryChange, onJump, searchStatus)
                ReaderPanelTab.Benefits -> BenefitsList(state, colors, onJump)
                ReaderPanelTab.Bookmarks -> BookmarksList(state, colors, onJump)
            }
        }
        // Scrim over the sliver of page still showing; tapping it closes the panel.
        if (!docked) Box(
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
    status: String?,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(AtharCorners.Card)
                .background(colors.divider.copy(alpha = 0.28f))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AtharIcons.Search,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(24.dp),
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
                text = status ?: if (hits.isEmpty()) {
                    "لا توجد نتائج"
                } else {
                    "${if (hits.size >= 200) "أول " else ""}${com.atharchive.data.arabicCount(hits.size, "لا نتائج", "نتيجة واحدة", "نتيجتان", "نتائج", "نتيجة")}"
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
                        fontSize = AtharTextSize.Ui,
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
    val groups = remember(state.toc) { tocGroups(state.toc) }
    // "Current" is the last entry at or before the reading position; its كتاب starts unfolded.
    val active = remember(state.toc, currentBlockIndex) { state.toc.lastOrNull { it.blockIndex <= currentBlockIndex }?.anchor }
    val activeGroup = groups.indexOfLast { g -> g.kitab?.anchor == active || g.abwab.any { it.anchor == active } }
    var open by remember(groups) { mutableStateOf(setOfNotNull(groups.getOrNull(activeGroup)?.kitab?.anchor)) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEachIndexed { index, group ->
            val kitab = group.kitab
            val unfolded = kitab == null || kitab.anchor in open
            if (kitab != null) item(key = kitab.anchor) {
                TocRow(
                    entry = kitab,
                    // A folded كتاب carries the mark for the باب hidden inside it.
                    active = kitab.anchor == active || (!unfolded && index == activeGroup),
                    colors = colors,
                    onJump = onJump,
                    fold = if (group.abwab.isEmpty()) null else unfolded,
                    onFold = { open = if (unfolded) open - kitab.anchor else open + kitab.anchor },
                )
            }
            if (unfolded) items(group.abwab, key = { it.anchor }) { bab ->
                TocRow(bab, bab.anchor == active, colors, onJump, indent = kitab != null)
            }
        }
    }
}

@Composable
private fun TocRow(
    entry: TocEntry,
    active: Boolean,
    colors: ReaderColors,
    onJump: (Int) -> Unit,
    indent: Boolean = false,
    fold: Boolean? = null,
    onFold: () -> Unit = {},
) {
    val kitab = entry.level <= 2
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .clickable(role = Role.Button) { onJump(entry.blockIndex) }
                .padding(start = if (indent) 30.dp else 14.dp, end = if (fold == null) 14.dp else 4.dp, top = 10.dp, bottom = 10.dp)
                .testTag("reader_toc_${entry.anchor}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) Box(Modifier.padding(end = 8.dp).width(2.5.dp).height(16.dp).background(colors.accent))
            Text(
                text = entry.title,
                modifier = Modifier.weight(1f),
                color = if (active) colors.accent else colors.text,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = if (kitab) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (kitab) 16.sp else 15.sp,
                lineHeight = 25.sp,
            )
            if (entry.page.isNotEmpty()) Text(entry.page, Modifier.padding(start = 10.dp), color = colors.secondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            if (fold != null) Box(
                Modifier.size(40.dp).clip(AtharCorners.Control).clickable(role = Role.Button, onClickLabel = if (fold) "طيّ الأبواب" else "عرض الأبواب", onClick = onFold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AtharIcons.ChevronDown,
                    contentDescription = if (fold) "طيّ الأبواب" else "عرض الأبواب",
                    tint = colors.secondary,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (fold) 180f else 0f },
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = if (indent) 30.dp else 14.dp, end = 14.dp), thickness = 0.7.dp, color = colors.divider.copy(alpha = 0.6f))
    }
}

@Composable
private fun BenefitsList(state: ReaderUiState, colors: ReaderColors, onJump: (Int) -> Unit) {
    if (state.benefits.isEmpty()) {
        PanelEmpty("لا شيء من هذا الكتاب في الكناشة", "حدّد نصًا واختر «إلى الكناشة».", colors)
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
                    fontSize = AtharTextSize.Ui,
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
        PanelEmpty("لا توجد علامات", "اضغط زر العلامة لحفظ موضعك الحالي.", colors)
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
                        fontSize = AtharTextSize.Ui,
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
            fontSize = AtharTextSize.Title,
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = 4.dp),
            color = colors.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
