package com.appdian.store.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appdian.store.data.Category
import com.appdian.store.vm.CategoryViewModel

/** 分类管理页：编辑 / 新增 / 删除分类 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    onBack: () -> Unit,
    viewModel: CategoryViewModel = viewModel(factory = viewModelFactory())
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = "新增分类")
            }
        }
    ) { padding ->
        CategoryManageContent(
            categories = ui.categories,
            onEdit = { editing = it },
            onAdd = { adding = true },
            onDelete = { c -> viewModel.deleteCategory(c.id) },
            modifier = Modifier.padding(padding)
        )
    }

    if (adding) {
        CategoryEditDialog(
            initial = Category(id = "", name = ""),
            title = "新增分类",
            onConfirm = { c ->
                if (c.id.isNotBlank() && c.name.isNotBlank()) viewModel.addCategory(c)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }
    editing?.let { c ->
        CategoryEditDialog(
            initial = c,
            title = "编辑分类",
            onConfirm = { updated ->
                viewModel.saveCategories(ui.categories.map { if (it.id == c.id) updated else it })
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/** 编辑/新增分类对话框 */
@Composable
private fun CategoryEditDialog(
    initial: Category,
    title: String,
    onConfirm: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var id by remember { mutableStateOf(initial.id) }
    var name by remember { mutableStateOf(initial.name) }
    var keywords by remember { mutableStateOf(initial.keywords.joinToString("，")) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                if (initial.id.isBlank()) {
                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it },
                        label = { Text("id（英文小写，稳定引用）") },
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("关键词（逗号分隔，用于自动归类）") },
                    minLines = 2,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "提示：id 用于源里声明（如 category: \"tools\"），修改 id 会使已有声明失效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(
                        Category(
                            id = id.ifBlank { name.lowercase() },
                            name = name.trim(),
                            keywords = keywords.split(",", "，", "、")
                                .map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
