package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class TelegramSnackbarVisuals(
    override val message: String,
    val isError: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false
) : SnackbarVisuals

suspend fun SnackbarHostState.showTelegramSnackbar(
    message: String,
    isError: Boolean = false
) {
    currentSnackbarData?.dismiss()
    showSnackbar(TelegramSnackbarVisuals(message = message, isError = isError))
}

@Composable
fun TelegramSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .imePadding()
            .padding(
                start = SNACKBAR_SCREEN_PADDING,
                top = SNACKBAR_SCREEN_PADDING,
                end = SNACKBAR_SCREEN_PADDING,
                bottom = SNACKBAR_SCREEN_PADDING + bottomContentPadding
            )
    ) { data ->
        TelegramSnackbar(
            message = data.visuals.message,
            isError = (data.visuals as? TelegramSnackbarVisuals)?.isError == true
        )
    }
}

@Composable
fun TelegramSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = SNACKBAR_MAX_WIDTH)
                .fillMaxWidth()
                .defaultMinSize(minHeight = SNACKBAR_MIN_HEIGHT),
            shape = RoundedCornerShape(SNACKBAR_CORNER_RADIUS),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = SNACKBAR_SHADOW_ELEVATION,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SNACKBAR_HORIZONTAL_PADDING,
                        vertical = SNACKBAR_VERTICAL_PADDING
                    ),
                horizontalArrangement = Arrangement.spacedBy(SNACKBAR_CONTENT_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isError) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.size(SNACKBAR_ICON_SIZE)
                    )
                }
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = SNACKBAR_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private val SNACKBAR_MAX_WIDTH = 420.dp
private val SNACKBAR_MIN_HEIGHT = 52.dp
private val SNACKBAR_SCREEN_PADDING = 16.dp
private val SNACKBAR_CORNER_RADIUS = 10.dp
private val SNACKBAR_SHADOW_ELEVATION = 4.dp
private val SNACKBAR_HORIZONTAL_PADDING = 16.dp
private val SNACKBAR_VERTICAL_PADDING = 11.dp
private val SNACKBAR_CONTENT_SPACING = 10.dp
private val SNACKBAR_ICON_SIZE = 20.dp
private const val SNACKBAR_MAX_LINES = 3
