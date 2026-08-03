package com.familylibrary.app.ui.settings

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familylibrary.app.BuildConfig
import com.familylibrary.app.FamilyLibraryApplication
import com.familylibrary.app.data.backup.BackupManager
import com.familylibrary.app.data.entity.AppSettings
import com.familylibrary.app.ui.admin.AdminActionRow
import com.familylibrary.app.ui.admin.AdminBadge
import com.familylibrary.app.ui.admin.AdminModeController
import com.familylibrary.app.util.Hash
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(private val app: FamilyLibraryApplication) : ViewModel() {
    val adminController = app.serviceLocator.adminModeController
    val settings = app.serviceLocator.appSettingsDao.observe()

    fun changePin(oldPin: String, newPin: String, onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val current = app.serviceLocator.appSettingsDao.get()
        if (current == null) {
            onResult(false, "设置未初始化")
            return@launch
        }
        if (!Hash.verifyPin(oldPin, current.adminPinSalt, current.adminPinHash)) {
            onResult(false, "原 PIN 码错误")
            return@launch
        }
        val salt = Hash.generateSalt()
        val hash = Hash.sha256(newPin, salt)
        app.serviceLocator.appSettingsDao.upsert(current.copy(adminPinHash = hash, adminPinSalt = salt))
        onResult(true, "PIN 码已更新")
    }

    suspend fun verifyPin(pin: String): Boolean {
        val settings = app.serviceLocator.appSettingsDao.get() ?: return false
        return Hash.verifyPin(pin, settings.adminPinSalt, settings.adminPinHash)
    }

    suspend fun export(uri: Uri) =
        BackupManager.exportTo(app, uri, app.serviceLocator.database)

    suspend fun import(uri: Uri) =
        BackupManager.importFrom(app, uri, app.serviceLocator.database)

    class Factory(private val app: FamilyLibraryApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(app) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: FamilyLibraryApplication,
    onRequestAdmin: () -> Unit,
    vm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = SettingsViewModel.Factory(app)),
) {
    val isAdmin by vm.adminController.isAdminMode.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showChangePin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                when (val r = vm.export(uri)) {
                    is BackupManager.ExportResult.Success -> snackbar.showSnackbar("导出成功")
                    is BackupManager.ExportResult.Failure -> snackbar.showSnackbar(r.message)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                actions = {
                    AdminBadge(isAdmin)
                    AdminActionRow(
                        isAdmin = isAdmin,
                        onRequestAdmin = onRequestAdmin,
                        onExitAdmin = { vm.adminController.exit() },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("家庭图书馆 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("图书数据本地存储；封面通过 ISBN 从网络拉取", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("权限说明", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Text(
                "普通用户：查找图书、浏览分类、记录阅读、查看统计、管理待购书单\n" +
                    "管理员：书架管理、图书录入/删除/移动、批量操作",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("默认管理员 PIN：${AdminModeController.DEFAULT_PIN}", style = MaterialTheme.typography.labelSmall)

            if (isAdmin) {
                Button(onClick = { showChangePin = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("修改管理员 PIN")
                }
            }

            Text("数据备份", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Button(
                onClick = {
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportLauncher.launch("family_library_backup_$ts.zip")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出备份 (ZIP)") }

            Button(
                onClick = {
                    vm.adminController.extend()
                    importLauncher.launch(arrayOf("application/zip", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导入备份") }
        }
    }

    if (showImportConfirm && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            },
            title = { Text("确认导入备份？") },
            text = {
                Text("将覆盖全部本地图书、书架与阅读记录，此操作不可撤销。建议先导出当前备份。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri!!
                    showImportConfirm = false
                    pendingImportUri = null
                    scope.launch {
                        when (val r = vm.import(uri)) {
                            is BackupManager.ImportResult.Success -> {
                                snackbar.showSnackbar("导入成功，正在刷新…")
                                (context as? ComponentActivity)?.recreate()
                            }
                            is BackupManager.ImportResult.Failure -> snackbar.showSnackbar(r.message)
                        }
                    }
                }) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) { Text("取消") }
            },
        )
    }

    if (showChangePin) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePin = false },
            title = { Text("修改 PIN 码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = oldPin, onValueChange = { oldPin = it }, label = { Text("原 PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newPin, onValueChange = { newPin = it }, label = { Text("新 PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.changePin(oldPin, newPin) { ok, msg ->
                        message = msg
                        if (ok) showChangePin = false
                    }
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { showChangePin = false }) { Text("取消") } },
        )
    }
}
