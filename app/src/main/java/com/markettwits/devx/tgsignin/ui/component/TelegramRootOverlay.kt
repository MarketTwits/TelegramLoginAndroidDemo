package com.markettwits.devx.tgsignin.ui.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AppThemeMode
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.repository.isTrustedReleaseUrl
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.screen.ConfigurationDialog
import com.markettwits.devx.tgsignin.ui.theme.AppThemeAnimationState
import com.markettwits.devx.tgsignin.ui.viewmodel.AppLinkVerificationUiState
import com.markettwits.devx.tgsignin.ui.viewmodel.AppUpdateUiState
import com.markettwits.devx.tgsignin.ui.viewmodel.BackendReadinessUiState
import kotlinx.coroutines.launch

private enum class RootDialog {
    Configuration,
    Logout
}

@Composable
fun BoxScope.TelegramRootOverlay(
    authenticationState: RootAuthenticationState,
    themeMode: AppThemeMode,
    animationState: AppThemeAnimationState,
    backendReadinessState: BackendReadinessUiState,
    appLinkVerificationState: AppLinkVerificationUiState,
    appUpdateState: AppUpdateUiState,
    telegramLoginConfig: TelegramLoginConfig,
    snackbarHostState: SnackbarHostState,
    screenModalVisible: Boolean,
    onThemeSelected: (AppThemeMode) -> Unit,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    onRetryBackendReadiness: () -> Unit,
    onRetryAppLinkVerification: () -> Unit,
    onDismissUpdate: () -> Unit
) {
    var rootDialog by rememberSaveable { mutableStateOf<RootDialog?>(null) }
    val resources = LocalResources.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (authenticationState is RootAuthenticationState.Authenticated) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 6.dp, start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelegramIconAction(
                icon = Icons.Outlined.Edit,
                contentDescription = resources.getString(R.string.bloom_edit_profile),
                onClick = onEditProfile
            )
            TelegramIconAction(
                icon = Icons.AutoMirrored.Outlined.Logout,
                contentDescription = resources.getString(R.string.logout_account_description),
                onClick = { rootDialog = RootDialog.Logout }
            )
        }
    }

    if (authenticationState !is RootAuthenticationState.Loading) {
        ConfigurationInfoButton(
            hasError = backendReadinessState is BackendReadinessUiState.Error ||
                    appLinkVerificationState is AppLinkVerificationUiState.Error,
            onClick = { rootDialog = RootDialog.Configuration },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 6.dp, end = 64.dp)
        )
    }

    AppearanceToggleButton(
        currentMode = themeMode,
        expressiveAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        systemDark = isSystemInDarkTheme(),
        animationState = animationState,
        onModeSelected = onThemeSelected,
        iconTint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 6.dp, end = 8.dp)
    )

    TelegramSnackbarHost(
        hostState = snackbarHostState,
        bottomContentPadding = authenticationState.snackbarBottomClearance(),
        modifier = Modifier.align(Alignment.BottomCenter)
    )

    when (rootDialog) {
        RootDialog.Configuration -> ConfigurationDialog(
            telegramConfig = telegramLoginConfig,
            backendReadinessState = backendReadinessState,
            appLinkVerificationState = appLinkVerificationState,
            onRetryBackendReadiness = onRetryBackendReadiness,
            onRetryAppLinkVerification = onRetryAppLinkVerification,
            onDismiss = { rootDialog = null }
        )

        RootDialog.Logout -> TelegramConfirmationDialog(
            title = resources.getString(R.string.logout_title),
            message = resources.getString(R.string.logout_confirmation),
            confirmText = resources.getString(R.string.logout_title),
            dismissText = resources.getString(R.string.cancel),
            onConfirm = {
                rootDialog = null
                onLogout()
            },
            onDismiss = { rootDialog = null },
            destructive = true
        )

        null -> Unit
    }

    val availableUpdate = appUpdateState as? AppUpdateUiState.Available
    if (
        availableUpdate != null &&
        authenticationState !is RootAuthenticationState.Loading &&
        rootDialog == null &&
        !screenModalVisible
    ) {
        TelegramUpdateBottomSheet(
            release = availableUpdate.release,
            onDownload = {
                if (context.openReleasePage(availableUpdate.release.releasePageUrl)) {
                    onDismissUpdate()
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showTelegramSnackbar(
                            message = resources.getString(R.string.update_open_failed),
                            isError = true
                        )
                    }
                }
            },
            onDismiss = onDismissUpdate
        )
    }
}

private fun RootAuthenticationState.snackbarBottomClearance() = when (this) {
    is RootAuthenticationState.Unauthenticated,
    is RootAuthenticationState.RecoverableError -> 92.dp

    is RootAuthenticationState.OnboardingRequired -> 60.dp
    is RootAuthenticationState.Authenticated,
    RootAuthenticationState.Loading -> 0.dp
}

private fun Context.openReleasePage(url: String): Boolean {
    if (!isTrustedReleaseUrl(url)) return false
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
