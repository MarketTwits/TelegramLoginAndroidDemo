package com.markettwits.devx.tgsignin.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendReadinessTest {
    @Test
    fun `is ready when every backend component is ready`() {
        val readiness = BackendReadiness(
            serviceReady = true,
            databaseConnected = true,
            telegramConfigured = true
        )

        assertTrue(readiness.isReady)
    }

    @Test
    fun `is not ready when Telegram is not configured`() {
        val readiness = BackendReadiness(
            serviceReady = true,
            databaseConnected = true,
            telegramConfigured = false
        )

        assertFalse(readiness.isReady)
    }
}
