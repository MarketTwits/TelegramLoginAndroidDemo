package com.markettwits.devx.tgsignin.ui.component

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Uses the system's haptic patterns and respects the user's touch feedback setting. */
class AppHaptics(private val view: View) {
    fun selection() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    fun action() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    fun confirmation() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK
    )
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(view) }
}
