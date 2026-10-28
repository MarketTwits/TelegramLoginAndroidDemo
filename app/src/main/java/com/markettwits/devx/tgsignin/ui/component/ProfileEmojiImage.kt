package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.markettwits.devx.tgsignin.data.model.ProfileEmoji
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepository
import com.markettwits.devx.tgsignin.ui.component.tgs.BakedTgsAnimation
import com.markettwits.devx.tgsignin.ui.component.tgs.TgsDecoder
import com.markettwits.devx.tgsignin.ui.component.tgs.TgsFrameBaker
import com.markettwits.devx.tgsignin.ui.component.tgs.TgsStickerPlayer
import org.koin.compose.koinInject

@Composable
fun ProfileEmojiImage(
    emoji: ProfileEmoji?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    repository: ProfileEmojiRepository = koinInject()
) {
    val accessibleModifier = if (contentDescription == null) modifier else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    if (emoji == null) {
        BadgePlaceholder(accessibleModifier)
        return
    }

    val cacheKey = remember(emoji.sha256) {
        "emoji:${emoji.sha256}:${TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX}x${TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX}:${TgsFrameBaker.DEFAULT_TARGET_FPS}fps"
    }

    var bakedAnimation by remember(emoji.sha256) {
        mutableStateOf(TgsFrameBaker.getCachedFrames(cacheKey))
    }

    LaunchedEffect(emoji.sha256) {
        if (bakedAnimation == null) {
            val file = runCatching { repository.loadAnimationFile(emoji) }.getOrNull()
            if (file != null) {
                val json = TgsDecoder.decodeTgsToJson(file, cacheKey = emoji.sha256)
                if (json != null) {
                    bakedAnimation = TgsFrameBaker.bake(
                        tgsJson = json,
                        cacheKey = cacheKey,
                        widthPx = TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX,
                        heightPx = TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX,
                        targetFps = TgsFrameBaker.DEFAULT_TARGET_FPS
                    )
                }
            }
        }
    }

    TgsStickerPlayer(
        bakedAnimation = bakedAnimation,
        modifier = modifier,
        contentDescription = contentDescription,
        placeholder = { BadgePlaceholder(accessibleModifier) }
    )
}

@Composable
private fun BadgePlaceholder(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "profile emoji placeholder")
    val alpha by transition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720),
            repeatMode = RepeatMode.Reverse
        ),
        label = "profile emoji placeholder alpha"
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.22f))
    )
}
