package com.markettwits.devx.tgsignin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.data.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = TelegramBlue,
    onPrimary = TelegramSurface,
    primaryContainer = TelegramBlueDark,
    onPrimaryContainer = TelegramSurface,
    secondary = TelegramBlue,
    onSecondary = TelegramSurface,
    background = TelegramDarkCanvas,
    surface = TelegramDarkSurface,
    surfaceVariant = TelegramDarkVariant,
    onSurfaceVariant = Color(0xFF9DB2C7),
    outline = TelegramDarkOutline,
    onBackground = TelegramSurface,
    onSurface = TelegramSurface,
    inverseSurface = TelegramDarkVariant,
    inverseOnSurface = TelegramSurface,
    inversePrimary = TelegramBlue,
    error = TelegramError,
    onError = TelegramSurface
)

private val LightColorScheme = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = TelegramSurface,
    primaryContainer = TelegramBlueLight,
    onPrimaryContainer = TelegramBlueDark,
    background = TelegramCanvas,
    onBackground = TelegramInk,
    surface = TelegramSurface,
    onSurface = TelegramInk,
    surfaceVariant = TelegramSurfaceSecondary,
    onSurfaceVariant = TelegramMuted,
    outline = Color(0xFFE1E2E3),
    inverseSurface = TelegramInk,
    inverseOnSurface = TelegramSurface,
    inversePrimary = TelegramBlue,
    error = TelegramError,
    onError = TelegramSurface
)

private val TelegramShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(48.dp)
)

@Composable
fun TelegramLoginDemoTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
        AppThemeMode.System, AppThemeMode.Expressive -> systemDark
    }
    val context = LocalContext.current
    val colorScheme = when {
        themeMode == AppThemeMode.Expressive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = if (themeMode == AppThemeMode.Expressive) ExpressiveShapes else TelegramShapes,
        content = content
    )
}
