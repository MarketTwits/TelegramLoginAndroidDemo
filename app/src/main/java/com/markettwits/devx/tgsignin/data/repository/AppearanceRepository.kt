package com.markettwits.devx.tgsignin.data.repository

import com.markettwits.devx.tgsignin.data.datasource.AppearanceLocalDataSource
import com.markettwits.devx.tgsignin.data.model.AppThemeMode
import kotlinx.coroutines.flow.Flow

interface AppearanceRepository {
    val themeMode: Flow<AppThemeMode>
    suspend fun setThemeMode(mode: AppThemeMode)
}

class AppearanceRepositoryImpl(
    private val localDataSource: AppearanceLocalDataSource
) : AppearanceRepository {
    override val themeMode = localDataSource.themeMode

    override suspend fun setThemeMode(mode: AppThemeMode) = localDataSource.setThemeMode(mode)
}
