package com.markettwits.devx.tgsignin.data.model

data class BackendReadiness(
    val serviceReady: Boolean,
    val databaseConnected: Boolean,
    val telegramConfigured: Boolean,
    val apiVersion: Int = 0
) {
    val isApiCompatible: Boolean
        get() = apiVersion == REQUIRED_BACKEND_API_VERSION

    val isReady: Boolean
        get() = serviceReady && databaseConnected && telegramConfigured && isApiCompatible

    private companion object {
        const val REQUIRED_BACKEND_API_VERSION = 3
    }
}
