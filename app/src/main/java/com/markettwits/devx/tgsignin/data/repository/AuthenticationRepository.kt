package com.markettwits.devx.tgsignin.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.markettwits.devx.tgsignin.data.dataSource.AuthenticationLocalDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramLoginDataSource
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import com.markettwits.devx.tgsignin.data.model.TelegramUser

interface AuthenticationRepository {
    val currentUser: StateFlow<TelegramUser?>
    val isSessionRestored: StateFlow<Boolean>

    fun startTelegramLogin(context: Context, scopes: Set<TelegramScope>)
    fun isTelegramCallback(uri: Uri): Boolean
    suspend fun completeTelegramLogin(callbackUri: Uri): Result<TelegramUser>
    suspend fun logout()
}

class AuthenticationRepositoryImpl(
    private val telegramLoginDataSource: TelegramLoginDataSource,
    private val telegramAuthApiDataSource: TelegramAuthApiDataSource,
    private val authenticationLocalDataSource: AuthenticationLocalDataSource,
    applicationScope: CoroutineScope
) : AuthenticationRepository {
    private val _currentUser = MutableStateFlow<TelegramUser?>(null)
    override val currentUser = _currentUser.asStateFlow()
    private val _isSessionRestored = MutableStateFlow(false)
    override val isSessionRestored = _isSessionRestored.asStateFlow()

    init {
        applicationScope.launch {
            try {
                val cachedSession = authenticationLocalDataSource.session.first()
                if (cachedSession != null) {
                    val verifiedSession = telegramAuthApiDataSource.getCurrentSession(
                        cachedSession.accessToken
                    )
                    authenticationLocalDataSource.save(verifiedSession)
                    _currentUser.value = verifiedSession.user
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                authenticationLocalDataSource.clear()
                _currentUser.value = null
            } finally {
                _isSessionRestored.value = true
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

    override suspend fun completeTelegramLogin(callbackUri: Uri): Result<TelegramUser> = try {
        val idToken = telegramLoginDataSource.consumeCallback(callbackUri)
        val session = telegramAuthApiDataSource.authenticate(idToken)
        authenticationLocalDataSource.save(session)
        _currentUser.value = session.user
        Result.success(session.user)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error.toAuthenticationError())
    }

    override suspend fun logout() {
        try {
            val session = authenticationLocalDataSource.session.first()
            if (session != null) {
                // Local logout must remain available offline. Revocation is attempted
                // first; an unreachable backend must not trap the user in the account.
                runCatching { telegramAuthApiDataSource.revokeSession(session.accessToken) }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                    }
            }
            authenticationLocalDataSource.clear()
            _currentUser.value = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw error.toAuthenticationError()
        }
    }
}
