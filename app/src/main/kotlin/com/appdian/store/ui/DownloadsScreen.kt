package com.appdian.store.ui
import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appdian.store.download.DlStatus
import com.appdian.store.download.DownloadTask
import com.appdian.store.vm.DownloadsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 待删除的任务集合（单个或批量），统一弹一次确认 */
private data class DeletePending(val ids: List<Long>, val hasFile: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = viewModel(factory = viewModelFactory())
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 批量管理模式
    var batchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    // 删除确认（单个或批量共用一个，只弹一次）
    var pendingDelete by remember { mutableStateOf<DeletePending?>(null) }

    fun hasFile(t: DownloadTask) = t.status == DlStatus.DONE && !t.localPath.isNullOrBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (batchMode) "已选 ${selectedIds.size} 项" else "下载") },
                actions = {
                    if (batchMode) {
                        TextButton(onClick = { batchMode = false; selectedIds = emptySet() }) { Text("完成") }
                    } else {
                        IconButton(onClick = { batchMode = true }) {
                            Icon(Icons.Default.Checklist, contentDescription = "批量管理")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (batchMode) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == tasks.size) emptySet()
                                else tasks.map { it.id }.toSet()
                            }
                        ) { Text(if (selectedIds.size == tasks.size) "取消全选" else "全选") }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                val ids = tasks.filter { it.status == DlStatus.QUEUED || it.status == DlStatus.RUNNING }
                                    .map { it.id }.toList()
                                if (ids.isNotEmpty()) { viewModel.cancelAll(ids); selectedIds = selectedIds - ids.toSet() }
                            },
                            enabled = tasks.any { it.status == DlStatus.QUEUED || it.status == DlStatus.RUNNING }
                        ) { Text("取消") }
                        TextButton(
                            onClick = {
                                val ids = tasks.filter { it.status == DlStatus.FAILED }.map { it.id }.toList()
                                if (ids.isNotEmpty()) { viewModel.retryAll(context, ids); selectedIds = selectedIds - ids.toSet() }
                            },
                            enabled = tasks.any { it.status == DlStatus.FAILED }
                        ) { Text("重试") }
                        TextButton(onClick = {
                            val ids = selectedIds.toList()
                            if (ids.isNotEmpty()) {
                                pendingDelete = DeletePending(
                                    ids,
                                    tasks.any { it.id in ids && hasFile(it) }
                                )
                            }
                        }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "还没有下载记录\n在应用详情页点「下载」开始吧",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskRow(
                        task = task,
                        batchMode = batchMode,
                        checked = task.id in selectedIds,
                        onToggle = {
                            selectedIds = if (task.id in selectedIds) selectedIds - task.id
                            else selectedIds + task.id
                        },
                        onCancel = { viewModel.cancel(task.id) },
                        onRetry = { viewModel.retry(context, task.id) },
                        onDelete = {
                            pendingDelete = DeletePending(listOf(task.id), hasFile(task))
                        },
                        onInstall = {
                            // 复制到 cacheDir 可能耗时长（大 APK），放后台线程
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    com.appdian.store.download.InstallUtil.installApk(context, task.localPath!!)
                                }.onFailure {
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "无法安装：${it.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // 删除确认弹窗（单个/批量共用，只弹一次）
    pendingDelete?.let { p ->
        val hasFile = p.hasFile
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (p.ids.size > 1) "删除 ${p.ids.size} 条下载记录？" else "删除这条下载记录？") },
            text = {
                Text(
                    if (hasFile) "是否同时删除已下载的安装包文件？"
                    else "该任务没有已下载的文件，仅删除记录。"
                )
            },
            confirmButton = {
                if (hasFile) {
                    TextButton(onClick = {
                        val deleted = viewModel.removeAll(p.ids, deleteFile = true)
                        pendingDelete = null
                        Toast.makeText(
                            context,
                            if (deleted > 0) "已删除记录，同时删除 $deleted 个下载文件"
                            else "已删除记录（未找到对应文件）",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("同时删除文件", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = {
                    viewModel.removeAll(p.ids, deleteFile = false)
                    pendingDelete = null
                    Toast.makeText(context, "已删除记录", Toast.LENGTH_SHORT).show()
                }) { Text(if (hasFile) "仅删除记录" else "删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DownloadTaskRow(
    task: DownloadTask,
    batchMode: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onInstall: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .let { m ->
                if (!batchMode && task.status == DlStatus.DONE) m.clip(RoundedCornerShape(12.dp)).clickable(onClick = onInstall)
                else m
            },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .then(
                    if (batchMode) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle)
                    else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (batchMode) {
                // 批量选择框
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when (task.status) {
                                DlStatus.DONE -> MaterialTheme.colorScheme.primary
                                DlStatus.FAILED, DlStatus.CANCELED -> MaterialTheme.colorScheme.error
                                DlStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.padding(top = 2.dp))
                when (task.status) {
                    DlStatus.DONE -> Text(
                        "已完成 · 点击安装",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DlStatus.FAILED, DlStatus.CANCELED -> Text(
                        task.error ?: "下载失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> {
                        LinearProgressIndicator(
                            progress = {
                                if (task.progress >= 0) task.progress / 100f else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.padding(top = 2.dp))
                        Text(
                            if (task.progress >= 0)
                                "${task.progress}% · ${humanSize(task.downloadedBytes)} / ${humanSize(task.totalBytes)}"
                            else "下载中…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            when (task.status) {
                DlStatus.QUEUED, DlStatus.RUNNING -> IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "取消", tint = MaterialTheme.colorScheme.outline)
                }
                DlStatus.FAILED -> IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试", tint = MaterialTheme.colorScheme.primary)
                }
                DlStatus.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!batchMode) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onInstall,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("安装", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除记录", tint = MaterialTheme.colorScheme.outline)
                    }
                }
                else -> IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除记录", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** 字节数 → 人类可读大小 */
fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> "未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
