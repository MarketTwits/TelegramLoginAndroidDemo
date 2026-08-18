package com.markettwits.devx.tgsignin.data.model

data class AppRelease(
    val versionName: String,
    val versionCode: Long,
    val tagName: String,
    val title: String,
    val notes: List<String>,
    val releasePageUrl: String,
    val publishedAt: String?
)

data class AppUpdateCache(
    val release: AppRelease? = null,
    val etag: String? = null,
    val checkedAtEpochMillis: Long = 0L
)

sealed interface AppUpdateAvailability {
    data class Available(val release: AppRelease) : AppUpdateAvailability
    data object UpToDate : AppUpdateAvailability
    data object Unavailable : AppUpdateAvailability
}
