package com.atharchive

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M7.1 regression checks.
 *
 * Each locks a defect that shipped in an APK and was found by using it, not by reading
 * it. They are about reachability rather than looks: whether the obvious gesture does
 * anything, whether a filter tab can ever be filled, whether a settings row is alive.
 */
@RunWith(AndroidJUnit4::class)
class InteractionQualityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val firstAudio = "al-aqida-al-wasitiyya-1"

    /**
     * الصوتيات rows carried no click action at all. The play disc and the ⋯ worked, and
     * tapping the title — the gesture every other list in the app answers — did nothing.
     */
    @Test
    fun anAudioRowRespondsToTheObviousGesture() {
        composeRule.onNodeWithTag("bottom_audio").performClick()
        composeRule.onNodeWithTag("audio_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_row_$firstAudio").assertHasClickAction()
    }

    /** Save and download live in the row's ⋮ menu; both must be reachable from it. */
    @Test
    fun audioCanBeSavedAndDownloadedFromItsRow() {
        composeRule.onNodeWithTag("bottom_audio").performClick()
        composeRule.onNodeWithTag("audio_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("audio_more_$firstAudio").performClick()
        composeRule.onNodeWithTag("audio_save_$firstAudio")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("audio_download_$firstAudio")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    /**
     * المظهر and إعدادات القراءة والخط were two rows that did nothing. They are one row
     * now, and it opens a screen whose default page palette follows the app — which is
     * what stopped a cream page landing on a reader in dark mode.
     */
    @Test
    fun appearanceIsReachableAndCanFollowTheApp() {
        composeRule.onNodeWithTag("settings_action").performClick()
        composeRule.onNodeWithTag("section_settings").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_appearance").performClick()
        composeRule.onNodeWithTag("section_appearance").assertIsDisplayed()

        ThemeModeTags.forEach { composeRule.onNodeWithTag(it).assertIsDisplayed() }
        composeRule.onNodeWithTag("appearance_palette_0")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("appearance_preview").assertIsDisplayed()
    }

    private companion object {
        val ThemeModeTags = listOf(
            "appearance_theme_0",
            "appearance_theme_1",
            "appearance_theme_2",
            "appearance_theme_3",
        )
    }
}
