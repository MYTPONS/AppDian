package com.appdian.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appdian.engine.model.AppItem
import com.appdian.store.vm.CategoryViewModel

/**
 * 分类详情：某分类（或未分类）下的应用列表，跨源聚合。
 * [categoryId] 传 null 表示"未分类"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryId: String?,
    onBack: () -> Unit,
    onOpenDetail: (sourceName: String, item: AppItem) -> Unit,
    viewModel: CategoryViewModel = viewModel(factory = viewModelFactory())
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val category = ui.categories.firstOrNull { it.id == categoryId }
    val entries = viewModel.entriesFor(categoryId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(category?.name ?: "未分类")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (ui.loading && entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "该分类下暂无应用\n下拉刷新试试",
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
                items(entries, key = { "${it.source.sourceName}-${it.item.packageName}-${it.item.detailUrl}-${it.item.name}" }) { e ->
                    AppItemCard(
                        item = e.item,
                        sourceName = e.source.sourceName,
                        onClick = { onOpenDetail(e.source.sourceName, e.item) }
                    )
                }
            }
        }
    }
}
