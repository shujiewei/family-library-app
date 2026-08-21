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

    @Test
    fun parseOpenLibrarySearch_extractsTitleAndAuthor() {
        val json = """
            {
              "numFound": 1,
              "docs": [{
                "title": "Hong lou meng",
                "author_name": ["Cao xue qin"],
                "number_of_pages_median": 771
              }]
            }
        """.trimIndent()
        val parsed = BookMetadataParser.parseOpenLibrarySearch(json)
        requireNotNull(parsed)
        assertEquals("Hong lou meng", parsed.title)
        assertEquals("Cao xue qin", parsed.author)
        assertEquals(771, parsed.pageCount)
    }

    @Test
    fun parseDouban_extractsChineseFields() {
        val html = """
            <html>
            <head>
              <meta property="og:title" content="红楼梦（下） (豆瓣)">
              <meta property="og:image" content="https://img3.doubanio.com/view/subject/l/public/s123.jpg">
            </head>
            <body>
              <h1><span property="v:itemreviewed">红楼梦（下）</span></h1>
              <div id="info">
                <span class="pl"> 作者</span>: <a href="/search/曹雪芹">曹雪芹</a><br/>
                <span class="pl">出版社</span>: <a href="/press/1">人民文学出版社</a><br/>
                <span class="pl">页数</span>: 771<br/>
                <span class="pl">ISBN</span>: 9787020002207<br/>
              </div>
            </body>
            </html>
        """.trimIndent()

        val parsed = BookMetadataParser.parseDouban(html, "9787020002207")
        requireNotNull(parsed)
        assertEquals("红楼梦（下）", parsed.title)
        assertEquals("曹雪芹", parsed.author)
        assertEquals("人民文学出版社", parsed.publisher)
        assertEquals(771, parsed.pageCount)
        assertEquals(
            "https://img3.doubanio.com/view/subject/l/public/s123.jpg",
            BookMetadataParser.doubanCoverUrl(html),
        )
    }

    @Test
    fun parseDouban_notFound_returnsNull() {
        val html = """<html><body>没有找到关于 9787020002207 的书目信息</body></html>"""
        assertNull(BookMetadataParser.parseDouban(html, "9787020002207"))
    }
}
