package com.atharchive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryBuilderTest {
    @Test
    fun returnsNoQueryForEmptyOrPunctuationOnlyInput() {
        assertNull(FtsQueryBuilder.build(""))
        assertNull(FtsQueryBuilder.build("  ( * : - )  "))
        assertNull(FtsQueryBuilder.build("\u200F\u200E"))
    }

    @Test
    fun normalizesAndPrefixesOnlyTheLastOpenToken() {
        assertEquals("\"احمد\" AND \"ايمان\"*", FtsQueryBuilder.build("أَحْمَد إيمان"))
    }

    @Test
    fun expandsTheDefiniteArticleWithoutChangingNormalization() {
        assertEquals("(\"الايمان\"* OR \"ايمان\"*)", FtsQueryBuilder.build("الإيمان"))
    }

    @Test
    fun keepsAClosedPhraseClosedAndUnprefixed() {
        assertEquals("\"طلب العلم\"", FtsQueryBuilder.build("\"طلب العلم\""))
    }

    @Test
    fun compactModeRendersPhraseCandidatesWithoutPositionSyntax() {
        assertEquals(
            "\"طلب\" AND \"العلم\"",
            FtsQueryBuilder.buildCompactCandidate("\"طلب العلم\""),
        )
        assertEquals(
            "((\"عبد\" AND \"الله\") OR \"عبدالله\")",
            FtsQueryBuilder.buildCompactCandidate("عبد الله"),
        )
    }

    @Test
    fun joinsCompoundNamesAsARecallAlternative() {
        assertEquals("((\"عبد\" \"الله\") OR \"عبدالله\")", FtsQueryBuilder.build("عبد الله"))
    }

    @Test
    fun adversarialInputsNeverLeakFtsOperators() {
        val inputs = listOf(
            "\"", "*", "(", ")", "^", ":", "-", "OR", "AND", "NOT", "NEAR",
            "\"عبارة غير مغلقة", "foo:bar OR baz*", "a".repeat(10_000),
            "نص\u200Dمختلط\u200C test", "(((\" OR * NEAR :)))",
        )

        for (input in inputs) {
            val query = FtsQueryBuilder.build(input) ?: continue
            assertFalse("raw colon leaked for $input", query.contains(':'))
            assertFalse("raw caret leaked for $input", query.contains('^'))
            assertFalse("raw paren imbalance for $input", query.count { it == '(' } != query.count { it == ')' })
            assertFalse("raw NOT leaked for $input", Regex("(^|\\s)NOT(\\s|$)").containsMatchIn(query))
            assertFalse("raw NEAR leaked for $input", Regex("(^|\\s)NEAR(\\s|$)").containsMatchIn(query))
            assertNotNull(query)
        }
    }
}
