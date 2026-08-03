package com.familylibrary.app.ui.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.ui.admin.AdminModeController
import com.familylibrary.app.data.cover.CoverService
import com.familylibrary.app.data.repository.BookRepository
import com.familylibrary.app.ui.components.CameraPermissionGate
import com.familylibrary.app.ui.components.OrganizeSessionSummaryDialog
import com.familylibrary.app.util.ScanFeedback
import com.familylibrary.app.util.locationLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OrganizeOutcome { MOVED, ALREADY_HERE, NOT_FOUND }

data class OrganizeScanItem(
    val scanIndex: Long,
    val isbn: String,
    val title: String,
    val detail: String,
    val outcome: OrganizeOutcome,
)

data class ScanOrganizeUiState(
    val items: List<OrganizeScanItem> = emptyList(),
    val movedCount: Int = 0,
    val alreadyHereCount: Int = 0,
    val notFoundCount: Int = 0,
    val lastMessage: String? = null,
)

class ScanOrganizeViewModel(
    private val bookRepo: BookRepository,
    private val targetRowId: Long,
    private val targetLabel: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanOrganizeUiState())
    val state: StateFlow<ScanOrganizeUiState> = _state.asStateFlow()

    private var lastScanKey = ""
    private var lastScanAt = 0L
    private var scanCounter = 0L

    fun onIsbnScanned(rawIsbn: String) {
        val isbn = CoverService.normalizeIsbn(rawIsbn)
        if (!CoverService.isValidIsbn(isbn)) return
        val now = System.currentTimeMillis()
        if (isbn == lastScanKey && now - lastScanAt < 2500) return
        lastScanKey = isbn
        lastScanAt = now

        viewModelScope.launch(Dispatchers.IO) {
            val scanIndex = ++scanCounter
            val found = bookRepo.findByIsbn(isbn)
            when {
                found == null -> {
                    _state.update {
                        it.copy(
                            items = listOf(
                                OrganizeScanItem(scanIndex, isbn, "未入库", "书库中无此 ISBN", OrganizeOutcome.NOT_FOUND),
                            ) + it.items,
                            notFoundCount = it.notFoundCount + 1,
                            lastMessage = "未找到：$isbn",
                        )
                    }
                }
                found.book.shelfRowId == targetRowId -> {
                    _state.update {
                        it.copy(
                            items = listOf(
                                OrganizeScanItem(
                                    scanIndex,
                                    isbn,
                                    found.book.title,
                                    "已在目标位置",
                                    OrganizeOutcome.ALREADY_HERE,
                                ),
                            ) + it.items,
                            alreadyHereCount = it.alreadyHereCount + 1,
                            lastMessage = "已在目标位：${found.book.title}",
                        )
                    }
                }
                else -> {
                    val from = found.locationLabel()
                    bookRepo.moveBooks(listOf(found.book.id), targetRowId)
                    _state.update {
                        it.copy(
                            items = listOf(
                                OrganizeScanItem(
                                    scanIndex,
                                    isbn,
                                    found.book.title,
                                    "$from → $targetLabel",
                                    OrganizeOutcome.MOVED,
                                ),
                            ) + it.items,
                            movedCount = it.movedCount + 1,
                            lastMessage = "已移入：${found.book.title}",
                        )
                    }
                }
            }
        }
    }

    fun clearHistory() {
        lastScanKey = ""
        lastScanAt = 0L
        scanCounter = 0L
        _state.value = ScanOrganizeUiState()
    }

    fun hasSessionActivity(): Boolean =
        _state.value.movedCount > 0 ||
            _state.value.alreadyHereCount > 0 ||
            _state.value.notFoundCount > 0

    class Factory(
        private val app: FamilyLibraryApplication,
        private val targetRowId: Long,
        private val targetLabel: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScanOrganizeViewModel(app.serviceLocator.bookRepository, targetRowId, targetLabel) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOrganizeScreen(
    app: FamilyLibraryApplication,
    targetRowId: Long,
    targetLabel: String,
    onBack: () -> Unit,
    adminController: AdminModeController = app.serviceLocator.adminModeController,
    vm: ScanOrganizeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ScanOrganizeViewModel.Factory(app, targetRowId, targetLabel),
    ),
) {
    val ui by vm.state.collectAsState()
    val view = LocalView.current
    var showSummary by remember { mutableStateOf(false) }

    fun tryExit() {
        if (vm.hasSessionActivity()) showSummary = true else onBack()
    }

    BackHandler { tryExit() }

    androidx.compose.runtime.LaunchedEffect(ui.lastMessage) {
        val msg = ui.lastMessage ?: return@LaunchedEffect
        when {
            msg.startsWith("已移入") -> ScanFeedback.onSuccess(view)
            msg.startsWith("未找到") -> ScanFeedback.onError(view)
        }
    }

    if (showSummary) {
        OrganizeSessionSummaryDialog(
            movedCount = ui.movedCount,
            alreadyHereCount = ui.alreadyHereCount,
            notFoundCount = ui.notFoundCount,
            onDismiss = {
                showSummary = false
                onBack()
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码整理") },
                navigationIcon = {
                    IconButton(onClick = { tryExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (ui.items.isNotEmpty()) {
                        TextButton(onClick = { vm.clearHistory() }) { Text("清空记录") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "移入目标：$targetLabel",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "拿出待整理的书，扫 ISBN 即自动移入此排（已移入 ${ui.movedCount} 本）",
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
                        enabled = true,
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

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(ui.items, key = { it.scanIndex }) { item ->
                    val color = when (item.outcome) {
                        OrganizeOutcome.MOVED -> MaterialTheme.colorScheme.primary
                        OrganizeOutcome.ALREADY_HERE -> MaterialTheme.colorScheme.onSurfaceVariant
                        OrganizeOutcome.NOT_FOUND -> MaterialTheme.colorScheme.error
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = color)
                        Text("${item.isbn} · ${item.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
