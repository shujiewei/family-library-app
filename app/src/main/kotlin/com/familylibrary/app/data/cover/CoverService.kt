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
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
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
            if (bytes.size < MIN_IMAGE_BYTES) return@withContext FetchResult.Error("图片过小或无效")
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
        private const val MIN_IMAGE_BYTES = 800
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000

        fun normalizeIsbn(isbn: String): String =
            isbn.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

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
            return sum % 11 == checkVal
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
    }

    private suspend fun downloadCoverBytes(isbn: String): ByteArray? {
        val openLibraryUrl = "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg?default=false"
        downloadIfValid(openLibraryUrl)?.let { return it }

        val googleUrl = fetchGoogleBooksCoverUrl(isbn) ?: return null
        return downloadIfValid(upgradeGoogleImageUrl(googleUrl))
    }

    private fun fetchGoogleBooksCoverUrl(isbn: String): String? {
        val apiUrl = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn&maxResults=1"
        return try {
            val json = httpGetString(apiUrl) ?: return null
            extractJsonField(json, "thumbnail")
                ?: extractJsonField(json, "smallThumbnail")
        } catch (t: Throwable) {
            Log.w(TAG, "google books lookup failed", t)
            null
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val regex = """"$field"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")
            ?.replace("\\/", "/")
    }

    private fun upgradeGoogleImageUrl(url: String): String =
        url.replace("http://", "https://")
            .replace("zoom=1", "zoom=2")

    private fun downloadIfValid(url: String): ByteArray? {
        return try {
            val bytes = httpGetBytes(url) ?: return null
            if (bytes.size < MIN_IMAGE_BYTES) null else bytes
        } catch (t: Throwable) {
            Log.w(TAG, "download failed: $url", t)
            null
        }
    }

    private fun httpGetBytes(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FamilyLibrary/1.0")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGetString(url: String): String? =
        httpGetBytes(url)?.toString(Charsets.UTF_8)

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
