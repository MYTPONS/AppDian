package com.appdian.store.download

import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource
import com.appdian.store.data.GroupResult
import com.appdian.store.data.StoreRepository
import kotlinx.coroutines.flow.toList

/**
 * 下载失败自动换源/换链接：
 * 用任务的应用名跨源搜索（不去重，保留同源同名同版本的多条不同下载入口），
 * 按以下顺序找替代：
 *   1. 同源换链接：原源里其他同名同版本条目（不同的下载链接），不消耗换源次数，链接试尽为止
 *   2. 换源：其他源的同名同版本条目（triedSources 防循环，最多 MAX_SWITCHES 次）
 * 条目没有直链时抓详情页解析下载链接。
 */
class SourceSwitcher(private val repo: StoreRepository) {

    /** 换源/换链接结果：新源 + 下载链接 + 需要的请求头 */
    data class Alternative(
        val sourceName: String,
        val url: String,
        val userAgent: String?,
        val referer: String?,
        /** true = 同源内的另一个下载链接；false = 换了新源 */
        val sameSource: Boolean
    )

    /**
     * 为失败任务找一个替代下载链接。
     * 返回 null 表示已无可用替代（同源链接试尽 + 其他源已全部尝试或没有可下载的匹配条目）。
     */
    suspend fun find(task: DownloadTask): Alternative? {
        if (task.title.isBlank()) return null
        val groups = repo.searchFlow(task.title, dedup = false).toList()

        // 1) 同源换链接：优先，且不受换源次数限制
        findInSource(groups, task, sameSourceOnly = true)?.let { return it }
        // 2) 换源：受已尝试源数量限制
        if (task.triedSources.size >= MAX_SWITCHES) return null
        return findInSource(groups, task, sameSourceOnly = false)
    }

    /**
     * [sameSourceOnly] = true 时只在原源内找未尝试过的链接；
     * = false 时在未尝试过的其他源里找。
     */
    private suspend fun findInSource(
        groups: List<GroupResult>,
        task: DownloadTask,
        sameSourceOnly: Boolean
    ): Alternative? {
        for (g in groups) {
            if (g.error != null) continue
            if (sameSourceOnly) {
                if (g.source.sourceName != task.sourceName) continue
            } else {
                if (g.source.sourceName in task.triedSources) continue
            }
            for (item in g.items) {
                if (!nameMatches(item.name, task.title)) continue
                if (!versionMatches(item.version, task.version)) continue
                val url = resolveDownloadUrl(g.source, item) ?: continue
                if (url in task.triedLinks) continue
                if (sameSourceOnly && url == task.url) continue
                return Alternative(
                    g.source.sourceName, url,
                    g.source.userAgent, g.source.sourceUrl,
                    sameSource = sameSourceOnly
                )
            }
        }
        return null
    }

    /** 条目直链没有时抓详情页解析下载链接（网络请求，耗时长，统一收敛） */
    private suspend fun resolveDownloadUrl(source: AppSource, item: AppItem): String? {
        item.downloadUrl?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            item.detailUrl?.let { repo.detail(source, item).downloadUrl }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val MAX_SWITCHES = 3

        /**
         * 名称匹配：严格同名（忽略大小写与首尾空白）。
         * 刻意不用"包含"匹配——"抖音极速版"不能当"抖音"的替代下载。
         */
        fun nameMatches(a: String, b: String): Boolean =
            a.trim().equals(b.trim(), ignoreCase = true)

        /** 版本匹配：未知版本（空）时宽松放行，否则严格相等 */
        fun versionMatches(a: String?, b: String?): Boolean {
            if (a.isNullOrBlank() || b.isNullOrBlank()) return true
            return a.trim() == b.trim()
        }
    }
}
