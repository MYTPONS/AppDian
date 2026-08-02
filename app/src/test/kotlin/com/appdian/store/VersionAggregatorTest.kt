package com.appdian.store

import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource
import com.appdian.store.data.GroupResult
import com.appdian.store.data.VersionAggregator
import com.appdian.store.data.VersionSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情页版本聚合逻辑测试：
 *  - 版本字符串解析与排序（v 前缀、多段、无法解析）
 *  - 同名应用跨源聚合成不同版本，按版本降序
 *  - 严格同名（变体不算）、同版本去重（有直链优先、当前源优先）
 */
class VersionAggregatorTest {

    private fun item(name: String, version: String?, url: String? = null) =
        AppItem(name = name, version = version, downloadUrl = url)

    private fun src(name: String) = AppSource(sourceName = name, sourceUrl = "https://x.example")

    private fun group(sourceName: String, vararg items: AppItem) =
        GroupResult(src(sourceName), sourceName, items.toList())

    @Test
    fun `版本解析-数字段序列与v前缀`() {
        assertEquals(listOf(2, 1, 0), VersionSort.parse("v2.1.0"))
        assertEquals(listOf(30, 5, 0), VersionSort.parse("30.5.0"))
        assertEquals(listOf(8, 0, 76), VersionSort.parse("8.0.76"))
        assertNull(VersionSort.parse("最新版"))
        assertNull(VersionSort.parse(null))
    }

    @Test
    fun `版本比较-高版本在前-无法解析排最后`() {
        assertTrue(VersionSort.compare("30.5.0", "30.4.0") > 0)
        assertTrue(VersionSort.compare("8.0.76", "8.0.7") > 0)
        assertTrue(VersionSort.compare("3.2", "3.2.0") == 0)
        assertTrue(VersionSort.compare("最新版", "30.5.0") < 0)
        assertTrue(VersionSort.compare("30.5.0", "最新版") > 0)
    }

    @Test
    fun `聚合-同名不同版本按版本降序`() {
        val groups = listOf(
            group("华军", item("抖音", "30.4.0"), item("抖音", "30.3.0")),
            group("F-Droid", item("抖音", "30.5.0"))
        )
        val result = VersionAggregator.aggregate("抖音", groups, "华军")
        assertEquals(listOf("30.5.0", "30.4.0", "30.3.0"), result.map { it.version })
    }

    @Test
    fun `聚合-严格同名-变体不算`() {
        val groups = listOf(
            group("华军", item("抖音", "30.5.0"), item("抖音极速版", "1.2.0"), item(" 抖音 ", "30.4.0"))
        )
        val result = VersionAggregator.aggregate("抖音", groups, "华军")
        // 抖音极速版（变体）不匹配；首尾空白的" 抖音 "经 trim 后匹配
        assertEquals(listOf("30.5.0", "30.4.0"), result.map { it.version })
    }

    @Test
    fun `聚合-同版本去重-有直链优先`() {
        val groups = listOf(
            group("华军", item("微信", "8.0.76", url = null)),
            group("F-Droid", item("微信", "8.0.76", url = "https://cdn.fdroid/wechat.apk"))
        )
        val result = VersionAggregator.aggregate("微信", groups, "华军")
        assertEquals(1, result.size)
        assertEquals("https://cdn.fdroid/wechat.apk", result[0].item.downloadUrl)
    }

    @Test
    fun `聚合-同版本去重-都无直链时当前源优先`() {
        val groups = listOf(
            group("华军", item("微信", "8.0.76")),
            group("F-Droid", item("微信", "8.0.76"))
        )
        val result = VersionAggregator.aggregate("微信", groups, "华军")
        assertEquals(1, result.size)
        assertEquals("华军", result[0].source.sourceName)
    }

    @Test
    fun `聚合-同名同版本多源-结果仅一条`() {
        val groups = listOf(
            group("S1", item("A", "1.0.0"), item("A", "1.0.0")),
            group("S2", item("A", "1.0.0"))
        )
        val result = VersionAggregator.aggregate("A", groups, "S1")
        assertEquals(1, result.size)
    }
}
