package com.appdian.store

import com.appdian.store.download.DlStatus
import com.appdian.store.download.DownloadHub
import com.appdian.store.download.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 下载任务数据保护测试：
 *  - 非终态任务（排队/下载中/失败待换源）的应用名需要保护
 *  - 已完成/已取消的任务解除保护
 */
class ProtectAppTest {

    private fun task(id: Long, title: String, status: DlStatus) =
        DownloadTask(id = id, url = "https://x/$id.apk", title = title, fileName = "$title.apk", status = status)

    @Test
    fun `未完成和失败任务的应用名被保护`() {
        val tasks = listOf(
            task(1, "抖音", DlStatus.RUNNING),
            task(2, "微信", DlStatus.QUEUED),
            task(3, "支付宝", DlStatus.PAUSED),
            task(4, "快手", DlStatus.FAILED)   // 失败待换源/重试，仍需保护
        )
        assertEquals(
            listOf("抖音", "微信", "支付宝", "快手"),
            DownloadHub.protectedAppNames(tasks)
        )
    }

    @Test
    fun `完成和取消的任务解除保护`() {
        val tasks = listOf(
            task(1, "抖音", DlStatus.DONE),
            task(2, "微信", DlStatus.CANCELED),
            task(3, "支付宝", DlStatus.QUEUED)
        )
        assertEquals(listOf("支付宝"), DownloadHub.protectedAppNames(tasks))
    }

    @Test
    fun `应用名去重与空白过滤`() {
        val tasks = listOf(
            task(1, "抖音", DlStatus.RUNNING),
            task(2, "抖音", DlStatus.QUEUED),   // 同名去重
            task(3, "   ", DlStatus.RUNNING),    // 空白忽略
            task(4, "", DlStatus.RUNNING)
        )
        assertEquals(listOf("抖音"), DownloadHub.protectedAppNames(tasks))
    }
}
