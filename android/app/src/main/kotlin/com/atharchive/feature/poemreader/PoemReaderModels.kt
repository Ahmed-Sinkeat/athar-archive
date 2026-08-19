package com.atharchive.feature.poemreader

/**
 * A poem being read, not a book being paged.
 *
 * Three things the content pipeline does not carry yet, and how they are handled:
 *  - **word meanings**: `src/content/term` holds a single placeholder, so [glossary] is a
 *    stub. Only words present in it are tappable; everything else is inert rather than
 *    opening an empty popover. Swap [PoemReaderUiState.meaningOf] for the real lookup
 *    when the معجم exists and nothing else changes.
 *  - **commentary**: this one is real — `annotation` entries are keyed per verse anchor,
 *    which is exactly [VerseUi.commentary].
 *  - **audio timing**: `poem-timing` is `{v, t}`, verse-level. [PoemAudioUi.cues] mirrors
 *    that. There are no word timestamps, so no word is ever highlighted.
 */

/** The side panel's two modes. Explanation and search share one surface. */
internal enum class PanelMode(val label: String) { Sharh("الشرح"), Search("البحث") }

data class VerseUi(
    val id: String,
    val number: Int,
    /** صدر — the opening hemistich. */
    val sadr: String,
    /** عجز — the closing hemistich. */
    val ajz: String,
    /** Absent for most verses; its presence is what draws the margin indicator. */
    val commentary: List<CommentaryUi> = emptyList(),
)

/** One commentator's explanation. Several may exist for the same verse. */
data class CommentaryUi(
    val sourceLabel: String,
    val text: String,
)

data class PoemAudioUi(
    val id: String,
    /** الاسم */
    val title: String,
    /** المصنف */
    val collection: String,
    /** المنشد */
    val reciter: String,
    val durationLabel: String,
    val durationSeconds: Int,
    /**
     * Verse-level cues: seconds at which each verse index begins. Empty for most
     * recordings — following is a property of the recording, not of the poem.
     */
    val cues: List<VerseCue> = emptyList(),
)

/** `{v, t}` from `src/content/poem-timing`, unchanged. */
data class VerseCue(val verseNumber: Int, val atSeconds: Int)

data class PoemReaderUiState(
    val title: String,
    val poet: String,
    val verses: List<VerseUi>,
    val recordings: List<PoemAudioUi>,
    /** Keyed by word with the harakat removed; see [meaningOf]. */
    val glossary: Map<String, String> = emptyMap(),
) {
    /** null → the word is not tappable. No popover ever opens empty. */
    fun meaningOf(word: String): String? = glossary[stripHarakat(word)]
}

/**
 * Drops harakat and tatweel so a vocalised word matches an unvocalised glossary key.
 * Written as code points on purpose: pasting Arabic character ranges into source
 * silently reorders them, and the resulting class matches the wrong letters.
 */
fun stripHarakat(word: String): String = word.filter { ch ->
    val c = ch.code
    val harakat = c in 0x064B..0x0652 || c == 0x0670
    val tatweel = c == 0x0640
    val punctuation = ch == '،' || ch == '.' || ch == ':' || ch == '؟' || ch == '!'
    !harakat && !tatweel && !punctuation
}

/**
 * القصيدة الحائية — ابن أبي داود السجستاني. Fixture text for layout review; the real
 * verses arrive with the content pipeline.
 */
val PoemReaderFixture = PoemReaderUiState(
    title = "القصيدة الحائية",
    poet = "ابن أبي داود السجستاني",
    verses = listOf(
        VerseUi(
            id = "v1",
            number = 1,
            sadr = "تَمَسَّكْ بِحَبْلِ اللهِ وَاتَّبِعِ الهُدَى",
            ajz = "وَلَا تَكُ بِدْعِيًّا لَعَلَّكَ تُفْلِحُ",
        ),
        VerseUi(
            id = "v2",
            number = 2,
            sadr = "وَدِنْ بِكِتَابِ اللهِ وَالسُّنَنِ الَّتِي",
            ajz = "أَتَتْ عَنْ رَسُولِ اللهِ تَنْجُو وَتَرْبَحُ",
            commentary = listOf(
                CommentaryUi(
                    sourceLabel = "شرح مختصر",
                    text = "هذا البيت يأمر بالاقتراب من كتاب الله تعالى وسنة نبيه ﷺ، " +
                        "والاهتداء بهما في كل قول وفعل، فإن الخير والنجاة في الاتباع " +
                        "لا في الابتداع، ومن اتبع الرسول صلى الله عليه وسلم ربح الدنيا والآخرة.",
                ),
                CommentaryUi(
                    sourceLabel = "حاشية",
                    text = "«ودِن» أي اجعل دينك وعملك على كتاب الله والسنة، " +
                        "و«تنجو وتربح» جواب الأمر: النجاة من النار والربح بالجنة.",
                ),
            ),
        ),
        VerseUi(
            id = "v3",
            number = 3,
            sadr = "وَقُلْ غَيْرُ مَخْلُوقٍ كَلَامُ مَلِيكِنَا",
            ajz = "بِذَلِكَ دَانَ الأَتْقِيَاءُ وَأَفْصَحُوا",
            commentary = listOf(
                CommentaryUi(
                    sourceLabel = "شرح مختصر",
                    text = "أي أن القرآن كلام الله غير مخلوق، منه بدأ وإليه يعود، " +
                        "وعلى هذا مضى أهل السنة وأفصحوا به ولم يجمجموا.",
                ),
            ),
        ),
        VerseUi(
            id = "v4",
            number = 4,
            sadr = "وَلَا تَكُ فِي القُرْآنِ بِالوَقْفِ قَائِلًا",
            ajz = "كَمَا قَالَ أَتْبَاعٌ لِجَهْمٍ وَأَسْجَحُوا",
        ),
        VerseUi(
            id = "v5",
            number = 5,
            sadr = "وَقُلْ يَتَجَلَّى اللهُ لِلْخَلْقِ جَهْرَةً",
            ajz = "كَمَا الْبَدْرُ لَا يَخْفَى وَرَبُّكَ أَوْضَحُ",
            commentary = listOf(
                CommentaryUi(
                    sourceLabel = "شرح مختصر",
                    text = "إثبات رؤية المؤمنين لربهم يوم القيامة عيانًا جهرة، " +
                        "وهو مذهب أهل السنة، والتشبيه هنا في وضوح الرؤية لا في المرئي.",
                ),
            ),
        ),
        VerseUi(
            id = "v6",
            number = 6,
            sadr = "وَلَيْسَ بِمَوْلُودٍ وَلَيْسَ بِوَالِدٍ",
            ajz = "وَلَيْسَ لَهُ شِبْهٌ تَعَالَى الْمُسَبَّحُ",
        ),
    ),
    recordings = listOf(
        PoemAudioUi(
            id = "r1",
            title = "إنشاد الحائية",
            collection = "المنظومات",
            reciter = "أبو عبد الملك",
            durationLabel = "٠٦:٢٠",
            durationSeconds = 380,
            // Spaced for review: a verse advances every ~10s so the follow can be seen
            // without waiting a minute. Real cues come from src/content/poem-timing.
            cues = listOf(
                VerseCue(1, 2), VerseCue(2, 12), VerseCue(3, 22),
                VerseCue(4, 32), VerseCue(5, 42), VerseCue(6, 52),
            ),
        ),
        PoemAudioUi(
            id = "r2",
            title = "إنشاد الحائية",
            collection = "المنظومات",
            reciter = "أحمد النفيس",
            durationLabel = "٠٧:٠٤",
            durationSeconds = 424,
        ),
        PoemAudioUi(
            id = "r3",
            title = "شرح صوتي للقصيدة",
            collection = "الشروح",
            reciter = "عبد العزيز الصيني",
            durationLabel = "٤٢:١٠",
            durationSeconds = 2530,
        ),
    ),
    // Stub معجم: only these words open a popover. Keys carry no harakat.
    glossary = mapOf(
        "بحبل" to "الحبل: العهد والميثاق، وقيل: هو القرآن؛ والمراد التمسك بدين الله.",
        "الهدى" to "البيان والدلالة على الحق، وضده الضلال.",
        "بدعيا" to "منسوبًا إلى البدعة، أي صاحب بدعة مخالفًا للسنة.",
        "تفلح" to "تفوز وتظفر بالمطلوب، والفلاح: الفوز والبقاء في النعيم.",
        "ودن" to "من الدِّين، أي اجعل دينك وعملك على كتاب الله.",
        "السنن" to "جمع سُنّة، وهي الطريقة المتبعة عن النبي ﷺ.",
        "تنجو" to "تخلص وتسلم، والنجاة: الخلاص من المكروه.",
        "تربح" to "تكسب، والربح هنا: الفوز بالجنة.",
        "مليكنا" to "المَلِيك: الملك، وهو من أسماء الله تعالى.",
        "دان" to "اعتقد ودان به، أي جعله دينًا.",
        "وأفصحوا" to "أبانوا وصرّحوا ولم يجمجموا.",
        "أسجحوا" to "من الإسجاح، وأصله السهولة؛ والمراد هنا أنهم مالوا وجاروا.",
        "جهرة" to "علانية عيانًا، لا سترة دونها.",
        "البدر" to "القمر ليلة تمامه، وسُمي بذلك لمبادرته الشمس بالطلوع.",
        "المسبح" to "المنزَّه عن كل نقص، من التسبيح وهو التنزيه.",
    ),
)
