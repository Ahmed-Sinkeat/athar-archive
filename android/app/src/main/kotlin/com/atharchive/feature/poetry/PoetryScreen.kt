package com.atharchive.feature.poetry

import kotlinx.coroutines.launch
import com.atharchive.ui.theme.AtharTextSize
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
import com.atharchive.core.ArNormalize
import com.atharchive.feature.downloads.TransferUi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.atharchive.ui.components.AtharFloatingHeader
import com.atharchive.ui.components.ShowOnChange
import com.atharchive.ui.components.rememberAtharCollapsingHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.atharchive.ui.components.AtharChipGroup
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharFilterButton
import com.atharchive.ui.components.enumValueOrNull
import com.atharchive.ui.components.orFirstIn
import com.atharchive.ui.components.rememberPersisted
import com.atharchive.ui.components.SectionTab
import com.atharchive.ui.components.emptyCopy
import com.atharchive.ui.components.AtharFavouriteButton
import com.atharchive.ui.components.AtharDownloadButton
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
    scrollToTopRequest: Int = 0,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SectionTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var topicStored by rememberPersisted("poetry.topic", AllTopics)
    var sortName by rememberPersisted("poetry.order", PoemSort.Title.name)
    val sort = enumValueOrNull<PoemSort>(sortName) ?: PoemSort.Title
    val listState = rememberLazyListState()
    val reselectScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    val topics = remember(state.poems) {
        listOf(AllTopics) + state.poems.map { it.topic }.filter(String::isNotBlank).distinct()
    }
    val topic = topicStored.orFirstIn(topics)
    val hasActiveFilters = topic != AllTopics || sort != PoemSort.Newest

    // Normalized once per catalog change, not per filter pass or per sort comparison.
    val normalized = remember(state.poems) {
        state.poems.associate { poem ->
            poem.id to Triple(ArNormalize.normalize(poem.title), ArNormalize.normalize(poem.poet), poem.openingVerses.map(ArNormalize::normalize))
        }
    }
    val visiblePoems = remember(state.poems, selectedTab, query, topic, sort) {
        val term = ArNormalize.normalize(query.trim())
        state.poems.filter { poem ->
            val matchesTab = selectedTab.matches(poem.recentRank != null, poem.downloaded, poem.saved)
            val (title, poet, opening) = normalized.getValue(poem.id)
            val matchesQuery = term.isBlank() ||
                title.contains(term, ignoreCase = true) ||
                poet.contains(term, ignoreCase = true) ||
                opening.any { it.contains(term, ignoreCase = true) }
            matchesTab && matchesQuery && (topic == AllTopics || poem.topic == topic)
        }.sortedWith(
            if (selectedTab == SectionTab.Recent) compareBy { it.recentRank ?: Int.MAX_VALUE } else when (sort) {
                PoemSort.Title -> compareBy { normalized.getValue(it.id).first }
                PoemSort.Poet -> compareBy { normalized.getValue(it.id).second }
                PoemSort.Newest -> compareBy { it.title }
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

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            val header = rememberAtharCollapsingHeader()
            header.ShowOnChange(listState, selectedTab, query, topic, sort)
            AtharFloatingHeader(header) {
                Column {
                    AtharTopBar(
                        title = "الشعر",
                        onSettings = onSettings,
                        showAppIcon = true,
                        onBack = onBack,
                        horizontalPadding = horizontalPadding,
                    )
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
            
        
            Column {
                AtharTabRow(
                    tabs = SectionTab.tabs,
                    selectedKey = selectedTab.key,
                    onSelect = { selectedTab = SectionTab.fromKey(it) },
                    tagPrefix = "poetry",
                    onReselect = { reselectScope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding - 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AtharFilterButton(
                        groups = listOfNotNull(
                            if (selectedTab == SectionTab.Recent) null else AtharChipGroup("الترتيب", PoemSort.entries.map { it.label }, sort.label) { label -> sortName = PoemSort.entries.first { it.label == label }.name },
                            AtharChipGroup("الموضوع", topics, topic) { topicStored = it },
                        ),
                        tagPrefix = "poetry_filter",
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    color = AtharTheme.colors.divider.copy(alpha = 0.72f),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .nestedScroll(header.connection)
                .testTag("poetry_screen"),
            contentPadding = PaddingValues(top = header.height, bottom = 28.dp),
        ) {
            if (!state.loaded) {
                // first catalog read not back yet: show nothing rather than a false "no poems"
            } else if (visiblePoems.isEmpty()) {
                item(key = "poetry-empty") {
                    val (title, body) = when {
                        query.isNotBlank() ->
                            "لا توجد نتائج" to "جرّب اسم قصيدة أو شاعر أقصر."
                        hasActiveFilters ->
                            "لا توجد قصائد بهذه التصفية" to "امسح بعض المرشحات لعرض مزيد من القصائد."
                        else -> selectedTab.emptyCopy("قصائد", "نزّل قصيدة لتقرأها دون اتصال.")
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
                // A first line identical to the title would only repeat it.
                val shown = poem.openingVerses.filter { it.trim() != poem.title.trim() }.take(2)
                shown.forEachIndexed { index, verse ->
                    Text(
                        text = if (index == shown.lastIndex) {
                            "$verse …"
                        } else {
                            verse
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        fontFamily = AtharEditorialFontFamily,
                        fontSize = AtharTextSize.Title,
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
                }
            }
        }
        AtharDownloadButton(
            title = poem.title,
            sizeLabel = poem.sizeLabel,
            downloaded = poem.downloaded,
            transfer = poem.transfer,
            onClick = onDownload,
            tag = "poem_download_${poem.id}",
        )
        AtharFavouriteButton(
            saved = poem.saved,
            onToggle = onSave,
            itemTitle = poem.title,
            tag = "poem_save_${poem.id}",
        )
    }
}
