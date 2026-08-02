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
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * StoreRepository 增量流测试：验证并发抓取带来的核心承诺：
 *  - 多个栏目并发执行：总耗时≈单个栏目耗时，而非叠加（慢源不拖累全局）
 *  - 单栏目失败不影响其他栏目
 *  - 搜索同样跨源并发
 */
class StoreFlowTest {

    companion object {
        private lateinit var server: MockWebServer
        private lateinit var base: String

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = MockWebServer()
            server.start()
            base = server.url("/").toString().trimEnd('/')
        }

        @JvmStatic
        @AfterClass
        fun stopServer() { server.shutdown() }
    }

    private fun source(): AppSource {
        fun section(title: String, path: String) = DiscoverySection(
            title = title,
            section = AppSection(
                url = path,
                listRule = "css:div.app",
                itemRules = ItemRules(name = "css:.n@text")
            )
        )
        return AppSource(
            sourceName = "S",
            sourceUrl = base,
            discovery = listOf(
                section("栏目A", "/a"),
                section("栏目B", "/b")
            )
        )
    }

    private val html = "<html><div class='app'><span class='n'>微信</span></div></html>"

    @Test
    fun `发现栏目并发执行-慢源不拖累全局`() = runBlocking {
        // 两个栏目都延迟 400ms：若并发总耗时≈400ms；若串行则≈800ms
        server.enqueue(MockResponse().setBodyDelay(400, TimeUnit.MILLISECONDS).setBody(html))
        server.enqueue(MockResponse().setBodyDelay(400, TimeUnit.MILLISECONDS).setBody(html))

        val repo = StoreRepository(HttpFetcher(), sources = { listOf(source()) })
        val t0 = System.currentTimeMillis()
        val received = repo.discoverFlow().toList()
        val elapsed = System.currentTimeMillis() - t0

        assertEquals(2, received.size)
        assertTrue("并发应远小于 800ms（串行），实际 ${elapsed}ms", elapsed < 700)
        received.forEach { assertEquals(1, it.items.size) }
    }

    @Test
    fun `发现flow单栏目失败不影响其他栏目`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(html))

        val repo = StoreRepository(HttpFetcher(), sources = { listOf(source()) })
        val received = repo.discoverFlow().toList()

        assertEquals(2, received.size)
        val failed = received.first { it.error != null }
        val ok = received.first { it.error == null }
        assertTrue(failed.items.isEmpty())
        assertEquals(1, ok.items.size)
        assertEquals("微信", ok.items[0].name)
    }

    @Test
    fun `搜索flow多个源增量返回`() = runBlocking {
        server.enqueue(MockResponse().setBody(html))
        server.enqueue(MockResponse().setBody(html))

        fun searchSource(name: String) = AppSource(
            sourceName = name,
            sourceUrl = base,
            search = AppSection(
                url = "/search?q={{key}}",
                listRule = "css:div.app",
                itemRules = ItemRules(name = "css:.n@text")
            )
        )
        val repo = StoreRepository(
            HttpFetcher(),
            sources = { listOf(searchSource("S1"), searchSource("S2")) }
        )
        val received = repo.searchFlow("微信").toList()

        assertEquals(2, received.size)
        received.forEach { assertEquals(1, it.items.size) }
    }

    @Test
    fun `搜索关键词二次过滤-大小写不敏感且要求名称或描述命中`() = runBlocking {
        val mixed = """
        <html><body>
            <div class='app'><span class='n'>DeepSeek 助手</span><p class='s'>智能AI应用</p></div>
            <div class='app'><span class='n'>deePseek Coder</span><p class='s'>编码工具</p></div>
            <div class='app'><span class='n'>抖音</span><p class='s'>短视频</p></div>
            <div class='app'><span class='n'>某工具</span><p class='s'>基于 deepseek 模型</p></div>
        </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(mixed))

        val src = AppSource(
            sourceName = "S",
            sourceUrl = base,
            search = AppSection(
                url = "/search?q={{key}}",
                listRule = "css:div.app",
                itemRules = ItemRules(
                    name = "css:.n@text",
                    summary = "css:.s@text"
                )
            )
        )
        val repo = StoreRepository(HttpFetcher(), sources = { listOf(src) })
        // 大小写不敏感：小写搜索词命中大写结果
        val received = repo.searchFlow("deepseek").toList()
        assertEquals(1, received.size)
        val names = received[0].items.map { it.name }
        assertEquals(listOf("DeepSeek 助手", "deePseek Coder", "某工具"), names)
        // 抖音（名称和描述都不含 deepseek）被过滤掉
        assertTrue(received[0].items.none { it.name == "抖音" })
    }

    @Test
    fun `搜索同名同版本去重-只保留一个`() = runBlocking {
        val dup = """
        <html><body>
            <div class='app'><span class='n'>抖音</span><span class='v'>30.5.0</span></div>
            <div class='app'><span class='n'>抖音</span><span class='v'>30.5.0</span></div>
            <div class='app'><span class='n'>抖音</span><span class='v'>30.4.0</span></div>
            <div class='app'><span class='n'>抖音极速版</span><span class='v'>30.5.0</span></div>
        </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(dup))

        val src = AppSource(
            sourceName = "S", sourceUrl = base,
            search = AppSection(
                url = "/search?q={{key}}",
                listRule = "css:div.app",
                itemRules = ItemRules(name = "css:.n@text", version = "css:.v@text")
            )
        )
        val repo = StoreRepository(HttpFetcher(), sources = { listOf(src) })
        val received = repo.searchFlow("抖音").toList()
        val names = received[0].items.map { it.name + "@" + it.version }
        assertEquals(listOf("抖音@30.5.0", "抖音@30.4.0", "抖音极速版@30.5.0"), names)
    }
}
