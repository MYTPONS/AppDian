package com.appdian.store

import com.appdian.engine.SourceParser
import com.appdian.engine.model.AppSection
import com.appdian.engine.model.AppSource
import com.appdian.engine.model.ItemRules
import com.appdian.store.net.HttpFetcher
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test

/**
 * 集成测试：真实网络层（OkHttp）+ MockWebServer + 规则引擎，
 * 验证「抓取 → 解析」全链路（纯 JVM，无需设备）。
 */
class PipelineIntegrationTest {

    companion object {
        private lateinit var server: MockWebServer
        private lateinit var base: String

        private val searchJson = """
        {"packages": [
          {"packageName": "com.termux", "name": "Termux", "summary": "终端模拟器",
           "icon": "com.termux_1187.png", "suggestedVersionCode": 1187},
          {"packageName": "org.fdroid.fdroid", "name": "F-Droid", "summary": "FOSS 商店",
           "icon": "org.fdroid.fdroid_1021.png", "suggestedVersionCode": 1021}
        ]}
        """.trimIndent()

        private val detailJson = """
        {"packageName": "com.termux", "name": "Termux", "summary": "终端模拟器",
         "description": "Termux 是一个终端模拟器与 Linux 环境。",
         "icon": "com.termux_1187.png", "suggestedVersionCode": 1187,
         "packages": [{"versionName": "0.118.1", "versionCode": 1187,
                       "apkName": "com.termux_1187.apk", "size": 12345678}]}
        """.trimIndent()

        private val htmlPage = """
        <html><body>
          <div class="apps">
            <div class="app"><h3><a href="/app/1">阅读器</a></h3>
              <img src="/img/reader.png"><span class="v">v2.0</span></div>
            <div class="app"><h3><a href="/app/2">计算器</a></h3>
              <img src="/img/calc.png"><span class="v">v1.1</span></div>
          </div>
        </body></html>
        """.trimIndent()

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

        private fun enqueueJson(body: String) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            )
        }

        private fun enqueueHtml(body: String) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/html").setBody(body)
            )
        }
    }

    private val fetcher = HttpFetcher()
    private val parser = SourceParser()

    @Test
    fun `JSON 源搜索全链路`() = runBlocking {
        enqueueJson(searchJson)
        val source = AppSource(
            sourceName = "T",
            sourceUrl = base,
            search = AppSection(
                url = "/api/v1/search?q={{key}}",
                listRule = "json:$.packages",
                itemRules = ItemRules(
                    name = "json:name",
                    packageName = "json:packageName",
                    summary = "json:summary",
                    icon = "json:icon => {{sourceUrl}}/repo/{{this}}",
                    detailUrl = "{{sourceUrl}}/api/v1/packages/{{packageName}}"
                )
            )
        )
        val url = parser.buildUrl(source.search!!, source, mapOf("key" to "termux"))
        assertEquals("$base/api/v1/search?q=termux", url)
        val body = fetcher.fetch(url, source, source.search!!)
        val items = parser.parseList(body, source.search!!, source, mapOf("key" to "termux"))

        assertEquals(2, items.size)
        assertEquals("Termux", items[0].name)
        assertEquals("$base/repo/com.termux_1187.png", items[0].icon)
    }

    @Test
    fun `JSON 源详情全链路`() = runBlocking {
        enqueueJson(detailJson)
        val source = AppSource(
            sourceName = "T",
            sourceUrl = base,
            detail = AppSection(
                url = "/api/v1/packages/{{packageName}}",
                itemRules = ItemRules(
                    name = "json:$.name",
                    description = "json:$.description",
                    version = "json:$.packages[0].versionName",
                    downloadName = "json:$.packages[0].apkName",
                    downloadUrl = "{{sourceUrl}}/repo/{{downloadName}}",
                    downloadSize = "json:$.packages[0].size"
                )
            )
        )
        val url = parser.buildUrl(source.detail!!, source, mapOf("packageName" to "com.termux"))
        val body = fetcher.fetch(url, source, source.detail!!)
        val item = parser.parseDetail(body, source.detail!!, source, mapOf("packageName" to "com.termux"))

        assertNotNull(item)
        assertEquals("0.118.1", item!!.version)
        assertEquals("$base/repo/com.termux_1187.apk", item.downloadUrl)
        assertEquals("12345678", item.downloadSize)
    }

    @Test
    fun `HTML 源列表全链路`() = runBlocking {
        enqueueHtml(htmlPage)
        val source = AppSource(
            sourceName = "H",
            sourceUrl = base,
            search = AppSection(
                url = "/html",
                listRule = "css:div.app",
                itemRules = ItemRules(
                    name = "css:h3 a@text",
                    icon = "css:img@attr:src",
                    version = "css:span.v@text",
                    detailUrl = "css:h3 a@attr:href"
                )
            )
        )
        val url = parser.buildUrl(source.search!!, source, mapOf("key" to "x"))
        val body = fetcher.fetch(url, source, source.search!!)
        val items = parser.parseList(body, source.search!!, source)

        assertEquals(2, items.size)
        assertEquals("阅读器", items[0].name)
        assertEquals("$base/app/1", items[0].detailUrl)
        assertEquals("v2.0", items[0].version)
    }
}
