package com.markettwits.devx.tgsignin.data.datasource

import android.os.SystemClock
import android.util.Log
import com.markettwits.devx.tgsignin.BuildConfig
import java.net.URL

/**
 * Logcat diagnostics for the demo HTTP stack. Credentials, query parameters,
 * request bodies, and response bodies are deliberately excluded.
 */
internal object NetworkRequestLogger {
    private const val TAG = "TelegramBloomHttp"

    fun start(method: String, url: URL): Long = SystemClock.elapsedRealtime().also {
        if (BuildConfig.DEBUG) Log.d(TAG, "--> $method ${url.safeAddress()}")
    }

    fun success(method: String, url: URL, statusCode: Int, startedAt: Long) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "<-- $statusCode $method ${url.safeAddress()} (${elapsed(startedAt)} ms)")
        }
    }

    fun httpFailure(
        method: String,
        url: URL,
        statusCode: Int,
        startedAt: Long,
        errorCode: String? = null,
        requestId: String? = null
    ) {
        if (!BuildConfig.DEBUG) return
        val diagnostics = listOfNotNull(
            errorCode?.let { "code=$it" },
            requestId?.let { "requestId=$it" }
        ).joinToString(separator = " ", prefix = " ").trimEnd()
        Log.w(
            TAG,
            "<-- $statusCode $method ${url.safeAddress()} (${elapsed(startedAt)} ms)$diagnostics"
        )
    }

    fun transportFailure(method: String, url: URL, startedAt: Long, error: Throwable) {
        if (!BuildConfig.DEBUG) return
        Log.e(
            TAG,
            "<-- NETWORK_ERROR $method ${url.safeAddress()} (${elapsed(startedAt)} ms) " +
                "${error.javaClass.simpleName}: ${error.message.orEmpty()}",
            error
        )
    }

    fun invalidResponse(method: String, url: URL, startedAt: Long, error: Throwable) {
        if (!BuildConfig.DEBUG) return
        Log.e(
            TAG,
            "<-- INVALID_RESPONSE $method ${url.safeAddress()} (${elapsed(startedAt)} ms) " +
                error.javaClass.simpleName,
            error
        )
    }

    fun incompatibleBackend(
        method: String,
        url: URL,
        startedAt: Long,
        expectedVersion: Int,
        actualVersion: Int?
    ) {
        if (!BuildConfig.DEBUG) return
        Log.e(
            TAG,
            "<-- INCOMPATIBLE_API $method ${url.safeAddress()} (${elapsed(startedAt)} ms) " +
                "expected=$expectedVersion actual=${actualVersion ?: "missing"}"
        )
    }

    private fun elapsed(startedAt: Long): Long = SystemClock.elapsedRealtime() - startedAt

    private fun URL.safeAddress(): String = buildString {
        append(protocol)
        append("://")
        append(host)
        if (port != -1 && port != defaultPort) append(":$port")
        append(path.ifBlank { "/" })
    }
}
