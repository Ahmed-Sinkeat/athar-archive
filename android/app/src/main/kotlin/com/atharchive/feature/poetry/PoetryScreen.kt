package com.atharchive.feature.poetry

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharBookmarkButton
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharMetaPill
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

private const val AllTopics = "كل المواضيع"

@Composable
fun PoetryScreen(
    state: PoetryUiState,
    onSettings: () -> Unit,
    onPoemClick: (PoemUi) -> Unit,
    onSaveClick: (PoemUi) -> Unit,
    onDownloadClick: (PoemUi) -> Unit,
    modifier: Modifier = Modifier,
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(PoetryTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf(AllTopics) }
    var sort by rememberSaveable { mutableStateOf(PoemSort.Newest) }
    val listState = rememberLazyListState()

    val topics = remember(state.poems) {
        listOf(AllTopics) + state.poems.map { it.topic }.distinct()
    }
    val hasActiveFilters = topic != AllTopics || sort != PoemSort.Newest

    val visiblePoems = remember(state.poems, selectedTab, query, topic, sort) {
        state.poems.filter { poem ->
            val matchesTab = when (selectedTab) {
                PoetryTab.All -> true
                PoetryTab.Downloaded -> poem.downloaded
                PoetryTab.MyList -> poem.saved
            }
            val term = query.trim()
            val matchesQuery = term.isBlank() ||
                poem.title.contains(term, ignoreCase = true) ||
                poem.poet.contains(term, ignoreCase = true) ||
                poem.openingVerses.any { it.contains(term, ignoreCase = true) }
            matchesTab && matchesQuery && (topic == AllTopics || poem.topic == topic)
        }.sortedWith(
            when (sort) {
                PoemSort.Title -> compareBy { it.title }
                PoemSort.Poet -> compareBy { it.poet }
                PoemSort.Newest -> compareBy { state.poems.indexOf(it) }
            },
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_poetry")
            .background(AtharTheme.colors.canvas),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp
        val compactTitle = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "الشعر",
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
                .testTag("poetry_screen"),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "poetry-search") {
                AtharSearchField(
                    query = query,
                    placeholder = "ابحث عن قصيدة، بيت أو شاعر…",
                    onQueryChange = { query = it },
                    tagPrefix = "poetry",
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 4.dp,
                    ),
                )
            }
            item(key = "poetry-tabs") {
                AtharTabRow(
                    tabs = PoetryTab.tabs,
                    selectedKey = selectedTab.key,
                    onSelect = { selectedTab = PoetryTab.fromKey(it) },
                    tagPrefix = "poetry",
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }
            item(key = "poetry-menus") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding - 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AtharMenuButton(
                        label = "الموضوع",
                        options = topics,
                        selected = topic,
                        defaultOption = AllTopics,
                        onSelect = { topic = it },
                        tagPrefix = "poetry_topic",
                    )
                    AtharMenuButton(
                        label = "الترتيب",
                        options = PoemSort.entries.map { it.label },
                        selected = sort.label,
                        defaultOption = PoemSort.Newest.label,
                        onSelect = { label -> sort = PoemSort.entries.first { it.label == label } },
                        tagPrefix = "poetry_sort",
                    )
                }
            }
            item(key = "poetry-list-divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    color = AtharTheme.colors.divider.copy(alpha = 0.72f),
                )
            }
            if (visiblePoems.isEmpty()) {
                item(key = "poetry-empty") {
                    val (title, body) = when {
                        query.isNotBlank() ->
                            "لا توجد نتائج" to "جرّب اسم قصيدة أو شاعر أقصر."
                        hasActiveFilters ->
                            "لا توجد قصائد بهذه التصفية" to "امسح بعض المرشحات لعرض مزيد من القصائد."
                        selectedTab == PoetryTab.Downloaded ->
                            "لا توجد قصائد محمّلة" to "نزّل قصيدة لتقرأها دون اتصال."
                        else ->
                            "قائمتك فارغة" to "ستظهر هنا القصائد المحفوظة في قائمتك."
                    }
                    AtharEmptyState(
                        title = title,
                        body = body,
                        tag = "poetry_empty_state",
                        horizontalPadding = horizontalPadding,
                    )
                }
            } else {
                itemsIndexed(visiblePoems, key = { _, poem -> poem.id }) { index, poem ->
                    PoemRow(
                        poem = poem,
                        query = query,
                        compactTitle = compactTitle,
                        horizontalPadding = horizontalPadding,
                        onClick = { onPoemClick(poem) },
                        onSave = { onSaveClick(poem) },
                        onDownload = { onDownloadClick(poem) },
                    )
                    if (index != visiblePoems.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = horizontalPadding),
                            color = AtharTheme.colors.divider.copy(alpha = 0.64f),
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PoemRow(
    poem: PoemUi,
    query: String,
    compactTitle: Boolean,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 104.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 16.dp)
            .testTag("poem_row_${poem.id}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val hl = AtharTheme.colors.accent.copy(alpha = 0.18f)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = highlightMatches(poem.title, query, hl),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compactTitle) 18.sp else 20.sp,
                    lineHeight = if (compactTitle) 27.sp else 29.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = highlightMatches(poem.poet, query, hl),
                modifier = Modifier.padding(top = 1.dp),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The verse is the hero of this screen, not the metadata around it: near-black
            // editorial type at reading size, tashkeel intact, never normalised for
            // display. Anything quieter and the section reads as a second books list.
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .testTag("poem_opening_${poem.id}"),
            ) {
                poem.openingVerses.take(2).forEachIndexed { index, verse ->
                    Text(
                        text = if (index == poem.openingVerses.take(2).lastIndex) {
                            "$verse …"
                        } else {
                            verse
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        fontFamily = AtharEditorialFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 32.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (poem.verseCountLabel != null || poem.downloaded) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (poem.verseCountLabel != null) {
                        AtharMetaPill(poem.verseCountLabel)
                    }
                    AtharMetaPill(poem.sizeLabel, latinDigits = true)
                }
            }
        }
        AtharBookmarkButton(
            saved = poem.saved,
            onToggle = onSave,
            itemTitle = poem.title,
            tag = "poem_save_${poem.id}",
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDownload)
                .semantics {
                    contentDescription = when {
                        poem.downloaded -> "${poem.title} محمّلة، الحجم ${poem.sizeLabel}"
                        poem.downloading -> "جار تنزيل ${poem.title}، الحجم ${poem.sizeLabel}"
                        else -> "تنزيل ${poem.title}، الحجم ${poem.sizeLabel}"
                    }
                }
                .testTag("poem_download_${poem.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (poem.downloaded) AtharIcons.Check else AtharIcons.Download,
                contentDescription = null,
                tint = when {
                    poem.downloaded -> AtharTheme.colors.success
                    poem.downloading -> AtharTheme.colors.accent
                    else -> AtharTheme.colors.secondaryText
                },
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
