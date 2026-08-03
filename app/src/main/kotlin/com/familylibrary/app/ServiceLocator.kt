package com.familylibrary.app

import android.content.Context
import androidx.room.Room
import com.familylibrary.app.data.cover.CoverService
import com.familylibrary.app.data.dao.AppSettingsDao
import com.familylibrary.app.data.db.AppDatabase
import com.familylibrary.app.data.db.MIGRATION_1_2
import com.familylibrary.app.data.db.MIGRATION_2_3
import com.familylibrary.app.data.db.MIGRATION_3_4
import com.familylibrary.app.data.db.MIGRATION_4_5
import com.familylibrary.app.data.entity.AppSettings
import com.familylibrary.app.data.repository.BookRepository
import com.familylibrary.app.data.repository.MemberRepository
import com.familylibrary.app.data.repository.ReadingRepository
import com.familylibrary.app.data.repository.ShelfRepository
import com.familylibrary.app.data.repository.WishlistRepository
import com.familylibrary.app.ui.admin.AdminModeController
import com.familylibrary.app.util.Hash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServiceLocator(context: Context) {
    val appContext: Context = context.applicationContext

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    val coverService by lazy { CoverService(appContext) }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val adminModeController = AdminModeController()

    val shelfRepository by lazy {
        ShelfRepository(database.bookshelfDao(), database.shelfRowDao(), database.bookDao())
    }

    val isbnLookupService by lazy { com.familylibrary.app.data.cover.IsbnLookupService() }

    val shelfPreferences by lazy { com.familylibrary.app.data.preferences.ShelfPreferences(appContext) }

    val bookRepository by lazy {
        BookRepository(
            database,
            database.bookDao(),
            coverService,
            isbnLookupService,
            applicationScope,
        )
    }

    val memberRepository by lazy { MemberRepository(database.familyMemberDao()) }

    val readingRepository by lazy { ReadingRepository(database.readingRecordDao()) }

    val wishlistRepository by lazy { WishlistRepository(database.wishlistDao()) }

    val appSettingsDao: AppSettingsDao get() = database.appSettingsDao()

    suspend fun ensureInitialized() {
        val salt = Hash.generateSalt()
        val hash = Hash.sha256(AdminModeController.DEFAULT_PIN, salt)
        appSettingsDao.ensureExists(
            AppSettings(adminPinHash = hash, adminPinSalt = salt)
        )
        val members = database.familyMemberDao().getAll()
        if (members.isEmpty()) {
            database.familyMemberDao().insert(
                com.familylibrary.app.data.entity.FamilyMember(name = "家人", colorIndex = 0)
            )
        }
        shelfRepository.ensureArchiveRow()
        _initialized.value = true
    }
}
