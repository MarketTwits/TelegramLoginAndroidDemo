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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuthenticationRepository {
    val state: StateFlow<RootAuthenticationState>

    fun startTelegramLogin(context: Context, scopes: Set<TelegramScope>)
    fun isTelegramCallback(uri: Uri): Boolean
    suspend fun completeTelegramLogin(callbackUri: Uri): Result<AuthenticationResult>
    suspend fun saveDraft(draft: ProfileDraft)
    suspend fun saveProfile(draft: ProfileDraft): Result<AuthenticationResult>
    suspend fun beginProfileEditing()
    suspend fun cancelProfileEditing()
    suspend fun updateProfileEmoji(emoji: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
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
    private val localMutationMutex = Mutex()

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
            if (_state.value.sessionOrNull?.accessToken != cached.accessToken) return
            localMutationMutex.withLock { authenticationLocalDataSource.save(refreshed) }
            val current = _state.value
            _state.value = if (current.isProfileEditing) {
                RootAuthenticationState.OnboardingRequired(
                    session = refreshed,
                    draft = (current as RootAuthenticationState.OnboardingRequired).draft
                )
            } else {
                route(refreshed, draft)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (_state.value.sessionOrNull?.accessToken != cached.accessToken) return
            when (error.toAuthenticationError()) {
                is AuthenticationError.AuthorizationRejected -> {
                    localMutationMutex.withLock { authenticationLocalDataSource.clear() }
                    _state.value = RootAuthenticationState.Unauthenticated(sessionExpired = true)
                }
                else -> {
                    val current = _state.value
                    _state.value = if (current.isProfileEditing) {
                        (current as RootAuthenticationState.OnboardingRequired).copy(isOffline = true)
                    } else {
                        route(cached, draft, isOffline = true)
                    }
                }
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
        localMutationMutex.withLock { authenticationLocalDataSource.save(result) }
        val draft = authenticationLocalDataSource.profileDraft.first()
            ?: initialDraft(result)
        if (result.account.onboardingState == OnboardingState.PROFILE_REQUIRED) {
            localMutationMutex.withLock { authenticationLocalDataSource.saveDraft(draft) }
        }
        _state.value = route(result, draft)
        Result.success(result)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error.toAuthenticationError())
    }

    override suspend fun saveDraft(draft: ProfileDraft) {
        val current = _state.value as? RootAuthenticationState.OnboardingRequired ?: return
        _state.value = current.copy(draft = draft)
        localMutationMutex.withLock { authenticationLocalDataSource.saveDraft(draft) }
    }

    override suspend fun saveProfile(draft: ProfileDraft): Result<AuthenticationResult> {
        if (!draft.isValid) return Result.failure(IllegalArgumentException("Invalid profile draft"))
        val current = (_state.value as? RootAuthenticationState.OnboardingRequired)?.session
            ?: return Result.failure(IllegalStateException("No authenticated session"))
        return try {
            localMutationMutex.withLock { authenticationLocalDataSource.saveDraft(draft) }
            val saved = telegramAuthApiDataSource.saveProfile(current.accessToken, draft)
            localMutationMutex.withLock {
                authenticationLocalDataSource.save(saved)
                authenticationLocalDataSource.clearDraft()
            }
            _state.value = route(saved, null)
            Result.success(saved)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val mapped = error.toAuthenticationError()
            if (mapped is AuthenticationError.AuthorizationRejected) {
                localMutationMutex.withLock { authenticationLocalDataSource.clear() }
                _state.value = RootAuthenticationState.Unauthenticated(sessionExpired = true)
            }
            Result.failure(mapped)
        }
    }

    override suspend fun beginProfileEditing() {
        val current = (_state.value as? RootAuthenticationState.Authenticated)?.session ?: return
        val profile = current.profile ?: return
        val draft = profile.toDraft()
        localMutationMutex.withLock { authenticationLocalDataSource.saveDraft(draft) }
        _state.value = RootAuthenticationState.OnboardingRequired(current, draft)
    }

    override suspend fun cancelProfileEditing() {
        val current = _state.value as? RootAuthenticationState.OnboardingRequired ?: return
        if (current.session.profile == null) return
        localMutationMutex.withLock { authenticationLocalDataSource.clearDraft() }
        _state.value = RootAuthenticationState.Authenticated(
            session = current.session,
            isOffline = current.isOffline
        )
    }

    override suspend fun updateProfileEmoji(emoji: String): Result<Unit> {
        if (emoji !in com.markettwits.devx.tgsignin.data.model.PROFILE_EMOJIS) {
            return Result.failure(IllegalArgumentException("Unsupported profile emoji"))
        }
        val current = _state.value as? RootAuthenticationState.Authenticated
            ?: return Result.failure(IllegalStateException("No profile to update"))
        val profile = current.session.profile
            ?: return Result.failure(IllegalStateException("No profile to update"))
        return try {
            val saved = telegramAuthApiDataSource.saveProfile(
                current.session.accessToken,
                profile.toDraft().copy(emoji = emoji)
            )
            localMutationMutex.withLock { authenticationLocalDataSource.save(saved) }
            _state.value = RootAuthenticationState.Authenticated(saved, current.isOffline)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val mapped = error.toAuthenticationError()
            if (mapped is AuthenticationError.AuthorizationRejected) {
                localMutationMutex.withLock { authenticationLocalDataSource.clear() }
                _state.value = RootAuthenticationState.Unauthenticated(sessionExpired = true)
            }
            Result.failure(mapped)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val current = (_state.value as? RootAuthenticationState.Authenticated)?.session
            ?: return Result.failure(IllegalStateException("No account to delete"))
        return try {
            telegramAuthApiDataSource.deleteAccount(current.accessToken)
            localMutationMutex.withLock { authenticationLocalDataSource.clear() }
            _state.value = RootAuthenticationState.Unauthenticated()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val mapped = error.toAuthenticationError()
            if (mapped is AuthenticationError.AuthorizationRejected) {
                localMutationMutex.withLock { authenticationLocalDataSource.clear() }
                _state.value = RootAuthenticationState.Unauthenticated(sessionExpired = true)
            }
            Result.failure(mapped)
        }
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
            localMutationMutex.withLock { authenticationLocalDataSource.clear() }
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
        avatarSource = com.markettwits.devx.tgsignin.data.model.AvatarSource.TELEGRAM,
        emoji = com.markettwits.devx.tgsignin.data.model.PROFILE_EMOJIS.first()
    )
}

private fun com.markettwits.devx.tgsignin.data.model.ServiceProfile.toDraft() = ProfileDraft(
    displayName = displayName,
    headline = headline,
    intent = intent,
    topics = topics.toSet(),
    avatarSource = com.markettwits.devx.tgsignin.data.model.AvatarSource.TELEGRAM,
    emoji = emoji
)

private val RootAuthenticationState.isProfileEditing: Boolean
    get() = this is RootAuthenticationState.OnboardingRequired && session.profile != null

private val RootAuthenticationState.sessionOrNull: AuthenticationResult?
    get() = when (this) {
        is RootAuthenticationState.Authenticated -> session
        is RootAuthenticationState.OnboardingRequired -> session
        is RootAuthenticationState.RecoverableError -> cachedSession
        RootAuthenticationState.Loading,
        is RootAuthenticationState.Unauthenticated -> null
    }
