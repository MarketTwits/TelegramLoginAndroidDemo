package com.markettwits.devx.tgsignin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.data.model.UserSessionInfo
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionUiState {
    data object Loading : SessionUiState
    data class Ready(val sessions: List<UserSessionInfo>) : SessionUiState
    data class Revoking(val sessionId: String, val sessions: List<UserSessionInfo>) : SessionUiState
    data class RevokingOthers(val sessions: List<UserSessionInfo>) : SessionUiState
    data class Error(val sessions: List<UserSessionInfo>, val message: String) : SessionUiState
}

val SessionUiState.sessions: List<UserSessionInfo>
    get() = when (this) {
        is SessionUiState.Loading -> emptyList()
        is SessionUiState.Ready -> sessions
        is SessionUiState.Revoking -> sessions
        is SessionUiState.RevokingOthers -> sessions
        is SessionUiState.Error -> sessions
    }

val SessionUiState.isBusy: Boolean
    get() = this is SessionUiState.Loading ||
        this is SessionUiState.Revoking ||
        this is SessionUiState.RevokingOthers

class SessionViewModel(
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            val currentSessions = _uiState.value.sessions
            _uiState.value = if (currentSessions.isEmpty()) SessionUiState.Loading else SessionUiState.Ready(currentSessions)
            authenticationRepository.listSessions()
                .onSuccess { sessions ->
                    _uiState.value = SessionUiState.Ready(sessions)
                }
                .onFailure { error ->
                    _uiState.value = SessionUiState.Error(currentSessions, error.message ?: "Failed to load sessions")
                }
        }
    }

    fun revokeSession(session: UserSessionInfo) {
        viewModelScope.launch {
            val currentSessions = _uiState.value.sessions
            _uiState.value = SessionUiState.Revoking(session.id, currentSessions)
            authenticationRepository.revokeSessionById(session.id, session.current)
                .onSuccess {
                    if (!session.current) {
                        loadSessions()
                    }
                }
                .onFailure { error ->
                    _uiState.value = SessionUiState.Error(currentSessions, error.message ?: "Failed to revoke session")
                }
        }
    }

    fun revokeOtherSessions() {
        viewModelScope.launch {
            val currentSessions = _uiState.value.sessions
            _uiState.value = SessionUiState.RevokingOthers(currentSessions)
            authenticationRepository.revokeOtherSessions()
                .onSuccess {
                    loadSessions()
                }
                .onFailure { error ->
                    _uiState.value = SessionUiState.Error(currentSessions, error.message ?: "Failed to revoke other sessions")
                }
        }
    }
}
