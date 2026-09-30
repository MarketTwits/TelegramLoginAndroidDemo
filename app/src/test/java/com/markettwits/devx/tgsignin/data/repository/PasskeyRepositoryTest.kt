package com.markettwits.devx.tgsignin.data.repository

import com.markettwits.devx.tgsignin.data.datasource.BackendHttpException
import com.markettwits.devx.tgsignin.data.datasource.PasskeyCredentialDataSource
import com.markettwits.devx.tgsignin.data.datasource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.model.DeletedPasskey
import com.markettwits.devx.tgsignin.data.model.PasskeyError
import com.markettwits.devx.tgsignin.data.model.PasskeyInfo
import com.markettwits.devx.tgsignin.data.model.PasskeyOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class PasskeyRepositoryTest {

    private class FakeApi : TelegramAuthApiDataSource by notImplementedApi() {
        var listResponse: List<PasskeyInfo> = emptyList()
        var registrationOptionsResponse: PasskeyOptions = PasskeyOptions("op1", "{}")
        var finishRegistrationResponse: List<PasskeyInfo> = emptyList()
        var renameResponse: List<PasskeyInfo> = emptyList()
        var deleteResponse: DeletedPasskey = DeletedPasskey("cred1", "example.com")
        var reauthOptionsResponse: PasskeyOptions = PasskeyOptions("reauth1", "{}")
        var reauthFinished = false

        var errorToThrow: Throwable? = null

        override suspend fun listPasskeys(accessToken: String): List<PasskeyInfo> {
            errorToThrow?.let { throw it }
            return listResponse
        }

        override suspend fun beginPasskeyRegistration(accessToken: String): PasskeyOptions {
            errorToThrow?.let { throw it }
            return registrationOptionsResponse
        }

        override suspend fun finishPasskeyRegistration(
            accessToken: String,
            operationId: String,
            credentialJson: String,
            name: String
        ): List<PasskeyInfo> {
            errorToThrow?.let { throw it }
            return finishRegistrationResponse
        }

        override suspend fun renamePasskey(accessToken: String, id: String, name: String): List<PasskeyInfo> {
            errorToThrow?.let { throw it }
            return renameResponse
        }

        override suspend fun deletePasskey(accessToken: String, id: String): DeletedPasskey {
            errorToThrow?.let { throw it }
            return deleteResponse
        }

        override suspend fun beginPasskeyReauthentication(accessToken: String): PasskeyOptions {
            errorToThrow?.let { throw it }
            return reauthOptionsResponse
        }

        override suspend fun finishPasskeyReauthentication(
            accessToken: String,
            operationId: String,
            credentialJson: String
        ) {
            errorToThrow?.let { throw it }
            reauthFinished = true
        }
    }

    private class FakeCredentialDataSource : PasskeyCredentialDataSource {
        override val isSupported: Boolean = true
        var signalledRpId: String? = null
        var signalledCredentialId: String? = null

        override suspend fun create(context: android.content.Context, requestJson: String): String = "{}"
        override suspend fun get(context: android.content.Context, requestJson: String): String = "{}"
        override suspend fun clearState() {}
        override suspend fun signalUnknownCredential(rpId: String, credentialId: String) {
            signalledRpId = rpId
            signalledCredentialId = credentialId
        }
    }

    @Test
    fun `getPasskeys returns success with passkey list`() = runBlocking {
        val api = FakeApi().apply {
            listResponse = listOf(
                PasskeyInfo("id1", "cred1", "Key 1", "2026-01-01T00:00:00Z", null, "platform", true)
            )
        }
        val credentials = FakeCredentialDataSource()
        val repo = PasskeyRepositoryImpl(api, credentials)

        val result = repo.getPasskeys("token")
        assertTrue(result.isSuccess)
        assertEquals("Key 1", result.getOrNull()?.first()?.name)
    }

    @Test
    fun `getPasskeys maps http error to typed PasskeyError`() = runBlocking {
        val api = FakeApi().apply {
            errorToThrow = BackendHttpException(401, "SESSION_INVALID", "req-123")
        }
        val credentials = FakeCredentialDataSource()
        val repo = PasskeyRepositoryImpl(api, credentials)

        val result = repo.getPasskeys("token")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is PasskeyError.SessionExpired)
    }

    @Test
    fun `rethrows CancellationException on any call`() = runBlocking {
        val api = FakeApi().apply {
            errorToThrow = CancellationException("Job was cancelled")
        }
        val credentials = FakeCredentialDataSource()
        val repo = PasskeyRepositoryImpl(api, credentials)

        try {
            repo.getPasskeys("token")
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Job was cancelled", e.message)
        }
    }

    @Test
    fun `deletePasskey calls signalUnknownCredential on data source`() = runBlocking {
        val api = FakeApi().apply {
            deleteResponse = DeletedPasskey("cred-del", "rp.example.com")
        }
        val credentials = FakeCredentialDataSource()
        val repo = PasskeyRepositoryImpl(api, credentials)

        val result = repo.deletePasskey("token", "id-1")
        assertTrue(result.isSuccess)
        assertEquals("cred-del", result.getOrNull()?.credentialId)

        repo.signalUnknownCredential(result.getOrNull()!!.rpId, result.getOrNull()!!.credentialId)
        assertEquals("rp.example.com", credentials.signalledRpId)
        assertEquals("cred-del", credentials.signalledCredentialId)
    }

    @Test
    fun `maps network IO exception to NetworkUnavailable`() = runBlocking {
        val api = FakeApi().apply {
            errorToThrow = IOException("Connection reset")
        }
        val credentials = FakeCredentialDataSource()
        val repo = PasskeyRepositoryImpl(api, credentials)

        val result = repo.beginRegistration("token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PasskeyError.NetworkUnavailable)
    }
}

private fun notImplementedApi(): TelegramAuthApiDataSource =
    java.lang.reflect.Proxy.newProxyInstance(
        TelegramAuthApiDataSource::class.java.classLoader,
        arrayOf(TelegramAuthApiDataSource::class.java)
    ) { _, method, _ ->
        throw UnsupportedOperationException("Method ${method.name} not implemented")
    } as TelegramAuthApiDataSource
