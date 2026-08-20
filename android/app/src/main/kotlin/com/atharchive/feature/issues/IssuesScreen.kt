package com.atharchive.feature.issues

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharBookmarkButton
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharSearchField
import com.atharchive.ui.components.AtharTabRow
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.highlightMatches
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

private const val AllTopics = "كل المواضيع"

@Composable
fun IssuesScreen(
    state: IssuesUiState,
    onSettings: () -> Unit,
    onIssueClick: (IssueUi) -> Unit,
    onSaveClick: (IssueUi) -> Unit,
    onDownloadClick: (IssueUi) -> Unit,
    modifier: Modifier = Modifier,
    onLogo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(IssuesTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf(AllTopics) }
    var sort by rememberSaveable { mutableStateOf(IssueSort.Newest) }
    val listState = rememberLazyListState()

    val topics = remember(state.issues) {
        listOf(AllTopics) + state.issues.map { it.topic }.distinct()
    }
    val hasActiveFilters = topic != AllTopics || sort != IssueSort.Newest

    val visible = remember(state.issues, selectedTab, query, topic, sort) {
        state.issues.filter { issue ->
            val matchesTab = when (selectedTab) {
                IssuesTab.All -> true
                IssuesTab.Downloaded -> issue.downloaded
                IssuesTab.MyList -> issue.saved
            }
            val term = query.trim()
            val matchesQuery = term.isBlank() ||
                issue.question.contains(term, ignoreCase = true) ||
                issue.scholar.contains(term, ignoreCase = true) ||
                issue.answerExcerpt.contains(term, ignoreCase = true)
            matchesTab && matchesQuery && (topic == AllTopics || issue.topic == topic)
        }.sortedWith(
            when (sort) {
                IssueSort.Question -> compareBy { it.question }
                IssueSort.Scholar -> compareBy { it.scholar }
                IssueSort.Newest -> compareBy { state.issues.indexOf(it) }
            },
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

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "المسائل",
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
                    .testTag("issues_screen"),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item(key = "issues-search") {
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
                item(key = "issues-tabs") {
                    AtharTabRow(
                        tabs = IssuesTab.tabs,
                        selectedKey = selectedTab.key,
                        onSelect = { selectedTab = IssuesTab.fromKey(it) },
                        tagPrefix = "issues",
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
                item(key = "issues-menus") {
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
                            tagPrefix = "issues_topic",
                        )
                        AtharMenuButton(
                            label = "الترتيب",
                            options = IssueSort.entries.map { it.label },
                            selected = sort.label,
                            defaultOption = IssueSort.Newest.label,
                            onSelect = { label ->
                                sort = IssueSort.entries.first { it.label == label }
                            },
                            tagPrefix = "issues_sort",
                        )
                    }
                }
                item(key = "issues-list-divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = horizontalPadding,
                            vertical = 2.dp,
                        ),
                        thickness = 0.7.dp,
                        color = AtharTheme.colors.divider.copy(alpha = 0.5f),
                    )
                }

                if (visible.isEmpty()) {
                    item(key = "issues-empty") {
                        val (title, body) = when {
                            query.isNotBlank() ->
                                "لا توجد نتائج" to "جرّب كلمة أقصر من نص المسألة أو اسم المجيب."
                            hasActiveFilters ->
                                "لا توجد مسائل بهذه التصفية" to "اختر «كل المواضيع» لعرض مزيد من المسائل."
                            selectedTab == IssuesTab.Downloaded ->
                                "لا توجد مسائل محمّلة" to "نزّل مسألة لتقرأها دون اتصال."
                            selectedTab == IssuesTab.MyList ->
                                "قائمتك فارغة" to "ستظهر هنا المسائل المحفوظة في قائمتك."
                            else ->
                                "لا توجد مسائل" to "لم تتوفر مسائل في هذا القسم بعد."
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
            Text(
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
                val factColor = AtharTheme.colors.secondaryText.copy(alpha = 0.72f)
                Text(
                    text = issue.topic,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Text("·", color = factColor, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = issue.sizeLabel,
                    color = factColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        // Save, then download — the order Books and الشعر use. المقالات has them the
        // other way round; that row is the one that should move.
        AtharBookmarkButton(
            saved = issue.saved,
            onToggle = onSave,
            itemTitle = issue.question,
            tag = "issue_save_${issue.id}",
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDownload)
                .semantics {
                    contentDescription = if (issue.downloaded) {
                        "${issue.question} محمّلة، الحجم ${issue.sizeLabel}"
                    } else {
                        "تنزيل ${issue.question}، الحجم ${issue.sizeLabel}"
                    }
                }
                .testTag("issue_download_${issue.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (issue.downloaded) AtharIcons.Check else AtharIcons.Download,
                contentDescription = null,
                tint = if (issue.downloaded) {
                    AtharTheme.colors.success
                } else {
                    AtharTheme.colors.secondaryText
                },
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
