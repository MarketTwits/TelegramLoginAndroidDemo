package com.markettwits.devx.tgsignin.data.datasource

import com.markettwits.devx.tgsignin.data.model.AvatarSource
import com.markettwits.devx.tgsignin.data.model.OnboardingState
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.ProfileIntent
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class TelegramAuthApiDataSourceTest {

    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private lateinit var dataSource: TelegramAuthApiDataSourceImpl

    private var lastRecordedMethod: String? = null
    private var lastRecordedPath: String? = null
    private var lastRecordedHeaders: Map<String, List<String>> = emptyMap()
    private var lastRecordedBody: String? = null

    private var responseCode: Int = 200
    private var responseHeaders: Map<String, String> = mapOf("X-Telegram-Bloom-Api-Version" to "8")
    private var responseBody: String = "{}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            handleExchange(exchange)
        }
        server.start()
        serverPort = server.address.port

        val config = TelegramLoginConfig(
            clientId = "test_client",
            redirectUri = "https://example.com/tglogin",
            redirectHost = "example.com",
            backendUrl = "http://127.0.0.1:$serverPort",
            appToken = "test_app_token"
        )
        dataSource = TelegramAuthApiDataSourceImpl(config, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun handleExchange(exchange: HttpExchange) {
        lastRecordedMethod = exchange.requestMethod
        lastRecordedPath = exchange.requestURI.path
        lastRecordedHeaders = exchange.requestHeaders
        lastRecordedBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()

        responseHeaders.forEach { (key, value) ->
            exchange.responseHeaders.set(key, value)
        }
        val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(responseCode, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.close()
    }

    @Test
    fun `beginPasskeyAuthentication sends empty body with X-App-Token and parses PasskeyOptions`() = runBlocking {
        responseBody = JSONObject()
            .put("operationId", "op_auth_42")
            .put("publicKey", JSONObject().put("challenge", "mock_challenge").put("rpId", "example.com"))
            .toString()

        val options = dataSource.beginPasskeyAuthentication()

        assertEquals("POST", lastRecordedMethod)
        assertEquals("/auth/passkeys/options", lastRecordedPath)
        assertEquals("test_app_token", lastRecordedHeaders["X-app-token"]?.firstOrNull())
        assertEquals("{}", lastRecordedBody)
        assertEquals("op_auth_42", options.operationId)
        assertTrue(options.requestJson.contains("mock_challenge"))
        assertTrue(options.requestJson.contains("example.com"))
    }

    @Test
    fun `finishPasskeyAuthentication sends operationId and credential and parses AuthenticationResult`() = runBlocking {
        val sampleAccount = JSONObject()
            .put("id", "acc_1")
            .put("memberNumber", 101L)
            .put("onboardingState", "PROFILE_COMPLETED")
            .put("registeredAt", "2026-01-01T00:00:00Z")
            .put("lastLoginAt", "2026-01-02T00:00:00Z")
            .put("loginCount", 3)
        val sampleTelegram = JSONObject()
            .put("userId", "tg_user_1")
            .put("name", "Test User")
            .put("username", "testuser")
        val sampleProfile = JSONObject()
            .put("displayName", "Test User")
            .put("headline", "Developer")
            .put("intent", "BUILDING")
            .put("topics", org.json.JSONArray(listOf("ANDROID")))
            .put("avatarSource", "TELEGRAM")
            .put("visualSeed", "seed_1")
            .put("createdAt", "2026-01-01T00:00:00Z")
            .put("updatedAt", "2026-01-02T00:00:00Z")

        responseBody = JSONObject()
            .put("sessionToken", "new_session_token_123")
            .put("account", sampleAccount)
            .put("telegram", sampleTelegram)
            .put("profile", sampleProfile)
            .toString()

        val result = dataSource.finishPasskeyAuthentication(
            operationId = "op_auth_42",
            credentialJson = """{"id":"cred_abc","rawId":"raw_abc"}"""
        )

        assertEquals("POST", lastRecordedMethod)
        assertEquals("/auth/passkeys/verify", lastRecordedPath)
        val bodyJson = JSONObject(lastRecordedBody!!)
        assertEquals("op_auth_42", bodyJson.getString("operationId"))
        assertEquals("cred_abc", bodyJson.getJSONObject("credential").getString("id"))

        assertEquals("new_session_token_123", result.accessToken)
        assertEquals("acc_1", result.account.id)
        assertEquals(OnboardingState.PROFILE_COMPLETED, result.account.onboardingState)
        assertEquals("Test User", result.profile?.displayName)
    }

    @Test
    fun `listPasskeys sends Bearer token and parses passkeys array`() = runBlocking {
        responseBody = JSONObject()
            .put(
                "passkeys",
                org.json.JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "pk_1")
                            .put("credentialId", "cred_id_1")
                            .put("name", "Pixel 9")
                            .put("createdAt", "2026-01-01T00:00:00Z")
                            .put("lastUsedAt", "2026-01-02T00:00:00Z")
                            .put("deviceType", "multiDevice")
                            .put("backedUp", true)
                    )
                    .put(
                        JSONObject()
                            .put("id", "pk_2")
                            .put("credentialId", "cred_id_2")
                            .put("name", "YubiKey")
                            .put("createdAt", "2026-01-03T00:00:00Z")
                            .put("backedUp", false)
                    )
            )
            .toString()

        val passkeys = dataSource.listPasskeys("user_session_token")

        assertEquals("GET", lastRecordedMethod)
        assertEquals("/me/passkeys", lastRecordedPath)
        assertEquals("Bearer user_session_token", lastRecordedHeaders["Authorization"]?.firstOrNull())

        assertEquals(2, passkeys.size)
        assertEquals("pk_1", passkeys[0].id)
        assertEquals("Pixel 9", passkeys[0].name)
        assertEquals("multiDevice", passkeys[0].deviceType)
        assertTrue(passkeys[0].backedUp)

        assertEquals("pk_2", passkeys[1].id)
        assertEquals("YubiKey", passkeys[1].name)
        assertNull(passkeys[1].lastUsedAt)
        assertFalse(passkeys[1].backedUp)
    }

    @Test
    fun `finishPasskeyRegistration sends operationId, credential and name`() = runBlocking {
        responseBody = JSONObject()
            .put(
                "passkeys",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("id", "pk_new")
                        .put("credentialId", "cred_new")
                        .put("name", "My Work Passkey")
                        .put("createdAt", "2026-01-01T00:00:00Z")
                        .put("backedUp", true)
                )
            )
            .toString()

        val passkeys = dataSource.finishPasskeyRegistration(
            accessToken = "user_session_token",
            operationId = "op_reg_1",
            credentialJson = """{"id":"cred_new","rawId":"raw_new"}""",
            name = "My Work Passkey"
        )

        assertEquals("POST", lastRecordedMethod)
        assertEquals("/me/passkeys/registration/verify", lastRecordedPath)
        val bodyJson = JSONObject(lastRecordedBody!!)
        assertEquals("op_reg_1", bodyJson.getString("operationId"))
        assertEquals("My Work Passkey", bodyJson.getString("name"))
        assertEquals("cred_new", bodyJson.getJSONObject("credential").getString("id"))
        assertEquals(1, passkeys.size)
        assertEquals("My Work Passkey", passkeys[0].name)
    }

    @Test
    fun `renamePasskey sends PATCH with new name and parses updated list`() = runBlocking {
        responseBody = JSONObject()
            .put("passkeys", org.json.JSONArray())
            .toString()

        val passkeys = dataSource.renamePasskey("user_session_token", "pk_123", "Renamed Key")

        val effectiveMethod = lastRecordedHeaders["X-http-method-override"]?.firstOrNull() ?: lastRecordedMethod
        assertEquals("PATCH", effectiveMethod)
        assertEquals("/me/passkeys/pk_123", lastRecordedPath)
        val bodyJson = JSONObject(lastRecordedBody!!)
        assertEquals("Renamed Key", bodyJson.getString("name"))
        assertTrue(passkeys.isEmpty())
    }

    @Test
    fun `deletePasskey sends DELETE and parses DeletedPasskey response`() = runBlocking {
        responseBody = JSONObject()
            .put("credentialId", "cred_to_delete")
            .put("rpId", "example.com")
            .toString()

        val deleted = dataSource.deletePasskey("user_session_token", "pk_to_delete")

        assertEquals("DELETE", lastRecordedMethod)
        assertEquals("/me/passkeys/pk_to_delete", lastRecordedPath)
        assertEquals("cred_to_delete", deleted.credentialId)
        assertEquals("example.com", deleted.rpId)
    }

    @Test
    fun `beginPasskeyReauthentication and finishPasskeyReauthentication send expected requests`() = runBlocking {
        responseBody = JSONObject()
            .put("operationId", "reauth_op_9")
            .put("publicKey", JSONObject().put("challenge", "reauth_ch"))
            .toString()

        val options = dataSource.beginPasskeyReauthentication("user_session_token")
        assertEquals("/me/reauth/passkeys/options", lastRecordedPath)
        assertEquals("reauth_op_9", options.operationId)

        responseBody = "{}"
        dataSource.finishPasskeyReauthentication(
            "user_session_token",
            "reauth_op_9",
            """{"id":"cred_1"}"""
        )
        assertEquals("/me/reauth/passkeys/verify", lastRecordedPath)
        val bodyJson = JSONObject(lastRecordedBody!!)
        assertEquals("reauth_op_9", bodyJson.getString("operationId"))
    }

    @Test
    fun `reauthenticateWithTelegram sends POST with idToken`() = runBlocking {
        responseBody = "{}"
        dataSource.reauthenticateWithTelegram("user_session_token", "tg_reauth_token")

        assertEquals("POST", lastRecordedMethod)
        assertEquals("/me/reauth/telegram", lastRecordedPath)
        val body = JSONObject(lastRecordedBody!!)
        assertEquals("tg_reauth_token", body.getString("idToken"))
    }

    @Test
    fun `http error parses status code, error code and X-Request-Id into BackendHttpException`() = runBlocking {
        responseCode = 400
        responseHeaders = mapOf(
            "X-Telegram-Bloom-Api-Version" to "8",
            "X-Request-Id" to "req_trace_404"
        )
        responseBody = JSONObject().put("code", "CHALLENGE_EXPIRED").toString()

        try {
            dataSource.beginPasskeyAuthentication()
            fail("Expected BackendHttpException")
        } catch (ex: BackendHttpException) {
            assertEquals(400, ex.statusCode)
            assertEquals("CHALLENGE_EXPIRED", ex.errorCode)
            assertEquals("req_trace_404", ex.requestId)
        }
    }

    @Test
    fun `incompatible API version header throws BackendIncompatibleException`() = runBlocking {
        responseHeaders = mapOf("X-Telegram-Bloom-Api-Version" to "7")
        responseBody = "{}"

        try {
            dataSource.beginPasskeyAuthentication()
            fail("Expected BackendIncompatibleException")
        } catch (ex: BackendIncompatibleException) {
            assertEquals(8, ex.expectedVersion)
            assertEquals(7, ex.actualVersion)
        }
    }
}
