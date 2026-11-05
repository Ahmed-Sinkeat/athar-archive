package com.atharchive.feature.reader

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atharchive.ui.theme.AtharEditorialFontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android draws a justified line spread to the full width but reports its glyph positions at
 * natural spacing (measured ~65 px off at the end of an Arabic line on a Redmi 12), so a press on
 * a line's last word took the one before. [boxAt] and [offsetAt] correct for that; these pin it.
 */
@RunWith(AndroidJUnit4::class)
class JustifiedLineHitTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun layout(indent: Boolean): TextLayoutResult {
        var result: TextLayoutResult? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    Paragraph,
                    Modifier.width(300.dp),
                    style = TextStyle(
                        fontFamily = AtharEditorialFontFamily,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Justify,
                        textDirection = TextDirection.Rtl,
                        textIndent = if (indent) TextIndent(firstLine = 24.sp) else null,
                    ),
                    onTextLayout = { result = it },
                )
            }
        }
        composeRule.waitForIdle()
        return requireNotNull(result).also { assertTrue("paragraph must wrap", it.lineCount >= 3) }
    }

    /** Every line but the paragraph's last, the ones Android stretches. */
    private fun TextLayoutResult.justifiedLines() = 0 until lineCount - 1

    // setContent runs once per test, so each case is its own test.
    @Test fun aJustifiedLineReachesBothEdges() = reachesBothEdges(indent = false)
    @Test fun anIndentedJustifiedLineReachesBothEdges() = reachesBothEdges(indent = true)
    @Test fun aPressOnAWordLandsInThatWord() = pressLandsInWord(indent = false)
    @Test fun aPressOnAWordOfAnIndentedParagraphLandsInThatWord() = pressLandsInWord(indent = true)

    private fun reachesBothEdges(indent: Boolean) {
        val layout = layout(indent)
        val indentPx = if (indent) with(layout.layoutInput.density) { 24.sp.toPx() } else 0f
        for (line in layout.justifiedLines()) {
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line, visibleEnd = true)
            val right = layout.size.width - if (line == 0) indentPx else 0f
            // Right to left: the line's first letter sits at the right edge, its last at the left.
            assertEquals("indent=$indent line $line starts at the right edge", right, layout.boxAt(start).right, EdgeTolerancePx)
            assertEquals("indent=$indent line $line ends at the left edge", 0f, layout.boxAt(end - 1).left, EdgeTolerancePx)
        }
    }

    private fun pressLandsInWord(indent: Boolean) {
        val layout = layout(indent)
        val text = layout.layoutInput.text.text
        for (line in layout.justifiedLines()) {
            val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
            for (word in words(text, layout.getLineStart(line), layout.getLineEnd(line, visibleEnd = true))) {
                // Where the word is drawn: the span of its letters' boxes (the edge test pins those).
                val left = word.minOf { layout.boxAt(it).left }
                val right = word.maxOf { layout.boxAt(it).right }
                val hit = layout.offsetAt(Offset((left + right) / 2, y))
                assertTrue(
                    "indent=$indent line $line: a press on «${text.substring(word.first, word.last + 1)}» took offset $hit",
                    hit in word.first..word.last + 1,
                )
            }
        }
    }

    private fun words(text: String, start: Int, end: Int): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var from = start
        for (i in start..end) {
            if (i == end || text[i] == ' ') {
                if (i > from) out += from until i
                from = i + 1
            }
        }
        return out
    }

    private companion object {
        const val EdgeTolerancePx = 2f
        // أصول السنة, plain prose long enough to wrap into several justified lines at 300 dp.
        const val Paragraph =
            "قال الإمام أحمد بن حنبل رحمه الله تعالى أصول السنة عندنا التمسك بما كان عليه أصحاب " +
                "رسول الله صلى الله عليه وسلم والاقتداء بهم وترك البدع وكل بدعة فهي ضلالة وترك " +
                "الخصومات والجلوس مع أصحاب الأهواء وترك المراء والجدال والخصومات في الدين"
    }
}
