package com.markettwits.devx.tgsignin.data.dataSource

import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.markettwits.devx.tgsignin.data.model.AuthenticatedSession
import com.markettwits.devx.tgsignin.data.model.TelegramUser
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig

/** Sends the untrusted Telegram ID token to the application server for verification. */
class TelegramAuthApiDataSourceImpl(
    config: TelegramLoginConfig,
    private val ioDispatcher: CoroutineDispatcher
) : TelegramAuthApiDataSource {
    private val baseUrl = config.backendUrl.trimEnd(PATH_SEPARATOR)

    override suspend fun authenticate(idToken: String): AuthenticatedSession = withContext(ioDispatcher) {
        val connection = openConnection(AUTHENTICATE_PATH, HTTP_POST).apply { doOutput = true }
        try {
            connection.setRequestProperty(HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE)
            val requestBody = JSONObject().put(JSON_ID_TOKEN, idToken).toString()
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(requestBody)
            }
            val statusCode = connection.responseCode
            val body = (if (statusCode in SUCCESS_STATUS_CODES) connection.inputStream else connection.errorStream)
                ?.let { stream -> InputStreamReader(stream, StandardCharsets.UTF_8).use { it.readText() } }
                .orEmpty()
            if (statusCode !in SUCCESS_STATUS_CODES) throw BackendHttpException(statusCode)

            parseSession(body, JSONObject(body).getString(JSON_SESSION_TOKEN))
        } catch (error: IOException) {
            throw BackendNetworkException(error)
        } catch (error: org.json.JSONException) {
            throw BackendResponseException(error)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun getCurrentSession(accessToken: String): AuthenticatedSession =
        withContext(ioDispatcher) {
            val connection = openConnection(SESSION_PATH, HTTP_GET).apply {
                setRequestProperty(HEADER_AUTHORIZATION, "$BEARER_PREFIX$accessToken")
            }
            try {
                val statusCode = connection.responseCode
                val body = (if (statusCode in SUCCESS_STATUS_CODES) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.let { stream ->
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { it.readText() }
                }.orEmpty()
                if (statusCode !in SUCCESS_STATUS_CODES) throw BackendHttpException(statusCode)
                parseSession(body, accessToken)
            } catch (error: IOException) {
                throw BackendNetworkException(error)
            } catch (error: org.json.JSONException) {
                throw BackendResponseException(error)
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun revokeSession(accessToken: String): Unit = withContext(ioDispatcher) {
        val connection = openConnection(SESSION_PATH, HTTP_DELETE).apply {
            setRequestProperty(HEADER_AUTHORIZATION, "$BEARER_PREFIX$accessToken")
        }
        try {
            val statusCode = connection.responseCode
            if (statusCode !in SUCCESS_STATUS_CODES) throw BackendHttpException(statusCode)
        } catch (error: IOException) {
            throw BackendNetworkException(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, method: String): HttpURLConnection = try {
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty(HEADER_ACCEPT, JSON_MEDIA_TYPE)
        }
    } catch (error: IOException) {
        throw BackendNetworkException(error)
    }

    private fun JSONObject.optionalString(key: String): String? =
        optString(key).takeIf(String::isNotBlank)

    private fun parseSession(body: String, accessToken: String): AuthenticatedSession {
        val user = JSONObject(body).getJSONObject(JSON_USER)
        return AuthenticatedSession(
            accessToken = accessToken,
            user = TelegramUser(
                id = user.getString(JSON_ID),
                name = user.optionalString(JSON_NAME),
                givenName = user.optionalString(JSON_GIVEN_NAME),
                familyName = user.optionalString(JSON_FAMILY_NAME),
                username = user.optionalString(JSON_USERNAME),
                phoneNumber = user.optionalString(JSON_PHONE_NUMBER),
                phoneVerified = user.optBoolean(JSON_PHONE_VERIFIED, false),
                pictureUrl = user.optionalString(JSON_PICTURE)
            )
        )
    }

    private companion object {
        const val AUTHENTICATE_PATH = "/auth/telegram"
        const val SESSION_PATH = "/auth/session"
        const val PATH_SEPARATOR = '/'
        const val HTTP_POST = "POST"
        const val HTTP_GET = "GET"
        const val HTTP_DELETE = "DELETE"
        const val NETWORK_TIMEOUT_MS = 15_000
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val JSON_MEDIA_TYPE = "application/json"
        const val JSON_CONTENT_TYPE = "$JSON_MEDIA_TYPE; charset=utf-8"
        const val BEARER_PREFIX = "Bearer "
        const val JSON_ID_TOKEN = "idToken"
        const val JSON_USER = "user"
        const val JSON_SESSION_TOKEN = "sessionToken"
        const val JSON_ID = "id"
        const val JSON_NAME = "name"
        const val JSON_GIVEN_NAME = "givenName"
        const val JSON_FAMILY_NAME = "familyName"
        const val JSON_USERNAME = "username"
        const val JSON_PHONE_NUMBER = "phoneNumber"
        const val JSON_PHONE_VERIFIED = "phoneVerified"
        const val JSON_PICTURE = "picture"
        val SUCCESS_STATUS_CODES = 200..299
    }
}
