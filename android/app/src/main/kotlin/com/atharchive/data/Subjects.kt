package com.atharchive.data

import com.atharchive.ui.components.arabicDigits

/** «الأقسام»: the site's subjects (src/content/subject), short names, in display order. */
val SubjectOrder = listOf("علوم القرآن", "الحديث", "العقيدة", "الفقه", "اللغة", "التزكية", "السيرة والتاريخ")

// copied from src/content/topic/*.md `subject:`; a topic added there needs a line here
// (unknown topics, and «عام», belong to no section). Ship subject in the app catalog if this drifts.
private val topicSubject = mapOf(
    "تفسير القرآن" to "علوم القرآن", "علوم القرآن" to "علوم القرآن", "القراءات" to "علوم القرآن", "التجويد" to "علوم القرآن",
    "حديث عام" to "الحديث", "أحكام الحديث" to "الحديث", "فضائل" to "الحديث", "مصطلح الحديث" to "الحديث", "متون الحديث" to "الحديث",
    "العقيدة العامة" to "العقيدة", "الأسماء والصفات" to "العقيدة", "الفرق والردود" to "العقيدة", "الإمامة والصحابة" to "العقيدة",
    "الإيمان" to "العقيدة", "القضاء والقدر" to "العقيدة", "السمعيات" to "العقيدة", "السنة والبدعة" to "العقيدة",
    "الولاء والبراء" to "العقيدة", "التوحيد" to "العقيدة", "أصول البدع" to "العقيدة",
    "القواعد الفقهية" to "الفقه", "السياسة الشرعية" to "الفقه", "فقه عام" to "الفقه", "الفقه الحنفي" to "الفقه",
    "الفقه الحنبلي" to "الفقه", "الفقه المالكي" to "الفقه", "الفقه الشافعي" to "الفقه", "علم الفرائض" to "الفقه",
    "مقاصد الشريعة" to "الفقه", "أصول الفقه" to "الفقه", "أصول الفقه الحنفي" to "الفقه", "أصول الفقه الحنبلي" to "الفقه",
    "أصول الفقه المالكي" to "الفقه", "أصول الفقه الشافعي" to "الفقه",
    "الأدب" to "اللغة", "البلاغة" to "اللغة", "الإملاء" to "اللغة", "المفردات" to "اللغة", "النحو" to "اللغة", "الصرف" to "اللغة",
    "طلب العلم وآدابه" to "التزكية", "الرقاق والآداب والأذكار" to "التزكية", "تزكية النفس" to "التزكية",
    "السيرة النبوية" to "السيرة والتاريخ", "تراجم العلماء" to "السيرة والتاريخ",
)

fun subjectsOf(topics: Collection<String>): Set<String> = topics.mapNotNullTo(mutableSetOf()) { topicSubject[it] }

/** The topic titles that make up [subjects]: what a section filter sends to the search. */
fun topicsIn(subjects: Collection<String>): Set<String> = topicSubject.filterValues { it in subjects }.keys

/** «القرن ٨» for a death year; centuries 1–15 always listed so a filter shows the whole span. */
fun centuryOf(died: Int) = "القرن ${arabicDigits((died - 1) / 100 + 1)}"
val AllCenturies = (1..15).map { "القرن ${arabicDigits(it)}" }
