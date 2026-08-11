package com.markettwits.devx.tgsignin.data.model

sealed class AuthenticationError(cause: Throwable? = null) : Exception(cause) {
    class NetworkUnavailable(cause: Throwable) : AuthenticationError(cause)
    class ConnectionFailed(cause: Throwable) : AuthenticationError(cause)
    class Timeout(cause: Throwable) : AuthenticationError(cause)
    class SecureConnectionFailed(cause: Throwable) : AuthenticationError(cause)
    class AuthorizationRejected(cause: Throwable? = null) : AuthenticationError(cause)
    class TooManyRequests(cause: Throwable? = null) : AuthenticationError(cause)
    class ServerUnavailable(cause: Throwable? = null) : AuthenticationError(cause)
    class RequestRejected(val statusCode: Int, cause: Throwable? = null) : AuthenticationError(cause)
    class InvalidServerResponse(cause: Throwable) : AuthenticationError(cause)
    class InvalidConfiguration(cause: Throwable? = null) : AuthenticationError(cause)
    class TelegramSdk(cause: Throwable) : AuthenticationError(cause)
    class BrowserUnavailable(cause: Throwable) : AuthenticationError(cause)
    class LocalStorage(cause: Throwable) : AuthenticationError(cause)
    class Unknown(cause: Throwable) : AuthenticationError(cause)
}
