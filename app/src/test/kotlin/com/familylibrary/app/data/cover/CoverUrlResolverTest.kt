package com.familylibrary.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverUrlResolverTest {

    @Test
    fun openLibraryCoverUrls_extractsLargeMediumSmall() {
        val json = """
            {
              "ISBN:9787020002207": {
                "title": "Hong lou meng",
                "cover": {
                  "small": "https://covers.openlibrary.org/b/id/14344059-S.jpg",
                  "medium": "https://covers.openlibrary.org/b/id/14344059-M.jpg",
                  "large": "https://covers.openlibrary.org/b/id/14344059-L.jpg"
                }
              }
            }
        """.trimIndent()

        val urls = CoverUrlResolver.openLibraryCoverUrls(json, "9787020002207")
        assertEquals(3, urls.size)
        assertTrue(urls[0].contains("14344059-L.jpg"))
        assertTrue(urls[1].contains("14344059-M.jpg"))
    }

    @Test
    fun openLibraryIsbn10_fromIdentifiers() {
        val json = """{"ISBN:9787020002207":{"identifiers":{"isbn_10":["702000220X"]}}}"""
        assertEquals("702000220X", CoverUrlResolver.openLibraryIsbn10(json))
    }

    @Test
    fun googleBooksCoverUrls_readsThumbnails() {
        val json = """
            {
              "totalItems": 1,
              "items": [{
                "volumeInfo": {
                  "title": "Test",
                  "imageLinks": {
                    "smallThumbnail": "http://books.google.com/s.jpg",
                    "thumbnail": "http://books.google.com/m.jpg"
                  }
                }
              }]
            }
        """.trimIndent()
        val urls = CoverUrlResolver.googleBooksCoverUrls(json)
        assertTrue(urls.any { it.contains("books.google.com") })
    }

    @Test
    fun longitoodCoverUrl_parsesUrl() {
        val json = """{"url":"https://example.com/cover.jpg"}"""
        assertEquals("https://example.com/cover.jpg", CoverUrlResolver.longitoodCoverUrl(json))
    }

    @Test
    fun longitoodCoverUrl_errorReturnsNull() {
        assertNull(CoverUrlResolver.longitoodCoverUrl("""{"error":"not found"}"""))
    }
}
