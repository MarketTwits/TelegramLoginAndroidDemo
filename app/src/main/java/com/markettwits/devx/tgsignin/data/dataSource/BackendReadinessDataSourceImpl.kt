package com.markettwits.devx.tgsignin.data.dataSource

import com.markettwits.devx.tgsignin.data.model.BackendReadiness
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class BackendReadinessDataSourceImpl(
    config: TelegramLoginConfig,
    private val ioDispatcher: CoroutineDispatcher
) : BackendReadinessDataSource {
    private val readinessUrl = URL(config.backendUrl.trimEnd(PATH_SEPARATOR) + READINESS_PATH)
    private val appToken = config.appToken

    override suspend fun checkReadiness(): BackendReadiness = withContext(ioDispatcher) {
        val startedAt = NetworkRequestLogger.start(HTTP_GET, readinessUrl)
        var connection: HttpURLConnection? = null
        try {
            connection = openConnection()
            val statusCode = connection.responseCode
            if (statusCode !in SUCCESS_STATUS_CODES) {
                val requestId = connection.getHeaderField("X-Request-Id")
                NetworkRequestLogger.httpFailure(
                    HTTP_GET,
                    readinessUrl,
                    statusCode,
                    startedAt,
                    requestId = requestId
                )
                throw BackendHttpException(statusCode, requestId = requestId)
            }

            val body = connection.inputStream?.let { stream ->
                InputStreamReader(stream, StandardCharsets.UTF_8).use { it.readText() }
            }.orEmpty()
            parseResponse(body).also {
                NetworkRequestLogger.success(HTTP_GET, readinessUrl, statusCode, startedAt)
            }
        } catch (error: IOException) {
            NetworkRequestLogger.transportFailure(HTTP_GET, readinessUrl, startedAt, error)
            throw BackendNetworkException(error)
        } catch (error: JSONException) {
            NetworkRequestLogger.invalidResponse(HTTP_GET, readinessUrl, startedAt, error)
            throw BackendResponseException(error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnection(): HttpURLConnection =
        (readinessUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = HTTP_GET
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty(HEADER_ACCEPT, JSON_MEDIA_TYPE)
            if (appToken.isNotBlank()) setRequestProperty("X-App-Token", appToken)
        }

    private fun parseResponse(body: String): BackendReadiness {
        val response = JSONObject(body)
        return BackendReadiness(
            serviceReady = response.optString(JSON_STATUS) == STATUS_READY,
            databaseConnected = response.optString(JSON_DATABASE) == DATABASE_CONNECTED,
            telegramConfigured = response.optString(JSON_TELEGRAM) == TELEGRAM_CONFIGURED,
            apiVersion = response.optInt(JSON_API_VERSION, 0)
        )
    }

    private companion object {
        const val READINESS_PATH = "/api/health/ready"
        const val PATH_SEPARATOR = '/'
        const val HTTP_GET = "GET"
        const val NETWORK_TIMEOUT_MS = 15_000
        const val HEADER_ACCEPT = "Accept"
        const val JSON_MEDIA_TYPE = "application/json"
        const val JSON_STATUS = "status"
        const val JSON_DATABASE = "database"
        const val JSON_TELEGRAM = "telegram"
        const val JSON_API_VERSION = "apiVersion"
        const val STATUS_READY = "ready"
        const val DATABASE_CONNECTED = "connected"
        const val TELEGRAM_CONFIGURED = "configured"
        val SUCCESS_STATUS_CODES = 200..299
    }
}
