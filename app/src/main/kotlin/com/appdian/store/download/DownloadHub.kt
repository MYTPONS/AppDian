package com.appdian.store.download

import android.content.ContentUris
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
        if (appKey != null) {
            val existing = _tasks.value.firstOrNull { it.appKey == appKey }
            if (existing != null) {
                // 失败/已取消的任务允许重新入队下载；进行中/暂停/已完成则去重
                if (existing.status != DlStatus.FAILED && existing.status != DlStatus.CANCELED) return -1L
                _tasks.value = _tasks.value.filterNot { it.id == existing.id }
                _queue.removeAll { it == existing.id }
            }
        }
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

    /** 批量删除任务记录；[deleteFile] 时同时删除各自已下载的文件。返回实际删除的文件数 */
    fun removeAll(ids: List<Long>, deleteFile: Boolean = false): Int {
        val targets = _tasks.value.filter { it.id in ids }
        ids.forEach { running.remove(it)?.cancel() }
        _tasks.value = _tasks.value.filterNot { it.id in ids }
        val deleted = if (deleteFile) targets.count { deleteLocalFile(it) } else 0
        persist()
        return deleted
    }

    /**
     * 删除任务对应的本地已下载文件（API29+ 走 MediaStore，低版本走文件路径）。
     * 返回是否真的删除了文件。
     *
     * 注意：MediaStore 在下载目录遇到同名文件时会自动改名（如 微信.apk → 微信(1).apk），
     * 精确按 DISPLAY_NAME 匹配会漏删，因此按 目录 + 文件名前缀变体 模糊匹配后全部删除。
     */
    fun deleteLocalFile(task: DownloadTask): Boolean {
        val path = task.localPath ?: return false
        val ctx = appContext ?: return false
        if (Build.VERSION.SDK_INT >= 29) {
            val rel = Environment.DIRECTORY_DOWNLOADS + "/应用大典"
            val ids = mutableListOf<Long>()
            ctx.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(rel),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (sameMediaName(name, path)) ids.add(c.getLong(0))
                }
            }
            var deleted = 0
            ids.forEach { id ->
                deleted += ctx.contentResolver.delete(
                    ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    null, null
                )
            }
            return deleted > 0
        } else {
            return runCatching { File(path).delete() }.getOrDefault(false)
        }
    }

    /**
     * 判断 MediaStore 里的文件名是否对应同一个下载文件：
     * 精确同名，或系统冲突改名变体（微信.apk → 微信(1).apk）。
     */
    private fun sameMediaName(name: String, path: String): Boolean {
        if (name == path) return true
        val stem = path.substringBeforeLast('.')
        val ext = path.substringAfterLast('.', "")
        return name.startsWith("$stem(") && name.endsWith(if (ext.isEmpty()) "" else ".$ext")
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
