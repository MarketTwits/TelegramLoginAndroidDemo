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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.android.ext.android.inject
import com.markettwits.devx.tgsignin.data.model.AppThemeMode
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.component.AppearanceToggleButton
import com.markettwits.devx.tgsignin.ui.component.ConfigurationInfoButton
import com.markettwits.devx.tgsignin.ui.model.AppLinkVerificationUiState
import com.markettwits.devx.tgsignin.ui.model.BackendReadinessUiState
import com.markettwits.devx.tgsignin.ui.screen.ConfigurationDialog
import com.markettwits.devx.tgsignin.ui.screen.LoginScreen
import com.markettwits.devx.tgsignin.ui.screen.ProfileScreen
import com.markettwits.devx.tgsignin.ui.theme.AppThemeAnimationScope
import com.markettwits.devx.tgsignin.ui.theme.TelegramLoginDemoTheme
import com.markettwits.devx.tgsignin.ui.theme.rememberAppThemeAnimationState
import com.markettwits.devx.tgsignin.ui.viewModel.AppearanceViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.AppLinkVerificationViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.BackendReadinessViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.LoginScreenViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.ProfileScreenViewModel

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
            val user by profileViewModel.user.collectAsState()
            val isSessionRestored by profileViewModel.isSessionRestored.collectAsState()
            val backendReadinessState by backendReadinessViewModel.uiState.collectAsState()
            val appLinkVerificationState by appLinkVerificationViewModel.uiState.collectAsState()
            var showLoginConfiguration by rememberSaveable { mutableStateOf(false) }
            val systemDark = isSystemInDarkTheme()
            val expressiveAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val animationState = rememberAppThemeAnimationState(
                themeModes = appearanceViewModel.themeMode,
                animationSpec = tween(durationMillis = THEME_ANIMATION_DURATION_MS),
                useDynamicContent = true
            )
            val animatedThemeMode = animationState.uiMode

            Box(Modifier.fillMaxSize()) {
                AppThemeAnimationScope(state = animationState) {
                    TelegramLoginDemoTheme(themeMode = animatedThemeMode) {
                        Box(Modifier.fillMaxSize()) {
                            val loginUiState by loginViewModel.uiState.collectAsState()
                            val currentUser = user
                            when {
                                currentUser != null -> {
                                    ProfileScreen(
                                        user = currentUser,
                                        telegramConfig = telegramLoginConfig,
                                        backendReadinessState = backendReadinessState,
                                        appLinkVerificationState = appLinkVerificationState,
                                        messages = profileViewModel.messages,
                                        onLogout = profileViewModel::logout,
                                        onRetryBackendReadiness = backendReadinessViewModel::checkReadiness,
                                        onRetryAppLinkVerification =
                                            appLinkVerificationViewModel::checkVerification
                                    )
                                }
                                !isSessionRestored -> SessionRestoringScreen()
                                else -> {
                                    LoginScreen(
                                        uiState = loginUiState,
                                        onScopesChanged = loginViewModel::updateScopes,
                                        onLogin = { loginViewModel.login(this@MainActivity) }
                                    )
                                }
                            }
                        }
                    }
                }
                TelegramLoginDemoTheme(themeMode = animatedThemeMode) {
                    if (isSessionRestored && user == null) {
                        ConfigurationInfoButton(
                            hasError = backendReadinessState is BackendReadinessUiState.Error ||
                                appLinkVerificationState is AppLinkVerificationUiState.Error,
                            onClick = { showLoginConfiguration = true },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(top = 6.dp, start = 8.dp)
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
                    if (showLoginConfiguration && user == null) {
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
                }
            }
        }
    }

    private companion object {
        const val THEME_ANIMATION_DURATION_MS = 700
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
