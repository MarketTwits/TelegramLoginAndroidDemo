package com.markettwits.devx.tgsignin.data.repository

import android.content.ActivityNotFoundException
import com.markettwits.devx.tgsignin.data.datasource.BackendConfigurationException
import com.markettwits.devx.tgsignin.data.datasource.BackendHttpException
import com.markettwits.devx.tgsignin.data.datasource.BackendIncompatibleException
import com.markettwits.devx.tgsignin.data.datasource.BackendNetworkException
import com.markettwits.devx.tgsignin.data.datasource.BackendResponseException
import com.markettwits.devx.tgsignin.data.datasource.TelegramConfigurationException
import com.markettwits.devx.tgsignin.data.datasource.TelegramLoginException
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private val SERVER_ERROR_STATUS_CODES = 500..599

internal fun Throwable.toAuthenticationError(): AuthenticationError {
    if (this is AuthenticationError) return this
    val causes = generateSequence(this as Throwable?) { it.cause }.toList()

    causes.filterIsInstance<BackendHttpException>().firstOrNull()?.let { error ->
        return when (error.statusCode) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> AuthenticationError.AuthorizationRejected(error)
            HTTP_TOO_MANY_REQUESTS -> AuthenticationError.TooManyRequests(error)
            in SERVER_ERROR_STATUS_CODES -> AuthenticationError.ServerUnavailable(error)
            else -> AuthenticationError.RequestRejected(error.statusCode, error)
        }
    }
    causes.filterIsInstance<SocketTimeoutException>().firstOrNull()?.let {
        return AuthenticationError.Timeout(it)
    }
    causes.firstOrNull { it is UnknownHostException || it is NoRouteToHostException }?.let {
        return AuthenticationError.NetworkUnavailable(it)
    }
    causes.filterIsInstance<ConnectException>().firstOrNull()?.let {
        return AuthenticationError.ConnectionFailed(it)
    }
    causes.filterIsInstance<SSLException>().firstOrNull()?.let {
        return AuthenticationError.SecureConnectionFailed(it)
    }
    causes.filterIsInstance<BackendResponseException>().firstOrNull()?.let {
        return AuthenticationError.InvalidServerResponse(it)
    }
    causes.filterIsInstance<BackendIncompatibleException>().firstOrNull()?.let {
        return AuthenticationError.IncompatibleBackend(it)
    }
    causes.firstOrNull {
        it is BackendConfigurationException ||
            it is TelegramConfigurationException ||
            it is MalformedURLException
    }?.let {
        return AuthenticationError.InvalidConfiguration(it)
    }
    causes.filterIsInstance<BackendNetworkException>().firstOrNull()?.let {
        return AuthenticationError.ConnectionFailed(it)
    }
    causes.filterIsInstance<TelegramLoginException>().firstOrNull()?.let {
        return AuthenticationError.TelegramSdk(it)
    }
    causes.firstOrNull { it is ActivityNotFoundException || it is SecurityException }?.let {
        return AuthenticationError.BrowserUnavailable(it)
    }
    causes.filterIsInstance<IOException>().firstOrNull()?.let {
        return AuthenticationError.LocalStorage(it)
    }
    return AuthenticationError.Unknown(this)
}
