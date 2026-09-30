package com.markettwits.devx.tgsignin.data.model

sealed interface PasskeyUserAction {
    data object Retry : PasskeyUserAction
    data object SignInAgain : PasskeyUserAction
    data object SetupScreenLock : PasskeyUserAction
    data object UpdatePlayServices : PasskeyUserAction
    data object OpenSettings : PasskeyUserAction
    data class ContactSupport(val requestId: String?) : PasskeyUserAction
    data object None : PasskeyUserAction
}

sealed class PasskeyError(
    val userAction: PasskeyUserAction = PasskeyUserAction.None,
    cause: Throwable? = null
) : Exception(cause) {
    data class Cancelled(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.None, originalCause)

    data class ScreenLockRequired(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.SetupScreenLock, originalCause)

    data class ProviderUnavailable(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.UpdatePlayServices, originalCause)

    data class CredentialNotFound(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.Retry, originalCause)

    data class DigitalAssetLinksInvalid(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.OpenSettings, originalCause)

    data class SessionExpired(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.SignInAgain, originalCause)

    data class ChallengeExpired(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.Retry, originalCause)

    data class AlreadyRegistered(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.Retry, originalCause)

    data class LimitReached(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.None, originalCause)

    data class BackendNotConfigured(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.OpenSettings, originalCause)

    data class NetworkUnavailable(val originalCause: Throwable? = null) :
        PasskeyError(PasskeyUserAction.Retry, originalCause)

    data class Unknown(
        val requestId: String? = null,
        val originalCause: Throwable? = null
    ) : PasskeyError(PasskeyUserAction.ContactSupport(requestId), originalCause)
}
