package com.atharchive.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.atharchive.ui.icons.AtharIcons

@Immutable
data class ContinueReadingUi(
    val title: String,
    val author: String,
    val location: String,
    val progress: Float,
    val progressLabel: String,
)

@Immutable
data class QuickAccessUi(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

@Immutable
data class IssueUi(
    val id: String,
    val title: String,
)

@Immutable
data class RecentItemUi(
    val id: String,
    val title: String,
    val context: String,
    val time: String,
    val icon: ImageVector,
)

@Immutable
data class ArchiveSelectionUi(
    val title: String,
    val author: String,
    val description: String,
)

@Immutable
data class HomeUiState(
    val continueReading: ContinueReadingUi?,
    val quickAccess: List<QuickAccessUi>,
    val issues: List<IssueUi>,
    val recentItems: List<RecentItemUi>,
    val archiveSelection: ArchiveSelectionUi?,
    val archiveScale: String,
)

val HomeFixture = HomeUiState(
    continueReading = ContinueReadingUi(
        title = "العقيدة الطحاوية",
        author = "أبو جعفر الطحاوي",
        location = "الباب الثالث · ص ١٣١ من ٢٨٤",
        progress = 0.46f,
        progressLabel = "٤٦٪",
    ),
    quickAccess = listOf(
        QuickAccessUi("mutun", "المتون", AtharIcons.Books),
        QuickAccessUi("adhkar", "الأذكار", AtharIcons.Adhkar),
        QuickAccessUi("audio", "الصوتيات", AtharIcons.Audio),
        QuickAccessUi("people", "التراجم", AtharIcons.People),
    ),
    issues = listOf(
        IssueUi("tawassul", "حكم التوسل بذوات الصالحين"),
        IssueUi("raising-hands", "حكم رفع اليدين بعد الصلاة"),
        IssueUi("quran-menstruation", "حكم قراءة القرآن للحائض"),
    ),
    recentItems = listOf(
        RecentItemUi(
            id = "al-istiqamah",
            title = "الاستقامة",
            context = "كتاب · ابن تيمية",
            time = "منذ ساعتين",
            icon = AtharIcons.Books,
        ),
        RecentItemUi(
            id = "issue-tawassul",
            title = "حكم التوسل بذوات الصالحين",
            context = "مسألة · العقيدة والتوحيد",
            time = "أمس",
            icon = AtharIcons.Issues,
        ),
        RecentItemUi(
            id = "article-seeking-knowledge",
            title = "فضل العلم وآداب طلبه",
            context = "مقالة · آداب طالب العلم",
            time = "منذ ٣ أيام",
            icon = AtharIcons.Articles,
        ),
    ),
    archiveSelection = ArchiveSelectionUi(
        title = "جامع بيان العلم وفضله",
        author = "ابن عبد البر القرطبي",
        description = "من أصول المكتبة في فضل العلم وآداب حمله وطلبه.",
    ),
    archiveScale = "٤٬٧٣١ مادة في الأرشيف",
)
