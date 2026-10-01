package com.markettwits.devx.tgsignin.data.repository

import com.markettwits.devx.tgsignin.data.datasource.AppUpdateLocalDataSource
import com.markettwits.devx.tgsignin.data.datasource.GitHubReleaseDataSource
import com.markettwits.devx.tgsignin.data.datasource.GitHubReleasePayload
import com.markettwits.devx.tgsignin.data.datasource.GitHubReleaseResponse
import com.markettwits.devx.tgsignin.data.model.AppRelease
import com.markettwits.devx.tgsignin.data.model.AppUpdateAvailability
import com.markettwits.devx.tgsignin.data.model.AppUpdateCache
import kotlinx.coroutines.CancellationException
import java.net.URI

interface AppUpdateRepository {
    suspend fun checkForUpdate(forceRefresh: Boolean = false): AppUpdateAvailability
}

class AppUpdateRepositoryImpl(
    private val remoteDataSource: GitHubReleaseDataSource,
    private val localDataSource: AppUpdateLocalDataSource,
    private val currentVersionCode: Long,
    private val clock: () -> Long = System::currentTimeMillis
) : AppUpdateRepository {
    override suspend fun checkForUpdate(forceRefresh: Boolean): AppUpdateAvailability {
        val now = clock()
        val cache = try {
            localDataSource.readCache()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            AppUpdateCache()
        }
        if (!forceRefresh && now - cache.checkedAtEpochMillis in 0 until CACHE_TTL_MILLIS) {
            return cache.release.toAvailability()
        }

        return try {
            val requestEtag = cache.etag.takeIf { cache.release != null }
            when (val response = remoteDataSource.fetchLatestRelease(requestEtag)) {
                is GitHubReleaseResponse.Modified -> {
                    val release = response.release.toAppRelease()
                    if (release == null) {
                        localDataSource.clearRelease(now)
                        AppUpdateAvailability.Unavailable
                    } else {
                        localDataSource.saveRelease(release, response.etag, now)
                        release.toAvailability()
                    }
                }

                GitHubReleaseResponse.NotModified -> {
                    localDataSource.markChecked(now)
                    cache.release.toAvailability()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            try {
                localDataSource.markChecked(now)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Update checks are best-effort and must never interrupt the app flow.
            }
            cache.release.toAvailability()
        }
    }

    private fun AppRelease?.toAvailability(): AppUpdateAvailability = when {
        this == null -> AppUpdateAvailability.Unavailable
        versionCode > currentVersionCode -> AppUpdateAvailability.Available(
            copy(notes = parseReleaseNotes(notes.joinToString("\n") { "- $it" }))
        )
        else -> AppUpdateAvailability.UpToDate
    }

    private companion object {
        const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1_000L
    }
}

internal fun GitHubReleasePayload.toAppRelease(): AppRelease? {
    if (draft || prerelease) return null
    val version = parseReleaseTag(tagName) ?: return null
    if (!isTrustedReleaseUrl(htmlUrl)) return null
    return AppRelease(
        versionName = version.versionName,
        versionCode = version.versionCode,
        tagName = tagName,
        title = title.ifBlank { "v${version.versionName}" },
        notes = parseReleaseNotes(body),
        releasePageUrl = htmlUrl,
        publishedAt = publishedAt
    )
}

internal data class ReleaseTagVersion(
    val versionName: String,
    val versionCode: Long
)

internal fun parseReleaseTag(tag: String): ReleaseTagVersion? {
    val match = RELEASE_TAG_PATTERN.matchEntire(tag.trim()) ?: return null
    val versionCode = match.groupValues[4].toLongOrNull() ?: return null
    return ReleaseTagVersion(
        versionName = match.groupValues.drop(1).take(3).joinToString("."),
        versionCode = versionCode
    )
}

internal fun parseReleaseNotes(body: String): List<String> {
    val withoutComments = HTML_LIST_ITEM_CLOSE_PATTERN.replace(
        HTML_LIST_ITEM_OPEN_PATTERN.replace(HTML_COMMENT_PATTERN.replace(body, ""), "\n- "),
        "\n"
    )
    val sections = withoutComments.lineSequence().toList()
    val changesHeading = sections.indexOfFirst { it.trim().matches(WHATS_CHANGED_HEADING) }
    val visibleLines = if (changesHeading >= 0) {
        sections.drop(changesHeading + 1).takeWhile { !it.trim().startsWith("#") }
    } else {
        sections
    }
    return visibleLines.asSequence()
        .map(String::trim)
        .filter { it.startsWith("- ") || it.startsWith("* ") }
        .map { it.drop(2).trim() }
        .filterNot { RELEASE_METADATA_PATTERN.containsMatchIn(it) }
        .map { MARKDOWN_LINK_PATTERN.replace(it, "$1") }
        .map { MARKDOWN_DECORATION_PATTERN.replace(it, "") }
        .map { HTML_TAG_PATTERN.replace(it, "") }
        .map { it.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ") }
        .filterNot { it.contains("Full Changelog", ignoreCase = true) }
        .map { it.take(MAX_NOTE_LENGTH).trim() }
        .filter(String::isNotBlank)
        .distinct()
        .take(MAX_RELEASE_NOTES)
        .toList()
}

internal fun isTrustedReleaseUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.path.startsWith(TRUSTED_RELEASE_PATH)
}.getOrDefault(false)

private val RELEASE_TAG_PATTERN = Regex(
    "^(?:v)?([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([1-9][0-9]*)$"
)
private val HTML_COMMENT_PATTERN = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))
private val HTML_LIST_ITEM_OPEN_PATTERN = Regex("<li(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE)
private val HTML_LIST_ITEM_CLOSE_PATTERN = Regex("</li>", RegexOption.IGNORE_CASE)
private val MARKDOWN_LINK_PATTERN = Regex("\\[([^]]+)]\\([^)]*\\)")
private val MARKDOWN_DECORATION_PATTERN = Regex("\\*\\*|__|`")
private val HTML_TAG_PATTERN = Regex("<[^>]+>")
private val WHATS_CHANGED_HEADING = Regex("#{2,6}\\s*What(?:'|’)s Changed\\s*", RegexOption.IGNORE_CASE)
private val RELEASE_METADATA_PATTERN = Regex(
    "^(?:\\*\\*)?(?:Version|Target Commit|APK SHA-256|Signing Certificate SHA-256|Minimum Backend API Version)(?:\\*\\*)?:",
    RegexOption.IGNORE_CASE
)
private const val TRUSTED_RELEASE_PATH = "/MarketTwits/TelegramLoginAndroidDemo/releases/"
private const val MAX_RELEASE_NOTES = 6
private const val MAX_NOTE_LENGTH = 160
