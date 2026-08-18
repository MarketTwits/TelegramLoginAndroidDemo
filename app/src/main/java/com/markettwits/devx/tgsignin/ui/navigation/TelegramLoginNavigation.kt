package com.markettwits.devx.tgsignin.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import com.markettwits.devx.tgsignin.ui.screen.BloomProfileScreen
import com.markettwits.devx.tgsignin.ui.screen.LoginScreen
import com.markettwits.devx.tgsignin.ui.screen.ProfileSetupScreen
import com.markettwits.devx.tgsignin.ui.viewmodel.LoginUiState
import com.markettwits.devx.tgsignin.ui.viewmodel.ProfileUiState

@Composable
fun TelegramLoginNavigation(
    profileUiState: ProfileUiState,
    loginUiState: LoginUiState,
    snackbarHostState: SnackbarHostState,
    onLogin: () -> Unit,
    onScopesChanged: (Set<TelegramScope>) -> Unit,
    onDraftChanged: (ProfileDraft) -> Unit,
    onSaveProfile: (ProfileDraft) -> Unit,
    onCancelProfileEditing: () -> Unit,
    onEmojiChanged: (ProfileEmojiSelection) -> Unit,
    onDeleteAccount: () -> Unit,
    onModalVisibilityChanged: (Boolean) -> Unit
) {
    when (val state = profileUiState.authenticationState) {
        is RootAuthenticationState.Authenticated -> BloomProfileScreen(
            session = state.session,
            isOffline = state.isOffline,
            onEmojiChanged = onEmojiChanged,
            onDelete = onDeleteAccount,
            onModalVisibilityChanged = onModalVisibilityChanged
        )

        is RootAuthenticationState.OnboardingRequired -> ProfileSetupScreen(
            session = state.session,
            draft = state.draft,
            isSaving = profileUiState.isSaving,
            isOffline = state.isOffline,
            onDraftChanged = onDraftChanged,
            onSave = onSaveProfile,
            onCancel = onCancelProfileEditing
        )

        RootAuthenticationState.Loading -> SessionRestoringScreen()

        is RootAuthenticationState.Unauthenticated,
        is RootAuthenticationState.RecoverableError -> LoginScreen(
            uiState = loginUiState,
            snackbarHostState = snackbarHostState,
            sessionExpired = (state as? RootAuthenticationState.Unauthenticated)
                ?.sessionExpired == true,
            onScopesChanged = onScopesChanged,
            onLogin = onLogin,
            onModalVisibilityChanged = onModalVisibilityChanged
        )
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
