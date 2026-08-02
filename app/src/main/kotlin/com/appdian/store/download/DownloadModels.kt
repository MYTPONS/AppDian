package com.appdian.store.download

import kotlinx.serialization.Serializable

/** 下载状态 */
@Serializable
enum class DlStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELED }

/** 一条下载任务/记录（可持久化到 downloads.json） */
@Serializable
data class DownloadTask(
    val id: Long,
    val url: String,
    val title: String,
    val fileName: String,
    /** 应用名等去重键（同应用不重复下载） */
    val appKey: String? = null,
    /** 下载请求 User-Agent（防盗链源需要；为空时用通用浏览器 UA） */
    val userAgent: String? = null,
    /** 下载请求 Referer（防盗链源需要） */
    val referer: String? = null,
    /** 来源源名（下载失败自动换源时用） */
    val sourceName: String? = null,
    /** 应用版本号（换源时按同名同版本匹配） */
    val version: String? = null,
    /** 已尝试过的源（避免换源循环） */
    val triedSources: List<String> = emptyList(),
    /** 已尝试过的下载链接（同源内换链接时避免循环） */
    val triedLinks: List<String> = emptyList(),
    val enqueuedAt: Long = System.currentTimeMillis(),
    val status: DlStatus = DlStatus.QUEUED,
    val progress: Int = 0,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val error: String? = null,
    val localPath: String? = null
) {
    val isFinished: Boolean get() = status == DlStatus.DONE
    val isFailed: Boolean get() = status == DlStatus.FAILED || status == DlStatus.CANCELED
}
