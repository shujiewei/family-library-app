package com.familylibrary.app.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * 封面服务：ISBN 网络拉取 + 用户自定义（拍照/相册）统一生成缩略图缓存。
 *
 * [Book.coverUri] 存储相对路径 `covers/thumb_{id}.jpg`
 * [Book.coverSource] 区分 isbn / custom，自定义封面不会被 ISBN 更新覆盖
 */
class CoverService(private val context: Context) {

    private val coversDir: File
        get() = File(context.filesDir, COVERS_DIR).also { it.mkdirs() }

    /** ISBN 网络拉取结果 */
    sealed class FetchResult {
        data class Success(val relativePath: String) : FetchResult()
        data object NotFound : FetchResult()
        data class Error(val message: String) : FetchResult()
    }

    suspend fun fetchAndSaveThumbnail(bookId: Long, isbn: String): FetchResult = withContext(Dispatchers.IO) {
        val normalized = normalizeIsbn(isbn)
        if (normalized.isBlank()) return@withContext FetchResult.NotFound

        try {
            val imageBytes = downloadCoverBytes(normalized)
                ?: return@withContext FetchResult.NotFound
            saveThumbnailBytes(bookId, imageBytes)?.let { FetchResult.Success(it) }
                ?: FetchResult.Error("缩略图生成失败")
        } catch (t: Throwable) {
            Log.w(TAG, "fetch cover failed isbn=$isbn bookId=$bookId", t)
            FetchResult.Error(t.message ?: "网络错误")
        }
    }

    suspend fun saveCustomFromUri(bookId: Long, uri: Uri): FetchResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext FetchResult.Error("无法读取图片")
            if (!isValidImageBytes(bytes)) return@withContext FetchResult.Error("图片过小或无效")
            saveThumbnailBytes(bookId, bytes)?.let { FetchResult.Success(it) }
                ?: FetchResult.Error("缩略图生成失败")
        } catch (t: Throwable) {
            Log.w(TAG, "save custom cover failed bookId=$bookId", t)
            FetchResult.Error(t.message ?: "保存失败")
        }
    }

    suspend fun saveCustomFromFile(bookId: Long, file: File): FetchResult = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext FetchResult.Error("照片文件不存在")
            val bytes = file.readBytes()
            if (!isValidImageBytes(bytes)) return@withContext FetchResult.Error("图片无效")
            saveThumbnailBytes(bookId, bytes)?.let { FetchResult.Success(it) }
                ?: FetchResult.Error("缩略图生成失败")
        } catch (t: Throwable) {
            FetchResult.Error(t.message ?: "保存失败")
        }
    }

    private fun saveThumbnailBytes(bookId: Long, imageBytes: ByteArray): String? {
        val thumbBytes = createThumbnail(imageBytes, THUMB_MAX_WIDTH) ?: return null
        val relativePath = "$COVERS_DIR/thumb_$bookId.jpg"
        File(context.filesDir, relativePath).writeBytes(thumbBytes)
        return relativePath
    }

    fun toAbsolutePath(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        val file = File(context.filesDir, relativePath)
        return file.takeIf { it.exists() && it.isFile }
    }

    fun deleteCover(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        File(context.filesDir, relativePath).delete()
    }

    fun deleteCoverForBook(bookId: Long) {
        File(coversDir, "thumb_$bookId.jpg").delete()
    }

    fun createCameraTempFile(): File {
        val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
        return File(dir, "cover_${System.currentTimeMillis()}.jpg")
    }

    companion object {
        private const val TAG = "CoverService"
        const val COVERS_DIR = "covers"
        private const val THUMB_MAX_WIDTH = 200
        private const val JPEG_QUALITY = 85
        private const val MIN_IMAGE_BYTES = 400
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000

        fun normalizeIsbn(isbn: String): String =
            isbn.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

        /** 10/13 位格式，扫码录入用（不校验 checksum，避免误扫无反馈） */
        fun isPlausibleIsbn(isbn: String): Boolean {
            val n = normalizeIsbn(isbn)
            return n.length == 10 || n.length == 13
        }

        fun isValidIsbn(isbn: String): Boolean {
            val n = normalizeIsbn(isbn)
            return when (n.length) {
                10 -> isValidIsbn10(n)
                13 -> isValidIsbn13(n)
                else -> false
            }
        }

        private fun isValidIsbn10(isbn: String): Boolean {
            if (isbn.length != 10) return false
            var sum = 0
            for (i in 0 until 9) {
                val c = isbn[i]
                if (!c.isDigit()) return false
                sum += (c - '0') * (10 - i)
            }
            val check = isbn[9]
            val checkVal = if (check == 'X') 10 else check.digitToIntOrNull() ?: return false
            return (sum + checkVal) % 11 == 0
        }

        private fun isValidIsbn13(isbn: String): Boolean {
            if (isbn.length != 13 || !isbn.all { it.isDigit() }) return false
            var sum = 0
            for (i in 0 until 12) {
                val digit = isbn[i] - '0'
                sum += digit * if (i % 2 == 0) 1 else 3
            }
            val check = (10 - (sum % 10)) % 10
            return check == (isbn[12] - '0')
        }

        internal fun isValidImageBytes(bytes: ByteArray): Boolean =
            bytes.size >= MIN_IMAGE_BYTES && (isJpeg(bytes) || isPng(bytes))

        private fun isJpeg(bytes: ByteArray): Boolean =
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte()

        private fun isPng(bytes: ByteArray): Boolean =
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
    }

    private fun downloadCoverBytes(isbn: String): ByteArray? {
        val normalized = normalizeIsbn(isbn)

        // 1. book345 国内封面 CDN（中文 ISBN 命中率高）
        for (size in listOf("l", "m", "s")) {
            val url = "https://static.book345.com/covers/$size/$normalized.jpg"
            downloadIfValid(url)?.let { return it }
        }

        // 2. 豆瓣 og:image
        fetchDoubanHtml(normalized)?.let { html ->
            BookMetadataParser.doubanCoverUrl(html)?.let { url ->
                downloadIfValid(url)?.let { return it }
            }
        }

        // 3. longitood（Goodreads 封面代理）
        fetchLongitoodCoverUrl(normalized)?.let { url ->
            downloadIfValid(url)?.let { return it }
        }

        // 4. Open Library API（按 cover id，比 /b/isbn/ 直连可靠，尤其中文 ISBN）
        fetchOpenLibraryBooksJson(normalized)?.let { json ->
            downloadFirstValid(CoverUrlResolver.openLibraryCoverUrls(json, normalized))?.let { return it }
            CoverUrlResolver.openLibraryIsbn10(json)?.let { isbn10 ->
                fetchOpenLibraryBooksJson(isbn10)?.let { json10 ->
                    downloadFirstValid(CoverUrlResolver.openLibraryCoverUrls(json10, isbn10))?.let { return it }
                }
            }
        }

        // 5. Open Library 直连 ISBN（部分旧书仍可用）
        for (size in listOf("L", "M", "S")) {
            val url = "https://covers.openlibrary.org/b/isbn/$normalized-$size.jpg?default=false"
            downloadIfValid(url)?.let { return it }
        }

        // 6. Google Books（国内可能不可用，作备用）
        fetchGoogleBooksJson(normalized)?.let { json ->
            val urls = CoverUrlResolver.googleBooksCoverUrls(json).map { upgradeGoogleImageUrl(it) }
            downloadFirstValid(urls)?.let { return it }
        }

        return null
    }

    private fun downloadFirstValid(urls: List<String>): ByteArray? {
        for (url in urls) {
            downloadIfValid(url)?.let { return it }
        }
        return null
    }

    private fun fetchOpenLibraryBooksJson(isbn: String): String? {
        val normalized = normalizeIsbn(isbn)
        if (normalized.isBlank()) return null
        val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$normalized&jscmd=data&format=json"
        return try {
            IsbnHttp.getString(url)?.takeIf { it.contains("ISBN:$normalized") }
        } catch (t: Throwable) {
            Log.w(TAG, "openlibrary books api failed isbn=$isbn", t)
            null
        }
    }

    private fun fetchDoubanHtml(isbn: String): String? {
        val normalized = normalizeIsbn(isbn)
        if (normalized.isBlank()) return null
        return try {
            IsbnHttp.getString(
                url = "https://book.douban.com/isbn/$normalized/",
                connectTimeoutMs = CONNECT_TIMEOUT_MS,
                readTimeoutMs = READ_TIMEOUT_MS,
                accept = "text/html,application/xhtml+xml,*/*",
            )
        } catch (t: Throwable) {
            Log.w(TAG, "douban cover page failed isbn=$isbn", t)
            null
        }
    }

    private fun fetchLongitoodCoverUrl(isbn: String): String? {
        val normalized = normalizeIsbn(isbn)
        if (normalized.isBlank()) return null
        val url = "https://bookcover.longitood.com/bookcover?isbn=$normalized"
        return try {
            IsbnHttp.getString(
                url = url,
                connectTimeoutMs = CONNECT_TIMEOUT_MS,
                readTimeoutMs = READ_TIMEOUT_MS,
            )?.let { CoverUrlResolver.longitoodCoverUrl(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "longitood cover failed isbn=$isbn", t)
            null
        }
    }

    private fun fetchGoogleBooksJson(isbn: String): String? {
        val apiUrl = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn&maxResults=1"
        return try {
            IsbnHttp.getString(apiUrl)?.takeIf { json ->
                json.contains("\"totalItems\"") && !json.contains("\"totalItems\": 0") &&
                    !json.contains("\"totalItems\":0")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "google books lookup failed", t)
            null
        }
    }

    private fun upgradeGoogleImageUrl(url: String): String =
        url.replace("http://", "https://")
            .replace("zoom=1", "zoom=2")

    private fun downloadIfValid(url: String): ByteArray? {
        return try {
            val bytes = IsbnHttp.getBytes(
                url = url,
                connectTimeoutMs = CONNECT_TIMEOUT_MS,
                readTimeoutMs = READ_TIMEOUT_MS,
            ) ?: return null
            if (!isValidImageBytes(bytes)) null else bytes
        } catch (t: Throwable) {
            Log.w(TAG, "download failed: $url", t)
            null
        }
    }

    private fun createThumbnail(sourceBytes: ByteArray, maxWidth: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = max(1, bounds.outWidth / maxWidth)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        var bitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, decodeOpts)
            ?: return null

        if (bitmap.width > maxWidth) {
            val ratio = maxWidth.toFloat() / bitmap.width
            val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, targetH, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }
}
