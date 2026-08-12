package com.markettwits.devx.tgsignin.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomVisualSpecTest {
    @Test
    fun `same stable seed always produces the same bounded visual`() {
        val first = bloomVisualSpec("stable-service-seed")
        assertEquals(first, bloomVisualSpec("stable-service-seed"))
        assertTrue(first.primaryHue in 0f..<360f)
        assertTrue(first.secondaryHue in 0f..<360f)
        assertTrue(first.petalCount in 6..9)
    }

    @Test
    fun `different seeds produce different visual specs`() {
        assertNotEquals(bloomVisualSpec("seed-one"), bloomVisualSpec("seed-two"))
    }
}
