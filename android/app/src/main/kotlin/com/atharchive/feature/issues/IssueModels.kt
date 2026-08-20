package com.atharchive.feature.issues

import com.atharchive.ui.components.AtharTab

/**
 * المسائل — a question and the answer it was given.
 *
 * The question is the title: it is what a reader scans for and what they remember. The
 * answering scholar is the second fact, because the same مسألة gets different answers
 * from different people and knowing whose you are reading is not optional.
 */

enum class IssuesTab(val key: String, val label: String) {
    All("all", "الكل"),
    Downloaded("downloaded", "المحمّلة"),
    MyList("mylist", "قائمتي"),
    ;

    companion object {
        val tabs = entries.map { AtharTab(it.key, it.label) }
        fun fromKey(key: String): IssuesTab = entries.first { it.key == key }
    }
}

enum class IssueSort(val label: String) {
    Newest("الأحدث"),
    Question("المسألة"),
    Scholar("المجيب"),
}

data class IssueUi(
    val id: String,
    /** The مسألة itself, as asked. */
    val question: String,
    /** The opening of the جواب — enough to know whether to open it. */
    val answerExcerpt: String,
    val scholar: String,
    val topic: String,
    val sizeLabel: String,
    val downloaded: Boolean = false,
    val saved: Boolean = false,
)

data class IssuesUiState(
    val countLabel: String,
    val issues: List<IssueUi>,
)

val IssuesFixture = IssuesUiState(
    countLabel = "٦٤١ مسألة",
    issues = listOf(
        IssueUi(
            id = "i1",
            question = "حكم التوسل بذوات الصالحين",
            answerExcerpt = "التوسل المشروع ثلاثة أنواع: التوسل بأسماء الله وصفاته، والتوسل " +
                "بالعمل الصالح، والتوسل بدعاء الرجل الصالح الحي الحاضر. وأما التوسل بذات " +
                "المخلوق أو جاهه فلم يرد به دليل يصح.",
            scholar = "محمد بن صالح العثيمين",
            topic = "العقيدة",
            sizeLabel = "0.3 MB",
            saved = true,
        ),
        IssueUi(
            id = "i2",
            question = "ما ضابط البدعة الإضافية؟",
            answerExcerpt = "البدعة الإضافية ما كان له أصل في الشرع من جهة، ولا أصل له من " +
                "جهة أخرى، كتخصيص وقت أو هيئة أو عدد لعبادة مشروعة في أصلها دون دليل يخصّها.",
            scholar = "عبد العزيز بن باز",
            topic = "العقيدة",
            sizeLabel = "0.2 MB",
            downloaded = true,
        ),
        IssueUi(
            id = "i3",
            question = "هل يجوز الجمع بين الصلاتين للمطر؟",
            answerExcerpt = "يجوز الجمع بين الظهر والعصر وبين المغرب والعشاء للمطر الذي " +
                "يبلّ الثياب وتلحق معه المشقة في الذهاب إلى المسجد، وهو مذهب جمهور أهل العلم.",
            scholar = "عبد الله بن عبد الرحمن الجبرين",
            topic = "الصلاة",
            sizeLabel = "0.2 MB",
        ),
        IssueUi(
            id = "i4",
            question = "حكم صيام يوم السبت تطوعًا",
            answerExcerpt = "حديث النهي عن صيام يوم السبت اختُلف في ثبوته، ومن صححه حمله " +
                "على إفراده بالصيام تعظيمًا له. فأما من صامه مع الجمعة أو وافق عادةً فلا بأس.",
            scholar = "محمد ناصر الدين الألباني",
            topic = "الصيام",
            sizeLabel = "0.4 MB",
            downloaded = true,
            saved = true,
        ),
        IssueUi(
            id = "i5",
            question = "هل تجب الزكاة في الحلي المُعدّ للاستعمال؟",
            answerExcerpt = "المسألة من مواضع الخلاف المشهورة بين أهل العلم؛ فذهب جماعة إلى " +
                "وجوب الزكاة فيه إذا بلغ النصاب، وذهب آخرون إلى أن ما أُعدّ للاستعمال لا زكاة فيه.",
            scholar = "عبد العزيز بن باز",
            topic = "الزكاة",
            sizeLabel = "0.3 MB",
        ),
        IssueUi(
            id = "i6",
            question = "ما حكم قراءة القرآن للحائض من المصحف؟",
            answerExcerpt = "جمهور أهل العلم على منع مسّ المصحف لغير الطاهر، وأما القراءة عن " +
                "ظهر قلب أو من وراء حائل فقد رخّص فيها جماعة من أهل العلم للحاجة.",
            scholar = "محمد بن صالح العثيمين",
            topic = "الطهارة",
            sizeLabel = "0.2 MB",
        ),
        IssueUi(
            id = "i7",
            question = "ضابط الاستطاعة في الحج",
            answerExcerpt = "الاستطاعة تكون بالبدن والمال معًا: صحة تحتمل السفر، وزاد وراحلة " +
                "فاضلان عن حاجته وحاجة من تلزمه نفقتهم إلى أن يرجع، وأمن الطريق.",
            scholar = "عبد الرحمن السعدي",
            topic = "الحج",
            sizeLabel = "0.3 MB",
        ),
        IssueUi(
            id = "i8",
            question = "هل يُشرع رفع اليدين في الدعاء بعد الصلاة المكتوبة؟",
            answerExcerpt = "لم يثبت عن النبي صلى الله عليه وسلم أنه كان يرفع يديه للدعاء " +
                "عقب المكتوبة، والمشروع بعدها الأذكار الواردة. وأما الدعاء المطلق فلا حرج فيه.",
            scholar = "عبد العزيز بن باز",
            topic = "الصلاة",
            sizeLabel = "0.2 MB",
        ),
    ),
)
