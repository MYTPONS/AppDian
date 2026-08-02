package com.appdian.store.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appdian.store.vm.SettingsViewModel
import com.appdian.store.vm.UpdateUiState
import com.appdian.store.vm.UpdateViewModel
import kotlinx.coroutines.launch

/**
 * 设置页（第 5 个 tab）：集中管理
 *  - 批量分类（关键词检索 + 批量归类）
 *  - 分类管理（增删改统一分类表）
 *  - 源管理（导入 / 导出 / 启停应用源）
 *  - 分类配置导出 / 导入（JSON）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSources: () -> Unit,
    onOpenBatchCategorize: () -> Unit,
    onOpenCategoryManage: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = viewModelFactory())
) {
    val context = LocalContext.current
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var showUa by remember { mutableStateOf(false) }
    var uaText by remember { mutableStateOf(viewModel.userAgent()) }
    var showCrashLog by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showNetImportCfg by remember { mutableStateOf(false) }
    var netCfgUrl by remember { mutableStateOf("") }
    var netCfgError by remember { mutableStateOf<String?>(null) }
    var netCfgLoading by remember { mutableStateOf(false) }
    val updateVm: UpdateViewModel = viewModel(factory = viewModelFactory())
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 导出到本地文件 / 从本地文件导入 / 从网络导入（legado 式）
    val exportFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && ImportExport.writeUriText(context, uri, viewModel.exportConfig())) {
            Toast.makeText(context, "已保存到文件", Toast.LENGTH_SHORT).show()
            showExport = false
        }
    }
    val importFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = ImportExport.readUriText(context, uri)
            if (text == null) {
                Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
            } else {
                val err = viewModel.importConfig(text)
                if (err == null) {
                    Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                    showImport = false
                } else Toast.makeText(context, "导入失败：$err", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item { SectionLabel("内容管理") }
            item {
                SettingsEntry(
                    icon = Icons.Default.Checklist,
                    title = "批量分类",
                    subtitle = "按关键词检索应用，多选后批量归类",
                    onClick = onOpenBatchCategorize
                )
            }
            item {
                SettingsEntry(
                    icon = Icons.Default.Category,
                    title = "分类管理",
                    subtitle = "新增 / 编辑 / 删除统一分类及关键词",
                    onClick = onOpenCategoryManage
                )
            }
            item { SectionLabel("应用源") }
            item {
                SettingsEntry(
                    icon = Icons.Default.Source,
                    title = "源管理",
                    subtitle = "导入 / 导出 / 启停应用源",
                    onClick = onOpenSources
                )
            }
            item { SectionLabel("网络") }
            item {
                SettingsEntry(
                    icon = Icons.Default.DeviceHub,
                    title = "User-Agent（用户代理）",
                    subtitle = "全局默认 UA：源没配 UA 时抓取和下载都使用它",
                    onClick = { uaText = viewModel.userAgent(); showUa = true }
                )
            }
            item { SectionLabel("关于") }
            item {
                SettingsEntry(
                    icon = Icons.Default.SystemUpdate,
                    title = "检查更新",
                    subtitle = "从 GitHub 仓库检查新版本 APK",
                    onClick = { showUpdate = true; updateVm.check() }
                )
            }
            item {
                SettingsEntry(
                    icon = Icons.Default.Info,
                    title = "关于应用大典",
                    subtitle = "项目介绍 · 开源信息 · 功能特性",
                    onClick = onOpenAbout
                )
            }
            item { SectionLabel("帮助与诊断") }
            item {
                SettingsEntry(
                    icon = Icons.Default.Info,
                    title = "崩溃日志",
                    subtitle = "查看最近一次崩溃堆栈，可复制/分享给开发者排查",
                    onClick = { showCrashLog = true }
                )
            }
            item { SectionLabel("分类配置（跨设备同步）") }
            item {
                SettingsEntry(
                    icon = Icons.Default.Archive,
                    title = "导出分类配置",
                    subtitle = "导出分类表与手动归类记录（JSON）",
                    onClick = { showExport = true }
                )
            }
            item {
                SettingsEntry(
                    icon = Icons.Default.Unarchive,
                    title = "导入分类配置",
                    subtitle = "粘贴 JSON 恢复分类表与归类记录",
                    onClick = { showImport = true }
                )
            }
        }
    }

    if (showExport) {
        val cfg = remember { viewModel.exportConfig() }
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("导出分类配置") },
            text = {
                Column {
                    Text(
                        cfg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .clip(RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { exportFileLauncher.launch("appdian-categories.json") }) { Text("保存到文件") }
                    TextButton(onClick = {
                        runCatching {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("分类配置", cfg))
                        }
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        showExport = false
                    }) { Text("复制") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExport = false }) { Text("关闭") }
            }
        )
    }

    if (showUa) {
        AlertDialog(
            onDismissRequest = { showUa = false },
            title = { Text("设置 User-Agent") },
            text = {
                Column {
                    OutlinedTextField(
                        value = uaText,
                        onValueChange = { uaText = it },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Mozilla/5.0 (Linux; Android 14) …") }
                    )
                    Text(
                        "留空恢复默认。改后立即生效，可用来绕过网站对默认 UA 的限制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setUserAgent(uaText)
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    showUa = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showUa = false }) { Text("取消") }
            }
        )
    }

    if (showUpdate) {
        val updateUi by updateVm.ui.collectAsStateWithLifecycle()
        when (val u = updateUi) {
            is UpdateUiState.Checking -> {
                AlertDialog(
                    onDismissRequest = { showUpdate = false; updateVm.reset() },
                    title = { Text("检查更新") },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("正在从 GitHub 检查新版本…")
                        }
                    },
                    confirmButton = {}
                )
            }
            is UpdateUiState.HasUpdate -> {
                AlertDialog(
                    onDismissRequest = { showUpdate = false; updateVm.reset() },
                    title = { Text("发现新版本 ${u.info.version}") },
                    text = {
                        Column {
                            Text(
                                "当前版本：v${com.appdian.store.ui.APP_VERSION_NAME}\n最新版本：${u.info.version}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (u.info.notes.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    u.info.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.heightIn(max = 160.dp)
                                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            runCatching {
                                com.appdian.store.download.DownloadService.enqueue(
                                    context,
                                    u.info.apkUrl,
                                    "应用大典 ${u.info.version}",
                                    "appdian-${u.info.version}.apk",
                                    appKey = "appdian-update"
                                )
                            }
                            Toast.makeText(context, "已开始下载更新包", Toast.LENGTH_SHORT).show()
                            showUpdate = false
                            updateVm.reset()
                        }) { Text("下载更新") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdate = false; updateVm.reset() }) { Text("取消") }
                    }
                )
            }
            is UpdateUiState.NoUpdate -> {
                AlertDialog(
                    onDismissRequest = { showUpdate = false; updateVm.reset() },
                    title = { Text("检查更新") },
                    text = { Text("已是最新版本 v${com.appdian.store.ui.APP_VERSION_NAME}") },
                    confirmButton = {
                        TextButton(onClick = { showUpdate = false; updateVm.reset() }) { Text("知道了") }
                    }
                )
            }
            is UpdateUiState.Error -> {
                AlertDialog(
                    onDismissRequest = { showUpdate = false; updateVm.reset() },
                    title = { Text("检查更新") },
                    text = { Text(u.message, color = MaterialTheme.colorScheme.error) },
                    confirmButton = {
                        TextButton(onClick = { showUpdate = false; updateVm.reset() }) { Text("关闭") }
                    }
                )
            }
            is UpdateUiState.Idle -> {}
        }
    }

    if (showCrashLog) {
        val app = context.applicationContext as com.appdian.store.AppDianApp
        val crashLog = remember {
            val txt = app.crashLog()
            if (txt.isBlank()) {
                "没有崩溃记录。\n\n崩溃日志写在应用私有目录，机制已就绪。\n请再点一次「源管理」，闪退后重开应用，回到这里查看。\n如果仍然无记录，说明进程是被系统直接杀死的（不是代码异常），请把此提示发给我。"
            } else txt
        }
        AlertDialog(
            onDismissRequest = { showCrashLog = false },
            title = { Text("崩溃日志") },
            text = {
                Text(
                    crashLog,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .clip(RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("崩溃日志", crashLog))
                    }
                    Toast.makeText(context, "已复制，粘贴发给开发者", Toast.LENGTH_SHORT).show()
                    showCrashLog = false
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = {
                    runCatching {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, crashLog)
                            putExtra(Intent.EXTRA_SUBJECT, "应用大典崩溃日志")
                        }
                        context.startActivity(Intent.createChooser(send, "分享崩溃日志"))
                    }
                    showCrashLog = false
                }) { Text("分享") }
            }
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入分类配置") },
            text = {
                Column {
                    Text(
                        "选择导入方式：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { importFileLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("本地文件") }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showNetImportCfg = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("网络导入") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it; importMsg = null },
                        placeholder = { Text("或直接粘贴 JSON") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    importMsg?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val err = viewModel.importConfig(importText)
                    if (err == null) {
                        Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                        showImport = false
                        importText = ""
                    } else {
                        importMsg = "导入失败：$err"
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("取消") }
            }
        )
    }

    // 网络导入分类配置
    if (showNetImportCfg) {
        AlertDialog(
            onDismissRequest = { showNetImportCfg = false; netCfgUrl = ""; netCfgError = null },
            title = { Text("从网络导入分类配置") },
            text = {
                Column {
                    OutlinedTextField(
                        value = netCfgUrl,
                        onValueChange = { netCfgUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入分类配置 JSON 直链") },
                        singleLine = true
                    )
                    netCfgError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (netCfgLoading) {
                        Text("下载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (netCfgUrl.isBlank()) return@TextButton
                    netCfgLoading = true; netCfgError = null
                    scope.launch {
                        try {
                            val text = ImportExport.fetchText(netCfgUrl.trim())
                            val err = viewModel.importConfig(text)
                            if (err == null) {
                                Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                                showNetImportCfg = false; netCfgUrl = ""; netCfgLoading = false
                                showImport = false
                            } else {
                                netCfgLoading = false
                                netCfgError = "导入失败：$err"
                            }
                        } catch (e: Exception) {
                            netCfgLoading = false
                            netCfgError = e.message ?: "下载失败"
                        }
                    }
                }, enabled = netCfgUrl.isNotBlank() && !netCfgLoading) { Text("下载并导入") }
            },
            dismissButton = {
                TextButton(onClick = { showNetImportCfg = false; netCfgUrl = ""; netCfgError = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

