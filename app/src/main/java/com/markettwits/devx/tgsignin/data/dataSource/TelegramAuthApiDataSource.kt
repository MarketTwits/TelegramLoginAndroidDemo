package com.markettwits.devx.tgsignin.data.dataSource

import com.markettwits.devx.tgsignin.data.model.AuthenticatedSession

interface TelegramAuthApiDataSource {
    suspend fun authenticate(idToken: String): AuthenticatedSession
    suspend fun getCurrentSession(accessToken: String): AuthenticatedSession
    suspend fun revokeSession(accessToken: String)
}

class BackendHttpException(val statusCode: Int) : Exception()
class BackendResponseException(cause: Throwable) : Exception(cause)
class BackendConfigurationException : Exception()
class BackendNetworkException(cause: Throwable) : Exception(cause)
