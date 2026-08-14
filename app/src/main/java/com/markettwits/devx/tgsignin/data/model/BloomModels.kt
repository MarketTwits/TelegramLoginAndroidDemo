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

const val DEFAULT_PROFILE_BADGE_ID = "outline"

enum class ProfileBadgeKind {
    LOTTIE_TGS,
    STATIC_WEBP
}

data class ProfileBadge(
    val id: String,
    val kind: ProfileBadgeKind,
    val assetPath: String,
    val sha256: String,
    val sizeBytes: Int,
    val width: Int,
    val height: Int,
    val labels: Map<String, String>,
    val enabled: Boolean
) {
    fun label(language: String): String = labels[language] ?: labels["en"] ?: id
}

data class ProfileBadgeCatalog(
    val version: Int,
    val defaultBadgeId: String,
    val badges: List<ProfileBadge>
)

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
    val badgeId: String,
    val visualSeed: String,
    val createdAt: String,
    val updatedAt: String
)

data class ProfileDraft(
    val displayName: String = "",
    val headline: String = "",
    val intent: ProfileIntent = ProfileIntent.BUILDING,
    val topics: Set<ProfileTopic> = emptySet(),
    val avatarSource: AvatarSource = AvatarSource.TELEGRAM,
    val badgeId: String = DEFAULT_PROFILE_BADGE_ID
) {
    val isValid: Boolean
        get() = displayName.isNotBlank() && displayName.trim().length <= 80 &&
            headline.isNotBlank() && headline.trim().length <= 120 &&
            topics.size in 1..3 && badgeId.isNotBlank()
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
