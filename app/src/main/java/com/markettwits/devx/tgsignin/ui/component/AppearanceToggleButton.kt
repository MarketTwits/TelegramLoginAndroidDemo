package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.data.model.AppThemeMode
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.ui.theme.AppThemeAnimationState
import kotlin.math.absoluteValue

@Composable
fun AppearanceToggleButton(
    currentMode: AppThemeMode,
    expressiveAvailable: Boolean,
    systemDark: Boolean,
    animationState: AppThemeAnimationState,
    onModeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    val nextMode = currentMode.next(expressiveAvailable, systemDark)
    val haptics = LocalHapticFeedback.current

    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            animationState.animateTo(nextMode)
            onModeSelected(nextMode)
        },
        enabled = !animationState.isAnimating,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = iconTint,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = iconTint.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .shadow(3.dp, CircleShape)
            .onGloballyPositioned { coordinates ->
                animationState.updateButtonPosition(coordinates.boundsInWindow())
            }
    ) {
        RollingThemeIcon(targetMode = nextMode, iconTint = iconTint)
    }
}

@Composable
private fun RollingThemeIcon(targetMode: AppThemeMode, iconTint: Color) {
    var visibleMode by remember { mutableStateOf(targetMode) }
    val offset = remember { Animatable(0f) }

    LaunchedEffect(targetMode) {
        if (targetMode == visibleMode) return@LaunchedEffect
        offset.animateTo(
            targetValue = -1f,
            animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing)
        )
        visibleMode = targetMode
        offset.snapTo(1f)
        offset.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
    }

    Icon(
        imageVector = visibleMode.icon(),
        contentDescription = stringResource(
            R.string.switch_to_theme,
            stringResource(targetMode.titleRes())
        ),
        tint = iconTint,
        modifier = Modifier
            .clipToBounds()
            .graphicsLayer {
                translationY = offset.value * size.height
                rotationZ = offset.value * 35f
                alpha = 1f - offset.value.absoluteValue
            }
    )
}

private fun AppThemeMode.next(expressiveAvailable: Boolean, systemDark: Boolean): AppThemeMode = when (this) {
    AppThemeMode.System -> if (systemDark) AppThemeMode.Light else AppThemeMode.Dark
    AppThemeMode.Light -> AppThemeMode.Dark
    AppThemeMode.Dark -> if (expressiveAvailable) AppThemeMode.Expressive else AppThemeMode.Light
    AppThemeMode.Expressive -> AppThemeMode.Light
}

private fun AppThemeMode.icon(): ImageVector = when (this) {
    AppThemeMode.Dark -> Icons.Outlined.DarkMode
    AppThemeMode.Expressive -> Icons.Outlined.AutoAwesome
    AppThemeMode.Light, AppThemeMode.System -> Icons.Outlined.LightMode
}

private fun AppThemeMode.titleRes(): Int = when (this) {
    AppThemeMode.Dark -> R.string.theme_dark
    AppThemeMode.Expressive -> R.string.theme_expressive
    AppThemeMode.Light -> R.string.theme_light
    AppThemeMode.System -> R.string.theme_system
}
