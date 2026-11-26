package com.markettwits.devx.tgsignin.ui.viewmodel

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.OnboardingState
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.model.ServiceAccount
import com.markettwits.devx.tgsignin.data.model.TelegramIdentity
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import com.markettwits.devx.tgsignin.data.model.UserSessionInfo
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.data.repository.ReauthenticationScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepo : AuthenticationRepository {
        val stateFlow = MutableStateFlow<RootAuthenticationState>(
            RootAuthenticationState.Authenticated(
                AuthenticationResult(
                    accessToken = "tok",
                    expiresAt = null,
                    account = ServiceAccount("acc-1", 1, OnboardingState.PROFILE_COMPLETED, "2026-01-01", "2026-01-01", 1),
                    telegram = TelegramIdentity(userId = "1", name = "User"),
                    profile = null
                )
            )
        )
        override val state: StateFlow<RootAuthenticationState> = stateFlow.asStateFlow()
        val reauthVersion = MutableStateFlow(0L)
        override val reauthenticationVersion: StateFlow<Long> = reauthVersion.asStateFlow()
        val reauthFailureVersion = MutableStateFlow(0L)
        override val reauthenticationFailureVersion: StateFlow<Long> = reauthFailureVersion.asStateFlow()
        val callbackInFlight = MutableStateFlow(false)
        override val isTelegramCallbackInFlight: StateFlow<Boolean> = callbackInFlight.asStateFlow()
        override var lastReauthenticationScope: ReauthenticationScope? = null

        var deleteCallCount = 0
        var deleteResult: Result<Unit> = Result.success(Unit)
        var passkeyReauthResult: Result<Unit> = Result.success(Unit)
        var startTelegramCalled = false
        var cancelTelegramCalled = false

        override suspend fun deleteAccount(): Result<Unit> {
            deleteCallCount++
            return deleteResult
        }

        override fun startTelegramReauthentication(context: Context, scope: ReauthenticationScope) {
            startTelegramCalled = true
        }

        override fun startTelegramReauthentication(context: Context) {
            startTelegramCalled = true
        }

        override fun cancelTelegramReauthentication() {
            cancelTelegramCalled = true
        }

        override suspend fun reauthenticateWithPasskey(context: Context, scope: ReauthenticationScope): Result<Unit> {
            return passkeyReauthResult.onSuccess {
                lastReauthenticationScope = scope
                reauthVersion.value += 1
            }
        }

        override suspend fun reauthenticateWithPasskey(context: Context): Result<Unit> =
            reauthenticateWithPasskey(context, ReauthenticationScope.ACCOUNT)

        override fun startTelegramLogin(context: Context, scopes: Set<TelegramScope>) {}
        override fun isTelegramCallback(uri: Uri): Boolean = false
        override suspend fun completeTelegramLogin(callbackUri: Uri): Result<AuthenticationResult> = Result.failure(NotImplementedError())
        override suspend fun signInWithPasskey(context: Context): Result<AuthenticationResult> = Result.failure(NotImplementedError())
        override suspend fun saveDraft(draft: ProfileDraft) {}
        override suspend fun saveProfile(draft: ProfileDraft): Result<AuthenticationResult> = Result.failure(NotImplementedError())
        override suspend fun beginProfileEditing() {}
        override suspend fun cancelProfileEditing() {}
        override suspend fun updateProfileEmoji(selection: ProfileEmojiSelection): Result<Unit> = Result.failure(NotImplementedError())
        override suspend fun logout() {}
        override suspend fun listSessions(): Result<List<UserSessionInfo>> = Result.success(emptyList())
        override suspend fun revokeSessionById(sessionId: String, isCurrentSession: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun revokeOtherSessions(): Result<Int> = Result.success(0)
    }

    private fun dummyContext(): Context = ContextWrapper(null)

    @Test
    fun `account deletion reauthentication prompts and retries automatically after verification`() = runTest {
        val repo = FakeAuthRepo().apply {
            deleteResult = Result.failure(AuthenticationError.ReauthenticationRequired())
        }
        val viewModel = ProfileViewModel(repo)
        val events = mutableListOf<ProfileUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deleteAccountReauthRequired)
        assertTrue(events.isEmpty())

        repo.deleteResult = Result.success(Unit)
        viewModel.verifyDeleteAccountWithTelegram(dummyContext())
        advanceUntilIdle()
        assertTrue(repo.startTelegramCalled)
        assertTrue(viewModel.uiState.value.isReauthenticatingAccountDeletion)

        // In-flight callback keeps spinner active on resume
        repo.callbackInFlight.value = true
        viewModel.onResume()
        advanceUntilIdle()
        assertFalse(repo.cancelTelegramCalled)
        assertTrue(viewModel.uiState.value.isReauthenticatingAccountDeletion)

        // Complete Telegram reauth -> retries deleteAccount
        repo.callbackInFlight.value = false
        repo.lastReauthenticationScope = ReauthenticationScope.ACCOUNT
        repo.reauthVersion.value += 1
        advanceUntilIdle()

        assertEquals(2, repo.deleteCallCount)
        assertFalse(viewModel.uiState.value.deleteAccountReauthRequired)
        assertFalse(viewModel.uiState.value.isReauthenticatingAccountDeletion)
        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `cancelling Telegram reauth or clicking cancel clears account deletion reauth state`() = runTest {
        val repo = FakeAuthRepo().apply {
            deleteResult = Result.failure(AuthenticationError.ReauthenticationRequired())
        }
        val viewModel = ProfileViewModel(repo)

        viewModel.deleteAccount()
        advanceUntilIdle()
        viewModel.verifyDeleteAccountWithTelegram(dummyContext())
        advanceUntilIdle()
        viewModel.onResume()
        advanceUntilIdle()

        assertTrue(repo.cancelTelegramCalled)
        assertTrue(viewModel.uiState.value.deleteAccountReauthRequired)
        assertFalse(viewModel.uiState.value.isReauthenticatingAccountDeletion)

        viewModel.cancelDeleteAccountReauth()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.deleteAccountReauthRequired)
    }

    @Test
    fun `non-reauth delete error emits error message`() = runTest {
        val repo = FakeAuthRepo().apply {
            deleteResult = Result.failure(AuthenticationError.ServerUnavailable())
        }
        val viewModel = ProfileViewModel(repo)
        val events = mutableListOf<ProfileUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.deleteAccountReauthRequired)
        assertEquals(listOf(ProfileUiEvent.ShowMessage(R.string.error_account_delete)), events)
        job.cancel()
    }
}
