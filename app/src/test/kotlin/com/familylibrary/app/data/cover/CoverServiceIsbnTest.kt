package com.familylibrary.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverServiceIsbnTest {

    @Test
    fun normalizeIsbn_stripsHyphens() {
        assertEquals("9780123456789", CoverService.normalizeIsbn("978-0-123456-78-9"))
    }

    @Test
    fun normalizeIsbn_uppercasesX() {
        assertEquals("X", CoverService.normalizeIsbn("x"))
    }
}
