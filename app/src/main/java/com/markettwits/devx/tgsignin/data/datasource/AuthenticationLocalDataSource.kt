package com.markettwits.devx.tgsignin.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.AvatarSource
import com.markettwits.devx.tgsignin.data.model.DEFAULT_PROFILE_EMOJI
import com.markettwits.devx.tgsignin.data.model.OnboardingState
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.ProfileIntent
import com.markettwits.devx.tgsignin.data.model.ProfileTopic
import com.markettwits.devx.tgsignin.data.model.ServiceAccount
import com.markettwits.devx.tgsignin.data.model.ServiceProfile
import com.markettwits.devx.tgsignin.data.model.TelegramIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val AUTH_DATA_STORE_NAME = "authenticated_account"
private val Context.authDataStore by preferencesDataStore(AUTH_DATA_STORE_NAME)

interface AuthenticationLocalDataSource {
    val session: Flow<AuthenticationResult?>
    val profileDraft: Flow<ProfileDraft?>

    suspend fun save(session: AuthenticationResult)
    suspend fun saveDraft(draft: ProfileDraft)
    suspend fun clearDraft()
    suspend fun clear()
}

/** Keeps the application session, service profile, and interrupted draft encrypted at rest. */
class AuthenticationLocalDataSourceImpl(
    private val context: Context,
    private val cryptoManager: CryptoManager
) : AuthenticationLocalDataSource {
    private val preferences = context.authDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val session: Flow<AuthenticationResult?> = preferences.map { values ->
        runCatching {
            values[encryptedSessionKey]?.let(cryptoManager::decrypt)?.let(::decodeSession)
        }.getOrNull()
    }
    override val profileDraft: Flow<ProfileDraft?> = preferences.map { values ->
        runCatching {
            values[encryptedDraftKey]?.let(cryptoManager::decrypt)?.let(::decodeDraft)
        }.getOrNull()
    }

    override suspend fun save(session: AuthenticationResult) {
        context.authDataStore.edit { it[encryptedSessionKey] = cryptoManager.encrypt(encodeSession(session)) }
    }

    override suspend fun saveDraft(draft: ProfileDraft) {
        context.authDataStore.edit { it[encryptedDraftKey] = cryptoManager.encrypt(encodeDraft(draft)) }
    }

    override suspend fun clearDraft() {
        context.authDataStore.edit { it.remove(encryptedDraftKey) }
    }

    override suspend fun clear() {
        context.authDataStore.edit {
            it.remove(encryptedSessionKey)
            it.remove(encryptedDraftKey)
        }
    }

    private fun encodeSession(value: AuthenticationResult): String = JSONObject().apply {
        put("accessToken", value.accessToken)
        put("expiresAt", value.expiresAt)
        put("account", JSONObject().apply {
            put("id", value.account.id)
            put("memberNumber", value.account.memberNumber)
            put("onboardingState", value.account.onboardingState.name)
            put("registeredAt", value.account.registeredAt)
            put("lastLoginAt", value.account.lastLoginAt)
            put("loginCount", value.account.loginCount)
        })
        put("telegram", JSONObject().apply {
            put("userId", value.telegram.userId)
            put("name", value.telegram.name)
            put("givenName", value.telegram.givenName)
            put("familyName", value.telegram.familyName)
            put("username", value.telegram.username)
            put("picture", value.telegram.pictureUrl)
            put("phoneNumber", value.telegram.phoneNumber)
            put("phoneVerified", value.telegram.phoneVerified)
            put("syncedAt", value.telegram.syncedAt)
        })
        value.profile?.let { profile ->
            put("profile", JSONObject().apply {
                put("displayName", profile.displayName)
                put("headline", profile.headline)
                put("intent", profile.intent.name)
                put("topics", JSONArray(profile.topics.map(ProfileTopic::name)))
                put("avatarSource", profile.avatarSource.name)
                put("emojiStatus", profile.emojiStatus.toJson())
                put("phoneNumber", profile.phoneNumber)
                put("visualSeed", profile.visualSeed)
                put("createdAt", profile.createdAt)
                put("updatedAt", profile.updatedAt)
            })
        }
    }.toString()

    private fun decodeSession(value: String): AuthenticationResult = JSONObject(value).let { json ->
        val account = json.getJSONObject("account")
        val telegram = json.getJSONObject("telegram")
        AuthenticationResult(
            accessToken = json.getString("accessToken"),
            expiresAt = json.nullableString("expiresAt"),
            account = ServiceAccount(
                id = account.getString("id"),
                memberNumber = account.getLong("memberNumber"),
                onboardingState = OnboardingState.valueOf(account.getString("onboardingState")),
                registeredAt = account.getString("registeredAt"),
                lastLoginAt = account.getString("lastLoginAt"),
                loginCount = account.getInt("loginCount")
            ),
            telegram = TelegramIdentity(
                userId = telegram.nullableString("userId"),
                name = telegram.nullableString("name"),
                givenName = telegram.nullableString("givenName"),
                familyName = telegram.nullableString("familyName"),
                username = telegram.nullableString("username"),
                pictureUrl = telegram.nullableString("picture"),
                phoneNumber = telegram.nullableString("phoneNumber"),
                phoneVerified = telegram.optBoolean("phoneVerified"),
                syncedAt = telegram.nullableString("syncedAt")
            ),
            profile = json.optJSONObject("profile")?.let { profile ->
                ServiceProfile(
                    displayName = profile.getString("displayName"),
                    headline = profile.getString("headline"),
                    intent = ProfileIntent.valueOf(profile.getString("intent")),
                    topics = profile.getJSONArray("topics").strings().map(ProfileTopic::valueOf),
                    avatarSource = AvatarSource.valueOf(profile.getString("avatarSource")),
                    emojiStatus = profile.optJSONObject("emojiStatus")?.toProfileEmojiSelection()
                        ?: DEFAULT_PROFILE_EMOJI,
                    phoneNumber = profile.nullableString("phoneNumber"),
                    visualSeed = profile.getString("visualSeed"),
                    createdAt = profile.getString("createdAt"),
                    updatedAt = profile.getString("updatedAt")
                )
            }
        )
    }

    private fun encodeDraft(value: ProfileDraft): String = JSONObject().apply {
        put("displayName", value.displayName)
        put("headline", value.headline)
        put("intent", value.intent.name)
        put("topics", JSONArray(value.topics.map(ProfileTopic::name)))
        put("avatarSource", value.avatarSource.name)
        put("emojiStatus", value.emojiStatus?.toJson() ?: JSONObject.NULL)
        put("phoneNumber", value.phoneNumber)
        put("phoneNumberEdited", value.phoneNumberEdited)
    }.toString()

    private fun decodeDraft(value: String): ProfileDraft = JSONObject(value).let { json ->
        ProfileDraft(
            displayName = json.getString("displayName"),
            headline = json.getString("headline"),
            intent = ProfileIntent.valueOf(json.getString("intent")),
            topics = json.getJSONArray("topics").strings().map(ProfileTopic::valueOf).toSet(),
            // Bloom is a badge beside the name; Telegram always remains the avatar source.
            avatarSource = AvatarSource.TELEGRAM,
            emojiStatus = json.optJSONObject("emojiStatus")?.toProfileEmojiSelection()
                ?: DEFAULT_PROFILE_EMOJI,
            phoneNumber = json.optString("phoneNumber"),
            phoneNumberEdited = json.optBoolean("phoneNumberEdited")
        )
    }

    private companion object {
        val encryptedSessionKey = stringPreferencesKey("encrypted_session_v2")
        val encryptedDraftKey = stringPreferencesKey("encrypted_profile_draft")
    }
}

private fun JSONObject.nullableString(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != JSONObject.NULL.toString() }

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun ProfileEmojiSelection.toJson(): JSONObject = JSONObject()
    .put("setId", setId)
    .put("emojiId", emojiId)

private fun JSONObject.toProfileEmojiSelection() = ProfileEmojiSelection(
    setId = getString("setId"),
    emojiId = getString("emojiId")
)
