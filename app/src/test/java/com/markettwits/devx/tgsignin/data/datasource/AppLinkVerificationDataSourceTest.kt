package com.markettwits.devx.tgsignin.data.datasource

import com.markettwits.devx.tgsignin.data.model.AppLinkVerification
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLinkVerificationDataSourceTest {
    @Test
    fun `returns verified when package and fingerprint match`() {
        val result = verifyRegisteredTargets(
            targets = listOf(RegisteredAppLinkTarget(setOf(REGISTERED_FINGERPRINT))),
            installedFingerprints = setOf(REGISTERED_FINGERPRINT.lowercase())
        )

        assertEquals(AppLinkVerification.Verified, result)
    }

    @Test
    fun `returns signature error when package exists but fingerprint does not match`() {
        val result = verifyRegisteredTargets(
            targets = listOf(RegisteredAppLinkTarget(setOf(REGISTERED_FINGERPRINT))),
            installedFingerprints = setOf(OTHER_FINGERPRINT)
        )

        assertEquals(AppLinkVerification.SignatureNotRegistered, result)
    }

    @Test
    fun `returns package error when target is absent`() {
        val result = verifyRegisteredTargets(
            targets = emptyList(),
            installedFingerprints = setOf(REGISTERED_FINGERPRINT)
        )

        assertEquals(AppLinkVerification.PackageNotRegistered, result)
    }

    @Test
    fun `checks every matching statement`() {
        val result = verifyRegisteredTargets(
            targets = listOf(
                RegisteredAppLinkTarget(setOf(OTHER_FINGERPRINT)),
                RegisteredAppLinkTarget(setOf(REGISTERED_FINGERPRINT))
            ),
            installedFingerprints = setOf(REGISTERED_FINGERPRINT)
        )

        assertEquals(AppLinkVerification.Verified, result)
    }

    private companion object {
        const val REGISTERED_FINGERPRINT = "6A:D5:07:B6:5A:EC:F2:C8"
        const val OTHER_FINGERPRINT = "08:AE:C6:1A:BD:B1:D4:49"
    }
}
