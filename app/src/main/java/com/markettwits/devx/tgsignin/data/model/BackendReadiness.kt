package com.markettwits.devx.tgsignin.data.model

data class BackendReadiness(
    val serviceReady: Boolean,
    val databaseConnected: Boolean,
    val telegramConfigured: Boolean
) {
    val isReady: Boolean
        get() = serviceReady && databaseConnected && telegramConfigured
}
