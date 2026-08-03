package com.familylibrary.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ShelfDisplayMode {
    SPINE,
    COVER,
}

data class LastShelfSelection(
    val bookshelfId: Long,
    val rowId: Long,
)

private val Context.shelfDataStore: DataStore<Preferences> by preferencesDataStore(name = "shelf_prefs")

class ShelfPreferences(private val context: Context) {

    private val keyDisplayMode = stringPreferencesKey("display_mode")
    private val keyLastBookshelfId = longPreferencesKey("last_bookshelf_id")
    private val keyLastRowId = longPreferencesKey("last_row_id")

    val displayMode: Flow<ShelfDisplayMode> = context.shelfDataStore.data.map { prefs ->
        when (prefs[keyDisplayMode]) {
            ShelfDisplayMode.COVER.name -> ShelfDisplayMode.COVER
            else -> ShelfDisplayMode.SPINE
        }
    }

    suspend fun setDisplayMode(mode: ShelfDisplayMode) {
        context.shelfDataStore.edit { it[keyDisplayMode] = mode.name }
    }

    suspend fun getLastSelection(): LastShelfSelection? {
        val prefs = context.shelfDataStore.data.first()
        val shelfId = prefs[keyLastBookshelfId] ?: return null
        val rowId = prefs[keyLastRowId] ?: return null
        if (shelfId <= 0L || rowId <= 0L) return null
        return LastShelfSelection(shelfId, rowId)
    }

    suspend fun setLastSelection(bookshelfId: Long, rowId: Long) {
        context.shelfDataStore.edit {
            it[keyLastBookshelfId] = bookshelfId
            it[keyLastRowId] = rowId
        }
    }
}
