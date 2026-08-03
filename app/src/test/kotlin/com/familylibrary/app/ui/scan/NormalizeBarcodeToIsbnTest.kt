package com.familylibrary.app.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizeBarcodeToIsbnTest {

    @Test
    fun ean13() {
        assertEquals("9780123456789", normalizeBarcodeToIsbn("9780123456789"))
    }

    @Test
    fun isbn10() {
        assertEquals("0123456789", normalizeBarcodeToIsbn("0123456789"))
    }

    @Test
    fun tooShort_returnsNull() {
        assertNull(normalizeBarcodeToIsbn("12345"))
    }

    @Test
    fun null_returnsNull() {
        assertNull(normalizeBarcodeToIsbn(null))
    }
}
