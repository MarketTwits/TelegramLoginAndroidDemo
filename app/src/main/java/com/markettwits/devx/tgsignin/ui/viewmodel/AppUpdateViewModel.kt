package com.markettwits.devx.tgsignin.ui.viewmodel

import android.os.SystemClock
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
    private var lastCheckStartedAtMillis: Long? = null
    private var dismissedVersionCode: Long? = null

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        val now = SystemClock.elapsedRealtime()
        val checkedRecently = lastCheckStartedAtMillis
            ?.let { startedAt -> now - startedAt in 0 until MIN_CHECK_INTERVAL_MILLIS }
            ?: false
        if (
            checkJob?.isActive == true ||
            checkedRecently
        ) return
        lastCheckStartedAtMillis = now
        checkJob = viewModelScope.launch {
            _uiState.value =
                when (val availability = repository.checkForUpdate(forceRefresh = true)) {
                    is AppUpdateAvailability.Available -> if (
                        availability.release.versionCode == dismissedVersionCode
                    ) {
                        AppUpdateUiState.Hidden
                    } else {
                        AppUpdateUiState.Available(availability.release)
                    }
                AppUpdateAvailability.UpToDate,
                AppUpdateAvailability.Unavailable -> AppUpdateUiState.Hidden
            }
        }
    }

    fun dismissUpdate() {
        dismissedVersionCode = (_uiState.value as? AppUpdateUiState.Available)
            ?.release
            ?.versionCode
        _uiState.value = AppUpdateUiState.Hidden
    }

    private companion object {
        const val MIN_CHECK_INTERVAL_MILLIS = 60_000L
    }
}
