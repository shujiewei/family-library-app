package com.familylibrary.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTitleRulesTest {

    @Test
    fun blankTitle_isPlaceholder() {
        assertTrue(isPlaceholderTitle(""))
        assertTrue(isPlaceholderTitle("  "))
    }

    @Test
    fun isbnPlaceholder_isPlaceholder() {
        assertTrue(isPlaceholderTitle("ISBN 9780123456789", "9780123456789"))
        assertTrue(isPlaceholderTitle("9780123456789", "9780123456789"))
    }

    @Test
    fun realTitle_isValid() {
        assertFalse(isPlaceholderTitle("三体", "9780123456789"))
        assertTrue(hasValidTitle("三体", "9780123456789"))
    }

    @Test
    fun titleWithoutIsbn_validWhenNotBlank() {
        assertTrue(hasValidTitle("手工录入的书"))
        assertFalse(hasValidTitle(""))
    }
}
