package com.markettwits.devx.tgsignin.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test
    fun `normalizes formatted numbers from different countries to E164`() {
        assertEquals("+14155552671", "+1 (415) 555-2671".normalizedInternationalPhoneNumberOrNull())
        assertEquals("+442079460018", "+44 20 7946 0018".normalizedInternationalPhoneNumberOrNull())
        assertEquals("+819012345678", "+81 90-1234-5678".normalizedInternationalPhoneNumberOrNull())
    }

    @Test
    fun `formats stored E164 number for display`() {
        assertEquals("+44 20 7946 0018", "+442079460018".formattedInternationalPhoneNumber())
    }

    @Test
    fun `formats international phone progressively by detected country`() {
        assertEquals("+1 555-123-4567", "+15551234567".formattedPhoneNumberAsYouType())
        assertEquals("+44 20 7946 0018", "+442079460018".formattedPhoneNumberAsYouType())
    }

    @Test
    fun `accepts structurally possible test numbers without claiming they are verified`() {
        assertEquals("+15551234567", "+1 555 123 4567".normalizedInternationalPhoneNumberOrNull())
    }

    @Test
    fun `keeps only canonical input characters when a formatted number is pasted`() {
        assertEquals("+15551234567", "  +1 (555) 123-4567".asPhoneNumberInput())
    }

    @Test
    fun `normalizes Telegram OIDC phone without a leading plus`() {
        assertEquals("+442079460018", "442079460018".normalizedTelegramPhoneNumberOrNull())
    }

    @Test
    fun `optional phone accepts blank and rejects ambiguous or invalid input`() {
        assertTrue("".isValidOptionalInternationalPhoneNumber())
        assertFalse("4155552671".isValidOptionalInternationalPhoneNumber())
        assertFalse("+999123".isValidOptionalInternationalPhoneNumber())
    }
}
