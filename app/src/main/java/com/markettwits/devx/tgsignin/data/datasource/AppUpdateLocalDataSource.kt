package com.markettwits.devx.tgsignin.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.markettwits.devx.tgsignin.data.model.AppRelease
import com.markettwits.devx.tgsignin.data.model.AppUpdateCache
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val APP_UPDATE_DATA_STORE_NAME = "app_updates"
private val Context.appUpdateDataStore by preferencesDataStore(APP_UPDATE_DATA_STORE_NAME)

interface AppUpdateLocalDataSource {
    suspend fun readCache(): AppUpdateCache
    suspend fun saveRelease(release: AppRelease, etag: String?, checkedAtEpochMillis: Long)
    suspend fun markChecked(checkedAtEpochMillis: Long)
    suspend fun clearRelease(checkedAtEpochMillis: Long)
}

class AppUpdateLocalDataSourceImpl(
    private val context: Context
) : AppUpdateLocalDataSource {
    override suspend fun readCache(): AppUpdateCache = context.appUpdateDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppUpdateCache(
                release = preferences[releaseKey]?.let(::releaseFromJson),
                etag = preferences[etagKey],
                checkedAtEpochMillis = preferences[checkedAtKey] ?: 0L
            )
        }
        .first()

    override suspend fun saveRelease(
        release: AppRelease,
        etag: String?,
        checkedAtEpochMillis: Long
    ) {
        context.appUpdateDataStore.edit { preferences ->
            preferences[releaseKey] = release.toJson()
            preferences[checkedAtKey] = checkedAtEpochMillis
            if (etag.isNullOrBlank()) preferences.remove(etagKey) else preferences[etagKey] = etag
        }
    }

    override suspend fun markChecked(checkedAtEpochMillis: Long) {
        context.appUpdateDataStore.edit { preferences ->
            preferences[checkedAtKey] = checkedAtEpochMillis
        }
    }

    override suspend fun clearRelease(checkedAtEpochMillis: Long) {
        context.appUpdateDataStore.edit { preferences ->
            preferences.remove(releaseKey)
            preferences.remove(etagKey)
            preferences[checkedAtKey] = checkedAtEpochMillis
        }
    }

    private fun AppRelease.toJson(): String = JSONObject()
        .put(JSON_VERSION_NAME, versionName)
        .put(JSON_VERSION_CODE, versionCode)
        .put(JSON_TAG_NAME, tagName)
        .put(JSON_TITLE, title)
        .put(JSON_NOTES, JSONArray(notes))
        .put(JSON_RELEASE_PAGE_URL, releasePageUrl)
        .apply { publishedAt?.let { put(JSON_PUBLISHED_AT, it) } }
        .toString()

    private fun releaseFromJson(value: String): AppRelease? = runCatching {
        val json = JSONObject(value)
        val notesJson = json.getJSONArray(JSON_NOTES)
        AppRelease(
            versionName = json.getString(JSON_VERSION_NAME),
            versionCode = json.getLong(JSON_VERSION_CODE),
            tagName = json.getString(JSON_TAG_NAME),
            title = json.getString(JSON_TITLE),
            notes = buildList {
                repeat(notesJson.length()) { index -> add(notesJson.getString(index)) }
            },
            releasePageUrl = json.getString(JSON_RELEASE_PAGE_URL),
            publishedAt = if (json.isNull(JSON_PUBLISHED_AT)) {
                null
            } else {
                json.optString(JSON_PUBLISHED_AT).takeIf(String::isNotBlank)
            }
        )
    }.getOrNull()

    private companion object {
        val releaseKey = stringPreferencesKey("latest_release_json")
        val etagKey = stringPreferencesKey("latest_release_etag")
        val checkedAtKey = longPreferencesKey("latest_release_checked_at_epoch_ms")
        const val JSON_VERSION_NAME = "versionName"
        const val JSON_VERSION_CODE = "versionCode"
        const val JSON_TAG_NAME = "tagName"
        const val JSON_TITLE = "title"
        const val JSON_NOTES = "notes"
        const val JSON_RELEASE_PAGE_URL = "releasePageUrl"
        const val JSON_PUBLISHED_AT = "publishedAt"
    }
}
