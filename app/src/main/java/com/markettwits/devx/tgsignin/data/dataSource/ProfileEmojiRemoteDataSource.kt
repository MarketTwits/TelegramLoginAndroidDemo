package com.markettwits.devx.tgsignin.data.dataSource

import com.markettwits.devx.tgsignin.data.model.ProfileEmoji
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

interface ProfileEmojiRemoteDataSource {
    suspend fun fetchCatalog(): String
    suspend fun fetchAsset(emoji: ProfileEmoji): ByteArray
}

class ProfileEmojiRemoteDataSourceImpl(
    config: TelegramLoginConfig,
    private val ioDispatcher: CoroutineDispatcher
) : ProfileEmojiRemoteDataSource {
    private val baseUrl = config.backendUrl.trimEnd('/')

    override suspend fun fetchCatalog(): String = request(
        "/api/profile-emoji-sets",
        MAX_CATALOG_BYTES
    )
        .toString(Charsets.UTF_8)

    override suspend fun fetchAsset(emoji: ProfileEmoji): ByteArray {
        require(emoji.assetPath.startsWith("/assets/profile-emojis/") && ".." !in emoji.assetPath) {
            "Unsafe profile emoji asset path"
        }
        return request(emoji.assetPath, MAX_ASSET_BYTES, retryRateLimit = true)
    }

    private suspend fun request(
        path: String,
        maxBytes: Int,
        retryRateLimit: Boolean = false
    ): ByteArray {
        var rateLimitRetries = 0
        while (true) {
            try {
                return requestOnce(path, maxBytes)
            } catch (error: ProfileEmojiHttpException) {
                if (!retryRateLimit || error.status != HTTP_TOO_MANY_REQUESTS ||
                    rateLimitRetries >= MAX_RATE_LIMIT_RETRIES
                ) {
                    throw error
                }
                rateLimitRetries += 1
                val retryDelay = (error.retryAfterMillis ?: DEFAULT_RETRY_DELAY_MILLIS)
                    .coerceIn(MIN_RETRY_DELAY_MILLIS, MAX_RETRY_DELAY_MILLIS)
                delay(retryDelay + Random.nextLong(RETRY_JITTER_MILLIS + 1))
            }
        }
    }

    private suspend fun requestOnce(path: String, maxBytes: Int): ByteArray =
        withContext(ioDispatcher) {
            val requestUrl = URL(baseUrl + path)
            val startedAt = NetworkRequestLogger.start("GET", requestUrl)
            var connection: HttpURLConnection? = null
            try {
                connection = (requestUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/json, application/x-tgsticker")
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    NetworkRequestLogger.httpFailure("GET", requestUrl, status, startedAt)
                    val retryAfterMillis = connection.getHeaderField("Retry-After")
                        ?.toLongOrNull()
                        ?.times(1_000L)
                    throw ProfileEmojiHttpException(status, retryAfterMillis)
                }
                val apiVersion = connection.getHeaderField(API_VERSION_HEADER)?.toIntOrNull()
                if (apiVersion != REQUIRED_API_VERSION) {
                    NetworkRequestLogger.incompatibleBackend(
                        "GET", requestUrl, startedAt, REQUIRED_API_VERSION, apiVersion
                    )
                    throw ProfileEmojiApiException()
                }
                connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException("Profile emoji response is too large")
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
        const val REQUIRED_API_VERSION = 7
        const val MAX_CATALOG_BYTES = 512 * 1024
        const val MAX_ASSET_BYTES = 64 * 1024
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RATE_LIMIT_RETRIES = 1
        const val MIN_RETRY_DELAY_MILLIS = 750L
        const val DEFAULT_RETRY_DELAY_MILLIS = 1_500L
        const val MAX_RETRY_DELAY_MILLIS = 60_000L
        const val RETRY_JITTER_MILLIS = 500L
    }
}

private class ProfileEmojiHttpException(
    val status: Int,
    val retryAfterMillis: Long?
) :
    IllegalStateException("Profile emoji request failed with HTTP $status")

private class ProfileEmojiApiException : IllegalStateException("Incompatible backend API")
