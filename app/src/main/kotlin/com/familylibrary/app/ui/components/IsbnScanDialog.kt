package com.familylibrary.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familylibrary.app.ui.components.CameraPermissionGate
import com.familylibrary.app.ui.scan.IsbnBarcodeScanner

/** 弹出扫码框，识别 ISBN 后回调 */
@Composable
fun IsbnScanDialog(
    onDismiss: () -> Unit,
    onIsbnScanned: (String) -> Unit,
) {
    var last by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫描 ISBN") },
        text = {
            Column {
                Text("对准书籍条码")
                CameraPermissionGate(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                ) {
                    IsbnBarcodeScanner(
                        enabled = true,
                        onIsbnDetected = { isbn ->
                            if (isbn != last) {
                                last = isbn
                                onIsbnScanned(isbn)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
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
