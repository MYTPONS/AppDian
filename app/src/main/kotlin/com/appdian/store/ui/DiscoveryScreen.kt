package com.appdian.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appdian.engine.model.AppItem
import com.appdian.store.vm.DiscoveryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onOpenDetail: (sourceName: String, item: AppItem) -> Unit,
    onOpenSources: () -> Unit,
    viewModel: DiscoveryViewModel = viewModel(factory = viewModelFactory())
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用大典") },
                actions = {
                    IconButton(onClick = { viewModel.refresh(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
    PullToRefreshBox(
        isRefreshing = ui.loading,
        onRefresh = { viewModel.refresh(force = true) },
        modifier = Modifier.fillMaxSize().padding(padding)
    ) {
        when {
            ui.groups.isEmpty() && ui.loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text("正在加载…", modifier = Modifier.padding(top = 12.dp))
                }
            }
            ui.groups.isEmpty() -> {
                EmptyDiscoveryState(ui.hasSources, onOpenSources)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (ui.loading) {
                        item(key = "loading-hint") {
                            Text(
                                "正在刷新…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                    ui.groups.forEach { group ->
                    item(key = "header-${group.source.sourceName}-${group.title}") {
                        GroupHeader("${group.source.sourceName} · ${group.title}")
                    }
                    if (group.error != null) {
                        item(key = "err-${group.source.sourceName}-${group.title}") {
                            Text(
                                "${group.source.sourceName}：加载失败（${group.error}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }
                    items(group.items, key = { "it-${group.source.sourceName}-${group.title}-${it.name}-${it.packageName}-${it.detailUrl}-${it.version}" }) { item ->
                        AppItemCard(
                            item = item,
                            sourceName = group.source.sourceName,
                            onClick = { onOpenDetail(group.source.sourceName, item) }
                        )
                    }
                    item(key = "div-${group.source.sourceName}-${group.title}") {
                        SectionSpacer()
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    }
}
}

@Composable
private fun EmptyDiscoveryState(hasSources: Boolean, onOpenSources: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (hasSources) "暂无可用内容\n请下拉刷新试试" else "还没有可用的应用源\n去「源管理」导入一个源吧",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
