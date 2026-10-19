package com.atharchive.feature.kannashah

/**
 * الكناشة is a notebook of full excerpts, not a bookmark manager.
 *
 * Two deliberate absences, both from the design decisions of 18 Aug 2026:
 * there are **no user-created collections** — source and topic are the only two ways to
 * organise, and a third manual layer earns its place only when one is demonstrably
 * needed — and there is **no truncation**: [text] is always rendered in full, in every
 * view, so الكناشة is itself a reading surface rather than a list of doorways.
 */

enum class KannashahSort(val label: String) {
    Newest("الأحدث"),
    Oldest("الأقدم"),
    SourceOrder("ترتيب المصدر"),
}

enum class KannashahFilter(val label: String) {
    All("كل المقتطفات"),
    Highlights("التظليلات"),
    Notes("التعليقات"),
    Lost("فقدت موضعها"),
}

data class ExcerptUi(
    val id: String,
    val entityId: String = "",
    /** The saved text, complete. Never shortened for display. */
    val text: String,
    val sourceTitle: String,
    val sourceAuthor: String,
    /** e.g. "ص ١٣١" — absent for sources without pagination. */
    val locationLabel: String? = null,
    val topics: List<String> = emptyList(),
    /** The user's own note. Always visually subordinate to [text]. */
    val comment: String? = null,
    /** Sort key for ترتيب المصدر; ordinal within the source work. */
    val sourceOrdinal: Int = 0,
    val addedOrder: Long = 0,
    val kind: String = "highlight",
    val attachmentState: String = "exact",
    val sourceUrl: String = "",
)

data class KannashahUiState(
    val headline: String = "٠ مقتطفات من ٠ مصادر",
    val excerpts: List<ExcerptUi> = emptyList(),
)

val KannashahFixture = KannashahUiState(
    headline = "١٤٨ مقتطفًا من ٢٧ مصدرًا",
    excerpts = listOf(
        ExcerptUi(
            id = "e1",
            text = "إن الله واحد لا شريك له، ولا شيء مثله، وله الملك وله الحمد، " +
                "وهو على كل شيء قدير.",
            sourceTitle = "العقيدة الطحاوية",
            sourceAuthor = "أبو جعفر الطحاوي",
            locationLabel = "ص ١٣١",
            topics = listOf("التوحيد", "أسماء الله وصفاته"),
            sourceOrdinal = 412,
            addedOrder = 0,
        ),
        ExcerptUi(
            id = "e2",
            text = "ومن دلائل وحدانيته تعالى أنه لا شيء في الوجود يساويه في ذاته " +
                "ولا في صفاته ولا في أفعاله.",
            sourceTitle = "الاستقامة",
            sourceAuthor = "ابن تيمية",
            locationLabel = "ص ١٨٧",
            topics = listOf("التوحيد", "صفات الله"),
            sourceOrdinal = 88,
            addedOrder = 1,
        ),
        ExcerptUi(
            id = "e3",
            text = "ومن أعظم ما يعين طالب العلم على الثبات: ملازمة العلماء والصالحين، " +
                "وكثرة الدعاء.",
            sourceTitle = "شرح حلية طالب العلم",
            sourceAuthor = "ابن رجب الحنبلي",
            locationLabel = "ص ٤٨",
            topics = listOf("طلب العلم", "الصحبة"),
            comment = "يُراجع مع باب الصحبة في مدارج السالكين.",
            sourceOrdinal = 39,
            addedOrder = 2,
        ),
        ExcerptUi(
            id = "e4",
            text = "العلم قبل القول والعمل، فمن عمل قبل علم كان ما يفسد أكثر مما يصلح.",
            sourceTitle = "العلم قبل القول والعمل",
            sourceAuthor = "أهل الأثر",
            topics = listOf("طلب العلم"),
            sourceOrdinal = 3,
            addedOrder = 3,
        ),
        ExcerptUi(
            id = "e5",
            text = "بيان أن الإيمان قول وعمل، يزيد بالطاعة وينقص بالمعصية.",
            sourceTitle = "العقيدة الطحاوية",
            sourceAuthor = "أبو جعفر الطحاوي",
            locationLabel = "ص ١٢٢",
            topics = listOf("الإيمان"),
            sourceOrdinal = 388,
            addedOrder = 4,
        ),
        ExcerptUi(
            id = "e6",
            text = "ليس كمثله شيء، وهو السميع البصير.",
            sourceTitle = "العقيدة الطحاوية",
            sourceAuthor = "أبو جعفر الطحاوي",
            locationLabel = "ص ١٣٩",
            topics = listOf("التوحيد", "صفات الله"),
            sourceOrdinal = 430,
            addedOrder = 5,
        ),
    ),
)
