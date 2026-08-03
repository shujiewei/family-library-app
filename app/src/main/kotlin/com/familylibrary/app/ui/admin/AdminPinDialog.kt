package com.familylibrary.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AdminPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isInCooldown: Boolean,
    cooldownRemainingMs: Long,
    failedAttempts: Int,
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理员验证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("请输入管理员 PIN 码以管理书架和图书", style = MaterialTheme.typography.bodySmall)
                if (failedAttempts > 0) {
                    Text("已输错 $failedAttempts 次", color = MaterialTheme.colorScheme.error)
                }
                if (isInCooldown) {
                    Text("请等待 ${cooldownRemainingMs / 1000 + 1} 秒", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("PIN 码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isInCooldown,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.length >= 4 && !isInCooldown,
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun AdminBadge(isAdmin: Boolean) {
    if (isAdmin) {
        Text(
            "管理员",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
fun AdminActionRow(
    isAdmin: Boolean,
    onRequestAdmin: () -> Unit,
    onExitAdmin: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isAdmin) {
            TextButton(onClick = onExitAdmin) { Text("退出管理") }
        } else {
            TextButton(onClick = onRequestAdmin) { Text("管理员") }
        }
    }
}
