package com.familylibrary.app.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.BookWithLocation
import com.familylibrary.app.data.entity.Bookshelf
import com.familylibrary.app.data.entity.ShelfRow
import com.familylibrary.app.data.preferences.ShelfDisplayMode
import com.familylibrary.app.data.preferences.ShelfPreferences
import com.familylibrary.app.data.repository.BatchAddResult
import com.familylibrary.app.data.repository.BookRepository
import com.familylibrary.app.data.repository.MoveTarget
import com.familylibrary.app.data.repository.ShelfRepository
import com.familylibrary.app.data.repository.parseBatchLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShelfUiState(
    val bookshelves: List<Bookshelf> = emptyList(),
    val selectedBookshelfId: Long? = null,
    val rows: List<ShelfRow> = emptyList(),
    val selectedRowId: Long? = null,
    val books: List<BookWithLocation> = emptyList(),
    val selectedBookIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isArchiveShelf: Boolean = false,
    val currentLocationLabel: String = "",
    val displayMode: ShelfDisplayMode = ShelfDisplayMode.SPINE,
)

class ShelfViewModel(
    private val shelfRepo: ShelfRepository,
    private val bookRepo: BookRepository,
    private val shelfPreferences: ShelfPreferences,
) : ViewModel() {

    private val _selectedBookshelfId = MutableStateFlow<Long?>(null)
    private val _selectedRowId = MutableStateFlow<Long?>(null)
    private val _selectedBookIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)
    private val _isArchiveShelf = MutableStateFlow(false)
    private val _locationLabel = MutableStateFlow("")
    private val _displayMode = MutableStateFlow(ShelfDisplayMode.SPINE)
    private var pendingRestoreRowId: Long? = null
    private var hasAttemptedRestore = false
    private var rowsJob: Job? = null
    private var booksJob: Job? = null

    private val _moveTargets = MutableStateFlow<List<MoveTarget>>(emptyList())
    val moveTargets: StateFlow<List<MoveTarget>> = _moveTargets.asStateFlow()

    val uiState: StateFlow<ShelfUiState> = combine(
        combine(
            shelfRepo.observeBookshelves(),
            _selectedBookshelfId,
            _selectedRowId,
        ) { bookshelvesRaw, shelfId, rowId ->
            Triple(bookshelvesRaw, shelfId, rowId)
        },
        combine(
            _selectedBookIds,
            _isSelectionMode,
            _isArchiveShelf,
        ) { selectedIds, selectionMode, isArchive ->
            Triple(selectedIds, selectionMode, isArchive)
        },
        combine(_locationLabel, _displayMode) { locationLabel, displayMode ->
            locationLabel to displayMode
        },
    ) { shelfTriple, flagsTriple, labelPair ->
        val (bookshelvesRaw, shelfId, rowId) = shelfTriple
        val (selectedIds, selectionMode, isArchive) = flagsTriple
        val (locationLabel, displayMode) = labelPair
        val bookshelves = shelfRepo.sortBookshelvesForDisplay(bookshelvesRaw)
        ShelfUiState(
            bookshelves = bookshelves,
            selectedBookshelfId = shelfId,
            selectedRowId = rowId,
            selectedBookIds = selectedIds,
            isSelectionMode = selectionMode,
            isArchiveShelf = isArchive,
            currentLocationLabel = locationLabel,
            displayMode = displayMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShelfUiState())

    private val _rows = MutableStateFlow<List<ShelfRow>>(emptyList())
    val rows: StateFlow<List<ShelfRow>> = _rows.asStateFlow()

    private val _books = MutableStateFlow<List<BookWithLocation>>(emptyList())
    val books: StateFlow<List<BookWithLocation>> = _books.asStateFlow()

    init {
        viewModelScope.launch {
            shelfPreferences.displayMode.collect { _displayMode.value = it }
        }
        viewModelScope.launch {
            shelfRepo.ensureArchiveRow()
            refreshMoveTargets()
            val lastSelection = shelfPreferences.getLastSelection()
            pendingRestoreRowId = lastSelection?.rowId
            shelfRepo.observeBookshelves().collect { shelves ->
                val sorted = shelfRepo.sortBookshelvesForDisplay(shelves)
                val nonArchive = sorted.filter { !com.familylibrary.app.data.ArchiveConfig.isArchiveShelf(it.name) }
                if (_selectedBookshelfId.value == null && !hasAttemptedRestore) {
                    hasAttemptedRestore = true
                    val targetShelfId = lastSelection?.bookshelfId?.takeIf { id ->
                        sorted.any { it.id == id }
                    } ?: nonArchive.firstOrNull()?.id
                    if (targetShelfId != null) {
                        if (lastSelection?.bookshelfId == targetShelfId) {
                            pendingRestoreRowId = lastSelection.rowId
                        }
                        selectBookshelf(targetShelfId)
                    }
                }
            }
        }
    }

    private fun refreshMoveTargets() = viewModelScope.launch {
        _moveTargets.value = shelfRepo.getMoveTargets()
    }

    private fun updateLocationLabel() {
        val shelf = uiState.value.bookshelves.find { it.id == _selectedBookshelfId.value }
        val row = _rows.value.find { it.id == _selectedRowId.value }
        _locationLabel.value = when {
            shelf != null && row != null -> "${shelf.name} / ${row.name}"
            shelf != null -> shelf.name
            else -> ""
        }
    }

    fun setDisplayMode(mode: ShelfDisplayMode) {
        viewModelScope.launch { shelfPreferences.setDisplayMode(mode) }
    }

    fun selectBookshelf(id: Long) {
        rowsJob?.cancel()
        booksJob?.cancel()
        _selectedBookshelfId.value = id
        _selectedRowId.value = null
        _books.value = emptyList()
        rowsJob = viewModelScope.launch {
            _isArchiveShelf.value = shelfRepo.isArchiveBookshelf(id)
            shelfRepo.observeRows(id).collect { rowList ->
                _rows.value = rowList
                if (_selectedRowId.value == null && rowList.isNotEmpty()) {
                    val rowId = pendingRestoreRowId?.takeIf { pending ->
                        rowList.any { it.id == pending }
                    } ?: rowList.first().id
                    pendingRestoreRowId = null
                    selectRow(rowId)
                } else {
                    updateLocationLabel()
                }
            }
        }
    }

    fun selectRow(id: Long) {
        booksJob?.cancel()
        _selectedRowId.value = id
        clearSelection()
        updateLocationLabel()
        _selectedBookshelfId.value?.let { shelfId ->
            viewModelScope.launch { shelfPreferences.setLastSelection(shelfId, id) }
        }
        booksJob = viewModelScope.launch {
            shelfRepo.observeBooksInRow(id).collect { bookList ->
                _books.value = bookList
            }
        }
    }

    fun createBookshelf(name: String) = viewModelScope.launch {
        shelfRepo.createBookshelf(name)
        refreshMoveTargets()
    }

    fun createRow(name: String) = viewModelScope.launch {
        val shelfId = _selectedBookshelfId.value ?: return@launch
        shelfRepo.createRow(shelfId, name)
        refreshMoveTargets()
    }

    fun deleteBookshelf(id: Long) = viewModelScope.launch {
        shelfRepo.deleteBookshelf(id)
        refreshMoveTargets()
        if (_selectedBookshelfId.value == id) {
            _selectedBookshelfId.value = null
            _selectedRowId.value = null
        }
    }

    fun deleteRow(id: Long) = viewModelScope.launch {
        shelfRepo.deleteRow(id)
        refreshMoveTargets()
        if (_selectedRowId.value == id) _selectedRowId.value = null
    }

    fun addBook(book: Book) = viewModelScope.launch {
        bookRepo.addBook(book, _selectedRowId.value)
    }

    fun addBooksBatch(lines: List<String>, onResult: (BatchAddResult) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val rowId = _selectedRowId.value ?: return@launch
            val entries = lines.mapNotNull { parseBatchLine(it) }
            val result = bookRepo.addBooksBatch(entries, rowId)
            onResult(result)
        }

    fun updateBook(book: Book) = viewModelScope.launch {
        bookRepo.updateBook(book)
    }

    fun deleteSelectedBooks() = viewModelScope.launch {
        bookRepo.deleteBooks(_selectedBookIds.value.toList())
        clearSelection()
    }

    fun moveSelectedBooks(targetRowId: Long) = viewModelScope.launch {
        bookRepo.moveBooks(_selectedBookIds.value.toList(), targetRowId)
        clearSelection()
    }

    fun archiveSelectedBooks() = viewModelScope.launch {
        val archiveRowId = shelfRepo.ensureArchiveRow()
        bookRepo.moveBooks(_selectedBookIds.value.toList(), archiveRowId)
        clearSelection()
        refreshMoveTargets()
    }

    fun toggleBookSelection(id: Long) {
        _selectedBookIds.value = _selectedBookIds.value.let {
            if (id in it) it - id else it + id
        }
    }

    fun enterSelectionMode() {
        _isSelectionMode.value = true
    }

    fun clearSelection() {
        _selectedBookIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun selectAll() {
        _selectedBookIds.value = _books.value.map { it.book.id }.toSet()
    }

    class Factory(private val app: FamilyLibraryApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val sl = app.serviceLocator
            return ShelfViewModel(
                sl.shelfRepository,
                sl.bookRepository,
                sl.shelfPreferences,
            ) as T
        }
    }
}
