package com.markettwits.devx.tgsignin.data.dataSource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import com.markettwits.devx.tgsignin.data.model.AuthenticatedSession
import com.markettwits.devx.tgsignin.data.model.TelegramUser

private const val AUTH_DATA_STORE_NAME = "authenticated_account"
private val Context.authDataStore by preferencesDataStore(AUTH_DATA_STORE_NAME)

interface AuthenticationLocalDataSource {
    val session: Flow<AuthenticatedSession?>

    suspend fun save(session: AuthenticatedSession)
    suspend fun clear()
}

/** Persists the current account between launches. The session payload is encrypted with Android Keystore. */
class AuthenticationLocalDataSourceImpl(
    private val context: Context,
    private val cryptoManager: CryptoManager
) : AuthenticationLocalDataSource {
    override val session: Flow<AuthenticatedSession?> = context.authDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            runCatching {
                preferences[encryptedSessionKey]
                    ?.let(cryptoManager::decrypt)
                    ?.let(::decodeSession)
            }.getOrNull()
        }

    override suspend fun save(session: AuthenticatedSession) {
        context.authDataStore.edit { preferences ->
            preferences[encryptedSessionKey] = cryptoManager.encrypt(encodeSession(session))
        }
    }

    override suspend fun clear() {
        context.authDataStore.edit { it.remove(encryptedSessionKey) }
    }

    private fun encodeSession(session: AuthenticatedSession): String = JSONObject().apply {
        put(JSON_ACCESS_TOKEN, session.accessToken)
        put(JSON_ID, session.user.id)
        put(JSON_NAME, session.user.name)
        put(JSON_GIVEN_NAME, session.user.givenName)
        put(JSON_FAMILY_NAME, session.user.familyName)
        put(JSON_USERNAME, session.user.username)
        put(JSON_PHONE_NUMBER, session.user.phoneNumber)
        put(JSON_PHONE_VERIFIED, session.user.phoneVerified)
        put(JSON_PICTURE_URL, session.user.pictureUrl)
    }.toString()

    private fun decodeSession(value: String): AuthenticatedSession? = runCatching {
        JSONObject(value).let { json ->
            AuthenticatedSession(
                accessToken = json.getString(JSON_ACCESS_TOKEN),
                user = TelegramUser(
                    id = json.getString(JSON_ID),
                    name = json.optNullableString(JSON_NAME),
                    givenName = json.optNullableString(JSON_GIVEN_NAME),
                    familyName = json.optNullableString(JSON_FAMILY_NAME),
                    username = json.optNullableString(JSON_USERNAME),
                    phoneNumber = json.optNullableString(JSON_PHONE_NUMBER),
                    phoneVerified = json.optBoolean(JSON_PHONE_VERIFIED),
                    pictureUrl = json.optNullableString(JSON_PICTURE_URL)
                )
            )
        }
    }.getOrNull()

    private companion object {
        val encryptedSessionKey = stringPreferencesKey("encrypted_session")
        const val JSON_ACCESS_TOKEN = "accessToken"
        const val JSON_ID = "id"
        const val JSON_NAME = "name"
        const val JSON_GIVEN_NAME = "givenName"
        const val JSON_FAMILY_NAME = "familyName"
        const val JSON_USERNAME = "username"
        const val JSON_PHONE_NUMBER = "phoneNumber"
        const val JSON_PHONE_VERIFIED = "phoneVerified"
        const val JSON_PICTURE_URL = "pictureUrl"
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != JSONObject.NULL.toString() }
