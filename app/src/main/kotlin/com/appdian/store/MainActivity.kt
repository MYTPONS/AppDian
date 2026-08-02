package com.appdian.store

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appdian.engine.model.AppItem
import com.appdian.store.ui.theme.AppDianTheme
import com.appdian.store.ui.BatchCategorizeScreen
import com.appdian.store.ui.CategoryDetailScreen
import com.appdian.store.ui.CategoryManageScreen
import com.appdian.store.ui.CategoryScreen
import com.appdian.store.ui.DownloadsScreen
import com.appdian.store.ui.DetailScreen
import com.appdian.store.ui.DiscoveryScreen
import com.appdian.store.ui.SearchScreen
import com.appdian.store.ui.AboutScreen
import com.appdian.store.ui.SettingsScreen
import com.appdian.store.ui.SourcesScreen
import com.appdian.store.ui.viewModelFactory
import com.appdian.store.vm.DetailViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppDianTheme {
                AppRoot()
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val tabs = remember {
        listOf(
            Tab("discovery", "发现", Icons.Default.Home),
            Tab("categories", "分类", Icons.Default.Folder),
            Tab("search", "搜索", Icons.Default.Search),
            Tab("downloads", "下载", Icons.Default.Download),
            Tab("sources", "设置", Icons.Default.Settings)
        )
    }

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // API 33+ 请求通知权限（下载进度通知需要）
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 详情页需要 Activity 级共享的 VM 来承接列表传来的条目
    val detailViewModel: DetailViewModel = viewModel(factory = viewModelFactory())

    // 从 Application 容器取源仓库（点击时才查一次源，源文件极小）
    val app = LocalContext.current.applicationContext as AppDianApp
    val openDetail: (sourceName: String, item: AppItem) -> Unit = { sourceName, item ->
        val source = app.sourceRepository.get(sourceName)
        if (source != null) {
            detailViewModel.load(source, item)
            try { nav.navigate("detail") } catch (e: Exception) {
                android.widget.Toast.makeText(app, "打开详情失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 这些子页面自带功能栏/返回，不显示底部主导航（避免两套功能栏重叠）
    val hideBottomNav = currentRoute in setOf(
        "detail", "sourceManage", "batchCategorize", "categoryManage", "about"
    )

    Scaffold(
        bottomBar = {
            if (hideBottomNav) {
                // 子页面不显示底部导航
            } else {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                try {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(app, "切换页面失败", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "discovery",
            modifier = Modifier.padding(padding)
        ) {
            composable("discovery") {
                DiscoveryScreen(
                    onOpenDetail = openDetail,
                    onOpenSources = { nav.navigate("sources") }
                )
            }
            composable("categories") {
                CategoryScreen(
                    onOpenCategory = { id ->
                        nav.navigate("category/${id ?: "uncategorized"}")
                    },
                    onOpenCategoryManage = { nav.navigate("categoryManage") }
                )
            }
            composable("category/{categoryId}") { entry ->
                val id = entry.arguments?.getString("categoryId")
                CategoryDetailScreen(
                    categoryId = id?.takeIf { it != "uncategorized" },
                    onBack = { nav.popBackStack() },
                    onOpenDetail = openDetail
                )
            }
            composable("categoryManage") {
                CategoryManageScreen(onBack = { nav.popBackStack() })
            }
            composable("search") {
                SearchScreen(
                    onOpenDetail = openDetail
                )
            }
            // 设置页（第 5 tab）：源管理 / 批量分类 / 分类管理 / 分类配置导入导出
            composable("sources") {
                SettingsScreen(
                    onOpenSources = {
                        app.logEvent("点击源管理")
                        runCatching { nav.navigate("sourceManage") }
                    },
                    onOpenBatchCategorize = { runCatching { nav.navigate("batchCategorize") } },
                    onOpenCategoryManage = { runCatching { nav.navigate("categoryManage") } },
                    onOpenAbout = { runCatching { nav.navigate("about") } }
                )
            }
            composable("sourceManage") { SourcesScreen(onBack = { nav.popBackStack() }) }
            composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
            composable("batchCategorize") {
                BatchCategorizeScreen(onBack = { nav.popBackStack() })
            }
            composable("downloads") { DownloadsScreen() }
            composable("detail") {
                DetailScreen(onBack = { nav.popBackStack() }, viewModel = detailViewModel)
            }
        }
    }
}
