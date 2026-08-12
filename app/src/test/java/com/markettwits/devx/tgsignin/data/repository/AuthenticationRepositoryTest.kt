package com.markettwits.devx.tgsignin.data.repository

import android.content.Context
import android.net.Uri
import com.markettwits.devx.tgsignin.data.dataSource.AuthenticationLocalDataSource
import com.markettwits.devx.tgsignin.data.dataSource.BackendHttpException
import com.markettwits.devx.tgsignin.data.dataSource.BackendNetworkException
import com.markettwits.devx.tgsignin.data.dataSource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramLoginDataSource
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.AvatarSource
import com.markettwits.devx.tgsignin.data.model.OnboardingState
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileIntent
import com.markettwits.devx.tgsignin.data.model.ProfileTopic
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.model.ServiceAccount
import com.markettwits.devx.tgsignin.data.model.ServiceProfile
import com.markettwits.devx.tgsignin.data.model.TelegramIdentity
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import java.io.IOException
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

class AuthenticationRepositoryTest {
    @Test
    fun `renders cached completed profile, refreshes it, and revokes on logout`() = runBlocking {
        val cached = session("Cached")
        val verified = session("Verified")
        val local = FakeAuthenticationLocalDataSource(cached)
        val api = FakeTelegramAuthApiDataSource(verified)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(FakeTelegramLoginDataSource(), api, local, scope)

        val state = repository.state.first {
            it is RootAuthenticationState.Authenticated && it.session.profile?.displayName == "Verified"
        } as RootAuthenticationState.Authenticated
        assertEquals("Verified", state.session.profile?.displayName)
        assertEquals("session-token", api.validatedToken)

        repository.logout()
        assertEquals("session-token", api.revokedToken)
        assertNull(local.sessionValue.value)
        assertEquals(RootAuthenticationState.Unauthenticated(), repository.state.value)
        scope.cancel()
    }

    @Test
    fun `keeps completed cached profile visible offline`() = runBlocking {
        val cached = session("Offline profile")
        val local = FakeAuthenticationLocalDataSource(cached)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            FakeTelegramLoginDataSource(),
            FakeTelegramAuthApiDataSource(failure = BackendNetworkException(IOException("offline"))),
            local,
            scope
        )
        val state = repository.state.first {
            it is RootAuthenticationState.Authenticated && it.isOffline
        } as RootAuthenticationState.Authenticated
        assertEquals("Offline profile", state.session.profile?.displayName)
        assertEquals(cached, local.sessionValue.value)
        scope.cancel()
    }

    @Test
    fun `expired server session clears encrypted cache and returns to login`() = runBlocking {
        val local = FakeAuthenticationLocalDataSource(session("Expired"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            FakeTelegramLoginDataSource(),
            FakeTelegramAuthApiDataSource(failure = BackendHttpException(401)),
            local,
            scope
        )
        val state = repository.state.first {
            it is RootAuthenticationState.Unauthenticated && it.sessionExpired
        }
        assertEquals(RootAuthenticationState.Unauthenticated(sessionExpired = true), state)
        assertNull(local.sessionValue.value)
        scope.cancel()
    }
}

private fun session(displayName: String) = AuthenticationResult(
    accessToken = "session-token",
    expiresAt = "2026-09-01T00:00:00Z",
    account = ServiceAccount(
        id = "account-1",
        memberNumber = 42,
        onboardingState = OnboardingState.PROFILE_COMPLETED,
        registeredAt = "2026-08-01T00:00:00Z",
        lastLoginAt = "2026-08-12T00:00:00Z",
        loginCount = 3
    ),
    telegram = TelegramIdentity(username = "demo", phoneVerified = true),
    profile = ServiceProfile(
        displayName = displayName,
        headline = "Building a demo",
        intent = ProfileIntent.BUILDING,
        topics = listOf(ProfileTopic.ANDROID),
        avatarSource = AvatarSource.BLOOM,
        visualSeed = "stable-seed",
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-12T00:00:00Z"
    )
)

private class FakeAuthenticationLocalDataSource(session: AuthenticationResult?) : AuthenticationLocalDataSource {
    val sessionValue = MutableStateFlow(session)
    val draftValue = MutableStateFlow<ProfileDraft?>(null)
    override val session: Flow<AuthenticationResult?> = sessionValue
    override val profileDraft: Flow<ProfileDraft?> = draftValue
    override suspend fun save(session: AuthenticationResult) { sessionValue.value = session }
    override suspend fun saveDraft(draft: ProfileDraft) { draftValue.value = draft }
    override suspend fun clearDraft() { draftValue.value = null }
    override suspend fun clear() { sessionValue.value = null; draftValue.value = null }
}

private class FakeTelegramAuthApiDataSource(
    private val verified: AuthenticationResult = session("Verified"),
    private val failure: Throwable? = null
) : TelegramAuthApiDataSource {
    var revokedToken: String? = null
    var validatedToken: String? = null
    override suspend fun authenticate(idToken: String): AuthenticationResult = verified
    override suspend fun getCurrentSession(accessToken: String): AuthenticationResult {
        validatedToken = accessToken
        failure?.let { throw it }
        return verified
    }
    override suspend fun saveProfile(accessToken: String, draft: ProfileDraft): AuthenticationResult = verified
    override suspend fun revokeSession(accessToken: String) { revokedToken = accessToken }
}

private class FakeTelegramLoginDataSource : TelegramLoginDataSource {
    override fun startLogin(context: Context, scopes: Set<TelegramScope>) = Unit
    override suspend fun consumeCallback(uri: Uri): String = "id-token"
    override fun isTelegramCallback(uri: Uri): Boolean = false
}
