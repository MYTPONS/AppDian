package com.appdian.store.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource
import com.appdian.store.AppDianApp
import com.appdian.store.data.Category
import com.appdian.store.data.VersionEntry
import com.appdian.store.vm.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(factory = viewModelFactory())
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as AppDianApp
    var showPicker by remember { mutableStateOf(false) }

    val category = ui.item?.let { item ->
        ui.source?.let { src -> viewModel.currentCategory(item, src) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.item?.name ?: "应用详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text("正在加载详情…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
                ui.item == null -> Text("没有详情", modifier = Modifier.padding(24.dp))
                else -> DetailContent(
                    item = ui.item!!,
                    source = ui.source,
                    error = ui.error,
                    context = context,
                    category = category,
                    versions = ui.versions,
                    activeVersion = ui.activeVersion,
                    onSelectVersion = { v -> viewModel.selectVersion(v) },
                    onCategoryClick = { showPicker = true }
                )
            }
        }
    }

    // 更改分类对话框
    val itemForPicker = ui.item
    val sourceForPicker = ui.source
    if (showPicker && itemForPicker != null && sourceForPicker != null) {
        CategoryPickerDialog(
            categories = app.categoryRepository.list(),
            current = category,
            onSelect = { id ->
                viewModel.setOverride(itemForPicker, sourceForPicker, id)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/** 更改分类对话框：自动判定（清除覆盖）/ 各分类 / 未分类 */
@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    current: Category?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更改分类") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(320.dp)) {
                item(key = "__auto") {
                    androidx.compose.material3.ListItem(
                        headlineContent = {
                            Text(if (current != null) "自动判定：${current.name}" else "自动判定（未命中）")
                        },
                        supportingContent = { Text("清除手动归类，恢复自动判定") },
                        modifier = Modifier.clickable { onSelect(null) }
                    )
                }
                items(categories, key = { it.id }) { c ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text(c.id) },
                        trailingContent = { if (current?.id == c.id) Icon(Icons.Default.Check, contentDescription = "当前分类", tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onSelect(c.id) }
                    )
                }
                item(key = "__none") {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("未分类") },
                        supportingContent = { Text("强制归入未分类（不再自动判定）") },
                        trailingContent = { if (current == null) Icon(Icons.Default.Check, contentDescription = "当前分类", tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onSelect(com.appdian.store.data.CategoryClassifier.UNCATEGORIZED) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DetailContent(
    item: AppItem,
    source: AppSource?,
    error: String?,
    context: Context,
    category: Category? = null,
    versions: List<VersionEntry> = emptyList(),
    activeVersion: String? = null,
    onSelectVersion: (String) -> Unit = {},
    onCategoryClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.icon != null) {
                    AsyncImage(model = item.icon, contentDescription = item.name, modifier = Modifier.size(68.dp))
                } else {
                    Text(
                        item.name.take(1),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                item.version?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                item.packageName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                source?.let {
                    Text("来源：${it.sourceName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // 版本选择：同源/跨源同名应用存在多个版本时展示，默认选版本最高的
        if (versions.size > 1) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    "共 ${versions.size} 个版本",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    versions.forEach { v ->
                        val selected = v.version?.trim() == activeVersion?.trim()
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = { if (!selected) onSelectVersion(v.version ?: "") },
                            label = { Text(v.version ?: "未知版本") }
                        )
                    }
                }
                Text(
                    "默认显示版本最高的，可点选其他版本下载",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        error?.let {
            Text(
                "详情加载失败：$it（显示列表信息）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 分类（点击可更改）
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onCategoryClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                category?.name ?: "未分类",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "更改",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
        }

        // 元信息
        val meta = listOfNotNull(
            item.developer?.let { "开发者" to it },
            item.lastUpdate?.let { "更新时间" to it },
            item.downloadSize?.let { "大小" to humanSize(it) }
        )
        if (meta.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                meta.forEach { (k, v) ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("$k：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // 简介
        item.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text("简介", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 下载区块：真实下载（系统 DownloadManager）+ 浏览器/复制
        item.downloadUrl?.takeIf { it.isNotBlank() }?.let { url ->
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                Text("下载", style = MaterialTheme.typography.titleSmall)
                Text(
                    item.downloadName ?: url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Button(
                    onClick = { context.download(url, item, source) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("下载 APK")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { context.openBrowser(url) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("浏览器打开")
                    }
                    OutlinedButton(
                        onClick = { context.copyText(url) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("复制链接")
                    }
                }
                Text(
                    "下载后在通知栏可查看进度；完成会保存到 下载/应用大典/。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // 详情页原始地址
        item.detailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            Text(
                "详情地址：$url",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private fun humanSize(raw: String): String {
    val n = raw.toLongOrNull() ?: return raw
    if (n <= 0) return raw
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = n.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) {
        v /= 1024
        u++
    }
    return "%.1f %s".format(v, units[u])
}

private fun Context.openBrowser(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun Context.download(url: String, item: AppItem, source: AppSource?) {
    runCatching {
        // 去重键含版本：不同版本可分别下载，同版本不重复
        val appKey = buildString {
            append(item.name.ifBlank { item.packageName ?: url })
            item.version?.let { if (it.isNotBlank()) append("@$it") }
        }
        // 文件名用应用名（+版本号），不用网站的随意命名
        val fileName = buildString {
            append(item.name.ifBlank { item.downloadName ?: "应用" })
            item.version?.let { if (it.isNotBlank()) append("-$it") }
            append(".apk")
        }
        val id = com.appdian.store.download.DownloadService.enqueue(
            context = this,
            url = url,
            title = item.name.ifBlank { "应用下载" },
            fileName = fileName,
            appKey = appKey,
            userAgent = source?.userAgent,
            referer = source?.sourceUrl,
            sourceName = source?.sourceName,
            version = item.version
        )
        if (id == -1L) {
            android.widget.Toast.makeText(
                this,
                "该应用已有下载记录，可到「下载」页查看/安装",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                this,
                "已加入下载，可到「下载」页或通知栏查看进度",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }.onFailure {
        android.widget.Toast.makeText(this, "下载失败：${it.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun Context.copyText(text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("应用大典", text))
}
