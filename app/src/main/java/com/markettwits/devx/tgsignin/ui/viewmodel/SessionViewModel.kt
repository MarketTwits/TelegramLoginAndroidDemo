package com.markettwits.devx.tgsignin.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import com.markettwits.devx.tgsignin.data.model.UserSessionInfo
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionUiState {
    data object Loading : SessionUiState
    data class Refreshing(val sessions: List<UserSessionInfo>) : SessionUiState
    data class Ready(val sessions: List<UserSessionInfo>) : SessionUiState
    data class Revoking(val sessionId: String, val sessions: List<UserSessionInfo>) : SessionUiState
    data class RevokingOthers(val sessions: List<UserSessionInfo>) : SessionUiState
    data class ReauthenticationRequired(
        val sessions: List<UserSessionInfo>,
        val verificationFailed: Boolean = false
    ) : SessionUiState
    data class Reauthenticating(val sessions: List<UserSessionInfo>) : SessionUiState
    data class Error(val sessions: List<UserSessionInfo>, val error: Throwable) : SessionUiState
}

val SessionUiState.sessions: List<UserSessionInfo>
    get() = when (this) {
        SessionUiState.Loading -> emptyList()
        is SessionUiState.Refreshing -> sessions
        is SessionUiState.Ready -> sessions
        is SessionUiState.Revoking -> sessions
        is SessionUiState.RevokingOthers -> sessions
        is SessionUiState.ReauthenticationRequired -> sessions
        is SessionUiState.Reauthenticating -> sessions
        is SessionUiState.Error -> sessions
    }

val SessionUiState.isBusy: Boolean
    get() = this is SessionUiState.Loading || this is SessionUiState.Refreshing ||
        this is SessionUiState.Revoking || this is SessionUiState.RevokingOthers ||
        this is SessionUiState.Reauthenticating

private sealed interface PendingSessionAction {
    data class Revoke(val session: UserSessionInfo) : PendingSessionAction
    data object RevokeOthers : PendingSessionAction
}

class SessionViewModel(
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    private var pendingAction: PendingSessionAction? = null
    private var telegramVerificationPending = false
    private var observedReauthenticationVersion = authenticationRepository.reauthenticationVersion.value

    init {
        loadSessions()
        viewModelScope.launch {
            authenticationRepository.reauthenticationVersion.collect { version ->
                if (version != observedReauthenticationVersion) {
                    observedReauthenticationVersion = version
                    pendingAction?.let { action -> runAction(action) }
                }
            }
        }
    }

    fun loadSessions() {
        if (_uiState.value.isBusy && _uiState.value !is SessionUiState.Loading) return
        viewModelScope.launch { refreshSessions() }
    }

    fun revokeSession(session: UserSessionInfo) {
        if (_uiState.value.isBusy || pendingAction != null) return
        _uiState.value = SessionUiState.Revoking(session.id, _uiState.value.sessions)
        viewModelScope.launch { runAction(PendingSessionAction.Revoke(session)) }
    }

    fun revokeOtherSessions() {
        if (_uiState.value.isBusy || pendingAction != null) return
        _uiState.value = SessionUiState.RevokingOthers(_uiState.value.sessions)
        viewModelScope.launch { runAction(PendingSessionAction.RevokeOthers) }
    }

    fun verifyWithPasskey(context: Context) {
        val current = _uiState.value as? SessionUiState.ReauthenticationRequired ?: return
        telegramVerificationPending = false
        _uiState.value = SessionUiState.Reauthenticating(current.sessions)
        viewModelScope.launch {
            authenticationRepository.reauthenticateWithPasskey(context)
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = SessionUiState.ReauthenticationRequired(
                        current.sessions,
                        verificationFailed = error !is AuthenticationError.PasskeyCancelled
                    )
                }
        }
    }

    fun verifyWithTelegram(context: Context) {
        val current = _uiState.value as? SessionUiState.ReauthenticationRequired ?: return
        try {
            authenticationRepository.startTelegramReauthentication(context)
            telegramVerificationPending = true
            _uiState.value = SessionUiState.Reauthenticating(current.sessions)
        } catch (_: Throwable) {
            _uiState.value = SessionUiState.ReauthenticationRequired(current.sessions, verificationFailed = true)
        }
    }

    fun onResume() {
        if (!telegramVerificationPending) return
        telegramVerificationPending = false
        authenticationRepository.cancelTelegramReauthentication()
        val current = _uiState.value as? SessionUiState.Reauthenticating ?: return
        _uiState.value = SessionUiState.ReauthenticationRequired(current.sessions)
    }

    fun cancelPendingAction() {
        pendingAction = null
        telegramVerificationPending = false
        authenticationRepository.cancelTelegramReauthentication()
        _uiState.value = SessionUiState.Ready(_uiState.value.sessions)
    }

    private suspend fun runAction(action: PendingSessionAction) {
        telegramVerificationPending = false
        val sessions = _uiState.value.sessions
        when (action) {
            is PendingSessionAction.Revoke ->
                _uiState.value = SessionUiState.Revoking(action.session.id, sessions)
            PendingSessionAction.RevokeOthers ->
                _uiState.value = SessionUiState.RevokingOthers(sessions)
        }
        val result = when (action) {
            is PendingSessionAction.Revoke -> authenticationRepository.revokeSessionById(
                action.session.id, action.session.current
            )
            PendingSessionAction.RevokeOthers -> authenticationRepository.revokeOtherSessions().map { Unit }
        }
        result.onSuccess {
            pendingAction = null
            if (action is PendingSessionAction.Revoke && action.session.current) {
                _uiState.value = SessionUiState.Ready(emptyList())
            } else {
                _uiState.value = SessionUiState.Ready(
                    when (action) {
                        is PendingSessionAction.Revoke -> sessions.filterNot { it.id == action.session.id }
                        PendingSessionAction.RevokeOthers -> sessions.filter { it.current }
                    }
                )
                refreshSessions()
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            if (error is AuthenticationError.ReauthenticationRequired) {
                pendingAction = action
                _uiState.value = SessionUiState.ReauthenticationRequired(sessions)
            } else {
                pendingAction = null
                _uiState.value = SessionUiState.Error(sessions, error)
            }
        }
    }

    private suspend fun refreshSessions() {
        val previous = _uiState.value.sessions
        _uiState.value = if (previous.isEmpty()) SessionUiState.Loading else SessionUiState.Refreshing(previous)
        authenticationRepository.listSessions()
            .onSuccess { _uiState.value = SessionUiState.Ready(it) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.value = SessionUiState.Error(previous, error)
            }
    }
}
