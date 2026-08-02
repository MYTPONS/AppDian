package com.appdian.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appdian.store.data.CategoryClassifier
import com.appdian.store.vm.CategorizedEntry
import com.appdian.store.vm.CategoryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 批量分类：按关键词检索「发现页聚合的应用」，勾选多个 → 批量归类。
 * 归类结果与详情页/分类页共享同一份覆盖表（category-overrides.json）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCategorizeScreen(
    onBack: () -> Unit,
    viewModel: CategoryViewModel = viewModel(factory = viewModelFactory())
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<String?>(null) }   // null = 全部
    val selected = remember { SelectedState() }
    // 把勾选集合作为 Compose 可观察状态：点击后能触发重组刷新勾选框
    val selectedKeys by selected.keysFlow.collectAsStateWithLifecycle()

    // 本地过滤：关键词（名称/摘要/来源）+ 分类
    val filtered = remember(ui.entries, query, categoryFilter) {
        ui.entries.filter { e ->
            val q = query.trim()
            val matchQ = q.isEmpty() ||
                e.item.name.contains(q, ignoreCase = true) ||
                (e.item.summary?.contains(q, ignoreCase = true) ?: false) ||
                e.source.sourceName.contains(q, ignoreCase = true)
            val matchC = categoryFilter == null || e.categoryId == categoryFilter
            matchQ && matchC
        }.sortedBy { it.item.name }
    }

    // 勾选中的应用条目（用于批量归类）
    val pickEntries = remember(filtered, selectedKeys) {
        filtered.filter { stableKey(it) in selectedKeys }
    }

    // 每次进入页面都刷新一遍最新条目
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("批量分类") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedKeys.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已选 ${selectedKeys.size} 项",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { selected.clear() }) { Text("清空") }
                    TextButton(
                        onClick = {
                            val keys = pickEntries
                            selected.clear()
                            viewModel.setOverrides(keys, null)
                            android.widget.Toast.makeText(context, "已恢复自动判定", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("恢复自动") }
                    TextButton(
                        onClick = { selected.showPicker = true },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary)
                    ) {
                        Text("归类到…", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 检索框
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("按应用名 / 摘要 / 来源检索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            // 分类筛选 chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { categoryFilter = null },
                    label = { Text("全部") }
                )
                ui.categories.forEach { c ->
                    FilterChip(
                        selected = categoryFilter == c.id,
                        onClick = { categoryFilter = if (categoryFilter == c.id) null else c.id },
                        label = { Text(c.name) }
                    )
                }
                FilterChip(
                    selected = categoryFilter == "__uncategorized",
                    onClick = { categoryFilter = if (categoryFilter == "__uncategorized") null else "__uncategorized" },
                    label = { Text("未分类") }
                )
            }

            if (ui.loading && ui.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (ui.entries.isEmpty()) "暂无应用数据\n先到「发现」页刷新一下\n或检查「设置 → 源管理」里的源" else "没有匹配的应用",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filtered, key = { CategoryClassifier.itemKey(it.source.sourceName, it.item) + "-" + it.item.detailUrl }) { e ->
                        BatchRow(
                            entry = e,
                            checked = stableKey(e) in selectedKeys,
                            onClick = { selected.toggle(stableKey(e)) }
                        )
                    }
                }
            }
        }
    }

    if (selected.showPicker) {
        val keys = pickEntries
        AlertDialog(
            onDismissRequest = { selected.showPicker = false },
            title = { Text("归类到（${keys.size} 项）") },
            text = {
                Column {
                    ui.categories.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected.showPicker = false
                                    viewModel.setOverrides(keys, c.id)
                                    android.widget.Toast.makeText(context, "已归类到「${c.name}」", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(c.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        HorizontalDivider()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected.showPicker = false
                                viewModel.setOverrides(keys, CategoryClassifier.UNCATEGORIZED)
                                android.widget.Toast.makeText(context, "已归入未分类", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Text("未分类", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selected.showPicker = false }) { Text("取消") }
            }
        )
    }
}

/** 单条应用的稳定键：源名 + itemKey + 详情地址，避免因刷新/筛选重建对象导致引用失效 */
private fun stableKey(e: CategorizedEntry): String =
    CategoryClassifier.itemKey(e.source.sourceName, e.item) + "|" + (e.item.detailUrl ?: "")

/** 多选状态：存稳定键集合；keysFlow 由界面收集以驱动勾选刷新 */
private class SelectedState {
    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keysFlow: StateFlow<Set<String>> = _keys.asStateFlow()
    var showPicker by mutableStateOf(false)

    fun toggle(key: String) {
        _keys.update { if (key in it) it - key else it + key }
    }
    fun clear() { _keys.value = emptySet() }
}

@Composable
private fun BatchRow(
    entry: CategorizedEntry,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                entry.item.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${entry.source.sourceName} · ${entry.category?.name ?: "未分类"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
