package com.familylibrary.app.data.cover

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnChecksumTest {

    @Test
    fun validIsbn13() {
        assertTrue(CoverService.isValidIsbn("9780306406157"))
    }

    @Test
    fun validIsbn10() {
        assertTrue(CoverService.isValidIsbn("0306406152"))
    }

    @Test
    fun invalidChecksum_rejected() {
        assertFalse(CoverService.isValidIsbn("9780123456789"))
    }

    @Test
    fun tooShort_rejected() {
        assertFalse(CoverService.isValidIsbn("12345"))
    }
}
