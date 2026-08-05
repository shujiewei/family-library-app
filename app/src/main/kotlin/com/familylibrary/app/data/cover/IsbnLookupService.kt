package com.familylibrary.app.data.cover

import android.util.Log
import com.familylibrary.app.data.entity.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 通过 ISBN 查询书目信息（Open Library 优先，Google Books 备用） */
class IsbnLookupService : IsbnTitleLookup {

    data class BookInfo(
        val isbn: String,
        val title: String,
        val author: String,
        val publisher: String,
        val pageCount: Int,
        val description: String,
    )

    data class SupplementalInfo(
        val author: String,
        val publisher: String,
        val pageCount: Int,
        val description: String,
    )

    /** 同步拉取书名；添加图书前必须成功 */
    override suspend fun lookupTitle(isbn: String): String? = withContext(Dispatchers.IO) {
        lookupParsed(isbn)?.title
    }

    suspend fun lookup(isbn: String): BookInfo? = withContext(Dispatchers.IO) {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (!CoverService.isPlausibleIsbn(normalized)) return@withContext null
        lookupParsed(normalized)?.toBookInfo(normalized)
    }

    /** 后台补全：仅作者、出版社等，不含书名 */
    suspend fun lookupSupplemental(isbn: String): SupplementalInfo? = withContext(Dispatchers.IO) {
        val parsed = lookupParsed(isbn) ?: return@withContext null
        SupplementalInfo(
            author = parsed.author,
            publisher = parsed.publisher,
            pageCount = parsed.pageCount,
            description = parsed.description,
        )
    }

    fun toBook(info: BookInfo): Book = Book(
        title = info.title,
        author = info.author,
        publisher = info.publisher,
        isbn = info.isbn,
        pageCount = info.pageCount,
        description = info.description,
    )

    private fun lookupParsed(isbn: String): BookMetadataParser.ParsedBook? {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (!CoverService.isPlausibleIsbn(normalized)) return null
        fetchOpenLibraryJson(normalized)?.let { json ->
            BookMetadataParser.parseOpenLibrary(json, normalized)?.let { return it }
        }
        fetchGoogleBooksJson(normalized)?.let { json ->
            BookMetadataParser.parseGoogleBooks(json)?.let { return it }
        }
        return null
    }

    private fun BookMetadataParser.ParsedBook.toBookInfo(isbn: String) = BookInfo(
        isbn = isbn,
        title = title,
        author = author,
        publisher = publisher,
        pageCount = pageCount,
        description = description,
    )

    private fun fetchOpenLibraryJson(isbn: String): String? {
        val normalized = CoverService.normalizeIsbn(isbn)
        val url =
            "https://openlibrary.org/api/books?bibkeys=ISBN:$normalized&jscmd=data&format=json"
        return try {
            httpGet(url)?.takeIf { it.contains("ISBN:$normalized") }
        } catch (t: Throwable) {
            Log.w(TAG, "openlibrary lookup failed isbn=$isbn", t)
            null
        }
    }

    private fun fetchGoogleBooksJson(isbn: String): String? {
        val normalized = CoverService.normalizeIsbn(isbn)
        return try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$normalized&maxResults=1"
            httpGet(url)?.takeIf { json ->
                json.contains("\"totalItems\"") && !json.contains("\"totalItems\": 0") &&
                    !json.contains("\"totalItems\":0")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "google books lookup failed isbn=$isbn", t)
            null
        }
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FamilyLibrary/1.0")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "IsbnLookup"
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 8_000
    }
}
