package com.markettwits.devx.tgsignin.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.model.sessionOrNull
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.data.repository.ReauthenticationScope
import com.markettwits.devx.tgsignin.ui.model.toUserMessageRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val authenticationState: RootAuthenticationState = RootAuthenticationState.Loading,
    val isSaving: Boolean = false,
    val deleteAccountReauthRequired: Boolean = false,
    val isReauthenticatingAccountDeletion: Boolean = false,
    val accountDeletionVerificationFailed: Boolean = false
)

sealed interface ProfileUiEvent {
    data class ShowMessage(@StringRes val messageRes: Int) : ProfileUiEvent
}

private data class DeleteAccountUiState(
    val reauthRequired: Boolean = false,
    val isReauthenticating: Boolean = false,
    val verificationFailed: Boolean = false
)

class ProfileViewModel(
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {
    private val _isSaving = MutableStateFlow(false)
    private val _deleteState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<ProfileUiState> = combine(
        authenticationRepository.state,
        _isSaving,
        _deleteState
    ) { state, isSaving, deleteState ->
        ProfileUiState(
            authenticationState = state,
            isSaving = isSaving,
            deleteAccountReauthRequired = deleteState.reauthRequired,
            isReauthenticatingAccountDeletion = deleteState.isReauthenticating,
            accountDeletionVerificationFailed = deleteState.verificationFailed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ProfileUiState(authenticationRepository.state.value)
    )
    private val _events = MutableSharedFlow<ProfileUiEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<ProfileUiEvent> = _events.asSharedFlow()
    private var actionJob: Job? = null
    private var pendingDeleteAfterReauth = false
    private var telegramVerificationPending = false
    private var observedReauthenticationVersion = authenticationRepository.reauthenticationVersion.value
    private var observedReauthenticationFailureVersion = authenticationRepository.reauthenticationFailureVersion.value
    private var observedSessionKey: Pair<String, String>? =
        authenticationRepository.state.value.sessionOrNull?.let { it.account.id to it.accessToken }

    init {
        viewModelScope.launch {
            authenticationRepository.state.collect { authState ->
                val session = authState.sessionOrNull
                val sessionKey = session?.let { it.account.id to it.accessToken }
                if (session == null && observedSessionKey != null && authState !is RootAuthenticationState.Loading) {
                    observedSessionKey = null
                    resetDeleteAccountState()
                } else if (sessionKey != null && sessionKey != observedSessionKey) {
                    val hadSession = observedSessionKey != null
                    observedSessionKey = sessionKey
                    if (hadSession) resetDeleteAccountState()
                }
            }
        }
        viewModelScope.launch {
            authenticationRepository.reauthenticationVersion.collect { version ->
                if (version != observedReauthenticationVersion) {
                    observedReauthenticationVersion = version
                    val scope = authenticationRepository.lastReauthenticationScope
                    if (pendingDeleteAfterReauth && (scope == null || scope == ReauthenticationScope.ACCOUNT)) {
                        telegramVerificationPending = false
                        executeDeleteAccount()
                    }
                }
            }
        }
        viewModelScope.launch {
            authenticationRepository.reauthenticationFailureVersion.collect { version ->
                if (version != observedReauthenticationFailureVersion) {
                    observedReauthenticationFailureVersion = version
                    val scope = authenticationRepository.lastReauthenticationScope
                    if (telegramVerificationPending && (scope == null || scope == ReauthenticationScope.ACCOUNT)) {
                        telegramVerificationPending = false
                        _deleteState.value = DeleteAccountUiState(
                            reauthRequired = true,
                            isReauthenticating = false,
                            verificationFailed = true
                        )
                    }
                }
            }
        }
    }

    fun updateDraft(draft: ProfileDraft) {
        viewModelScope.launch { authenticationRepository.saveDraft(draft) }
    }

    fun saveProfile(draft: ProfileDraft) {
        if (_isSaving.value || !draft.isValid) return
        actionJob = viewModelScope.launch {
            _isSaving.value = true
            try {
                authenticationRepository.saveProfile(draft)
                    .onFailure { showMessage(R.string.error_profile_save) }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun editProfile() {
        viewModelScope.launch { authenticationRepository.beginProfileEditing() }
    }

    fun cancelProfileEditing() {
        viewModelScope.launch { authenticationRepository.cancelProfileEditing() }
    }

    fun updateEmoji(selection: ProfileEmojiSelection) {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            authenticationRepository.updateProfileEmoji(selection)
                .onFailure { showMessage(R.string.error_profile_save) }
        }
    }

    fun deleteAccount() {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            executeDeleteAccount()
        }
    }

    private suspend fun executeDeleteAccount() {
        _deleteState.value = _deleteState.value.copy(isReauthenticating = false, verificationFailed = false)
        authenticationRepository.deleteAccount()
            .onSuccess {
                resetDeleteAccountState()
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                if (error is AuthenticationError.ReauthenticationRequired) {
                    pendingDeleteAfterReauth = true
                    _deleteState.value = DeleteAccountUiState(reauthRequired = true)
                } else {
                    resetDeleteAccountState()
                    showMessage(R.string.error_account_delete)
                }
            }
    }

    fun verifyDeleteAccountWithPasskey(context: Context) {
        if (!_deleteState.value.reauthRequired || _deleteState.value.isReauthenticating) return
        telegramVerificationPending = false
        pendingDeleteAfterReauth = true
        _deleteState.value = DeleteAccountUiState(reauthRequired = true, isReauthenticating = true)
        actionJob = viewModelScope.launch {
            authenticationRepository.reauthenticateWithPasskey(context, ReauthenticationScope.ACCOUNT)
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _deleteState.value = DeleteAccountUiState(
                        reauthRequired = true,
                        isReauthenticating = false,
                        verificationFailed = error !is AuthenticationError.PasskeyCancelled
                    )
                }
        }
    }

    fun verifyDeleteAccountWithTelegram(context: Context) {
        if (!_deleteState.value.reauthRequired || _deleteState.value.isReauthenticating) return
        pendingDeleteAfterReauth = true
        try {
            authenticationRepository.startTelegramReauthentication(context, ReauthenticationScope.ACCOUNT)
            telegramVerificationPending = true
            _deleteState.value = DeleteAccountUiState(reauthRequired = true, isReauthenticating = true)
        } catch (_: Throwable) {
            _deleteState.value = DeleteAccountUiState(
                reauthRequired = true,
                isReauthenticating = false,
                verificationFailed = true
            )
        }
    }

    fun onResume() {
        if (authenticationRepository.isTelegramCallbackInFlight.value) return
        if (!telegramVerificationPending) return
        telegramVerificationPending = false
        authenticationRepository.cancelTelegramReauthentication()
        if (_deleteState.value.isReauthenticating) {
            _deleteState.value = DeleteAccountUiState(reauthRequired = true, isReauthenticating = false)
        }
    }

    fun cancelDeleteAccountReauth() {
        if (telegramVerificationPending) {
            authenticationRepository.cancelTelegramReauthentication()
        }
        resetDeleteAccountState()
    }

    private fun resetDeleteAccountState() {
        pendingDeleteAfterReauth = false
        telegramVerificationPending = false
        _deleteState.value = DeleteAccountUiState()
    }

    fun logout() {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            runCatching { authenticationRepository.logout() }
                .onFailure { showMessage(it.toUserMessageRes()) }
        }
    }

    private suspend fun showMessage(messageRes: Int) {
        _events.emit(ProfileUiEvent.ShowMessage(messageRes))
    }
}
