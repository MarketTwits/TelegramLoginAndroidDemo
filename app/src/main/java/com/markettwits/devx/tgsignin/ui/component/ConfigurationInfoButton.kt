package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.R

@Composable
fun ConfigurationInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasError: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    BadgedBox(
        badge = {
            if (hasError) {
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    androidx.compose.material3.Text(stringResource(R.string.error_symbol))
                }
            }
        },
        modifier = modifier
    ) {
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.shadow(3.dp, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(
                    if (hasError) {
                        R.string.configuration_info_error_description
                    } else {
                        R.string.configuration_info_description
                    }
                )
            )
        }
    }
}
