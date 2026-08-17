package com.markettwits.devx.tgsignin.data.telegram

import org.junit.Assert.assertThrows
import org.junit.Test

class TelegramLoginConfigTest {
    @Test
    fun `accepts valid Telegram and backend endpoints`() {
        TelegramLoginConfig(
            clientId = "123456",
            redirectUri = "https://example.com/tglogin",
            redirectHost = "example.com",
            backendUrl = "http://10.0.2.2:8080",
            appToken = "dummy-token"
        )
    }

    @Test
    fun `rejects redirect host mismatch`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelegramLoginConfig(
                clientId = "123456",
                redirectUri = "https://other.example.com/tglogin",
                redirectHost = "example.com",
                backendUrl = "https://api.example.com",
                appToken = "dummy-token"
            )
        }
    }

    @Test
    fun `rejects malformed backend endpoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelegramLoginConfig(
                clientId = "123456",
                redirectUri = "https://example.com/tglogin",
                redirectHost = "example.com",
                backendUrl = "not a URL",
                appToken = "dummy-token"
            )
        }
    }
}
