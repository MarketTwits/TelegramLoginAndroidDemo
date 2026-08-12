package com.markettwits.devx.tgsignin.data.dataSource

import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.ProfileDraft

interface TelegramAuthApiDataSource {
    suspend fun authenticate(idToken: String): AuthenticationResult
    suspend fun getCurrentSession(accessToken: String): AuthenticationResult
    suspend fun saveProfile(accessToken: String, draft: ProfileDraft): AuthenticationResult
    suspend fun revokeSession(accessToken: String)
}

class BackendHttpException(val statusCode: Int) : Exception()
class BackendResponseException(cause: Throwable) : Exception(cause)
class BackendConfigurationException : Exception()
class BackendNetworkException(cause: Throwable) : Exception(cause)
