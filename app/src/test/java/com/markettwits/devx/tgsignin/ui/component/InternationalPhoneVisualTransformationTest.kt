package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class InternationalPhoneVisualTransformationTest {
    @Test
    fun `formats NANP input and keeps every original cursor offset reversible`() {
        val original = "+15551234567"
        val transformed = InternationalPhoneVisualTransformation.filter(AnnotatedString(original))

        assertEquals("+1 555-123-4567", transformed.text.text)
        for (offset in 0..original.length) {
            val visualOffset = transformed.offsetMapping.originalToTransformed(offset)
            assertEquals(offset, transformed.offsetMapping.transformedToOriginal(visualOffset))
        }
    }
}
