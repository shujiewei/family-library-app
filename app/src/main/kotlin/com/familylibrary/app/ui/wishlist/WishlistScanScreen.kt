package com.familylibrary.app.ui.wishlist

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.cover.CoverService
import com.familylibrary.app.data.cover.IsbnLookupService
import com.familylibrary.app.data.entity.WishlistItem
import com.familylibrary.app.data.repository.WishlistRepository
import com.familylibrary.app.ui.components.CameraPermissionGate
import com.familylibrary.app.ui.scan.IsbnBarcodeScanner
import com.familylibrary.app.util.ScanFeedback
import com.familylibrary.app.util.hasValidTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WishlistScanItem(
    val isbn: String,
    val title: String,
    val author: String = "",
    val isLookingUpTitle: Boolean = false,
    val needsManualTitle: Boolean = false,
    val saved: Boolean = false,
)

data class WishlistScanUiState(
    val sessionItems: List<WishlistScanItem> = emptyList(),
    val lastMessage: String? = null,
)

class WishlistScanViewModel(
    private val wishlistRepo: WishlistRepository,
    private val lookup: IsbnLookupService,
) : ViewModel() {

    private val _state = MutableStateFlow(WishlistScanUiState())
    val state: StateFlow<WishlistScanUiState> = _state.asStateFlow()

    private val seenIsbns = mutableSetOf<String>()
    private var lastScanKey = ""
    private var lastScanAt = 0L

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

        viewModelScope.launch(Dispatchers.IO) {
            val existing = wishlistRepo.findByIsbn(isbn)
            if (existing != null) {
                _state.update {
                    it.copy(
                        lastMessage = "已在待购书单：${existing.title}",
                        sessionItems = listOf(
                            WishlistScanItem(isbn, existing.title, existing.author, saved = true),
                        ) + it.sessionItems,
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    sessionItems = listOf(
                        WishlistScanItem(isbn = isbn, title = "", isLookingUpTitle = true),
                    ) + it.sessionItems,
                    lastMessage = "正在查询书名…",
                )
            }

            try {
                val info = lookup.lookup(isbn)
                if (info != null && hasValidTitle(info.title, isbn)) {
                    wishlistRepo.add(
                        WishlistItem(
                            title = info.title,
                            author = info.author,
                            isbn = isbn,
                        ),
                    )
                    _state.update { s ->
                        s.copy(
                            sessionItems = s.sessionItems.map { item ->
                                if (item.isbn != isbn) item
                                else WishlistScanItem(
                                    isbn = isbn,
                                    title = info.title,
                                    author = info.author,
                                    saved = true,
                                )
                            },
                            lastMessage = "已加入待购：${info.title}",
                        )
                    }
                } else {
                    _state.update { s ->
                        s.copy(
                            sessionItems = s.sessionItems.map { item ->
                                if (item.isbn != isbn) item
                                else item.copy(isLookingUpTitle = false, needsManualTitle = true)
                            },
                            lastMessage = "未找到书名（请检查网络或手动输入）：$isbn",
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update { s ->
                    s.copy(
                        sessionItems = s.sessionItems.map { item ->
                            if (item.isbn != isbn) item
                            else item.copy(isLookingUpTitle = false, needsManualTitle = true)
                        },
                        lastMessage = "书名查询失败，请手动输入：$isbn",
                    )
                }
            }
        }
    }

    fun saveManualTitle(isbn: String, title: String, author: String = "") {
        if (!hasValidTitle(title, isbn)) return
        viewModelScope.launch(Dispatchers.IO) {
            wishlistRepo.add(
                WishlistItem(title = title.trim(), author = author.trim(), isbn = isbn),
            )
            _state.update { s ->
                s.copy(
                    sessionItems = s.sessionItems.map { item ->
                        if (item.isbn != isbn) item
                        else item.copy(
                            title = title.trim(),
                            author = author.trim(),
                            needsManualTitle = false,
                            saved = true,
                        )
                    },
                    lastMessage = "已加入待购：$title",
                )
            }
        }
    }

    class Factory(private val app: FamilyLibraryApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val sl = app.serviceLocator
            return WishlistScanViewModel(sl.wishlistRepository, sl.isbnLookupService) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScanScreen(
    app: FamilyLibraryApplication,
    onBack: () -> Unit,
    vm: WishlistScanViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = WishlistScanViewModel.Factory(app),
    ),
) {
    val ui by vm.state.collectAsState()
    var manualIsbn by remember { mutableStateOf<String?>(null) }
    var manualTitle by remember { mutableStateOf("") }
    var manualAuthor by remember { mutableStateOf("") }
    val view = LocalView.current

    androidx.compose.runtime.LaunchedEffect(ui.lastMessage) {
        val msg = ui.lastMessage ?: return@LaunchedEffect
        when {
            msg.startsWith("已加入") || msg.startsWith("已在待购") ->
                ScanFeedback.onSuccess(view)
            msg.startsWith("未找到") -> ScanFeedback.onError(view)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码加入待购") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "在书店或图书馆看到想买的书，扫 ISBN 即可加入待购书单",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                CameraPermissionGate(Modifier.fillMaxSize()) {
                    IsbnBarcodeScanner(
                        enabled = true,
                        onIsbnDetected = { vm.onIsbnScanned(it) },
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

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(ui.sessionItems, key = { it.isbn }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    item.isLookingUpTitle -> "查询书名中…"
                                    item.title.isNotBlank() -> item.title
                                    else -> "未找到书名"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                buildString {
                                    append(item.isbn)
                                    if (item.author.isNotBlank()) append(" · ${item.author}")
                                    if (item.saved) append(" · 已保存")
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (item.isLookingUpTitle) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        if (item.needsManualTitle) {
                            IconButton(onClick = {
                                manualIsbn = item.isbn
                                manualTitle = ""
                                manualAuthor = ""
                            }) {
                                Icon(Icons.Default.Edit, "手动输入")
                            }
                        }
                    }
                }
            }
        }
    }

    manualIsbn?.let { isbn ->
        AlertDialog(
            onDismissRequest = { manualIsbn = null },
            title = { Text("手动输入书名") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ISBN: $isbn", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("书名 *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = manualAuthor,
                        onValueChange = { manualAuthor = it },
                        label = { Text("作者") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveManualTitle(isbn, manualTitle, manualAuthor)
                        manualIsbn = null
                    },
                    enabled = manualTitle.isNotBlank(),
                ) { Text("加入待购") }
            },
            dismissButton = {
                TextButton(onClick = { manualIsbn = null }) { Text("取消") }
            },
        )
    }
}
