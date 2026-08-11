package com.markettwits.devx.tgsignin.data.telegram

import java.net.URI

data class TelegramLoginConfig(
    val clientId: String,
    val redirectUri: String,
    val redirectHost: String,
    val backendUrl: String
) {
    init {
        require(clientId.isNotBlank()) { ERROR_CLIENT_ID_REQUIRED }
        require(redirectHost.isNotBlank()) { ERROR_REDIRECT_HOST_REQUIRED }

        val parsedRedirectUri = redirectUri.parseAbsoluteHttpUri(ERROR_REDIRECT_URI_INVALID)
        require(parsedRedirectUri.scheme.equals(HTTPS_SCHEME, ignoreCase = true)) {
            ERROR_REDIRECT_URI_HTTPS
        }
        require(parsedRedirectUri.host.equals(redirectHost, ignoreCase = true)) {
            ERROR_REDIRECT_HOST_MISMATCH
        }
        backendUrl.parseAbsoluteHttpUri(ERROR_BACKEND_URL_INVALID)
    }

    private fun String.parseAbsoluteHttpUri(errorMessage: String): URI {
        val uri = runCatching(::URI).getOrElse { throw IllegalArgumentException(errorMessage, it) }
        require(
            uri.isAbsolute &&
                uri.host != null &&
                (uri.scheme.equals(HTTP_SCHEME, ignoreCase = true) ||
                    uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true))
        ) { errorMessage }
        return uri
    }

    private companion object {
        const val HTTP_SCHEME = "http"
        const val HTTPS_SCHEME = "https"
        const val ERROR_CLIENT_ID_REQUIRED = "Telegram client ID is required"
        const val ERROR_REDIRECT_HOST_REQUIRED = "Telegram redirect host is required"
        const val ERROR_REDIRECT_URI_INVALID = "Telegram redirect URI must be an absolute HTTP(S) URI"
        const val ERROR_REDIRECT_URI_HTTPS = "Telegram redirect URI must use HTTPS"
        const val ERROR_REDIRECT_HOST_MISMATCH = "Telegram redirect URI host must match redirectHost"
        const val ERROR_BACKEND_URL_INVALID = "Telegram backend URL must be an absolute HTTP(S) URI"
    }
}
