package com.markettwits.devx.tgsignin.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.markettwits.devx.tgsignin.data.model.ProfileEmoji
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiCatalog
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSet
import org.junit.Rule
import org.junit.Test

class TelegramEmojiSetDropdownTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun groupedPickerOpensSwitchesCategoriesAndSearchesWithoutIntrinsicCrash() {
        val music = emoji("animated-icons", "music", "Music")
        val cart = emoji("animated-icons", "cart", "Shopping Cart")
        val neon = emoji("neon", "glow", "Neon Glow")
        val catalog = ProfileEmojiCatalog(
            version = 3,
            defaultEmoji = ProfileEmojiSelection(music.setId, music.id),
            sets = listOf(
                ProfileEmojiSet(
                    id = "animated-icons",
                    labels = mapOf("en" to "Animated Icons"),
                    thumbnailEmojiId = music.id,
                    emojis = listOf(music, cart)
                ),
                ProfileEmojiSet(
                    id = "neon",
                    labels = mapOf("en" to "Neon"),
                    thumbnailEmojiId = neon.id,
                    emojis = listOf(neon)
                )
            )
        )

        composeRule.setContent {
            MaterialTheme {
                TelegramEmojiSetDropdown(
                    expanded = true,
                    catalog = catalog,
                    selectedEmoji = catalog.defaultEmoji,
                    recentSelections = emptyList(),
                    onEmojiSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Recently used emoji").assertExists()
        composeRule.onNodeWithContentDescription("Neon").performClick()
        composeRule.onNodeWithContentDescription("Neon Glow").assertExists()
        composeRule.onNode(hasSetTextAction()).performTextInput("music")
        composeRule.onNodeWithContentDescription("Music").assertExists()
    }

    private fun emoji(setId: String, id: String, name: String) = ProfileEmoji(
        setId = setId,
        id = id,
        name = name,
        keywords = listOf(name),
        assetPath = "/assets/profile-emojis/v3/${id.padEnd(64, 'a')}.tgs",
        sha256 = id.padEnd(64, 'a'),
        sizeBytes = 100,
        width = 512,
        height = 512,
        framesPerSecond = 60f,
        durationMs = 1_000,
        enabled = true
    )
}
