package com.markettwits.devx.tgsignin.data.datasource

import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.AvatarSource
import com.markettwits.devx.tgsignin.data.model.DEFAULT_PROFILE_EMOJI
import com.markettwits.devx.tgsignin.data.model.OnboardingState
import com.markettwits.devx.tgsignin.data.model.DeletedPasskey
import com.markettwits.devx.tgsignin.data.model.PasskeyInfo
import com.markettwits.devx.tgsignin.data.model.PasskeyOptions
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.ProfileIntent
import com.markettwits.devx.tgsignin.data.model.ProfileTopic
import com.markettwits.devx.tgsignin.data.model.ServiceAccount
import com.markettwits.devx.tgsignin.data.model.ServiceProfile
import com.markettwits.devx.tgsignin.data.model.TelegramIdentity
import com.markettwits.devx.tgsignin.data.model.normalizedInternationalPhoneNumberOrNull
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

interface TelegramAuthApiDataSource {
    suspend fun authenticate(idToken: String): AuthenticationResult
    suspend fun getCurrentSession(accessToken: String): AuthenticationResult
    suspend fun saveProfile(accessToken: String, draft: ProfileDraft): AuthenticationResult
    suspend fun deleteAccount(accessToken: String)
    suspend fun revokeSession(accessToken: String)
    suspend fun beginPasskeyAuthentication(): PasskeyOptions = error("Passkeys are unavailable")
    suspend fun finishPasskeyAuthentication(operationId: String, credentialJson: String): AuthenticationResult =
        error("Passkeys are unavailable")
    suspend fun listPasskeys(accessToken: String): List<PasskeyInfo> = error("Passkeys are unavailable")
    suspend fun beginPasskeyRegistration(accessToken: String): PasskeyOptions = error("Passkeys are unavailable")
    suspend fun finishPasskeyRegistration(
        accessToken: String,
        operationId: String,
        credentialJson: String,
        name: String
    ): List<PasskeyInfo> = error("Passkeys are unavailable")
    suspend fun renamePasskey(accessToken: String, id: String, name: String): List<PasskeyInfo> =
        error("Passkeys are unavailable")
    suspend fun beginPasskeyReauthentication(accessToken: String): PasskeyOptions =
        error("Passkeys are unavailable")
    suspend fun reauthenticateWithTelegram(accessToken: String, idToken: String): Unit =
        error("Telegram reauthentication is unavailable")
    suspend fun finishPasskeyReauthentication(
        accessToken: String,
        operationId: String,
        credentialJson: String
    ): Unit {
        error("Passkeys are unavailable")
    }
    suspend fun deletePasskey(accessToken: String, id: String): DeletedPasskey {
        error("Passkeys are unavailable")
    }
}

/** Real backend client. Telegram credentials are never interpreted on-device. */
class TelegramAuthApiDataSourceImpl(
    config: TelegramLoginConfig,
    private val ioDispatcher: CoroutineDispatcher
) : TelegramAuthApiDataSource {
    private val baseUrl = config.backendUrl.trimEnd('/')
    private val appToken = config.appToken

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
            put("emojiStatus", draft.emojiStatus?.toJson() ?: JSONObject.NULL)
            put("phoneNumber", draft.phoneNumber.normalizedInternationalPhoneNumberOrNull())
        }
    ) { json -> parseAuthenticationResult(json, accessToken) }

    override suspend fun deleteAccount(accessToken: String) {
        request(
            path = "/me/account",
            method = "DELETE",
            accessToken = accessToken
        ) { }
    }

    override suspend fun revokeSession(accessToken: String) {
        request(path = "/auth/session", method = "DELETE", accessToken = accessToken) { }
    }

    override suspend fun beginPasskeyAuthentication(): PasskeyOptions = request(
        path = "/auth/passkeys/options", method = "POST", accessToken = null, body = JSONObject()
    ) { it.toPasskeyOptions() }

    override suspend fun finishPasskeyAuthentication(
        operationId: String,
        credentialJson: String
    ): AuthenticationResult = request(
        path = "/auth/passkeys/verify",
        method = "POST",
        accessToken = null,
        body = JSONObject()
            .put("operationId", operationId)
            .put("credential", JSONObject(credentialJson))
    ) { json -> parseAuthenticationResult(json, json.getString("sessionToken")) }

    override suspend fun listPasskeys(accessToken: String): List<PasskeyInfo> = request(
        path = "/me/passkeys", method = "GET", accessToken = accessToken
    ) { it.getJSONArray("passkeys").toPasskeys() }

    override suspend fun beginPasskeyRegistration(accessToken: String): PasskeyOptions = request(
        path = "/me/passkeys/registration/options",
        method = "POST",
        accessToken = accessToken,
        body = JSONObject()
    ) { it.toPasskeyOptions() }

    override suspend fun finishPasskeyRegistration(
        accessToken: String,
        operationId: String,
        credentialJson: String,
        name: String
    ): List<PasskeyInfo> = request(
        path = "/me/passkeys/registration/verify",
        method = "POST",
        accessToken = accessToken,
        body = JSONObject()
            .put("operationId", operationId)
            .put("credential", JSONObject(credentialJson))
            .put("name", name)
    ) { it.getJSONArray("passkeys").toPasskeys() }

    override suspend fun renamePasskey(
        accessToken: String,
        id: String,
        name: String
    ): List<PasskeyInfo> = request(
        path = "/me/passkeys/$id",
        method = "PATCH",
        accessToken = accessToken,
        body = JSONObject().put("name", name)
    ) { it.getJSONArray("passkeys").toPasskeys() }

    override suspend fun beginPasskeyReauthentication(accessToken: String): PasskeyOptions = request(
        path = "/me/reauth/passkeys/options",
        method = "POST",
        accessToken = accessToken,
        body = JSONObject()
    ) { it.toPasskeyOptions() }

    override suspend fun reauthenticateWithTelegram(accessToken: String, idToken: String) {
        request(
            path = "/me/reauth/telegram",
            method = "POST",
            accessToken = accessToken,
            body = JSONObject().put("idToken", idToken)
        ) { }
    }

    override suspend fun finishPasskeyReauthentication(
        accessToken: String,
        operationId: String,
        credentialJson: String
    ) {
        request(
            path = "/me/reauth/passkeys/verify",
            method = "POST",
            accessToken = accessToken,
            body = JSONObject()
                .put("operationId", operationId)
                .put("credential", JSONObject(credentialJson))
        ) { }
    }

    override suspend fun deletePasskey(accessToken: String, id: String): DeletedPasskey = request(
        path = "/me/passkeys/$id", method = "DELETE", accessToken = accessToken
    ) { DeletedPasskey(it.getString("credentialId"), it.getString("rpId")) }

    private suspend fun <T> request(
        path: String,
        method: String,
        accessToken: String?,
        body: JSONObject? = null,
        parse: (JSONObject) -> T
    ): T = withContext(ioDispatcher) {
        val requestUrl = URL(baseUrl + path)
        val startedAt = NetworkRequestLogger.start(method, requestUrl)
        var connection: HttpURLConnection? = null
        try {
            connection = openConnection(requestUrl, method).apply {
                if (appToken.isNotBlank()) setRequestProperty("X-App-Token", appToken)
                accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use {
                    it.write(body.toString())
                }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.readUtf8Bounded(MAX_RESPONSE_BYTES)
                .orEmpty()
            if (status !in 200..299) {
                val errorCode = runCatching { JSONObject(text).optString("code") }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
                val requestId = connection.getHeaderField("X-Request-Id")
                NetworkRequestLogger.httpFailure(
                    method, requestUrl, status, startedAt, errorCode, requestId
                )
                throw BackendHttpException(status, errorCode, requestId)
            }
            val apiVersion = connection.getHeaderField(API_VERSION_HEADER)?.toIntOrNull()
            if (apiVersion != REQUIRED_API_VERSION) {
                NetworkRequestLogger.incompatibleBackend(
                    method,
                    requestUrl,
                    startedAt,
                    REQUIRED_API_VERSION,
                    apiVersion
                )
                throw BackendIncompatibleException(REQUIRED_API_VERSION, apiVersion)
            }
            parse(if (text.isBlank()) JSONObject() else JSONObject(text)).also {
                NetworkRequestLogger.success(method, requestUrl, status, startedAt)
            }
        } catch (error: ResponseTooLargeException) {
            NetworkRequestLogger.invalidResponse(method, requestUrl, startedAt, error)
            throw BackendResponseException(error)
        } catch (error: IOException) {
            NetworkRequestLogger.transportFailure(method, requestUrl, startedAt, error)
            throw BackendNetworkException(error)
        } catch (error: org.json.JSONException) {
            NetworkRequestLogger.invalidResponse(method, requestUrl, startedAt, error)
            throw BackendResponseException(error)
        } catch (error: IllegalArgumentException) {
            NetworkRequestLogger.invalidResponse(method, requestUrl, startedAt, error)
            throw BackendResponseException(error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnection(url: URL, method: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }

    private fun parseAuthenticationResult(
        json: JSONObject,
        accessToken: String
    ): AuthenticationResult {
        if (!json.has("account") && json.has("user")) {
            throw BackendIncompatibleException(REQUIRED_API_VERSION, actualVersion = 1)
        }
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
                userId = telegram.optionalString("userId"),
                name = telegram.optionalString("name"),
                givenName = telegram.optionalString("givenName"),
                familyName = telegram.optionalString("familyName"),
                username = telegram.optionalString("username"),
                pictureUrl = telegram.optionalString("picture"),
                phoneNumber = telegram.optionalString("phoneNumber"),
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
                    emojiStatus = profile.optJSONObject("emojiStatus")?.toProfileEmojiSelection()
                        ?: DEFAULT_PROFILE_EMOJI,
                    phoneNumber = profile.optionalString("phoneNumber"),
                    visualSeed = profile.getString("visualSeed"),
                    createdAt = profile.getString("createdAt"),
                    updatedAt = profile.getString("updatedAt")
                )
            }
        )
    }
}

private const val API_VERSION_HEADER = "X-Telegram-Bloom-Api-Version"
private const val REQUIRED_API_VERSION = 8
private const val MAX_RESPONSE_BYTES = 128 * 1024

private fun JSONObject.optionalString(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != JSONObject.NULL.toString() }

private fun JSONArray.toStrings(): List<String> = (0 until length()).map(::getString)

private fun JSONObject.toPasskeyOptions() = PasskeyOptions(
    operationId = getString("operationId"),
    requestJson = getJSONObject("publicKey").toString()
)

private fun JSONArray.toPasskeys(): List<PasskeyInfo> = (0 until length()).map { index ->
    getJSONObject(index).let { value ->
        PasskeyInfo(
            id = value.getString("id"),
            credentialId = value.getString("credentialId"),
            name = value.getString("name"),
            createdAt = value.getString("createdAt"),
            lastUsedAt = value.optionalString("lastUsedAt"),
            deviceType = value.optionalString("deviceType"),
            backedUp = value.optBoolean("backedUp")
        )
    }
}

private fun ProfileEmojiSelection.toJson(): JSONObject = JSONObject()
    .put("setId", setId)
    .put("emojiId", emojiId)

private fun JSONObject.toProfileEmojiSelection() = ProfileEmojiSelection(
    setId = getString("setId"),
    emojiId = getString("emojiId")
)

class BackendHttpException(
    val statusCode: Int,
    val errorCode: String? = null,
    val requestId: String? = null
) : Exception()

class BackendResponseException(cause: Throwable) : Exception(cause)

class BackendIncompatibleException(
    val expectedVersion: Int,
    val actualVersion: Int?
) : Exception("Backend API version ${actualVersion ?: "missing"}; expected $expectedVersion")

class BackendConfigurationException : Exception()

class BackendNetworkException(cause: Throwable) : Exception(cause)
