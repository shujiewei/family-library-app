package com.familylibrary.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.familylibrary.app.data.entity.AppSettings
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.BookWithLocation
import com.familylibrary.app.data.entity.Bookshelf
import com.familylibrary.app.data.entity.CategoryReadingStats
import com.familylibrary.app.data.entity.FamilyMember
import com.familylibrary.app.data.entity.MemberReadingStats
import com.familylibrary.app.data.entity.ReadingRecord
import com.familylibrary.app.data.entity.ReadingRecordWithBook
import com.familylibrary.app.data.entity.ShelfRow
import com.familylibrary.app.data.entity.WishlistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BookshelfDao {
    @Query("SELECT * FROM bookshelf ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Bookshelf>>

    @Query("SELECT * FROM bookshelf ORDER BY sortOrder, id")
    suspend fun getAll(): List<Bookshelf>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookshelf: Bookshelf): Long

    @Update
    suspend fun update(bookshelf: Bookshelf)

    @Query("DELETE FROM bookshelf WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkReplace(items: List<Bookshelf>)
}

@Dao
interface ShelfRowDao {
    @Query("SELECT * FROM shelf_row WHERE bookshelfId = :bookshelfId ORDER BY sortOrder, id")
    fun observeByBookshelf(bookshelfId: Long): Flow<List<ShelfRow>>

    @Query("SELECT * FROM shelf_row WHERE bookshelfId = :bookshelfId ORDER BY sortOrder, id")
    suspend fun getByBookshelf(bookshelfId: Long): List<ShelfRow>

    @Query("SELECT * FROM shelf_row ORDER BY bookshelfId, sortOrder, id")
    suspend fun getAll(): List<ShelfRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: ShelfRow): Long

    @Update
    suspend fun update(row: ShelfRow)

    @Query("DELETE FROM shelf_row WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM shelf_row WHERE bookshelfId = :bookshelfId")
    suspend fun deleteByBookshelf(bookshelfId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkReplace(items: List<ShelfRow>)
}

@Dao
interface BookDao {
    @Query(
        """
        SELECT b.*, bs.name AS bookshelfName, sr.name AS shelfRowName
        FROM book b
        LEFT JOIN shelf_row sr ON b.shelfRowId = sr.id
        LEFT JOIN bookshelf bs ON sr.bookshelfId = bs.id
        WHERE b.shelfRowId = :shelfRowId
        ORDER BY b.positionInRow, b.id
        """
    )
    fun observeByShelfRow(shelfRowId: Long): Flow<List<BookWithLocation>>

    @Query(
        """
        SELECT b.*, bs.name AS bookshelfName, sr.name AS shelfRowName
        FROM book b
        LEFT JOIN shelf_row sr ON b.shelfRowId = sr.id
        LEFT JOIN bookshelf bs ON sr.bookshelfId = bs.id
        ORDER BY b.title
        """
    )
    fun observeAllWithLocation(): Flow<List<BookWithLocation>>

    @Query("SELECT * FROM book WHERE id = :id")
    suspend fun getById(id: Long): Book?

    @Query(
        """
        SELECT b.*, bs.name AS bookshelfName, sr.name AS shelfRowName
        FROM book b
        LEFT JOIN shelf_row sr ON b.shelfRowId = sr.id
        LEFT JOIN bookshelf bs ON sr.bookshelfId = bs.id
        WHERE b.id = :id
        LIMIT 1
        """
    )
    suspend fun getByIdWithLocation(id: Long): BookWithLocation?

    @Query("SELECT COALESCE(MAX(positionInRow), -1) + 1 FROM book WHERE shelfRowId = :shelfRowId")
    suspend fun nextPositionInRow(shelfRowId: Long): Int

    @Query(
        """
        SELECT b.*, bs.name AS bookshelfName, sr.name AS shelfRowName
        FROM book b
        LEFT JOIN shelf_row sr ON b.shelfRowId = sr.id
        LEFT JOIN bookshelf bs ON sr.bookshelfId = bs.id
        WHERE b.title LIKE '%' || :query || '%'
           OR b.author LIKE '%' || :query || '%'
           OR b.series LIKE '%' || :query || '%'
           OR b.publisher LIKE '%' || :query || '%'
           OR b.isbn LIKE '%' || :query || '%'
        ORDER BY b.title
        """
    )
    suspend fun search(query: String): List<BookWithLocation>

    @Query(
        """
        SELECT b.*, bs.name AS bookshelfName, sr.name AS shelfRowName
        FROM book b
        LEFT JOIN shelf_row sr ON b.shelfRowId = sr.id
        LEFT JOIN bookshelf bs ON sr.bookshelfId = bs.id
        WHERE b.isbn = :isbn
        LIMIT 1
        """
    )
    suspend fun findByIsbn(isbn: String): BookWithLocation?

    @Query("SELECT * FROM book WHERE author = :author AND id != :excludeId ORDER BY title LIMIT :limit")
    suspend fun findByAuthor(author: String, excludeId: Long, limit: Int = 10): List<Book>

    @Query("SELECT * FROM book WHERE series = :series AND series != '' AND id != :excludeId ORDER BY title LIMIT :limit")
    suspend fun findBySeries(series: String, excludeId: Long, limit: Int = 10): List<Book>

    @Query("SELECT * FROM book WHERE recommendedAge = :age AND recommendedAge != '' AND id != :excludeId ORDER BY title LIMIT :limit")
    suspend fun findByAge(age: String, excludeId: Long, limit: Int = 10): List<Book>

    @Query("SELECT * FROM book WHERE lexileLevel != '' AND id != :excludeId ORDER BY ABS(CAST(lexileLevel AS INTEGER) - :lexile) LIMIT :limit")
    suspend fun findBySimilarLexile(lexile: Int, excludeId: Long, limit: Int = 10): List<Book>

    @Query("SELECT DISTINCT author FROM book WHERE author != '' ORDER BY author")
    fun observeAuthors(): Flow<List<String>>

    @Query("SELECT DISTINCT series FROM book WHERE series != '' ORDER BY series")
    fun observeSeries(): Flow<List<String>>

    @Query("SELECT DISTINCT recommendedAge FROM book WHERE recommendedAge != '' ORDER BY recommendedAge")
    fun observeAges(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM book WHERE category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM book WHERE author = :author ORDER BY title")
    fun observeByAuthor(author: String): Flow<List<Book>>

    @Query("SELECT * FROM book WHERE series = :series ORDER BY title")
    fun observeBySeries(series: String): Flow<List<Book>>

    @Query("SELECT * FROM book WHERE recommendedAge = :age ORDER BY title")
    fun observeByAge(age: String): Flow<List<Book>>

    @Query("SELECT * FROM book WHERE category = :category ORDER BY title")
    fun observeByCategory(category: String): Flow<List<Book>>

    @Query("SELECT * FROM book WHERE isEnglish = 1 AND lexileLevel != '' ORDER BY CAST(lexileLevel AS INTEGER)")
    fun observeEnglishByLexile(): Flow<List<Book>>

    @Query("SELECT * FROM book WHERE shelfRowId = :shelfRowId ORDER BY positionInRow, id")
    suspend fun getByShelfRow(shelfRowId: Long): List<Book>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)

    @Update
    suspend fun update(book: Book)

    @Query("DELETE FROM book WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM book ORDER BY id")
    suspend fun getAll(): List<Book>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkReplace(items: List<Book>)
}

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_member ORDER BY id")
    fun observeAll(): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_member ORDER BY id")
    suspend fun getAll(): List<FamilyMember>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: FamilyMember): Long

    @Update
    suspend fun update(member: FamilyMember)

    @Query("DELETE FROM family_member WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkReplace(items: List<FamilyMember>)
}

@Dao
interface ReadingRecordDao {
    @Query(
        """
        SELECT rr.*, b.title AS bookTitle, b.author AS bookAuthor,
               b.wordCount AS bookWordCount, b.category AS bookCategory,
               fm.name AS memberName
        FROM reading_record rr
        JOIN book b ON rr.bookId = b.id
        JOIN family_member fm ON rr.memberId = fm.id
        ORDER BY rr.finishDate DESC, rr.createdAt DESC
        """
    )
    fun observeAllWithDetails(): Flow<List<ReadingRecordWithBook>>

    @Query(
        """
        SELECT rr.*, b.title AS bookTitle, b.author AS bookAuthor,
               b.wordCount AS bookWordCount, b.category AS bookCategory,
               fm.name AS memberName
        FROM reading_record rr
        JOIN book b ON rr.bookId = b.id
        JOIN family_member fm ON rr.memberId = fm.id
        WHERE rr.memberId = :memberId
        ORDER BY rr.finishDate DESC, rr.createdAt DESC
        """
    )
    fun observeByMember(memberId: Long): Flow<List<ReadingRecordWithBook>>

    @Query(
        """
        SELECT fm.id AS memberId, fm.name AS memberName,
               COUNT(DISTINCT rr.bookId) AS bookCount,
               COALESCE(SUM(b.wordCount), 0) AS totalWordCount
        FROM family_member fm
        LEFT JOIN reading_record rr ON fm.id = rr.memberId
        LEFT JOIN book b ON rr.bookId = b.id
        GROUP BY fm.id, fm.name
        ORDER BY fm.id
        """
    )
    fun observeMemberStats(): Flow<List<MemberReadingStats>>

    @Query(
        """
        SELECT COALESCE(b.category, '未分类') AS category,
               COUNT(DISTINCT rr.bookId) AS bookCount,
               COALESCE(SUM(b.wordCount), 0) AS totalWordCount
        FROM reading_record rr
        JOIN book b ON rr.bookId = b.id
        WHERE rr.memberId = :memberId
        GROUP BY b.category
        ORDER BY bookCount DESC
        """
    )
    fun observeCategoryStatsByMember(memberId: Long): Flow<List<CategoryReadingStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ReadingRecord): Long

    @Query("DELETE FROM reading_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM reading_record ORDER BY id")
    suspend fun getAll(): List<ReadingRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkReplace(items: List<ReadingRecord>)
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_item ORDER BY priority DESC, createdAt DESC")
    fun observeAll(): Flow<List<WishlistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WishlistItem): Long

    @Update
    suspend fun update(item: WishlistItem)

    @Query("DELETE FROM wishlist_item WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM wishlist_item WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): WishlistItem?

    @Query("SELECT * FROM wishlist_item ORDER BY id")
    suspend fun getAll(): List<WishlistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkReplace(items: List<WishlistItem>)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettings?

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettings)

    @Transaction
    suspend fun ensureExists(initialSettings: AppSettings) {
        if (get() == null) upsert(initialSettings)
    }
}
