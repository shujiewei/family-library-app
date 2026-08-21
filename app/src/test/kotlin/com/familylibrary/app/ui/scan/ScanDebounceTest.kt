package com.familylibrary.app.ui.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDebounceTest {

    @Test
    fun sameIsbnWithinCooldown_rejected() {
        val debounce = ScanDebounce()
        assertTrue(debounce.shouldAccept("9780306406157", 3_000))
        assertFalse(debounce.shouldAccept("9780306406157", 3_000))
    }

    @Test
    fun differentIsbn_accepted() {
        val debounce = ScanDebounce()
        assertTrue(debounce.shouldAccept("9780306406157", 3_000))
        assertTrue(debounce.shouldAccept("9780140328721", 3_000))
    }
}
