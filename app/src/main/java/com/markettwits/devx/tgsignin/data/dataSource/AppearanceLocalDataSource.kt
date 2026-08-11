package com.markettwits.devx.tgsignin.data.dataSource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.markettwits.devx.tgsignin.data.model.AppThemeMode

private const val APPEARANCE_DATA_STORE_NAME = "appearance"
private val Context.appearanceDataStore by preferencesDataStore(APPEARANCE_DATA_STORE_NAME)

interface AppearanceLocalDataSource {
    val themeMode: Flow<AppThemeMode>
    suspend fun setThemeMode(mode: AppThemeMode)
}

class AppearanceLocalDataSourceImpl(
    private val context: Context
) : AppearanceLocalDataSource {
    override val themeMode = context.appearanceDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[themeModeKey]
                ?.let { stored -> AppThemeMode.entries.firstOrNull { it.name == stored } }
                ?: AppThemeMode.System
        }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        context.appearanceDataStore.edit { preferences -> preferences[themeModeKey] = mode.name }
    }

    private companion object {
        val themeModeKey = stringPreferencesKey("theme_mode")
    }
}
