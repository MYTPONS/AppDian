package com.appdian.store

import com.appdian.store.data.GroupResult
import com.appdian.store.data.StoreRepository
import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource
import com.appdian.engine.model.AppSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 搜索结果同源分组合并测试：
 * 发现缓存的多栏目 group + 在线搜索 group 按 sourceName 合并为一个 group，
 * 避免 LazyColumn key（h-sourceName）冲突崩溃（如华军软件园 3 个发现栏目同时命中关键词）。
 */
class SearchMergeTest {

    private val huajun = AppSource(
        sourceName = "华军软件园",
        sourceUrl = "https://www.onlinedown.net",
        sourceVersion = "1",
        search = AppSection(url = "/search", listRule = "css:.item", itemRules = com.appdian.engine.model.ItemRules(name = "css:.tt")),
        enabled = true
    )

    private fun item(name: String, version: String, pkg: String) = AppItem(
        name = name,
        packageName = pkg,
        version = version,
        detailUrl = "/soft/$pkg"
    )

    private fun group(title: String, items: List<AppItem>) =
        GroupResult(source = huajun, title = title, items = items)

    @Test
    fun `同源多栏目合并为一个group-全部保留去重`() {
        // 本地发现缓存命中 3 个华军栏目（本周热推/热门榜单/安卓分类推荐）
        val local1 = group("本周热推", listOf(item("微信", "8.0.76", "com.tencent.mm")))
        val local2 = group("热门榜单", listOf(item("QQ", "9.1.0", "com.tencent.mobileqq")))
        val local3 = group("安卓分类推荐", listOf(item("微信", "8.0.76", "com.tencent.mm")))

        var merged: GroupResult? = null
        for (g in listOf(local1, local2, local3)) {
            merged = StoreRepository.mergeSameSourceGroup(merged, g, g.items, preferOnlineTitle = false)
        }
        // 3 个栏目合并为 1 个 group，微信去重只剩一条
        assertEquals(2, merged!!.items.size)
        val names = merged.items.map { it.name }.toSet()
        assertEquals(setOf("微信", "QQ"), names)
    }

    @Test
    fun `在线搜索合并进本地group-在线标题优先且去重`() {
        val local = group("本周热推", listOf(item("微信", "8.0.76", "com.tencent.mm")))
        val online = group("搜索", listOf(item("微信", "8.0.76", "com.tencent.mm"), item("微信极速版", "8.0.76", "com.tencent.mm.speed")))

        val merged = StoreRepository.mergeSameSourceGroup(local, online, online.items, preferOnlineTitle = true)
        // 在线标题优先，同名同版本去重（微信只留 1 条）
        assertEquals("搜索", merged.title)
        assertEquals(2, merged.items.size)
    }

    @Test
    fun `无既有group时直接使用新group`() {
        val g = group("搜索", listOf(item("QQ", "9.1.0", "com.tencent.mobileqq")))
        val merged = StoreRepository.mergeSameSourceGroup(null, g, g.items, preferOnlineTitle = true)
        assertEquals("搜索", merged.title)
        assertEquals(1, merged.items.size)
    }

    @Test
    fun `不同版本的同名应用不被去重`() {
        val a = group("本周热推", listOf(item("微信", "8.0.75", "com.tencent.mm")))
        val b = group("热门榜单", listOf(item("微信", "8.0.76", "com.tencent.mm")))
        val merged = StoreRepository.mergeSameSourceGroup(a, b, b.items, preferOnlineTitle = false)
        assertEquals(2, merged.items.size)
    }

    @Test
    fun `合并结果不改变source与category字段`() {
        val a = GroupResult(source = huajun, title = "本周热推", items = listOf(item("微信", "8.0.76", "com.tencent.mm")), category = "social")
        val b = group("搜索", listOf(item("QQ", "9.1.0", "com.tencent.mobileqq")))
        val merged = StoreRepository.mergeSameSourceGroup(a, b, b.items, preferOnlineTitle = false)
        assertEquals("华军软件园", merged.source.sourceName)
        assertEquals("social", merged.category)   // 本地 group 的 category 声明保留
    }
}
