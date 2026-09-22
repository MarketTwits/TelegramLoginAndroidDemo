package com.markettwits.devx.tgsignin.data.model

data class PasskeyOptions(
    val operationId: String,
    val requestJson: String
)

data class PasskeyInfo(
    val id: String,
    val credentialId: String,
    val name: String,
    val createdAt: String,
    val lastUsedAt: String?,
    val deviceType: String?,
    val backedUp: Boolean
)

data class DeletedPasskey(
    val credentialId: String,
    val rpId: String
)
