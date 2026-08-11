package com.markettwits.devx.tgsignin.data.repository

import kotlinx.coroutines.CancellationException
import com.markettwits.devx.tgsignin.data.dataSource.BackendReadinessDataSource
import com.markettwits.devx.tgsignin.data.model.BackendReadiness

interface BackendReadinessRepository {
    suspend fun checkReadiness(): Result<BackendReadiness>
}

class BackendReadinessRepositoryImpl(
    private val dataSource: BackendReadinessDataSource
) : BackendReadinessRepository {
    override suspend fun checkReadiness(): Result<BackendReadiness> = try {
        Result.success(dataSource.checkReadiness())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error.toAuthenticationError())
    }
}
