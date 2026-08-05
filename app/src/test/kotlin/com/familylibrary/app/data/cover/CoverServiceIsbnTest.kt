package com.familylibrary.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun plausibleIsbn_acceptsLength10or13() {
        assertTrue(CoverService.isPlausibleIsbn("9780123456789"))
        assertTrue(CoverService.isPlausibleIsbn("0123456789"))
        assertFalse(CoverService.isPlausibleIsbn("12345"))
    }

    @Test
    fun validIsbn_stricterThanPlausible() {
        assertTrue(CoverService.isValidIsbn("9780306406157"))
        assertTrue(CoverService.isPlausibleIsbn("9780123456789"))
        assertFalse(CoverService.isValidIsbn("9780123456789"))
    }
}
