package com.markettwits.devx.tgsignin.data.dataSource

import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.AvatarSource
import com.markettwits.devx.tgsignin.data.model.OnboardingState
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileIntent
import com.markettwits.devx.tgsignin.data.model.ProfileTopic
import com.markettwits.devx.tgsignin.data.model.ServiceAccount
import com.markettwits.devx.tgsignin.data.model.ServiceProfile
import com.markettwits.devx.tgsignin.data.model.TelegramIdentity
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Real backend client. Telegram credentials are never interpreted on-device. */
class TelegramAuthApiDataSourceImpl(
    config: TelegramLoginConfig,
    private val ioDispatcher: CoroutineDispatcher
) : TelegramAuthApiDataSource {
    private val baseUrl = config.backendUrl.trimEnd('/')

    override suspend fun authenticate(idToken: String): AuthenticationResult = request(
        path = "/auth/telegram",
        method = "POST",
        body = JSONObject().put("idToken", idToken),
        accessToken = null
    ) { json -> parseAuthenticationResult(json, json.getString("sessionToken")) }

    override suspend fun getCurrentSession(accessToken: String): AuthenticationResult = request(
        path = "/auth/session",
        method = "GET",
        accessToken = accessToken
    ) { json -> parseAuthenticationResult(json, accessToken) }

    override suspend fun saveProfile(
        accessToken: String,
        draft: ProfileDraft
    ): AuthenticationResult = request(
        path = "/me/profile",
        method = "PUT",
        accessToken = accessToken,
        body = JSONObject().apply {
            put("displayName", draft.displayName.trim())
            put("headline", draft.headline.trim())
            put("intent", draft.intent.name)
            put("topics", JSONArray(draft.topics.map(ProfileTopic::name)))
            put("avatarSource", draft.avatarSource.name)
        }
    ) { json -> parseAuthenticationResult(json, accessToken) }

    override suspend fun revokeSession(accessToken: String) {
        request(path = "/auth/session", method = "DELETE", accessToken = accessToken) { Unit }
    }

    private suspend fun <T> request(
        path: String,
        method: String,
        accessToken: String?,
        body: JSONObject? = null,
        parse: (JSONObject) -> T
    ): T = withContext(ioDispatcher) {
        val connection = openConnection(path, method).apply {
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use {
                    it.write(body.toString())
                }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.let { InputStreamReader(it, StandardCharsets.UTF_8).use(InputStreamReader::readText) }
                .orEmpty()
            if (status !in 200..299) throw BackendHttpException(status)
            parse(if (text.isBlank()) JSONObject() else JSONObject(text))
        } catch (error: IOException) {
            throw BackendNetworkException(error)
        } catch (error: org.json.JSONException) {
            throw BackendResponseException(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, method: String): HttpURLConnection = try {
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
    } catch (error: IOException) {
        throw BackendNetworkException(error)
    }

    private fun parseAuthenticationResult(json: JSONObject, accessToken: String): AuthenticationResult {
        val account = json.getJSONObject("account")
        val telegram = json.getJSONObject("telegram")
        return AuthenticationResult(
            accessToken = accessToken,
            expiresAt = json.optionalString("expiresAt"),
            account = ServiceAccount(
                id = account.getString("id"),
                memberNumber = account.getLong("memberNumber"),
                onboardingState = OnboardingState.valueOf(account.getString("onboardingState")),
                registeredAt = account.getString("registeredAt"),
                lastLoginAt = account.getString("lastLoginAt"),
                loginCount = account.getInt("loginCount")
            ),
            telegram = TelegramIdentity(
                name = telegram.optionalString("name"),
                givenName = telegram.optionalString("givenName"),
                familyName = telegram.optionalString("familyName"),
                username = telegram.optionalString("username"),
                pictureUrl = telegram.optionalString("picture"),
                phoneVerified = telegram.optBoolean("phoneVerified"),
                syncedAt = telegram.optionalString("syncedAt")
            ),
            profile = json.optJSONObject("profile")?.let { profile ->
                ServiceProfile(
                    displayName = profile.getString("displayName"),
                    headline = profile.getString("headline"),
                    intent = ProfileIntent.valueOf(profile.getString("intent")),
                    topics = profile.getJSONArray("topics").toStrings().map(ProfileTopic::valueOf),
                    avatarSource = AvatarSource.valueOf(profile.getString("avatarSource")),
                    visualSeed = profile.getString("visualSeed"),
                    createdAt = profile.getString("createdAt"),
                    updatedAt = profile.getString("updatedAt")
                )
            }
        )
    }
}

private fun JSONObject.optionalString(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != JSONObject.NULL.toString() }

private fun JSONArray.toStrings(): List<String> = (0 until length()).map(::getString)
