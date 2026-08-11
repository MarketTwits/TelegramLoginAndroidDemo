package com.markettwits.devx.tgsignin.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.markettwits.devx.tgsignin.data.dataSource.AuthenticationLocalDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramLoginDataSource
import com.markettwits.devx.tgsignin.data.model.AuthenticatedSession
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import com.markettwits.devx.tgsignin.data.model.TelegramUser

class AuthenticationRepositoryTest {
    @Test
    fun `restores cached user and clears local session on logout`() = runBlocking {
        val user = TelegramUser(id = "user-1", username = "demo")
        val verifiedUser = user.copy(name = "Verified Demo")
        val localDataSource = FakeAuthenticationLocalDataSource(
            AuthenticatedSession(accessToken = "session-token", user = user)
        )
        val apiDataSource = FakeTelegramAuthApiDataSource(verifiedUser)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            telegramLoginDataSource = FakeTelegramLoginDataSource(),
            telegramAuthApiDataSource = apiDataSource,
            authenticationLocalDataSource = localDataSource,
            applicationScope = applicationScope
        )

        assertEquals(verifiedUser, repository.currentUser.first { it != null })
        assertEquals("session-token", apiDataSource.validatedToken)
        repository.logout()

        assertEquals("session-token", apiDataSource.revokedToken)
        assertNull(repository.currentUser.value)
        assertNull(localDataSource.sessionValue.value)
        applicationScope.cancel()
    }

    @Test
    fun `does not restore a cached user when the server rejects the session`() = runBlocking {
        val cachedSession = AuthenticatedSession(
            accessToken = "expired-token",
            user = TelegramUser(id = "cached-user")
        )
        val localDataSource = FakeAuthenticationLocalDataSource(cachedSession)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            telegramLoginDataSource = FakeTelegramLoginDataSource(),
            telegramAuthApiDataSource = FakeTelegramAuthApiDataSource(validationFails = true),
            authenticationLocalDataSource = localDataSource,
            applicationScope = applicationScope
        )

        repository.isSessionRestored.first { it }

        assertNull(repository.currentUser.value)
        assertNull(localDataSource.sessionValue.value)
        applicationScope.cancel()
    }
}

private class FakeAuthenticationLocalDataSource(
    session: AuthenticatedSession?
) : AuthenticationLocalDataSource {
    val sessionValue = MutableStateFlow(session)
    override val session: Flow<AuthenticatedSession?> = sessionValue

    override suspend fun save(session: AuthenticatedSession) {
        sessionValue.value = session
    }

    override suspend fun clear() {
        sessionValue.value = null
    }
}

private class FakeTelegramAuthApiDataSource(
    private val verifiedUser: TelegramUser = TelegramUser(id = "verified-user"),
    private val validationFails: Boolean = false
) : TelegramAuthApiDataSource {
    var revokedToken: String? = null
    var validatedToken: String? = null

    override suspend fun authenticate(idToken: String): AuthenticatedSession =
        error("Not used in this test")

    override suspend fun getCurrentSession(accessToken: String): AuthenticatedSession {
        validatedToken = accessToken
        if (validationFails) error("Session rejected")
        return AuthenticatedSession(accessToken = accessToken, user = verifiedUser)
    }

    override suspend fun revokeSession(accessToken: String) {
        revokedToken = accessToken
    }
}

private class FakeTelegramLoginDataSource : TelegramLoginDataSource {
    override fun startLogin(context: Context, scopes: Set<TelegramScope>) = Unit
    override suspend fun consumeCallback(uri: Uri): String = error("Not used in this test")
    override fun isTelegramCallback(uri: Uri): Boolean = false
}
