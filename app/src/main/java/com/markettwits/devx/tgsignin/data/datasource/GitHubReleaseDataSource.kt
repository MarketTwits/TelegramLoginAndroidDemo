package com.markettwits.devx.tgsignin.data.datasource

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleasePayload(
    val tagName: String,
    val title: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val draft: Boolean,
    val prerelease: Boolean
)

sealed interface GitHubReleaseResponse {
    data class Modified(
        val release: GitHubReleasePayload,
        val etag: String?
    ) : GitHubReleaseResponse

    data object NotModified : GitHubReleaseResponse
}

interface GitHubReleaseDataSource {
    suspend fun fetchLatestRelease(etag: String?): GitHubReleaseResponse
}

class GitHubReleaseDataSourceImpl(
    latestReleaseUrl: String,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher
) : GitHubReleaseDataSource {
    private val initialUrl = URL(latestReleaseUrl)

    init {
        require(initialUrl.protocol == HTTPS_SCHEME && initialUrl.host == GITHUB_API_HOST) {
            "GitHub release API URL must use api.github.com over HTTPS"
        }
    }

    override suspend fun fetchLatestRelease(etag: String?): GitHubReleaseResponse =
        withContext(ioDispatcher) {
            var requestUrl = initialUrl
            repeat(MAX_REDIRECTS + 1) { redirectIndex ->
                val startedAt = NetworkRequestLogger.start(HTTP_GET, requestUrl)
                var connection: HttpURLConnection? = null
                try {
                    connection = openConnection(requestUrl, etag)
                    val status = connection.responseCode
                    when {
                        status == HTTP_NOT_MODIFIED -> {
                            NetworkRequestLogger.success(HTTP_GET, requestUrl, status, startedAt)
                            return@withContext GitHubReleaseResponse.NotModified
                        }

                        status in REDIRECT_STATUS_CODES -> {
                            NetworkRequestLogger.success(HTTP_GET, requestUrl, status, startedAt)
                            if (redirectIndex == MAX_REDIRECTS) {
                                throw IOException("Too many GitHub API redirects")
                            }
                            requestUrl = validatedRedirect(requestUrl, connection)
                        }

                        status in SUCCESS_STATUS_CODES -> {
                            val body = connection.inputStream.use(::readBounded)
                                .toString(Charsets.UTF_8)
                            val release = parseRelease(body)
                            NetworkRequestLogger.success(HTTP_GET, requestUrl, status, startedAt)
                            return@withContext GitHubReleaseResponse.Modified(
                                release = release,
                                etag = connection.getHeaderField(HEADER_ETAG)
                            )
                        }

                        else -> {
                            NetworkRequestLogger.httpFailure(
                                HTTP_GET,
                                requestUrl,
                                status,
                                startedAt
                            )
                            throw GitHubReleaseHttpException(status)
                        }
                    }
                } catch (error: GitHubReleaseHttpException) {
                    throw error
                } catch (error: JSONException) {
                    NetworkRequestLogger.invalidResponse(HTTP_GET, requestUrl, startedAt, error)
                    throw GitHubReleaseResponseException(error)
                } catch (error: IOException) {
                    NetworkRequestLogger.transportFailure(HTTP_GET, requestUrl, startedAt, error)
                    throw error
                } finally {
                    connection?.disconnect()
                }
            }
            error("Unreachable redirect state")
        }

    private fun openConnection(url: URL, etag: String?): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = HTTP_GET
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            instanceFollowRedirects = false
            setRequestProperty(HEADER_ACCEPT, GITHUB_JSON_MEDIA_TYPE)
            setRequestProperty(HEADER_API_VERSION, GITHUB_API_VERSION)
            setRequestProperty(HEADER_USER_AGENT, userAgent)
            etag?.takeIf(String::isNotBlank)?.let { setRequestProperty(HEADER_IF_NONE_MATCH, it) }
        }

    private fun validatedRedirect(currentUrl: URL, connection: HttpURLConnection): URL {
        val location = connection.getHeaderField(HEADER_LOCATION)
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("GitHub API redirect is missing Location")
        val redirected = currentUrl.toURI().resolve(location).toURL()
        if (redirected.protocol != HTTPS_SCHEME || redirected.host != GITHUB_API_HOST) {
            throw IOException("GitHub API redirected outside api.github.com HTTPS")
        }
        return redirected
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) throw IOException("GitHub release response is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun parseRelease(body: String): GitHubReleasePayload {
        val json = JSONObject(body)
        return GitHubReleasePayload(
            tagName = json.getString(JSON_TAG_NAME),
            title = json.nullableString(JSON_NAME).orEmpty(),
            body = json.nullableString(JSON_BODY).orEmpty().take(MAX_RELEASE_BODY_CHARS),
            htmlUrl = json.getString(JSON_HTML_URL),
            publishedAt = json.nullableString(JSON_PUBLISHED_AT),
            draft = json.optBoolean(JSON_DRAFT, false),
            prerelease = json.optBoolean(JSON_PRERELEASE, false)
        )
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private companion object {
        const val HTTP_GET = "GET"
        const val HTTPS_SCHEME = "https"
        const val GITHUB_API_HOST = "api.github.com"
        const val GITHUB_JSON_MEDIA_TYPE = "application/vnd.github+json"
        const val GITHUB_API_VERSION = "2026-03-10"
        const val NETWORK_TIMEOUT_MILLIS = 5_000
        const val MAX_RESPONSE_BYTES = 256 * 1024
        const val MAX_RELEASE_BODY_CHARS = 20 * 1024
        const val MAX_REDIRECTS = 3
        const val HTTP_NOT_MODIFIED = 304
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_API_VERSION = "X-GitHub-Api-Version"
        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_IF_NONE_MATCH = "If-None-Match"
        const val HEADER_ETAG = "ETag"
        const val HEADER_LOCATION = "Location"
        const val JSON_TAG_NAME = "tag_name"
        const val JSON_NAME = "name"
        const val JSON_BODY = "body"
        const val JSON_HTML_URL = "html_url"
        const val JSON_PUBLISHED_AT = "published_at"
        const val JSON_DRAFT = "draft"
        const val JSON_PRERELEASE = "prerelease"
        val SUCCESS_STATUS_CODES = 200..299
        val REDIRECT_STATUS_CODES = setOf(301, 302, 307, 308)
    }
}

class GitHubReleaseHttpException(val status: Int) :
    IOException("GitHub release request failed with HTTP $status")

class GitHubReleaseResponseException(cause: Throwable) :
    IOException("GitHub release response is invalid", cause)
