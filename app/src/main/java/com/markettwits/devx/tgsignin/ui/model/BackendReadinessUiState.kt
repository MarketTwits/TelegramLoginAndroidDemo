package com.markettwits.devx.tgsignin.ui.model

import androidx.annotation.StringRes
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationError

sealed interface BackendReadinessUiState {
    data object Checking : BackendReadinessUiState
    data object Ready : BackendReadinessUiState
    data class Error(@StringRes val messageRes: Int) : BackendReadinessUiState
}

@StringRes
fun Throwable.toBackendReadinessMessageRes(): Int = when (this) {
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
