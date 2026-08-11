package com.markettwits.devx.tgsignin.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.markettwits.devx.tgsignin.data.model.AppThemeMode
import com.markettwits.devx.tgsignin.data.repository.AppearanceRepository

class AppearanceViewModel(
    private val repository: AppearanceRepository
) : ViewModel() {
    val themeMode = repository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppThemeMode.System
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }
}
