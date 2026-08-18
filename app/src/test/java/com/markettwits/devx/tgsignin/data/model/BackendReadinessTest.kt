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
            telegramConfigured = true,
            apiVersion = 8
        )

        assertTrue(readiness.isReady)
    }

    @Test
    fun `is not ready when Telegram is not configured`() {
        val readiness = BackendReadiness(
            serviceReady = true,
            databaseConnected = true,
            telegramConfigured = false,
            apiVersion = 8
        )

        assertFalse(readiness.isReady)
    }

    @Test
    fun `legacy backend contract is not compatible or ready`() {
        val readiness = BackendReadiness(
            serviceReady = true,
            databaseConnected = true,
            telegramConfigured = true,
            apiVersion = 1
        )

        assertFalse(readiness.isApiCompatible)
        assertFalse(readiness.isReady)
    }
}
