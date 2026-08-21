package com.familylibrary.app.data.cover

import android.util.Log
import com.familylibrary.app.data.entity.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 通过 ISBN 查询书目信息（豆瓣 / Open Library 优先，Google Books 备用） */
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
        lookup(isbn)?.title
    }

    suspend fun lookup(isbn: String): BookInfo? = withContext(Dispatchers.IO) {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (!CoverService.isPlausibleIsbn(normalized)) return@withContext null
        withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            lookupParsed(normalized)?.toBookInfo(normalized)
        }
    }

    /** 后台补全：仅作者、出版社等，不含书名 */
    suspend fun lookupSupplemental(isbn: String): SupplementalInfo? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            lookupParsed(CoverService.normalizeIsbn(isbn))?.let {
                SupplementalInfo(
                    author = it.author,
                    publisher = it.publisher,
                    pageCount = it.pageCount,
                    description = it.description,
                )
            }
        }
    }

    fun toBook(info: BookInfo): Book = Book(
        title = info.title,
        author = info.author,
        publisher = info.publisher,
        isbn = info.isbn,
        pageCount = info.pageCount,
        description = info.description,
    )

    private suspend fun lookupParsed(normalized: String): BookMetadataParser.ParsedBook? = coroutineScope {
        // 国内中文书：豆瓣最可靠
        val doubanDeferred = async { fetchDoubanHtml(normalized) }
        val olSearchDeferred = async { fetchOpenLibrarySearchJson(normalized) }
        val googleDeferred = async { fetchGoogleBooksJson(normalized) }

        doubanDeferred.await()?.let { html ->
            BookMetadataParser.parseDouban(html, normalized)?.let { return@coroutineScope it }
        }

        olSearchDeferred.await()?.let { json ->
            BookMetadataParser.parseOpenLibrarySearch(json)?.let { return@coroutineScope it }
        }

        val olJson = fetchOpenLibraryJson(normalized)
        olJson?.let { json ->
            BookMetadataParser.parseOpenLibrary(json, normalized)?.let { return@coroutineScope it }
            CoverUrlResolver.openLibraryIsbn10(json)?.let { isbn10 ->
                fetchOpenLibraryJson(isbn10)?.let { json10 ->
                    BookMetadataParser.parseOpenLibrary(json10, isbn10)?.let { return@coroutineScope it }
                }
            }
        }

        googleDeferred.await()?.let { BookMetadataParser.parseGoogleBooks(it) }
    }

    private fun BookMetadataParser.ParsedBook.toBookInfo(isbn: String) = BookInfo(
        isbn = isbn,
        title = title,
        author = author,
        publisher = publisher,
        pageCount = pageCount,
        description = description,
    )

    private fun fetchDoubanHtml(isbn: String): String? {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (normalized.isBlank()) return null
        val url = "https://book.douban.com/isbn/$normalized/"
        return try {
            IsbnHttp.getString(
                url = url,
                accept = "text/html,application/xhtml+xml,*/*",
            )
        } catch (t: Throwable) {
            Log.w(TAG, "douban lookup failed isbn=$isbn", t)
            null
        }
    }

    private fun fetchOpenLibrarySearchJson(isbn: String): String? {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (normalized.isBlank()) return null
        val url = "https://openlibrary.org/search.json?isbn=$normalized&limit=1"
        return try {
            IsbnHttp.getString(url)?.takeIf { it.contains("\"docs\"") && !it.contains("\"numFound\":0") }
        } catch (t: Throwable) {
            Log.w(TAG, "openlibrary search failed isbn=$isbn", t)
            null
        }
    }

    private fun fetchOpenLibraryJson(isbn: String): String? {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (normalized.isBlank()) return null
        val url =
            "https://openlibrary.org/api/books?bibkeys=ISBN:$normalized&jscmd=data&format=json"
        return try {
            IsbnHttp.getString(url)?.takeIf { it.contains("ISBN:$normalized") }
        } catch (t: Throwable) {
            Log.w(TAG, "openlibrary lookup failed isbn=$isbn", t)
            null
        }
    }

    private fun fetchGoogleBooksJson(isbn: String): String? {
        val normalized = CoverService.normalizeIsbn(isbn)
        return try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$normalized&maxResults=1"
            IsbnHttp.getString(url)?.takeIf { json ->
                json.contains("\"totalItems\"") && !json.contains("\"totalItems\": 0") &&
                    !json.contains("\"totalItems\":0")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "google books lookup failed isbn=$isbn", t)
            null
        }
    }

    companion object {
        private const val TAG = "IsbnLookup"
        private const val LOOKUP_TIMEOUT_MS = 12_000L
    }
}
