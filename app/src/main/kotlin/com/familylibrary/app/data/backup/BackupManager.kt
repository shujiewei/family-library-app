package com.familylibrary.app.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.familylibrary.app.BuildConfig
import com.familylibrary.app.data.db.AppDatabase
import com.familylibrary.app.data.entity.AppSettings
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.Bookshelf
import com.familylibrary.app.data.entity.FamilyMember
import com.familylibrary.app.data.entity.ReadingRecord
import com.familylibrary.app.data.entity.ShelfRow
import com.familylibrary.app.data.entity.WishlistItem
import com.familylibrary.app.data.cover.CoverService
import com.familylibrary.app.util.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "BackupManager"

object BackupManager {
    private const val FILE_MANIFEST = "manifest.json"
    private const val FILE_DATA = "data.json"

    suspend fun exportTo(ctx: Context, uri: Uri, db: AppDatabase): ExportResult {
        return try {
            val manifest = buildManifest()
            val data = buildDataJson(db)
            ctx.contentResolver.openOutputStream(uri, "w")?.use { os ->
                ZipOutputStream(os).use { zos ->
                    putEntry(zos, FILE_MANIFEST, manifest)
                    putEntry(zos, FILE_DATA, data)
                    exportCoverFiles(ctx, zos)
                }
            } ?: return ExportResult.Failure("无法写入所选位置")
            ExportResult.Success
        } catch (t: Throwable) {
            Log.w(TAG, "export failed", t)
            ExportResult.Failure(t.message ?: "未知错误")
        }
    }

    suspend fun importFrom(ctx: Context, uri: Uri, db: AppDatabase): ImportResult {
        return try {
            var manifestJson: String? = null
            var dataJson: String? = null
            val coverEntries = mutableMapOf<String, ByteArray>()
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var e: ZipEntry? = zis.nextEntry
                    while (e != null) {
                        val name = e.name
                        when {
                            name == FILE_MANIFEST -> manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                            name == FILE_DATA -> dataJson = zis.readBytes().toString(Charsets.UTF_8)
                            name.startsWith("${CoverService.COVERS_DIR}/") && !e.isDirectory -> {
                                if (isSafeCoverEntry(name)) {
                                    coverEntries[name] = zis.readBytes()
                                }
                            }
                        }
                        zis.closeEntry()
                        e = zis.nextEntry
                    }
                }
            } ?: return ImportResult.Failure("无法读取该文件")

            if (manifestJson == null || dataJson == null) {
                return ImportResult.Failure("ZIP 结构无效")
            }
            val mani = Json.parseObject(manifestJson!!)
            val dbVer = (mani["db_version"] as? Number)?.toInt() ?: -1
            if (dbVer > AppDatabase.SCHEMA_VERSION) {
                return ImportResult.Failure("数据库版本过新：备份=$dbVer，当前=${AppDatabase.SCHEMA_VERSION}。请先升级 App。")
            }
            clearCoversDir(ctx)
            applyData(db, Json.parseObject(dataJson!!))
            coverEntries.forEach { (name, bytes) ->
                importCoverFile(ctx, name, bytes)
            }
            ImportResult.Success
        } catch (t: Throwable) {
            Log.w(TAG, "import failed", t)
            ImportResult.Failure(t.message ?: "未知错误")
        }
    }

    private fun buildManifest(): String = buildString {
        append("{")
        append("\"app_version\":${Json.string(BuildConfig.VERSION_NAME)},")
        append("\"db_version\":${AppDatabase.SCHEMA_VERSION},")
        append("\"exported_at\":${System.currentTimeMillis()}")
        append("}")
    }

    private suspend fun buildDataJson(db: AppDatabase): String {
        val bookshelves = db.bookshelfDao().getAll()
        val rows = db.shelfRowDao().getAll()
        val books = db.bookDao().getAll()
        val members = db.familyMemberDao().getAll()
        val records = db.readingRecordDao().getAll()
        val wishlist = db.wishlistDao().getAll()
        val settings = db.appSettingsDao().get()

        return buildString {
            append("{")
            append("\"bookshelf\":").append(Json.array(bookshelves) { bookshelfJson(it) }).append(",")
            append("\"shelf_row\":").append(Json.array(rows) { rowJson(it) }).append(",")
            append("\"book\":").append(Json.array(books) { bookJson(it) }).append(",")
            append("\"family_member\":").append(Json.array(members) { memberJson(it) }).append(",")
            append("\"reading_record\":").append(Json.array(records) { recordJson(it) }).append(",")
            append("\"wishlist_item\":").append(Json.array(wishlist) { wishlistJson(it) }).append(",")
            append("\"app_settings\":").append(if (settings == null) "null" else settingsJson(settings))
            append("}")
        }
    }

    private fun bookshelfJson(b: Bookshelf) = buildString {
        append("{\"id\":${b.id},\"name\":${Json.string(b.name)},\"sortOrder\":${b.sortOrder},\"createdAt\":${b.createdAt}}")
    }

    private fun rowJson(r: ShelfRow) = buildString {
        append("{\"id\":${r.id},\"bookshelfId\":${r.bookshelfId},\"name\":${Json.string(r.name)},\"sortOrder\":${r.sortOrder}}")
    }

    private fun bookJson(b: Book) = buildString {
        append("{")
        append("\"id\":${b.id},")
        append("\"title\":${Json.string(b.title)},")
        append("\"author\":${Json.string(b.author)},")
        append("\"publisher\":${Json.string(b.publisher)},")
        append("\"pageCount\":${b.pageCount},")
        append("\"wordCount\":${b.wordCount},")
        append("\"description\":${Json.string(b.description)},")
        append("\"isbn\":${Json.string(b.isbn)},")
        append("\"coverUri\":${Json.stringOrNull(b.coverUri)},")
        append("\"coverSource\":${Json.string(b.coverSource)},")
        append("\"coverStatus\":${Json.string(b.coverStatus)},")
        append("\"series\":${Json.string(b.series)},")
        append("\"recommendedAge\":${Json.string(b.recommendedAge)},")
        append("\"lexileLevel\":${Json.string(b.lexileLevel)},")
        append("\"category\":${Json.string(b.category)},")
        append("\"isEnglish\":${b.isEnglish},")
        append("\"shelfRowId\":${b.shelfRowId ?: "null"},")
        append("\"positionInRow\":${b.positionInRow},")
        append("\"createdAt\":${b.createdAt},")
        append("\"updatedAt\":${b.updatedAt}")
        append("}")
    }

    private fun memberJson(m: FamilyMember) = buildString {
        append("{\"id\":${m.id},\"name\":${Json.string(m.name)},\"colorIndex\":${m.colorIndex},\"createdAt\":${m.createdAt}}")
    }

    private fun recordJson(r: ReadingRecord) = buildString {
        append("{")
        append("\"id\":${r.id},\"memberId\":${r.memberId},\"bookId\":${r.bookId},")
        append("\"startDate\":${Json.stringOrNull(r.startDate)},")
        append("\"finishDate\":${Json.stringOrNull(r.finishDate)},")
        append("\"notes\":${Json.string(r.notes)},\"createdAt\":${r.createdAt}")
        append("}")
    }

    private fun wishlistJson(w: WishlistItem) = buildString {
        append("{")
        append("\"id\":${w.id},\"title\":${Json.string(w.title)},\"author\":${Json.string(w.author)},")
        append("\"isbn\":${Json.string(w.isbn)},\"note\":${Json.string(w.note)},\"priority\":${w.priority},\"createdAt\":${w.createdAt}")
        append("}")
    }

    private fun settingsJson(s: AppSettings) = buildString {
        append("{")
        append("\"id\":${s.id},\"adminPinHash\":${Json.string(s.adminPinHash)},")
        append("\"adminPinSalt\":${Json.string(s.adminPinSalt)},\"createdAt\":${s.createdAt}")
        append("}")
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun applyData(db: AppDatabase, data: Map<String, Any?>) {
        val bookshelves = (data["bookshelf"] as List<Map<String, Any?>>).map { bookshelfFrom(it) }
        val rows = (data["shelf_row"] as List<Map<String, Any?>>).map { rowFrom(it) }
        val rowIds = rows.map { it.id }.toSet()
        val books = (data["book"] as List<Map<String, Any?>>).map { bookFrom(it) }
            .map { validateBookShelfRef(it, rowIds) }
        val members = (data["family_member"] as List<Map<String, Any?>>).map { memberFrom(it) }
        val records = (data["reading_record"] as List<Map<String, Any?>>).map { recordFrom(it) }
        val wishlist = (data["wishlist_item"] as List<Map<String, Any?>>).map { wishlistFrom(it) }
        val settings = (data["app_settings"] as? Map<String, Any?>)?.let { settingsFrom(it) }

        db.withTransaction {
            settings?.let { db.appSettingsDao().upsert(it) }
            db.bookshelfDao().bulkReplace(bookshelves)
            db.shelfRowDao().bulkReplace(rows)
            db.bookDao().bulkReplace(books)
            db.familyMemberDao().bulkReplace(members)
            db.readingRecordDao().bulkReplace(records)
            db.wishlistDao().bulkReplace(wishlist)
        }
    }

    private fun bookshelfFrom(m: Map<String, Any?>) = Bookshelf(
        id = (m["id"] as Number).toLong(),
        name = m["name"] as String,
        sortOrder = (m["sortOrder"] as Number).toInt(),
        createdAt = (m["createdAt"] as Number).toLong(),
    )

    private fun rowFrom(m: Map<String, Any?>) = ShelfRow(
        id = (m["id"] as Number).toLong(),
        bookshelfId = (m["bookshelfId"] as Number).toLong(),
        name = m["name"] as String,
        sortOrder = (m["sortOrder"] as Number).toInt(),
    )

    private fun bookFrom(m: Map<String, Any?>) = Book(
        id = (m["id"] as Number).toLong(),
        title = m["title"] as String,
        author = m["author"] as String,
        publisher = m["publisher"] as String,
        pageCount = (m["pageCount"] as Number).toInt(),
        wordCount = (m["wordCount"] as Number).toInt(),
        description = m["description"] as String,
        isbn = m["isbn"] as? String ?: "",
        coverUri = m["coverUri"] as String?,
        coverSource = m["coverSource"] as? String ?: "none",
        coverStatus = m["coverStatus"] as? String ?: "none",
        series = m["series"] as String,
        recommendedAge = m["recommendedAge"] as String,
        lexileLevel = m["lexileLevel"] as String,
        category = m["category"] as String,
        isEnglish = m["isEnglish"] as Boolean,
        shelfRowId = (m["shelfRowId"] as Number?)?.toLong(),
        positionInRow = (m["positionInRow"] as Number).toInt(),
        createdAt = (m["createdAt"] as Number).toLong(),
        updatedAt = (m["updatedAt"] as Number).toLong(),
    )

    private fun memberFrom(m: Map<String, Any?>) = FamilyMember(
        id = (m["id"] as Number).toLong(),
        name = m["name"] as String,
        colorIndex = (m["colorIndex"] as Number).toInt(),
        createdAt = (m["createdAt"] as Number).toLong(),
    )

    private fun recordFrom(m: Map<String, Any?>) = ReadingRecord(
        id = (m["id"] as Number).toLong(),
        memberId = (m["memberId"] as Number).toLong(),
        bookId = (m["bookId"] as Number).toLong(),
        startDate = m["startDate"] as String?,
        finishDate = m["finishDate"] as String?,
        notes = m["notes"] as String,
        createdAt = (m["createdAt"] as Number).toLong(),
    )

    private fun wishlistFrom(m: Map<String, Any?>) = WishlistItem(
        id = (m["id"] as Number).toLong(),
        title = m["title"] as String,
        author = m["author"] as String,
        isbn = m["isbn"] as? String ?: "",
        note = m["note"] as String,
        priority = (m["priority"] as Number).toInt(),
        createdAt = (m["createdAt"] as Number).toLong(),
    )

    private fun settingsFrom(m: Map<String, Any?>) = AppSettings(
        id = AppSettings.SINGLETON_ID,
        adminPinHash = m["adminPinHash"] as String,
        adminPinSalt = m["adminPinSalt"] as String,
        createdAt = (m["createdAt"] as Number).toLong(),
    )

    private fun putEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun putBinaryEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun exportCoverFiles(ctx: Context, zos: ZipOutputStream) {
        val coversDir = File(ctx.filesDir, CoverService.COVERS_DIR)
        if (!coversDir.exists()) return
        coversDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                putBinaryEntry(zos, "${CoverService.COVERS_DIR}/${file.name}", file.readBytes())
            }
        }
    }

    private fun validateBookShelfRef(book: Book, validRowIds: Set<Long>): Book {
        val rowId = book.shelfRowId ?: return book
        return if (rowId in validRowIds) book else book.copy(shelfRowId = null)
    }

    private fun isSafeCoverEntry(entryName: String): Boolean {
        if (entryName.contains("..") || entryName.contains('\\')) return false
        if (!entryName.startsWith("${CoverService.COVERS_DIR}/")) return false
        val fileName = entryName.removePrefix("${CoverService.COVERS_DIR}/")
        return fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\')
    }

    private fun clearCoversDir(ctx: Context) {
        val dir = File(ctx.filesDir, CoverService.COVERS_DIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun importCoverFile(ctx: Context, entryName: String, bytes: ByteArray) {
        if (!isSafeCoverEntry(entryName)) return
        val base = ctx.filesDir.canonicalFile
        val target = File(base, entryName).canonicalFile
        if (!target.path.startsWith(base.path + File.separator)) return
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    sealed class ExportResult {
        data object Success : ExportResult()
        data class Failure(val message: String) : ExportResult()
    }

    sealed class ImportResult {
        data object Success : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }
}
