package com.atharchive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        composeRule.onNodeWithTag("athar_page_title").assertTextEquals("الكتب")
        composeRule.onNodeWithTag("athar_app_icon").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_books").assertIsSelected()

        val rtlOrder = listOf("books", "poetry", "search", "audio", "kannashah")
        rtlOrder.forEach { route ->
            composeRule.onNodeWithTag("bottom_$route").assertIsDisplayed()
        }
        val centers = rtlOrder.map { route ->
            composeRule.onNodeWithTag("bottom_$route").fetchSemanticsNode().boundsInRoot.center.x
        }
        assertTrue(
            "Primary destinations must follow the agreed RTL order",
            centers.zipWithNext().all { (a, b) -> a > b },
        )
    }

    @Test
    fun catalogExposesCurrentTabsAndThePersonalLibrary() {
        listOf("all", "recent", "downloaded", "mylist").forEach { tab ->
            composeRule.onNodeWithTag("books_tab_$tab").assertIsDisplayed()
        }

        composeRule.onNodeWithContentDescription("مكتبتي").performClick()
        composeRule.onNodeWithTag("library_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("athar_page_title").assertTextEquals("مكتبتي")
        composeRule.onNodeWithTag("library_empty").assertIsDisplayed()
    }

    @Test
    fun libraryOffersCollectionCreation() {
        composeRule.onNodeWithContentDescription("مكتبتي").performClick()
        composeRule.onNodeWithContentDescription("إنشاء مجموعة").performClick()
        composeRule.onNodeWithText("مجموعة جديدة").assertIsDisplayed()
        composeRule.onNodeWithTag("collection_title").performTextInput("بحوث")
        composeRule.onNodeWithTag("collection_title").assertTextContains("بحوث")
        composeRule.onNodeWithText("إلغاء").performClick()
        composeRule.onAllNodesWithText("مجموعة جديدة").assertCountEquals(0)
    }

    @Test
    fun globalSearchAcceptsAQueryAndExplainsItsLocalScope() {
        composeRule.onNodeWithTag("bottom_search").performClick()
        composeRule.onNodeWithTag("section_search").assertIsDisplayed()
        composeRule.onNodeWithTag("athar_page_title").assertTextEquals("البحث")
        composeRule.onNodeWithTag("search_search").performTextInput("علم")
        composeRule.onNodeWithTag("search_search").assertTextContains("علم")
        composeRule.onNodeWithTag("search_offline_note").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_search").assertIsSelected()
    }
}
