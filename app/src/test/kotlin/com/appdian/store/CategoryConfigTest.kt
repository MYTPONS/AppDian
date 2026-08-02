package com.appdian.store

import com.appdian.store.data.Category
import com.appdian.store.data.CategoryJson
import com.appdian.store.data.CategoryRepository
import com.appdian.store.data.DEFAULT_CATEGORIES
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类配置相关纯逻辑测试：
 *  - 用户分类 × 默认分类的关键词合并
 *  - 导入导出 JSON 的往返一致性
 *  - 扩充后的关键词能命中真实软件名
 */
class CategoryConfigTest {

    @Test
    fun `用户分类与默认分类合并-同id关键词取并集`() {
        val user = listOf(
            Category("media", "影音播放", listOf("我的自定义词")),
            Category("mycat", "我的分类", listOf("x"))
        )
        val merged = CategoryRepository.mergeWithDefaults(user)
        val media = merged.first { it.id == "media" }
        // 用户词 + 默认词去重合并
        assertTrue(media.keywords.contains("我的自定义词"))
        assertTrue(media.keywords.contains("抖音"))
        // 默认有但用户没写的分类要补全
        assertTrue(merged.any { it.id == "social" })
        // 用户自定义分类保留
        assertTrue(merged.any { it.id == "mycat" })
        // 去重
        assertEquals(media.keywords.size, media.keywords.distinct().size)
    }

    @Test
    fun `默认分类关键词覆盖常见软件名`() {
        val cases = mapOf(
            "微信" to "social",
            "抖音短视频" to "media",
            "高德地图" to "travel",
            "NetGuard" to "security",
            "Termux" to "dev",
            "WPS Office" to "office",
            "原神" to "game",
            "淘宝" to "shopping",
            "Keep" to "health",
            "作业帮" to "edu",
            "今日头条" to "news",
            "夸克浏览器" to "tools"
        )
        val classifier = com.appdian.store.data.CategoryClassifier(DEFAULT_CATEGORIES)
        cases.forEach { (name, expected) ->
            val item = com.appdian.engine.model.AppItem(name = name)
            val src = com.appdian.engine.model.AppSource(sourceName = "test", sourceUrl = "https://x")
            val got = classifier.classify(item, src)
            assertEquals("$name 应归入 $expected，实际 $got", expected, got)
        }
    }

    @Test
    fun `分类配置导出导入往返一致`() {
        val categories = listOf(
            Category("a", "分类A", listOf("词1", "词2")),
            Category("b", "分类B", emptyList())
        )
        val payload = com.appdian.store.data.CategoryExport(categories, mapOf("s::pkg" to "a"))
        val jsonText = CategoryJson.json.encodeToString(
            com.appdian.store.data.CategoryExport.serializer(), payload
        )
        val decoded = CategoryJson.json.decodeFromString<com.appdian.store.data.CategoryExport>(jsonText)
        assertEquals(categories, decoded.categories)
        assertEquals(mapOf("s::pkg" to "a"), decoded.overrides)
    }

    @Test
    fun `旧版分类json带icon字段仍可解析`() {
        val old = """[{"id":"media","name":"影音播放","icon":"🎬","keywords":["视频"]}]"""
        val parsed = CategoryJson.json.decodeFromString<List<Category>>(old)
        assertEquals(1, parsed.size)
        assertEquals("media", parsed[0].id)
        assertTrue(parsed[0].keywords.contains("视频"))
    }
}
