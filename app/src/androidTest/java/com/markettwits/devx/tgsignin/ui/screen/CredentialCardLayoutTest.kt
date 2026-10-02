package com.markettwits.devx.tgsignin.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.data.model.PasskeyInfo
import com.markettwits.devx.tgsignin.data.model.UserSessionInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CredentialCardLayoutTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun currentSessionCardStaysCompactWithLongDeviceLabel() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    SessionCard(
                        session = UserSessionInfo(
                            id = "session-1",
                            createdAt = "2026-09-30T12:00:00.000Z",
                            lastSeenAt = "2026-10-02T12:00:00.000Z",
                            expiresAt = "2026-10-30T12:00:00.000Z",
                            authenticationMethod = "PASSKEY",
                            deviceLabel = "Very long Android device label with model and build details",
                            current = true
                        ),
                        enabled = true,
                        onRevoke = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Current device").assertExists()
        assertCardHeightAtMost("session-card", 220)
    }

    @Test
    fun passkeyCardStaysCompactWithLongNameAndTwoActions() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    PasskeyCard(
                        passkey = PasskeyInfo(
                            id = "passkey-1",
                            credentialId = "credential-1",
                            name = "Very long passkey name ".repeat(4),
                            createdAt = "2026-09-30T12:00:00.000Z",
                            lastUsedAt = "2026-10-02T12:00:00.000Z",
                            deviceType = "platform",
                            backedUp = false
                        ),
                        enabled = true,
                        onRename = {},
                        onDelete = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("This device").assertExists()
        assertCardHeightAtMost("passkey-card", 240)
    }

    private fun assertCardHeightAtMost(tag: String, maxDp: Int) {
        val heightPx = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
        val maxPx = maxDp * composeRule.activity.resources.displayMetrics.density
        assertTrue("$tag is too tall: $heightPx px", heightPx <= maxPx)
    }
}
