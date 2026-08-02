package com.appdian.store

import com.appdian.engine.model.AppSection
import com.appdian.engine.model.AppSource
import com.appdian.engine.model.ItemRules
import com.appdian.store.data.StoreRepository
import com.appdian.store.download.DownloadTask
import com.appdian.store.download.SourceSwitcher
import com.appdian.store.net.HttpFetcher
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 下载失败自动换源/换链接逻辑测试：
 *  - 同源换链接：原源其他同名同版本条目的下载链接优先，且不消耗换源次数
 *  - 换源：其他源的同名同版本条目（跳过已尝试源、上限 3 次）
 *  - 搜索无直链时抓详情页解析下载链接
 *  - 并发搜索顺序不可控 → 用 Dispatcher 按 path 分发，不同源不同路径
 */
class SourceSwitcherTest {

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

    /** 搜索列表自带下载直链的源（直链换源场景）；搜索路径带源名以便 dispatcher 区分 */
    private fun source(name: String): AppSource = AppSource(
        sourceName = name,
        sourceUrl = base,
        search = AppSection(
            url = "/search-$name?q={{key}}",
            listRule = "css:div.app",
            itemRules = ItemRules(
                name = "css:.n@text",
                detailUrl = "css:a.d@attr:href",
                downloadUrl = "css:a.dl@attr:href"
            )
        )
    )

    /** 搜索列表无直链、需抓详情页的源 */
    private fun detailSource(name: String): AppSource = AppSource(
        sourceName = name,
        sourceUrl = base,
        search = AppSection(
            url = "/search-$name?q={{key}}",
            listRule = "css:div.app",
            itemRules = ItemRules(name = "css:.n@text", detailUrl = "css:a.d@attr:href")
        ),
        detail = AppSection(
            url = "{{detailUrl}}",
            itemRules = ItemRules(
                name = "css:h1@text",
                downloadUrl = "css:a.qrcode_show@attr:href"
            )
        )
    )

    /** 每个源返回不同的 CDN 链接，避免跨源撞链接 */
    private fun listHtml(cdn: String): String = """
        <html><body>
            <div class='app'><span class='n'>抖音</span><a class='d' href='$base/soft/123'>详情</a><a class='dl' href='$cdn/douyin.apk'>下</a></div>
            <div class='app'><span class='n'>抖音极速版</span><a class='d' href='$base/soft/124'>详情</a><a class='dl' href='$cdn/lite.apk'>下</a></div>
        </body></html>
    """.trimIndent()

    private val detailHtml: String
        get() = """
        <html><body>
            <h1>抖音 30.5.0</h1>
            <a class='qrcode_show' href='https://cdn2.example.com/douyin-3050.apk'>本地下载</a>
        </body></html>
    """.trimIndent()

    /** 按 path 分发：/search-源名 → 该源列表（不同 CDN）；/soft/123 → 详情页 */
    private fun dispatcher(cdns: Map<String, String>): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: return MockResponse().setResponseCode(404)
            return when {
                path.startsWith("/search-") -> {
                    val src = path.removePrefix("/search-").substringBefore("?")
                    val cdn = cdns[src] ?: "https://cdn-none.example.com"
                    MockResponse().setBody(listHtml(cdn))
                }
                path.startsWith("/soft/") -> MockResponse().setBody(detailHtml)
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun switcher(vararg srcs: AppSource) =
        SourceSwitcher(StoreRepository(HttpFetcher(), sources = { srcs.toList() }))

    @Test
    fun `匹配逻辑-名称大小写不敏感严格同名-变体不匹配`() {
        assertTrue(SourceSwitcher.nameMatches("抖音", "抖音"))
        assertTrue(SourceSwitcher.nameMatches("DEEPSEEK", "deepseek"))
        assertTrue(SourceSwitcher.nameMatches(" 微信 ", "微信"))
        // 变体/包含不算同一软件
        assertFalse(SourceSwitcher.nameMatches("抖音极速版", "抖音"))
        assertFalse(SourceSwitcher.nameMatches("快手", "抖音"))
    }

    @Test
    fun `匹配逻辑-版本未知宽松放行-已知需严格相等`() {
        assertTrue(SourceSwitcher.versionMatches(null, "30.5.0"))
        assertTrue(SourceSwitcher.versionMatches("30.5.0", null))
        assertTrue(SourceSwitcher.versionMatches("30.5.0", "30.5.0"))
        assertFalse(SourceSwitcher.versionMatches("30.5.0", "30.4.0"))
    }

    @Test
    fun `换源-同名同版本在别的源找到直链`() = runBlocking {
        server.dispatcher = dispatcher(mapOf("S2" to "https://cdn2.example.com", "S3" to "https://cdn3.example.com"))

        val task = DownloadTask(
            id = 1, url = "https://bad.example.com/x.apk", title = "抖音",
            fileName = "抖音.apk", sourceName = "S1", version = "30.5.0",
            triedSources = listOf("S1")
        )
        val alt = switcher(source("S2"), source("S3")).find(task)
        assertNotNull(alt)
        // 原源 S1 不在源列表 → 同源无链接 → 换源（S2/S3 谁先返回都行）
        assertTrue(alt!!.sourceName == "S2" || alt.sourceName == "S3")
        assertFalse(alt.sameSource)
        assertTrue(alt.url.endsWith("douyin.apk"))
    }

    @Test
    fun `换源-跳过已尝试的源-从其他源找`() = runBlocking {
        server.dispatcher = dispatcher(mapOf("S2" to "https://cdn2.example.com", "S3" to "https://cdn3.example.com"))

        // S2 的同名同版本链接已试过（triedLinks），同源无新链接 → 换 S3
        val task = DownloadTask(
            id = 1, url = "https://cdn2.example.com/douyin.apk", title = "抖音",
            fileName = "抖音.apk", sourceName = "S2", version = null,
            triedSources = listOf("S2"),
            triedLinks = listOf("https://cdn2.example.com/douyin.apk")
        )
        val alt = switcher(source("S2"), source("S3")).find(task)
        assertNotNull(alt)
        assertEquals("S3", alt!!.sourceName)
        assertEquals("https://cdn3.example.com/douyin.apk", alt.url)
    }

    @Test
    fun `换源-已尝试源数量达到上限返回null`() = runBlocking {
        server.dispatcher = dispatcher(mapOf("S2" to "https://cdn2.example.com"))

        val task = DownloadTask(
            id = 1, url = "x", title = "抖音", fileName = "抖音.apk",
            sourceName = "S1", triedSources = listOf("A", "B", "C")
        )
        // 同源 S1 无组（不在源列表）→ 换源次数用尽 → null
        assertNull(switcher(source("S2")).find(task))
    }

    @Test
    fun `换源-搜索无直链时抓详情页解析下载链接`() = runBlocking {
        server.dispatcher = dispatcher(mapOf("S2" to "https://cdn2.example.com", "S3" to "https://cdn3.example.com"))

        val task = DownloadTask(
            id = 1, url = "x", title = "抖音", fileName = "抖音.apk", version = "30.5.0"
        )
        val alt = switcher(detailSource("S2"), detailSource("S3")).find(task)
        assertNotNull(alt)
        assertTrue(alt!!.sourceName == "S2" || alt.sourceName == "S3")
        // 列表条目无直链，走详情页拿到下载链
        assertEquals("https://cdn2.example.com/douyin-3050.apk", alt.url)
    }

    @Test
    fun `换源-找不到匹配应用返回null`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setBody("<html><div class='app'><span class='n'>快手</span></div></html>")
        }

        val task = DownloadTask(
            id = 1, url = "x", title = "抖音", fileName = "抖音.apk"
        )
        assertNull(switcher(source("S2"), source("S3")).find(task))
    }

    @Test
    fun `同源换链接-原源另一下载链接优先于换源`() = runBlocking {
        // 华军场景：同名同版本多条，各自不同下载链接。S1 失败后先在同源找第二条链接
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(
                """
                <html><body>
                    <div class='app'><span class='n'>微信</span><a class='dl' href='https://cdn1.example.com/wechat-a.apk'>下</a></div>
                    <div class='app'><span class='n'>微信</span><a class='dl' href='https://cdn1.example.com/wechat-b.apk'>下</a></div>
                </body></html>
                """.trimIndent()
            )
        }

        val task = DownloadTask(
            id = 1, url = "https://cdn1.example.com/wechat-a.apk", title = "微信",
            fileName = "微信.apk", sourceName = "S1", version = "8.0.76",
            triedSources = emptyList(),
            triedLinks = listOf("https://cdn1.example.com/wechat-a.apk")
        )
        val alt = switcher(source("S1"), source("S2")).find(task)
        assertNotNull(alt)
        assertTrue(alt!!.sameSource)
        assertEquals("S1", alt.sourceName)
        assertEquals("https://cdn1.example.com/wechat-b.apk", alt.url)
    }

    @Test
    fun `同源换链接不消耗换源次数-次数用尽仍可同源换链接`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(
                """
                <html><body>
                    <div class='app'><span class='n'>微信</span><a class='dl' href='https://cdn1.example.com/wechat-c.apk'>下</a></div>
                </body></html>
                """.trimIndent()
            )
        }

        // triedSources 已满 3 个，但同源还有新链接 → 仍然返回同源链接
        val task = DownloadTask(
            id = 1, url = "https://old.example.com/x.apk", title = "微信",
            fileName = "微信.apk", sourceName = "S1", version = "8.0.76",
            triedSources = listOf("A", "B", "C")
        )
        val alt = switcher(source("S1"), source("S2")).find(task)
        assertNotNull(alt)
        assertTrue(alt!!.sameSource)
        assertEquals("https://cdn1.example.com/wechat-c.apk", alt.url)
    }

    @Test
    fun `同源无新链接且换源次数用尽返回null`() = runBlocking {
        server.dispatcher = dispatcher(mapOf("S1" to "https://cdn1.example.com", "S2" to "https://cdn2.example.com"))

        // S1 只有"抖音"（链接已试）和"抖音极速版"（名称不匹配）→ 同源无新链接
        // triedSources 已满 → 换源也不允许 → null
        val task = DownloadTask(
            id = 1, url = "https://cdn1.example.com/douyin.apk", title = "抖音",
            fileName = "抖音.apk", sourceName = "S1", version = "30.5.0",
            triedSources = listOf("A", "B", "C"),
            triedLinks = listOf("https://cdn1.example.com/douyin.apk")
        )
        assertNull(switcher(source("S1"), source("S2")).find(task))
    }
}
