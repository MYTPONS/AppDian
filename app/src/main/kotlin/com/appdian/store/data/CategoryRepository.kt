package com.appdian.store.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * 分类仓库：
 *  - 统一分类表存 filesDir/categories.json（用户可编辑，导入导出同源管理）
 *  - 用户手动覆盖存 filesDir/category-overrides.json（itemKey -> categoryId）
 */
class CategoryRepository(private val context: Context) {

    private val file = File(context.filesDir, "categories.json")
    private val overrideFile = File(context.filesDir, "category-overrides.json")

    /**
     * 读取分类表：文件缺失 → 内置默认；文件存在 → 与内置默认做并集合并
     * （同 id 的关键词取并集，这样内置分类表扩充关键词后老用户也能自动获得新词）。
     */
    fun list(): List<Category> {
        val user = if (!file.exists()) emptyList() else runCatching {
            CategoryJson.json.decodeFromString<List<Category>>(file.readText())
        }.getOrElse { emptyList() }
        if (user.isEmpty()) return DEFAULT_CATEGORIES
        return mergeWithDefaults(user)
    }

    companion object {
        /** 用户分类表 × 内置默认分类合并（纯逻辑，可单测） */
        fun mergeWithDefaults(user: List<Category>): List<Category> {
            return user.map { c ->
                val d = DEFAULT_CATEGORIES.firstOrNull { it.id == c.id }
                if (d == null) c else c.copy(keywords = (c.keywords + d.keywords).distinct())
            } + DEFAULT_CATEGORIES.filter { d -> user.none { it.id == d.id } }
        }
    }

    fun get(idOrName: String): Category? {
        val v = idOrName.trim()
        val all = list()
        return all.firstOrNull { it.id == v } ?: all.firstOrNull { it.name == v }
    }

    /** 整体保存（增删改分类都走这里） */
    fun save(categories: List<Category>) {
        file.parentFile?.mkdirs()
        file.writeText(CategoryJson.json.encodeToString(ListSerializer(Category.serializer()), categories))
    }

    fun add(category: Category) {
        val all = list().filterNot { it.id == category.id }
        save(all + category)
    }

    fun delete(id: String) {
        save(list().filterNot { it.id == id })
    }

    // ---------------- 手动覆盖 ----------------

    private fun readOverrides(): Map<String, String> {
        if (!overrideFile.exists()) return emptyMap()
        return runCatching {
            CategoryJson.json.decodeFromString<Map<String, String>>(overrideFile.readText())
        }.getOrElse { emptyMap() }
    }

    private fun writeOverrides(map: Map<String, String>) {
        overrideFile.parentFile?.mkdirs()
        overrideFile.writeText(
            CategoryJson.json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()), map
            )
        )
    }

    fun overrides(): Map<String, String> = readOverrides()

    fun setOverride(itemKey: String, categoryId: String) {
        writeOverrides(readOverrides() + (itemKey to categoryId))
    }

    fun removeOverride(itemKey: String) {
        writeOverrides(readOverrides() - itemKey)
    }

    fun classifier(): CategoryClassifier = CategoryClassifier(list(), readOverrides())

    /** 未分类条目数统计用的辅助：给分类 id 数组按分类分组 */
    fun groupCounts(ids: List<String?>): Map<String?, Int> = ids.groupingBy { it }.eachCount()

    // ---------------- 配置导入 / 导出 ----------------

    /** 导出完整分类配置（分类表 + 手动覆盖），返回 JSON 文本 */
    fun exportConfig(): String {
        val payload = CategoryExport(
            categories = list(),
            overrides = readOverrides()
        )
        return CategoryJson.json.encodeToString(CategoryExport.serializer(), payload)
    }

    /** 导入完整分类配置（分类表 + 手动覆盖），解析失败抛异常 */
    fun importConfig(raw: String) {
        val payload = CategoryJson.json.decodeFromString<CategoryExport>(raw)
        require(payload.categories.isNotEmpty()) { "分类列表为空" }
        save(payload.categories)
        writeOverrides(payload.overrides)
    }
}

/** 分类配置的导入导出载体 */
@kotlinx.serialization.Serializable
data class CategoryExport(
    val categories: List<Category>,
    val overrides: Map<String, String> = emptyMap()
)
