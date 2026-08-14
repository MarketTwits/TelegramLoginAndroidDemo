package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.markettwits.devx.tgsignin.data.model.ProfileBadge
import com.markettwits.devx.tgsignin.data.model.ProfileBadgeKind
import com.markettwits.devx.tgsignin.data.repository.ProfileBadgeRepository
import org.koin.compose.koinInject

@Composable
fun ProfileBadgeImage(
    badge: ProfileBadge?,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    contentDescription: String? = null,
    repository: ProfileBadgeRepository = koinInject()
) {
    val accessibleModifier = if (contentDescription == null) modifier else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    if (badge == null) {
        BadgePlaceholder(accessibleModifier)
        return
    }
    when (badge.kind) {
        ProfileBadgeKind.LOTTIE_TGS -> {
            val json by produceState<String?>(null, badge.sha256) {
                value = runCatching { repository.loadAnimationJson(badge) }.getOrNull()
            }
            val animationJson = json
            if (animationJson == null) {
                BadgePlaceholder(accessibleModifier)
            } else {
                val composition by rememberLottieComposition(
                    LottieCompositionSpec.JsonString(animationJson)
                )
                if (composition == null) {
                    BadgePlaceholder(accessibleModifier)
                } else if (animate) {
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = accessibleModifier
                    )
                } else {
                    LottieAnimation(
                        composition = composition,
                        progress = { 0f },
                        modifier = accessibleModifier
                    )
                }
            }
        }
        ProfileBadgeKind.STATIC_WEBP -> {
            val file by produceState<java.io.File?>(null, badge.sha256) {
                value = runCatching { repository.loadStaticAsset(badge) }.getOrNull()
            }
            if (file == null) {
                BadgePlaceholder(accessibleModifier)
            } else {
                AsyncImage(
                    model = file,
                    contentDescription = contentDescription,
                    modifier = accessibleModifier
                )
            }
        }
    }
}

@Composable
private fun BadgePlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize(0.32f)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
