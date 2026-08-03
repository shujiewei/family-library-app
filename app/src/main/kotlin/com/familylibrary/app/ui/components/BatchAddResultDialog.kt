package com.familylibrary.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familylibrary.app.data.repository.BatchAddResult

@Composable
fun BatchAddResultDialog(
    result: BatchAddResult,
    onDismiss: () -> Unit,
) {
    val title = when {
        result.addedCount == 0 && result.hasFailures -> "录入失败"
        result.hasFailures -> "录入完成（部分失败）"
        else -> "录入完成"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (result.addedCount > 0) {
                    Text(
                        "成功录入 ${result.addedCount} 本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (result.failures.isNotEmpty()) {
                    Text(
                        if (result.addedCount > 0) {
                            "以下 ${result.failures.size} 本未能录入："
                        } else {
                            "共 ${result.failures.size} 本未能录入："
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    result.failures.forEach { failure ->
                        Text(
                            "• ${failure.displayLabel}：${failure.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (result.failures.any { it.reason.contains("书名") }) {
                        Text(
                            "提示：可改用「扫码录入」或手动填写书名后重试",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}
