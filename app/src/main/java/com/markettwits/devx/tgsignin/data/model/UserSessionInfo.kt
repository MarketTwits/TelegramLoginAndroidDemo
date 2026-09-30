package com.markettwits.devx.tgsignin.data.model

data class UserSessionInfo(
    val id: String,
    val createdAt: String,
    val lastSeenAt: String,
    val expiresAt: String,
    val authenticationMethod: String,
    val deviceLabel: String?,
    val current: Boolean
)
