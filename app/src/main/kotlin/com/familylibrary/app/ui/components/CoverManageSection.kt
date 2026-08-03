package com.familylibrary.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.CoverMeta
import com.familylibrary.app.data.repository.CoverActionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CoverManageSection(
    book: Book,
    isAdmin: Boolean,
    app: FamilyLibraryApplication,
    scope: CoroutineScope,
    onBookUpdated: (Book) -> Unit,
    onMessage: (String) -> Unit,
) {
    if (!isAdmin) {
        CoverStatusText(book)
        return
    }

    val context = LocalContext.current
    val repo = app.serviceLocator.bookRepository
    val coverService = app.serviceLocator.coverService
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun reloadBook() {
        scope.launch {
            repo.getById(book.id)?.let { onBookUpdated(it) }
        }
    }

    fun handleResult(result: CoverActionResult) {
        when (result) {
            CoverActionResult.Success -> {
                onMessage("封面已更新")
                reloadBook()
            }
            CoverActionResult.NoIsbn -> onMessage("请先填写 ISBN")
            CoverActionResult.SkippedCustom -> onMessage("已使用自定义封面，如需网络封面请点「重新拉取」")
            is CoverActionResult.Failed -> onMessage(result.message)
            CoverActionResult.NotFound -> onMessage("图书不存在")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.saveCustomCoverFromUri(book.id, uri)
            }
            handleResult(result)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = cameraFile
        if (!success || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.saveCustomCoverFromFile(book.id, file)
            }
            handleResult(result)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            onMessage("需要相机权限才能拍照")
            return@rememberLauncherForActivityResult
        }
        val file = coverService.createCameraTempFile()
        cameraFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraLauncher.launch(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CoverStatusText(book)

        if (book.coverStatus == CoverMeta.STATUS_FAILED) {
            Text(
                "可能原因：无网络、ISBN 无封面数据、或服务暂时不可用。可重试或拍照/相册上传实际封面。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (book.coverSource == CoverMeta.SOURCE_CUSTOM) {
            Text(
                "当前为自定义封面，修改 ISBN 不会自动覆盖。如需换回网络封面，请点「重新拉取」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("从相册选择封面") }

        OutlinedButton(
            onClick = {
                when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED -> {
                        val file = coverService.createCameraTempFile()
                        cameraFile = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        cameraLauncher.launch(uri)
                    }
                    else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("拍照更新封面") }

        if (book.isbn.isNotBlank()) {
            Button(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            repo.retryFetchCoverFromIsbn(book.id)
                        }
                        handleResult(result)
                    }
                },
                enabled = book.coverStatus != CoverMeta.STATUS_LOADING,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("按 ISBN 重新拉取封面") }
        }
    }
}

@Composable
private fun CoverStatusText(book: Book) {
    val label = CoverMeta.statusLabel(
        book.coverSource,
        book.coverStatus,
        !book.coverUri.isNullOrBlank(),
    )
    val color = when (book.coverStatus) {
        CoverMeta.STATUS_FAILED -> MaterialTheme.colorScheme.error
        CoverMeta.STATUS_LOADING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text("封面：$label", style = MaterialTheme.typography.bodySmall, color = color)
}
