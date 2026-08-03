package com.familylibrary.app.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.repository.BookRepository
import kotlinx.coroutines.flow.Flow

enum class BrowseMode(val label: String) {
    AUTHOR("按作者"),
    SERIES("按系列"),
    AGE("按年龄"),
    CATEGORY("按分类"),
    LEXILE("蓝思值"),
}

class BrowseViewModel(private val bookRepo: BookRepository) : ViewModel() {
    fun observeAuthors() = bookRepo.observeAuthors()
    fun observeSeries() = bookRepo.observeSeries()
    fun observeAges() = bookRepo.observeAges()
    fun observeCategories() = bookRepo.observeCategories()
    fun observeEnglishByLexile() = bookRepo.observeEnglishByLexile()
    fun observeByAuthor(a: String): Flow<List<Book>> = bookRepo.observeByAuthor(a)
    fun observeBySeries(s: String): Flow<List<Book>> = bookRepo.observeBySeries(s)
    fun observeByAge(a: String): Flow<List<Book>> = bookRepo.observeByAge(a)
    fun observeByCategory(c: String): Flow<List<Book>> = bookRepo.observeByCategory(c)

    class Factory(private val app: FamilyLibraryApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BrowseViewModel(app.serviceLocator.bookRepository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    app: FamilyLibraryApplication,
    onBookClick: (Long) -> Unit,
    vm: BrowseViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = BrowseViewModel.Factory(app)),
) {
    var mode by remember { mutableStateOf(BrowseMode.AUTHOR) }
    var selectedKey by remember { mutableStateOf<String?>(null) }

    val authors by vm.observeAuthors().collectAsState(initial = emptyList())
    val series by vm.observeSeries().collectAsState(initial = emptyList())
    val ages by vm.observeAges().collectAsState(initial = emptyList())
    val categories by vm.observeCategories().collectAsState(initial = emptyList())
    val lexileBooks by vm.observeEnglishByLexile().collectAsState(initial = emptyList())

    val keys = when (mode) {
        BrowseMode.AUTHOR -> authors
        BrowseMode.SERIES -> series
        BrowseMode.AGE -> ages
        BrowseMode.CATEGORY -> categories
        BrowseMode.LEXILE -> emptyList()
    }

    val books by remember(selectedKey, mode) {
        when {
            mode == BrowseMode.LEXILE -> vm.observeEnglishByLexile()
            selectedKey == null -> kotlinx.coroutines.flow.flowOf(emptyList())
            mode == BrowseMode.AUTHOR -> vm.observeByAuthor(selectedKey!!)
            mode == BrowseMode.SERIES -> vm.observeBySeries(selectedKey!!)
            mode == BrowseMode.AGE -> vm.observeByAge(selectedKey!!)
            else -> vm.observeByCategory(selectedKey!!)
        }
    }.collectAsState(initial = emptyList())

    Scaffold(
        topBar = { TopAppBar(title = { Text("分类浏览") }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(BrowseMode.entries.size) { i ->
                    val m = BrowseMode.entries[i]
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m; selectedKey = null },
                        label = { Text(m.label) },
                    )
                }
            }

            if (mode != BrowseMode.LEXILE) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(keys.size) { i ->
                        val key = keys[i]
                        FilterChip(
                            selected = selectedKey == key,
                            onClick = { selectedKey = key },
                            label = { Text(key) },
                        )
                    }
                }
            }

            val displayBooks = if (mode == BrowseMode.LEXILE) lexileBooks else books

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(displayBooks, key = { it.id }) { book ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onBookClick(book.id) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(book.title, style = MaterialTheme.typography.bodyLarge)
                        val meta = buildList {
                            if (book.author.isNotBlank()) add(book.author)
                            if (book.lexileLevel.isNotBlank()) add("Lexile ${book.lexileLevel}")
                            if (book.recommendedAge.isNotBlank()) add(book.recommendedAge)
                        }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
