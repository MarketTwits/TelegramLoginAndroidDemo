package com.markettwits.devx.tgsignin.data.repository

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.markettwits.devx.tgsignin.data.datasource.BackendConfigurationException
import com.markettwits.devx.tgsignin.data.datasource.BackendHttpException
import com.markettwits.devx.tgsignin.data.datasource.BackendNetworkException
import com.markettwits.devx.tgsignin.data.model.PasskeyError
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun Throwable.toPasskeyError(): PasskeyError {
    if (this is PasskeyError) return this

    val causes = generateSequence(this as Throwable?) { it.cause }.toList()

    // 1. Cancellations
    causes.firstOrNull {
        it is CreateCredentialCancellationException ||
            it is GetCredentialCancellationException ||
            it is CreateCredentialInterruptedException ||
            it is GetCredentialInterruptedException
    }?.let { return PasskeyError.Cancelled(it) }

    // 2. DOM Exceptions from Public Key Credential operations
    causes.filterIsInstance<CreatePublicKeyCredentialDomException>().firstOrNull()?.let { domEx ->
        val dom = domEx.domError
        val domType = dom.type
        val message = domEx.message.orEmpty().lowercase()
        return when {
            dom is androidx.credentials.exceptions.domerrors.AbortError || domType.contains("abort", ignoreCase = true) ->
                PasskeyError.Cancelled(domEx)
            dom is androidx.credentials.exceptions.domerrors.NotAllowedError || domType.contains("not_allowed", ignoreCase = true) || domType.contains("notallowed", ignoreCase = true) -> when {
                message.contains("screen lock") || message.contains("keyguard") || message.contains("user verification") ->
                    PasskeyError.ScreenLockRequired(domEx)
                message.contains("timeout") || message.contains("timed out") ->
                    PasskeyError.ChallengeExpired(domEx)
                else -> PasskeyError.Cancelled(domEx)
            }
            dom is androidx.credentials.exceptions.domerrors.SecurityError || domType.contains("security", ignoreCase = true) ->
                PasskeyError.DigitalAssetLinksInvalid(domEx)
            dom is androidx.credentials.exceptions.domerrors.ConstraintError ||
                dom is androidx.credentials.exceptions.domerrors.InvalidStateError ||
                domType.contains("constraint", ignoreCase = true) ||
                domType.contains("invalid_state", ignoreCase = true) ||
                domType.contains("invalidstate", ignoreCase = true) ->
                PasskeyError.AlreadyRegistered(domEx)
            dom is androidx.credentials.exceptions.domerrors.NotSupportedError || domType.contains("not_supported", ignoreCase = true) || domType.contains("notsupported", ignoreCase = true) ->
                PasskeyError.ProviderUnavailable(domEx)
            dom is androidx.credentials.exceptions.domerrors.TimeoutError || domType.contains("timeout", ignoreCase = true) ->
                PasskeyError.ChallengeExpired(domEx)
            else -> PasskeyError.Unknown(originalCause = domEx)
        }
    }

    causes.filterIsInstance<GetPublicKeyCredentialDomException>().firstOrNull()?.let { domEx ->
        val dom = domEx.domError
        val domType = dom.type
        val message = domEx.message.orEmpty().lowercase()
        return when {
            dom is androidx.credentials.exceptions.domerrors.AbortError || domType.contains("abort", ignoreCase = true) ->
                PasskeyError.Cancelled(domEx)
            dom is androidx.credentials.exceptions.domerrors.NotAllowedError || domType.contains("not_allowed", ignoreCase = true) || domType.contains("notallowed", ignoreCase = true) -> when {
                message.contains("screen lock") || message.contains("keyguard") || message.contains("user verification") ->
                    PasskeyError.ScreenLockRequired(domEx)
                message.contains("timeout") || message.contains("timed out") ->
                    PasskeyError.ChallengeExpired(domEx)
                else -> PasskeyError.Cancelled(domEx)
            }
            dom is androidx.credentials.exceptions.domerrors.SecurityError || domType.contains("security", ignoreCase = true) ->
                PasskeyError.DigitalAssetLinksInvalid(domEx)
            dom is androidx.credentials.exceptions.domerrors.ConstraintError ||
                dom is androidx.credentials.exceptions.domerrors.InvalidStateError ||
                domType.contains("constraint", ignoreCase = true) ||
                domType.contains("invalid_state", ignoreCase = true) ||
                domType.contains("invalidstate", ignoreCase = true) ->
                PasskeyError.AlreadyRegistered(domEx)
            dom is androidx.credentials.exceptions.domerrors.NotSupportedError || domType.contains("not_supported", ignoreCase = true) || domType.contains("notsupported", ignoreCase = true) ->
                PasskeyError.ProviderUnavailable(domEx)
            dom is androidx.credentials.exceptions.domerrors.TimeoutError || domType.contains("timeout", ignoreCase = true) ->
                PasskeyError.ChallengeExpired(domEx)
            else -> PasskeyError.Unknown(originalCause = domEx)
        }
    }

    // 3. Missing credentials or provider config issues
    causes.filterIsInstance<NoCredentialException>().firstOrNull()?.let {
        return PasskeyError.CredentialNotFound(it)
    }

    causes.firstOrNull {
        it is CreateCredentialProviderConfigurationException ||
            it is GetCredentialProviderConfigurationException
    }?.let { providerEx ->
        val message = providerEx.message.orEmpty().lowercase()
        return if (message.contains("screen lock") || message.contains("user verification") || message.contains("keyguard")) {
            PasskeyError.ScreenLockRequired(providerEx)
        } else {
            PasskeyError.ProviderUnavailable(providerEx)
        }
    }

    // 4. Server HTTP status / error codes
    causes.filterIsInstance<BackendHttpException>().firstOrNull()?.let { httpError ->
        return when (httpError.errorCode) {
            "CHALLENGE_EXPIRED" -> PasskeyError.ChallengeExpired(httpError)
            "CREDENTIAL_ALREADY_REGISTERED" -> PasskeyError.AlreadyRegistered(httpError)
            "ORIGIN_REJECTED", "RP_ID_REJECTED" -> PasskeyError.DigitalAssetLinksInvalid(httpError)
            "PASSKEY_LIMIT_REACHED" -> PasskeyError.LimitReached(httpError)
            "PASSKEYS_NOT_CONFIGURED" -> PasskeyError.BackendNotConfigured(httpError)
            "SESSION_INVALID", "REAUTHENTICATION_REQUIRED" -> PasskeyError.SessionExpired(httpError)
            "CREDENTIAL_NOT_FOUND", "PASSKEY_NOT_FOUND" -> PasskeyError.CredentialNotFound(httpError)
            else -> when (httpError.statusCode) {
                401, 403 -> PasskeyError.SessionExpired(httpError)
                404 -> PasskeyError.CredentialNotFound(httpError)
                409 -> PasskeyError.AlreadyRegistered(httpError)
                503 -> PasskeyError.BackendNotConfigured(httpError)
                else -> PasskeyError.Unknown(httpError.requestId, httpError)
            }
        }
    }

    // 5. Network failures
    causes.firstOrNull {
        it is UnknownHostException ||
            it is NoRouteToHostException ||
            it is ConnectException ||
            it is SocketTimeoutException ||
            it is SSLException ||
            it is BackendNetworkException ||
            it is IOException
    }?.let { return PasskeyError.NetworkUnavailable(it) }

    // 6. Device compatibility / configuration
    causes.firstOrNull {
        it is IllegalStateException && it.message?.contains("Android 9") == true
    }?.let { return PasskeyError.ProviderUnavailable(it) }

    causes.filterIsInstance<BackendConfigurationException>().firstOrNull()?.let {
        return PasskeyError.BackendNotConfigured(it)
    }

    return PasskeyError.Unknown(originalCause = this)
}
