package com.markettwits.devx.tgsignin.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.ui.model.toUserMessageRes
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
    val isSaving: Boolean = false
)

sealed interface ProfileUiEvent {
    data class ShowMessage(@StringRes val messageRes: Int) : ProfileUiEvent
}

class ProfileViewModel(
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {
    private val _isSaving = MutableStateFlow(false)
    val uiState: StateFlow<ProfileUiState> = combine(
        authenticationRepository.state,
        _isSaving
    ) { state, isSaving ->
        ProfileUiState(authenticationState = state, isSaving = isSaving)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ProfileUiState(authenticationRepository.state.value)
    )
    private val _events = MutableSharedFlow<ProfileUiEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<ProfileUiEvent> = _events.asSharedFlow()
    private var actionJob: Job? = null

    fun updateDraft(draft: ProfileDraft) {
        viewModelScope.launch { authenticationRepository.saveDraft(draft) }
    }

    fun saveProfile(draft: ProfileDraft) {
        if (_isSaving.value || !draft.isValid) return
        actionJob = viewModelScope.launch {
            _isSaving.value = true
            authenticationRepository.saveProfile(draft)
                .onFailure { showMessage(R.string.error_profile_save) }
            _isSaving.value = false
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
            authenticationRepository.deleteAccount()
                .onFailure { showMessage(R.string.error_account_delete) }
        }
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
