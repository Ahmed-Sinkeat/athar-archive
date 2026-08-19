package com.atharchive.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.R
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme
import com.atharchive.ui.theme.AtharUiFontFamily

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSettings: () -> Unit,
    onContinueReading: () -> Unit,
    onBrowseBooks: () -> Unit,
    onQuickAccess: (QuickAccessUi) -> Unit,
    onIssue: (IssueUi) -> Unit,
    onAllIssues: () -> Unit,
    onRecentItem: (RecentItemUi) -> Unit,
    scrollToTopRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen")
            .background(AtharTheme.colors.canvas),
    ) {
        val expanded = maxWidth >= 720.dp
        val horizontalPadding = if (expanded) 32.dp else 20.dp

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "home-header") {
                AtharTopBar(
                    title = "أهل الأثر",
                    onSettings = onSettings,
                    modifier = Modifier.padding(
                        start = horizontalPadding - 20.dp,
                        end = horizontalPadding - 20.dp,
                    ),
                )
            }

            item(key = "home-content") {
                if (expanded) {
                    TabletHomeContent(
                        state = state,
                        onContinueReading = onContinueReading,
                        onBrowseBooks = onBrowseBooks,
                        onQuickAccess = onQuickAccess,
                        onIssue = onIssue,
                        onAllIssues = onAllIssues,
                        onRecentItem = onRecentItem,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                } else {
                    PhoneHomeContent(
                        state = state,
                        onContinueReading = onContinueReading,
                        onBrowseBooks = onBrowseBooks,
                        onQuickAccess = onQuickAccess,
                        onIssue = onIssue,
                        onAllIssues = onAllIssues,
                        onRecentItem = onRecentItem,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneHomeContent(
    state: HomeUiState,
    onContinueReading: () -> Unit,
    onBrowseBooks: () -> Unit,
    onQuickAccess: (QuickAccessUi) -> Unit,
    onIssue: (IssueUi) -> Unit,
    onAllIssues: () -> Unit,
    onRecentItem: (RecentItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Spacer(Modifier.height(12.dp))
        ContinueSection(state.continueReading, onContinueReading, onBrowseBooks)
        MajorDivider()
        QuickAccessSection(state.quickAccess, onQuickAccess)
        SectionLink(
            text = "عرض جميع الكتب ←",
            onClick = onBrowseBooks,
            modifier = Modifier.testTag("all_books_link"),
        )
        MajorDivider()
        IssuesSection(state.issues, onIssue, onAllIssues)
        MajorDivider()
        RecentSection(state.recentItems, onRecentItem)
    }
}

@Composable
private fun TabletHomeContent(
    state: HomeUiState,
    onContinueReading: () -> Unit,
    onBrowseBooks: () -> Unit,
    onQuickAccess: (QuickAccessUi) -> Unit,
    onIssue: (IssueUi) -> Unit,
    onAllIssues: () -> Unit,
    onRecentItem: (RecentItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            ContinueSection(state.continueReading, onContinueReading, onBrowseBooks)
            MajorDivider()
            QuickAccessSection(state.quickAccess, onQuickAccess)
            SectionLink(
                text = "عرض جميع الكتب ←",
                onClick = onBrowseBooks,
                modifier = Modifier.testTag("all_books_link"),
            )
        }
        Column(Modifier.weight(1f)) {
            IssuesSection(state.issues, onIssue, onAllIssues)
            MajorDivider()
            RecentSection(state.recentItems, onRecentItem)
        }
    }
}

@Composable
private fun ContinueSection(
    item: ContinueReadingUi?,
    onContinueReading: () -> Unit,
    onBrowseBooks: () -> Unit,
) {
    SectionHeading("واصل القراءة", accented = true)
    if (item == null) {
        CalmEmptyState(
            title = "لا توجد قراءة حديثة",
            body = "عندما تفتح كتابًا سيظهر آخر موضع وصلت إليه هنا.",
        )
        SectionLink(
            text = "تصفح الكتب ←",
            onClick = onBrowseBooks,
            modifier = Modifier.testTag("browse_books_link"),
        )
        return
    }
    val continueDescription = "${stringResource(R.string.continue_reading)}، ${item.title}"

    Surface(
        onClick = onContinueReading,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("continue_reading")
            .semantics {
                contentDescription = continueDescription
            },
        color = Color.Transparent,
        shape = RoundedCornerShape(2.dp),
    ) {
        Column {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 32.sp,
            )
            Text(
                text = item.author,
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = item.location,
                modifier = Modifier.padding(top = 4.dp),
                color = AtharTheme.colors.secondaryText,
                fontFamily = AtharUiFontFamily,
                fontSize = 13.sp,
                lineHeight = 21.sp,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AtharTheme.colors.divider)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(item.progress, 0f..1f)
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progress)
                            .align(Alignment.CenterStart)
                            .background(AtharTheme.colors.accent),
                    )
                }
                Text(
                    text = item.progressLabel,
                    color = AtharTheme.colors.accent,
                    fontFamily = AtharUiFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "متابعة القراءة ←",
                    color = AtharTheme.colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun QuickAccessSection(
    items: List<QuickAccessUi>,
    onQuickAccess: (QuickAccessUi) -> Unit,
) {
    SectionHeading("وصول سريع")
    val largeText = LocalDensity.current.fontScale >= 1.6f
    val visibleItems = items.take(4)

    if (largeText) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visibleItems.forEach { item ->
                QuickAccessItem(item, onQuickAccess, Modifier.fillMaxWidth())
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visibleItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        QuickAccessItem(item, onQuickAccess, Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickAccessItem(
    item: QuickAccessUi,
    onQuickAccess: (QuickAccessUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onQuickAccess(item) },
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AtharTheme.colors.divider.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = AtharTheme.colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = item.label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun IssuesSection(
    items: List<IssueUi>,
    onIssue: (IssueUi) -> Unit,
    onAllIssues: () -> Unit,
) {
    SectionHeading("المسائل")
    if (items.isEmpty()) {
        CalmEmptyState(
            title = "لا توجد مسائل متاحة",
            body = "ستظهر هنا أحدث المسائل عند توفرها.",
        )
    } else {
        Column {
            items.take(3).forEachIndexed { index, item ->
                Surface(
                    onClick = { onIssue(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("issue_${item.id}"),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = item.title,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = AtharUiFontFamily,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = AtharIcons.Forward,
                            contentDescription = stringResource(R.string.open_item, item.title),
                            tint = AtharTheme.colors.secondaryText,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                if (index != items.take(3).lastIndex) {
                    HorizontalDivider(color = AtharTheme.colors.divider.copy(alpha = 0.58f))
                }
            }
        }
    }
    SectionLink(
        text = "عرض جميع المسائل ←",
        onClick = onAllIssues,
        modifier = Modifier.testTag("all_issues_link"),
    )
}

@Composable
private fun RecentSection(
    items: List<RecentItemUi>,
    onRecentItem: (RecentItemUi) -> Unit,
) {
    SectionHeading("مؤخرًا")
    if (items.isEmpty()) {
        CalmEmptyState(
            title = "لا توجد مواد حديثة",
            body = "ستظهر هنا المواد التي فتحتها مؤخرًا من مختلف أقسام الأرشيف.",
        )
        return
    }

    Column {
        items.take(3).forEachIndexed { index, item ->
            RecentItem(item = item, onClick = { onRecentItem(item) })
            if (index != items.take(3).lastIndex) {
                HorizontalDivider(color = AtharTheme.colors.divider.copy(alpha = 0.58f))
            }
        }
    }
}

@Composable
private fun RecentItem(item: RecentItemUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(2.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = AtharTheme.colors.secondaryText,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AtharUiFontFamily,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.context} · ${item.time}",
                    modifier = Modifier.padding(top = 1.dp),
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = AtharIcons.Forward,
                contentDescription = stringResource(R.string.open_item, item.title),
                tint = AtharTheme.colors.secondaryText,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, accented: Boolean = false) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (accented) {
            Box(
                Modifier
                    .size(width = 24.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AtharTheme.colors.accent),
            )
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 28.sp,
        )
    }
}

@Composable
private fun SectionLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = text,
                color = AtharTheme.colors.accent,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun MajorDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        color = AtharTheme.colors.divider.copy(alpha = 0.58f),
    )
}

@Composable
private fun CalmEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = body,
            modifier = Modifier.padding(top = 2.dp),
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(name = "Athar Home", widthDp = 393, heightDp = 852, locale = "ar")
@Composable
private fun HomePreview() {
    AtharTheme(darkTheme = false) {
        HomeScreen(
            state = HomeFixture,
            onSettings = {},
            onContinueReading = {},
            onBrowseBooks = {},
            onQuickAccess = {},
            onIssue = {},
            onAllIssues = {},
            onRecentItem = {},
        )
    }
}

@Preview(name = "Athar Home 200%", widthDp = 393, heightDp = 852, locale = "ar", fontScale = 2f)
@Composable
private fun HomeLargeTextPreview() {
    AtharTheme(darkTheme = false) {
        HomeScreen(
            state = HomeFixture,
            onSettings = {},
            onContinueReading = {},
            onBrowseBooks = {},
            onQuickAccess = {},
            onIssue = {},
            onAllIssues = {},
            onRecentItem = {},
        )
    }
}
