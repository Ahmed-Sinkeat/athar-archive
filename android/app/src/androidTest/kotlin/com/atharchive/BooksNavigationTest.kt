package com.atharchive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BooksNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun booksCatalogIsTheRootAndPrimaryNavigationIsRtl() {
        composeRule.onNodeWithTag("books_screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("home_screen").assertCountEquals(0)
        composeRule.onNodeWithText("الكتب").assertIsDisplayed()
        composeRule.onNodeWithTag("athar_app_icon").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_books").assertIsSelected()

        val rtlOrder = listOf("books", "poetry", "search", "articles", "kannashah")
        rtlOrder.forEach { route ->
            composeRule.onNodeWithTag("bottom_$route").assertIsDisplayed()
        }
        val centers = rtlOrder.map { route ->
            composeRule.onNodeWithTag("bottom_$route").fetchSemanticsNode().boundsInRoot.center.x
        }
        assertTrue("Primary destinations must follow the agreed RTL order", centers.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun catalogHasFiveTabsAndMutunIsARealSubset() {
        listOf("all", "mutun", "recent", "downloaded", "mylist").forEach { tab ->
            composeRule.onNodeWithTag("books_tab_$tab").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("books_tab_mutun").performClick().assertIsSelected()
        composeRule.onNodeWithText("العقيدة الطحاوية").assertIsDisplayed()
        composeRule.onAllNodesWithTag("book_row_sharh-hilyat-talib-al-ilm").assertCountEquals(0)
    }

    @Test
    fun inlineSearchMatchesTitleAndAuthorInsideTheCurrentTab() {
        composeRule.onNodeWithTag("books_tab_mutun").performClick()
        composeRule.onNodeWithTag("books_search").performTextInput("الاستقامة")
        composeRule.onNodeWithTag("books_empty_state").assertIsDisplayed()

        composeRule.onNodeWithTag("books_search").performTextReplacement("الطحاوي")
        composeRule.onNodeWithText("العقيدة الطحاوية").assertIsDisplayed()
        composeRule.onNodeWithTag("books_search_clear").performClick()

        composeRule.onNodeWithTag("books_tab_all").performClick()
        composeRule.onNodeWithTag("books_search").performTextInput("الآداب")
        composeRule.onNodeWithTag("books_empty_state").assertIsDisplayed()
    }

    @Test
    fun combinedFiltersApplyImmediatelyAndRecentKeepsRecencyOrder() {
        composeRule.onNodeWithTag("books_filter").performClick()
        composeRule.onNodeWithTag("books_filter_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_discipline_2").performClick()
        composeRule.onNodeWithTag("books_filter_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("تم").performClick()
        composeRule.onNodeWithText("جامع بيان العلم وفضله").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("تصفية الكتب، توجد مرشحات مفعلة").assertIsDisplayed()

        composeRule.onNodeWithTag("books_tab_recent").performClick()
        composeRule.onNodeWithTag("books_filter").performClick()
        composeRule.onAllNodesWithText("الترتيب").assertCountEquals(0)
    }

    @Test
    fun recentRowsOwnTheOnlyCatalogReadingProgress() {
        composeRule.onAllNodesWithTag("reading_progress_aqida-tahawiyya").assertCountEquals(0)
        composeRule.onNodeWithTag("books_tab_recent").performClick()
        composeRule.onNodeWithTag("reading_progress_aqida-tahawiyya").assertIsDisplayed()
        composeRule.onNodeWithText("٤٦٪").assertIsDisplayed()
    }

    @Test
    fun booksStateSurvivesOtherTabsAndRetapScrollsWithoutClearingIt() {
        composeRule.onNodeWithTag("books_tab_mutun").performClick()
        composeRule.onNodeWithTag("books_search").performTextInput("الطحاوي")
        composeRule.onNodeWithTag("bottom_articles").performClick()
        composeRule.onNodeWithTag("section_articles").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_books").performClick()
        composeRule.onNodeWithTag("books_tab_mutun").assertIsSelected()
        composeRule.onNodeWithTag("books_search").assertTextContains("الطحاوي")
        composeRule.onNodeWithTag("bottom_books").performClick()
        composeRule.onNodeWithTag("athar_page_title").assertIsDisplayed()
        composeRule.onNodeWithTag("books_search").assertTextContains("الطحاوي")
    }

    @Test
    fun booksRetapReturnsAScrolledCatalogToTheHeader() {
        composeRule.onNodeWithTag("books_screen")
            .performScrollToNode(hasTestTag("book_row_khalq-afal-al-ibad"))
        composeRule.onNodeWithTag("bottom_books").performClick()
        composeRule.onNodeWithTag("athar_page_title").assertIsDisplayed()
    }

    @Test
    fun globalSearchRemainsADedicatedEqualPrimaryDestination() {
        composeRule.onNodeWithTag("bottom_search").performClick()
        composeRule.onNodeWithTag("section_search").assertIsDisplayed()
        composeRule.onNodeWithTag("athar_page_title").assertTextEquals("البحث")
        composeRule.onNodeWithTag("search_search").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_search").assertIsSelected()
        // Search lives here and nowhere else: no field in any section header.
        composeRule.onAllNodesWithTag("global_search_field").assertCountEquals(0)
    }
}
