package com.atharchive.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryLookupTest {
    private val k = DictionaryLookup::key
    private val index = DictionaryLookup.Index(
        sources = listOf("mukhtar-al-sihah" to "مختار الصحاح"),
        roots = setOf("علم", "كتب", "قول", "اجر", "دحرج").map(k).toSet(),
        forms = mapOf(k("الأجر") to listOf(k("اجر"))),
    )

    @Test fun listedFormWinsAfterClitics() = assertEquals(listOf(k("اجر")), DictionaryLookup.roots("وَبِالأَجْرِ", index))

    @Test fun augmentLettersAreTrimmedToTheRoot() {
        assertEquals(listOf(k("علم")), DictionaryLookup.roots("المعلومات", index))
        assertEquals(listOf(k("كتب")), DictionaryLookup.roots("يكتبون", index))
        assertEquals(listOf(k("دحرج")), DictionaryLookup.roots("تدحرج", index))
    }

    @Test fun hollowRootFromTwoLetters() = assertEquals(listOf(k("قول")), DictionaryLookup.roots("قل", index))

    @Test fun stopwordsAndUnknownsGiveNothing() {
        assertTrue(DictionaryLookup.roots("على", index).isEmpty())
        assertTrue(DictionaryLookup.roots("زخرف", index).isEmpty())
    }

    @Test fun aStrayHeadingLosesToTheCommonRoot() {
        // «تبك» is a real heading in one lexicon, but «تبكي» is بكي with the present-tense ت.
        val lex = DictionaryLookup.Index(
            sources = emptyList(),
            roots = setOf("تبك", "بكي", "قول", "وقي", "قوي").map(k).toSet(),
            forms = mapOf("تبك" to listOf("تبك"), "بكي" to listOf("بكي"), "يبكي" to listOf("بكي"), "بكاء" to listOf("بكي"),
                "القول" to listOf("قول"), "قيل" to listOf("قول"), "التقوي" to listOf("وقي"), "وقايه" to listOf("وقي"))
                .map { (f, r) -> k(f) to r.map(k) }.toMap(),
        )
        assertEquals(k("بكي"), DictionaryLookup.roots("تَبْكِي", lex).first())
        assertEquals(k("قول"), DictionaryLookup.roots("قال", lex).first())
        assertTrue(k("وقي") in DictionaryLookup.roots("المتقين", lex))
    }

    @Test fun fileNameIsHexOfTheRoot() = assertEquals("063906440645", DictionaryLookup.fileName("علم"))

    @Test fun snippetStartsAtTheClauseNamingTheWord() {
        val text = "(علم) العِلْم نقيض الجهل. والمَعْلَم الأثر يُستدل به على الطريق، وجمعه المعالم."
        assertTrue(DictionaryLookup.snippet(text, "المعلم").startsWith("… والمَعْلَم"))
        assertEquals(text, DictionaryLookup.snippet(text, "زخرف"))
    }
}
