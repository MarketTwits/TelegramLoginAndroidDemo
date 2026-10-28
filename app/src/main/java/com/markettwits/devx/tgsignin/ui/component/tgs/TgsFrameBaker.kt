package com.markettwits.devx.tgsignin.ui.component.tgs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.markettwits.devx.tgsignin.data.model.ProfileEmoji
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

/**
 * Frame Baker for Telegram TGS stickers.
 *
 * Pre-renders vector Lottie compositions into raster Bitmaps on background threads,
 * caching frames in a memory-bounded LRU cache (24 MiB).
 * Playback draws raster Bitmaps instead of recalculating vector compositions on every frame,
 * eliminating HWUI rendering delays and frame drops.
 */
object TgsFrameBaker {
    private const val TAG = "TgsFrameBaker"

    const val DEFAULT_FRAME_WIDTH_PX = 260
    const val DEFAULT_FRAME_HEIGHT_PX = 260
    const val DEFAULT_TARGET_FPS = 30
    const val MAX_FRAMES = 90
    const val MAX_CACHE_BYTES = 24 * 1024 * 1024 // 24 MiB

    private val frameCache = object : LruCache<String, BakedTgsAnimation>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: BakedTgsAnimation): Int {
            val frame = value.frames.firstOrNull() ?: return 0
            val bytes = value.frames.size.toLong() * frame.width.toLong() * frame.height.toLong() * 4L
            return bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private val bakeMutex = Mutex()

    fun getCachedFrames(cacheKey: String): BakedTgsAnimation? = frameCache.get(cacheKey)

    fun clearCache() {
        frameCache.evictAll()
    }

    fun currentCacheBytes(): Int = frameCache.size()

    fun calculateTotalFrames(durationMs: Long, targetFps: Int = DEFAULT_TARGET_FPS): Int {
        val fps = targetFps.coerceIn(1, 60)
        return ceil(durationMs.toDouble() * fps / 1000.0).toInt().coerceIn(1, MAX_FRAMES)
    }

    suspend fun prebake(
        emojis: List<ProfileEmoji>,
        repository: ProfileEmojiRepository,
        widthPx: Int = DEFAULT_FRAME_WIDTH_PX,
        heightPx: Int = DEFAULT_FRAME_HEIGHT_PX,
        targetFps: Int = DEFAULT_TARGET_FPS
    ) {
        emojis.forEach { emoji ->
            currentCoroutineContext().ensureActive()
            val cacheKey = "emoji:${emoji.sha256}:${widthPx}x${heightPx}:${targetFps}fps"
            if (getCachedFrames(cacheKey) != null) return@forEach
            try {
                val file = repository.loadAnimationFile(emoji)
                val json = TgsDecoder.decodeTgsToJson(file, cacheKey = emoji.sha256) ?: return@forEach
                bake(
                    tgsJson = json,
                    cacheKey = cacheKey,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    targetFps = targetFps
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore failure for a single prebake asset
            }
        }
    }

    suspend fun prebake(
        file: File,
        cacheKey: String = "tgs:${file.name}:${DEFAULT_FRAME_WIDTH_PX}x${DEFAULT_FRAME_HEIGHT_PX}:${DEFAULT_TARGET_FPS}fps",
        widthPx: Int = DEFAULT_FRAME_WIDTH_PX,
        heightPx: Int = DEFAULT_FRAME_HEIGHT_PX,
        targetFps: Int = DEFAULT_TARGET_FPS
    ) {
        if (getCachedFrames(cacheKey) != null) return
        val json = TgsDecoder.decodeTgsToJson(file, cacheKey = file.absolutePath) ?: return
        bake(
            tgsJson = json,
            cacheKey = cacheKey,
            widthPx = widthPx,
            heightPx = heightPx,
            targetFps = targetFps
        )
    }

    suspend fun bake(
        file: File,
        cacheKey: String = "tgs:${file.name}:${DEFAULT_FRAME_WIDTH_PX}x${DEFAULT_FRAME_HEIGHT_PX}:${DEFAULT_TARGET_FPS}fps",
        widthPx: Int = DEFAULT_FRAME_WIDTH_PX,
        heightPx: Int = DEFAULT_FRAME_HEIGHT_PX,
        targetFps: Int = DEFAULT_TARGET_FPS
    ): BakedTgsAnimation? {
        frameCache.get(cacheKey)?.let { return it }

        val json = TgsDecoder.decodeTgsToJson(file, cacheKey = file.absolutePath) ?: return null
        return bake(
            tgsJson = json,
            cacheKey = cacheKey,
            widthPx = widthPx,
            heightPx = heightPx,
            targetFps = targetFps
        )
    }

    suspend fun bake(
        tgsJson: String,
        cacheKey: String,
        widthPx: Int = DEFAULT_FRAME_WIDTH_PX,
        heightPx: Int = DEFAULT_FRAME_HEIGHT_PX,
        targetFps: Int = DEFAULT_TARGET_FPS
    ): BakedTgsAnimation? {
        frameCache.get(cacheKey)?.let { return it }

        return bakeMutex.withLock {
            currentCoroutineContext().ensureActive()

            // Double check cache after acquiring lock
            frameCache.get(cacheKey)?.let { return@withLock it }

            try {
                val animation = withContext(Dispatchers.Default) {
                    render(
                        tgsJson = tgsJson,
                        cacheKey = cacheKey,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        targetFps = targetFps
                    )
                }

                if (animation != null) {
                    frameCache.put(cacheKey, animation)
                }
                animation
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bake TGS: $cacheKey", e)
                null
            }
        }
    }

    private suspend fun render(
        tgsJson: String,
        cacheKey: String,
        widthPx: Int,
        heightPx: Int,
        targetFps: Int
    ): BakedTgsAnimation? {
        require(widthPx > 0 && heightPx > 0) { "Bitmap dimensions must be positive" }

        val result = LottieCompositionFactory.fromJsonStringSync(tgsJson, cacheKey)
        val composition = result.value
        if (composition == null) {
            Log.e(TAG, "Lottie composition was null for $cacheKey: ${result.exception?.message}", result.exception)
            return null
        }

        val durationMs = composition.duration.toLong().coerceAtLeast(1L)
        val totalFrames = calculateTotalFrames(durationMs, targetFps)

        val startFrame = composition.startFrame
        val endFrame = composition.endFrame

        val drawable = LottieDrawable().apply {
            setComposition(composition)
            bounds = Rect(0, 0, widthPx, heightPx)
        }

        val frames = ArrayList<ImageBitmap>(totalFrames)

        try {
            for (i in 0 until totalFrames) {
                currentCoroutineContext().ensureActive()

                val progress = i.toFloat() / totalFrames
                val frame = (
                    startFrame + (endFrame - startFrame) * progress
                ).toInt().coerceIn(
                    startFrame.toInt(),
                    endFrame.toInt().coerceAtLeast(startFrame.toInt())
                )

                val bitmap = Bitmap.createBitmap(
                    widthPx,
                    heightPx,
                    Bitmap.Config.ARGB_8888
                )

                drawable.frame = frame
                drawable.draw(Canvas(bitmap))

                frames.add(bitmap.asImageBitmap())
            }

            return BakedTgsAnimation(
                frames = frames,
                durationMs = durationMs
            )
        } catch (e: CancellationException) {
            // Free partially allocated bitmaps immediately if baking was cancelled
            frames.forEach { image ->
                image.asAndroidBitmap().recycle()
            }
            throw e
        }
    }
}
