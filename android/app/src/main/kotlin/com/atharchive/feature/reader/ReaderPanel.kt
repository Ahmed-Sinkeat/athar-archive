package com.atharchive.feature.reader

import com.atharchive.ui.components.atharControl
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.atharchive.ui.components.AtharMenu
import androidx.compose.foundation.selection.selectable
import com.atharchive.ui.components.arabicDigits
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.components.markRanges
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
    /** Percent of the book on the phone when the search could not cover all of it. */
    partialSearch: Int? = null,
    downloadAsked: Boolean = false,
    onDownloadForSearch: (() -> Unit)? = null,
    onPageJump: (Int, Int?) -> Unit = { _, _ -> },
    docked: Boolean = false,
    recentQueries: List<String> = emptyList(),
    onHit: (InBookHit) -> Unit = { onJump(it.blockIndex) },
    /** The whole paragraph behind a hit, for «المزيد». */
    loadPassage: suspend (InBookHit) -> String? = { null },
    /** How the words must appear, as on the search screen; null hides the row (readers searching their open text only). */
    searchMode: com.atharchive.feature.search.SearchMode? = null,
    onSearchMode: (com.atharchive.feature.search.SearchMode) -> Unit = {},
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
            when (tab) {
                ReaderPanelTab.Contents -> {
                    Box(Modifier.weight(1f)) { ContentsList(state, colors, currentBlockIndex, onJump) }
                    if (pageBreaks.isNotEmpty() || state.pageRange != null || state.volumeStarts.isNotEmpty()) {
                        PageJumpRow(state, colors, currentBlockIndex, onPageJump)
                    }
                }
                ReaderPanelTab.Search -> PanelSearch(query, hits, colors, onQueryChange, onHit, searchStatus, partialSearch, downloadAsked, onDownloadForSearch, recentQueries, searchMode, onSearchMode, loadPassage)
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

/** Menus never scroll, so up to this many volumes are a grid of numbers; more are typed. */
private const val VolumeGridMax = 60

/** The volume as a small menu, then the printed page; the keyboard's Go jumps too. */
@Composable
private fun PageJumpRow(state: ReaderUiState, colors: ReaderColors, currentBlockIndex: Int, onPageJump: (Int, Int?) -> Unit) {
    val volumes = state.volumeStarts
    val here = volumes.lastOrNull { it.second <= currentBlockIndex }?.first ?: volumes.firstOrNull()?.first
    var volume by remember(here) { mutableStateOf(here) }
    // Hundreds of lessons or issues: the number is typed, «الدرس [١٢]», and must be one the book has.
    val typedVolume = volumes.size > VolumeGridMax
    var volumeText by remember(here) { mutableStateOf(here?.let(::arabicDigits).orEmpty()) }
    val chosen = if (typedVolume) volumeText.toIntOrNull()?.takeIf { v -> volumes.any { it.first == v } } else volume
    var page by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val number = page.toIntOrNull()?.takeIf { it > 0 }
    val ready = number != null && (here == null || chosen != null)
    val go: () -> Unit = { if (ready) number?.let { onPageJump(it, chosen) } }
    HorizontalDivider(thickness = 0.7.dp, color = colors.divider)
    // A tinted strip edge to edge, the joined volume + page field on the page colour inside it.
    Row(
        Modifier.fillMaxWidth().then(if (com.atharchive.ui.theme.LocalAtharStyle.current == com.atharchive.ui.theme.AtharStyle.Ink) Modifier else Modifier.background(colors.divider.copy(alpha = 0.35f))).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(40.dp).clip(AtharCorners.Card).background(colors.page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (here != null && typedVolume) {
                Text(when (state.volumeUnit) { "lesson" -> "الدرس"; "issue" -> "العدد"; else -> "الجزء" },
                    Modifier.padding(start = 12.dp), color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
                BasicTextField(
                    value = volumeText,
                    onValueChange = { typed -> volumeText = typed.filter(Char::isDigit).take(4) },
                    modifier = Modifier.width(56.dp).padding(horizontal = 8.dp).testTag("reader_panel_volume"),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
            } else if (volume != null) Box(Modifier.fillMaxHeight()) {
                // The whole height of the bar is the button: sized to its text, taps just above or below missed.
                Row(
                    Modifier
                        .fillMaxHeight()
                        .clickable(role = Role.DropdownList) { menu = true }
                        .padding(horizontal = 12.dp)
                        .testTag("reader_panel_volume"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(volumeLabel(volume!!, state.volumeUnit), color = colors.text, style = MaterialTheme.typography.bodyMedium)
                    Icon(AtharIcons.ChevronDown, null, tint = colors.secondary, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
                AtharMenu(expanded = menu, onDismiss = { menu = false }, width = 232.dp,
                    colors = com.atharchive.ui.components.AtharMenuColors(colors.page, colors.text, colors.secondary, colors.accent)) {
                    // The numbers six to a row: sixty fit with nothing to scroll.
                    volumes.chunked(6).forEach { row ->
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            row.forEach { (vol, _) ->
                                val on = vol == volume
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(AtharCorners.Control)
                                        .background(if (on) colors.accentFill else Color.Transparent)
                                        .selectable(selected = on, role = Role.RadioButton) { volume = vol; menu = false }
                                        .testTag("reader_panel_volume_$vol"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(arabicDigits(vol), color = if (on) colors.onAccent else colors.text, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
            if (here != null) Box(Modifier.width(0.7.dp).height(22.dp).background(colors.divider))
            BasicTextField(
                value = page,
                onValueChange = { typed -> page = typed.filter(Char::isDigit).take(5) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .testTag("reader_panel_page"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (page.isEmpty()) Text(
                            state.pageRange?.let { "الصفحة ${arabicDigits(it.first)}–${arabicDigits(it.last)}" } ?: "رقم الصفحة",
                            color = colors.secondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        )
                        inner()
                    }
                },
            )
        }
        TextButton(onClick = { go() }, enabled = ready,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent, disabledContentColor = colors.secondary)) {
            Text("انتقل", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PanelSearch(
    query: String,
    hits: List<InBookHit>,
    colors: ReaderColors,
    onQueryChange: (String) -> Unit,
    onHit: (InBookHit) -> Unit,
    status: String?,
    partialSearch: Int? = null,
    downloadAsked: Boolean = false,
    onDownloadForSearch: (() -> Unit)? = null,
    recentQueries: List<String> = emptyList(),
    searchMode: com.atharchive.feature.search.SearchMode? = null,
    onSearchMode: (com.atharchive.feature.search.SearchMode) -> Unit = {},
    loadPassage: suspend (InBookHit) -> String? = { null },
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(AtharCorners.Card)
                .atharControl(colors.divider.copy(alpha = 0.28f), AtharCorners.Card)
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

        if (searchMode != null) {
            val modes = listOf(
                com.atharchive.feature.search.SearchMode.All, com.atharchive.feature.search.SearchMode.Any,
                com.atharchive.feature.search.SearchMode.Phrase, com.atharchive.feature.search.SearchMode.Exact,
            )
            Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) {
                SegmentRow(modes.map { it.label }, searchMode.label, colors, "reader_search_mode") { label ->
                    onSearchMode(modes.first { it.label == label })
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
            partialSearch?.let { percent ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (downloadAsked) "جارٍ تحميل الكتاب؛ تكتمل النتائج بعده تلقائيًا"
                            else "بُحث في المحفوظ فقط (${com.atharchive.ui.components.arabicDigits(percent)}٪ من الكتاب)",
                        modifier = Modifier.weight(1f),
                        color = colors.secondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (onDownloadForSearch != null && !downloadAsked) {
                        androidx.compose.material3.TextButton(
                            onClick = onDownloadForSearch,
                            modifier = Modifier.testTag("reader_panel_search_download"),
                        ) { Text("تحميل الكتاب كاملًا", color = colors.accent, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (query.isBlank()) items(recentQueries, key = { "recent-$it" }) { recent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onQueryChange(recent) }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                        .testTag("reader_panel_recent_$recent"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AtharIcons.Repeat, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(16.dp))
                    Text(recent, modifier = Modifier.padding(start = 10.dp), color = colors.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
            items(hits, key = { it.id }) { hit ->
                var expanded by remember(hit.id) { mutableStateOf(false) }
                var passage by remember(hit.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(expanded) {
                    if (expanded && passage == null) passage = loadPassage(hit)?.let { com.atharchive.feature.search.around(it, hit.excerpt, hit.matches) }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onHit(hit) }
                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 11.dp)
                        .testTag("reader_panel_hit_${hit.id}"),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = listOfNotNull(hit.chapterTitle.ifBlank { null }, hit.pageLabel).joinToString(" · "),
                            modifier = Modifier.weight(1f),
                            color = colors.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        androidx.compose.material3.IconButton(onClick = { expanded = !expanded }, Modifier.size(32.dp).testTag("reader_panel_hit_more_${hit.id}")) {
                            Icon(AtharIcons.ChevronDown, contentDescription = if (expanded) "أقل" else "المزيد", tint = colors.secondary,
                                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f })
                        }
                    }
                    // The words the search matched (its own ranges), not the query re-found letter by letter:
                    // that lit «كتب» inside «مكتبة» and missed every word of a two-word query.
                    val shown = passage ?: hit.excerpt
                    val ranges = if (passage == null) hit.matches else com.atharchive.feature.search.wordRanges(shown, hit.excerpt, hit.matches)
                    Text(
                        text = if (ranges.isEmpty()) highlightMatches(shown, query, colors.highlight) else markRanges(shown, ranges, colors.highlight),
                        color = colors.text,
                        fontFamily = AtharEditorialFontFamily,
                        fontSize = AtharTextSize.Ui,
                        lineHeight = 27.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
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

/**
 * One contents line. A كتاب is a heading of its own: bold, with a rule over it that separates the
 * groups. A باب under it hangs off a thin tree line, smaller and with no rule, so the two levels
 * never read alike. The current line wears a soft accent pill.
 */
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
    val kitab = !indent && entry.level <= 2
    Column {
        if (kitab) HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 0.7.dp, color = colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .padding(start = if (indent) 22.dp else 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The tree line: the أبواب hang off it.
            if (indent) Box(Modifier.fillMaxHeight().width(1.dp).background(colors.divider))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (indent) 6.dp else 0.dp, top = 2.dp, bottom = 2.dp)
                    .clip(AtharCorners.Control)
                    .background(if (active) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(role = Role.Button) { onJump(entry.blockIndex) }
                    .defaultMinSize(minHeight = if (kitab) 46.dp else 38.dp)
                    .padding(start = 8.dp, end = if (fold == null) 8.dp else 0.dp, top = if (kitab) 9.dp else 6.dp, bottom = if (kitab) 9.dp else 6.dp)
                    .testTag("reader_toc_${entry.anchor}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title,
                    modifier = Modifier.weight(1f),
                    color = when { active -> colors.accent; kitab -> colors.text; else -> colors.text.copy(alpha = 0.82f) },
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = if (kitab) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (kitab) 16.sp else 14.5.sp,
                    lineHeight = if (kitab) 25.sp else 22.sp,
                )
                if (entry.page.isNotEmpty()) Text(entry.page, Modifier.padding(start = 10.dp), color = colors.secondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (fold != null) Box(
                    Modifier.size(36.dp).clip(AtharCorners.Control).clickable(role = Role.Button, onClickLabel = if (fold) "طيّ الأبواب" else "عرض الأبواب", onClick = onFold),
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
        }
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
