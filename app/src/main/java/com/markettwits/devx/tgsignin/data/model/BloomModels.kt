package com.markettwits.devx.tgsignin.data.model

enum class OnboardingState {
    PROFILE_REQUIRED,
    PROFILE_COMPLETED,
    DISABLED
}

enum class ProfileIntent {
    BUILDING,
    HELPING,
    EXPLORING
}

enum class ProfileTopic {
    ANDROID,
    BACKEND,
    DESIGN,
    SECURITY,
    OPEN_SOURCE,
    AI,
    PRODUCT,
    TELEGRAM,
    OTHER
}

enum class AvatarSource {
    TELEGRAM,
    BLOOM
}

data class ProfileEmojiSelection(
    val setId: String,
    val emojiId: String
)

val DEFAULT_PROFILE_EMOJI = ProfileEmojiSelection(
    setId = "spotty-persik",
    emojiId = "e-0007fab99d521710"
)

data class ProfileEmoji(
    val setId: String,
    val id: String,
    val assetPath: String,
    val sha256: String,
    val sizeBytes: Int,
    val width: Int,
    val height: Int,
    val framesPerSecond: Float,
    val durationMs: Int,
    val enabled: Boolean
)

data class ProfileEmojiSet(
    val id: String,
    val labels: Map<String, String>,
    val thumbnailEmojiId: String,
    val emojis: List<ProfileEmoji>
) {
    fun label(language: String): String = labels[language] ?: labels["en"] ?: id
}

data class ProfileEmojiCatalog(
    val version: Int,
    val defaultEmoji: ProfileEmojiSelection,
    val sets: List<ProfileEmojiSet>
) {
    fun resolve(selection: ProfileEmojiSelection?): ProfileEmojiSelection =
        selection?.takeIf(::contains) ?: defaultEmoji

    fun contains(selection: ProfileEmojiSelection): Boolean =
        sets.firstOrNull { it.id == selection.setId }
            ?.emojis?.any { it.id == selection.emojiId && it.enabled } == true

    fun emoji(selection: ProfileEmojiSelection?): ProfileEmoji? {
        val resolved = resolve(selection)
        return sets.firstOrNull { it.id == resolved.setId }
            ?.emojis?.firstOrNull { it.id == resolved.emojiId }
    }
}

data class ServiceAccount(
    val id: String,
    val memberNumber: Long,
    val onboardingState: OnboardingState,
    val registeredAt: String,
    val lastLoginAt: String,
    val loginCount: Int
)

data class TelegramIdentity(
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val username: String? = null,
    val pictureUrl: String? = null,
    val phoneNumber: String? = null,
    val phoneVerified: Boolean = false,
    val syncedAt: String? = null
) {
    fun suggestedDisplayName(): String = sequenceOf(
        name,
        listOfNotNull(givenName, familyName).joinToString(" ").ifBlank { null },
        username
    ).filterNotNull().firstOrNull(String::isNotBlank) ?: "Telegram user"
}

data class ServiceProfile(
    val displayName: String,
    val headline: String,
    val intent: ProfileIntent,
    val topics: List<ProfileTopic>,
    val avatarSource: AvatarSource,
    val emojiStatus: ProfileEmojiSelection,
    val visualSeed: String,
    val createdAt: String,
    val updatedAt: String,
    val phoneNumber: String? = null
)

data class ProfileDraft(
    val displayName: String = "",
    val headline: String = "",
    val intent: ProfileIntent = ProfileIntent.BUILDING,
    val topics: Set<ProfileTopic> = emptySet(),
    val avatarSource: AvatarSource = AvatarSource.TELEGRAM,
    val emojiStatus: ProfileEmojiSelection? = null,
    val phoneNumber: String = "",
    val phoneNumberEdited: Boolean = false
) {
    val isValid: Boolean
        get() = displayName.isNotBlank() && displayName.trim().length <= 80 &&
            headline.isNotBlank() && headline.trim().length <= 120 &&
                topics.size in 1..3 &&
                phoneNumber.isValidOptionalInternationalPhoneNumber()
}

data class AuthenticationResult(
    val accessToken: String,
    val expiresAt: String?,
    val account: ServiceAccount,
    val telegram: TelegramIdentity,
    val profile: ServiceProfile?
)

sealed interface RootAuthenticationState {
    data object Loading : RootAuthenticationState
    data class Unauthenticated(val sessionExpired: Boolean = false) : RootAuthenticationState
    data class OnboardingRequired(
        val session: AuthenticationResult,
        val draft: ProfileDraft,
        val isOffline: Boolean = false
    ) : RootAuthenticationState
    data class Authenticated(
        val session: AuthenticationResult,
        val isOffline: Boolean = false
    ) : RootAuthenticationState
    data class RecoverableError(val cachedSession: AuthenticationResult?) : RootAuthenticationState
}
