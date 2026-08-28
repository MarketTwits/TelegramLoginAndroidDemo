package com.markettwits.devx.tgsignin.data.repository

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import com.markettwits.devx.tgsignin.data.datasource.ProfileEmojiRemoteDataSource
import com.markettwits.devx.tgsignin.data.model.ProfileEmoji
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiCatalog
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

interface ProfileEmojiRepository {
    val catalog: StateFlow<ProfileEmojiCatalog?>
    val recentSelections: StateFlow<List<ProfileEmojiSelection>>
    suspend fun refresh(): Result<Unit>
    suspend fun loadAnimationFile(emoji: ProfileEmoji): File
    suspend fun prefetch(emojis: List<ProfileEmoji>)
    fun recordRecent(selection: ProfileEmojiSelection)
}

class ProfileEmojiRepositoryImpl(
    context: Context,
    private val remoteDataSource: ProfileEmojiRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher,
    private val applicationScope: CoroutineScope
) : ProfileEmojiRepository {
    private val cacheDirectory = File(context.cacheDir, "profile_emojis")
    private val catalogFile = File(cacheDirectory, "catalog-v3.json")
    private val cacheVersionFile = File(cacheDirectory, ".cache-v3")
    private val historyPreferences = context.getSharedPreferences(
        "profile_emoji_history",
        Context.MODE_PRIVATE
    )
    private val _catalog = MutableStateFlow<ProfileEmojiCatalog?>(null)
    private val _recentSelections = MutableStateFlow(readRecentSelections())
    private val assetMutexes = ConcurrentHashMap<String, Mutex>()
    private val verifiedAssets = ConcurrentHashMap.newKeySet<String>()
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_ASSET_DOWNLOADS)
    override val catalog: StateFlow<ProfileEmojiCatalog?> = _catalog.asStateFlow()
    override val recentSelections: StateFlow<List<ProfileEmojiSelection>> =
        _recentSelections.asStateFlow()

    init {
        applicationScope.launch {
            withContext(ioDispatcher) {
                prepareCacheDirectory()
                runCatching { catalogFile.takeIf(File::isFile)?.readText()?.let(::parseCatalog) }
                    .getOrNull()?.let {
                        _catalog.value = it
                        warmEssentialAssets(it)
                    }
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
        retainAvailableRecentSelections(parsed)
        warmEssentialAssets(parsed)
    }

    @Synchronized
    override fun recordRecent(selection: ProfileEmojiSelection) {
        if (_catalog.value?.contains(selection) != true) return
        val updated = listOf(selection) + _recentSelections.value.filterNot { it == selection }
        persistRecentSelections(updated.take(MAX_RECENT_EMOJIS))
    }

    override suspend fun loadAnimationFile(emoji: ProfileEmoji): File = loadVerifiedAsset(emoji)

    override suspend fun prefetch(emojis: List<ProfileEmoji>) {
        coroutineScope {
            emojis.asSequence()
                .filter(ProfileEmoji::enabled)
                .distinctBy(ProfileEmoji::sha256)
                .chunked(MAX_CONCURRENT_ASSET_DOWNLOADS)
                .forEach { batch ->
                    batch.map { emoji ->
                        async(ioDispatcher) {
                            try {
                                loadVerifiedAsset(emoji)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                // One broken asset must not stop the remaining cache warmup.
                            }
                        }
                    }
                        .awaitAll()
                }
        }
    }

    private fun warmEssentialAssets(catalog: ProfileEmojiCatalog) {
        val essentials = buildList {
            catalog.emoji(catalog.defaultEmoji)?.let(::add)
            catalog.sets.mapNotNullTo(this) { set ->
                set.emojis.firstOrNull { it.id == set.thumbnailEmojiId }
            }
            _recentSelections.value.mapNotNullTo(this, catalog::emoji)
        }
        applicationScope.launch { prefetch(essentials) }
    }

    private suspend fun loadVerifiedAsset(emoji: ProfileEmoji): File =
        assetMutexes.computeIfAbsent(emoji.sha256) { Mutex() }.withLock {
            withContext(ioDispatcher) {
                cacheDirectory.mkdirs()
                val target = File(cacheDirectory, "${emoji.sha256}.tgs")
                if (
                    target.isFile && target.length() == emoji.sizeBytes.toLong() &&
                    (verifiedAssets.contains(emoji.sha256) || target.sha256() == emoji.sha256)
                ) {
                    verifiedAssets.add(emoji.sha256)
                    return@withContext target
                }
                verifiedAssets.remove(emoji.sha256)
                target.delete()
                val bytes = downloadSemaphore.withPermit {
                    remoteDataSource.fetchAsset(emoji)
                }
                if (bytes.size != emoji.sizeBytes || bytes.sha256() != emoji.sha256) {
                    throw IOException("Profile emoji integrity check failed")
                }
                validateCompressedAnimation(bytes)
                atomicWrite(target, bytes)
                verifiedAssets.add(emoji.sha256)
                target
            }
        }

    private fun validateCompressedAnimation(bytes: ByteArray) {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_UNCOMPRESSED_TGS_BYTES) throw IOException("TGS is too large")
            }
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
                    name = item.getString("name"),
                    keywords = item.getJSONArray("keywords").let { keywords ->
                        (0 until keywords.length()).map(keywords::getString)
                    },
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
        val atomicFile = AtomicFile(target)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
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

    private fun readRecentSelections(): List<ProfileEmojiSelection> = runCatching {
        val stored =
            historyPreferences.getString(RECENT_EMOJIS_KEY, null) ?: return@runCatching emptyList()
        val json = JSONArray(stored)
        (0 until json.length()).map { index ->
            json.getJSONObject(index).let {
                ProfileEmojiSelection(it.getString("setId"), it.getString("emojiId"))
            }
        }.distinct().take(MAX_RECENT_EMOJIS)
    }.getOrDefault(emptyList())

    @Synchronized
    private fun retainAvailableRecentSelections(catalog: ProfileEmojiCatalog) {
        val available = _recentSelections.value.filter(catalog::contains)
        if (available != _recentSelections.value) persistRecentSelections(available)
    }

    private fun persistRecentSelections(selections: List<ProfileEmojiSelection>) {
        _recentSelections.value = selections
        val json = JSONArray(selections.map { selection ->
            JSONObject()
                .put("setId", selection.setId)
                .put("emojiId", selection.emojiId)
        })
        historyPreferences.edit { putString(RECENT_EMOJIS_KEY, json.toString()) }
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
        const val MAX_CONCURRENT_ASSET_DOWNLOADS = 3
        const val MAX_RECENT_EMOJIS = 24
        const val RECENT_EMOJIS_KEY = "recent_selections_v1"
    }
}
