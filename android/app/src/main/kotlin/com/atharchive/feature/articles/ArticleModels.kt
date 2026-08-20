package com.atharchive.feature.articles

import com.atharchive.ui.components.AtharTab

/**
 * `src/content/article` carries title, status, published_at and person on all 2,363 entries
 * (measured 18 Aug 2026). It carries **no** description or excerpt field.
 *
 * `excerpt` below is therefore a required addition to the `app/v2` catalog contract (§4.2):
 * the build pipeline must emit a short opening extract per article. Until it does, these
 * fixtures stand in for it. Nothing derives an excerpt on device.
 */

enum class ArticlesTab(val key: String, val label: String) {
    All("all", "الكل"),
    Downloaded("downloaded", "المحمّلة"),
    MyList("mylist", "قائمتي"),
    ;

    companion object {
        val tabs: List<AtharTab> = entries.map { AtharTab(it.key, it.label) }
        fun fromKey(key: String): ArticlesTab = entries.first { it.key == key }
    }
}

enum class ArticleSort(val label: String) {
    Newest("الأحدث"),
    Title("العنوان أ–ي"),
    Author("المؤلف"),
}

data class ArticleUi(
    val id: String,
    val title: String,
    val author: String,
    /** Hijri publication year, already formatted for display. */
    val dateLabel: String,
    /** Opening extract from the article body — supplied by the build, never derived here. */
    val excerpt: String,
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val saved: Boolean = false,
)

data class ArticlesUiState(
    val countLabel: String,
    val articles: List<ArticleUi>,
)

val ArticlesFixture = ArticlesUiState(
    countLabel = "٢٬٣٦٣ مقالًا",
    articles = listOf(
        ArticleUi(
            id = "hukm-al-ihtifal-bil-mawlid",
            title = "حكم الاحتفال بالمولد النبوي",
            author = "محمد بن صالح العثيمين",
            dateLabel = "١٤٤٥هـ",
            excerpt = "الحمد لله رب العالمين، وبعد: فإن الاحتفال بالمولد النبوي لم يكن " +
                "معروفًا في القرون المفضلة، ولم ينقل عن أحد من الصحابة رضوان الله عليهم…",
            saved = true,
        ),
        ArticleUi(
            id = "fadl-talab-al-ilm",
            title = "فضل طلب العلم",
            author = "عبد العزيز بن باز",
            dateLabel = "١٤٤٤هـ",
            excerpt = "إن طلب العلم من أفضل القربات وأجل الطاعات، وقد رفع الله أهله " +
                "درجات، وجعلهم ورثة الأنبياء في البلاغ والبيان…",
            downloaded = true,
        ),
        ArticleUi(
            id = "adab-talib-al-ilm-maa-shaykhihi",
            title = "آداب طالب العلم مع شيخه",
            author = "بكر بن عبد الله أبو زيد",
            dateLabel = "١٤٤٣هـ",
            excerpt = "من أعظم ما يعين الطالب على التحصيل: حسن أدبه مع معلمه، وتوقيره " +
                "في حضرته وغيبته، وترك الاعتراض عليه بما لا يحسن…",
            downloaded = true,
            saved = true,
        ),
        ArticleUi(
            id = "manzilat-al-sunnah-fil-islam",
            title = "منزلة السنة في الإسلام",
            author = "محمد ناصر الدين الألباني",
            dateLabel = "١٤٤٢هـ",
            excerpt = "اعلم أن السنة النبوية هي الأصل الثاني من أصول التشريع، وأنه لا " +
                "سبيل إلى فهم الكتاب على وجهه إلا بها…",
        ),
        ArticleUi(
            id = "al-ikhlas-wa-atharuhu",
            title = "الإخلاص وأثره في قبول العمل",
            author = "صالح بن فوزان الفوزان",
            dateLabel = "١٤٤١هـ",
            excerpt = "الإخلاص شرط في قبول العمل، فلا يقبل الله من العمل إلا ما كان " +
                "خالصًا لوجهه وصوابًا على سنة نبيه ﷺ…",
        ),
        ArticleUi(
            id = "khatar-al-ghuluww",
            title = "خطر الغلو في الدين",
            author = "عبد الرحمن بن ناصر البراك",
            dateLabel = "١٤٤٠هـ",
            excerpt = "الغلو مجاوزة الحد في التعظيم أو التحريم، وقد نهى النبي ﷺ عنه " +
                "وأخبر أنه سبب هلاك من كان قبلنا…",
            saved = true,
        ),
    ),
)
