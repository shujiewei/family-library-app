package com.familylibrary.app.ui.reading

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.entity.CategoryReadingStats
import com.familylibrary.app.data.entity.FamilyMember
import com.familylibrary.app.data.entity.MemberReadingStats
import com.familylibrary.app.data.entity.ReadingRecord
import com.familylibrary.app.data.entity.ReadingRecordWithBook
import com.familylibrary.app.data.repository.BookRepository
import com.familylibrary.app.data.repository.MemberRepository
import com.familylibrary.app.data.repository.ReadingRepository
import com.familylibrary.app.ui.components.formatWordCount
import com.familylibrary.app.ui.theme.MemberColors
import com.familylibrary.app.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReadingViewModel(
    private val readingRepo: ReadingRepository,
    private val memberRepo: MemberRepository,
    private val bookRepo: BookRepository,
) : ViewModel() {
    val members = memberRepo.observeAll()
    val memberStats = readingRepo.observeMemberStats()
    val allRecords = readingRepo.observeAll()

    private val _selectedMemberId = MutableStateFlow<Long?>(null)
    val selectedMemberId = _selectedMemberId.asStateFlow()

    fun selectMember(id: Long?) { _selectedMemberId.value = id }

    fun categoryStats(memberId: Long) = readingRepo.observeCategoryStats(memberId)

    fun memberRecords(memberId: Long) = readingRepo.observeByMember(memberId)

    fun addRecord(memberId: Long, bookId: Long, notes: String) = viewModelScope.launch {
        readingRepo.addRecord(
            ReadingRecord(
                memberId = memberId,
                bookId = bookId,
                finishDate = DateUtil.today(),
                notes = notes,
            )
        )
    }

    fun addMember(name: String) = viewModelScope.launch {
        val count = memberRepo.observeAll().first().size
        memberRepo.add(name, count % com.familylibrary.app.ui.theme.MemberColors.size)
    }

    suspend fun searchBooks(q: String) = bookRepo.search(q)

    class Factory(private val app: FamilyLibraryApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val sl = app.serviceLocator
            return ReadingViewModel(sl.readingRepository, sl.memberRepository, sl.bookRepository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    app: FamilyLibraryApplication,
    vm: ReadingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = ReadingViewModel.Factory(app)),
) {
    val members by vm.members.collectAsState(initial = emptyList())
    val stats by vm.memberStats.collectAsState(initial = emptyList())
    val selectedId by vm.selectedMemberId.collectAsState()
    val activeMemberId = selectedId ?: members.firstOrNull()?.id

    val records by remember(activeMemberId) {
        if (activeMemberId != null) vm.memberRecords(activeMemberId) else vm.allRecords
    }.collectAsState(initial = emptyList())

    val categoryStats by remember(activeMemberId) {
        if (activeMemberId != null) vm.categoryStats(activeMemberId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    var showAddRecord by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("阅读记录") }) },
        floatingActionButton = {
            if (activeMemberId != null) {
                FloatingActionButton(onClick = { showAddRecord = true }) {
                    Icon(Icons.Default.Add, "添加阅读记录")
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text("阅读量统计", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp))
            stats.forEach { s ->
                MemberStatsCard(s, members)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                members.forEach { m ->
                    FilterChip(
                        selected = activeMemberId == m.id,
                        onClick = { vm.selectMember(m.id) },
                        label = { Text(m.name) },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showAddMember = true },
                    label = { Text("+") },
                )
            }

            if (categoryStats.isNotEmpty()) {
                Text("分类统计", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
                categoryStats.forEach { cs ->
                    Text(
                        "${cs.category}：${cs.bookCount} 本，${formatWordCount(cs.totalWordCount.toInt())}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }

            Text("阅读记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp))
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(records, key = { it.record.id }) { r ->
                    RecordCard(r)
                }
            }
        }
    }

    if (showAddMember) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMember = false },
            title = { Text("添加家庭成员") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("姓名") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { vm.addMember(name); showAddMember = false }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddMember = false }) { Text("取消") } },
        )
    }

    if (showAddRecord && activeMemberId != null) {
        AddRecordDialog(
            vm = vm,
            memberId = activeMemberId,
            onDismiss = { showAddRecord = false },
            onConfirm = { bookId, notes ->
                vm.addRecord(activeMemberId, bookId, notes)
                showAddRecord = false
            },
        )
    }
}

@Composable
private fun MemberStatsCard(stats: MemberReadingStats, members: List<FamilyMember>) {
    val member = members.find { it.id == stats.memberId }
    val color = MemberColors.getOrElse(member?.colorIndex ?: 0) { MemberColors.first() }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stats.memberName, style = MaterialTheme.typography.titleSmall, color = color)
            Text("${stats.bookCount} 本 · ${formatWordCount(stats.totalWordCount.toInt())}")
        }
    }
}

@Composable
private fun RecordCard(r: ReadingRecordWithBook) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(r.bookTitle, style = MaterialTheme.typography.bodyLarge)
            Text("${r.memberName} · ${r.record.finishDate ?: "进行中"}", style = MaterialTheme.typography.bodySmall)
            if (r.bookCategory.isNotBlank()) Text("分类：${r.bookCategory}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AddRecordDialog(
    vm: ReadingViewModel,
    memberId: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.familylibrary.app.data.entity.BookWithLocation>>(emptyList()) }
    var selectedBookId by remember { mutableStateOf<Long?>(null) }
    var notes by remember { mutableStateOf("") }
    val scope = remember { androidx.compose.runtime.rememberCoroutineScope() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录阅读") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        scope.launch {
                            results = vm.searchBooks(it)
                        }
                    },
                    label = { Text("搜索图书") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                results.take(5).forEach { item ->
                    TextButton(
                        onClick = { selectedBookId = item.book.id },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            item.book.title + if (selectedBookId == item.book.id) " ✓" else "",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedBookId?.let { onConfirm(it, notes) } },
                enabled = selectedBookId != null,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
