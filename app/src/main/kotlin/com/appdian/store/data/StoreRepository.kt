package com.appdian.store.data

import com.appdian.engine.SourceParser
import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource
import com.appdian.store.net.HttpFetcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select

/** 一组结果：一个源的某个栏目（发现/搜索）的条目集合，含失败信息 */
data class GroupResult(
    val source: AppSource,
    val title: String,
    val items: List<AppItem>,
    val error: String? = null,
    /** 栏目声明的分类（discovery 栏目的 category 字段），供分类页判定 */
    val category: String? = null
)

/**
 * 业务编排层：把「网络抓取 + 规则引擎解析」组合成
 * 发现页 / 搜索 / 详情三个入口。
 */
class StoreRepository(
    private val fetcher: HttpFetcher,
    private val sourceRepo: SourceRepository? = null,
    /** 源列表提供者（默认取仓库；测试可注入固定列表） */
    private val sources: () -> List<AppSource> = { sourceRepo?.list() ?: emptyList() }
) {
    private val parser = SourceParser()

    // ---------------- 内存缓存（数据复用，避免重复加载） ----------------

    private class CacheEntry(
        val groups: List<GroupResult>,
        val fetchedAt: Long = System.currentTimeMillis(),
        val fingerprint: String = ""
    ) {
        fun isFresh(): Boolean = System.currentTimeMillis() - fetchedAt < TTL_MS
    }

    @Volatile private var discoverCache: CacheEntry? = null

    /** 搜索关键词缓存（LRU，满 20 条淘汰最旧的） */
    private val searchCaches = LruCache<String, CacheEntry>(MAX_CACHE_ITEMS)

    /** 条目级数据池（LRU，满 20 条淘汰最旧）：发现/搜索/详情解析出的应用条目都进池，各页复用 */
    private val itemCache = LruCache<String, CachedItem>(MAX_CACHE_ITEMS)

    private data class CachedItem(val source: AppSource, val item: AppItem)

    private fun itemKey(sourceName: String, item: AppItem) =
        "$sourceName::${item.name}::${item.version ?: ""}"

    /** 把解析出的条目写入数据池 */
    private fun cacheItems(source: AppSource, items: List<AppItem>) {
        items.forEach { itemCache.put(itemKey(source.sourceName, it), CachedItem(source, it)) }
    }

    /** 缓存有效期：5 分钟内不重复抓取 */
    private fun freshCache(entry: CacheEntry?, fingerprint: String): List<GroupResult>? =
        entry?.takeIf { it.isFresh() && it.fingerprint == fingerprint }?.groups

    /** 缓存指纹：启用源列表（名称 + 版本），源变更自动失效 */
    private fun fingerprint(): String =
        sources().filter { it.enabled }.joinToString("|") { it.sourceName + ":" + (it.sourceVersion ?: "") }

    /**
     * 本地搜索匹配（数据复用）：进入搜索页先秒出本地结果，再等在线结果。
     * 1. 发现缓存快照（整组匹配）
     * 2. 条目级数据池兜底（发现缓存过期/失效后，池里仍有的条目）
     */
    fun localMatches(key: String): List<GroupResult> {
        val words = key.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val fromGroups = discoverCache?.groups?.map { g ->
            g.copy(items = g.items.filter { item ->
                words.all { w ->
                    item.name.contains(w, ignoreCase = true) ||
                        (item.summary?.contains(w, ignoreCase = true) == true)
                }
            })
        }?.filter { it.items.isNotEmpty() } ?: emptyList()
        if (fromGroups.isNotEmpty()) return fromGroups

        // 条目池兜底：按源分组返回
        val matched = itemCache.values.filter { c ->
            words.all { w ->
                c.item.name.contains(w, ignoreCase = true) ||
                    (c.item.summary?.contains(w, ignoreCase = true) == true)
            }
        }
        if (matched.isEmpty()) return emptyList()
        return matched.groupBy { it.source.sourceName }.map { (name, list) ->
            val first = list.first()
            GroupResult(first.source, "本地缓存 · $name", list.map { it.item })
        }
    }

    /**
     * 保护某应用的数据不被 LRU 淘汰：下载中/可能换源换链接的应用，
     * 其搜索缓存与条目池中的条目被保护，确保换源时立即可用（不重新抓取）。
     */
    fun protectApp(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        searchCaches.protect(n)
        itemCache.protectWhere { _, c -> c.item.name.equals(n, ignoreCase = true) }
    }

    /** 解除某应用的数据保护 */
    fun unprotectApp(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        searchCaches.unprotect(n)
        itemCache.unprotectWhere { _, c -> c.item.name.equals(n, ignoreCase = true) }
    }

    companion object {
        private const val TTL_MS = 5 * 60 * 1000L
        private const val MAX_CACHE_ITEMS = 20

        /** 同名同版本去重键（本地 + 在线搜索结果合并时用） */
        fun dedupKey(item: AppItem): String =
            (item.name.trim().lowercase() + "|" + (item.version?.trim() ?: "")).lowercase()

        /**
         * 搜索结果分组合并：同一应用源只保留一个 group（本地发现缓存多栏目 + 在线搜索合并），
         * 避免重复 sourceName 导致列表 key 冲突崩溃。items 按 名称+版本 去重。
         * @param existing 已存在的同源 group（可能是本地缓存 group）
         * @param preferOnlineTitle 合并时优先采用在线 group 的标题
         */
        fun mergeSameSourceGroup(
            existing: GroupResult?,
            g: GroupResult,
            kept: List<AppItem>,
            preferOnlineTitle: Boolean
        ): GroupResult {
            if (existing == null) return g.copy(items = kept)
            val all = (existing.items + kept).distinctBy { dedupKey(it) }
            val base = if (preferOnlineTitle) g else existing
            return base.copy(items = all)
        }
    }

    /** 发现页（一次性）：所有启用源的所有发现栏目（并发，缓存优先） */
    suspend fun discover(force: Boolean = false): List<GroupResult> {
        val groups = mutableListOf<GroupResult>()
        discoverFlow(force).collect { groups.add(it) }
        return groups
    }

    /**
     * 发现页（增量流）：所有栏目**并发**抓取，哪个先完成先发哪个。
     * UI 订阅后可以逐条展示，不必等最慢的源。
     */
    fun discoverFlow(force: Boolean = false): Flow<GroupResult> = flow {
        val fp = fingerprint()
        if (!force) freshCache(discoverCache, fp)?.let { cached ->
            cached.forEach { emit(it) }
            return@flow
        }
        val enabled = sources().filter { it.enabled && it.discovery.isNotEmpty() }
        coroutineScope {
            val deferred = mutableListOf<Deferred<GroupResult>>()
            enabled.forEach { src ->
                src.discovery.forEach { d ->
                    deferred.add(
                        async {
                            runCatching {
                                val url = parser.buildUrl(d.section, src, emptyMap())
                                val body = fetcher.fetch(url, src, d.section)
                                val items = parser.parseList(body, d.section, src)
                                cacheItems(src, items)
                                GroupResult(src, d.title, items, category = d.category)
                            }.getOrElse { e ->
                                GroupResult(src, d.title, emptyList(), e.message ?: "未知错误", category = d.category)
                            }
                        }
                    )
                }
            }
            // 按完成顺序逐个发出（先完成的先 emit，实现实时加载）
            val remaining = deferred.toMutableList()
            val collected = mutableListOf<GroupResult>()
            while (remaining.isNotEmpty()) {
                val done = select<Deferred<GroupResult>> {
                    remaining.forEach { d -> d.onAwait { d } }
                }
                val g = done.await()
                emit(g)
                collected.add(g)
                remaining.remove(done)
            }
            discoverCache = CacheEntry(collected, fingerprint = fp)
        }
    }

    /** 跨源搜索（一次性，串行） */
    suspend fun search(key: String): List<GroupResult> {
        val groups = mutableListOf<GroupResult>()
        searchFlow(key).collect { groups.add(it) }
        return groups
    }

    /** 跨源搜索（增量流）：并发搜索所有源，逐个返回，先到先得。
     *  [dedup] = true 时同名同版本只保留一个（搜索结果页展示用）；
     *  = false 时保留全部（下载失败换源/换链接时用，同源同版本可能有多条不同下载入口）。 */
    fun searchFlow(key: String, dedup: Boolean = true, force: Boolean = false): Flow<GroupResult> = flow {
        if (key.isBlank()) return@flow
        val fp = fingerprint()
        if (!force) freshCache(searchCaches.get(key), fp)?.let { cached ->
            cached.forEach { emit(it) }
            return@flow
        }
        val enabled = sources().filter { it.enabled && it.search != null }
        coroutineScope {
            val deferred = mutableListOf<Deferred<GroupResult>>()
            enabled.forEach { src ->
                val section = src.search!!
                deferred.add(
                    async {
                        runCatching {
                            val vars = mapOf("key" to key)
                            val url = parser.buildUrl(section, src, vars)
                            val body = fetcher.fetch(url, src, section)
                            var items = filterByKeywords(parser.parseList(body, section, src, vars), key)
                            if (dedup) items = dedupByNameAndVersion(items)
                            cacheItems(src, items)
                            GroupResult(src, "“$key” 的结果", items)
                        }.getOrElse { e ->
                            GroupResult(src, "“$key” 的结果", emptyList(), e.message ?: "未知错误")
                        }
                    }
                )
            }
            val remaining = deferred.toMutableList()
            val collected = mutableListOf<GroupResult>()
            while (remaining.isNotEmpty()) {
                val done = select<Deferred<GroupResult>> {
                    remaining.forEach { d -> d.onAwait { d } }
                }
                val g = done.await()
                emit(g)
                collected.add(g)
                remaining.remove(done)
            }
            searchCaches.put(key, CacheEntry(collected, fingerprint = fp))
        }
    }

    /** 详情：先用详情规则抓详情页；源没配详情规则则直接返回列表条目 */
    suspend fun detail(source: AppSource, item: AppItem): AppItem {
        val section = source.detail ?: return item
        val url = item.detailUrl ?: return item
        val vars = item.vars + itemVars(item) + ("detailUrl" to url)
        val detailUrl = runCatching { parser.buildUrl(section, source, vars) }.getOrElse { url }
        val body = fetcher.fetch(detailUrl, source, section)
        return (parser.parseDetail(body, section, source, vars) ?: item).also { detailItem ->
            itemCache.put(itemKey(source.sourceName, detailItem), CachedItem(source, detailItem))
        }
    }

    /**
     * 搜索词二次过滤：网站返回的结果不一定匹配搜索词（站点搜索逻辑各异），
     * 只保留名称或描述里包含搜索关键词的条目，大小写不敏感。
     * 多关键词（空格分隔）需全部命中；英文如 deepseek 可命中 deePseek。
     */
    private fun filterByKeywords(items: List<AppItem>, key: String): List<AppItem> {
        val words = key.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return items
        return items.filter { item ->
            words.all { w ->
                val desc = item.summary
                item.name.contains(w, ignoreCase = true) ||
                    (desc != null && desc.contains(w, ignoreCase = true))
            }
        }
    }

    /**
     * 搜索去重：同名同版本的条目只保留一个（不同网站/榜单会重复收录同一软件）。
     * 保留顺序中的第一个，等价于“随机保留一个”。
     */
    private fun dedupByNameAndVersion(items: List<AppItem>): List<AppItem> =
        items.distinctBy { item ->
            (item.name.trim().lowercase() + "|" + (item.version?.trim() ?: "")).lowercase()
        }

    private fun itemVars(item: AppItem): Map<String, String> = buildMap {
        item.name.let { if (it.isNotBlank()) put("name", it) }
        item.packageName?.let { put("packageName", it) }
        item.version?.let { put("version", it) }
    }
}
