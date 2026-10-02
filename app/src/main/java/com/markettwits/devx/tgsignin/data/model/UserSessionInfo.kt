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

fun normalizedDeviceLabel(value: String?): String? {
    val label = value?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        ?: return null
    if (!label.startsWith("Dalvik/", ignoreCase = true)) return label
    return DALVIK_DEVICE_PATTERN.find(label)?.groupValues?.get(1)?.trim()
        ?.takeIf(String::isNotEmpty) ?: "Android device"
}

private val DALVIK_DEVICE_PATTERN = Regex(";\\s*([^;)]+?)\\s+Build/", RegexOption.IGNORE_CASE)
