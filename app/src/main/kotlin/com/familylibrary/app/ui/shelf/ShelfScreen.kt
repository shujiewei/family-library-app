package com.familylibrary.app.ui.shelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.ArchiveConfig
import com.familylibrary.app.data.entity.BookWithLocation
import com.familylibrary.app.data.preferences.ShelfDisplayMode
import com.familylibrary.app.data.repository.BatchAddResult
import com.familylibrary.app.ui.admin.AdminModeController
import com.familylibrary.app.ui.components.BatchAddResultDialog
import com.familylibrary.app.ui.components.BatchBookDialog
import com.familylibrary.app.ui.components.BookCover
import com.familylibrary.app.ui.components.BookFormDialog
import com.familylibrary.app.ui.components.BookSpine
import com.familylibrary.app.ui.components.BookTitleText
import com.familylibrary.app.ui.components.toBook
import com.familylibrary.app.ui.components.toFormState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShelfScreen(
    isAdmin: Boolean,
    app: FamilyLibraryApplication,
    onBookClick: (Long) -> Unit,
    onScanBatch: (rowId: Long, locationLabel: String) -> Unit,
    onScanOrganize: (rowId: Long, locationLabel: String) -> Unit,
    adminController: AdminModeController = app.serviceLocator.adminModeController,
    vm: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory(app)),
) {
    fun touchAdmin() {
        if (isAdmin) adminController.extend()
    }
    val uiState by vm.uiState.collectAsState()
    val rows by vm.rows.collectAsState()
    val books by vm.books.collectAsState()
    val moveTargets by vm.moveTargets.collectAsState()

    var showAddShelf by remember { mutableStateOf(false) }
    var showAddRow by remember { mutableStateOf(false) }
    var showAddBook by remember { mutableStateOf(false) }
    var showBatchAdd by remember { mutableStateOf(false) }
    var isBatchAdding by remember { mutableStateOf(false) }
    var batchAddResult by remember { mutableStateOf<BatchAddResult?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<com.familylibrary.app.data.entity.Book?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("书架管理")
                        if (uiState.currentLocationLabel.isNotBlank()) {
                            Text(
                                uiState.currentLocationLabel,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    if (isAdmin && uiState.isSelectionMode && uiState.selectedBookIds.size == 1) {
                        IconButton(onClick = {
                            touchAdmin()
                            val id = uiState.selectedBookIds.first()
                            editingBook = books.find { it.book.id == id }?.book
                            vm.clearSelection()
                        }) {
                            Icon(Icons.Default.Edit, "编辑")
                        }
                    }
                    if (isAdmin && uiState.isSelectionMode) {
                        IconButton(onClick = { touchAdmin(); vm.selectAll() }) {
                            Icon(Icons.Default.SelectAll, "全选")
                        }
                        IconButton(
                            onClick = { touchAdmin(); showMoveDialog = true },
                            enabled = uiState.selectedBookIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.DriveFileMove, "移动")
                        }
                        if (!uiState.isArchiveShelf) {
                            IconButton(
                                onClick = { touchAdmin(); vm.archiveSelectedBooks() },
                                enabled = uiState.selectedBookIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Archive, "归档")
                            }
                        }
                        IconButton(
                            onClick = { touchAdmin(); vm.deleteSelectedBooks() },
                            enabled = uiState.selectedBookIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Delete, "删除")
                        }
                        TextButton(onClick = { vm.clearSelection() }) { Text("取消") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (isAdmin && uiState.selectedRowId != null && !uiState.isSelectionMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FloatingActionButton(
                        onClick = {
                            touchAdmin()
                            val rowId = uiState.selectedRowId ?: return@FloatingActionButton
                            onScanOrganize(rowId, uiState.currentLocationLabel)
                        },
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Default.DriveFileMove, "扫码整理") }
                    if (!uiState.isArchiveShelf) {
                        FloatingActionButton(onClick = { touchAdmin(); showBatchAdd = true },
                            modifier = Modifier.size(48.dp),
                        ) { Text("批", style = MaterialTheme.typography.labelMedium) }
                        FloatingActionButton(onClick = { touchAdmin(); showAddBook = true },
                            modifier = Modifier.size(48.dp),
                        ) { Icon(Icons.Default.Add, "手动录入") }
                        ExtendedFloatingActionButton(
                            onClick = {
                                touchAdmin()
                                val rowId = uiState.selectedRowId ?: return@ExtendedFloatingActionButton
                                onScanBatch(rowId, uiState.currentLocationLabel)
                            },
                            icon = { Icon(Icons.Default.QrCodeScanner, null) },
                            text = { Text("扫码录入") },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(uiState.bookshelves) { shelf ->
                        val isArchive = ArchiveConfig.isArchiveShelf(shelf.name)
                        FilterChip(
                            selected = uiState.selectedBookshelfId == shelf.id,
                            onClick = { touchAdmin(); vm.selectBookshelf(shelf.id) },
                            label = {
                                Text(
                                    if (isArchive) "📦 ${shelf.name}" else shelf.name,
                                    fontWeight = if (isArchive) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
                if (isAdmin) {
                    IconButton(onClick = { showAddShelf = true }) {
                        Icon(Icons.Default.Add, "新建书架")
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!uiState.isArchiveShelf) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(rows) { row ->
                            FilterChip(
                                selected = uiState.selectedRowId == row.id,
                                onClick = { touchAdmin(); vm.selectRow(row.id) },
                                label = { Text(row.name) },
                            )
                        }
                    }
                    if (isAdmin && uiState.selectedBookshelfId != null) {
                        IconButton(onClick = { showAddRow = true }) {
                            Icon(Icons.Default.Add, "新建排")
                        }
                    }
                } else {
                    Text(
                        "归档区 · 扫码整理可将书移入此处",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            if (uiState.selectedRowId == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("请选择书架和排", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (uiState.isArchiveShelf) "归档区暂无图书" else "此排暂无图书",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isAdmin && uiState.selectedRowId != null) {
                            if (uiState.isArchiveShelf) {
                                Button(
                                    onClick = {
                                        val rowId = uiState.selectedRowId ?: return@Button
                                        onScanOrganize(rowId, uiState.currentLocationLabel)
                                    },
                                ) {
                                    Icon(Icons.Default.DriveFileMove, null, Modifier.size(18.dp))
                                    Text("扫码整理", modifier = Modifier.padding(start = 8.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val rowId = uiState.selectedRowId ?: return@Button
                                        onScanBatch(rowId, uiState.currentLocationLabel)
                                    },
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp))
                                    Text("扫码录入", modifier = Modifier.padding(start = 8.dp))
                                }
                                Text(
                                    "或点右下角更多录入方式",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else if (uiState.displayMode == ShelfDisplayMode.SPINE) {
                Column(Modifier.fillMaxSize()) {
                    ShelfDisplayModeChips(
                        mode = uiState.displayMode,
                        onModeChange = vm::setDisplayMode,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        items(books, key = { it.book.id }) { item ->
                            BookSpineItem(
                                item = item,
                                isSelected = item.book.id in uiState.selectedBookIds,
                                isSelectionMode = uiState.isSelectionMode,
                                onClick = {
                                    if (uiState.isSelectionMode) vm.toggleBookSelection(item.book.id)
                                    else onBookClick(item.book.id)
                                },
                                onLongClick = {
                                    if (isAdmin) {
                                        if (!uiState.isSelectionMode) vm.enterSelectionMode()
                                        vm.toggleBookSelection(item.book.id)
                                    }
                                },
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .padding(horizontal = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ShelfDisplayModeChips(
                        mode = uiState.displayMode,
                        onModeChange = vm::setDisplayMode,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(100.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        items(books, key = { it.book.id }) { item ->
                            BookGridItem(
                                item = item,
                                isSelected = item.book.id in uiState.selectedBookIds,
                                isSelectionMode = uiState.isSelectionMode,
                                onClick = {
                                    if (uiState.isSelectionMode) vm.toggleBookSelection(item.book.id)
                                    else onBookClick(item.book.id)
                                },
                                onLongClick = {
                                    if (isAdmin) {
                                        if (!uiState.isSelectionMode) vm.enterSelectionMode()
                                        vm.toggleBookSelection(item.book.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddShelf) {
        NameDialog("新建书架", onDismiss = { showAddShelf = false }) { name ->
            vm.createBookshelf(name)
            showAddShelf = false
        }
    }
    if (showAddRow) {
        NameDialog("新建排", onDismiss = { showAddRow = false }) { name ->
            vm.createRow(name)
            showAddRow = false
        }
    }
    if (showAddBook) {
        BookFormDialog(
            "录入图书",
            isbnLookup = app.serviceLocator.isbnLookupService,
            onDismiss = { showAddBook = false },
        ) { form ->
            vm.addBook(form.toBook())
            showAddBook = false
        }
    }
    if (showBatchAdd) {
        BatchBookDialog(
            isLoading = isBatchAdding,
            onDismiss = { if (!isBatchAdding) showBatchAdd = false },
            onConfirm = { lines ->
                isBatchAdding = true
                vm.addBooksBatch(lines) { result ->
                    isBatchAdding = false
                    showBatchAdd = false
                    batchAddResult = result
                }
            },
        )
    }
    batchAddResult?.let { result ->
        BatchAddResultDialog(result) { batchAddResult = null }
    }
    editingBook?.let { book ->
        BookFormDialog(
            "编辑图书",
            initial = book.toFormState(),
            isbnLookup = app.serviceLocator.isbnLookupService,
            onDismiss = { editingBook = null },
            onConfirm = { form ->
                vm.updateBook(form.toBook(book))
                editingBook = null
            },
        )
    }
    if (showMoveDialog) {
        MoveTargetDialog(
            title = if (uiState.isArchiveShelf) "移出归档到" else "移动到",
            targets = moveTargets,
            currentRowId = uiState.selectedRowId,
            onDismiss = { showMoveDialog = false },
            onSelect = { rowId ->
                vm.moveSelectedBooks(rowId)
                showMoveDialog = false
            },
        )
    }
}

@Composable
private fun ShelfDisplayModeChips(
    mode: ShelfDisplayMode,
    onModeChange: (ShelfDisplayMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = mode == ShelfDisplayMode.SPINE,
            onClick = { onModeChange(ShelfDisplayMode.SPINE) },
            label = { Text("书脊") },
            leadingIcon = { Icon(Icons.Default.MenuBook, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = mode == ShelfDisplayMode.COVER,
            onClick = { onModeChange(ShelfDisplayMode.COVER) },
            label = { Text("封面") },
            leadingIcon = { Icon(Icons.Default.GridView, null, Modifier.size(18.dp)) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookSpineItem(
    item: BookWithLocation,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        BookSpine(
            title = item.book.title,
            bookId = item.book.id,
            author = item.book.author,
            isSelected = isSelectionMode && isSelected,
        )
        if (isSelectionMode && isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    item: BookWithLocation,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BookCover(
                    coverUri = item.book.coverUri,
                    title = item.book.title,
                    coverStatus = item.book.coverStatus,
                    modifier = Modifier.size(80.dp, 110.dp),
                )
                BookTitleText(item.book.title, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
            if (isSelectionMode && isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
