package com.appdian.store.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import com.appdian.store.download.DownloadHub
import com.appdian.store.download.DownloadService
import com.appdian.store.download.DownloadTask
import kotlinx.coroutines.flow.StateFlow

class DownloadsViewModel : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = DownloadHub.tasks

    fun cancel(id: Long) = DownloadHub.cancel(id)

    fun retry(context: Context, id: Long) {
        DownloadHub.retry(id)
        DownloadService.start(context)
    }

    /** 删除任务记录；[deleteFile] 时同时删除已下载的本地文件 */
    fun remove(id: Long, deleteFile: Boolean = false) = DownloadHub.remove(id, deleteFile)

    /** 批量删除；[deleteFile] 时同时删除各自已下载的本地文件 */
    fun removeAll(ids: List<Long>, deleteFile: Boolean = false) = DownloadHub.removeAll(ids, deleteFile)

    /** 批量取消（不影响已下载文件） */
    fun cancelAll(ids: List<Long>) = ids.forEach { DownloadHub.cancel(it) }

    /** 批量重试（重新入队） */
    fun retryAll(context: Context, ids: List<Long>) {
        ids.forEach { DownloadHub.retry(it) }
        DownloadService.start(context)
    }
}
