package com.markettwits.devx.tgsignin

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.component.AppearanceToggleButton
import com.markettwits.devx.tgsignin.ui.component.ConfigurationInfoButton
import com.markettwits.devx.tgsignin.ui.component.TelegramConfirmationDialog
import com.markettwits.devx.tgsignin.ui.component.TelegramIconAction
import com.markettwits.devx.tgsignin.ui.component.TelegramSnackbarHost
import com.markettwits.devx.tgsignin.ui.component.showTelegramSnackbar
import com.markettwits.devx.tgsignin.ui.model.AppLinkVerificationUiState
import com.markettwits.devx.tgsignin.ui.model.BackendReadinessUiState
import com.markettwits.devx.tgsignin.ui.screen.BloomProfileScreen
import com.markettwits.devx.tgsignin.ui.screen.ConfigurationDialog
import com.markettwits.devx.tgsignin.ui.screen.LoginScreen
import com.markettwits.devx.tgsignin.ui.screen.ProfileSetupScreen
import com.markettwits.devx.tgsignin.ui.theme.AppThemeAnimationScope
import com.markettwits.devx.tgsignin.ui.theme.TelegramLoginDemoTheme
import com.markettwits.devx.tgsignin.ui.theme.rememberAppThemeAnimationState
import com.markettwits.devx.tgsignin.ui.viewModel.AppLinkVerificationViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.AppearanceViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.BackendReadinessViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.LoginScreenViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.ProfileScreenViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginScreenViewModel by viewModel()
    private val profileViewModel: ProfileScreenViewModel by viewModel()
    private val appearanceViewModel: AppearanceViewModel by viewModel()
    private val backendReadinessViewModel: BackendReadinessViewModel by viewModel()
    private val appLinkVerificationViewModel: AppLinkVerificationViewModel by viewModel()
    private val telegramLoginConfig: TelegramLoginConfig by inject()
    private var wentToBackgroundDuringLogin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeTelegramIntent(intent)
        setContent {
            val themeMode by appearanceViewModel.themeMode.collectAsState()
            val authenticationState by profileViewModel.state.collectAsState()
            val isSavingProfile by profileViewModel.isSaving.collectAsState()
            val backendReadinessState by backendReadinessViewModel.uiState.collectAsState()
            val appLinkVerificationState by appLinkVerificationViewModel.uiState.collectAsState()
            var showLoginConfiguration by rememberSaveable { mutableStateOf(false) }
            var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
            val snackbarHostState = remember { SnackbarHostState() }
            val resources = LocalResources.current
            val systemDark = isSystemInDarkTheme()
            val expressiveAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val animationState = rememberAppThemeAnimationState(
                themeModes = appearanceViewModel.themeMode,
                animationSpec = tween(durationMillis = THEME_ANIMATION_DURATION_MS),
                useDynamicContent = true
            )
            val animatedThemeMode = animationState.uiMode
            val snackbarBottomContentPadding = when (authenticationState) {
                is RootAuthenticationState.Unauthenticated,
                is RootAuthenticationState.RecoverableError -> LOGIN_BOTTOM_ACTION_CLEARANCE

                is RootAuthenticationState.OnboardingRequired -> SETUP_BOTTOM_ACTION_CLEARANCE
                is RootAuthenticationState.Authenticated,
                RootAuthenticationState.Loading -> 0.dp
            }

            androidx.compose.runtime.LaunchedEffect(profileViewModel.messages, resources) {
                profileViewModel.messages.collectLatest { messageRes ->
                    snackbarHostState.showTelegramSnackbar(
                        message = resources.getString(messageRes),
                        isError = true
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                AppThemeAnimationScope(state = animationState) {
                    TelegramLoginDemoTheme(themeMode = animatedThemeMode) {
                        Box(Modifier.fillMaxSize()) {
                            val loginUiState by loginViewModel.uiState.collectAsState()
                            when (val state = authenticationState) {
                                is RootAuthenticationState.Authenticated -> {
                                    BloomProfileScreen(
                                        session = state.session,
                                        isOffline = state.isOffline,
                                        onEmojiChanged = profileViewModel::updateEmoji,
                                        onDelete = profileViewModel::deleteAccount
                                    )
                                }
                                is RootAuthenticationState.OnboardingRequired -> {
                                    ProfileSetupScreen(
                                        session = state.session,
                                        draft = state.draft,
                                        isSaving = isSavingProfile,
                                        isOffline = state.isOffline,
                                        onDraftChanged = profileViewModel::updateDraft,
                                        onSave = profileViewModel::saveProfile,
                                        onCancel = profileViewModel::cancelProfileEditing
                                    )
                                }
                                RootAuthenticationState.Loading -> SessionRestoringScreen()
                                is RootAuthenticationState.Unauthenticated,
                                is RootAuthenticationState.RecoverableError -> {
                                    LoginScreen(
                                        uiState = loginUiState,
                                        snackbarHostState = snackbarHostState,
                                        sessionExpired = (state as? RootAuthenticationState.Unauthenticated)
                                            ?.sessionExpired == true,
                                        onScopesChanged = loginViewModel::updateScopes,
                                        onLogin = { loginViewModel.login(this@MainActivity) }
                                    )
                                }
                            }
                        }
                    }
                }
                TelegramLoginDemoTheme(themeMode = animatedThemeMode) {
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
                                contentDescription = getString(R.string.bloom_edit_profile),
                                onClick = profileViewModel::editProfile
                            )
                            TelegramIconAction(
                                icon = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = getString(R.string.logout_account_description),
                                onClick = { showLogoutConfirmation = true }
                            )
                        }
                    }
                    if (authenticationState !is RootAuthenticationState.Loading) {
                        ConfigurationInfoButton(
                            hasError = backendReadinessState is BackendReadinessUiState.Error ||
                                appLinkVerificationState is AppLinkVerificationUiState.Error,
                            onClick = { showLoginConfiguration = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 6.dp, end = 64.dp)
                        )
                    }
                    AppearanceToggleButton(
                        currentMode = themeMode,
                        expressiveAvailable = expressiveAvailable,
                        systemDark = systemDark,
                        animationState = animationState,
                        onModeSelected = appearanceViewModel::setThemeMode,
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 6.dp, end = 8.dp)
                    )
                    TelegramSnackbarHost(
                        hostState = snackbarHostState,
                        bottomContentPadding = snackbarBottomContentPadding,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    if (showLoginConfiguration) {
                        ConfigurationDialog(
                            telegramConfig = telegramLoginConfig,
                            backendReadinessState = backendReadinessState,
                            appLinkVerificationState = appLinkVerificationState,
                            onRetryBackendReadiness = backendReadinessViewModel::checkReadiness,
                            onRetryAppLinkVerification =
                                appLinkVerificationViewModel::checkVerification,
                            onDismiss = { showLoginConfiguration = false }
                        )
                    }
                    if (showLogoutConfirmation) {
                        TelegramConfirmationDialog(
                            title = getString(R.string.logout_title),
                            message = getString(R.string.logout_confirmation),
                            confirmText = getString(R.string.logout_title),
                            dismissText = getString(R.string.cancel),
                            onConfirm = {
                                showLogoutConfirmation = false
                                profileViewModel.logout()
                            },
                            onDismiss = { showLogoutConfirmation = false },
                            destructive = true
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val THEME_ANIMATION_DURATION_MS = 700
        val LOGIN_BOTTOM_ACTION_CLEARANCE = 92.dp
        val SETUP_BOTTOM_ACTION_CLEARANCE = 60.dp
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeTelegramIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        wentToBackgroundDuringLogin = true
    }

    override fun onResume() {
        super.onResume()
        if (wentToBackgroundDuringLogin) {
            loginViewModel.cancelIfAwaitingCallback()
            wentToBackgroundDuringLogin = false
        }
    }

    private fun consumeTelegramIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        loginViewModel.consumeCallback(uri)
        // An authorization code must not be replayed after an Activity recreation.
        intent.data = null
    }
}

@Composable
private fun SessionRestoringScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp
        )
    }
}
