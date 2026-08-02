package com.appdian.store

import com.appdian.engine.model.AppSection
import com.appdian.engine.model.AppSource
import com.appdian.engine.model.DiscoverySection
import com.appdian.engine.model.ItemRules
import com.appdian.store.data.StoreRepository
import com.appdian.store.net.HttpFetcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 数据复用（内存缓存）测试：
 *  - 发现缓存命中：第二次 discover 不重新抓取
 *  - 源列表变化（指纹）缓存自动失效
 *  - 搜索缓存：同关键词第二次秒回不抓取
 *  - localMatches：发现缓存可本地匹配关键词
 */
class CacheReuseTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() { server.shutdown() }

    private fun source(name: String) = AppSource(
        sourceName = name,
        sourceUrl = base,
        sourceVersion = "1",
        discovery = listOf(
            DiscoverySection(
                title = "推荐",
                section = AppSection(
                    url = "/rec-$name",
                    listRule = "css:div.app",
                    itemRules = ItemRules(name = "css:.n@text", version = "css:.v@text", summary = "css:.s@text")
                )
            )
        )
    )

    private fun searchSource(name: String) = AppSource(
        sourceName = name,
        sourceUrl = base,
        sourceVersion = "1",
        search = AppSection(
            url = "/search-$name?q={{key}}",
            listRule = "css:div.app",
            itemRules = ItemRules(name = "css:.n@text")
        )
    )

    private fun body(vararg apps: String) = buildString {
        append("<html><body>")
        apps.forEach { a ->
            append("<div class='app'><span class='n'>$a</span><span class='v'>1.0</span><span class='s'>这是 $a 的简介</span></div>")
        }
        append("</body></html>")
    }

    @Test
    fun `发现缓存-第二次调用不重新抓取`() = runBlocking {
        server.enqueue(MockResponse().setBody(body("微信")))
        val repo = StoreRepository(HttpFetcher(), sources = { listOf(source("S1")) })

        val first = repo.discover()
        assertEquals(1, first.size)
        val requestsAfterFirst = server.requestCount

        val second = repo.discover()   // 缓存命中
        assertEquals(1, second.size)
        assertEquals(requestsAfterFirst, server.requestCount)  // 请求数不变
    }

    @Test
    fun `发现缓存-强制刷新重新抓取`() = runBlocking {
        server.enqueue(MockResponse().setBody(body("微信")))
        server.enqueue(MockResponse().setBody(body("微信", "QQ")))
        val repo = StoreRepository(HttpFetcher(), sources = { listOf(source("S1")) })

        repo.discover()
        val second = repo.discover(force = true)  // force → 重新抓
        assertEquals(2, second[0].items.size)
    }

    @Test
    fun `发现缓存-源列表变化指纹失效重新抓取`() = runBlocking {
        server.enqueue(MockResponse().setBody(body("微信")))
        server.enqueue(MockResponse().setBody(body("微信")))
        var list = listOf(source("S1"))
        val repo = StoreRepository(HttpFetcher(), sources = { list })

        repo.discover()
        val n1 = server.requestCount
        list = listOf(source("S1"), source("S2"))   // 加了新源 → 指纹变化
        repo.discover()
        assertTrue(server.requestCount > n1)
    }

    @Test
    fun `搜索缓存-同关键词第二次不重新抓取`() = runBlocking {
        server.enqueue(MockResponse().setBody(body("微信")))
        server.enqueue(MockResponse().setBody(body("微信")))
        val repo = StoreRepository(HttpFetcher(), sources = { listOf(searchSource("S1")) })

        repo.searchFlow("微信").toList()
        val n1 = server.requestCount
        repo.searchFlow("微信").toList()   // 缓存命中
        assertEquals(n1, server.requestCount)
    }

    @Test
    fun `localMatches-发现缓存本地匹配关键词`() = runBlocking {
        server.enqueue(MockResponse().setBody(body("微信", "抖音", "支付宝")))
        val repo = StoreRepository(HttpFetcher(), sources = { listOf(source("S1")) })
        repo.discover()

        val hit = repo.localMatches("微信")
        assertEquals(1, hit.size)
        assertEquals(listOf("微信"), hit[0].items.map { it.name })

        val miss = repo.localMatches("不存在的东西")
        assertTrue(miss.isEmpty())
    }
}
