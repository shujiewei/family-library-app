package com.familylibrary.app.ui.wishlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.entity.WishlistItem
import com.familylibrary.app.data.repository.WishlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WishlistViewModel(private val repo: WishlistRepository) : ViewModel() {
    val items = repo.observeAll()
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode = _selectionMode.asStateFlow()

    fun add(title: String, author: String, note: String) = viewModelScope.launch {
        repo.add(WishlistItem(title = title, author = author, note = note))
    }

    fun deleteSelected() = viewModelScope.launch {
        repo.delete(_selectedIds.value.toList())
        clearSelection()
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.let {
            if (id in it) it - id else it + id
        }
    }

    fun enterSelection() { _selectionMode.value = true }
    fun clearSelection() {
        _selectedIds.value = emptySet()
        _selectionMode.value = false
    }

    class Factory(private val app: FamilyLibraryApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WishlistViewModel(app.serviceLocator.wishlistRepository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WishlistScreen(
    app: FamilyLibraryApplication,
    onScanWishlist: () -> Unit = {},
    vm: WishlistViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = WishlistViewModel.Factory(app)),
) {
    val items by vm.items.collectAsState(initial = emptyList())
    val selectedIds by vm.selectedIds.collectAsState()
    val selectionMode by vm.selectionMode.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待购书单") },
                actions = {
                    if (selectionMode && selectedIds.isNotEmpty()) {
                        IconButton(onClick = { vm.deleteSelected() }) {
                            Icon(Icons.Default.Delete, "删除")
                        }
                        TextButton(onClick = { vm.clearSelection() }) { Text("取消") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!selectionMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FloatingActionButton(
                        onClick = onScanWishlist,
                        modifier = Modifier,
                    ) {
                        Icon(Icons.Default.QrCodeScanner, "扫码加入")
                    }
                    FloatingActionButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, "手动添加")
                    }
                }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("暂无待购图书", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) vm.toggleSelection(item.id)
                                },
                                onLongClick = {
                                    vm.enterSelection()
                                    vm.toggleSelection(item.id)
                                },
                            ),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectionMode) {
                                Checkbox(
                                    checked = item.id in selectedIds,
                                    onCheckedChange = { vm.toggleSelection(item.id) },
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                if (item.author.isNotBlank()) Text(item.author, style = MaterialTheme.typography.bodySmall)
                                if (item.isbn.isNotBlank()) {
                                    Text("ISBN ${item.isbn}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var author by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加到待购书单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("书名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("作者") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.add(title, author, note); showAdd = false }, enabled = title.isNotBlank()) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}
