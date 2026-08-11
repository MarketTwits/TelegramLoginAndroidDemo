package com.markettwits.devx.tgsignin.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.dataSource.BackendConfigurationException
import com.markettwits.devx.tgsignin.data.dataSource.BackendHttpException
import com.markettwits.devx.tgsignin.data.dataSource.BackendNetworkException
import com.markettwits.devx.tgsignin.data.dataSource.BackendResponseException
import com.markettwits.devx.tgsignin.data.model.AuthenticationError
import com.markettwits.devx.tgsignin.ui.model.toUserMessageRes
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException

class AuthenticationErrorMapperTest {
    @Test
    fun `unknown host becomes network unavailable`() {
        assertTrue(UnknownHostException().toAuthenticationError() is AuthenticationError.NetworkUnavailable)
    }

    @Test
    fun `connection refusal becomes connection failed`() {
        assertTrue(ConnectException().toAuthenticationError() is AuthenticationError.ConnectionFailed)
        assertTrue(
            BackendNetworkException(IOException()).toAuthenticationError()
                is AuthenticationError.ConnectionFailed
        )
    }

    @Test
    fun `timeout remains distinguishable from other network errors`() {
        assertTrue(SocketTimeoutException().toAuthenticationError() is AuthenticationError.Timeout)
    }

    @Test
    fun `http status codes map to actionable categories`() {
        assertTrue(BackendHttpException(401).toAuthenticationError() is AuthenticationError.AuthorizationRejected)
        assertTrue(BackendHttpException(429).toAuthenticationError() is AuthenticationError.TooManyRequests)
        assertTrue(BackendHttpException(503).toAuthenticationError() is AuthenticationError.ServerUnavailable)
        assertEquals(
            422,
            (BackendHttpException(422).toAuthenticationError() as AuthenticationError.RequestRejected).statusCode
        )
    }

    @Test
    fun `invalid backend payload and configuration are separated`() {
        assertTrue(
            BackendResponseException(IllegalArgumentException()).toAuthenticationError()
                is AuthenticationError.InvalidServerResponse
        )
        assertTrue(
            BackendConfigurationException().toAuthenticationError()
                is AuthenticationError.InvalidConfiguration
        )
    }

    @Test
    fun `unwrapped io error is treated as local storage failure`() {
        assertTrue(IOException().toAuthenticationError() is AuthenticationError.LocalStorage)
    }

    @Test
    fun `unknown error maps to a safe localized resource`() {
        val resource = AuthenticationError.Unknown(
            IllegalStateException("secret backend response")
        ).toUserMessageRes()

        assertEquals(R.string.error_unknown, resource)
    }
}
