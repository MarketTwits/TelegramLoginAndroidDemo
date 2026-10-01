package com.markettwits.devx.tgsignin.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSessionInfoTest {
    @Test
    fun `device labels exclude null and whitespace`() {
        assertNull(normalizedDeviceLabel(null))
        assertNull(normalizedDeviceLabel("null"))
        assertNull(normalizedDeviceLabel(" NULL "))
        assertNull(normalizedDeviceLabel("  "))
        assertEquals("Pixel 8", normalizedDeviceLabel(" Pixel 8 "))
    }
}
