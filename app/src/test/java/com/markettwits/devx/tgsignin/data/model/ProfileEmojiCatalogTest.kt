package com.markettwits.devx.tgsignin.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEmojiCatalogTest {
    private val default = emoji("spotty-persik", "first")
    private val neon = emoji("neon", "glow")
    private val catalog = ProfileEmojiCatalog(
        version = 3,
        defaultEmoji = ProfileEmojiSelection("spotty-persik", "first"),
        sets = listOf(
            ProfileEmojiSet("spotty-persik", mapOf("en" to "Spotty"), "first", listOf(default)),
            ProfileEmojiSet("neon", mapOf("en" to "Neon"), "glow", listOf(neon))
        )
    )

    @Test
    fun `missing and unsupported selections resolve to catalog default`() {
        assertEquals(catalog.defaultEmoji, catalog.resolve(null))
        assertEquals(catalog.defaultEmoji, catalog.resolve(ProfileEmojiSelection("old", "gone")))
        assertEquals(default, catalog.emoji(null))
    }

    @Test
    fun `selection identity includes both set and emoji ids`() {
        val selection = ProfileEmojiSelection("neon", "glow")
        assertTrue(catalog.contains(selection))
        assertEquals(neon, catalog.emoji(selection))
        assertFalse(catalog.contains(ProfileEmojiSelection("spotty-persik", "glow")))
    }

    @Test
    fun `search matches emoji metadata and localized set labels`() {
        assertEquals(listOf(neon), catalog.search("glow", "en"))
        assertEquals(listOf(neon), catalog.search("neon", "en"))
        assertTrue(catalog.search("missing", "en").isEmpty())
        assertTrue(catalog.search("   ", "en").isEmpty())
    }

    private fun emoji(setId: String, id: String) = ProfileEmoji(
        setId = setId,
        id = id,
        name = id,
        keywords = listOf(id),
        assetPath = "/assets/profile-emojis/v3/${"a".repeat(64)}.tgs",
        sha256 = "a".repeat(64),
        sizeBytes = 100,
        width = 512,
        height = 512,
        framesPerSecond = 60f,
        durationMs = 1_000,
        enabled = true
    )
}
