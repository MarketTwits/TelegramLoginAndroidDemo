package com.markettwits.devx.tgsignin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.data.model.AppRelease
import com.markettwits.devx.tgsignin.data.model.AppUpdateAvailability
import com.markettwits.devx.tgsignin.data.repository.AppUpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppUpdateUiState {
    data object Checking : AppUpdateUiState
    data object Hidden : AppUpdateUiState
    data class Available(val release: AppRelease) : AppUpdateUiState
}

class AppUpdateViewModel(
    private val repository: AppUpdateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Checking)
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()
    private var checkJob: Job? = null
    private var hasChecked = false

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (hasChecked || checkJob?.isActive == true) return
        hasChecked = true
        checkJob = viewModelScope.launch {
            _uiState.value = when (val availability = repository.checkForUpdate()) {
                is AppUpdateAvailability.Available -> AppUpdateUiState.Available(availability.release)
                AppUpdateAvailability.UpToDate,
                AppUpdateAvailability.Unavailable -> AppUpdateUiState.Hidden
            }
        }
    }

    fun dismissUpdate() {
        _uiState.value = AppUpdateUiState.Hidden
    }
}
