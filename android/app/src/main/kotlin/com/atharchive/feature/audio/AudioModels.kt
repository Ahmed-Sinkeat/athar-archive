package com.atharchive.feature.audio

import com.atharchive.ui.components.AtharTab

/**
 * Shaped by what `src/content/audio` actually carries (measured 18 Aug 2026, 971 files):
 * title, `source_type`, `source_id`, `duration`, `size_bytes`, `format`, `url`.
 *
 * There is **no speaker field** — only 15 of 971 titles embed a name — and **no series
 * field**; `source_id` points at the parent work, which is the nearest thing to a series.
 * [speaker] is therefore nullable and the row simply omits the line until the content
 * pipeline starts emitting it. Nothing here invents one.
 *
 * `source_type` splits المسائل 854 · الكتب 103 · الشعر 14, which is exactly the tab set.
 */

enum class AudioTab(val key: String, val label: String) {
    All("all", "الكل"),
    Books("books", "الكتب"),
    Poetry("poetry", "الشعر"),
    Issues("issues", "المسائل"),
    MyList("mylist", "قائمتي"),
    ;

    companion object {
        val tabs: List<AtharTab> = entries.map { AtharTab(it.key, it.label) }
        fun fromKey(key: String): AudioTab = entries.first { it.key == key }
    }
}

enum class AudioSourceKind(val key: String, val label: String) {
    Book("book", "كتاب"),
    Question("question", "مسألة"),
    Poem("poem", "قصيدة"),
}

data class AudioUi(
    val id: String,
    val title: String,
    /** The parent work this recording is attached to. */
    val sourceTitle: String,
    val sourceKind: AudioSourceKind,
    /** Formatted as authored, e.g. "٢٠:٣١". */
    val durationLabel: String,
    val sizeLabel: String,
    /** Absent for all but a handful of recordings today; the row hides the line when null. */
    val speaker: String? = null,
    val downloaded: Boolean = false,
    val saved: Boolean = false,
)

data class NowPlayingUi(
    val audio: AudioUi,
    val positionLabel: String,
    val durationLabel: String,
    val progress: Float,
    val playing: Boolean,
)

data class AudioUiState(
    val countLabel: String,
    val continueListening: NowPlayingUi?,
    val recordings: List<AudioUi>,
)

val AudioFixture = AudioUiState(
    countLabel = "٩٧١ تسجيلًا",
    continueListening = NowPlayingUi(
        audio = AudioUi(
            id = "sharh-kitab-al-tawhid-5",
            title = "الدرس الخامس: إثبات صفات الله تعالى",
            sourceTitle = "شرح كتاب التوحيد",
            sourceKind = AudioSourceKind.Book,
            durationLabel = "٤٢:١٨",
            sizeLabel = "18.6 MB",
            speaker = "محمد بن صالح العثيمين",
            downloaded = true,
        ),
        positionLabel = "١٤:٣٢",
        durationLabel = "٤٢:١٨",
        progress = 0.42f,
        playing = false,
    ),
    recordings = listOf(
        AudioUi(
            id = "al-aqida-al-wasitiyya-1",
            title = "الدرس الأول: مقدمة في العقيدة",
            sourceTitle = "العقيدة الواسطية",
            sourceKind = AudioSourceKind.Book,
            durationLabel = "٤٢:١٨",
            sizeLabel = "18.6 MB",
            saved = true,
        ),
        AudioUi(
            id = "sharh-nuniyyat-ibn-al-qayyim",
            title = "شرح نونية ابن القيم",
            sourceTitle = "نونية ابن القيم",
            sourceKind = AudioSourceKind.Poem,
            durationLabel = "٣٥:١٠",
            sizeLabel = "16.2 MB",
            downloaded = true,
        ),
        AudioUi(
            id = "hukm-al-tawassul-audio",
            title = "حكم التوسل بذوات الصالحين",
            sourceTitle = "مسألة في التوسل",
            sourceKind = AudioSourceKind.Question,
            durationLabel = "٠٨:٤٧",
            sizeLabel = "3.1 MB",
        ),
        AudioUi(
            id = "madarij-al-salikin-1",
            title = "مدارج السالكين بين منازل إياك نعبد",
            sourceTitle = "مدارج السالكين",
            sourceKind = AudioSourceKind.Book,
            durationLabel = "٢٨:١٩",
            sizeLabel = "12.1 MB",
            downloaded = true,
            saved = true,
        ),
        AudioUi(
            id = "al-ajurrumiyyah-audio",
            title = "المقدمة الآجرومية",
            sourceTitle = "المقدمة الآجرومية",
            sourceKind = AudioSourceKind.Book,
            durationLabel = "٢٠:٣١",
            sizeLabel = "4.9 MB",
            speaker = "عبد العزيز الصيني",
        ),
        AudioUi(
            id = "hukm-raf-al-yadayn",
            title = "حكم رفع اليدين بعد الصلاة",
            sourceTitle = "مسألة في الأذكار",
            sourceKind = AudioSourceKind.Question,
            durationLabel = "٠٥:١٢",
            sizeLabel = "1.9 MB",
        ),
    ),
)
