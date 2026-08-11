package com.markettwits.devx.tgsignin.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.markettwits.devx.tgsignin.data.model.TelegramUser
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.ui.model.toUserMessageRes

class ProfileScreenViewModel(
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {
    val user: StateFlow<TelegramUser?> = authenticationRepository.currentUser
    val isSessionRestored: StateFlow<Boolean> = authenticationRepository.isSessionRestored
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = MESSAGE_BUFFER_CAPACITY)
    val messages = _messages.asSharedFlow()
    private var logoutJob: Job? = null

    fun logout() {
        if (logoutJob?.isActive == true) return
        logoutJob = viewModelScope.launch {
            runCatching { authenticationRepository.logout() }
                .onFailure { error -> _messages.emit(error.toUserMessageRes()) }
        }
    }

    private companion object {
        const val MESSAGE_BUFFER_CAPACITY = 1
    }
}
