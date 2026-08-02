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

    fun remove(id: Long) = DownloadHub.remove(id)
}
