package com.markettwits.devx.tgsignin.data.repository

import androidx.credentials.exceptions.domerrors.AbortError
import androidx.credentials.exceptions.domerrors.ConstraintError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.domerrors.TimeoutError
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.markettwits.devx.tgsignin.data.datasource.BackendConfigurationException
import com.markettwits.devx.tgsignin.data.datasource.BackendHttpException
import com.markettwits.devx.tgsignin.data.model.PasskeyError
import com.markettwits.devx.tgsignin.data.model.PasskeyUserAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PasskeyErrorMapperTest {

    @Test
    fun `credential manager cancellations map to Cancelled`() {
        val createCancelled = CreateCredentialCancellationException("User dismissed")
            .toPasskeyError()
        assertTrue(createCancelled is PasskeyError.Cancelled)
        assertEquals(PasskeyUserAction.None, createCancelled.userAction)

        val getCancelled = GetCredentialCancellationException("User dismissed")
            .toPasskeyError()
        assertTrue(getCancelled is PasskeyError.Cancelled)

        val domAbort = CreatePublicKeyCredentialDomException(AbortError(), "aborted")
            .toPasskeyError()
        assertTrue(domAbort is PasskeyError.Cancelled)
    }

    @Test
    fun `screen lock missing maps to ScreenLockRequired`() {
        val providerLock = CreateCredentialProviderConfigurationException("Screen lock is required")
            .toPasskeyError()
        assertTrue(providerLock is PasskeyError.ScreenLockRequired)
        assertEquals(PasskeyUserAction.SetupScreenLock, providerLock.userAction)

        val domLock = GetPublicKeyCredentialDomException(NotAllowedError(), "Keyguard not setup")
            .toPasskeyError()
        assertTrue(domLock is PasskeyError.ScreenLockRequired)
    }

    @Test
    fun `provider missing maps to ProviderUnavailable`() {
        val providerMissing = GetCredentialProviderConfigurationException("Google Play Services missing")
            .toPasskeyError()
        assertTrue(providerMissing is PasskeyError.ProviderUnavailable)
        assertEquals(PasskeyUserAction.UpdatePlayServices, providerMissing.userAction)

        val domNotSupported = CreatePublicKeyCredentialDomException(NotSupportedError(), "Not supported")
            .toPasskeyError()
        assertTrue(domNotSupported is PasskeyError.ProviderUnavailable)

        val oldSdk = IllegalStateException("Passkeys require Android 9 or newer")
            .toPasskeyError()
        assertTrue(oldSdk is PasskeyError.ProviderUnavailable)
    }

    @Test
    fun `missing passkey maps to CredentialNotFound`() {
        val noCred = NoCredentialException("No passkey on device").toPasskeyError()
        assertTrue(noCred is PasskeyError.CredentialNotFound)
        assertEquals(PasskeyUserAction.Retry, noCred.userAction)

        val serverCredNotFound = BackendHttpException(404, errorCode = "CREDENTIAL_NOT_FOUND")
            .toPasskeyError()
        assertTrue(serverCredNotFound is PasskeyError.CredentialNotFound)
    }

    @Test
    fun `security errors and origin mismatches map to DigitalAssetLinksInvalid`() {
        val domSecurity = CreatePublicKeyCredentialDomException(SecurityError(), "Origin not allowed")
            .toPasskeyError()
        assertTrue(domSecurity is PasskeyError.DigitalAssetLinksInvalid)
        assertEquals(PasskeyUserAction.OpenSettings, domSecurity.userAction)

        val originRejected = BackendHttpException(400, errorCode = "ORIGIN_REJECTED")
            .toPasskeyError()
        assertTrue(originRejected is PasskeyError.DigitalAssetLinksInvalid)

        val rpidRejected = BackendHttpException(400, errorCode = "RP_ID_REJECTED")
            .toPasskeyError()
        assertTrue(rpidRejected is PasskeyError.DigitalAssetLinksInvalid)
    }

    @Test
    fun `expired session or reauth required maps to SessionExpired`() {
        val sessionInvalid = BackendHttpException(401, errorCode = "SESSION_INVALID")
            .toPasskeyError()
        assertTrue(sessionInvalid is PasskeyError.SessionExpired)
        assertEquals(PasskeyUserAction.SignInAgain, sessionInvalid.userAction)

        val reauthRequired = BackendHttpException(403, errorCode = "REAUTHENTICATION_REQUIRED")
            .toPasskeyError()
        assertTrue(reauthRequired is PasskeyError.SessionExpired)
    }

    @Test
    fun `challenge timeout maps to ChallengeExpired`() {
        val serverExpired = BackendHttpException(400, errorCode = "CHALLENGE_EXPIRED")
            .toPasskeyError()
        assertTrue(serverExpired is PasskeyError.ChallengeExpired)
        assertEquals(PasskeyUserAction.Retry, serverExpired.userAction)

        val domTimeout = CreatePublicKeyCredentialDomException(TimeoutError(), "Timed out")
            .toPasskeyError()
        assertTrue(domTimeout is PasskeyError.ChallengeExpired)
    }

    @Test
    fun `already registered credentials map to AlreadyRegistered`() {
        val serverDuplicate = BackendHttpException(409, errorCode = "CREDENTIAL_ALREADY_REGISTERED")
            .toPasskeyError()
        assertTrue(serverDuplicate is PasskeyError.AlreadyRegistered)
        assertEquals(PasskeyUserAction.Retry, serverDuplicate.userAction)

        val domConstraint = CreatePublicKeyCredentialDomException(ConstraintError(), "Excluded credential matched")
            .toPasskeyError()
        assertTrue(domConstraint is PasskeyError.AlreadyRegistered)
    }

    @Test
    fun `limit reached maps to LimitReached`() {
        val limit = BackendHttpException(409, errorCode = "PASSKEY_LIMIT_REACHED")
            .toPasskeyError()
        assertTrue(limit is PasskeyError.LimitReached)
        assertEquals(PasskeyUserAction.None, limit.userAction)
    }

    @Test
    fun `unconfigured passkeys map to BackendNotConfigured`() {
        val notConfigured = BackendHttpException(503, errorCode = "PASSKEYS_NOT_CONFIGURED")
            .toPasskeyError()
        assertTrue(notConfigured is PasskeyError.BackendNotConfigured)
        assertEquals(PasskeyUserAction.OpenSettings, notConfigured.userAction)

        val localConfigErr = BackendConfigurationException().toPasskeyError()
        assertTrue(localConfigErr is PasskeyError.BackendNotConfigured)
    }

    @Test
    fun `network failures map to NetworkUnavailable`() {
        assertTrue(UnknownHostException().toPasskeyError() is PasskeyError.NetworkUnavailable)
        assertTrue(ConnectException().toPasskeyError() is PasskeyError.NetworkUnavailable)
        assertTrue(SocketTimeoutException().toPasskeyError() is PasskeyError.NetworkUnavailable)
    }

    @Test
    fun `unknown errors preserve request ID and support action`() {
        val unknownServer = BackendHttpException(500, errorCode = "INTERNAL_ERROR", requestId = "req-test-999")
            .toPasskeyError()
        assertTrue(unknownServer is PasskeyError.Unknown)
        assertEquals("req-test-999", (unknownServer as PasskeyError.Unknown).requestId)
        assertEquals(PasskeyUserAction.ContactSupport("req-test-999"), unknownServer.userAction)

        val unknownGeneric = RuntimeException("Something unexpected").toPasskeyError()
        assertTrue(unknownGeneric is PasskeyError.Unknown)
        assertEquals(null, (unknownGeneric as PasskeyError.Unknown).requestId)
    }
}
