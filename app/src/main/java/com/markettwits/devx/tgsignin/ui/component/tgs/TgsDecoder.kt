package com.markettwits.devx.tgsignin.ui.component.tgs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Collections
import java.util.LinkedHashMap
import java.util.zip.GZIPInputStream

/**
 * Decodes compressed TGS files (gzip-compressed Lottie JSON) into JSON strings,
 * caching uncompressed JSON in memory to avoid redundant disk I/O and gzip decompression.
 */
object TgsDecoder {
    private const val MAX_JSON_CACHE_ENTRIES = 250

    private val jsonCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_JSON_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > MAX_JSON_CACHE_ENTRIES
            }
        }
    )

    fun getJsonFromCache(cacheKey: String): String? = jsonCache[cacheKey]

    fun putJsonInCache(cacheKey: String, json: String) {
        if (json.isNotBlank()) {
            jsonCache[cacheKey] = json
        }
    }

    suspend fun decodeTgsToJson(file: File, cacheKey: String = file.absolutePath): String? {
        jsonCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                if (!file.isFile || !file.exists()) return@withContext null
                FileInputStream(file).use { fis ->
                    decodeStreamToJson(fis, cacheKey)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun decodeTgsBytesToJson(bytes: ByteArray, cacheKey: String): String? {
        jsonCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.Default) {
            try {
                ByteArrayInputStream(bytes).use { bais ->
                    decodeStreamToJson(bais, cacheKey)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun decodeStreamToJson(inputStream: InputStream, cacheKey: String? = null): String? {
        return try {
            val json = GZIPInputStream(inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (json.isNotBlank()) {
                if (cacheKey != null) {
                    jsonCache[cacheKey] = json
                }
                json
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        jsonCache.clear()
    }
}
