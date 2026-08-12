package com.markettwits.devx.tgsignin.ui.model

import androidx.annotation.StringRes
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationError

sealed interface AppLinkVerificationUiState {
    data object Checking : AppLinkVerificationUiState
    data object Verified : AppLinkVerificationUiState
    data class Error(@StringRes val messageRes: Int) : AppLinkVerificationUiState
}

@StringRes
fun Throwable.toAppLinkVerificationMessageRes(): Int = when (this) {
    is AuthenticationError.NetworkUnavailable -> R.string.error_network_unavailable
    is AuthenticationError.Timeout -> R.string.app_link_check_timeout
    is AuthenticationError.SecureConnectionFailed -> R.string.app_link_secure_connection_failed
    is AuthenticationError.ConnectionFailed,
    is AuthenticationError.ServerUnavailable,
    is AuthenticationError.RequestRejected,
    is AuthenticationError.AuthorizationRejected,
    is AuthenticationError.TooManyRequests -> R.string.app_link_document_unavailable
    is AuthenticationError.InvalidServerResponse -> R.string.app_link_invalid_document
    else -> R.string.app_link_check_failed
}
