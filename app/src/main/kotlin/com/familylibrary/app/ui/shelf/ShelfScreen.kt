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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.familylibrary.app.data.entity.Bookshelf
import com.familylibrary.app.data.entity.ShelfRow
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
    onRequestAdmin: () -> Unit = {},
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
    var editingShelf by remember { mutableStateOf<Bookshelf?>(null) }
    var editingRow by remember { mutableStateOf<ShelfRow?>(null) }
    var pendingDeleteShelfId by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteRowId by remember { mutableStateOf<Long?>(null) }
    var showAddBook by remember { mutableStateOf(false) }
    var showBatchAdd by remember { mutableStateOf(false) }
    var isBatchAdding by remember { mutableStateOf(false) }
    var batchAddResult by remember { mutableStateOf<BatchAddResult?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<com.familylibrary.app.data.entity.Book?>(null) }

    val selectedShelf = uiState.bookshelves.find { it.id == uiState.selectedBookshelfId }
    val selectedRow = rows.find { it.id == uiState.selectedRowId }
    val locationDescription = when {
        selectedRow?.description?.isNotBlank() == true -> selectedRow.description
        selectedShelf?.description?.isNotBlank() == true -> selectedShelf.description
        else -> ""
    }

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
                        if (locationDescription.isNotBlank()) {
                            Text(
                                locationDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
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
                    }
                },
            )
        },
        bottomBar = {
            when {
                isAdmin && uiState.isSelectionMode -> {
                    ShelfSelectionActionBar(
                        selectedCount = uiState.selectedBookIds.size,
                        isArchiveShelf = uiState.isArchiveShelf,
                        onMove = { touchAdmin(); showMoveDialog = true },
                        onArchive = { touchAdmin(); vm.archiveSelectedBooks() },
                        onDelete = { touchAdmin(); vm.deleteSelectedBooks() },
                        onCancel = { vm.clearSelection() },
                    )
                }
                isAdmin && uiState.selectedRowId != null -> {
                    ShelfPrimaryActionBar(
                        isArchiveShelf = uiState.isArchiveShelf,
                        onScanBatch = {
                            touchAdmin()
                            val rowId = uiState.selectedRowId ?: return@ShelfPrimaryActionBar
                            onScanBatch(rowId, uiState.currentLocationLabel)
                        },
                        onScanOrganize = {
                            touchAdmin()
                            val rowId = uiState.selectedRowId ?: return@ShelfPrimaryActionBar
                            onScanOrganize(rowId, uiState.currentLocationLabel)
                        },
                        onBatchMove = {
                            touchAdmin()
                            vm.enterSelectionMode()
                        },
                        onManualAdd = { touchAdmin(); showAddBook = true },
                        onBatchText = { touchAdmin(); showBatchAdd = true },
                    )
                }
                !isAdmin && uiState.selectedRowId != null -> {
                    ShelfAdminHintBar(onRequestAdmin = onRequestAdmin)
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
                    if (uiState.selectedBookshelfId != null) {
                        IconButton(onClick = {
                            touchAdmin()
                            editingShelf = selectedShelf
                        }) {
                            Icon(Icons.Default.Edit, "编辑书架")
                        }
                    }
                    IconButton(onClick = { touchAdmin(); showAddShelf = true }) {
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
                        if (uiState.selectedRowId != null) {
                            IconButton(onClick = {
                                touchAdmin()
                                editingRow = selectedRow
                            }) {
                                Icon(Icons.Default.Edit, "编辑排")
                            }
                        }
                        IconButton(onClick = { touchAdmin(); showAddRow = true }) {
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text("请选择书架和排", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isAdmin) {
                            Text(
                                "或点上方 ＋ 新建书架 / 新建排",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "查看图书无需管理员；录入请前往设置开启管理员",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                        if (isAdmin) {
                            Text(
                                "请使用下方操作栏开始录入",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (uiState.displayMode == ShelfDisplayMode.SPINE) {
                Column(Modifier.fillMaxSize()) {
                    ShelfDisplayModeChips(
                        mode = uiState.displayMode,
                        onModeChange = vm::setDisplayMode,
                    )
                    if (isAdmin && !uiState.isSelectionMode) {
                        Text(
                            "提示：点「批量移动」或长按图书多选",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                    if (uiState.isSelectionMode) {
                        Text(
                            if (uiState.selectedBookIds.isEmpty()) {
                                "请选择要操作的图书"
                            } else {
                                "已选 ${uiState.selectedBookIds.size} 本，使用底部按钮移动/删除"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
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
                    if (isAdmin && !uiState.isSelectionMode) {
                        Text(
                            "提示：点「批量移动」或长按图书多选",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                    if (uiState.isSelectionMode) {
                        Text(
                            if (uiState.selectedBookIds.isEmpty()) {
                                "请选择要操作的图书"
                            } else {
                                "已选 ${uiState.selectedBookIds.size} 本，使用底部按钮移动/删除"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
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
        ShelfManageDialog(
            title = "新建书架",
            onDismiss = { showAddShelf = false },
            onConfirm = { name, description ->
                vm.createBookshelf(name, description)
                showAddShelf = false
            },
        )
    }
    if (showAddRow) {
        ShelfManageDialog(
            title = "新建排",
            onDismiss = { showAddRow = false },
            onConfirm = { name, description ->
                vm.createRow(name, description)
                showAddRow = false
            },
        )
    }
    editingShelf?.let { shelf ->
        val isArchive = ArchiveConfig.isArchiveShelf(shelf.name)
        ShelfManageDialog(
            title = if (isArchive) "编辑归档书架" else "编辑书架",
            initialName = shelf.name,
            initialDescription = shelf.description,
            allowRename = !isArchive,
            allowDelete = !isArchive,
            onDismiss = { editingShelf = null },
            onConfirm = { name, description ->
                vm.updateBookshelf(shelf.id, name, description)
                editingShelf = null
            },
            onDelete = { pendingDeleteShelfId = shelf.id; editingShelf = null },
        )
    }
    editingRow?.let { row ->
        val isArchiveRow = uiState.isArchiveShelf
        ShelfManageDialog(
            title = if (isArchiveRow) "编辑归档排" else "编辑排",
            initialName = row.name,
            initialDescription = row.description,
            allowRename = !isArchiveRow,
            allowDelete = !isArchiveRow,
            onDismiss = { editingRow = null },
            onConfirm = { name, description ->
                vm.updateRow(row.id, name, description)
                editingRow = null
            },
            onDelete = { pendingDeleteRowId = row.id; editingRow = null },
        )
    }
    pendingDeleteShelfId?.let { shelfId ->
        val shelf = uiState.bookshelves.find { it.id == shelfId }
        AlertDialog(
            onDismissRequest = { pendingDeleteShelfId = null },
            title = { Text("删除书架") },
            text = {
                Text("确定删除「${shelf?.name ?: ""}」？其下所有排将被删除，图书将变为未上架。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBookshelf(shelfId)
                    pendingDeleteShelfId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteShelfId = null }) { Text("取消") }
            },
        )
    }
    pendingDeleteRowId?.let { rowId ->
        val row = rows.find { it.id == rowId }
        AlertDialog(
            onDismissRequest = { pendingDeleteRowId = null },
            title = { Text("删除排") },
            text = {
                Text("确定删除「${row?.name ?: ""}」？此排上的图书将变为未上架。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRow(rowId)
                    pendingDeleteRowId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRowId = null }) { Text("取消") }
            },
        )
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
private fun ShelfManageDialog(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    allowRename: Boolean = true,
    allowDelete: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    enabled = allowRename,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述/备注") },
                    placeholder = { Text("如：客厅左侧、儿童绘本区…") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allowDelete && onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("删除", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
