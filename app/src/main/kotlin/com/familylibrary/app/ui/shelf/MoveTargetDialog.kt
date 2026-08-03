package com.familylibrary.app.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.familylibrary.app.data.repository.MoveTarget

@Composable
fun MoveTargetDialog(
    title: String,
    targets: List<MoveTarget>,
    currentRowId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                var lastShelf = ""
                items(targets.filter { it.rowId != currentRowId }) { target ->
                    if (target.bookshelfName != lastShelf) {
                        lastShelf = target.bookshelfName
                        Text(
                            target.bookshelfName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        if (target.isArchive) "  ${target.bookshelfName}" else "  ${target.rowName}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(target.rowId) },
                        color = if (target.isArchive) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
