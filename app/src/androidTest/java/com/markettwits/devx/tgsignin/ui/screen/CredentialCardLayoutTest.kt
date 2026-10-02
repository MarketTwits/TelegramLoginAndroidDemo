package com.markettwits.devx.tgsignin.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        assertCardHeightAtMost("passkey-card", 190)
    }

    @Test
    fun registeredPasskeyCardKeepsActionsBesideDetails() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    PasskeyCard(
                        passkey = passkey("KeePassXC"),
                        enabled = true,
                        onRename = {},
                        onDelete = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Rename").assertExists()
        composeRule.onNodeWithContentDescription("Delete").assertExists()
        assertCardHeightAtMost("passkey-card", 160)
    }

    @Test
    fun deleteActionShowsProgressAndCardExitsAfterRemoval() {
        var passkeys by mutableStateOf(listOf(passkey("KeePassXC")))
        var deleting by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    AnimatedCredentialCards(passkeys, PasskeyInfo::id) { passkey, exiting ->
                        PasskeyCard(
                            passkey = passkey,
                            enabled = !deleting,
                            deleting = deleting || exiting,
                            onRename = {},
                            onDelete = { deleting = true }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithTag("passkey-delete-progress").assertExists()
        composeRule.runOnIdle { passkeys = emptyList() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("passkey-card").assertDoesNotExist()
    }

    @Test
    fun revokeActionShowsProgressAndSessionExitsAfterRemoval() {
        var sessions by mutableStateOf(listOf(session()))
        var revoking by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    AnimatedCredentialCards(sessions, UserSessionInfo::id) { session, exiting ->
                        SessionCard(
                            session = session,
                            enabled = !revoking,
                            revoking = revoking || exiting,
                            onRevoke = { revoking = true }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Terminate").performClick()
        composeRule.onNodeWithTag("session-revoke-progress").assertExists()
        composeRule.runOnIdle { sessions = emptyList() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("session-card").assertDoesNotExist()
    }

    private fun session() = UserSessionInfo(
        id = "session-1",
        createdAt = "2026-09-30T12:00:00.000Z",
        lastSeenAt = "2026-10-02T12:00:00.000Z",
        expiresAt = "2026-10-30T12:00:00.000Z",
        authenticationMethod = "PASSKEY",
        deviceLabel = "Pixel 8",
        current = false
    )

    private fun passkey(name: String) = PasskeyInfo(
        id = "passkey-1",
        credentialId = "credential-1",
        name = name,
        createdAt = "2026-09-30T12:00:00.000Z",
        lastUsedAt = "2026-10-02T12:00:00.000Z",
        deviceType = "platform",
        backedUp = true
    )

    private fun assertCardHeightAtMost(tag: String, maxDp: Int) {
        val heightPx = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
        val maxPx = maxDp * composeRule.activity.resources.displayMetrics.density
        assertTrue("$tag is too tall: $heightPx px", heightPx <= maxPx)
    }
}
