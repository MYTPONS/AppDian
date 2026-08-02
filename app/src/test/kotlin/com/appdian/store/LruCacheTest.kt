package com.appdian.store

import com.appdian.store.data.LruCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LRU 缓存机制测试（用户要求的淘汰逻辑）：
 * 容量上限内正常存取；满 20 条后写入新数据淘汰最旧的；
 * get 刷新活跃度（最近用过的最后淘汰）；put 已存在刷新顺序。
 */
class LruCacheTest {

    @Test
    fun `满容量写入新数据淘汰最旧的`() {
        val cache = LruCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        assertEquals(listOf("a", "b", "c"), cache.keys)

        // 第 4 条进来，最旧的 "a" 被淘汰
        val evicted = cache.put("d", "4")
        assertEquals("a", evicted)
        assertNull(cache.get("a"))
        assertEquals(listOf("b", "c", "d"), cache.keys)
    }

    @Test
    fun `get 刷新活跃度-最近用过的最后淘汰`() {
        val cache = LruCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")

        cache.get("a")   // a 最近用过，变最新
        val evicted = cache.put("d", "4")
        assertEquals("b", evicted)   // 淘汰的是没用过的 b，而不是 a
        assertEquals(listOf("c", "a", "d"), cache.keys)
    }

    @Test
    fun `put 已存在的 key 刷新为最新且不超上限`() {
        val cache = LruCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")

        cache.put("a", "1")   // a 重新写入，刷新活跃度
        val evicted = cache.put("d", "4")
        assertEquals("b", evicted)
        assertEquals(3, cache.size)
        assertEquals("1", cache.get("a"))
    }

    @Test
    fun `淘汰按写入顺序进行`() {
        val cache = LruCache<String, String>(2)
        cache.put("x", "1")
        cache.put("y", "2")
        assertEquals("x", cache.put("z", "3"))
        assertEquals("y", cache.put("w", "4"))
        assertEquals(listOf("z", "w"), cache.keys)
    }

    @Test
    fun `remove 与 clear`() {
        val cache = LruCache<String, String>(5)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.remove("a")
        assertEquals(1, cache.size)
        cache.clear()
        assertEquals(0, cache.size)
    }

    @Test
    fun `保护键不被淘汰-其他键照常淘汰`() {
        val cache = LruCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        cache.protect("a")   // a 受保护

        // 新数据进来，淘汰最旧的未保护键（b 或 c 中最旧的 b）
        val evicted = cache.put("d", "4")
        assertEquals("b", evicted)
        // 保护键 a 仍在，其余键值完整
        assertEquals("1", cache.get("a"))
        assertEquals("3", cache.get("c"))
        assertEquals("4", cache.get("d"))
    }

    @Test
    fun `全部受保护时允许暂时超限-解除后恢复淘汰`() {
        val cache = LruCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        cache.protect("a")
        cache.protect("b")
        cache.protect("c")

        // 全保护：无法淘汰，允许超限
        assertNull(cache.put("d", "4"))
        assertEquals(4, cache.size)

        // 解除 b 的保护 → 下次写入淘汰 b（最旧未保护），同时把超限的 d 清理回容量
        cache.unprotect("b")
        val evicted = cache.put("e", "5")
        assertEquals("b", evicted)
        assertEquals(3, cache.size)
    }

    @Test
    fun `protectWhere 按条件批量保护`() {
        val cache = LruCache<String, String>(3)
        cache.put("微信", "1")
        cache.put("抖音", "2")
        cache.put("QQ", "3")
        cache.protectWhere { k, _ -> k.contains("微") }

        val evicted = cache.put("支付宝", "4")
        assertEquals("抖音", evicted)   // 微信被保护，淘汰未保护的抖音
        assertEquals("1", cache.get("微信"))

        cache.unprotectWhere { k, _ -> k.contains("微") }
        // get("微信") 把它刷新为最新 → 淘汰的变成 QQ（更旧）
        val evicted2 = cache.put("钉钉", "5")
        assertEquals("QQ", evicted2)
    }
}
