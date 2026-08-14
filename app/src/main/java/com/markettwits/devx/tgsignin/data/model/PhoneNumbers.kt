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
    }.getOrNull()?.takeIf(phoneNumberUtil::isValidNumber)?.let {
        phoneNumberUtil.format(it, PhoneNumberUtil.PhoneNumberFormat.E164)
    }
}

fun String.isValidOptionalInternationalPhoneNumber(): Boolean =
    isBlank() || normalizedInternationalPhoneNumberOrNull() != null

fun String.formattedInternationalPhoneNumber(): String {
    val normalized = normalizedInternationalPhoneNumberOrNull() ?: return this
    return runCatching {
        phoneNumberUtil.format(
            phoneNumberUtil.parse(normalized, null),
            PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
        )
    }.getOrDefault(this)
}

fun String.asPhoneNumberInput(): String = trimStart().filterIndexed { index, character ->
    character.isDigit() || character in " ()-." || (character == '+' && index == 0)
}.take(40)
