package com.markettwits.devx.tgsignin.data.model

enum class TelegramScope(val value: String) {
    Profile("profile"),
    Phone("phone")
}

data class TelegramUser(
    val id: String,
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val username: String? = null,
    val phoneNumber: String? = null,
    val phoneVerified: Boolean = false,
    val pictureUrl: String? = null
)

typealias AuthenticatedSession = AuthenticationResult
