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

    @Test
    fun `legacy Android user agent is displayed as a device name`() {
        assertEquals(
            "Pixel 8",
            normalizedDeviceLabel("Dalvik/2.1.0 (Linux; U; Android 17; Pixel 8 Build/")
        )
        assertEquals("Android device", normalizedDeviceLabel("Dalvik/2.1.0 (Linux; U; Android 17)"))
    }
}
