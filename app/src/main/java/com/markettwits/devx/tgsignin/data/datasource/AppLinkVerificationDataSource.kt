package com.markettwits.devx.tgsignin.data.datasource

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.markettwits.devx.tgsignin.data.model.AppLinkVerification
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

interface AppLinkVerificationDataSource {
    suspend fun checkVerification(): AppLinkVerification
}

class AppLinkVerificationDataSourceImpl(
    private val context: Context,
    config: TelegramLoginConfig,
    private val ioDispatcher: CoroutineDispatcher
) : AppLinkVerificationDataSource {
    private val assetLinksUrl = URL("https://${config.redirectHost}$ASSET_LINKS_PATH")

    override suspend fun checkVerification(): AppLinkVerification = withContext(ioDispatcher) {
        val startedAt = NetworkRequestLogger.start(HTTP_GET, assetLinksUrl)
        var connection: HttpURLConnection? = null
        try {
            connection = openConnection()
            val statusCode = connection.responseCode
            if (statusCode !in SUCCESS_STATUS_CODES) {
                NetworkRequestLogger.httpFailure(HTTP_GET, assetLinksUrl, statusCode, startedAt)
                throw BackendHttpException(statusCode)
            }
            val body = connection.inputStream?.readUtf8Bounded(MAX_RESPONSE_BYTES).orEmpty()
            parseVerification(body).also {
                NetworkRequestLogger.success(HTTP_GET, assetLinksUrl, statusCode, startedAt)
            }
        } catch (error: ResponseTooLargeException) {
            NetworkRequestLogger.invalidResponse(HTTP_GET, assetLinksUrl, startedAt, error)
            throw BackendResponseException(error)
        } catch (error: IOException) {
            NetworkRequestLogger.transportFailure(HTTP_GET, assetLinksUrl, startedAt, error)
            throw BackendNetworkException(error)
        } catch (error: JSONException) {
            NetworkRequestLogger.invalidResponse(HTTP_GET, assetLinksUrl, startedAt, error)
            throw BackendResponseException(error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnection(): HttpURLConnection =
        (assetLinksUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = HTTP_GET
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty(HEADER_ACCEPT, JSON_MEDIA_TYPE)
        }

    private fun parseVerification(body: String): AppLinkVerification = verifyAssetLinks(
        body = body,
        packageName = context.packageName,
        installedFingerprints = installedCertificateFingerprints()
    )

    @Suppress("DEPRECATION")
    private fun installedCertificateFingerprints(): Set<String> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, flags)
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            packageInfo.signatures
        }
        return signatures
            .orEmpty()
            .map { signature ->
                MessageDigest.getInstance(SHA_256).digest(signature.toByteArray()).toHexString()
            }
            .toSet()
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        HEX_BYTE_FORMAT.format(byte)
    }

    private companion object {
        const val ASSET_LINKS_PATH = "/.well-known/assetlinks.json"
        const val HTTP_GET = "GET"
        const val NETWORK_TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_BYTES = 256 * 1024
        const val HEADER_ACCEPT = "Accept"
        const val JSON_MEDIA_TYPE = "application/json"
        const val SHA_256 = "SHA-256"
        const val HEX_BYTE_FORMAT = "%02x"
        val SUCCESS_STATUS_CODES = 200..299
    }
}

internal fun verifyAssetLinks(
    body: String,
    packageName: String,
    installedFingerprints: Set<String>
): AppLinkVerification {
    val matchingTargets = JSONArray(body)
        .asSequence()
        .mapNotNull { it as? org.json.JSONObject }
        .filter { statement ->
            statement.optJSONArray(JSON_RELATION)
                ?.asSequence()
                ?.any { it == APP_LINK_RELATION } == true
        }
        .mapNotNull { it.optJSONObject(JSON_TARGET) }
        .filter { target ->
            target.optString(JSON_NAMESPACE) == ANDROID_APP_NAMESPACE &&
                    target.optString(JSON_PACKAGE_NAME) == packageName
        }
        .toList()

    val registeredTargets = matchingTargets.map { target ->
        RegisteredAppLinkTarget(
            fingerprints = target.optJSONArray(JSON_FINGERPRINTS)
                ?.asSequence()
                .orEmpty()
                .mapNotNull { it as? String }
                .toSet()
        )
    }
    return verifyRegisteredTargets(registeredTargets, installedFingerprints)
}

internal fun verifyRegisteredTargets(
    targets: List<RegisteredAppLinkTarget>,
    installedFingerprints: Set<String>
): AppLinkVerification {
    if (targets.isEmpty()) return AppLinkVerification.PackageNotRegistered

    val registeredFingerprints = targets
        .asSequence()
        .flatMap { it.fingerprints.asSequence() }
        .map(::normalizeFingerprint)
        .toSet()
    val normalizedInstalledFingerprints = installedFingerprints.map(::normalizeFingerprint).toSet()

    return if (normalizedInstalledFingerprints.any(registeredFingerprints::contains)) {
        AppLinkVerification.Verified
    } else {
        AppLinkVerification.SignatureNotRegistered
    }
}

internal data class RegisteredAppLinkTarget(
    val fingerprints: Set<String>
)

private fun normalizeFingerprint(value: String): String =
    value.filter(Char::isLetterOrDigit).lowercase()

private fun JSONArray.asSequence(): Sequence<Any?> = sequence {
    for (index in 0 until length()) yield(opt(index))
}

private const val JSON_RELATION = "relation"
private const val JSON_TARGET = "target"
private const val JSON_NAMESPACE = "namespace"
private const val JSON_PACKAGE_NAME = "package_name"
private const val JSON_FINGERPRINTS = "sha256_cert_fingerprints"
private const val APP_LINK_RELATION = "delegate_permission/common.handle_all_urls"
private const val ANDROID_APP_NAMESPACE = "android_app"
