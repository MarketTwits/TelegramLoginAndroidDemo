package com.markettwits.devx.tgsignin.ui.component.tgs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TgsFrameBakerTest {

    @Test
    fun calculateTotalFramesFollowsDurationAndCapsAtMaxFrames() {
        // 500ms at 30 fps -> 15 frames
        assertEquals(15, TgsFrameBaker.calculateTotalFrames(durationMs = 500L, targetFps = 30))

        // 1000ms at 30 fps -> 30 frames
        assertEquals(30, TgsFrameBaker.calculateTotalFrames(durationMs = 1_000L, targetFps = 30))

        // 2000ms at 30 fps -> 60 frames
        assertEquals(60, TgsFrameBaker.calculateTotalFrames(durationMs = 2_000L, targetFps = 30))

        // 3000ms at 30 fps -> 90 frames (exactly MAX_FRAMES)
        assertEquals(90, TgsFrameBaker.calculateTotalFrames(durationMs = 3_000L, targetFps = 30))

        // 5000ms at 30 fps -> 90 frames (must cap at MAX_FRAMES = 90 to prevent memory blowup)
        assertEquals(90, TgsFrameBaker.calculateTotalFrames(durationMs = 5_000L, targetFps = 30))

        // 10000ms at 30 fps -> 90 frames
        assertEquals(90, TgsFrameBaker.calculateTotalFrames(durationMs = 10_000L, targetFps = 30))

        // Edge cases: 0ms or negative
        assertEquals(1, TgsFrameBaker.calculateTotalFrames(durationMs = 0L, targetFps = 30))
        assertEquals(1, TgsFrameBaker.calculateTotalFrames(durationMs = -100L, targetFps = 30))

        // Custom FPS
        assertEquals(60, TgsFrameBaker.calculateTotalFrames(durationMs = 1_000L, targetFps = 60))
        assertEquals(90, TgsFrameBaker.calculateTotalFrames(durationMs = 2_000L, targetFps = 60))
    }

    @Test
    fun maxMemoryPerAnimationFitsInsideLruCacheLimit() {
        // 90 frames at 260x260 ARGB_8888 (4 bytes per pixel)
        val frameBytes = 260L * 260L * 4L
        val maxAnimationBytes = TgsFrameBaker.MAX_FRAMES * frameBytes

        // Max animation size is ~24.3 MB, fitting inside the 24 MiB (25,165,824 bytes) LRU cache
        assertTrue(
            "Max animation size ($maxAnimationBytes bytes) must not exceed LRU cache capacity (${TgsFrameBaker.MAX_CACHE_BYTES} bytes)",
            maxAnimationBytes <= TgsFrameBaker.MAX_CACHE_BYTES
        )
    }

    @Test
    fun defaultConstantsMatchOptimizationArticle() {
        assertEquals("Target width must be 260px as per Habr article", 260, TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX)
        assertEquals("Target height must be 260px as per Habr article", 260, TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX)
        assertEquals("Target FPS must be 30", 30, TgsFrameBaker.DEFAULT_TARGET_FPS)
        assertEquals("Max frames must be 90", 90, TgsFrameBaker.MAX_FRAMES)
        assertEquals("Cache limit must be 24 MiB", 24 * 1024 * 1024, TgsFrameBaker.MAX_CACHE_BYTES)
    }
}
