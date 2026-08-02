package com.appdian.store

import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource
import com.appdian.store.data.CategoryClassifier
import com.appdian.store.data.DEFAULT_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryClassifierTest {

    private val classifier = CategoryClassifier(DEFAULT_CATEGORIES)

    private val source = AppSource(sourceName = "测试源", sourceUrl = "https://a.com")

    private fun item(
        name: String,
        packageName: String? = null,
        summary: String? = null,
        category: String? = null
    ) = AppItem(name = name, packageName = packageName, summary = summary, category = category)

    @Test
    fun `栏目声明按 id 直接命中`() {
        assertEquals("media", classifier.classify(item("任意应用"), source, declaredCategory = "media"))
    }

    @Test
    fun `栏目声明按名称命中`() {
        assertEquals("tools", classifier.classify(item("任意应用"), source, declaredCategory = "系统工具"))
    }

    @Test
    fun `动态提取的原始分类走关键词映射`() {
        // F-Droid 的官方 category 值
        assertEquals("media", classifier.classify(item("VLC", category = "Multimedia"), source))
        assertEquals("game", classifier.classify(item("Sudoku", category = "Games"), source))
        assertEquals("dev", classifier.classify(item("Termux", category = "Development"), source))
        assertEquals("edu", classifier.classify(item("Anki", category = "Education"), source))
    }

    @Test
    fun `动态提取中文分类名走关键词映射`() {
        assertEquals("media", classifier.classify(item("某App", category = "影音播放"), source))
    }

    @Test
    fun `名称摘要关键词兜底`() {
        assertEquals("media", classifier.classify(item("VLC Media Player"), source))
        assertEquals("edu", classifier.classify(item("新华字典"), source))
        assertEquals("shopping", classifier.classify(item("淘宝", summary = "网购必备"), source))
    }

    @Test
    fun `手动覆盖优先于一切`() {
        val override = mapOf("测试源::com.example.game" to "tools")
        val c = CategoryClassifier(DEFAULT_CATEGORIES, override)
        // 声明和关键词都说 media，但手动覆盖说 tools
        assertEquals("tools", c.classify(item("VLC", packageName = "com.example.game", category = "Multimedia"), source, declaredCategory = "media"))
    }

    @Test
    fun `哨兵值强制归入未分类`() {
        val override = mapOf("测试源::com.example.x" to CategoryClassifier.UNCATEGORIZED)
        val c = CategoryClassifier(DEFAULT_CATEGORIES, override)
        // 即使声明和关键词都命中，强制未分类
        assertNull(c.classify(item("VLC", packageName = "com.example.x", category = "Multimedia"), source, declaredCategory = "media"))
    }

    @Test
    fun `未命中返回 null 归未分类`() {
        assertNull(classifier.classify(item("Xyzw"), source))
    }

    @Test
    fun `itemKey 优先用包名`() {
        val it = item("名字A", packageName = "com.a.b", category = null)
        assertEquals("测试源::com.a.b", CategoryClassifier.itemKey("测试源", it))
        val noPkg = item("名字A")
        assertEquals("测试源::名字A", CategoryClassifier.itemKey("测试源", noPkg))
    }

    @Test
    fun `声明分类后条目字段与关键词不再覆盖声明`() {
        // 栏目声明了 media，即使条目 category 是 Development 也不覆盖
        assertEquals("media", classifier.classify(item("X", category = "Development"), source, declaredCategory = "media"))
    }

    @Test
    fun `应用名关键词优先于简介关键词`() {
        // 应用名命中 media（VLC），简介命中 dev——应以应用名为准
        val it = item("VLC", summary = "Termux 终端模拟器 代码工具")
        assertEquals("media", classifier.classify(it, source))
    }

    @Test
    fun `应用名无命中时简介关键词兜底分类`() {
        // 应用名"轻量助手"无关键词命中，简介含 视频播放 → media
        val it = item("轻量助手", summary = "支持高清视频播放、音乐，本地媒体管理")
        assertEquals("media", classifier.classify(it, source))
        // 简介含 购物 关键词 → shopping
        val it2 = item("省钱助手", summary = "淘宝京东拼多多优惠券聚合")
        assertEquals("shopping", classifier.classify(it2, source))
    }

    @Test
    fun `简介兜底不覆盖其他更高优先级`() {
        // 手动覆盖优先于简介兜底
        val override = mapOf("测试源::com.override" to "game")
        val c = CategoryClassifier(DEFAULT_CATEGORIES, override)
        val it = item("普通软件", packageName = "com.override", summary = "王者荣耀 游戏专区")
        assertEquals("game", c.classify(it, source))
    }
}
