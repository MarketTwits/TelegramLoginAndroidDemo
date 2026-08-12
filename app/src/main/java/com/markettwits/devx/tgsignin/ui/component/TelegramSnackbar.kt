package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
                .fillMaxWidth(),
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private val SNACKBAR_MAX_WIDTH = 420.dp
private val SNACKBAR_CORNER_RADIUS = 10.dp
private val SNACKBAR_SHADOW_ELEVATION = 4.dp
private val SNACKBAR_HORIZONTAL_PADDING = 16.dp
private val SNACKBAR_VERTICAL_PADDING = 11.dp
private val SNACKBAR_CONTENT_SPACING = 10.dp
private val SNACKBAR_ICON_SIZE = 20.dp
