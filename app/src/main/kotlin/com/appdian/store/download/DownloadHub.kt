package com.appdian.store.download

import android.content.Context
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 下载中心（单例）：维护任务队列与实时进度，
 * 由 [DownloadService] 驱动，UI 直接观察 [tasks]。
 *
 * 任务记录持久化到 filesDir/downloads.json：
 *  - 每次任务状态变化后自动保存（后台 IO）
 *  - App 启动时调用 [init] 恢复历史记录（含已完成/失败的）
 *  - 未完成任务（排队中/下载中）重启后标记为失败，可手动重试
 */
object DownloadHub {

    private const val FILE_NAME = "downloads.json"
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _queue = ArrayDeque<Long>()      // 待执行任务 id
    private val running = mutableMapOf<Long, kotlinx.coroutines.Job>()
    private val idCounter = AtomicLong(0)
    private val saveMutex = Mutex()
    @Volatile private var appContext: Context? = null
    @Volatile private var storeRepo: com.appdian.store.data.StoreRepository? = null
    private val protectedNames = mutableSetOf<String>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** App 启动时恢复历史记录；未完成任务标记为失败 */
    fun init(context: Context) {
        appContext = context.applicationContext
        storeRepo = (context.applicationContext as com.appdian.store.AppDianApp).storeRepository
        val f = File(context.filesDir, FILE_NAME)
        val restored = if (f.exists()) {
            runCatching {
                json.decodeFromString<List<DownloadTask>>(f.readText())
            }.getOrElse { emptyList() }
        } else emptyList()
        val marked = restored.map { t ->
            when (t.status) {
                DlStatus.QUEUED, DlStatus.RUNNING, DlStatus.PAUSED ->
                    t.copy(status = DlStatus.FAILED, error = "应用重启，任务中断，可点重试")
                else -> t
            }
        }
        // 最新在前
        _tasks.value = marked.sortedByDescending { it.id }
        idCounter.set(marked.maxOfOrNull { it.id } ?: 0L)
        persist()
    }

    /**
     * 入队。返回任务 id；若已有同一应用的记录则返回 -1（不重复入队）。
     * [appKey] 用于去重（如应用名），null 则不去重。
     */
    fun enqueue(
        url: String,
        title: String,
        fileName: String,
        appKey: String? = null,
        userAgent: String? = null,
        referer: String? = null,
        sourceName: String? = null,
        version: String? = null
    ): Long {
        if (appKey != null && hasRecord(appKey)) return -1L
        val id = idCounter.incrementAndGet()
        _tasks.value = listOf(
            DownloadTask(
                id, url, title, fileName,
                appKey = appKey, userAgent = userAgent, referer = referer,
                sourceName = sourceName, version = version
            )
        ) + _tasks.value
        _queue.addLast(id)
        persist()
        return id
    }

    /** 是否已有该应用的下载记录（去重用）：任务存在 或 本地已存在同名 APK */
    fun hasRecord(appKey: String): Boolean =
        _tasks.value.any { it.appKey == appKey }

    fun cancel(id: Long) {
        running.remove(id)?.cancel()
        update(id) { it.copy(status = DlStatus.CANCELED, error = "已取消") }
    }

    fun remove(id: Long, deleteFile: Boolean = false) {
        val t = _tasks.value.firstOrNull { it.id == id }
        running.remove(id)?.cancel()
        _tasks.value = _tasks.value.filterNot { it.id == id }
        if (deleteFile) t?.let { deleteLocalFile(it) }
        persist()
    }

    /** 批量删除任务记录；[deleteFile] 时同时删除各自已下载的文件 */
    fun removeAll(ids: List<Long>, deleteFile: Boolean = false) {
        val targets = _tasks.value.filter { it.id in ids }
        ids.forEach { running.remove(it)?.cancel() }
        _tasks.value = _tasks.value.filterNot { it.id in ids }
        if (deleteFile) targets.forEach { deleteLocalFile(it) }
        persist()
    }

    /** 删除任务对应的本地已下载文件（API29+ 走 MediaStore，低版本走文件路径） */
    fun deleteLocalFile(task: DownloadTask) {
        val path = task.localPath ?: return
        val ctx = appContext ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            // localPath 保存的是 MediaStore 的 displayName
            ctx.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(path, Environment.DIRECTORY_DOWNLOADS + "/应用大典")
            )
        } else {
            runCatching { File(path).delete() }
        }
    }

    /** 换源重试：更新任务（换 URL/Referer 等）后重新入队，进度清零 */
    fun switchSource(id: Long, f: (DownloadTask) -> DownloadTask) {
        update(id) { t ->
            f(t).copy(
                status = DlStatus.QUEUED, progress = 0,
                downloadedBytes = 0, totalBytes = 0, localPath = null
            )
        }
        _queue.addFirst(id)
    }

    fun retry(id: Long) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        update(id) { it.copy(status = DlStatus.QUEUED, error = null, progress = 0, downloadedBytes = 0, localPath = null) }
        _queue.addFirst(id)
    }

    fun nextPending(): DownloadTask? {
        while (_queue.isNotEmpty()) {
            val id = _queue.removeFirst()
            val t = _tasks.value.firstOrNull { it.id == id && it.status == DlStatus.QUEUED } ?: continue
            return t
        }
        return null
    }

    fun markRunning(id: Long, job: kotlinx.coroutines.Job) {
        running[id] = job
        update(id) { it.copy(status = DlStatus.RUNNING) }
    }

    fun update(id: Long, f: (DownloadTask) -> DownloadTask) {
        val old = _tasks.value
        val changed = old.map { if (it.id == id) f(it) else it }
        if (changed != old) {
            _tasks.value = changed
            persist()
        }
    }

    fun task(id: Long): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    /** 保存到公共下载目录，返回本地路径；失败抛异常 */
    fun saveToPublicDir(context: Context, temp: File, fileName: String): String {
        val name = fileName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(120)
        val displayName = if (name.endsWith(".apk", true)) name else "$name.apk"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/应用大典")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建下载文件")
            context.contentResolver.openOutputStream(uri).use { out ->
                temp.inputStream().use { it.copyTo(out!!) }
            }
            // 已归档到 MediaStore，删除临时文件
            temp.delete()
            return displayName
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "应用大典")
            dir.mkdirs()
            val dest = File(dir, displayName)
            temp.copyTo(dest, overwrite = true)
            temp.delete()
            return dest.absolutePath
        }
    }

    /**
     * 数据保护同步：把「未完成/未取消」下载任务的应用名
     * 保护到缓存（搜索缓存 + 条目池），确保下载失败换源/换链接时数据立即可用；
     * 任务完成/取消后解除保护。
     */
    private fun syncProtection() {
        val repo = storeRepo ?: return
        val names = protectedAppNames(_tasks.value)
        val current = protectedNames.toSet()
        names.filter { it !in current }.forEach {
            repo.protectApp(it)
            protectedNames.add(it)
        }
        current.filter { it !in names }.forEach {
            repo.unprotectApp(it)
            protectedNames.remove(it)
        }
    }

    /** 需要保护的下载任务应用名：未完成、未取消的任务（含失败待换源/重试） */
    fun protectedAppNames(tasks: List<DownloadTask>): List<String> =
        tasks.filter { it.status != DlStatus.DONE && it.status != DlStatus.CANCELED }
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    // ---------------- 持久化 ----------------

    private fun persist() {
        syncProtection()
        val snapshot = _tasks.value
        val ctx = appContext ?: return
        ioScope.launch {
            saveMutex.withLock {
                runCatching {
                    val f = File(ctx.filesDir, FILE_NAME)
                    f.writeText(json.encodeToString(ListSerializer(DownloadTask.serializer()), snapshot))
                }
            }
        }
    }
}
