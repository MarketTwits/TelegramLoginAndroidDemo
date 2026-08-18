package com.markettwits.devx.tgsignin.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.ui.model.toUserMessageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val requestedScopes: Set<TelegramScope> = DEFAULT_TELEGRAM_SCOPES,
    val loginState: LoginState = LoginState.Ready
)

sealed interface LoginState {
    data object Ready : LoginState
    data object AwaitingConfirmation : LoginState
    data object Verifying : LoginState
    data object Cancelled : LoginState
    data class Error(@StringRes val messageRes: Int) : LoginState
}

private val DEFAULT_TELEGRAM_SCOPES = setOf(TelegramScope.Profile, TelegramScope.Phone)

class LoginViewModel(
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateScopes(scopes: Set<TelegramScope>) {
        if (uiState.value.loginState.isInProgress) return
        _uiState.update { it.copy(requestedScopes = scopes + TelegramScope.Profile) }
    }

    fun login(context: Context) {
        if (uiState.value.loginState.isInProgress) return
        val scopes = uiState.value.requestedScopes
        _uiState.update { it.copy(loginState = LoginState.AwaitingConfirmation) }
        runCatching { authenticationRepository.startTelegramLogin(context, scopes) }
            .onFailure { error -> _uiState.update { it.copy(loginState = LoginState.Error(error.toUserMessageRes())) } }
    }

    fun consumeCallback(uri: Uri) {
        if (!authenticationRepository.isTelegramCallback(uri)) return
        if (uiState.value.loginState is LoginState.Verifying) return
        viewModelScope.launch {
            _uiState.update { it.copy(loginState = LoginState.Verifying) }
            authenticationRepository.completeTelegramLogin(uri)
                .onSuccess { _uiState.update { it.copy(loginState = LoginState.Ready) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loginState = LoginState.Error(error.toUserMessageRes()))
                    }
                }
        }
    }

    /** Called when the user returns to the app without the Telegram callback. */
    fun cancelIfAwaitingCallback() {
        if (uiState.value.loginState is LoginState.AwaitingConfirmation) {
            _uiState.update { it.copy(loginState = LoginState.Cancelled) }
        }
    }

    private val LoginState.isInProgress: Boolean
        get() = this is LoginState.AwaitingConfirmation || this is LoginState.Verifying
}
