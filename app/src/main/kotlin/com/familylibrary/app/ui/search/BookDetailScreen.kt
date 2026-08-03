package com.familylibrary.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.BookWithLocation
import com.familylibrary.app.data.entity.CoverMeta
import com.familylibrary.app.ui.components.BookCover
import com.familylibrary.app.ui.components.BookMoveActions
import com.familylibrary.app.ui.components.CoverManageSection
import com.familylibrary.app.ui.components.formatWordCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    app: FamilyLibraryApplication,
    isAdmin: Boolean,
    onBack: () -> Unit,
) {
    var book by remember { mutableStateOf<Book?>(null) }
    var location by remember { mutableStateOf<BookWithLocation?>(null) }
    var recommendations by remember { mutableStateOf<List<Book>>(emptyList()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun loadBook() {
        withContext(Dispatchers.IO) {
            val sl = app.serviceLocator
            val b = sl.bookRepository.getById(bookId)
            book = b
            if (b != null) {
                location = sl.bookRepository.getByIdWithLocation(bookId)
                recommendations = sl.bookRepository.findSimilar(b)
            }
        }
    }

    LaunchedEffect(bookId) { loadBook() }

    // 封面拉取中时定时刷新，直到完成
    val b = book
    if (b?.coverStatus == CoverMeta.STATUS_LOADING) {
        LaunchedEffect(b.id, b.coverStatus) {
            kotlinx.coroutines.delay(1500)
            loadBook()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "图书详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = book
        if (current == null) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                Text("加载中…", modifier = Modifier.padding(16.dp))
            }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BookCover(
                        coverUri = current.coverUri,
                        title = current.title,
                        coverStatus = current.coverStatus,
                        modifier = Modifier.size(100.dp, 140.dp),
                    )
                    Column {
                        Text(current.title, style = MaterialTheme.typography.titleLarge)
                        if (current.author.isNotBlank()) Text("作者：${current.author}")
                        if (current.publisher.isNotBlank()) Text("出版社：${current.publisher}")
                        if (current.pageCount > 0) Text("页数：${current.pageCount}")
                        if (current.wordCount > 0) Text("字数：${formatWordCount(current.wordCount)}")
                        if (current.series.isNotBlank()) Text("系列：${current.series}")
                        if (current.category.isNotBlank()) Text("分类：${current.category}")
                        if (current.recommendedAge.isNotBlank()) Text("推荐年龄：${current.recommendedAge}")
                        if (current.isbn.isNotBlank()) Text("ISBN：${current.isbn}")
                        if (current.isEnglish && current.lexileLevel.isNotBlank()) {
                            Text("蓝思值：${current.lexileLevel}")
                        }
                    }
                }

                CoverManageSection(
                    book = current,
                    isAdmin = isAdmin,
                    app = app,
                    scope = scope,
                    onBookUpdated = { book = it },
                    onMessage = { msg ->
                        scope.launch { snackbar.showSnackbar(msg) }
                    },
                )

                location?.let { loc ->
                    val locText = if (loc.bookshelfName != null) {
                        "位置：${loc.bookshelfName} / ${loc.shelfRowName}"
                    } else "未上架"
                    Text(locText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }

                BookMoveActions(
                    bookId = current.id,
                    currentRowId = current.shelfRowId,
                    isAdmin = isAdmin,
                    app = app,
                    onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                    onMoved = { scope.launch { loadBook() } },
                )

                if (current.description.isNotBlank()) {
                    Text("简介", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Text(current.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (recommendations.isNotEmpty()) {
                    Text("相关推荐", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    recommendations.forEach { rec ->
                        Text(
                            "· ${rec.title}" + if (rec.author.isNotBlank()) " — ${rec.author}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
