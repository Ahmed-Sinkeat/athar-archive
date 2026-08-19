package com.atharchive.feature.reader

/**
 * Three location concepts live in the reader and must never be conflated:
 *
 * - **Reading position** — automatic, exactly one per book, written continuously.
 * - **Bookmark** — manual, many per book, a place the reader chose to return to.
 * - **Kunnasha excerpt** — a selected passage saved out of the book into the notebook.
 *
 * All three anchor to a [ReaderBlock] id rather than a page number, because pagination is
 * a property of the printed edition and typography settings, not of the text.
 */

sealed interface ReaderBlock {
    val id: String

    /** level 2 = kitab, level 3 = bab. */
    data class Heading(
        override val id: String,
        val level: Int,
        val text: String,
        val anchor: String,
    ) : ReaderBlock

    data class Paragraph(
        override val id: String,
        val text: String,
        val footnotes: List<String> = emptyList(),
    ) : ReaderBlock

    data class Quote(override val id: String, val text: String) : ReaderBlock

    /** [ajuz] is null when the source marks no caesura; the renderer centres it whole. */
    data class Verse(
        override val id: String,
        val sadr: String,
        val ajuz: String? = null,
    ) : ReaderBlock

    data class PageBreak(
        override val id: String,
        val page: Int,
        val volume: Int? = null,
    ) : ReaderBlock

    data class FootnoteBody(
        override val id: String,
        val marker: String,
        val text: String,
    ) : ReaderBlock
}

data class TocEntry(
    val anchor: String,
    val title: String,
    val level: Int,
    val blockIndex: Int,
)

data class ReaderBookmark(
    val id: String,
    val blockIndex: Int,
    val chapterTitle: String,
    val preview: String,
    val pageLabel: String?,
)

data class ReaderBenefit(
    val id: String,
    val blockIndex: Int,
    val text: String,
    val chapterTitle: String,
    val pageLabel: String?,
)

data class InBookHit(
    val id: String,
    val blockIndex: Int,
    val chapterTitle: String,
    val excerpt: String,
    val matchStart: Int,
    val matchEnd: Int,
    val pageLabel: String?,
)

// ---- reading appearance, independent of the app's light/dark theme ----

enum class ReaderPalette(val label: String) {
    White("أبيض"),
    Warm("دافئ"),
    Dark("داكن"),
}

enum class ReaderSpacing(val label: String, val multiplier: Float) {
    Tight("متقارب", 1.55f),
    Normal("عادي", 1.85f),
    Spacious("متباعد", 2.20f),
}

data class ReaderSettings(
    val fontSize: Int = 19,
    val spacing: ReaderSpacing = ReaderSpacing.Normal,
    val palette: ReaderPalette = ReaderPalette.Warm,
) {
    companion object {
        const val MIN_SIZE = 14
        const val MAX_SIZE = 30
    }
}

data class BookAudioUi(
    val id: String,
    val title: String,
    val durationLabel: String,
)

data class ReaderUiState(
    val bookTitle: String,
    val author: String,
    val blocks: List<ReaderBlock>,
    val toc: List<TocEntry>,
    val bookmarks: List<ReaderBookmark>,
    val benefits: List<ReaderBenefit>,
    /** The automatically restored position; the reader opens here. */
    val readingPositionIndex: Int = 0,
    /** Empty for most books; the bottom bar only shows an audio action when it is not. */
    val audio: List<BookAudioUi> = emptyList(),
)

private fun heading(id: String, level: Int, text: String, anchor: String) =
    ReaderBlock.Heading(id, level, text, anchor)

val ReaderFixture: ReaderUiState = run {
    val blocks = mutableListOf<ReaderBlock>()
    fun p(id: String, text: String, footnotes: List<String> = emptyList()) {
        blocks += ReaderBlock.Paragraph(id, text, footnotes)
    }

    blocks += heading("h-muq", 2, "المقدمة", "muqaddimah")
    p("p1", "قالَ العَلّامةُ حُجّةُ الإسلامِ أبو جعفرٍ الوَرّاقُ الطَّحاويُّ بِمِصرَ رَحِمَهُ اللهُ: هذا ذِكرُ بَيانِ عَقيدةِ أهلِ السُّنّةِ والجَماعةِ على مَذهَبِ فُقَهاءِ المِلّةِ.")
    p("p2", "نَقولُ في تَوحيدِ اللهِ مُعتَقِدينَ بِتَوفيقِ اللهِ: إنَّ اللهَ واحِدٌ لا شَريكَ لَهُ، ولا شَيءَ مِثلُهُ، ولا شَيءَ يُعجِزُهُ، ولا إلهَ غَيرُهُ.", listOf("fn1"))
    blocks += ReaderBlock.PageBreak("pb-131", 131, 1)
    p("p3", "قَديمٌ بِلا ابتِداءٍ، دائِمٌ بِلا انتِهاءٍ، لا يَفنى ولا يَبيدُ، ولا يَكونُ إلّا ما يُريدُ.")

    blocks += heading("h-k1", 2, "كتاب التوحيد", "kitab-tawhid")
    blocks += heading("h-b1", 3, "باب صفات الله تعالى", "bab-sifat")
    p("p4", "لا تَبلُغُهُ الأوهامُ، ولا تُدرِكُهُ الأفهامُ، ولا يُشبِهُ الأنامَ. حَيٌّ لا يَموتُ، قَيّومٌ لا يَنامُ.")
    blocks += ReaderBlock.Quote("q1", "لَيسَ كَمِثلِهِ شَيءٌ وَهوَ السَّميعُ البَصيرُ.")
    p("p5", "خالِقٌ بِلا حاجةٍ، رازِقٌ بِلا مَؤونةٍ، مُميتٌ بِلا مَخافةٍ، باعِثٌ بِلا مَشَقّةٍ.")
    blocks += ReaderBlock.PageBreak("pb-139", 139, 1)
    p("p6", "وَالإخلاصُ أصلُ العَمَلِ كُلِّهِ، فَلا يَقبَلُ اللهُ مِنَ العَمَلِ إلّا ما كانَ خالِصًا لِوَجهِهِ، وَما كانَ لِلهِ دامَ وَاتَّصَلَ.")

    blocks += heading("h-b2", 3, "باب الإيمان", "bab-iman")
    p("p7", "وَالإيمانُ قَولٌ وَعَمَلٌ، يَزيدُ بِالطّاعةِ وَيَنقُصُ بِالمَعصيةِ، وَأهلُ الكَبائِرِ في النّارِ لا يُخَلَّدونَ.")
    blocks += ReaderBlock.Verse(
        "v1",
        "وَمَن يَتَّقِ اللهَ يَجعَل لَهُ مَخرَجًا",
        "وَيَرزُقهُ مِن حَيثُ لا يَحتَسِبُ",
    )
    p("p8", "وَالإخلاصُ سِرٌّ بَينَ العَبدِ وَرَبِّهِ، لا يَعلَمُهُ مَلَكٌ فَيَكتُبَهُ، وَلا شَيطانٌ فَيُفسِدَهُ.")

    blocks += heading("h-k2", 2, "كتاب القدر", "kitab-qadar")
    blocks += heading("h-b3", 3, "باب الإيمان بالقدر", "bab-qadar")
    p("p9", "وَأصلُ القَدَرِ سِرُّ اللهِ تَعالى في خَلقِهِ، لَم يَطَّلِع على ذلِكَ مَلَكٌ مُقَرَّبٌ وَلا نَبيٌّ مُرسَلٌ.")
    p("p10", "وَالتَّعَمُّقُ وَالنَّظَرُ في ذلِكَ ذَريعةُ الخِذلانِ، وَسُلَّمُ الحِرمانِ، وَدَرَجةُ الطُّغيانِ.")
    blocks += ReaderBlock.PageBreak("pb-203", 203, 1)
    p("p11", "فَالحَذَرَ كُلَّ الحَذَرِ مِن ذلِكَ نَظَرًا وَفِكرًا وَوَسوَسةً، فَإنَّ اللهَ طَوى عِلمَ القَدَرِ عَن أنامِهِ.")
    blocks += ReaderBlock.FootnoteBody("fn1", "١", "أخرجه البخاري في صحيحه، كتاب التوحيد.")

    val toc = blocks.mapIndexedNotNull { index, b ->
        (b as? ReaderBlock.Heading)?.let { TocEntry(it.anchor, it.text, it.level, index) }
    }

    ReaderUiState(
        bookTitle = "العقيدة الطحاوية",
        author = "أبو جعفر الطحاوي",
        blocks = blocks,
        toc = toc,
        readingPositionIndex = 8,
        audio = listOf(
            BookAudioUi("a1", "الدرس الأول: مقدمة في العقيدة", "٤٢:١٨"),
            BookAudioUi("a2", "الدرس الثاني: توحيد الربوبية", "٣٨:٠٤"),
            BookAudioUi("a3", "الدرس الثالث: الإيمان بالقدر", "٥١:٢٧"),
        ),
        bookmarks = listOf(
            ReaderBookmark("bm1", 4, "كتاب التوحيد", "لا تَبلُغُهُ الأوهامُ، ولا تُدرِكُهُ الأفهامُ", "ص ١٣١"),
            ReaderBookmark("bm2", 13, "كتاب القدر", "وَأصلُ القَدَرِ سِرُّ اللهِ تَعالى في خَلقِهِ", "ص ٢٠٣"),
        ),
        benefits = listOf(
            ReaderBenefit(
                "bn1", 9,
                "وَالإخلاصُ أصلُ العَمَلِ كُلِّهِ، فَلا يَقبَلُ اللهُ مِنَ العَمَلِ إلّا ما كانَ خالِصًا لِوَجهِهِ",
                "باب صفات الله تعالى", "ص ١٣٩",
            ),
            ReaderBenefit(
                "bn2", 12,
                "وَالإخلاصُ سِرٌّ بَينَ العَبدِ وَرَبِّهِ، لا يَعلَمُهُ مَلَكٌ فَيَكتُبَهُ",
                "باب الإيمان", null,
            ),
        ),
    )
}
