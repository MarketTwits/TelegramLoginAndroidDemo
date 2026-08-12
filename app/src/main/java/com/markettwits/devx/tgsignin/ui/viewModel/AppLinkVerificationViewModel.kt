package com.markettwits.devx.tgsignin.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AppLinkVerification
import com.markettwits.devx.tgsignin.data.repository.AppLinkVerificationRepository
import com.markettwits.devx.tgsignin.ui.model.AppLinkVerificationUiState
import com.markettwits.devx.tgsignin.ui.model.toAppLinkVerificationMessageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
