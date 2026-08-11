package com.markettwits.devx.tgsignin.data.dataSource

import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import com.markettwits.devx.tgsignin.data.model.BackendReadiness
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig

class BackendReadinessDataSourceImpl(
    config: TelegramLoginConfig,
    private val ioDispatcher: CoroutineDispatcher
) : BackendReadinessDataSource {
    private val readinessUrl = config.backendUrl.trimEnd(PATH_SEPARATOR) + READINESS_PATH

    override suspend fun checkReadiness(): BackendReadiness = withContext(ioDispatcher) {
        val connection = openConnection()
        try {
            val statusCode = connection.responseCode
            if (statusCode !in SUCCESS_STATUS_CODES) throw BackendHttpException(statusCode)

            val body = connection.inputStream?.let { stream ->
                InputStreamReader(stream, StandardCharsets.UTF_8).use { it.readText() }
            }.orEmpty()
            parseResponse(body)
        } catch (error: IOException) {
            throw BackendNetworkException(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(): HttpURLConnection = try {
        (URL(readinessUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = HTTP_GET
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty(HEADER_ACCEPT, JSON_MEDIA_TYPE)
        }
    } catch (error: IOException) {
        throw BackendNetworkException(error)
    }

    private fun parseResponse(body: String): BackendReadiness = try {
        val response = JSONObject(body)
        BackendReadiness(
            serviceReady = response.optString(JSON_STATUS) == STATUS_READY,
            databaseConnected = response.optString(JSON_DATABASE) == DATABASE_CONNECTED,
            telegramConfigured = response.optString(JSON_TELEGRAM) == TELEGRAM_CONFIGURED
        )
    } catch (error: JSONException) {
        throw BackendResponseException(error)
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
        const val STATUS_READY = "ready"
        const val DATABASE_CONNECTED = "connected"
        const val TELEGRAM_CONFIGURED = "configured"
        val SUCCESS_STATUS_CODES = 200..299
    }
}
