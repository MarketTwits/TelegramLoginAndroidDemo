package com.markettwits.devx.tgsignin.data.repository

import android.content.Context
import android.util.LruCache
import com.markettwits.devx.tgsignin.data.dataSource.ProfileEmojiRemoteDataSource
import com.markettwits.devx.tgsignin.data.model.ProfileEmoji
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiCatalog
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSet
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

interface ProfileEmojiRepository {
    val catalog: StateFlow<ProfileEmojiCatalog?>
    suspend fun refresh(): Result<Unit>
    suspend fun loadAnimationJson(emoji: ProfileEmoji): String
}

class ProfileEmojiRepositoryImpl(
    context: Context,
    private val remoteDataSource: ProfileEmojiRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher,
    applicationScope: CoroutineScope
) : ProfileEmojiRepository {
    private val cacheDirectory = File(context.cacheDir, "profile_emojis")
    private val catalogFile = File(cacheDirectory, "catalog-v3.json")
    private val cacheVersionFile = File(cacheDirectory, ".cache-v3")
    private val _catalog = MutableStateFlow<ProfileEmojiCatalog?>(null)
    private val assetMutexes = ConcurrentHashMap<String, Mutex>()
    private val animationJsonCache = object : LruCache<String, String>(MAX_JSON_CACHE_KIB) {
        override fun sizeOf(key: String, value: String): Int =
            (value.toByteArray(Charsets.UTF_8).size / 1024).coerceAtLeast(1)
    }
    override val catalog: StateFlow<ProfileEmojiCatalog?> = _catalog.asStateFlow()

    init {
        applicationScope.launch {
            withContext(ioDispatcher) {
                prepareCacheDirectory()
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

    override suspend fun loadAnimationJson(emoji: ProfileEmoji): String {
        animationJsonCache.get(emoji.sha256)?.let { return it }
        val file = loadVerifiedAsset(emoji)
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
                output.toString(Charsets.UTF_8.name()).also(::JSONObject).also {
                    animationJsonCache.put(emoji.sha256, it)
                }
            }
        }
    }

    private suspend fun loadVerifiedAsset(emoji: ProfileEmoji): File =
        assetMutexes.getOrPut(emoji.sha256, ::Mutex).withLock {
            withContext(ioDispatcher) {
                cacheDirectory.mkdirs()
                val target = File(cacheDirectory, "${emoji.sha256}.tgs")
                if (
                    target.isFile && target.length() == emoji.sizeBytes.toLong() &&
                    target.sha256() == emoji.sha256
                ) {
                    return@withContext target
                }
                target.delete()
                val bytes = remoteDataSource.fetchAsset(emoji)
                if (bytes.size != emoji.sizeBytes || bytes.sha256() != emoji.sha256) {
                    throw IOException("Profile emoji integrity check failed")
                }
                atomicWrite(target, bytes)
                target
            }
        }

    private fun parseCatalog(raw: String): ProfileEmojiCatalog {
        val json = JSONObject(raw)
        val version = json.getInt("version")
        require(version == SUPPORTED_CATALOG_VERSION && json.getString("format") == "LOTTIE_TGS") {
            "Unsupported profile emoji catalog"
        }
        val setsJson = json.getJSONArray("sets")
        val sets = (0 until setsJson.length()).map { setIndex ->
            val setJson = setsJson.getJSONObject(setIndex)
            val setId = setJson.getString("id")
            val labelsJson = setJson.getJSONObject("labels")
            val labels = labelsJson.keys().asSequence().associateWith(labelsJson::getString)
            val emojisJson = setJson.getJSONArray("emojis")
            val emojis = (0 until emojisJson.length()).map { emojiIndex ->
                val item = emojisJson.getJSONObject(emojiIndex)
                val assetPath = item.getString("assetPath")
                val hash = item.getString("sha256")
                val size = item.getInt("sizeBytes")
                require(assetPath.startsWith("/assets/profile-emojis/v3/") && ".." !in assetPath)
                require(hash.matches(Regex("[0-9a-f]{64}")))
                require(size in 1..MAX_COMPRESSED_ASSET_BYTES)
                require(item.getInt("width") == 512 && item.getInt("height") == 512)
                ProfileEmoji(
                    setId = setId,
                    id = item.getString("id"),
                    assetPath = assetPath,
                    sha256 = hash,
                    sizeBytes = size,
                    width = item.getInt("width"),
                    height = item.getInt("height"),
                    framesPerSecond = item.getDouble("framesPerSecond").toFloat(),
                    durationMs = item.getInt("durationMs"),
                    enabled = item.optBoolean("enabled", true)
                )
            }
            require(
                emojis.isNotEmpty() && emojis.map(ProfileEmoji::id).distinct().size == emojis.size
            )
            ProfileEmojiSet(
                id = setId,
                labels = labels,
                thumbnailEmojiId = setJson.getString("thumbnailEmojiId"),
                emojis = emojis
            ).also { set -> require(set.emojis.any { it.id == set.thumbnailEmojiId }) }
        }
        require(sets.isNotEmpty() && sets.map(ProfileEmojiSet::id).distinct().size == sets.size)
        val defaultJson = json.getJSONObject("defaultEmoji")
        val catalog = ProfileEmojiCatalog(
            version = version,
            defaultEmoji = ProfileEmojiSelection(
                setId = defaultJson.getString("setId"),
                emojiId = defaultJson.getString("emojiId")
            ),
            sets = sets
        )
        require(catalog.contains(catalog.defaultEmoji))
        return catalog
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        target.delete()
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to commit profile emoji cache")
        }
    }

    private fun prepareCacheDirectory() {
        if (cacheVersionFile.isFile) return
        cacheDirectory.deleteRecursively()
        check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) {
            "Unable to prepare profile emoji cache"
        }
        cacheVersionFile.writeText("3")
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
        const val SUPPORTED_CATALOG_VERSION = 3
        const val MAX_COMPRESSED_ASSET_BYTES = 64 * 1024
        const val MAX_UNCOMPRESSED_TGS_BYTES = 2 * 1024 * 1024
        const val MAX_JSON_CACHE_KIB = 8 * 1024
    }
}
