package com.atharchive.feature.issues

import com.atharchive.ui.components.directed
import com.atharchive.ui.components.AtharDirectionGroup
import com.atharchive.ui.components.normKey
import com.atharchive.feature.search.sectionTextHits
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharFavouriteButton
import com.atharchive.ui.components.AtharDownloadButton
import com.atharchive.ui.components.AtharChipGroup
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharFilterButton
import com.atharchive.ui.components.AtharFacet
import com.atharchive.ui.components.asChoiceSet
import com.atharchive.ui.components.asStoredChoices
import com.atharchive.ui.components.enumValueOrNull
import com.atharchive.ui.components.orFirstIn
import com.atharchive.ui.components.rememberPersisted
import com.atharchive.ui.components.SectionTab
import com.atharchive.ui.components.emptyCopy
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme


@Composable
fun IssuesScreen(
    state: IssuesUiState,
    onSettings: () -> Unit,
    onIssueClick: (IssueUi) -> Unit,
    onSaveClick: (IssueUi) -> Unit,
    onDownloadClick: (IssueUi) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    textSearch: com.atharchive.feature.search.SectionTextSearch? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SectionTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    val textHits = com.atharchive.feature.search.rememberSectionTextHits(query, textSearch)
    var topicStored by rememberPersisted("issues.topics", "")
    var sortName by rememberPersisted("issues.order", IssueSort.Question.name)
    var descendingStored by rememberPersisted("issues.order.desc", "")
    val descending = descendingStored.isNotEmpty()
    val sort = enumValueOrNull<IssueSort>(sortName) ?: IssueSort.Question
    val listState = rememberLazyListState()
    val reselectScope = androidx.compose.runtime.rememberCoroutineScope()

    val topics = remember(state.issues) {
        state.issues.map { it.topic }.filter(String::isNotBlank).distinct()
    }
    val topic = topicStored.asChoiceSet() intersect topics.toSet()
    val hasActiveFilters = topic.isNotEmpty() || sort != IssueSort.Newest

    val visible = remember(state.issues, selectedTab, query, topic, sort, descending) {
        val term = ArNormalize.normalize(query.trim())
        state.issues.filter { issue ->
            val matchesTab = selectedTab.matches(issue.recentRank != null, issue.downloaded, issue.saved)
            val matchesQuery = term.isBlank() ||
                normKey(issue.question).contains(term, ignoreCase = true) ||
                normKey(issue.scholar).contains(term, ignoreCase = true) ||
                normKey(issue.answerExcerpt).contains(term, ignoreCase = true)
            matchesTab && matchesQuery && (topic.isEmpty() || issue.topic in topic)
        }.sortedWith(
            if (selectedTab == SectionTab.Recent) compareBy { it.recentRank ?: Int.MAX_VALUE } else (when (sort) {
                IssueSort.Question -> compareBy<IssueUi> { normKey(it.question) }
                IssueSort.Scholar -> compareBy<IssueUi> { normKey(it.scholar) }
                IssueSort.Newest -> compareBy<IssueUi> { it.question }
            }).directed(descending),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_issues")
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
                        title = "المسائل",
                        onSettings = onSettings,
                        showAppIcon = true,
                        onBack = onBack,
                        horizontalPadding = horizontalPadding,
                    )
                    AtharSearchField(
                        query = query,
                        placeholder = "ابحث في ${state.countLabel}…",
                        onQueryChange = { query = it },
                        tagPrefix = "issues",
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
                        tagPrefix = "issues",
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
                                if (selectedTab == SectionTab.Recent) null else AtharChipGroup("الترتيب", IssueSort.entries.map { it.label }, sort.label) { label -> sortName = IssueSort.entries.first { it.label == label }.name },
                            if (selectedTab == SectionTab.Recent) null else AtharDirectionGroup(descending) { descendingStored = if (it) "1" else "" },
                            ),
                            facets = listOf(AtharFacet("الموضوع", topics, topic, { topicStored = it.asStoredChoices() })),
                            tagPrefix = "issues_filter",
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = horizontalPadding,
                            vertical = 2.dp,
                        ),
                        thickness = 0.7.dp,
                        color = AtharTheme.colors.divider.copy(alpha = 0.5f),
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .nestedScroll(header.connection)
                .testTag("issues_screen"),
                contentPadding = PaddingValues(top = header.height, bottom = 28.dp),
            ) {

                if (!state.loaded) {
                    // first catalog read not back yet: show nothing rather than a false "no issues"
                } else if (visible.isEmpty() && textHits.isEmpty()) {
                    item(key = "issues-empty") {
                        val (title, body) = when {
                            query.isNotBlank() ->
                                "لا توجد نتائج" to "جرّب كلمة أقصر من نص المسألة أو اسم المجيب."
                            hasActiveFilters ->
                                "لا توجد مسائل بهذه التصفية" to "اختر «كل المواضيع» لعرض مزيد من المسائل."
                            else -> selectedTab.emptyCopy("مسائل", "نزّل مسألة لتقرأها دون اتصال.")
                        }
                        AtharEmptyState(
                            title = title,
                            body = body,
                            tag = "issues_empty_state",
                            horizontalPadding = horizontalPadding,
                        )
                    }
                } else {
                    itemsIndexed(visible, key = { _, issue -> issue.id }) { index, issue ->
                        IssueRow(
                            issue = issue,
                            query = query,
                            compactTitle = compactTitle,
                            horizontalPadding = horizontalPadding,
                            onClick = { onIssueClick(issue) },
                            onSave = { onSaveClick(issue) },
                            onDownload = { onDownloadClick(issue) },
                        )
                        if (index != visible.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = horizontalPadding),
                                thickness = 0.7.dp,
                                color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                            )
                        }
                    }
                }
                sectionTextHits(textHits, query, textSearch, horizontalPadding)
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: IssueUi,
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
            .defaultMinSize(minHeight = 96.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 12.dp)
            .testTag("issue_row_${issue.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val hl = AtharTheme.colors.accent.copy(alpha = 0.18f)
        Column(Modifier.weight(1f)) {
            // The question is the title. No «س:» prefix — the section is called المسائل
            // and the shape of the sentence already says what it is.
            Text(
                text = highlightMatches(issue.question, query, hl),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (compactTitle) 18.sp else 20.sp,
                lineHeight = if (compactTitle) 27.sp else 29.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = highlightMatches("المجيب: ${issue.scholar}", query, hl),
                modifier = Modifier.padding(top = 2.dp),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (issue.answerExcerpt.trim().removeSurrounding("[", "]").trim() != issue.question.trim().removeSurrounding("[", "]").trim()) Text(
                text = issue.answerExcerpt,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .testTag("issue_excerpt_${issue.id}"),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 23.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val factColor = AtharTheme.colors.secondaryText
                if (issue.topic.isNotBlank()) Text(
                    text = issue.topic,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        AtharDownloadButton(
            title = issue.question,
            sizeLabel = issue.sizeLabel,
            downloaded = issue.downloaded,
            transfer = issue.transfer,
            onClick = onDownload,
            tag = "issue_download_${issue.id}",
        )
        AtharFavouriteButton(
            saved = issue.saved,
            onToggle = onSave,
            itemTitle = issue.question,
            tag = "issue_save_${issue.id}",
        )
    }
}
