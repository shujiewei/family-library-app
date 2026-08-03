package com.familylibrary.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ParseBatchLineTest {

    @Test
    fun titleOnly() {
        val entry = parseBatchLine("三体")
        assertNotNull(entry)
        assertEquals("三体", entry!!.title)
        assertEquals("", entry.isbn)
    }

    @Test
    fun titleAndIsbn() {
        val entry = parseBatchLine("三体,9780306406157")
        assertEquals("三体", entry!!.title)
        assertEquals("9780306406157", entry.isbn)
    }

    @Test
    fun isbnOnly() {
        val entry = parseBatchLine("9780306406157")
        assertNotNull(entry)
        assertEquals("", entry!!.title)
        assertEquals("9780306406157", entry.isbn)
    }

    @Test
    fun blankLine_returnsNull() {
        assertNull(parseBatchLine("   "))
    }
}
