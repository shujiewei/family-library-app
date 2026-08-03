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
    fun isValidIsbn_accepts10And13() {
        assertTrue(CoverService.isValidIsbn("0306406157"))
        assertTrue(CoverService.isValidIsbn("9780306406157"))
        assertFalse(CoverService.isValidIsbn("123"))
    }
}
