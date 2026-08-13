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
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
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
            FakeTelegramAuthApiDataSource(
                validationFailure = BackendNetworkException(IOException("offline"))
            ),
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
            FakeTelegramAuthApiDataSource(validationFailure = BackendHttpException(401)),
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

    @Test
    fun `cancelling profile editing restores persisted profile and clears draft`() = runBlocking {
        val cached = session("Original profile")
        val local = FakeAuthenticationLocalDataSource(cached)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            FakeTelegramLoginDataSource(), FakeTelegramAuthApiDataSource(cached), local, scope
        )
        repository.state.first { it is RootAuthenticationState.Authenticated }

        repository.beginProfileEditing()
        val changed = ProfileDraft(
            displayName = "Unsaved name",
            headline = "Unsaved headline",
            topics = setOf(ProfileTopic.SECURITY)
        )
        repository.saveDraft(changed)
        repository.cancelProfileEditing()

        val restored = repository.state.value as RootAuthenticationState.Authenticated
        assertEquals("Original profile", restored.session.profile?.displayName)
        assertNull(local.draftValue.value)
        scope.cancel()
    }

    @Test
    fun `profile save rejected by expired session clears local state`() = runBlocking {
        val cached = session("Original profile")
        val local = FakeAuthenticationLocalDataSource(cached)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            FakeTelegramLoginDataSource(),
            FakeTelegramAuthApiDataSource(verified = cached, saveFailure = BackendHttpException(401)),
            local,
            scope
        )
        repository.state.first { it is RootAuthenticationState.Authenticated }
        repository.beginProfileEditing()
        val result = repository.saveProfile(
            ProfileDraft(
                displayName = "Valid",
                headline = "A valid updated signal",
                topics = setOf(ProfileTopic.ANDROID)
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            RootAuthenticationState.Unauthenticated(sessionExpired = true),
            repository.state.value
        )
        assertNull(local.sessionValue.value)
        scope.cancel()
    }

    @Test
    fun `late bootstrap response cannot restore a session after logout`() = runBlocking {
        val cached = session("Cached")
        val gate = CompletableDeferred<AuthenticationResult>()
        val local = FakeAuthenticationLocalDataSource(cached)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            FakeTelegramLoginDataSource(),
            FakeTelegramAuthApiDataSource(validationGate = gate),
            local,
            scope
        )
        repository.state.first { it is RootAuthenticationState.Authenticated }

        repository.logout()
        gate.complete(session("Late server response"))

        assertEquals(RootAuthenticationState.Unauthenticated(), repository.state.value)
        assertNull(local.sessionValue.value)
        scope.cancel()
    }

    @Test
    fun `background refresh preserves an active profile draft`() = runBlocking {
        val cached = session("Cached")
        val gate = CompletableDeferred<AuthenticationResult>()
        val local = FakeAuthenticationLocalDataSource(cached)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            FakeTelegramLoginDataSource(),
            FakeTelegramAuthApiDataSource(validationGate = gate),
            local,
            scope
        )
        repository.state.first { it is RootAuthenticationState.Authenticated }
        repository.beginProfileEditing()
        val draft = ProfileDraft(
            displayName = "Work in progress",
            headline = "This must survive refresh",
            topics = setOf(ProfileTopic.PRODUCT)
        )
        repository.saveDraft(draft)

        gate.complete(session("Refreshed server profile"))
        val state = repository.state.first {
            it is RootAuthenticationState.OnboardingRequired &&
                it.session.profile?.displayName == "Refreshed server profile"
        } as RootAuthenticationState.OnboardingRequired

        assertEquals(draft, state.draft)
        scope.cancel()
    }

    @Test
    fun `first profile save shows welcome pager before completed profile`() = runBlocking {
        val completed = session("New profile")
        val required = completed.copy(
            account = completed.account.copy(onboardingState = OnboardingState.PROFILE_REQUIRED),
            profile = null
        )
        val local = FakeAuthenticationLocalDataSource(required)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            FakeTelegramLoginDataSource(),
            FakeTelegramAuthApiDataSource(verified = required, saveResult = completed),
            local,
            scope
        )
        repository.state.first { it is RootAuthenticationState.OnboardingRequired }
        val draft = ProfileDraft(
            displayName = "New profile",
            headline = "Building the next iteration",
            topics = setOf(ProfileTopic.ANDROID),
            emoji = "🚀"
        )

        assertTrue(repository.saveProfile(draft).isSuccess)
        assertTrue(repository.state.value is RootAuthenticationState.ProfileWelcome)
        repository.completeProfileWelcome()
        assertTrue(repository.state.value is RootAuthenticationState.Authenticated)
        scope.cancel()
    }

    @Test
    fun `deleting service profile keeps session and returns to setup`() = runBlocking {
        val completed = session("Delete me")
        val local = FakeAuthenticationLocalDataSource(completed)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = AuthenticationRepositoryImpl(
            FakeTelegramLoginDataSource(), FakeTelegramAuthApiDataSource(completed), local, scope
        )
        repository.state.first { it is RootAuthenticationState.Authenticated }

        assertTrue(repository.deleteProfile().isSuccess)
        val state = repository.state.value as RootAuthenticationState.OnboardingRequired
        assertEquals("session-token", state.session.accessToken)
        assertNull(state.session.profile)
        assertEquals(OnboardingState.PROFILE_REQUIRED, state.session.account.onboardingState)
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
        emoji = "🚀",
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
    private val saveResult: AuthenticationResult = verified,
    private val validationFailure: Throwable? = null,
    private val saveFailure: Throwable? = null,
    private val validationGate: CompletableDeferred<AuthenticationResult>? = null
) : TelegramAuthApiDataSource {
    var revokedToken: String? = null
    var validatedToken: String? = null
    override suspend fun authenticate(idToken: String): AuthenticationResult = verified
    override suspend fun getCurrentSession(accessToken: String): AuthenticationResult {
        validatedToken = accessToken
        validationFailure?.let { throw it }
        validationGate?.let { return it.await() }
        return verified
    }
    override suspend fun saveProfile(accessToken: String, draft: ProfileDraft): AuthenticationResult {
        saveFailure?.let { throw it }
        return saveResult
    }
    override suspend fun deleteProfile(accessToken: String): AuthenticationResult = verified.copy(
        account = verified.account.copy(onboardingState = OnboardingState.PROFILE_REQUIRED),
        profile = null
    )
    override suspend fun revokeSession(accessToken: String) { revokedToken = accessToken }
}

private class FakeTelegramLoginDataSource : TelegramLoginDataSource {
    override fun startLogin(context: Context, scopes: Set<TelegramScope>) = Unit
    override suspend fun consumeCallback(uri: Uri): String = "id-token"
    override fun isTelegramCallback(uri: Uri): Boolean = false
}
