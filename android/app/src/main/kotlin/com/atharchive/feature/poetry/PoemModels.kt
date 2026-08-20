package com.atharchive.feature.poetry

import com.atharchive.ui.components.AtharTab

/**
 * Fixtures are shaped by what `src/content/poem` actually carries (measured 18 Aug 2026):
 * title, person and topics on all 108 poems, `description` holding the مطلع on 102, and
 * `work_type` (قصيدة) on 100. There is no meter or rhyme metadata, so the UI never shows any.
 */

enum class PoetryTab(val key: String, val label: String) {
    All("all", "الكل"),
    Downloaded("downloaded", "المحمّلة"),
    MyList("mylist", "قائمتي"),
    ;

    companion object {
        val tabs: List<AtharTab> = entries.map { AtharTab(it.key, it.label) }
        fun fromKey(key: String): PoetryTab = entries.first { it.key == key }
    }
}

enum class PoemSort(val label: String) {
    Newest("الأحدث"),
    Title("العنوان أ–ي"),
    Poet("الشاعر"),
}

data class PoemUi(
    val id: String,
    val title: String,
    val poet: String,
    /**
     * The opening verses, vocalised exactly as authored, each one a full line
     * (sadr … ajuz). Never fabricated: 59 of the 108 poems mark the caesura, and the
     * rest supply the line unsplit. One or two entries — the list, not the row, decides
     * how many it shows.
     */
    val openingVerses: List<String>,
    val topic: String,
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val saved: Boolean = false,
    val verseCountLabel: String? = null,
    val sizeLabel: String = "—",
)

data class PoetryUiState(
    val countLabel: String,
    val poems: List<PoemUi>,
)

val PoetryFixture = PoetryUiState(
    countLabel = "١٠٨ قصائد",
    poems = listOf(
        PoemUi(
            id = "tadhakkara-bad-ma-shattat",
            sizeLabel = "0.4 MB",
            title = "تذكر بعدما شطت نجودا",
            poet = "عبد الله بن رواحة",
            openingVerses = listOf(
                "تَذَّكَرَ بَعدَما شَطَّت نَجودا … وَكانَت تَيَّمَت قَلبي وَليدا",
                "كَذي داءٍ يُرى في الناسِ يَمشي … وَيَكتُمُ داءَهُ زَمَناً عَميدا",
            ),
            topic = "الأدب",
            saved = true,
            verseCountLabel = "١٤ بيتًا",
        ),
        PoemUi(
            id = "banat-suad",
            sizeLabel = "1.2 MB",
            title = "بانت سعاد فقلبي اليوم متبول",
            poet = "كعب بن زهير",
            openingVerses = listOf(
                "بانَت سُعادُ فَقَلبي اليَومَ مَتبولُ … مُتَيَّمٌ إِثرَها لَم يُفدَ مَكبولُ",
                "وَما سُعادُ غَداةَ البَينِ إِذ رَحَلوا … إِلّا أَغَنُّ غَضيضُ الطَرفِ مَكحولُ",
            ),
            topic = "الأدب",
            downloaded = true,
            verseCountLabel = "٥٨ بيتًا",
        ),
        PoemUi(
            id = "lamiyyat-ibn-al-wardi",
            sizeLabel = "1.6 MB",
            title = "لامية ابن الوردي",
            poet = "زين الدين ابن الوردي",
            openingVerses = listOf(
                "اِعتَزِل ذِكرَ الأَغاني وَالغَزَل … وَقُلِ الفَصلَ وَجانِب مَن هَزَل",
                "وَدَعِ الذِكرى لِأَيّامِ الصِبا … فَلِأَيّامِ الصِبا نَجمٌ أَفَل",
            ),
            topic = "الآداب",
            downloaded = true,
            saved = true,
            verseCountLabel = "٧٧ بيتًا",
        ),
        PoemUi(
            id = "qifa-nabki",
            sizeLabel = "1.8 MB",
            title = "قفا نبك من ذكرى حبيب ومنزل",
            poet = "امرؤ القيس",
            openingVerses = listOf(
                "قِفا نَبكِ مِن ذِكرى حَبيبٍ وَمَنزِلِ … بِسِقطِ اللِوى بَينَ الدَخولِ فَحَومَلِ",
                "فَتوضِحَ فَالمِقراةِ لَم يَعفُ رَسمُها … لِما نَسَجَتها مِن جَنوبٍ وَشَمأَلِ",
            ),
            topic = "الأدب",
            verseCountLabel = "٨٢ بيتًا",
        ),
        PoemUi(
            id = "mimiyyat-al-hafiz",
            sizeLabel = "0.7 MB",
            title = "ميمية الحافظ ابن القيم",
            poet = "ابن قيم الجوزية",
            openingVerses = listOf(
                "يا طالِبَ العِلمِ لا تَبغي بِهِ بَدَلًا … فَالعِلمُ أَشرَفُ ما تَسعى لَهُ هِمَمُ",
                "وَاِعمَل بِهِ وَاِبتَغِ الإِخلاصَ مُحتَسِبًا … فَالعِلمُ بِالعَملِ الخالِصِ يُختَتَمُ",
            ),
            topic = "العلم وآدابه",
            verseCountLabel = "٣١ بيتًا",
        ),
        PoemUi(
            id = "nuniyyat-al-qahtani",
            sizeLabel = "4.3 MB",
            title = "نونية القحطاني",
            poet = "محمد بن صالح القحطاني",
            openingVerses = listOf(
                "بِسمِ الإِلَهِ وَبِالهُدى نَستَفتِحُ … وَبِذِكرِهِ في كُلِّ حينٍ نَفرَحُ",
                "وَبِهِ نَعوذُ مِنَ الضَلالِ وَأَهلِهِ … وَإِلَيهِ في كُلِّ الأُمورِ نُصَرِّحُ",
            ),
            topic = "العقيدة",
            saved = true,
            verseCountLabel = "٥١٠ أبيات",
        ),
    ),
)
