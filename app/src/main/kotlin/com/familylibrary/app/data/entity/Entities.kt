package com.familylibrary.app.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookshelf")
data class Bookshelf(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "shelf_row")
data class ShelfRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookshelfId: Long,
    val name: String,
    val description: String = "",
    val sortOrder: Int = 0,
)

@Entity(tableName = "book")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val pageCount: Int = 0,
    val wordCount: Int = 0,
    val description: String = "",
    val isbn: String = "",
    /** 本地缩略图相对路径，如 covers/thumb_42.jpg */
    val coverUri: String? = null,
    /** 封面来源：none / isbn / custom */
    val coverSource: String = CoverMeta.SOURCE_NONE,
    /** 拉取状态：none / loading / ok / failed */
    val coverStatus: String = CoverMeta.STATUS_NONE,
    val series: String = "",
    val recommendedAge: String = "",
    val lexileLevel: String = "",
    val category: String = "",
    val isEnglish: Boolean = false,
    val shelfRowId: Long? = null,
    val positionInRow: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "family_member")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reading_record")
data class ReadingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val bookId: Long,
    val startDate: String? = null,
    val finishDate: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "wishlist_item")
data class WishlistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val isbn: String = "",
    val note: String = "",
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val adminPinHash: String = "",
    val adminPinSalt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

data class BookWithLocation(
    @Embedded val book: Book,
    val bookshelfName: String?,
    val shelfRowName: String?,
)

data class ReadingRecordWithBook(
    @Embedded val record: ReadingRecord,
    val bookTitle: String,
    val bookAuthor: String,
    val bookWordCount: Int,
    val bookCategory: String,
    val memberName: String,
)

data class MemberReadingStats(
    val memberId: Long,
    val memberName: String,
    val bookCount: Int,
    val totalWordCount: Long,
)

data class CategoryReadingStats(
    val category: String,
    val bookCount: Int,
    val totalWordCount: Long,
)
