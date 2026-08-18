package com.markettwits.devx.tgsignin.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class TelegramSensitiveValueTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun userIdStaysOutOfSemanticsUntilExplicitlyRevealed() {
        composeRule.setContent {
            MaterialTheme {
                TelegramSensitiveValue(
                    value = TELEGRAM_USER_ID,
                    showValueDescription = SHOW_DESCRIPTION,
                    hideValueDescription = HIDE_DESCRIPTION
                )
            }
        }

        composeRule.onNodeWithText(TELEGRAM_USER_ID).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(SHOW_DESCRIPTION).performClick()
        composeRule.onNodeWithText(TELEGRAM_USER_ID).assertExists()
        composeRule.onNodeWithContentDescription(HIDE_DESCRIPTION).performClick()
        composeRule.onNodeWithText(TELEGRAM_USER_ID).assertDoesNotExist()
    }

    private companion object {
        const val TELEGRAM_USER_ID = "987654321"
        const val SHOW_DESCRIPTION = "Show Telegram user ID"
        const val HIDE_DESCRIPTION = "Hide Telegram user ID"
    }
}
