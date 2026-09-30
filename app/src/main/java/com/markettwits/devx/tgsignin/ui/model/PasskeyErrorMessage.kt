package com.markettwits.devx.tgsignin.ui.model

import android.content.Context
import androidx.annotation.StringRes
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.PasskeyError
import com.markettwits.devx.tgsignin.data.model.PasskeyUserAction

data class PasskeyErrorMessage(
    val message: String,
    val actionText: String?,
    val action: PasskeyUserAction
)

@StringRes
fun PasskeyError.getUserMessageRes(): Int = when (this) {
    is PasskeyError.Cancelled -> R.string.passkey_error_cancelled
    is PasskeyError.ScreenLockRequired -> R.string.passkey_error_screen_lock_required
    is PasskeyError.ProviderUnavailable -> R.string.passkey_error_provider_unavailable
    is PasskeyError.CredentialNotFound -> R.string.passkey_error_credential_not_found
    is PasskeyError.DigitalAssetLinksInvalid -> R.string.passkey_error_digital_asset_links_invalid
    is PasskeyError.SessionExpired -> R.string.passkey_error_session_expired
    is PasskeyError.ChallengeExpired -> R.string.passkey_error_challenge_expired
    is PasskeyError.AlreadyRegistered -> R.string.passkey_error_already_registered
    is PasskeyError.LimitReached -> R.string.passkey_error_limit_reached
    is PasskeyError.BackendNotConfigured -> R.string.passkey_error_backend_not_configured
    is PasskeyError.NetworkUnavailable -> R.string.passkey_error_network_unavailable
    is PasskeyError.Unknown -> if (requestId != null) {
        R.string.passkey_error_unknown_with_request_id
    } else {
        R.string.passkey_error_unknown
    }
}

@StringRes
fun PasskeyUserAction.getActionTextRes(): Int? = when (this) {
    PasskeyUserAction.Retry -> R.string.passkey_action_retry
    PasskeyUserAction.SignInAgain -> R.string.passkey_action_sign_in_again
    PasskeyUserAction.SetupScreenLock -> R.string.passkey_action_setup_screen_lock
    PasskeyUserAction.UpdatePlayServices -> R.string.passkey_action_update_play_services
    PasskeyUserAction.OpenSettings -> R.string.passkey_action_open_settings
    is PasskeyUserAction.ContactSupport -> R.string.passkey_action_contact_support
    PasskeyUserAction.None -> null
}

fun PasskeyError.toUserErrorMessage(context: Context): PasskeyErrorMessage {
    val message = if (this is PasskeyError.Unknown && requestId != null) {
        context.getString(R.string.passkey_error_unknown_with_request_id, requestId)
    } else {
        context.getString(getUserMessageRes())
    }

    val actionTextRes = userAction.getActionTextRes()
    val actionText = actionTextRes?.let { context.getString(it) }
    return PasskeyErrorMessage(message, actionText, userAction)
}

fun PasskeyError.toPasskeyErrorMessage(context: Context): PasskeyErrorMessage = toUserErrorMessage(context)

