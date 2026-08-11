package com.markettwits.devx.tgsignin.data.dataSource

import com.markettwits.devx.tgsignin.data.model.BackendReadiness

interface BackendReadinessDataSource {
    suspend fun checkReadiness(): BackendReadiness
}
