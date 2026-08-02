package com.appdian.store

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.appdian.store.data.CategoryRepository
import com.appdian.store.data.SettingsStore
import com.appdian.store.data.SourceRepository
import com.appdian.store.data.StoreRepository
import com.appdian.store.download.DownloadService
import com.appdian.store.net.HttpFetcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** 应用容器：手动依赖注入（避免引入 Hilt/Koin 的复杂度） */
class AppDianApp : Application(), ImageLoaderFactory {

    lateinit var storeRepository: StoreRepository
        private set
    lateinit var sourceRepository: SourceRepository
        private set
    lateinit var categoryRepository: CategoryRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set

    /**
     * 全局图片加载器：带浏览器 UA 的 OkHttp 客户端。
     * 部分源站（如华军）对无 UA / 非浏览器 UA 的图片请求可能拦截，
     * 统一用浏览器 UA 提升图标加载成功率。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)
                    .build()
            )
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        installCrashLog()
        settingsStore = SettingsStore(this)
        sourceRepository = SourceRepository(this)
        sourceRepository.seedIfEmpty()
        categoryRepository = CategoryRepository(this)
        storeRepository = StoreRepository(
            HttpFetcher(defaultUa = { settingsStore.defaultUserAgent }),
            sourceRepository
        )
        // 恢复历史下载记录（重启后仍能看到已完成/失败的记录）
        com.appdian.store.download.DownloadHub.init(this)
    }

    /** 崩溃日志内容（含时间戳），供设置页「崩溃日志」入口展示 */
    fun crashLog(): String = runCatching {
        val prefs = getSharedPreferences("appdian_crash", MODE_PRIVATE)
        val recent = prefs.getString("last", null)
        val lastEvent = prefs.getString("last_event", null)
        val fileLog = runCatching {
            val f = File(filesDir, "crash.log")
            if (f.exists()) f.readText() else ""
        }.getOrDefault("")
        buildString {
            lastEvent?.let { appendLine("【最近操作】$it"); appendLine() }
            recent?.let { appendLine("【最近崩溃】$it"); appendLine() }
            if (fileLog.isNotBlank()) appendLine("【完整日志】"); append(fileLog)
        }
    }.getOrDefault("（读取崩溃日志失败）")

    /** 崩溃日志入口可见的说明文字（无记录时提示用户怎么触发） */
    fun crashLogEmpty(): Boolean = crashLog().isBlank()

    /** 记录用户操作事件（配合崩溃日志定位「闪退前最后做了什么」） */
    fun logEvent(msg: String) {
        runCatching {
            getSharedPreferences("appdian_crash", MODE_PRIVATE)
                .edit().putString("last_event", "${System.currentTimeMillis()} $msg").apply()
        }
    }

    /**
     * 全局崩溃日志：崩溃堆栈同步写入 filesDir/crash.log（强制落盘）+ SharedPreferences 冗余。
     * 位置：Android/data/com.appdian.store/files/crash.log
     */
    private fun installCrashLog() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val text = buildString {
                    appendLine("=== ${System.currentTimeMillis()} [${thread.name}] ===")
                    appendLine(throwable.toString())
                    throwable.stackTrace.take(40).forEach { appendLine("    at $it") }
                    var cause = throwable.cause
                    var depth = 0
                    while (cause != null && depth < 5) {
                        appendLine("Caused by: ${cause}")
                        cause.stackTrace.take(15).forEach { appendLine("    at $it") }
                        cause = cause.cause
                        depth++
                    }
                    appendLine()
                }
                // 1) 文件：强制落盘（flush + fd.sync），避免进程被杀丢数据
                val f = File(filesDir, "crash.log")
                java.io.FileOutputStream(f, true).use { fos ->
                    fos.write(text.toByteArray(Charsets.UTF_8))
                    fos.flush()
                    fos.fd.sync()
                }
                // 2) prefs 冗余：文件系统异常时设置页也能读到
                getSharedPreferences("appdian_crash", MODE_PRIVATE)
                    .edit().putString("last", text).apply()
            }
            prev?.uncaughtException(thread, throwable)
        }
    }
}
