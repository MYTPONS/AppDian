package com.appdian.store.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.appdian.store.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 前台下载服务：串行执行 DownloadHub 队列里的任务，
 * OkHttp 流式写入临时文件，实时更新通知进度，完成后归档到公共下载目录。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeId: Long = -1

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("应用大典下载")
            .setContentText("等待下载任务…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build())
        loop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getLongExtra("id", -1L).let { if (it > 0) DownloadHub.cancel(it) }
            return START_NOT_STICKY
        }
        loop()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 串行消费队列；队列空时自动停服务 */
    private fun loop() {
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            while (true) {
                val task = DownloadHub.nextPending() ?: break
                runTask(task)
            }
            stopSelf()
        }
    }

    private suspend fun runTask(task: DownloadTask) {
        activeId = task.id
        DownloadHub.markRunning(task.id, coroutineContext[Job]!!)
        updateNotification(task.title, -1)
        val temp = File(cacheDir, "dl_${task.id}.part")
        var call: okhttp3.Call? = null
        coroutineContext[Job]!!.invokeOnCompletion { call?.cancel() }
        try {
            val rb = Request.Builder().url(task.url)
            val ua = task.userAgent?.takeIf { it.isNotBlank() && it.all { ch -> ch.code < 128 } }
                ?: com.appdian.store.data.SettingsStore.currentUserAgent(this)
            rb.header("User-Agent", ua)
            task.referer?.takeIf { it.isNotBlank() }?.let { rb.header("Referer", it) }
            call = client.newCall(rb.build())
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val total = resp.body?.contentLength() ?: -1L
                resp.body!!.byteStream().use { input ->
                    temp.outputStream().use { out ->
                        var done = 0L
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (!coroutineContext.isActive) throw kotlinx.coroutines.CancellationException()
                            val p = if (total > 0) ((done * 100) / total).toInt() else -1
                            DownloadHub.update(task.id) {
                                it.copy(status = DlStatus.RUNNING, progress = p, totalBytes = total, downloadedBytes = done)
                            }
                            if (p >= 0 && p % 5 == 0) updateNotification(task.title, p)
                        }
                    }
                }
                if (total > 0 && temp.length() < total) throw IllegalStateException("下载不完整")
            }
            val displayName = task.fileName.ifBlank {
                task.title.replace(Regex("[\\/:*?\"<>|\\s]+"), "_") + ".apk"
            }
            val path = withContext(Dispatchers.IO) {
                DownloadHub.saveToPublicDir(this@DownloadService, temp, displayName)
            }
            DownloadHub.update(task.id) { it.copy(status = DlStatus.DONE, progress = 100, localPath = path) }
            showDoneNotification(task.title, path)
        } catch (e: kotlinx.coroutines.CancellationException) {
            temp.delete()
            DownloadHub.update(task.id) { it.copy(status = DlStatus.CANCELED, error = "已取消") }
        } catch (e: Exception) {
            temp.delete()
            // 下载失败：尝试自动换源（其他源的同名同版本应用）
            val switched = runCatching { trySwitchSource(task) }.getOrDefault(false)
            if (!switched) {
                val err = e.message ?: "下载失败"
                DownloadHub.update(task.id) {
                    it.copy(
                        status = DlStatus.FAILED,
                        error = if (task.error.isNullOrBlank()) err else "${task.error}；$err"
                    )
                }
                showFailedNotification(task.title, err)
            }
        }
        activeId = -1
    }

    /**
     * 失败自动换源/换链接：同源内换另一个下载链接，或换其他源的同名（同版本）应用；
     * 成功返回 true。
     */
    private suspend fun trySwitchSource(task: DownloadTask): Boolean {
        val app = applicationContext as com.appdian.store.AppDianApp
        val alt = SourceSwitcher(app.storeRepository).find(task) ?: return false
        val tried = (task.triedSources + listOfNotNull(task.sourceName)).distinct()
        val triedLinks = (task.triedLinks + listOf(task.url)).distinct()
        val note = if (alt.sameSource) {
            "链接下载失败，自动切换同源的另一个下载链接重试"
        } else {
            "源「${task.sourceName ?: "原"}」下载失败，自动切换到「${alt.sourceName}」重试"
        }
        DownloadHub.switchSource(task.id) { t ->
            t.copy(
                sourceName = alt.sourceName,
                url = alt.url,
                userAgent = alt.userAgent,
                referer = alt.referer,
                triedSources = tried,
                triedLinks = triedLinks,
                error = note
            )
        }
        return true
    }

    // ---------------- 通知 ----------------

    private fun updateNotification(title: String, progress: Int) {
        val n = if (progress >= 0) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(title)
                .setContentText("下载中 $progress%")
                .setProgress(100, progress, false)
                .setOngoing(true)
                .addAction(0, "取消", cancelIntent(activeId))
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(title)
                .setContentText("下载中…")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .addAction(0, "取消", cancelIntent(activeId))
                .build()
        }
        nm().notify(NOTIF_ID, n)
    }

    private fun showDoneNotification(title: String, path: String) {
        val openIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(InstallUtil.uriOf(this, path), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pi = PendingIntent.getActivity(this, path.hashCode(), openIntent, PendingIntent.FLAG_IMMUTABLE)
        nm().notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("下载完成：$title")
            .setContentText("点击安装 $path")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build())
    }

    private fun showFailedNotification(title: String, err: String) {
        nm().notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("下载失败：$title")
            .setContentText(err)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build())
    }

    private fun cancelIntent(id: Long): PendingIntent {
        val i = Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra("id", id)
        return PendingIntent.getService(this, id.toInt(), i, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            nm().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIF_ID = 1001
        const val ACTION_CANCEL = "com.appdian.store.CANCEL_DOWNLOAD"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** 入队并启动服务；返回任务 id，重复应用返回 -1 */
        fun enqueue(
            context: Context,
            url: String,
            title: String,
            fileName: String,
            appKey: String? = null,
            userAgent: String? = null,
            referer: String? = null,
            sourceName: String? = null,
            version: String? = null
        ): Long {
            val id = DownloadHub.enqueue(url, title, fileName, appKey, userAgent, referer, sourceName, version)
            if (id > 0) start(context)
            return id
        }

        fun start(context: Context) {
            val i = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }
}
