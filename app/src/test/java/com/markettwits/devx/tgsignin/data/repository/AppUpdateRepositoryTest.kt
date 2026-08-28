package com.markettwits.devx.tgsignin.data.repository

import com.markettwits.devx.tgsignin.data.datasource.AppUpdateLocalDataSource
import com.markettwits.devx.tgsignin.data.datasource.GitHubReleaseDataSource
import com.markettwits.devx.tgsignin.data.datasource.GitHubReleasePayload
import com.markettwits.devx.tgsignin.data.datasource.GitHubReleaseResponse
import com.markettwits.devx.tgsignin.data.model.AppRelease
import com.markettwits.devx.tgsignin.data.model.AppUpdateAvailability
import com.markettwits.devx.tgsignin.data.model.AppUpdateCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AppUpdateRepositoryTest {
    @Test
    fun `newer release code is available`() = runBlocking {
        val local = FakeAppUpdateLocalDataSource()
        val repository = AppUpdateRepositoryImpl(
            remoteDataSource = FakeGitHubReleaseDataSource(
                GitHubReleaseResponse.Modified(payload(tag = "1.0.2.2"), "etag-2")
            ),
            localDataSource = local,
            currentVersionCode = 1,
            clock = { NOW }
        )

        val result = repository.checkForUpdate() as AppUpdateAvailability.Available

        assertEquals("1.0.2", result.release.versionName)
        assertEquals(2L, result.release.versionCode)
        assertEquals("etag-2", local.cache.etag)
        assertEquals(NOW, local.cache.checkedAtEpochMillis)
    }

    @Test
    fun `equal release code is up to date`() = runBlocking {
        val repository = AppUpdateRepositoryImpl(
            remoteDataSource = FakeGitHubReleaseDataSource(
                GitHubReleaseResponse.Modified(payload(tag = "1.0.2.2"), null)
            ),
            localDataSource = FakeAppUpdateLocalDataSource(),
            currentVersionCode = 2,
            clock = { NOW }
        )

        assertEquals(AppUpdateAvailability.UpToDate, repository.checkForUpdate())
    }

    @Test
    fun `fresh cache skips network`() = runBlocking {
        val remote = FakeGitHubReleaseDataSource(error = IOException("must not run"))
        val local = FakeAppUpdateLocalDataSource(
            AppUpdateCache(
                release = appRelease(versionCode = 3),
                etag = "etag-3",
                checkedAtEpochMillis = NOW - 1_000
            )
        )
        val repository = AppUpdateRepositoryImpl(remote, local, 2, clock = { NOW })

        assertTrue(repository.checkForUpdate() is AppUpdateAvailability.Available)
        assertEquals(0, remote.calls)
    }

    @Test
    fun `forced check bypasses fresh cache and uses etag`() = runBlocking {
        val remote = FakeGitHubReleaseDataSource(GitHubReleaseResponse.NotModified)
        val local = FakeAppUpdateLocalDataSource(
            AppUpdateCache(
                release = appRelease(versionCode = 3),
                etag = "etag-3",
                checkedAtEpochMillis = NOW - 1_000
            )
        )
        val repository = AppUpdateRepositoryImpl(remote, local, 2, clock = { NOW })

        assertTrue(repository.checkForUpdate(forceRefresh = true) is AppUpdateAvailability.Available)
        assertEquals(1, remote.calls)
        assertEquals("etag-3", remote.requestedEtag)
        assertEquals(NOW, local.cache.checkedAtEpochMillis)
    }

    @Test
    fun `stale cache sends etag and 304 reuses release`() = runBlocking {
        val remote = FakeGitHubReleaseDataSource(GitHubReleaseResponse.NotModified)
        val local = FakeAppUpdateLocalDataSource(
            AppUpdateCache(
                release = appRelease(versionCode = 3),
                etag = "etag-3",
                checkedAtEpochMillis = 1L
            )
        )
        val repository = AppUpdateRepositoryImpl(remote, local, 2, clock = { NOW })

        assertTrue(repository.checkForUpdate() is AppUpdateAvailability.Available)
        assertEquals("etag-3", remote.requestedEtag)
        assertEquals(NOW, local.cache.checkedAtEpochMillis)
    }

    @Test
    fun `network failure keeps stale cached availability`() = runBlocking {
        val local = FakeAppUpdateLocalDataSource(
            AppUpdateCache(
                release = appRelease(versionCode = 3),
                checkedAtEpochMillis = 1L
            )
        )
        val repository = AppUpdateRepositoryImpl(
            FakeGitHubReleaseDataSource(error = IOException("offline")),
            local,
            currentVersionCode = 2,
            clock = { NOW }
        )

        assertTrue(repository.checkForUpdate() is AppUpdateAvailability.Available)
        assertEquals(NOW, local.cache.checkedAtEpochMillis)
    }

    @Test
    fun `draft prerelease malformed tag and untrusted URL are unavailable`() = runBlocking {
        val invalidPayloads = listOf(
            payload(draft = true),
            payload(prerelease = true),
            payload(tag = "1.0.2"),
            payload(url = "https://example.com/releases/1.0.2.2")
        )

        invalidPayloads.forEach { invalid ->
            val repository = AppUpdateRepositoryImpl(
                FakeGitHubReleaseDataSource(GitHubReleaseResponse.Modified(invalid, null)),
                FakeAppUpdateLocalDataSource(),
                currentVersionCode = 1,
                clock = { NOW }
            )
            assertEquals(AppUpdateAvailability.Unavailable, repository.checkForUpdate())
        }
    }

    @Test
    fun `tag parser supports optional v and rejects missing positive code`() {
        assertEquals(ReleaseTagVersion("1.2.3", 45), parseReleaseTag("1.2.3.45"))
        assertEquals(ReleaseTagVersion("1.2.3", 45), parseReleaseTag("v1.2.3.45"))
        assertNull(parseReleaseTag("1.2.3"))
        assertNull(parseReleaseTag("1.2.3.0"))
    }

    @Test
    fun `release notes become bounded unique plain text`() {
        val longNote = "a".repeat(200)
        val notes = parseReleaseNotes(
            """
            <!-- generated -->
            - [feat(auth): add update check](https://github.com/commit/1)
            - [feat(auth): add update check](https://github.com/commit/1)
            * fix(ui): respect insets
            - $longNote
            - fourth
            - fifth
            - sixth
            - seventh

            **Full Changelog**: https://github.com/compare
            """.trimIndent()
        )

        assertEquals("feat(auth): add update check", notes.first())
        assertEquals(6, notes.size)
        assertEquals(160, notes[2].length)
        assertFalse(notes.any { "Full Changelog" in it })
    }

    @Test
    fun `release URL validation accepts only repository release pages`() {
        assertTrue(
            isTrustedReleaseUrl(
                "https://github.com/MarketTwits/TelegramLoginAndroidDemo/releases/tag/1.0.2.2"
            )
        )
        assertFalse(isTrustedReleaseUrl("http://github.com/MarketTwits/TelegramLoginAndroidDemo/releases/latest"))
        assertFalse(isTrustedReleaseUrl("https://github.com/other/repository/releases/latest"))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L

        fun payload(
            tag: String = "1.0.2.2",
            draft: Boolean = false,
            prerelease: Boolean = false,
            url: String = "https://github.com/MarketTwits/TelegramLoginAndroidDemo/releases/tag/1.0.2.2"
        ) = GitHubReleasePayload(
            tagName = tag,
            title = "v1.0.2",
            body = "- [feat(update): notify about releases](https://github.com/commit/1)",
            htmlUrl = url,
            publishedAt = "2026-08-14T15:21:18Z",
            draft = draft,
            prerelease = prerelease
        )

        fun appRelease(versionCode: Long) = AppRelease(
            versionName = "1.0.$versionCode",
            versionCode = versionCode,
            tagName = "1.0.$versionCode.$versionCode",
            title = "v1.0.$versionCode",
            notes = listOf("Change"),
            releasePageUrl =
                "https://github.com/MarketTwits/TelegramLoginAndroidDemo/releases/tag/1.0.$versionCode.$versionCode",
            publishedAt = null
        )
    }
}

private class FakeGitHubReleaseDataSource(
    private val response: GitHubReleaseResponse? = null,
    private val error: Throwable? = null
) : GitHubReleaseDataSource {
    var calls = 0
    var requestedEtag: String? = null

    override suspend fun fetchLatestRelease(etag: String?): GitHubReleaseResponse {
        calls += 1
        requestedEtag = etag
        error?.let { throw it }
        return checkNotNull(response)
    }
}

private class FakeAppUpdateLocalDataSource(
    initial: AppUpdateCache = AppUpdateCache()
) : AppUpdateLocalDataSource {
    var cache = initial

    override suspend fun readCache(): AppUpdateCache = cache

    override suspend fun saveRelease(
        release: AppRelease,
        etag: String?,
        checkedAtEpochMillis: Long
    ) {
        cache = AppUpdateCache(release, etag, checkedAtEpochMillis)
    }

    override suspend fun markChecked(checkedAtEpochMillis: Long) {
        cache = cache.copy(checkedAtEpochMillis = checkedAtEpochMillis)
    }

    override suspend fun clearRelease(checkedAtEpochMillis: Long) {
        cache = AppUpdateCache(checkedAtEpochMillis = checkedAtEpochMillis)
    }
}
