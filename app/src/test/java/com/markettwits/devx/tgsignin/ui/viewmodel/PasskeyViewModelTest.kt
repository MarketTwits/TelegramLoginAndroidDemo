package com.markettwits.devx.tgsignin.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.markettwits.devx.tgsignin.data.datasource.BackendHttpException
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.DeletedPasskey
import com.markettwits.devx.tgsignin.data.model.OnboardingState
import com.markettwits.devx.tgsignin.data.model.PasskeyError
import com.markettwits.devx.tgsignin.data.model.PasskeyInfo
import com.markettwits.devx.tgsignin.data.model.PasskeyOptions
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.model.ServiceAccount
import com.markettwits.devx.tgsignin.data.model.TelegramIdentity
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.data.repository.PasskeyRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakePasskeyRepository : PasskeyRepository {
        override val isSupported: Boolean = true

        var passkeysList: List<PasskeyInfo> = listOf(
            PasskeyInfo("id-1", "cred-1", "MacBook", "2026-01-01T00:00:00Z", null, "platform", true)
        )
        var beginRegistrationResult: Result<PasskeyOptions> = Result.success(PasskeyOptions("op-reg", "{}"))
        var finishRegistrationResult: Result<List<PasskeyInfo>> = Result.success(emptyList())
        var renameResult: Result<List<PasskeyInfo>> = Result.success(emptyList())
        var deleteResult: Result<DeletedPasskey> = Result.success(DeletedPasskey("cred-1", "example.com"))
        var beginReauthResult: Result<PasskeyOptions> = Result.success(PasskeyOptions("op-reauth", "{}"))
        var finishReauthResult: Result<Unit> = Result.success(Unit)

        var listCallCount = 0
        var beginRegistrationCallCount = 0
        var finishRegistrationCallCount = 0
        var deleteCallCount = 0
        var renameCallCount = 0
        var reauthFinishCallCount = 0
        var signalledRpId: String? = null
        var signalledCredentialId: String? = null

        override suspend fun getPasskeys(accessToken: String): Result<List<PasskeyInfo>> {
            listCallCount++
            return Result.success(passkeysList)
        }

        override suspend fun beginRegistration(accessToken: String): Result<PasskeyOptions> {
            beginRegistrationCallCount++
            return beginRegistrationResult
        }

        override suspend fun finishRegistration(
            accessToken: String,
            operationId: String,
            credentialJson: String,
            name: String
        ): Result<List<PasskeyInfo>> {
            finishRegistrationCallCount++
            return finishRegistrationResult
        }

        override suspend fun renamePasskey(
            accessToken: String,
            id: String,
            name: String
        ): Result<List<PasskeyInfo>> {
            renameCallCount++
            return renameResult
        }

        override suspend fun deletePasskey(accessToken: String, id: String): Result<DeletedPasskey> {
            deleteCallCount++
            return deleteResult
        }

        override suspend fun signalUnknownCredential(rpId: String, credentialId: String) {
            signalledRpId = rpId
            signalledCredentialId = credentialId
        }

        override suspend fun beginPasskeyReauthentication(accessToken: String): Result<PasskeyOptions> =
            beginReauthResult

        override suspend fun finishPasskeyReauthentication(
            accessToken: String,
            operationId: String,
            credentialJson: String
        ): Result<Unit> {
            reauthFinishCallCount++
            return finishReauthResult
        }
    }

    private class FakeAuthRepository(initialState: RootAuthenticationState) :
        AuthenticationRepository by notImplementedAuthRepo() {
        val stateFlow = MutableStateFlow(initialState)
        override val state: StateFlow<RootAuthenticationState> = stateFlow.asStateFlow()
        val reauthVersionFlow = MutableStateFlow(0L)
        override val reauthenticationVersion: StateFlow<Long> = reauthVersionFlow.asStateFlow()
        override fun cancelTelegramReauthentication() = Unit
    }

    private fun testSession(): AuthenticationResult = AuthenticationResult(
        accessToken = "test-token",
        expiresAt = null,
        account = ServiceAccount(
            id = "acc-1",
            memberNumber = 1L,
            onboardingState = OnboardingState.PROFILE_COMPLETED,
            registeredAt = "2026-01-01",
            lastLoginAt = "2026-01-01",
            loginCount = 1
        ),
        telegram = TelegramIdentity(userId = "12345", name = "User"),
        profile = null
    )

    @Test
    fun `initial state loads passkeys for authenticated user`() = runTest {
        val repo = FakePasskeyRepository()
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val savedState = SavedStateHandle()

        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PasskeyUiState.Ready)
        assertEquals(1, (viewModel.uiState.value as PasskeyUiState.Ready).passkeys.size)
        assertEquals("MacBook", (viewModel.uiState.value as PasskeyUiState.Ready).passkeys.first().name)
    }

    @Test
    fun `credential manager cancellation resets state to Ready without error banner`() = runTest {
        val repo = FakePasskeyRepository()
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val savedState = SavedStateHandle()

        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()

        viewModel.createPasskey("Pixel 8")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PasskeyUiState.ExternalAuthenticationInProgress)

        // User cancelled Credential Manager dialog
        viewModel.onCredentialError(PasskeyError.Cancelled())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PasskeyUiState.Ready)
        assertEquals(1, viewModel.uiState.value.passkeys.size)
        assertNull(savedState.get<String>("passkey_pending_action_type"))
    }

    @Test
    fun `late credential callback cannot replace state after user cancels`() = runTest {
        val repo = FakePasskeyRepository()
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val savedState = SavedStateHandle()
        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()

        viewModel.createPasskey("Pixel 8")
        advanceUntilIdle()
        viewModel.cancelPendingAction()
        viewModel.onCredentialCreated("op-reg", "{}", "Pixel 8")
        viewModel.onCredentialError(PasskeyError.Unknown(), "op-reg")
        advanceUntilIdle()

        assertEquals(0, repo.finishRegistrationCallCount)
        assertTrue(viewModel.uiState.value is PasskeyUiState.Ready)
    }

    @Test
    fun `challenge expired between create and verify sets Error state and emits snackbar`() = runTest {
        val repo = FakePasskeyRepository().apply {
            finishRegistrationResult = Result.failure(PasskeyError.ChallengeExpired())
        }
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val savedState = SavedStateHandle()

        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()

        viewModel.createPasskey("Pixel 8")
        advanceUntilIdle()

        val events = mutableListOf<PasskeyUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.onCredentialCreated("op-reg", "{}", "Pixel 8")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PasskeyUiState.Error)
        val errorState = viewModel.uiState.value as PasskeyUiState.Error
        assertTrue(errorState.error is PasskeyError.ChallengeExpired)
        assertTrue(events.any { it is PasskeyUiEvent.ShowSnackbar && it.error is PasskeyError.ChallengeExpired })
        job.cancel()
    }

    @Test
    fun `reauthentication then automatically continues original delete operation`() = runTest {
        val repo = FakePasskeyRepository().apply {
            // First delete fails with REAUTHENTICATION_REQUIRED (SessionExpired)
            deleteResult = Result.failure(PasskeyError.SessionExpired(BackendHttpException(403, "REAUTHENTICATION_REQUIRED", "req-1")))
        }
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val savedState = SavedStateHandle()

        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()

        val events = mutableListOf<PasskeyUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.deletePasskey("id-1")
        advanceUntilIdle()

        // Should have emitted LaunchCredentialAuthentication with pending action
        val authEvent = events.filterIsInstance<PasskeyUiEvent.LaunchCredentialAuthentication>().firstOrNull()
        assertTrue(authEvent != null)
        assertEquals("op-reauth", authEvent?.operationId)
        assertTrue(authEvent?.pendingAction is PasskeyPendingAction.Delete)

        // Make subsequent delete succeed
        repo.deleteResult = Result.success(DeletedPasskey("cred-1", "rp.example.com"))

        // Complete reauthentication
        viewModel.completePasskeyReauthentication("op-reauth", "{}", authEvent!!.pendingAction)
        advanceUntilIdle()

        assertEquals(1, repo.reauthFinishCallCount)
        assertEquals(2, repo.deleteCallCount) // Initial call + continued call
        assertEquals("rp.example.com", repo.signalledRpId)
        assertEquals("cred-1", repo.signalledCredentialId)
        assertTrue(viewModel.uiState.value is PasskeyUiState.Ready)
        assertEquals(0, viewModel.uiState.value.passkeys.size)
        job.cancel()
    }

    @Test
    fun `duplicate clicks on Add or Delete while busy are ignored`() = runTest {
        val repo = FakePasskeyRepository()
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val savedState = SavedStateHandle()

        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()

        viewModel.createPasskey("Pixel 8")
        // Rapid second click while creating
        viewModel.createPasskey("Pixel 8 Duplicate")
        viewModel.deletePasskey("id-1")
        advanceUntilIdle()

        assertEquals(1, repo.beginRegistrationCallCount)
        assertEquals(0, repo.deleteCallCount)
    }

    @Test
    fun `process recreation restores pending action and resumes with fresh server state`() = runTest {
        val repo = FakePasskeyRepository()
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))

        // Simulate saved state from previous process during external flow
        val savedState = SavedStateHandle().apply {
            set("passkey_pending_action_type", "CREATE")
            set("passkey_pending_action_name", "Work Phone")
        }

        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()

        // Initial restored state reflects in-progress external auth
        assertTrue(viewModel.uiState.value is PasskeyUiState.ExternalAuthenticationInProgress)
        val inProgress = viewModel.uiState.value as PasskeyUiState.ExternalAuthenticationInProgress
        assertEquals(PasskeyPendingAction.Create("Work Phone"), inProgress.pendingAction)

        val events = mutableListOf<PasskeyUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // On resume from external flow
        viewModel.resumeAfterExternalAuth()
        advanceUntilIdle()

        // Fresh passkeys fetched from server
        assertTrue(repo.listCallCount >= 1)
        // Automatic execution of pending create
        assertEquals(1, repo.beginRegistrationCallCount)
        assertTrue(events.any { it is PasskeyUiEvent.LaunchCredentialCreation && it.name == "Work Phone" })
        job.cancel()
    }

    @Test
    fun `process recreation does not replay destructive passkey action without verification`() = runTest {
        val repo = FakePasskeyRepository()
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val savedState = SavedStateHandle().apply {
            set("passkey_pending_action_type", "DELETE")
            set("passkey_pending_action_id", "id-1")
        }

        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()
        viewModel.resumeAfterExternalAuth()
        advanceUntilIdle()

        assertEquals(0, repo.deleteCallCount)
        assertTrue(viewModel.uiState.value is PasskeyUiState.Ready)
        assertNull(savedState.get<String>("passkey_pending_action_type"))
    }

    @Test
    fun `returning from Telegram only retries after successful reauthentication`() = runTest {
        val repo = FakePasskeyRepository().apply {
            renameResult = Result.failure(PasskeyError.SessionExpired())
            beginReauthResult = Result.failure(PasskeyError.CredentialNotFound())
        }
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val viewModel = PasskeyViewModel(repo, authRepo, SavedStateHandle())
        advanceUntilIdle()

        viewModel.renamePasskey("id-1", "New name")
        advanceUntilIdle()
        assertEquals(1, repo.renameCallCount)
        viewModel.onTelegramReauthenticationStarted(PasskeyPendingAction.Rename("id-1", "New name"))
        viewModel.resumeAfterExternalAuth()
        advanceUntilIdle()
        assertEquals(1, repo.renameCallCount)

        repo.renameResult = Result.success(repo.passkeysList)
        authRepo.reauthVersionFlow.value += 1
        advanceUntilIdle()
        assertEquals(2, repo.renameCallCount)
        assertTrue(viewModel.uiState.value is PasskeyUiState.Ready)
    }

    @Test
    fun `saved state handle does not store credentials or bearer tokens`() = runTest {
        val repo = FakePasskeyRepository()
        val authRepo = FakeAuthRepository(RootAuthenticationState.Authenticated(testSession()))
        val savedState = SavedStateHandle()

        val viewModel = PasskeyViewModel(repo, authRepo, savedState)
        advanceUntilIdle()

        viewModel.createPasskey("My Device")
        advanceUntilIdle()

        // Verify keys stored
        for (key in savedState.keys()) {
            assertFalse(key.contains("token", ignoreCase = true))
            assertFalse(key.contains("credential", ignoreCase = true))
            assertFalse(key.contains("secret", ignoreCase = true))
        }
        assertEquals("CREATE", savedState.get<String>("passkey_pending_action_type"))
        assertEquals("My Device", savedState.get<String>("passkey_pending_action_name"))
    }
}

private fun notImplementedAuthRepo(): AuthenticationRepository =
    java.lang.reflect.Proxy.newProxyInstance(
        AuthenticationRepository::class.java.classLoader,
        arrayOf(AuthenticationRepository::class.java)
    ) { _, method, _ ->
        throw UnsupportedOperationException("Method ${method.name} not implemented")
    } as AuthenticationRepository
