package com.markettwits.devx.tgsignin.ui.viewmodel

import android.content.Context
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.model.UserSessionInfo
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthenticationRepository : AuthenticationRepository {
        override val state: StateFlow<RootAuthenticationState> =
            MutableStateFlow(RootAuthenticationState.Unauthenticated())
        private val reauthVersion = MutableStateFlow(0L)
        override val reauthenticationVersion: StateFlow<Long> = reauthVersion.asStateFlow()

        fun completeReauthentication() {
            reauthVersion.value += 1
        }

        var sessionsList: List<UserSessionInfo> = listOf(
            UserSessionInfo(
                id = "sess-1",
                createdAt = "2026-01-01T00:00:00Z",
                lastSeenAt = "2026-01-02T00:00:00Z",
                expiresAt = "2026-02-01T00:00:00Z",
                authenticationMethod = "passkey",
                deviceLabel = "Pixel 8",
                current = true
            ),
            UserSessionInfo(
                id = "sess-2",
                createdAt = "2026-01-01T00:00:00Z",
                lastSeenAt = "2026-01-01T12:00:00Z",
                expiresAt = "2026-02-01T00:00:00Z",
                authenticationMethod = "telegram",
                deviceLabel = "Chrome Mac",
                current = false
            )
        )

        var listSessionsResult: Result<List<UserSessionInfo>>? = null
        var revokeSessionResult: Result<Unit> = Result.success(Unit)
        var revokeOthersResult: Result<Int> = Result.success(1)

        var revokedSessionId: String? = null
        var revokedIsCurrent: Boolean? = null
        var revokedOthersCalled = false

        override suspend fun listSessions(): Result<List<UserSessionInfo>> {
            return listSessionsResult ?: Result.success(sessionsList)
        }

        override suspend fun revokeSessionById(sessionId: String, isCurrentSession: Boolean): Result<Unit> {
            revokedSessionId = sessionId
            revokedIsCurrent = isCurrentSession
            return revokeSessionResult
        }

        override suspend fun revokeOtherSessions(): Result<Int> {
            revokedOthersCalled = true
            return revokeOthersResult
        }
        override suspend fun reauthenticateWithPasskey(context: Context): Result<Unit> {
            completeReauthentication()
            return Result.success(Unit)
        }

        override fun startTelegramLogin(context: Context, scopes: Set<com.markettwits.devx.tgsignin.data.model.TelegramScope>) {}
        override fun startTelegramReauthentication(context: Context) {}
        override fun cancelTelegramReauthentication() {}
        override fun isTelegramCallback(uri: android.net.Uri): Boolean = false
        override suspend fun completeTelegramLogin(callbackUri: android.net.Uri): Result<AuthenticationResult> =
            Result.failure(NotImplementedError())
        override suspend fun signInWithPasskey(context: Context): Result<AuthenticationResult> =
            Result.failure(NotImplementedError())
        override suspend fun saveDraft(draft: ProfileDraft) {}
        override suspend fun saveProfile(draft: ProfileDraft): Result<AuthenticationResult> =
            Result.failure(NotImplementedError())
        override suspend fun beginProfileEditing() {}
        override suspend fun cancelProfileEditing() {}
        override suspend fun updateProfileEmoji(selection: com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection): Result<Unit> =
            Result.failure(NotImplementedError())
        override suspend fun deleteAccount(): Result<Unit> =
            Result.failure(NotImplementedError())
        override suspend fun logout() {}
    }

    @Test
    fun `initial load transitions from Loading to Ready`() = runTest {
        val repo = FakeAuthenticationRepository()
        val viewModel = SessionViewModel(repo)

        assertEquals(SessionUiState.Loading, viewModel.uiState.value)
        assertTrue(viewModel.uiState.value.isBusy)
        assertTrue(viewModel.uiState.value.sessions.isEmpty())

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SessionUiState.Ready)
        assertFalse(state.isBusy)
        assertEquals(2, (state as SessionUiState.Ready).sessions.size)
        assertEquals("Pixel 8", state.sessions[0].deviceLabel)
        assertTrue(state.sessions[0].current)
        assertFalse(state.sessions[1].current)
    }

    @Test
    fun `initial load failure transitions to Error`() = runTest {
        val repo = FakeAuthenticationRepository().apply {
            listSessionsResult = Result.failure(RuntimeException("Network timeout"))
        }
        val viewModel = SessionViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SessionUiState.Error)
        assertEquals("Network timeout", (state as SessionUiState.Error).error.message)
        assertFalse(state.isBusy)
    }

    @Test
    fun `revoke other session successfully reloads sessions`() = runTest {
        val repo = FakeAuthenticationRepository()
        val viewModel = SessionViewModel(repo)
        advanceUntilIdle()

        val targetSession = repo.sessionsList[1]
        // Simulate that after revocation, only 1 session remains
        repo.sessionsList = listOf(repo.sessionsList[0])

        viewModel.revokeSession(targetSession)

        advanceUntilIdle()

        assertEquals("sess-2", repo.revokedSessionId)
        assertEquals(false, repo.revokedIsCurrent)

        val state = viewModel.uiState.value
        assertTrue(state is SessionUiState.Ready)
        assertEquals(1, state.sessions.size)
        assertEquals("sess-1", state.sessions[0].id)
    }

    @Test
    fun `reauthentication retries the pending revocation`() = runTest {
        val repo = FakeAuthenticationRepository().apply {
            revokeSessionResult = Result.failure(AuthenticationError.ReauthenticationRequired())
        }
        val viewModel = SessionViewModel(repo)
        advanceUntilIdle()

        viewModel.revokeSession(repo.sessionsList[1])
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SessionUiState.ReauthenticationRequired)

        repo.revokeSessionResult = Result.success(Unit)
        repo.sessionsList = listOf(repo.sessionsList[0])
        repo.completeReauthentication()
        advanceUntilIdle()

        assertEquals(listOf("sess-1"), viewModel.uiState.value.sessions.map { it.id })
        assertTrue(viewModel.uiState.value is SessionUiState.Ready)
    }

    @Test
    fun `cancelling verification does not replay a session revocation`() = runTest {
        val repo = FakeAuthenticationRepository().apply {
            revokeOthersResult = Result.failure(AuthenticationError.ReauthenticationRequired())
        }
        val viewModel = SessionViewModel(repo)
        advanceUntilIdle()

        viewModel.revokeOtherSessions()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SessionUiState.ReauthenticationRequired)

        repo.revokedOthersCalled = false
        viewModel.cancelPendingAction()
        repo.completeReauthentication()
        advanceUntilIdle()

        assertFalse(repo.revokedOthersCalled)
        assertTrue(viewModel.uiState.value is SessionUiState.Ready)
    }

    @Test
    fun `revoke session failure transitions to Error`() = runTest {
        val repo = FakeAuthenticationRepository().apply {
            revokeSessionResult = Result.failure(RuntimeException("Forbidden: fresh auth required"))
        }
        val viewModel = SessionViewModel(repo)
        advanceUntilIdle()

        viewModel.revokeSession(repo.sessionsList[1])
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SessionUiState.Error)
        assertEquals("Forbidden: fresh auth required", (state as SessionUiState.Error).error.message)
        assertEquals(2, state.sessions.size)
    }

    @Test
    fun `revoke other sessions successfully reloads sessions`() = runTest {
        val repo = FakeAuthenticationRepository()
        val viewModel = SessionViewModel(repo)
        advanceUntilIdle()

        // Simulate that after revoking others, only current session remains
        repo.sessionsList = listOf(repo.sessionsList[0])

        viewModel.revokeOtherSessions()
        advanceUntilIdle()

        assertTrue(repo.revokedOthersCalled)
        val state = viewModel.uiState.value
        assertTrue(state is SessionUiState.Ready)
        assertEquals(1, state.sessions.size)
    }

    @Test
    fun `revoke other sessions failure transitions to Error`() = runTest {
        val repo = FakeAuthenticationRepository().apply {
            revokeOthersResult = Result.failure(RuntimeException("Server error"))
        }
        val viewModel = SessionViewModel(repo)
        advanceUntilIdle()

        viewModel.revokeOtherSessions()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SessionUiState.Error)
        assertEquals("Server error", (state as SessionUiState.Error).error.message)
    }
}
