package com.appdian.store.ui

import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.appdian.engine.model.AppSource
import com.appdian.store.vm.SourcesViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SourcesViewModel = viewModel(factory = viewModelFactory())
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        (context.applicationContext as com.appdian.store.AppDianApp).logEvent("进入源管理页")
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("源管理") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("导入源")
            }
            FilledTonalButton(
                onClick = {
                    val text = viewModel.exportAll()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_SUBJECT, "应用大典·应用源导出")
                    }
                    context.startActivity(Intent.createChooser(send, "分享应用源"))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("导出全部")
            }
        }

        ui.importSuccess?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        ui.importError?.let {
            Text(
                "导入失败：$it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (ui.sources.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "还没有应用源。\n点击「导入源」粘贴 JSON 源，\n或等待内置示例源就绪。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                item {
                    Text(
                        "应用源是 JSON 规则文件，描述如何从网站解析应用列表/详情。点击可查看源内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                itemsIndexed(ui.sources, key = { i, _ -> "$i" }) { _, src ->
                    SourceRow(
                        source = src,
                        onToggle = { viewModel.toggle(src.sourceName, it) },
                        onDelete = { confirmDelete = src.sourceName },
                        onShowRaw = { rawText = viewModel.readRaw(src.sourceName) ?: ""; showImportDialog = false }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }

    // 导入对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; rawText = "" },
            title = { Text("导入应用源") },
            text = {
                Column {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        placeholder = {
                            Text("粘贴 JSON 源内容，例如 F-Droid 源：\n{ \"sourceName\": \"...\", \"sourceUrl\": \"...\", ... }")
                        },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "提示：可从朋友分享、GitHub 等渠道获取源 JSON。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.import(rawText)
                    showImportDialog = false
                    rawText = ""
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; rawText = "" }) { Text("取消") }
            }
        )
    }

    // 查看源原始 JSON
    if (rawText.isNotEmpty() && !showImportDialog) {
        AlertDialog(
            onDismissRequest = { rawText = "" },
            title = { Text("源内容") },
            text = {
                Text(
                    rawText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 20,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, rawText)
                    }
                    context.startActivity(Intent.createChooser(send, "分享该源"))
                    rawText = ""
                }) { Text("分享") }
            },
            dismissButton = {
                TextButton(onClick = { rawText = "" }) { Text("关闭") }
            }
        )
    }

    // 删除确认
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除源") },
            text = { Text("确定删除源「$name」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(name)
                    confirmDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
    }
    }
}

@Composable
private fun SourceRow(
    source: AppSource,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onShowRaw: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onShowRaw)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            if (source.sourceIcon != null) {
                AsyncImage(
                    model = source.sourceIcon,
                    contentDescription = source.sourceName,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Text(
                    source.sourceName.take(1),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(source.sourceName, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "v${source.sourceVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                val capCount = (if (source.search != null) 1 else 0) +
                    (if (source.detail != null) 1 else 0) +
                    source.discovery.size
                Text(
                    "$capCount 个区块",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Switch(checked = source.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
