package com.markettwits.devx.tgsignin.data.model

import com.google.i18n.phonenumbers.PhoneNumberUtil

private val phoneNumberUtil: PhoneNumberUtil by lazy(PhoneNumberUtil::getInstance)

/**
 * Phone numbers in Bloom are unambiguous international numbers. The backend stores E.164;
 * presentation formatting stays local so it follows libphonenumber metadata on each client.
 */
fun String.normalizedInternationalPhoneNumberOrNull(): String? {
    val input = trim()
    if (input.isEmpty() || !input.startsWith('+')) return null
    return runCatching {
        phoneNumberUtil.parse(input, null)
    }.getOrNull()?.takeIf(phoneNumberUtil::isPossibleNumber)?.let {
        phoneNumberUtil.format(it, PhoneNumberUtil.PhoneNumberFormat.E164)
    }
}

fun String.isValidOptionalInternationalPhoneNumber(): Boolean =
    isBlank() || normalizedInternationalPhoneNumberOrNull() != null

/** Telegram's OIDC claim may omit the leading plus despite containing a country code. */
fun String.normalizedTelegramPhoneNumberOrNull(): String? {
    val input = trim()
    if (input.isEmpty()) return null
    return (if (input.startsWith('+')) input else "+$input")
        .normalizedInternationalPhoneNumberOrNull()
}

fun String.formattedInternationalPhoneNumber(): String {
    val normalized = normalizedInternationalPhoneNumberOrNull() ?: return this
    return runCatching {
        phoneNumberUtil.format(
            phoneNumberUtil.parse(normalized, null),
            PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
        )
    }.getOrDefault(this)
}

fun String.formattedPhoneNumberAsYouType(): String {
    if (isEmpty()) return this
    val formatter = phoneNumberUtil.getAsYouTypeFormatter("ZZ")
    return fold("") { _, character -> formatter.inputDigit(character) }
}

fun String.asPhoneNumberInput(): String = buildString {
    this@asPhoneNumberInput.trimStart().forEach { character ->
        when {
            character.isDigit() -> append(character)
            character == '+' && isEmpty() -> append(character)
        }
    }
}.take(16)
