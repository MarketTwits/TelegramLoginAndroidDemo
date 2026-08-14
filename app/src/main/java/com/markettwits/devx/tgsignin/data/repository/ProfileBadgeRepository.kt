package com.markettwits.devx.tgsignin.data.repository

import android.content.Context
import com.markettwits.devx.tgsignin.data.dataSource.ProfileBadgeRemoteDataSource
import com.markettwits.devx.tgsignin.data.model.ProfileBadge
import com.markettwits.devx.tgsignin.data.model.ProfileBadgeCatalog
import com.markettwits.devx.tgsignin.data.model.ProfileBadgeKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface ProfileBadgeRepository {
    val catalog: StateFlow<ProfileBadgeCatalog?>
    suspend fun refresh(): Result<Unit>
    suspend fun loadAnimationJson(badge: ProfileBadge): String
    suspend fun loadStaticAsset(badge: ProfileBadge): File
}

class ProfileBadgeRepositoryImpl(
    context: Context,
    private val remoteDataSource: ProfileBadgeRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher,
    applicationScope: CoroutineScope
) : ProfileBadgeRepository {
    private val cacheDirectory = File(context.cacheDir, "profile_badges")
    private val catalogFile = File(cacheDirectory, "catalog-v1.json")
    private val _catalog = MutableStateFlow<ProfileBadgeCatalog?>(null)
    private val assetMutex = Mutex()
    override val catalog: StateFlow<ProfileBadgeCatalog?> = _catalog.asStateFlow()

    init {
        applicationScope.launch {
            withContext(ioDispatcher) {
                runCatching { catalogFile.takeIf(File::isFile)?.readText()?.let(::parseCatalog) }
                    .getOrNull()?.let { _catalog.value = it }
            }
            refresh()
        }
    }

    override suspend fun refresh(): Result<Unit> = runCatching {
        val rawCatalog = remoteDataSource.fetchCatalog()
        val parsed = parseCatalog(rawCatalog)
        withContext(ioDispatcher) {
            cacheDirectory.mkdirs()
            atomicWrite(catalogFile, rawCatalog.toByteArray(Charsets.UTF_8))
        }
        _catalog.value = parsed
    }

    override suspend fun loadAnimationJson(badge: ProfileBadge): String {
        require(badge.kind == ProfileBadgeKind.LOTTIE_TGS)
        val file = loadVerifiedAsset(badge)
        return withContext(ioDispatcher) {
            GZIPInputStream(file.inputStream().buffered()).use { gzip ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = gzip.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_UNCOMPRESSED_TGS_BYTES) throw IOException("TGS is too large")
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name()).also(::JSONObject)
            }
        }
    }

    override suspend fun loadStaticAsset(badge: ProfileBadge): File {
        require(badge.kind == ProfileBadgeKind.STATIC_WEBP)
        return loadVerifiedAsset(badge)
    }

    private suspend fun loadVerifiedAsset(badge: ProfileBadge): File = assetMutex.withLock {
        withContext(ioDispatcher) {
            cacheDirectory.mkdirs()
            val extension = if (badge.kind == ProfileBadgeKind.LOTTIE_TGS) "tgs" else "webp"
            val target = File(cacheDirectory, "${badge.sha256}.$extension")
            if (
                target.isFile && target.length() == badge.sizeBytes.toLong() &&
                target.sha256() == badge.sha256
            ) {
                return@withContext target
            }
            target.delete()
            val bytes = remoteDataSource.fetchAsset(badge)
            if (bytes.size != badge.sizeBytes || bytes.sha256() != badge.sha256) {
                throw IOException("Profile badge integrity check failed")
            }
            atomicWrite(target, bytes)
            target
        }
    }

    private fun parseCatalog(raw: String): ProfileBadgeCatalog {
        val json = JSONObject(raw)
        val version = json.getInt("version")
        require(version == SUPPORTED_CATALOG_VERSION) { "Unsupported profile badge catalog" }
        val badgesJson = json.getJSONArray("badges")
        val badges = (0 until badgesJson.length()).map { index ->
            val item = badgesJson.getJSONObject(index)
            val assetPath = item.getString("assetPath")
            val hash = item.getString("sha256")
            val size = item.getInt("sizeBytes")
            require(assetPath.startsWith("/assets/profile-badges/") && ".." !in assetPath)
            require(hash.matches(Regex("[0-9a-f]{64}")))
            require(size in 1..MAX_COMPRESSED_ASSET_BYTES)
            val labelsJson = item.getJSONObject("labels")
            val labels = labelsJson.keys().asSequence().associateWith(labelsJson::getString)
            require(item.getInt("width") in 1..512 && item.getInt("height") in 1..512)
            ProfileBadge(
                id = item.getString("id"),
                kind = ProfileBadgeKind.valueOf(item.getString("kind")),
                assetPath = assetPath,
                sha256 = hash,
                sizeBytes = size,
                width = item.getInt("width"),
                height = item.getInt("height"),
                labels = labels,
                enabled = item.optBoolean("enabled", true)
            )
        }
        require(badges.isNotEmpty() && badges.map(ProfileBadge::id).distinct().size == badges.size)
        val defaultBadgeId = json.getString("defaultBadgeId")
        require(badges.any { it.id == defaultBadgeId && it.enabled })
        return ProfileBadgeCatalog(version, defaultBadgeId, badges)
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        target.delete()
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to commit profile badge cache")
        }
    }

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().toHex()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).toHex()

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private companion object {
        const val SUPPORTED_CATALOG_VERSION = 1
        const val MAX_COMPRESSED_ASSET_BYTES = 64 * 1024
        const val MAX_UNCOMPRESSED_TGS_BYTES = 1024 * 1024
    }
}
