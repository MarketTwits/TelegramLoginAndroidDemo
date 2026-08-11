package com.markettwits.devx.tgsignin.data.repository

import kotlinx.coroutines.flow.Flow
import com.markettwits.devx.tgsignin.data.dataSource.AppearanceLocalDataSource
import com.markettwits.devx.tgsignin.data.model.AppThemeMode

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
