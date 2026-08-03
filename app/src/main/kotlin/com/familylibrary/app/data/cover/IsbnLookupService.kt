package com.familylibrary.app.data.cover

import android.util.Log
import com.familylibrary.app.data.entity.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 通过 ISBN 从 Google Books 查询书目信息 */
class IsbnLookupService {

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
    suspend fun lookupTitle(isbn: String): String? = withContext(Dispatchers.IO) {
        fetchVolumeJson(isbn)?.let { extractField(it, "title") }
    }

    suspend fun lookup(isbn: String): BookInfo? = withContext(Dispatchers.IO) {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (!CoverService.isValidIsbn(normalized)) return@withContext null
        val json = fetchVolumeJson(normalized) ?: return@withContext null
        val title = extractField(json, "title") ?: return@withContext null
        BookInfo(
            isbn = normalized,
            title = title,
            author = extractAuthors(json),
            publisher = extractField(json, "publisher") ?: "",
            pageCount = extractField(json, "pageCount")?.toIntOrNull() ?: 0,
            description = extractField(json, "description") ?: "",
        )
    }

    /** 后台补全：仅作者、出版社等，不含书名 */
    suspend fun lookupSupplemental(isbn: String): SupplementalInfo? = withContext(Dispatchers.IO) {
        val json = fetchVolumeJson(isbn) ?: return@withContext null
        SupplementalInfo(
            author = extractAuthors(json),
            publisher = extractField(json, "publisher") ?: "",
            pageCount = extractField(json, "pageCount")?.toIntOrNull() ?: 0,
            description = extractField(json, "description") ?: "",
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

    private fun fetchVolumeJson(isbn: String): String? {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (!CoverService.isValidIsbn(normalized)) return null
        return try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$normalized&maxResults=1"
            val json = httpGet(url) ?: return null
            if (!json.contains("\"totalItems\"") || json.contains("\"totalItems\": 0")) return null
            json
        } catch (t: Throwable) {
            Log.w(TAG, "fetchVolumeJson failed isbn=$isbn", t)
            null
        }
    }

    private fun extractAuthors(json: String): String {
        val regex = """"authors"\s*:\s*\[(.*?)]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val block = regex.find(json)?.groupValues?.get(1) ?: return ""
        return """"([^"]+)"""".toRegex().findAll(block).map { it.groupValues[1] }.joinToString("、")
    }

    private fun extractField(json: String, field: String): String? {
        val regex = """"$field"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\u0026", "&")
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
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
    }
}
