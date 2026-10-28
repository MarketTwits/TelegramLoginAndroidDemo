package com.markettwits.devx.tgsignin.ui.component.tgs

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Represents a pre-baked TGS animation stored as a sequence of raster frames (Bitmaps).
 *
 * @property frames Sequence of rasterized animation frames as [ImageBitmap].
 * @property durationMs Total duration of the animation cycle in milliseconds.
 */
data class BakedTgsAnimation(
    val frames: List<ImageBitmap>,
    val durationMs: Long
)
