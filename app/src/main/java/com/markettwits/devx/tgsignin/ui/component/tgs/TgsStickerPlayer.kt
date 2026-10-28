package com.markettwits.devx.tgsignin.ui.component.tgs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.roundToInt

/**
 * High-performance Compose player for TGS stickers using pre-rendered frames.
 *
 * Uses [withFrameNanos] for time-based frame advancement to match actual wall-clock time,
 * and renders frames onto [Canvas] using [drawImage].
 */
@Composable
fun TgsStickerPlayer(
    bakedAnimation: BakedTgsAnimation?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholder: @Composable () -> Unit = { Spacer(modifier = modifier) }
) {
    val accessibleModifier = if (contentDescription == null) modifier else {
        modifier.semantics { this.contentDescription = contentDescription }
    }

    if (bakedAnimation == null) {
        placeholder()
        return
    }

    val frames = bakedAnimation.frames
    val durationMs = bakedAnimation.durationMs

    if (frames.isEmpty()) {
        placeholder()
        return
    }

    if (frames.size == 1 || durationMs <= 0L) {
        Canvas(modifier = accessibleModifier) {
            val frame = frames.first()
            val scale = minOf(
                size.width / frame.width.toFloat(),
                size.height / frame.height.toFloat()
            )
            val width = (frame.width * scale).roundToInt()
            val height = (frame.height * scale).roundToInt()

            if (width > 0 && height > 0) {
                drawImage(
                    image = frame,
                    dstOffset = IntOffset(
                        ((size.width - width) / 2f).roundToInt(),
                        ((size.height - height) / 2f).roundToInt()
                    ),
                    dstSize = IntSize(width, height),
                    filterQuality = FilterQuality.Medium
                )
            }
        }
        return
    }

    var frameIndex by remember(bakedAnimation) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(bakedAnimation) {
        val frameDurationNanos = durationMs * 1_000_000L / frames.size
        if (frameDurationNanos <= 0L) return@LaunchedEffect

        var previousFrameTime = 0L
        var elapsedNanos = 0L

        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (previousFrameTime == 0L) {
                    previousFrameTime = frameTimeNanos
                } else {
                    elapsedNanos += frameTimeNanos - previousFrameTime
                    previousFrameTime = frameTimeNanos

                    if (elapsedNanos >= frameDurationNanos) {
                        val framesToAdvance = (elapsedNanos / frameDurationNanos).toInt()
                        elapsedNanos %= frameDurationNanos
                        frameIndex = (frameIndex + framesToAdvance) % frames.size
                    }
                }
            }
        }
    }

    Canvas(modifier = accessibleModifier) {
        val safeIndex = (frameIndex % frames.size).coerceAtLeast(0)
        val frame = frames[safeIndex]
        val scale = minOf(
            size.width / frame.width.toFloat(),
            size.height / frame.height.toFloat()
        )
        val width = (frame.width * scale).roundToInt()
        val height = (frame.height * scale).roundToInt()

        if (width > 0 && height > 0) {
            drawImage(
                image = frame,
                dstOffset = IntOffset(
                    ((size.width - width) / 2f).roundToInt(),
                    ((size.height - height) / 2f).roundToInt()
                ),
                dstSize = IntSize(width, height),
                filterQuality = FilterQuality.Medium
            )
        }
    }
}

@Composable
fun TgsStickerPlayer(
    tgsJson: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cacheKey: String = remember(tgsJson) {
        "tgs:${tgsJson?.hashCode() ?: 0}:${TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX}x${TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX}:${TgsFrameBaker.DEFAULT_TARGET_FPS}fps"
    },
    placeholder: @Composable () -> Unit = { Spacer(modifier = modifier) }
) {
    var bakedAnimation by remember(cacheKey) {
        mutableStateOf(TgsFrameBaker.getCachedFrames(cacheKey))
    }

    LaunchedEffect(cacheKey, tgsJson) {
        if (tgsJson != null && bakedAnimation == null) {
            bakedAnimation = TgsFrameBaker.bake(
                tgsJson = tgsJson,
                cacheKey = cacheKey,
                widthPx = TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX,
                heightPx = TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX,
                targetFps = TgsFrameBaker.DEFAULT_TARGET_FPS
            )
        }
    }

    TgsStickerPlayer(
        bakedAnimation = bakedAnimation,
        modifier = modifier,
        contentDescription = contentDescription,
        placeholder = placeholder
    )
}

@Composable
fun TgsStickerPlayer(
    file: File?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cacheKey: String = remember(file) {
        "tgs:${file?.name.orEmpty()}:${TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX}x${TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX}:${TgsFrameBaker.DEFAULT_TARGET_FPS}fps"
    },
    placeholder: @Composable () -> Unit = { Spacer(modifier = modifier) }
) {
    var bakedAnimation by remember(cacheKey) {
        mutableStateOf(TgsFrameBaker.getCachedFrames(cacheKey))
    }

    LaunchedEffect(cacheKey, file) {
        if (file != null && bakedAnimation == null) {
            bakedAnimation = TgsFrameBaker.bake(
                file = file,
                cacheKey = cacheKey,
                widthPx = TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX,
                heightPx = TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX,
                targetFps = TgsFrameBaker.DEFAULT_TARGET_FPS
            )
        }
    }

    TgsStickerPlayer(
        bakedAnimation = bakedAnimation,
        modifier = modifier,
        contentDescription = contentDescription,
        placeholder = placeholder
    )
}
