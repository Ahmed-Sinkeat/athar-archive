package com.atharchive.feature.adhkar

import com.atharchive.ui.components.AtharTab

/**
 * الأذكار — a counted, repeated text, not a document.
 *
 * The unit here is a dhikr with a repetition count, and the thing a reader is doing is
 * working through a باب until every count is spent. That is why this screen carries a
 * progress bar and a per-item counter, and why nothing else in the app does.
 *
 * The أبواب are a directory over the same entries: الصباح and المساء earn tabs because
 * they are what people open daily; the rest live one tap away in [AdhkarBabsScreen].
 */

/** الصباح and المساء are tabs; المفضّلة is a view across every باب. */
enum class AdhkarTab(val key: String, val label: String) {
    Morning("morning", "الصباح"),
    Evening("evening", "المساء"),
    Saved("saved", "المفضّلة"),
    ;

    companion object {
        val tabs = entries.map { AtharTab(it.key, it.label) }
        fun fromKey(key: String): AdhkarTab = entries.first { it.key == key }
    }
}

data class AdhkarBabUi(
    val id: String,
    val label: String,
    /** The handful people open daily; they head the directory. */
    val frequent: Boolean = false,
)

data class DhikrUi(
    val id: String,
    val text: String,
    /** How many times it is said. The counter starts here and runs down. */
    val repeat: Int,
    /** التخريج — where it comes from. Absent for a few. */
    val source: String? = null,
    val saved: Boolean = false,
)

data class AdhkarUiState(
    val headline: String,
    val babs: List<AdhkarBabUi>,
    /** Keyed by [AdhkarBabUi.id]. */
    val entries: Map<String, List<DhikrUi>>,
) {
    fun of(babId: String): List<DhikrUi> = entries[babId].orEmpty()
    fun bab(babId: String): AdhkarBabUi? = babs.firstOrNull { it.id == babId }
    fun count(babId: String): Int = of(babId).size
    val saved: List<DhikrUi> get() = entries.values.flatten().filter { it.saved }
}

/** Arabic-Indic digits by code point. Escapes, never pasted glyphs. */
fun arabicDigits(value: Int): String =
    value.toString().map { ch -> if (ch in '0'..'9') '٠' + (ch - '0') else ch }.joinToString("")

private val MorningAdhkar = listOf(
    DhikrUi(
        id = "m-kursi",
        text = "اللَّهُ لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ، لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ، " +
            "لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ، مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ",
        repeat = 1,
        source = "آية الكرسي · من قالها حين يصبح أُجير من الجن حتى يمسي",
    ),
    DhikrUi(
        id = "m-ikhlas",
        text = "قُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ",
        repeat = 3,
        source = "سورة الإخلاص · رواه أبو داود والترمذي",
        saved = true,
    ),
    DhikrUi(
        id = "m-falaq",
        text = "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ، مِنْ شَرِّ مَا خَلَقَ، وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ، " +
            "وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ، وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ",
        repeat = 3,
        source = "سورة الفلق · رواه أبو داود والترمذي",
    ),
    DhikrUi(
        id = "m-nas",
        text = "قُلْ أَعُوذُ بِرَبِّ النَّاسِ، مَلِكِ النَّاسِ، إِلَهِ النَّاسِ، " +
            "مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ، الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ، مِنَ الْجِنَّةِ وَالنَّاسِ",
        repeat = 3,
        source = "سورة الناس · رواه أبو داود والترمذي",
    ),
    DhikrUi(
        id = "m-asbahna",
        text = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، " +
            "لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
        repeat = 1,
        source = "رواه مسلم",
    ),
    DhikrUi(
        id = "m-bika",
        text = "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ",
        repeat = 1,
        source = "رواه الترمذي",
    ),
    DhikrUi(
        id = "m-sayyid",
        text = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، " +
            "أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي، " +
            "فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
        repeat = 1,
        source = "سيد الاستغفار · رواه البخاري",
        saved = true,
    ),
    DhikrUi(
        id = "m-raditu",
        text = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا",
        repeat = 3,
        source = "رواه أبو داود والترمذي",
    ),
    DhikrUi(
        id = "m-hasbi",
        text = "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ، عَلَيْهِ تَوَكَّلْتُ، وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
        repeat = 7,
        source = "رواه أبو داود",
    ),
    DhikrUi(
        id = "m-subhan",
        text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        repeat = 100,
        source = "رواه البخاري ومسلم · حُطَّت خطاياه وإن كانت مثل زبد البحر",
    ),
)

private val EveningAdhkar = listOf(
    DhikrUi(
        id = "e-kursi",
        text = "اللَّهُ لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ، لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ، " +
            "لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ، مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ",
        repeat = 1,
        source = "آية الكرسي · من قالها حين يمسي أُجير من الجن حتى يصبح",
    ),
    DhikrUi(
        id = "e-ikhlas",
        text = "قُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ",
        repeat = 3,
        source = "سورة الإخلاص · رواه أبو داود والترمذي",
    ),
    DhikrUi(
        id = "e-amsayna",
        text = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، " +
            "لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
        repeat = 1,
        source = "رواه مسلم",
    ),
    DhikrUi(
        id = "e-bika",
        text = "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ",
        repeat = 1,
        source = "رواه الترمذي",
    ),
    DhikrUi(
        id = "e-audhu",
        text = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
        repeat = 3,
        source = "رواه مسلم · لم يضره شيء تلك الليلة",
    ),
    DhikrUi(
        id = "e-sayyid",
        text = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، " +
            "أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي، " +
            "فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
        repeat = 1,
        source = "سيد الاستغفار · رواه البخاري",
    ),
    DhikrUi(
        id = "e-subhan",
        text = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        repeat = 100,
        source = "رواه البخاري ومسلم",
    ),
)

private val SleepAdhkar = listOf(
    DhikrUi(
        id = "s-bismika",
        text = "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا",
        repeat = 1,
        source = "رواه البخاري",
    ),
    DhikrUi(
        id = "s-aslamtu",
        text = "اللَّهُمَّ أَسْلَمْتُ نَفْسِي إِلَيْكَ، وَفَوَّضْتُ أَمْرِي إِلَيْكَ، وَأَلْجَأْتُ ظَهْرِي إِلَيْكَ، " +
            "رَغْبَةً وَرَهْبَةً إِلَيْكَ، لَا مَلْجَأَ وَلَا مَنْجَا مِنْكَ إِلَّا إِلَيْكَ",
        repeat = 1,
        source = "رواه البخاري ومسلم",
    ),
    DhikrUi(
        id = "s-tasbih",
        text = "سُبْحَانَ اللَّهِ · وَالْحَمْدُ لِلَّهِ · وَاللَّهُ أَكْبَرُ",
        repeat = 33,
        source = "رواه البخاري ومسلم · ثلاثًا وثلاثين، والتكبير أربعًا وثلاثين",
    ),
)

private val WakingAdhkar = listOf(
    DhikrUi(
        id = "w-alhamd",
        text = "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ",
        repeat = 1,
        source = "رواه البخاري",
    ),
)

private val AfterPrayerAdhkar = listOf(
    DhikrUi(
        id = "p-astaghfir",
        text = "أَسْتَغْفِرُ اللَّهَ",
        repeat = 3,
        source = "رواه مسلم",
    ),
    DhikrUi(
        id = "p-allahumma",
        text = "اللَّهُمَّ أَنْتَ السَّلَامُ، وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ",
        repeat = 1,
        source = "رواه مسلم",
    ),
    DhikrUi(
        id = "p-ainni",
        text = "اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ",
        repeat = 1,
        source = "رواه أبو داود والنسائي",
    ),
)

/**
 * Fixture for layout review. The real أذكار arrive through the content pipeline; the
 * shape they arrive in is exactly [DhikrUi], so nothing here changes when they do.
 */
val AdhkarFixture = AdhkarUiState(
    headline = "أذكر الله تطمئن القلوب",
    babs = listOf(
        AdhkarBabUi("morning", "الصباح", frequent = true),
        AdhkarBabUi("evening", "المساء", frequent = true),
        AdhkarBabUi("sleep", "النوم", frequent = true),
        AdhkarBabUi("waking", "الاستيقاظ", frequent = true),
        AdhkarBabUi("after-prayer", "بعد الصلاة", frequent = true),
        AdhkarBabUi("prayer", "الصلاة"),
        AdhkarBabUi("food", "الطعام والشراب"),
        AdhkarBabUi("travel", "السفر"),
        AdhkarBabUi("illness", "المرض والألم"),
        AdhkarBabUi("grief", "الكرب والحزن"),
        AdhkarBabUi("fear", "الخوف والقلق"),
        AdhkarBabUi("home", "المنزل"),
        AdhkarBabUi("mosque", "دخول المسجد والخروج منه"),
        AdhkarBabUi("ruqyah", "الرقية"),
        AdhkarBabUi("rain", "المطر والريح"),
    ),
    entries = mapOf(
        "morning" to MorningAdhkar,
        "evening" to EveningAdhkar,
        "sleep" to SleepAdhkar,
        "waking" to WakingAdhkar,
        "after-prayer" to AfterPrayerAdhkar,
    ),
)
