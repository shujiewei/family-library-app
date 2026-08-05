package com.familylibrary.app.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 选中排后的主操作区：固定在内容底部，避免 FAB 被导航栏遮挡或挤成一团 */
@Composable
fun ShelfPrimaryActionBar(
    isArchiveShelf: Boolean,
    onScanBatch: () -> Unit,
    onScanOrganize: () -> Unit,
    onBatchMove: () -> Unit,
    onManualAdd: () -> Unit,
    onBatchText: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isArchiveShelf) {
                        Button(
                            onClick = onScanOrganize,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.DriveFileMove, null, Modifier.padding(end = 4.dp))
                            Text("扫码整理")
                        }
                    } else {
                        Button(
                            onClick = onScanBatch,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, Modifier.padding(end = 4.dp))
                            Text("扫码录入")
                        }
                        OutlinedButton(
                            onClick = onScanOrganize,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.DriveFileMove, null, Modifier.padding(end = 4.dp))
                            Text("扫码整理")
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onBatchMove, modifier = Modifier.weight(1f)) {
                        Text("批量移动")
                    }
                    if (!isArchiveShelf) {
                        OutlinedButton(onClick = onManualAdd, modifier = Modifier.weight(1f)) {
                            Text("手动录入")
                        }
                        OutlinedButton(onClick = onBatchText, modifier = Modifier.weight(1f)) {
                            Text("批量文本")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfSelectionActionBar(
    selectedCount: Int,
    isArchiveShelf: Boolean,
    onMove: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("已选 $selectedCount 本", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onCancel) { Text("取消") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onMove,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.DriveFileMove, null, Modifier.padding(end = 4.dp))
                    Text("移动")
                }
                if (!isArchiveShelf) {
                    OutlinedButton(
                        onClick = onArchive,
                        enabled = selectedCount > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Archive, null, Modifier.padding(end = 4.dp))
                        Text("归档")
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.padding(end = 4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
fun ShelfAdminHintBar(onRequestAdmin: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 0.dp, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "录入与移动图书需管理员权限",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestAdmin) { Text("管理员") }
        }
    }
}
