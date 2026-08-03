package com.familylibrary.app.data.repository

import com.familylibrary.app.data.ArchiveConfig
import com.familylibrary.app.data.cover.CoverService
import com.familylibrary.app.data.cover.IsbnLookupService
import com.familylibrary.app.data.dao.BookDao
import com.familylibrary.app.data.dao.BookshelfDao
import com.familylibrary.app.data.dao.ShelfRowDao
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.BookWithLocation
import com.familylibrary.app.data.entity.Bookshelf
import com.familylibrary.app.data.entity.CoverMeta
import com.familylibrary.app.data.entity.ShelfRow
import android.net.Uri
import androidx.room.withTransaction
import com.familylibrary.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.familylibrary.app.util.hasValidTitle
import com.familylibrary.app.util.locationLabel
import java.io.File

class ShelfRepository(
    private val bookshelfDao: BookshelfDao,
    private val shelfRowDao: ShelfRowDao,
    private val bookDao: BookDao,
) {
    fun observeBookshelves(): Flow<List<Bookshelf>> = bookshelfDao.observeAll()

    fun observeRows(bookshelfId: Long): Flow<List<ShelfRow>> =
        shelfRowDao.observeByBookshelf(bookshelfId)

    fun observeBooksInRow(shelfRowId: Long): Flow<List<BookWithLocation>> =
        bookDao.observeByShelfRow(shelfRowId)

    suspend fun createBookshelf(name: String): Long {
        val all = bookshelfDao.getAll()
        return bookshelfDao.insert(Bookshelf(name = name, sortOrder = all.size))
    }

    suspend fun createRow(bookshelfId: Long, name: String): Long {
        val rows = shelfRowDao.getByBookshelf(bookshelfId)
        return shelfRowDao.insert(ShelfRow(bookshelfId = bookshelfId, name = name, sortOrder = rows.size))
    }

    suspend fun deleteBookshelf(id: Long) {
        val rows = shelfRowDao.getByBookshelf(id)
        rows.forEach { row ->
            val books = bookDao.getByShelfRow(row.id)
            books.forEach { book ->
                bookDao.update(book.copy(shelfRowId = null, updatedAt = System.currentTimeMillis()))
            }
            shelfRowDao.deleteById(row.id)
        }
        bookshelfDao.deleteById(id)
    }

    suspend fun deleteRow(id: Long) {
        val books = bookDao.getByShelfRow(id)
        books.forEach { book ->
            bookDao.update(book.copy(shelfRowId = null, updatedAt = System.currentTimeMillis()))
        }
        shelfRowDao.deleteById(id)
    }

    suspend fun renameBookshelf(id: Long, name: String) {
        val shelf = bookshelfDao.getAll().find { it.id == id } ?: return
        bookshelfDao.update(shelf.copy(name = name))
    }

    suspend fun renameRow(id: Long, name: String) {
        val row = shelfRowDao.getAll().find { it.id == id } ?: return
        shelfRowDao.update(row.copy(name = name))
    }

    /** 确保「归档」书架存在，返回归档排 id */
    suspend fun ensureArchiveRow(): Long {
        val shelves = bookshelfDao.getAll()
        var shelfId = shelves.find { ArchiveConfig.isArchiveShelf(it.name) }?.id
        if (shelfId == null) {
            shelfId = bookshelfDao.insert(
                Bookshelf(name = ArchiveConfig.SHELF_NAME, sortOrder = Int.MAX_VALUE)
            )
        }
        val rows = shelfRowDao.getAll().filter { it.bookshelfId == shelfId }
        return rows.firstOrNull()?.id ?: shelfRowDao.insert(
            ShelfRow(bookshelfId = shelfId, name = ArchiveConfig.ROW_NAME, sortOrder = 0)
        )
    }

    suspend fun getMoveTargets(): List<MoveTarget> {
        val shelves = bookshelfDao.getAll()
        val rows = shelfRowDao.getAll()
        return rows.mapNotNull { row ->
            val shelf = shelves.find { it.id == row.bookshelfId } ?: return@mapNotNull null
            MoveTarget(
                rowId = row.id,
                bookshelfName = shelf.name,
                rowName = row.name,
                isArchive = ArchiveConfig.isArchiveShelf(shelf.name),
            )
        }.sortedWith(compareBy<MoveTarget> { it.isArchive }.thenBy { it.bookshelfName }.thenBy { it.rowName })
    }

    suspend fun isArchiveBookshelf(bookshelfId: Long): Boolean {
        val shelf = bookshelfDao.getAll().find { it.id == bookshelfId } ?: return false
        return ArchiveConfig.isArchiveShelf(shelf.name)
    }

    suspend fun isArchiveRow(rowId: Long): Boolean {
        val row = shelfRowDao.getAll().find { it.id == rowId } ?: return false
        return isArchiveBookshelf(row.bookshelfId)
    }

    fun sortBookshelvesForDisplay(shelves: List<Bookshelf>): List<Bookshelf> =
        shelves.sortedWith(compareBy<Bookshelf> { ArchiveConfig.isArchiveShelf(it.name) }.thenBy { it.sortOrder })
}

class BookRepository(
    private val database: AppDatabase,
    private val bookDao: BookDao,
    private val coverService: CoverService,
    private val isbnLookup: IsbnLookupService,
    private val backgroundScope: CoroutineScope,
) {
    private val enrichSemaphore = Semaphore(3)

    fun observeAll(): Flow<List<BookWithLocation>> = bookDao.observeAllWithLocation()

    suspend fun getById(id: Long): Book? = bookDao.getById(id)

    suspend fun getByIdWithLocation(id: Long): BookWithLocation? = bookDao.getByIdWithLocation(id)

    suspend fun search(query: String): List<BookWithLocation> =
        if (query.isBlank()) emptyList() else bookDao.search(query.trim())

    suspend fun findByIsbn(isbn: String): BookWithLocation? {
        val normalized = CoverService.normalizeIsbn(isbn)
        if (normalized.isBlank()) return null
        return bookDao.findByIsbn(normalized)
    }

    suspend fun addBook(book: Book, shelfRowId: Long?): Long? {
        if (!hasValidTitle(book.title, book.isbn)) return null
        if (book.isbn.isNotBlank() && findByIsbn(book.isbn) != null) return null
        val position = if (shelfRowId != null) bookDao.nextPositionInRow(shelfRowId) else 0
        val id = bookDao.insert(
            book.copy(
                shelfRowId = shelfRowId,
                positionInRow = position,
                coverUri = null,
                coverSource = CoverMeta.SOURCE_NONE,
                coverStatus = if (book.isbn.isNotBlank()) CoverMeta.STATUS_LOADING else CoverMeta.STATUS_NONE,
                updatedAt = System.currentTimeMillis(),
            )
        )
        scheduleEnrichment(id, book.isbn)
        return id
    }

    suspend fun addBooksBatch(entries: List<BatchBookEntry>, shelfRowId: Long): BatchAddResult {
        val added = mutableListOf<Long>()
        val failed = mutableListOf<BatchAddFailure>()
        var nextPosition = bookDao.nextPositionInRow(shelfRowId)
        database.withTransaction {
            entries.forEach { entry ->
                var title = entry.title.trim()
                val isbn = CoverService.normalizeIsbn(entry.isbn)
                if (!hasValidTitle(title, isbn) && isbn.isNotBlank()) {
                    title = isbnLookup.lookupTitle(isbn).orEmpty()
                }
                if (!hasValidTitle(title, isbn)) {
                    failed.add(BatchAddFailure(isbn = isbn, reason = "未找到书名，请手动填写"))
                    return@forEach
                }
                if (isbn.isNotBlank()) {
                    val existing = bookDao.findByIsbn(isbn)
                    if (existing != null) {
                        failed.add(
                            BatchAddFailure(
                                isbn = isbn,
                                title = existing.book.title,
                                reason = "已在库：${existing.locationLabel()}",
                            ),
                        )
                        return@forEach
                    }
                }
                val id = bookDao.insert(
                    Book(
                        title = title,
                        isbn = isbn,
                        author = entry.author.trim(),
                        shelfRowId = shelfRowId,
                        positionInRow = nextPosition++,
                        coverSource = CoverMeta.SOURCE_NONE,
                        coverStatus = if (isbn.isNotBlank()) CoverMeta.STATUS_LOADING else CoverMeta.STATUS_NONE,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                added.add(id)
            }
        }
        added.forEach { id ->
            val book = bookDao.getById(id)
            if (book != null && book.isbn.isNotBlank()) scheduleEnrichment(id, book.isbn)
        }
        return BatchAddResult(addedCount = added.size, failures = failed)
    }

    suspend fun addScannedBooks(books: List<Book>, shelfRowId: Long): BatchAddResult {
        if (books.isEmpty()) return BatchAddResult(0, emptyList())
        val added = mutableListOf<Long>()
        val failed = mutableListOf<BatchAddFailure>()
        var nextPosition = bookDao.nextPositionInRow(shelfRowId)
        database.withTransaction {
            books.forEach { book ->
                if (!hasValidTitle(book.title, book.isbn)) {
                    failed.add(
                        BatchAddFailure(
                            isbn = book.isbn,
                            title = book.title,
                            reason = "缺少有效书名",
                        ),
                    )
                    return@forEach
                }
                if (book.isbn.isNotBlank()) {
                    val existing = bookDao.findByIsbn(book.isbn)
                    if (existing != null) {
                        failed.add(
                            BatchAddFailure(
                                isbn = book.isbn,
                                title = existing.book.title,
                                reason = "已在库：${existing.locationLabel()}",
                            ),
                        )
                        return@forEach
                    }
                }
                val id = bookDao.insert(
                    book.copy(
                        shelfRowId = shelfRowId,
                        positionInRow = nextPosition++,
                        coverUri = null,
                        coverSource = CoverMeta.SOURCE_NONE,
                        coverStatus = if (book.isbn.isNotBlank()) CoverMeta.STATUS_LOADING else CoverMeta.STATUS_NONE,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                added.add(id)
            }
        }
        added.forEach { id ->
            val saved = bookDao.getById(id)
            if (saved != null && saved.isbn.isNotBlank()) scheduleEnrichment(id, saved.isbn)
        }
        return BatchAddResult(addedCount = added.size, failures = failed)
    }

    /** 后台补全书目信息 + 封面，不阻塞调用方 */
    fun scheduleEnrichment(bookId: Long, isbn: String) {
        if (isbn.isBlank()) return
        backgroundScope.launch {
            enrichSemaphore.withPermit {
                enrichBookFromIsbn(bookId, isbn)
            }
        }
    }

    private suspend fun enrichBookFromIsbn(bookId: Long, isbn: String) {
        var book = bookDao.getById(bookId) ?: return
        if (book.coverSource == CoverMeta.SOURCE_CUSTOM) return

        if (needsSupplementalMetadata(book)) {
            val info = isbnLookup.lookupSupplemental(isbn)
            if (info != null) {
                book = book.copy(
                    author = info.author.takeIf { book.author.isBlank() } ?: book.author,
                    publisher = info.publisher.takeIf { book.publisher.isBlank() } ?: book.publisher,
                    pageCount = if (book.pageCount == 0) info.pageCount else book.pageCount,
                    description = info.description.takeIf { book.description.isBlank() } ?: book.description,
                    updatedAt = System.currentTimeMillis(),
                )
                bookDao.update(book)
            }
        }

        if (book.coverSource != CoverMeta.SOURCE_CUSTOM) {
            fetchCoverFromIsbn(bookId, isbn)
        }
    }

    private fun needsSupplementalMetadata(book: Book): Boolean =
        book.author.isBlank() || book.publisher.isBlank() || book.description.isBlank()

    suspend fun updateBook(book: Book) {
        val old = bookDao.getById(book.id)
        val isbnChanged = old != null && book.isbn != old.isbn
        val keepCustomCover = book.coverSource == CoverMeta.SOURCE_CUSTOM
        bookDao.update(book.copy(updatedAt = System.currentTimeMillis()))
        if (!keepCustomCover && book.isbn.isNotBlank() && (isbnChanged || book.coverUri.isNullOrBlank())) {
            markCoverLoading(book.copy(coverSource = CoverMeta.SOURCE_ISBN))
            scheduleEnrichment(book.id, book.isbn)
        }
    }

    /** 按 ISBN 重新拉取；会覆盖当前封面（含自定义） */
    suspend fun retryFetchCoverFromIsbn(bookId: Long): CoverActionResult {
        val book = bookDao.getById(bookId) ?: return CoverActionResult.NotFound
        if (book.isbn.isBlank()) return CoverActionResult.NoIsbn
        return fetchCoverFromIsbn(bookId, book.isbn, force = true)
    }

    suspend fun saveCustomCoverFromUri(bookId: Long, uri: Uri): CoverActionResult {
        val book = bookDao.getById(bookId) ?: return CoverActionResult.NotFound
        markCoverLoading(book)
        return when (val result = coverService.saveCustomFromUri(bookId, uri)) {
            is CoverService.FetchResult.Success -> {
                applyCoverSuccess(bookId, result.relativePath, CoverMeta.SOURCE_CUSTOM)
                CoverActionResult.Success
            }
            is CoverService.FetchResult.NotFound -> {
                markCoverFailed(bookId, CoverMeta.SOURCE_CUSTOM)
                CoverActionResult.Failed("图片无效")
            }
            is CoverService.FetchResult.Error -> {
                markCoverFailed(bookId, book.coverSource)
                CoverActionResult.Failed(result.message)
            }
        }
    }

    suspend fun saveCustomCoverFromFile(bookId: Long, file: File): CoverActionResult {
        val book = bookDao.getById(bookId) ?: return CoverActionResult.NotFound
        markCoverLoading(book)
        return when (val result = coverService.saveCustomFromFile(bookId, file)) {
            is CoverService.FetchResult.Success -> {
                applyCoverSuccess(bookId, result.relativePath, CoverMeta.SOURCE_CUSTOM)
                file.delete()
                CoverActionResult.Success
            }
            is CoverService.FetchResult.NotFound,
            is CoverService.FetchResult.Error -> {
                markCoverFailed(bookId, book.coverSource)
                val msg = if (result is CoverService.FetchResult.Error) result.message else "保存失败"
                CoverActionResult.Failed(msg)
            }
        }
    }

    private suspend fun fetchCoverFromIsbn(
        bookId: Long,
        isbn: String,
        force: Boolean = false,
    ): CoverActionResult {
        val book = bookDao.getById(bookId) ?: return CoverActionResult.NotFound
        if (!force && book.coverSource == CoverMeta.SOURCE_CUSTOM) {
            return CoverActionResult.SkippedCustom
        }
        markCoverLoading(book.copy(coverSource = CoverMeta.SOURCE_ISBN))
        return when (val result = coverService.fetchAndSaveThumbnail(bookId, isbn)) {
            is CoverService.FetchResult.Success -> {
                applyCoverSuccess(bookId, result.relativePath, CoverMeta.SOURCE_ISBN)
                CoverActionResult.Success
            }
            is CoverService.FetchResult.NotFound -> {
                markCoverFailed(bookId, CoverMeta.SOURCE_ISBN)
                CoverActionResult.Failed("未找到该 ISBN 的封面")
            }
            is CoverService.FetchResult.Error -> {
                markCoverFailed(bookId, CoverMeta.SOURCE_ISBN)
                CoverActionResult.Failed(result.message)
            }
        }
    }

    private suspend fun markCoverLoading(book: Book) {
        bookDao.update(
            book.copy(
                coverStatus = CoverMeta.STATUS_LOADING,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun markCoverFailed(bookId: Long, source: String) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(
            book.copy(
                coverSource = source,
                coverStatus = CoverMeta.STATUS_FAILED,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun applyCoverSuccess(bookId: Long, relativePath: String, source: String) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(
            book.copy(
                coverUri = relativePath,
                coverSource = source,
                coverStatus = CoverMeta.STATUS_OK,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteBooks(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { id ->
            bookDao.getById(id)?.coverUri?.let { coverService.deleteCover(it) }
            coverService.deleteCoverForBook(id)
        }
        bookDao.deleteByIds(ids)
    }

    suspend fun moveBooks(ids: List<Long>, targetShelfRowId: Long) {
        var position = bookDao.nextPositionInRow(targetShelfRowId)
        ids.forEach { id ->
            val book = bookDao.getById(id) ?: return@forEach
            bookDao.update(
                book.copy(
                    shelfRowId = targetShelfRowId,
                    positionInRow = position++,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun findSimilar(book: Book, limit: Int = 8): List<Book> {
        val results = mutableListOf<Book>()
        val seen = mutableSetOf(book.id)

        if (book.author.isNotBlank()) {
            bookDao.findByAuthor(book.author, book.id, limit).forEach {
                if (seen.add(it.id)) results.add(it)
            }
        }
        if (book.series.isNotBlank() && results.size < limit) {
            bookDao.findBySeries(book.series, book.id, limit).forEach {
                if (seen.add(it.id)) results.add(it)
            }
        }
        if (book.recommendedAge.isNotBlank() && results.size < limit) {
            bookDao.findByAge(book.recommendedAge, book.id, limit).forEach {
                if (seen.add(it.id)) results.add(it)
            }
        }
        if (book.isEnglish && book.lexileLevel.isNotBlank() && results.size < limit) {
            val lexile = book.lexileLevel.filter { it.isDigit() }.toIntOrNull()
            if (lexile != null) {
                bookDao.findBySimilarLexile(lexile, book.id, limit).forEach {
                    if (seen.add(it.id)) results.add(it)
                }
            }
        }
        return results.take(limit)
    }

    fun observeAuthors() = bookDao.observeAuthors()
    fun observeSeries() = bookDao.observeSeries()
    fun observeAges() = bookDao.observeAges()
    fun observeCategories() = bookDao.observeCategories()
    fun observeByAuthor(author: String) = bookDao.observeByAuthor(author)
    fun observeBySeries(series: String) = bookDao.observeBySeries(series)
    fun observeByAge(age: String) = bookDao.observeByAge(age)
    fun observeByCategory(category: String) = bookDao.observeByCategory(category)
    fun observeEnglishByLexile() = bookDao.observeEnglishByLexile()
}

sealed class CoverActionResult {
    data object Success : CoverActionResult()
    data object NotFound : CoverActionResult()
    data object NoIsbn : CoverActionResult()
    data object SkippedCustom : CoverActionResult()
    data class Failed(val message: String) : CoverActionResult()
}

data class BatchBookEntry(
    val title: String,
    val isbn: String = "",
    val author: String = "",
)

data class BatchAddFailure(
    val isbn: String = "",
    val title: String = "",
    val reason: String,
) {
    val displayLabel: String
        get() = when {
            title.isNotBlank() -> title
            isbn.isNotBlank() -> "ISBN $isbn"
            else -> "未知图书"
        }
}

data class BatchAddResult(
    val addedCount: Int,
    val failures: List<BatchAddFailure>,
) {
    val hasFailures: Boolean get() = failures.isNotEmpty()
}

data class MoveTarget(
    val rowId: Long,
    val bookshelfName: String,
    val rowName: String,
    val isArchive: Boolean,
) {
    val label: String
        get() = if (isArchive) "📦 $bookshelfName" else "$bookshelfName / $rowName"
}

fun parseBatchLine(line: String): BatchBookEntry? {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return null
    val parts = trimmed.split(',', '，', '\t').map { it.trim() }
    return when {
        parts.size >= 2 -> BatchBookEntry(title = parts[0], isbn = parts[1])
        CoverService.isValidIsbn(CoverService.normalizeIsbn(parts[0])) ->
            BatchBookEntry(title = "", isbn = CoverService.normalizeIsbn(parts[0]))
        else -> BatchBookEntry(title = parts[0])
    }
}
