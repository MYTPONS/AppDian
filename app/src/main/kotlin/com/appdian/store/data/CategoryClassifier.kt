package com.appdian.store.data

import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource

/**
 * 分类判定链（纯逻辑，JVM 可测）：
 *
 * 对一条应用条目，按以下优先级确定它的统一分类：
 *   1. 用户手动覆盖（本地 mapping，最高优先）
 *   2. 源栏目声明的分类（discovery 栏目的 category 字段，按 id / 名称命中）
 *   3. 条目规则动态提取的 category 字段（按 id / 名称 / 关键词命中）
 *   4. 应用名关键词匹配（优先）
 *   5. 简介（摘要/描述）关键词匹配（兜底：给应用名无分类的应用简单归类）
 *   6. 未命中 → null（归入"未分类"）
 *
 * 关键词匹配为忽略大小写的子串匹配。
 */
class CategoryClassifier(
    private val categories: List<Category>,
    /** 手动覆盖表：itemKey -> categoryId */
    private val overrides: Map<String, String> = emptyMap()
) {

    private val byId = categories.associateBy { it.id }
    private val byName = categories.associateBy { it.name }

    /** 判定条目分类，返回分类 id；未命中返回 null */
    fun classify(item: AppItem, source: AppSource, declaredCategory: String? = null): String? {
        overrides[itemKey(source.sourceName, item)]?.let { id ->
            // 哨兵值表示用户强制归入未分类
            return id.takeIf { it != UNCATEGORIZED }
        }
        declaredCategory?.let { resolve(it)?.let { c -> return c.id } }
        item.category?.let { resolve(it)?.let { c -> return c.id } }
        // 应用名关键词优先；没命中再用简介兜底
        return matchNameKeywords(item) ?: matchSummaryKeywords(item)
    }

    /** 应用名关键词匹配（权重最高）：只有应用名里命中才算 */
    private fun matchNameKeywords(item: AppItem): String? =
        matchIn(item.name)

    /** 简介关键词兜底：摘要/描述 + 提取的分类字段（给没有分类的应用简单归类） */
    private fun matchSummaryKeywords(item: AppItem): String? =
        matchIn(listOfNotNull(item.summary, item.category).joinToString(" ") { it })

    private fun matchIn(haystackRaw: String): String? {
        if (haystackRaw.isBlank()) return null
        val haystack = haystackRaw.lowercase()
        return categories.firstOrNull { c ->
            c.id != "other" && c.keywords.any { haystack.contains(it.lowercase()) }
        }?.id
    }

    /** 分类引用解析：id 或名称直接命中，再退回关键词匹配（供声明/提取值使用） */
    private fun resolve(raw: String): Category? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        byId[v]?.let { return it }
        byName[v]?.let { return it }
        // 原始分类名（如 F-Droid 的 "Multimedia"）走关键词表
        return categories.firstOrNull { c ->
            c.id != "other" && c.keywords.any { v.contains(it, ignoreCase = true) }
        }
    }

    companion object {
        /** 手动覆盖的稳定 key：源 + 包名（无包名用应用名兜底） */
        fun itemKey(sourceName: String, item: AppItem): String {
            val id = item.packageName?.takeIf { it.isNotBlank() } ?: item.name
            return "$sourceName::$id"
        }

        /** 哨兵：用户强制归入未分类 */
        const val UNCATEGORIZED = "__none"
    }
}
