package com.familylibrary.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.BookWithLocation
import com.familylibrary.app.data.repository.BookRepository
import com.familylibrary.app.ui.components.BookCover
import com.familylibrary.app.ui.components.BookMoveActions
import com.familylibrary.app.ui.components.BookTitleText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val bookRepo: BookRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<BookWithLocation>>(emptyList())
    val results = _results.asStateFlow()

    private val _selectedBook = MutableStateFlow<BookWithLocation?>(null)
    val selectedBook = _selectedBook.asStateFlow()

    private val _recommendations = MutableStateFlow<List<Book>>(emptyList())
    val recommendations = _recommendations.asStateFlow()

    fun search(q: String) {
        _query.value = q
        viewModelScope.launch {
            _results.value = bookRepo.search(q)
        }
    }

    fun selectBook(item: BookWithLocation) {
        _selectedBook.value = item
        viewModelScope.launch {
            _recommendations.value = bookRepo.findSimilar(item.book)
        }
    }

    fun clearSelection() {
        _selectedBook.value = null
        _recommendations.value = emptyList()
    }

    fun refreshSelected(bookId: Long) {
        viewModelScope.launch {
            val book = bookRepo.getById(bookId) ?: return@launch
            val loc = bookRepo.search(book.title).find { it.book.id == bookId }
            if (loc != null) {
                _selectedBook.value = loc
            }
            _results.value = bookRepo.search(_query.value)
        }
    }

    class Factory(private val app: FamilyLibraryApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(app.serviceLocator.bookRepository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    app: FamilyLibraryApplication,
    isAdmin: Boolean,
    onBookClick: (Long) -> Unit,
    vm: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = SearchViewModel.Factory(app)),
) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val selected by vm.selectedBook.collectAsState()
    val recommendations by vm.recommendations.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("查找图书") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.search(it) },
                placeholder = { Text("搜索书名、作者、ISBN、系列…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (isAdmin) {
                Text(
                    "管理员：选中图书后可移动或归档",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            selected?.let { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onBookClick(item.book.id) },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BookCover(
                                item.book.coverUri,
                                item.book.title,
                                coverStatus = item.book.coverStatus,
                                modifier = Modifier.size(60.dp, 84.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.book.title, style = MaterialTheme.typography.titleMedium)
                                if (item.book.author.isNotBlank()) Text("作者：${item.book.author}")
                                if (item.bookshelfName != null) {
                                    Text(
                                        "位置：${item.bookshelfName} / ${item.shelfRowName ?: ""}",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Text("未上架", color = MaterialTheme.colorScheme.error)
                                }
                                if (item.book.isbn.isNotBlank()) Text("ISBN：${item.book.isbn}")
                            }
                        }
                        BookMoveActions(
                            bookId = item.book.id,
                            currentRowId = item.book.shelfRowId,
                            isAdmin = isAdmin,
                            app = app,
                            onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                            onMoved = { vm.refreshSelected(item.book.id) },
                        )
                        if (recommendations.isNotEmpty()) {
                            Text("相关推荐", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                            recommendations.forEach { rec ->
                                Text(
                                    "· ${rec.title}" + if (rec.author.isNotBlank()) " — ${rec.author}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .clickable { onBookClick(rec.id) }
                                        .padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results, key = { it.book.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectBook(item) },
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BookCover(
                                item.book.coverUri,
                                item.book.title,
                                coverStatus = item.book.coverStatus,
                                modifier = Modifier.size(48.dp, 64.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                BookTitleText(item.book.title)
                                if (item.book.author.isNotBlank()) {
                                    Text(item.book.author, style = MaterialTheme.typography.bodySmall)
                                }
                                val loc = if (item.bookshelfName != null) {
                                    "${item.bookshelfName} / ${item.shelfRowName}"
                                } else "未上架"
                                Text(loc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
