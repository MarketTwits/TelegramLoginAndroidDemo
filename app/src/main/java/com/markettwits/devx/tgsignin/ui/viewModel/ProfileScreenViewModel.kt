package com.markettwits.devx.tgsignin.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.ui.model.toUserMessageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileScreenViewModel(
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {
    val state = authenticationRepository.state
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private var actionJob: Job? = null

    fun updateDraft(draft: ProfileDraft) {
        viewModelScope.launch { authenticationRepository.saveDraft(draft) }
    }

    fun saveProfile(draft: ProfileDraft) {
        if (_isSaving.value || !draft.isValid) return
        actionJob = viewModelScope.launch {
            _isSaving.value = true
            authenticationRepository.saveProfile(draft)
                .onFailure { _messages.emit(R.string.error_profile_save) }
            _isSaving.value = false
        }
    }

    fun editProfile() {
        viewModelScope.launch { authenticationRepository.beginProfileEditing() }
    }

    fun cancelProfileEditing() {
        viewModelScope.launch { authenticationRepository.cancelProfileEditing() }
    }

    fun completeWelcome() {
        viewModelScope.launch { authenticationRepository.completeProfileWelcome() }
    }

    fun deleteProfile() {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            authenticationRepository.deleteProfile()
                .onFailure { _messages.emit(R.string.error_profile_delete) }
        }
    }

    fun logout() {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            runCatching { authenticationRepository.logout() }
                .onFailure { _messages.emit(it.toUserMessageRes()) }
        }
    }
}
