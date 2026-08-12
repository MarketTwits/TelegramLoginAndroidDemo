package com.markettwits.devx.tgsignin.ui.model

import androidx.annotation.StringRes
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationError

@StringRes
fun Throwable.toUserMessageRes(): Int = when (this) {
    is AuthenticationError.NetworkUnavailable -> R.string.error_network_unavailable
    is AuthenticationError.ConnectionFailed -> R.string.error_connection_failed
    is AuthenticationError.Timeout -> R.string.error_timeout
    is AuthenticationError.SecureConnectionFailed -> R.string.error_secure_connection_failed
    is AuthenticationError.AuthorizationRejected -> R.string.error_authorization_rejected
    is AuthenticationError.TooManyRequests -> R.string.error_too_many_requests
    is AuthenticationError.ServerUnavailable -> R.string.error_server_unavailable
    is AuthenticationError.RequestRejected -> R.string.error_request_rejected
    is AuthenticationError.InvalidServerResponse -> R.string.error_invalid_server_response
    is AuthenticationError.IncompatibleBackend -> R.string.error_incompatible_backend
    is AuthenticationError.InvalidConfiguration -> R.string.error_invalid_configuration
    is AuthenticationError.TelegramSdk -> R.string.error_telegram_sdk
    is AuthenticationError.BrowserUnavailable -> R.string.error_browser_unavailable
    is AuthenticationError.LocalStorage -> R.string.error_local_storage
    is AuthenticationError.Unknown -> R.string.error_unknown
    else -> R.string.error_unknown
}
