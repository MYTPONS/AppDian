package com.appdian.store.data

import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource

/** 版本字符串解析与比较（纯逻辑，便于单测） */
object VersionSort {

    /** 解析出数字段序列，如 "v2.1.0" -> [2,1,0]；无法解析返回 null */
    fun parse(v: String?): List<Int>? {
        val t = v?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        val nums = Regex("\\d+").findAll(t).map { it.value.toInt() }.toList()
        return if (nums.isEmpty()) null else nums
    }

    /** a > b 返回正数；无法解析的版本排最后 */
    fun compare(a: String?, b: String?): Int {
        val pa = parse(a)
        val pb = parse(b)
        if (pa == null && pb == null) return 0
        if (pa == null) return -1
        if (pb == null) return 1
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val da = pa.getOrElse(i) { 0 }
            val db = pb.getOrElse(i) { 0 }
            if (da != db) return da.compareTo(db)
        }
        return 0
    }
}

/** 同一软件的某个版本条目（来源 + 列表条目） */
data class VersionEntry(
    val version: String?,
    val item: AppItem,
    val source: AppSource
)

/**
 * 详情页版本聚合：跨源搜索同名应用，收集不同版本。
 * 规则：
 *  - 严格同名（忽略大小写/首尾空白），变体（抖音极速版）不算
 *  - 同名同版本只保留一条：优先有下载直链的，其次当前源
 *  - 结果按版本降序（高版本在前），无法解析版本的排最后
 */
object VersionAggregator {

    fun aggregate(name: String, groups: List<GroupResult>, currentSourceName: String?): List<VersionEntry> {
        if (name.isBlank()) return emptyList()
        val sameName = groups.flatMap { g ->
            g.items
                .filter { com.appdian.store.download.SourceSwitcher.nameMatches(it.name, name) }
                .map { VersionEntry(it.version, it, g.source) }
        }
        // 同版本去重
        val seen = LinkedHashMap<String, VersionEntry>()
        for (e in sameName) {
            val key = e.version?.trim()?.lowercase() ?: "?"
            val prev = seen[key]
            if (prev == null || better(e, prev, currentSourceName)) seen[key] = e
        }
        return seen.values.sortedWith { a, b ->
            val c = VersionSort.compare(a.version, b.version)
            if (c != 0) -c else 0
        }
    }

    /** 同版本保留谁：有直链优先于无直链；都有/都无时优先当前源 */
    private fun better(e: VersionEntry, prev: VersionEntry, currentSourceName: String?): Boolean {
        val eHas = !e.item.downloadUrl.isNullOrBlank()
        val pHas = !prev.item.downloadUrl.isNullOrBlank()
        if (eHas != pHas) return eHas
        if (e.source.sourceName == currentSourceName && prev.source.sourceName != currentSourceName) return true
        return false
    }
}
