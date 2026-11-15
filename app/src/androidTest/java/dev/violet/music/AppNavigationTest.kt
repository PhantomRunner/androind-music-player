package dev.violet.music

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeLibrarySearchAndSettingsAreReachable() {
        compose.onNodeWithContentDescription("Listening stats").assertIsDisplayed()
        compose.onNode(hasText("Library") and hasClickAction()).performClick()
        compose.onNode(hasText("Albums") and hasClickAction()).assertIsDisplayed().performClick()
        compose.onNode(hasText("Artists") and hasClickAction()).assertIsDisplayed().performClick()
        compose.onNode(hasText("Search") and hasClickAction()).performClick()
        compose.onNodeWithText("Songs, artists or albums").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Device library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").performClick()
        compose.onNodeWithText("Songs, artists or albums").assertIsDisplayed()
    }

    @Test
    fun searchCanBeEditedAndCleared() {
        compose.onNode(hasText("Search") and hasClickAction()).performClick()
        compose
            .onNodeWithText("Songs, artists or albums")
            .performTextInput("Violet test fixture query")
        compose.onNodeWithContentDescription("Clear search").assertIsDisplayed().performClick()
        compose
            .onNodeWithText("Songs, artists or albums")
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
            )
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }
}
