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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.markettwits.devx.tgsignin.data.model.ProfileEmoji
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepository
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
    val animationFile by produceState<String?>(null, emoji.sha256) {
        value = runCatching { repository.loadAnimationFile(emoji).absolutePath }.getOrNull()
    }
    val filePath = animationFile
    if (filePath == null) {
        BadgePlaceholder(accessibleModifier)
    } else {
        val composition by rememberLottieComposition(
            spec = LottieCompositionSpec.File(filePath),
            cacheKey = emoji.sha256
        )
        if (composition == null) {
            BadgePlaceholder(accessibleModifier)
        } else {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = accessibleModifier
            )
        }
    }
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
