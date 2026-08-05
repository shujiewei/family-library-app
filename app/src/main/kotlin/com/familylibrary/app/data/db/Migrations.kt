package com.familylibrary.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE book ADD COLUMN isbn TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE book ADD COLUMN coverSource TEXT NOT NULL DEFAULT 'none'")
        db.execSQL("ALTER TABLE book ADD COLUMN coverStatus TEXT NOT NULL DEFAULT 'none'")
        db.execSQL(
            "UPDATE book SET coverSource = 'isbn', coverStatus = 'ok' WHERE coverUri IS NOT NULL AND coverUri != ''"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wishlist_item ADD COLUMN isbn TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_book_isbn_nonempty ON book(isbn) WHERE isbn != ''",
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bookshelf ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE shelf_row ADD COLUMN description TEXT NOT NULL DEFAULT ''")
    }
}
