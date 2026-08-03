package com.familylibrary.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.familylibrary.app.data.repository.MoveTarget
import com.familylibrary.app.ui.shelf.MoveTargetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BookMoveActions(
    bookId: Long,
    currentRowId: Long?,
    isAdmin: Boolean,
    app: FamilyLibraryApplication,
    onMessage: (String) -> Unit,
    onMoved: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!isAdmin) return

    var showMoveDialog by remember { mutableStateOf(false) }
    var moveTargets by remember { mutableStateOf<List<MoveTarget>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val bookRepo = app.serviceLocator.bookRepository
    val shelfRepo = app.serviceLocator.shelfRepository

    LaunchedEffect(Unit) {
        moveTargets = withContext(Dispatchers.IO) { shelfRepo.getMoveTargets() }
    }

    Text("整理", style = MaterialTheme.typography.titleSmall, modifier = modifier.padding(top = 8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { showMoveDialog = true },
            modifier = Modifier.weight(1f),
        ) { Text("移动到…") }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val archiveRowId = withContext(Dispatchers.IO) { shelfRepo.ensureArchiveRow() }
                    withContext(Dispatchers.IO) {
                        bookRepo.moveBooks(listOf(bookId), archiveRowId)
                    }
                    onMessage("已移入归档")
                    onMoved()
                }
            },
            modifier = Modifier.weight(1f),
        ) { Text("移入归档") }
    }

    if (showMoveDialog) {
        MoveTargetDialog(
            title = "移动到",
            targets = moveTargets,
            currentRowId = currentRowId,
            onDismiss = { showMoveDialog = false },
            onSelect = { rowId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        bookRepo.moveBooks(listOf(bookId), rowId)
                    }
                    showMoveDialog = false
                    onMessage("移动成功")
                    onMoved()
                }
            },
        )
    }
}
