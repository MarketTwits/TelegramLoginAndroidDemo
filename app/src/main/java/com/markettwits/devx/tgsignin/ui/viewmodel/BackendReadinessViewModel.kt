package com.markettwits.devx.tgsignin.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import com.markettwits.devx.tgsignin.data.model.BackendReadiness
import com.markettwits.devx.tgsignin.data.repository.BackendReadinessRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackendReadinessUiState {
    data object Checking : BackendReadinessUiState
    data object Ready : BackendReadinessUiState
    data class Error(@StringRes val messageRes: Int) : BackendReadinessUiState
}

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
        !isApiCompatible -> BackendReadinessUiState.Error(R.string.error_incompatible_backend)
        else -> BackendReadinessUiState.Ready
    }
}

@StringRes
private fun Throwable.toBackendReadinessMessageRes(): Int = when (this) {
    is AuthenticationError.NetworkUnavailable -> R.string.error_network_unavailable
    is AuthenticationError.ConnectionFailed -> R.string.error_connection_failed
    is AuthenticationError.Timeout -> R.string.error_timeout
    is AuthenticationError.SecureConnectionFailed -> R.string.error_secure_connection_failed
    is AuthenticationError.ServerUnavailable -> R.string.backend_server_unavailable
    is AuthenticationError.RequestRejected,
    is AuthenticationError.AuthorizationRejected,
    is AuthenticationError.TooManyRequests -> R.string.backend_readiness_endpoint_unavailable

    is AuthenticationError.InvalidServerResponse -> R.string.backend_invalid_readiness_response
    is AuthenticationError.IncompatibleBackend -> R.string.error_incompatible_backend
    is AuthenticationError.InvalidConfiguration -> R.string.backend_address_invalid
    else -> R.string.backend_check_failed
}
