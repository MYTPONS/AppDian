package com.appdian.store.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** GitHub 开源仓库地址（设置页「关于」与「检查更新」共用） */
const val GITHUB_REPO_URL = "https://github.com/MYTPONS/AppDian"
const val GITHUB_REPO_API = "https://api.github.com/repos/MYTPONS/AppDian"

/** 版本信息（与 app/build.gradle.kts 的 versionName 保持一致） */
const val APP_VERSION_NAME = "0.1.0"

/**
 * 关于页：项目详细介绍 + 开源信息（仓库 / License / 作者 / 技术栈 / 内置源）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于应用大典") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            // 头部：图标 + 名称 + 版本
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("应用大典", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "v$APP_VERSION_NAME · 开源应用软件库",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { AboutSection("项目介绍") }
            item {
                AboutCard(
                    text = "应用大典（AppDian）是一个仿照 legado（阅读）书源机制自研的 Android 应用软件库。\n\n" +
                        "不依赖任何固定的应用商店：应用源（AppSource）是一份 JSON 规则文件，描述如何从任意网站解析应用列表、详情和下载地址。\n\n" +
                        "用户自由导入 / 分享源，绕开搜索引擎里铺天盖地的病毒和伪站，直达可信来源。"
                )
            }

            item { AboutSection("开源信息") }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        AboutRow(Icons.Default.Link, "GitHub 仓库", GITHUB_REPO_URL) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL))
                            )
                            Toast.makeText(context, "打开 GitHub 仓库", Toast.LENGTH_SHORT).show()
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        AboutRow(Icons.Default.Gavel, "开源协议", "MIT License") {}
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        AboutRow(Icons.Default.Person, "作者", "MYTPONS") {}
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }

            item { AboutSection("功能特性") }
            item {
                AboutCard(
                    text = "- 规则引擎：css / json / regex / text 规则、|| 回退、模板变量、自研轻量 JsonPath\n" +
                        "- 发现 / 搜索 / 详情：多源并发、增量上屏、数据缓存复用\n" +
                        "- 分类：关键词智能归类 + 手动归类 + 批量归类\n" +
                        "- 下载管理器：前台服务下载、通知进度、失败自动换源换链接\n" +
                        "- 源管理：导入 / 导出 / 启停应用源\n" +
                        "- 设置：全局 User-Agent、分类配置导入导出"
                )
            }

            item { AboutSection("内置演示源") }
            item {
                AboutCard(
                    text = "- F-Droid（JSON）：官方 API，全部开源软件\n" +
                        "- GitHub Releases（JSON）：NetGuard / Termux 真实 APK\n" +
                        "- GitHub Web 示例（HTML）：css 规则抓网页\n" +
                        "- 华军软件园（HTML）：真实反爬实战，Referer + 规则管道提取直链"
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    "MIT License\nCopyright (c) 2026 MYTPONS\n\n本项目按 MIT 协议开源，自由使用 / 修改 / 分发，注明出处即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun AboutCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun AboutRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
    }
}
