package com.markettwits.devx.tgsignin.data.repository

import com.markettwits.devx.tgsignin.data.datasource.AppLinkVerificationDataSource
import com.markettwits.devx.tgsignin.data.model.AppLinkVerification
import kotlinx.coroutines.CancellationException

interface AppLinkVerificationRepository {
    suspend fun checkVerification(): Result<AppLinkVerification>
}

class AppLinkVerificationRepositoryImpl(
    private val dataSource: AppLinkVerificationDataSource
) : AppLinkVerificationRepository {
    override suspend fun checkVerification(): Result<AppLinkVerification> = try {
        Result.success(dataSource.checkVerification())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error.toAuthenticationError())
    }
}
