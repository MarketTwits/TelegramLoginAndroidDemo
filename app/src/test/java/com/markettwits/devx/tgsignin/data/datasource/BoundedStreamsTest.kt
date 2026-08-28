package com.markettwits.devx.tgsignin.data.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BoundedStreamsTest {
    @Test
    fun `reads a UTF-8 response within the configured limit`() {
        val body = "Привет, Telegram"

        assertEquals(
            body,
            ByteArrayInputStream(body.toByteArray()).readUtf8Bounded(128)
        )
    }

    @Test
    fun `rejects a response larger than the configured limit`() {
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(ByteArray(17)).readUtf8Bounded(16)
        }
    }
}
