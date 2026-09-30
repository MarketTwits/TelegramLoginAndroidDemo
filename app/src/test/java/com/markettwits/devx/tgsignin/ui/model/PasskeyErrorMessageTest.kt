package com.markettwits.devx.tgsignin.ui.model

import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.PasskeyError
import com.markettwits.devx.tgsignin.data.model.PasskeyUserAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasskeyErrorMessageTest {

    @Test
    fun `every passkey error maps to a dedicated string resource`() {
        assertEquals(R.string.passkey_error_cancelled, PasskeyError.Cancelled().getUserMessageRes())
        assertEquals(R.string.passkey_error_screen_lock_required, PasskeyError.ScreenLockRequired().getUserMessageRes())
        assertEquals(R.string.passkey_error_provider_unavailable, PasskeyError.ProviderUnavailable().getUserMessageRes())
        assertEquals(R.string.passkey_error_credential_not_found, PasskeyError.CredentialNotFound().getUserMessageRes())
        assertEquals(R.string.passkey_error_digital_asset_links_invalid, PasskeyError.DigitalAssetLinksInvalid().getUserMessageRes())
        assertEquals(R.string.passkey_error_session_expired, PasskeyError.SessionExpired().getUserMessageRes())
        assertEquals(R.string.passkey_error_challenge_expired, PasskeyError.ChallengeExpired().getUserMessageRes())
        assertEquals(R.string.passkey_error_already_registered, PasskeyError.AlreadyRegistered().getUserMessageRes())
        assertEquals(R.string.passkey_error_limit_reached, PasskeyError.LimitReached().getUserMessageRes())
        assertEquals(R.string.passkey_error_backend_not_configured, PasskeyError.BackendNotConfigured().getUserMessageRes())
        assertEquals(R.string.passkey_error_network_unavailable, PasskeyError.NetworkUnavailable().getUserMessageRes())
        assertEquals(R.string.passkey_error_unknown, PasskeyError.Unknown().getUserMessageRes())
        assertEquals(R.string.passkey_error_unknown_with_request_id, PasskeyError.Unknown(requestId = "req-1").getUserMessageRes())
    }

    @Test
    fun `every passkey action maps to the expected action string resource`() {
        assertEquals(R.string.passkey_action_retry, PasskeyUserAction.Retry.getActionTextRes())
        assertEquals(R.string.passkey_action_sign_in_again, PasskeyUserAction.SignInAgain.getActionTextRes())
        assertEquals(R.string.passkey_action_setup_screen_lock, PasskeyUserAction.SetupScreenLock.getActionTextRes())
        assertEquals(R.string.passkey_action_update_play_services, PasskeyUserAction.UpdatePlayServices.getActionTextRes())
        assertEquals(R.string.passkey_action_open_settings, PasskeyUserAction.OpenSettings.getActionTextRes())
        assertEquals(R.string.passkey_action_contact_support, PasskeyUserAction.ContactSupport("req-1").getActionTextRes())
        assertNull(PasskeyUserAction.None.getActionTextRes())
    }
}
