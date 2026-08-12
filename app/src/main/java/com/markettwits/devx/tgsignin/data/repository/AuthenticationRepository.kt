package com.markettwits.devx.tgsignin.data.repository

import android.content.Context
import android.net.Uri
import com.markettwits.devx.tgsignin.data.dataSource.AuthenticationLocalDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramLoginDataSource
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.OnboardingState
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.RootAuthenticationState
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface AuthenticationRepository {
    val state: StateFlow<RootAuthenticationState>

    fun startTelegramLogin(context: Context, scopes: Set<TelegramScope>)
    fun isTelegramCallback(uri: Uri): Boolean
    suspend fun completeTelegramLogin(callbackUri: Uri): Result<AuthenticationResult>
    suspend fun saveDraft(draft: ProfileDraft)
    suspend fun saveProfile(draft: ProfileDraft): Result<AuthenticationResult>
    suspend fun beginProfileEditing()
    suspend fun logout()
}

class AuthenticationRepositoryImpl(
    private val telegramLoginDataSource: TelegramLoginDataSource,
    private val telegramAuthApiDataSource: TelegramAuthApiDataSource,
    private val authenticationLocalDataSource: AuthenticationLocalDataSource,
    applicationScope: CoroutineScope
) : AuthenticationRepository {
    private val _state = MutableStateFlow<RootAuthenticationState>(RootAuthenticationState.Loading)
    override val state = _state.asStateFlow()

    init {
        applicationScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        val cached = runCatching { authenticationLocalDataSource.session.first() }.getOrNull()
        val draft = runCatching { authenticationLocalDataSource.profileDraft.first() }.getOrNull()
        if (cached == null) {
            _state.value = RootAuthenticationState.Unauthenticated()
            return
        }
        _state.value = route(cached, draft)
        try {
            val refreshed = telegramAuthApiDataSource.getCurrentSession(cached.accessToken)
            authenticationLocalDataSource.save(refreshed)
            _state.value = route(refreshed, draft)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            when (error.toAuthenticationError()) {
                is AuthenticationError.AuthorizationRejected -> {
                    authenticationLocalDataSource.clear()
                    _state.value = RootAuthenticationState.Unauthenticated(sessionExpired = true)
                }
                else -> _state.value = route(cached, draft, isOffline = true)
            }
        }
    }

    override fun startTelegramLogin(context: Context, scopes: Set<TelegramScope>) {
        try {
            telegramLoginDataSource.startLogin(context, scopes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw error.toAuthenticationError()
        }
    }

    override fun isTelegramCallback(uri: Uri): Boolean = telegramLoginDataSource.isTelegramCallback(uri)

    override suspend fun completeTelegramLogin(callbackUri: Uri): Result<AuthenticationResult> = try {
        val result = telegramAuthApiDataSource.authenticate(
            telegramLoginDataSource.consumeCallback(callbackUri)
        )
        authenticationLocalDataSource.save(result)
        val draft = authenticationLocalDataSource.profileDraft.first()
            ?: initialDraft(result)
        if (result.account.onboardingState == OnboardingState.PROFILE_REQUIRED) {
            authenticationLocalDataSource.saveDraft(draft)
        }
        _state.value = route(result, draft)
        Result.success(result)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error.toAuthenticationError())
    }

    override suspend fun saveDraft(draft: ProfileDraft) {
        authenticationLocalDataSource.saveDraft(draft)
        val current = _state.value as? RootAuthenticationState.OnboardingRequired ?: return
        _state.value = current.copy(draft = draft)
    }

    override suspend fun saveProfile(draft: ProfileDraft): Result<AuthenticationResult> {
        if (!draft.isValid) return Result.failure(IllegalArgumentException("Invalid profile draft"))
        val current = (_state.value as? RootAuthenticationState.OnboardingRequired)?.session
            ?: return Result.failure(IllegalStateException("No authenticated session"))
        return try {
            authenticationLocalDataSource.saveDraft(draft)
            val saved = telegramAuthApiDataSource.saveProfile(current.accessToken, draft)
            authenticationLocalDataSource.save(saved)
            authenticationLocalDataSource.clearDraft()
            _state.value = route(saved, null)
            Result.success(saved)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error.toAuthenticationError())
        }
    }

    override suspend fun beginProfileEditing() {
        val current = (_state.value as? RootAuthenticationState.Authenticated)?.session ?: return
        val profile = current.profile ?: return
        val draft = ProfileDraft(
            displayName = profile.displayName,
            headline = profile.headline,
            intent = profile.intent,
            topics = profile.topics.toSet(),
            avatarSource = profile.avatarSource
        )
        authenticationLocalDataSource.saveDraft(draft)
        _state.value = RootAuthenticationState.OnboardingRequired(current, draft)
    }

    override suspend fun logout() {
        try {
            val session = when (val current = _state.value) {
                is RootAuthenticationState.Authenticated -> current.session
                is RootAuthenticationState.OnboardingRequired -> current.session
                else -> authenticationLocalDataSource.session.first()
            }
            if (session != null) runCatching {
                telegramAuthApiDataSource.revokeSession(session.accessToken)
            }.onFailure { if (it is CancellationException) throw it }
            authenticationLocalDataSource.clear()
            _state.value = RootAuthenticationState.Unauthenticated()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw error.toAuthenticationError()
        }
    }

    private fun route(
        session: AuthenticationResult,
        draft: ProfileDraft?,
        isOffline: Boolean = false
    ): RootAuthenticationState = if (
        session.account.onboardingState == OnboardingState.PROFILE_COMPLETED && session.profile != null
    ) {
        RootAuthenticationState.Authenticated(session, isOffline)
    } else {
        RootAuthenticationState.OnboardingRequired(session, draft ?: initialDraft(session), isOffline)
    }

    private fun initialDraft(session: AuthenticationResult) = ProfileDraft(
        displayName = session.telegram.suggestedDisplayName(),
        avatarSource = if (session.telegram.pictureUrl == null) {
            com.markettwits.devx.tgsignin.data.model.AvatarSource.BLOOM
        } else {
            com.markettwits.devx.tgsignin.data.model.AvatarSource.TELEGRAM
        }
    )
}
