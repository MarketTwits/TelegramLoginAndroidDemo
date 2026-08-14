package com.markettwits.devx.tgsignin.data.dataSource

import com.markettwits.devx.tgsignin.data.model.ProfileBadge
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface ProfileBadgeRemoteDataSource {
    suspend fun fetchCatalog(): String
    suspend fun fetchAsset(badge: ProfileBadge): ByteArray
}

class ProfileBadgeRemoteDataSourceImpl(
    config: TelegramLoginConfig,
    private val ioDispatcher: CoroutineDispatcher
) : ProfileBadgeRemoteDataSource {
    private val baseUrl = config.backendUrl.trimEnd('/')

    override suspend fun fetchCatalog(): String = request("/api/profile-badges", MAX_CATALOG_BYTES)
        .toString(Charsets.UTF_8)

    override suspend fun fetchAsset(badge: ProfileBadge): ByteArray {
        require(badge.assetPath.startsWith("/assets/profile-badges/") && ".." !in badge.assetPath) {
            "Unsafe profile badge asset path"
        }
        return request(badge.assetPath, MAX_ASSET_BYTES)
    }

    private suspend fun request(path: String, maxBytes: Int): ByteArray = withContext(ioDispatcher) {
        val requestUrl = URL(baseUrl + path)
        val startedAt = NetworkRequestLogger.start("GET", requestUrl)
        var connection: HttpURLConnection? = null
        try {
            connection = (requestUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json, application/x-tgsticker, image/webp")
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                NetworkRequestLogger.httpFailure("GET", requestUrl, status, startedAt)
                throw ProfileBadgeHttpException(status)
            }
            val apiVersion = connection.getHeaderField(API_VERSION_HEADER)?.toIntOrNull()
            if (apiVersion != REQUIRED_API_VERSION) {
                NetworkRequestLogger.incompatibleBackend(
                    "GET", requestUrl, startedAt, REQUIRED_API_VERSION, apiVersion
                )
                throw ProfileBadgeApiException()
            }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("Profile badge response is too large")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }.also { NetworkRequestLogger.success("GET", requestUrl, status, startedAt) }
        } catch (error: IOException) {
            NetworkRequestLogger.transportFailure("GET", requestUrl, startedAt, error)
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val API_VERSION_HEADER = "X-Telegram-Bloom-Api-Version"
        const val REQUIRED_API_VERSION = 6
        const val MAX_CATALOG_BYTES = 64 * 1024
        const val MAX_ASSET_BYTES = 64 * 1024
    }
}

private class ProfileBadgeHttpException(status: Int) :
    IllegalStateException("Profile badge request failed with HTTP $status")

private class ProfileBadgeApiException : IllegalStateException("Incompatible backend API")
