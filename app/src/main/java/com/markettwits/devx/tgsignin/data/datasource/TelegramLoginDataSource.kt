package com.markettwits.devx.tgsignin.data.datasource

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.telegram.login.TelegramLogin
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface TelegramLoginDataSource {
    fun startLogin(context: Context, scopes: Set<TelegramScope>)
    suspend fun consumeCallback(uri: Uri): String
    fun isTelegramCallback(uri: Uri): Boolean
}

/** Owns all interaction with the stateful Telegram Login SDK. */
class TelegramLoginDataSourceImpl(
    private val config: TelegramLoginConfig
) : TelegramLoginDataSource {
    private val redirectUri = config.redirectUri.toUri()

    override fun startLogin(context: Context, scopes: Set<TelegramScope>) {
        configure(scopes)
        TelegramLogin.startLogin(context)
    }

    override suspend fun consumeCallback(uri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            // The Activity can be recreated while the browser is open, so initialize the SDK again.
            configure(emptySet())
            TelegramLogin.handleLoginResponse(
                uri = uri,
                onSuccess = { result ->
                    if (continuation.isActive) continuation.resume(result.idToken)
                },
                onError = { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(TelegramLoginException(error.message))
                    }
                }
            )
        }

    override fun isTelegramCallback(uri: Uri): Boolean =
        uri.scheme.equals(redirectUri.scheme, ignoreCase = true) &&
                uri.host.equals(redirectUri.host, ignoreCase = true) &&
                uri.path == redirectUri.path

    private fun configure(scopes: Set<TelegramScope>) {
        TelegramLogin.init(config.clientId, config.redirectUri, scopes.map(TelegramScope::value))
    }
}

class TelegramLoginException(message: String) : Exception(message)

class TelegramConfigurationException : Exception()
