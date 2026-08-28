package com.markettwits.devx.tgsignin.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.component.TelegramRootOverlay
import com.markettwits.devx.tgsignin.ui.component.showTelegramSnackbar
import com.markettwits.devx.tgsignin.ui.navigation.TelegramLoginNavigation
import com.markettwits.devx.tgsignin.ui.theme.AppThemeAnimationScope
import com.markettwits.devx.tgsignin.ui.theme.TelegramLoginDemoTheme
import com.markettwits.devx.tgsignin.ui.theme.rememberAppThemeAnimationState
import com.markettwits.devx.tgsignin.ui.viewmodel.AppLinkVerificationViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.AppUpdateViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.AppearanceViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.BackendReadinessViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.LoginViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.ProfileUiEvent
import com.markettwits.devx.tgsignin.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun TelegramLoginApp(
    loginViewModel: LoginViewModel,
    onLogin: () -> Unit,
    profileViewModel: ProfileViewModel = koinViewModel(),
    appearanceViewModel: AppearanceViewModel = koinViewModel(),
    backendReadinessViewModel: BackendReadinessViewModel = koinViewModel(),
    appLinkVerificationViewModel: AppLinkVerificationViewModel = koinViewModel(),
    appUpdateViewModel: AppUpdateViewModel = koinViewModel(),
    telegramLoginConfig: TelegramLoginConfig = koinInject()
) {
    val themeMode by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val loginUiState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val backendReadinessState by backendReadinessViewModel.uiState.collectAsStateWithLifecycle()
    val appLinkVerificationState by appLinkVerificationViewModel.uiState.collectAsStateWithLifecycle()
    val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    var screenModalVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val animationState = rememberAppThemeAnimationState(
        themeModes = appearanceViewModel.uiState,
        animationSpec = tween(durationMillis = THEME_ANIMATION_DURATION_MILLIS),
        useDynamicContent = true
    )

    LaunchedEffect(profileViewModel.events, resources) {
        profileViewModel.events.collectLatest { event ->
            when (event) {
                is ProfileUiEvent.ShowMessage -> snackbarHostState.showTelegramSnackbar(
                    message = resources.getString(event.messageRes),
                    isError = true
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppThemeAnimationScope(state = animationState) {
            TelegramLoginDemoTheme(themeMode = animationState.uiMode) {
                TelegramLoginNavigation(
                    profileUiState = profileUiState,
                    loginUiState = loginUiState,
                    snackbarHostState = snackbarHostState,
                    onLogin = onLogin,
                    onScopesChanged = loginViewModel::updateScopes,
                    onDraftChanged = profileViewModel::updateDraft,
                    onSaveProfile = profileViewModel::saveProfile,
                    onCancelProfileEditing = profileViewModel::cancelProfileEditing,
                    onEmojiChanged = profileViewModel::updateEmoji,
                    onDeleteAccount = profileViewModel::deleteAccount,
                    onModalVisibilityChanged = { screenModalVisible = it }
                )
            }
        }

        TelegramLoginDemoTheme(themeMode = animationState.uiMode) {
            TelegramRootOverlay(
                authenticationState = profileUiState.authenticationState,
                themeMode = themeMode,
                animationState = animationState,
                backendReadinessState = backendReadinessState,
                appLinkVerificationState = appLinkVerificationState,
                appUpdateState = appUpdateState,
                telegramLoginConfig = telegramLoginConfig,
                snackbarHostState = snackbarHostState,
                screenModalVisible = screenModalVisible,
                onThemeSelected = appearanceViewModel::setThemeMode,
                onEditProfile = profileViewModel::editProfile,
                onLogout = profileViewModel::logout,
                onRetryBackendReadiness = backendReadinessViewModel::checkReadiness,
                onRetryAppLinkVerification = appLinkVerificationViewModel::checkVerification,
                onDismissUpdate = appUpdateViewModel::dismissUpdate
            )
        }
    }
}

private const val THEME_ANIMATION_DURATION_MILLIS = 700
