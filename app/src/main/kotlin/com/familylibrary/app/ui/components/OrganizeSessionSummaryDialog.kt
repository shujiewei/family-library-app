package com.familylibrary.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

@Composable
fun OrganizeSessionSummaryDialog(
    movedCount: Int,
    alreadyHereCount: Int,
    notFoundCount: Int,
    onDismiss: () -> Unit,
) {
    val total = movedCount + alreadyHereCount + notFoundCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("整理完成") },
        text = {
            Column {
                Text(
                    "本次共扫描 $total 次",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (movedCount > 0) {
                    Text(
                        "✓ 成功移入 $movedCount 本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (alreadyHereCount > 0) {
                    Text(
                        "· 已在目标位 $alreadyHereCount 本",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (notFoundCount > 0) {
                    Text(
                        "· 未入库 $notFoundCount 本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (movedCount == 0 && total > 0) {
                    Text(
                        "没有新书移入目标位置",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}
