package com.markettwits.devx.tgsignin.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AppLinkVerification
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import com.markettwits.devx.tgsignin.data.repository.AppLinkVerificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppLinkVerificationUiState {
    data object Checking : AppLinkVerificationUiState
    data object Verified : AppLinkVerificationUiState
    data class Error(@StringRes val messageRes: Int) : AppLinkVerificationUiState
}

class AppLinkVerificationViewModel(
    private val repository: AppLinkVerificationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppLinkVerificationUiState>(
        AppLinkVerificationUiState.Checking
    )
    val uiState: StateFlow<AppLinkVerificationUiState> = _uiState.asStateFlow()
    private var checkJob: Job? = null

    init {
        checkVerification()
    }

    fun checkVerification() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _uiState.value = AppLinkVerificationUiState.Checking
            _uiState.value = repository.checkVerification().fold(
                onSuccess = { verification -> verification.toUiState() },
                onFailure = { error ->
                    AppLinkVerificationUiState.Error(error.toAppLinkVerificationMessageRes())
                }
            )
        }
    }

    private fun AppLinkVerification.toUiState(): AppLinkVerificationUiState = when (this) {
        AppLinkVerification.Verified -> AppLinkVerificationUiState.Verified
        AppLinkVerification.PackageNotRegistered -> AppLinkVerificationUiState.Error(
            R.string.app_link_package_not_registered
        )
        AppLinkVerification.SignatureNotRegistered -> AppLinkVerificationUiState.Error(
            R.string.app_link_signature_not_registered
        )
    }
}

@StringRes
private fun Throwable.toAppLinkVerificationMessageRes(): Int = when (this) {
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
