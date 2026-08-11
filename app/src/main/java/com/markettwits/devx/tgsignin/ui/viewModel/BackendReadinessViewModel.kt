package com.markettwits.devx.tgsignin.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.BackendReadiness
import com.markettwits.devx.tgsignin.data.repository.BackendReadinessRepository
import com.markettwits.devx.tgsignin.ui.model.BackendReadinessUiState
import com.markettwits.devx.tgsignin.ui.model.toBackendReadinessMessageRes

class BackendReadinessViewModel(
    private val repository: BackendReadinessRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<BackendReadinessUiState>(BackendReadinessUiState.Checking)
    val uiState: StateFlow<BackendReadinessUiState> = _uiState.asStateFlow()
    private var checkJob: Job? = null

    init {
        checkReadiness()
    }

    fun checkReadiness() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _uiState.value = BackendReadinessUiState.Checking
            _uiState.value = repository.checkReadiness().fold(
                onSuccess = { readiness -> readiness.toUiState() },
                onFailure = { error ->
                    BackendReadinessUiState.Error(error.toBackendReadinessMessageRes())
                }
            )
        }
    }

    private fun BackendReadiness.toUiState(): BackendReadinessUiState = when {
        !serviceReady -> BackendReadinessUiState.Error(R.string.backend_service_not_ready)
        !databaseConnected -> BackendReadinessUiState.Error(R.string.backend_database_unavailable)
        !telegramConfigured -> BackendReadinessUiState.Error(R.string.backend_telegram_not_configured)
        else -> BackendReadinessUiState.Ready
    }
}
