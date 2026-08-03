package com.familylibrary.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.familylibrary.app.data.dao.AppSettingsDao
import com.familylibrary.app.data.dao.BookDao
import com.familylibrary.app.data.dao.BookshelfDao
import com.familylibrary.app.data.dao.FamilyMemberDao
import com.familylibrary.app.data.dao.ReadingRecordDao
import com.familylibrary.app.data.dao.ShelfRowDao
import com.familylibrary.app.data.dao.WishlistDao
import com.familylibrary.app.data.entity.AppSettings
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.Bookshelf
import com.familylibrary.app.data.entity.FamilyMember
import com.familylibrary.app.data.entity.ReadingRecord
import com.familylibrary.app.data.entity.ShelfRow
import com.familylibrary.app.data.entity.WishlistItem

const val DB_SCHEMA_VERSION = 5

@Database(
    entities = [
        Bookshelf::class,
        ShelfRow::class,
        Book::class,
        FamilyMember::class,
        ReadingRecord::class,
        WishlistItem::class,
        AppSettings::class,
    ],
    version = DB_SCHEMA_VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookshelfDao(): BookshelfDao
    abstract fun shelfRowDao(): ShelfRowDao
    abstract fun bookDao(): BookDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun readingRecordDao(): ReadingRecordDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        const val SCHEMA_VERSION = DB_SCHEMA_VERSION
        const val DB_NAME = "family_library.db"
    }
}
