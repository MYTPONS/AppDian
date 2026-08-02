package com.appdian.store.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appdian.store.vm.SettingsViewModel

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
                TextButton(onClick = {
                    runCatching {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("分类配置", cfg))
                    }
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    showExport = false
                }) { Text("复制") }
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
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it; importMsg = null },
                        placeholder = { Text("粘贴 JSON") },
                        minLines = 6,
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

