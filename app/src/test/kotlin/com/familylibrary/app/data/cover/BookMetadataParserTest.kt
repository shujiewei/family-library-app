package com.familylibrary.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookMetadataParserTest {

    @Test
    fun parseOpenLibrary_extractsTitleAndAuthor() {
        val json = """
            {
              "ISBN:9780140328721": {
                "title": "Fantastic Mr Fox",
                "authors": [{"name": "Roald Dahl"}],
                "publishers": [{"name": "Puffin"}],
                "number_of_pages": 96
              }
            }
        """.trimIndent()

        val parsed = BookMetadataParser.parseOpenLibrary(json, "9780140328721")
        requireNotNull(parsed)
        assertEquals("Fantastic Mr Fox", parsed.title)
        assertEquals("Roald Dahl", parsed.author)
        assertEquals("Puffin", parsed.publisher)
        assertEquals(96, parsed.pageCount)
    }

    @Test
    fun parseOpenLibrary_missingKey_returnsNull() {
        assertNull(BookMetadataParser.parseOpenLibrary("{}", "9780140328721"))
    }

    @Test
    fun parseGoogleBooks_extractsFields() {
        val json = """
            {
              "totalItems": 1,
              "items": [{
                "volumeInfo": {
                  "title": "Test Book",
                  "authors": ["Alice", "Bob"],
                  "publisher": "Test Pub",
                  "pageCount": 120,
                  "description": "A sample"
                }
              }]
            }
        """.trimIndent()

        val parsed = BookMetadataParser.parseGoogleBooks(json)
        requireNotNull(parsed)
        assertEquals("Test Book", parsed.title)
        assertEquals("Alice、Bob", parsed.author)
        assertEquals("Test Pub", parsed.publisher)
        assertEquals(120, parsed.pageCount)
        assertEquals("A sample", parsed.description)
    }

    @Test
    fun parseGoogleBooks_zeroItems_returnsNull() {
        val json = """{"totalItems": 0, "items": []}"""
        assertNull(BookMetadataParser.parseGoogleBooks(json))
    }
}
