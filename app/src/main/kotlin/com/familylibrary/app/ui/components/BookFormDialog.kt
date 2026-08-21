package com.familylibrary.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.familylibrary.app.data.cover.CoverService
import com.familylibrary.app.data.cover.IsbnLookupService
import com.familylibrary.app.data.entity.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class BookFormState(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val pageCount: String = "",
    val wordCount: String = "",
    val description: String = "",
    val series: String = "",
    val recommendedAge: String = "",
    val lexileLevel: String = "",
    val category: String = "",
    val isEnglish: Boolean = false,
)

fun Book.toFormState() = BookFormState(
    title = title,
    author = author,
    publisher = publisher,
    isbn = isbn,
    pageCount = if (pageCount > 0) pageCount.toString() else "",
    wordCount = if (wordCount > 0) wordCount.toString() else "",
    description = description,
    series = series,
    recommendedAge = recommendedAge,
    lexileLevel = lexileLevel,
    category = category,
    isEnglish = isEnglish,
)

fun BookFormState.toBook(existing: Book? = null) = Book(
    id = existing?.id ?: 0,
    title = title.trim(),
    author = author.trim(),
    publisher = publisher.trim(),
    isbn = CoverService.normalizeIsbn(isbn),
    pageCount = pageCount.toIntOrNull() ?: 0,
    wordCount = wordCount.toIntOrNull() ?: 0,
    description = description.trim(),
    coverUri = existing?.coverUri,
    coverSource = existing?.coverSource ?: com.familylibrary.app.data.entity.CoverMeta.SOURCE_NONE,
    coverStatus = existing?.coverStatus ?: com.familylibrary.app.data.entity.CoverMeta.STATUS_NONE,
    series = series.trim(),
    recommendedAge = recommendedAge.trim(),
    lexileLevel = lexileLevel.trim(),
    category = category.trim(),
    isEnglish = isEnglish,
    shelfRowId = existing?.shelfRowId,
    positionInRow = existing?.positionInRow ?: 0,
    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
)

@Composable
fun BookFormDialog(
    title: String,
    initial: BookFormState = BookFormState(),
    isbnLookup: IsbnLookupService? = null,
    onDismiss: () -> Unit,
    onConfirm: (BookFormState) -> Unit,
) {
    var state by remember { mutableStateOf(initial) }
    var showScan by remember { mutableStateOf(false) }
    var isLookingUpTitle by remember { mutableStateOf(false) }
    var lookupMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isbnValid = state.isbn.isBlank() || CoverService.isValidIsbn(state.isbn)

    LaunchedEffect(state.isbn, isbnLookup) {
        val lookup = isbnLookup ?: return@LaunchedEffect
        val normalized = CoverService.normalizeIsbn(state.isbn)
        if (!CoverService.isPlausibleIsbn(normalized)) {
            lookupMessage = null
            return@LaunchedEffect
        }
        delay(700)
        if (CoverService.normalizeIsbn(state.isbn) != normalized) return@LaunchedEffect
        isLookingUpTitle = true
        lookupMessage = null
        try {
            val info = withTimeoutOrNull(12_000) {
                withContext(Dispatchers.IO) { lookup.lookup(normalized) }
            }
            if (info != null) {
                state = state.copy(
                    title = if (state.title.isBlank()) info.title else state.title,
                    author = if (state.author.isBlank()) info.author else state.author,
                    publisher = if (state.publisher.isBlank()) info.publisher else state.publisher,
                    pageCount = if (state.pageCount.isBlank() && info.pageCount > 0) {
                        info.pageCount.toString()
                    } else {
                        state.pageCount
                    },
                    description = if (state.description.isBlank()) info.description else state.description,
                )
            } else if (state.title.isBlank()) {
                lookupMessage = "未找到书名或查询超时，请检查网络或手动填写"
            }
        } catch (_: Exception) {
            lookupMessage = "书名查询失败，请手动填写"
        } finally {
            isLookingUpTitle = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { state = state.copy(title = it) },
                    label = { Text("书名 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.isbn,
                    onValueChange = { state = state.copy(isbn = it) },
                    label = { Text("ISBN") },
                    placeholder = { Text("保存后自动拉取封面") },
                    trailingIcon = {
                        IconButton(onClick = { showScan = true }) {
                            Icon(Icons.Default.QrCodeScanner, "扫码")
                        }
                    },
                    supportingText = {
                        when {
                            isLookingUpTitle -> Text("正在查询书名…")
                            lookupMessage != null -> Text(lookupMessage!!, color = MaterialTheme.colorScheme.error)
                            state.isbn.isBlank() -> Text("填写 ISBN 将同步查询书名；封面保存后后台下载")
                            !isbnValid -> Text("ISBN 格式不正确（需 10 或 13 位）", color = MaterialTheme.colorScheme.error)
                            else -> Text("作者等信息保存后后台补全")
                        }
                    },
                    isError = state.isbn.isNotBlank() && !isbnValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.author,
                    onValueChange = { state = state.copy(author = it) },
                    label = { Text("作者") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.publisher,
                    onValueChange = { state = state.copy(publisher = it) },
                    label = { Text("出版社") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.pageCount,
                        onValueChange = { state = state.copy(pageCount = it.filter { c -> c.isDigit() }) },
                        label = { Text("页数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.wordCount,
                        onValueChange = { state = state.copy(wordCount = it.filter { c -> c.isDigit() }) },
                        label = { Text("字数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = state.series,
                    onValueChange = { state = state.copy(series = it) },
                    label = { Text("系列") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.category,
                    onValueChange = { state = state.copy(category = it) },
                    label = { Text("分类") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.recommendedAge,
                    onValueChange = { state = state.copy(recommendedAge = it) },
                    label = { Text("推荐年龄") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.isEnglish,
                        onCheckedChange = { state = state.copy(isEnglish = it) },
                    )
                    Text("英文书")
                }
                if (state.isEnglish) {
                    OutlinedTextField(
                        value = state.lexileLevel,
                        onValueChange = { state = state.copy(lexileLevel = it) },
                        label = { Text("蓝思值 (Lexile)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { state = state.copy(description = it) },
                    label = { Text("简介") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(state) },
                enabled = state.title.isNotBlank() && isbnValid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (showScan) {
        IsbnScanDialog(
            onDismiss = { showScan = false },
            onIsbnScanned = { isbn ->
                val normalized = CoverService.normalizeIsbn(isbn)
                state = state.copy(isbn = normalized)
                lookupMessage = null
                if (isbnLookup != null && CoverService.isPlausibleIsbn(normalized)) {
                    scope.launch {
                        isLookingUpTitle = true
                        try {
                            val info = withTimeoutOrNull(12_000) {
                                withContext(Dispatchers.IO) { isbnLookup.lookup(normalized) }
                            }
                            if (info != null) {
                                state = state.copy(
                                    title = if (state.title.isBlank()) info.title else state.title,
                                    author = if (state.author.isBlank()) info.author else state.author,
                                    publisher = if (state.publisher.isBlank()) info.publisher else state.publisher,
                                    pageCount = if (state.pageCount.isBlank() && info.pageCount > 0) {
                                        info.pageCount.toString()
                                    } else {
                                        state.pageCount
                                    },
                                    description = if (state.description.isBlank()) {
                                        info.description
                                    } else {
                                        state.description
                                    },
                                )
                                lookupMessage = null
                            } else {
                                lookupMessage = "未找到书名或查询超时，请手动填写"
                            }
                        } catch (_: Exception) {
                            lookupMessage = "书名查询失败，请手动填写"
                        } finally {
                            isLookingUpTitle = false
                        }
                    }
                }
                showScan = false
            },
        )
    }
}

@Composable
fun BatchBookDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    isLoading: Boolean = false,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("批量录入") },
        text = {
            Column {
                Text(
                    if (isLoading) {
                        "正在查询书名并录入，请稍候…"
                    } else {
                        "每行一本书。支持格式：\n· 书名\n· 书名,ISBN\n· 仅 ISBN（将同步查询书名）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (!isLoading) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) onConfirm(lines)
                },
                enabled = !isLoading && text.isNotBlank(),
            ) { Text(if (isLoading) "录入中…" else "录入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("取消") }
        },
    )
}
