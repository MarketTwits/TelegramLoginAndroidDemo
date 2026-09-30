package com.markettwits.devx.tgsignin.data.repository

import com.markettwits.devx.tgsignin.data.datasource.PasskeyCredentialDataSource
import com.markettwits.devx.tgsignin.data.datasource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.model.DeletedPasskey
import com.markettwits.devx.tgsignin.data.model.PasskeyInfo
import com.markettwits.devx.tgsignin.data.model.PasskeyOptions
import kotlinx.coroutines.CancellationException

interface PasskeyRepository {
    val isSupported: Boolean
    suspend fun getPasskeys(accessToken: String): Result<List<PasskeyInfo>>
    suspend fun beginRegistration(accessToken: String): Result<PasskeyOptions>
    suspend fun finishRegistration(
        accessToken: String,
        operationId: String,
        credentialJson: String,
        name: String
    ): Result<List<PasskeyInfo>>
    suspend fun renamePasskey(
        accessToken: String,
        id: String,
        name: String
    ): Result<List<PasskeyInfo>>
    suspend fun deletePasskey(
        accessToken: String,
        id: String
    ): Result<DeletedPasskey>
    suspend fun signalUnknownCredential(rpId: String, credentialId: String)
    suspend fun beginPasskeyReauthentication(accessToken: String): Result<PasskeyOptions>
    suspend fun finishPasskeyReauthentication(
        accessToken: String,
        operationId: String,
        credentialJson: String
    ): Result<Unit>
}

class PasskeyRepositoryImpl(
    private val api: TelegramAuthApiDataSource,
    private val credentialDataSource: PasskeyCredentialDataSource
) : PasskeyRepository {
    override val isSupported: Boolean
        get() = credentialDataSource.isSupported

    override suspend fun getPasskeys(accessToken: String): Result<List<PasskeyInfo>> =
        safeCall { api.listPasskeys(accessToken) }

    override suspend fun beginRegistration(accessToken: String): Result<PasskeyOptions> =
        safeCall { api.beginPasskeyRegistration(accessToken) }

    override suspend fun finishRegistration(
        accessToken: String,
        operationId: String,
        credentialJson: String,
        name: String
    ): Result<List<PasskeyInfo>> =
        safeCall { api.finishPasskeyRegistration(accessToken, operationId, credentialJson, name) }

    override suspend fun renamePasskey(
        accessToken: String,
        id: String,
        name: String
    ): Result<List<PasskeyInfo>> =
        safeCall { api.renamePasskey(accessToken, id, name) }

    override suspend fun deletePasskey(
        accessToken: String,
        id: String
    ): Result<DeletedPasskey> =
        safeCall { api.deletePasskey(accessToken, id) }

    override suspend fun signalUnknownCredential(rpId: String, credentialId: String) {
        safeCall { credentialDataSource.signalUnknownCredential(rpId, credentialId) }
    }

    override suspend fun beginPasskeyReauthentication(accessToken: String): Result<PasskeyOptions> =
        safeCall { api.beginPasskeyReauthentication(accessToken) }

    override suspend fun finishPasskeyReauthentication(
        accessToken: String,
        operationId: String,
        credentialJson: String
    ): Result<Unit> =
        safeCall { api.finishPasskeyReauthentication(accessToken, operationId, credentialJson) }

    private inline fun <T> safeCall(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e.toPasskeyError())
        }
}
