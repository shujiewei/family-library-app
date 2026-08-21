package com.familylibrary.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.familylibrary.app.ui.admin.AdminModeController
import com.familylibrary.app.data.cover.CoverService
import com.familylibrary.app.data.cover.IsbnTitleLookup
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.repository.BatchAddResult
import com.familylibrary.app.ui.components.BatchAddResultDialog
import com.familylibrary.app.ui.components.CameraPermissionGate
import com.familylibrary.app.data.repository.BatchScanBooksGateway
import com.familylibrary.app.util.hasValidTitle
import com.familylibrary.app.util.locationLabel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ScannedItem(
    val isbn: String,
    val title: String,
    val author: String = "",
    /** 正在同步查询书名 */
    val isLookingUpTitle: Boolean = false,
    /** 网络未查到书名，需手动输入 */
    val needsManualTitle: Boolean = false,
    /** 库中已有，不可重复录入 */
    val isDuplicate: Boolean = false,
    val duplicateDetail: String = "",
)

data class BatchScanUiState(
    val items: List<ScannedItem> = emptyList(),
    val isSaving: Boolean = false,
    val lastMessage: String? = null,
)

class BatchScanViewModel(
    private val bookRepo: BatchScanBooksGateway,
    private val lookup: IsbnTitleLookup,
    private val targetRowId: Long,
    private val targetLabel: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(BatchScanUiState())
    val state: StateFlow<BatchScanUiState> = _state.asStateFlow()

    private val seenIsbns = mutableSetOf<String>()
    private var lastScanKey = ""
    private var lastScanAt = 0L

    val targetDescription: String = targetLabel

    fun onIsbnScanned(rawIsbn: String) {
        val isbn = CoverService.normalizeIsbn(rawIsbn)
        if (!CoverService.isPlausibleIsbn(isbn)) {
            _state.update { it.copy(lastMessage = "条码无法识别为 ISBN：$rawIsbn") }
            return
        }
        val now = System.currentTimeMillis()
        if (isbn == lastScanKey && now - lastScanAt < 2500) return
        lastScanKey = isbn
        lastScanAt = now
        if (!seenIsbns.add(isbn)) {
            _state.update { it.copy(lastMessage = "已扫描过：$isbn") }
            return
        }

        _state.update {
            it.copy(
                items = it.items + ScannedItem(
                    isbn = isbn,
                    title = "",
                    isLookingUpTitle = true,
                ),
                lastMessage = "已识别 $isbn，正在查询…",
            )
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                val existing = bookRepo.findByIsbn(isbn)
                if (existing != null) {
                    val detail = if (existing.book.shelfRowId == targetRowId) {
                        "已在当前排：${existing.book.title}"
                    } else {
                        "已在库（${existing.locationLabel()}）：${existing.book.title}"
                    }
                    _state.update { s ->
                        s.copy(
                            items = s.items.map { item ->
                                if (item.isbn != isbn) item
                                else item.copy(
                                    title = existing.book.title,
                                    author = existing.book.author,
                                    isLookingUpTitle = false,
                                    isDuplicate = true,
                                    duplicateDetail = detail,
                                )
                            },
                            lastMessage = detail,
                        )
                    }
                    return@launch
                }

                val title = withTimeoutOrNull(12_000) { lookup.lookupTitle(isbn) }
                _state.update { s ->
                    s.copy(
                        items = s.items.map { item ->
                            if (item.isbn != isbn) item
                            else if (title != null) {
                                item.copy(
                                    title = title,
                                    isLookingUpTitle = false,
                                    needsManualTitle = false,
                                )
                            } else {
                                item.copy(
                                    isLookingUpTitle = false,
                                    needsManualTitle = true,
                                )
                            }
                        },
                        lastMessage = when {
                            title != null -> "已获取书名：$title"
                            else -> "未找到书名或查询超时，请手动输入：$isbn"
                        },
                    )
                }
            } catch (_: Exception) {
                _state.update { s ->
                    s.copy(
                        items = s.items.map { item ->
                            if (item.isbn != isbn) item
                            else item.copy(
                                isLookingUpTitle = false,
                                needsManualTitle = true,
                            )
                        },
                        lastMessage = "书名查询失败，请手动输入：$isbn",
                    )
                }
            }
        }
    }

    fun updateItemTitle(isbn: String, title: String) {
        _state.update { s ->
            s.copy(
                items = s.items.map { item ->
                    if (item.isbn != isbn) item
                    else item.copy(
                        title = title,
                        needsManualTitle = !hasValidTitle(title, isbn),
                    )
                },
            )
        }
    }

    fun removeItem(isbn: String) {
        seenIsbns.remove(isbn)
        _state.update { it.copy(items = it.items.filter { i -> i.isbn != isbn }) }
    }

    fun clearAll() {
        seenIsbns.clear()
        _state.update { it.copy(items = emptyList(), lastMessage = null) }
    }

    fun readyToSaveCount(): Int =
        _state.value.items.count {
            !it.isDuplicate && hasValidTitle(it.title, it.isbn) && !it.isLookingUpTitle
        }

    fun saveAll(onDone: (BatchAddResult) -> Unit) {
        val items = _state.value.items
        if (items.isEmpty() || _state.value.isSaving) return
        val pending = items.count { it.isLookingUpTitle }
        if (pending > 0) {
            _state.update { it.copy(lastMessage = "还有 $pending 本正在查询书名，请稍候") }
            return
        }
        val invalid = items.filter { !it.isDuplicate && !hasValidTitle(it.title, it.isbn) }
        if (invalid.isNotEmpty()) {
            _state.update {
                it.copy(lastMessage = "${invalid.size} 本缺少书名，请手动填写或移除")
            }
            return
        }
        val toSave = items.filter { !it.isDuplicate }
        if (toSave.isEmpty()) {
            _state.update { it.copy(lastMessage = "没有可录入的新书（均为重复）") }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isSaving = true) }
            val books = toSave.map { item ->
                Book(title = item.title.trim(), author = item.author, isbn = item.isbn)
            }
            val result = bookRepo.addScannedBooks(books, targetRowId)
            _state.update { it.copy(isSaving = false) }
            onDone(result)
        }
    }

    class Factory(
        private val app: FamilyLibraryApplication,
        private val targetRowId: Long,
        private val targetLabel: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val sl = app.serviceLocator
            return BatchScanViewModel(
                sl.bookRepository,
                sl.isbnLookupService,
                targetRowId,
                targetLabel,
            ) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScanScreen(
    app: FamilyLibraryApplication,
    targetRowId: Long,
    targetLabel: String,
    onBack: () -> Unit,
    onSaved: (BatchAddResult) -> Unit,
    adminController: AdminModeController = app.serviceLocator.adminModeController,
    vm: BatchScanViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = BatchScanViewModel.Factory(app, targetRowId, targetLabel),
    ),
) {
    val ui by vm.state.collectAsState()
    var editingIsbn by remember { mutableStateOf<String?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var saveResult by remember { mutableStateOf<BatchAddResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码录入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (ui.items.isNotEmpty()) {
                        TextButton(onClick = { vm.clearAll() }) { Text("清空") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "录入到：$targetLabel",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "连续扫描 ISBN；书名将同步查询（需联网），作者与封面在保存后后台补全",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                CameraPermissionGate(Modifier.fillMaxSize()) {
                    IsbnBarcodeScanner(
                        enabled = !ui.isSaving,
                        onIsbnDetected = {
                            adminController.extend()
                            vm.onIsbnScanned(it)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            ui.lastMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            val duplicateCount = ui.items.count { it.isDuplicate }
            Text(
                buildString {
                    append("已扫描 ${ui.items.size} 本")
                    if (duplicateCount > 0) append("（重复 $duplicateCount）")
                    append(" · 可保存 ${vm.readyToSaveCount()} 本")
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp, 8.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(ui.items, key = { it.isbn }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            when {
                                item.isDuplicate -> Text(
                                    item.title.ifBlank { "已在库" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                item.isLookingUpTitle -> Text("查询书名中…", style = MaterialTheme.typography.bodyMedium)
                                item.title.isNotBlank() -> Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                item.needsManualTitle -> Text(
                                    "未找到书名",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(
                                buildString {
                                    append(item.isbn)
                                    if (item.isDuplicate) {
                                        append(" · ")
                                        append(item.duplicateDetail)
                                    } else {
                                        if (item.author.isNotBlank()) append(" · ${item.author}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (item.isLookingUpTitle) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        if (item.needsManualTitle || (item.title.isNotBlank() && !item.isDuplicate)) {
                            IconButton(onClick = {
                                editingIsbn = item.isbn
                                editTitle = item.title
                            }) {
                                Icon(Icons.Default.Edit, "编辑书名")
                            }
                        }
                        IconButton(onClick = { vm.removeItem(item.isbn) }) {
                            Icon(Icons.Default.Delete, "移除")
                        }
                    }
                }
            }

            Button(
                onClick = { vm.saveAll { saveResult = it } },
                enabled = vm.readyToSaveCount() > 0 && !ui.isSaving,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(
                    if (ui.isSaving) "保存中…" else "完成录入（${vm.readyToSaveCount()} 本）",
                )
            }
            Text(
                "无书名的图书无法保存；可点编辑手动填写书名",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    editingIsbn?.let { isbn ->
        AlertDialog(
            onDismissRequest = { editingIsbn = null },
            title = { Text("手动输入书名") },
            text = {
                Column {
                    Text("ISBN: $isbn", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("书名 *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.updateItemTitle(isbn, editTitle)
                        editingIsbn = null
                    },
                    enabled = editTitle.isNotBlank(),
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingIsbn = null }) { Text("取消") }
            },
        )
    }

    saveResult?.let { result ->
        BatchAddResultDialog(result) {
            saveResult = null
            onSaved(result)
        }
    }
}
