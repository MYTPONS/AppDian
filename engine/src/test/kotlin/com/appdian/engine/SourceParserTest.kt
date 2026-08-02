package com.appdian.engine

import com.appdian.engine.model.AppSection
import com.appdian.engine.model.AppSource
import com.appdian.engine.model.ItemRules
import com.appdian.engine.model.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceParserTest {

    private val parser = SourceParser()

    // ---------------- 夹具：F-Droid 搜索接口（JSON 源） ----------------

    private val fdroidSearch = """
    {
      "packages": [
        {
          "packageName": "com.termux",
          "name": "Termux",
          "summary": "Terminal emulator with packages",
          "icon": "com.termux_1187.png",
          "suggestedVersionCode": 1187,
          "lastUpdated": 1699456165
        },
        {
          "packageName": "org.fdroid.fdroid",
          "name": "F-Droid",
          "summary": "Foss app store",
          "icon": "org.fdroid.fdroid_1021.png",
          "suggestedVersionCode": 1021,
          "lastUpdated": 1699000000
        }
      ]
    }
    """.trimIndent()

    private val fdroidSource = AppSource(
        sourceName = "F-Droid",
        sourceUrl = "https://f-droid.org",
        search = AppSection(
            url = "/api/v1/search?q={{key}}&lang=en",
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

    @Test
    fun `提取结果经转换模板加工`() {
        val src = AppSource(
            sourceName = "转换测试",
            sourceUrl = "https://a.com",
            detail = AppSection(
                url = "/x",
                itemRules = ItemRules(
                    name = "json:$.name",
                    icon = "json:$.icon => {{sourceUrl}}/repo/{{this}}",
                    downloadUrl = "json:$.apk => https://cdn.a.com/files/{{this}}"
                )
            )
        )
        val body = """{"name": "X", "icon": "i.png", "apk": "x.apk"}"""
        val item = parser.parseDetail(body, src.detail!!, src)
        assertNotNull(item)
        assertEquals("https://a.com/repo/i.png", item!!.icon)
        assertEquals("https://cdn.a.com/files/x.apk", item.downloadUrl)
    }

    @Test
    fun `extras 中间变量参与模板拼接`() {
        val src = AppSource(
            sourceName = "GitHub 风格",
            sourceUrl = "https://api.example.com",
            search = AppSection(
                url = "/search?q={{key}}",
                listRule = "json:$.items",
                itemRules = ItemRules(
                    name = "json:full_name",
                    summary = "json:description",
                    extras = mapOf("full_name" to "json:full_name"),
                    detailUrl = "{{sourceUrl}}/repos/{{full_name}}/releases/latest"
                )
            ),
            detail = AppSection(
                url = "/repos/{{full_name}}/releases/latest",
                itemRules = ItemRules(
                    name = "json:$.assets[0].name",
                    version = "json:$.tag_name"
                )
            )
        )
        val body = """{"items": [{"full_name": "M66B/NetGuard", "description": "防火墙"}]}"""
        val items = parser.parseList(body, src.search!!, src)
        assertEquals(1, items.size)
        assertEquals("https://api.example.com/repos/M66B/NetGuard/releases/latest", items[0].detailUrl)
        // 条目变量透传给详情 URL
        assertEquals("M66B/NetGuard", items[0].vars["full_name"])
        val detailUrl = parser.buildUrl(src.detail!!, src, items[0].vars)
        assertEquals("https://api.example.com/repos/M66B/NetGuard/releases/latest", detailUrl)
    }

    @Test
    fun `搜索解析出条目与图标补全`() {
        val items = parser.parseList(fdroidSearch, fdroidSource.search!!, fdroidSource, mapOf("key" to "termux"))
        assertEquals(2, items.size)
        val termux = items[0]
        assertEquals("Termux", termux.name)
        assertEquals("com.termux", termux.packageName)
        assertEquals("https://f-droid.org/repo/com.termux_1187.png", termux.icon)
        assertEquals("https://f-droid.org/api/v1/packages/com.termux", termux.detailUrl)
    }

    @Test
    fun `URL 模板展开`() {
        val url = parser.buildUrl(fdroidSource.search!!, fdroidSource, mapOf("key" to "termux"))
        assertEquals("https://f-droid.org/api/v1/search?q=termux&lang=en", url)
    }

    // ---------------- 夹具：F-Droid 详情接口 ----------------

    private val fdroidDetail = """
    {
      "packageName": "com.termux",
      "name": "Termux",
      "summary": "Terminal emulator with packages",
      "description": "Termux is a terminal emulator and Linux environment.",
      "icon": "com.termux_1187.png",
      "suggestedVersionCode": 1187,
      "packages": [
        {
          "versionName": "0.118.1",
          "versionCode": 1187,
          "apkName": "com.termux_1187.apk",
          "size": 12345678,
          "added": 1699456165
        }
      ]
    }
    """.trimIndent()

    private val fdroidDetailSource = AppSource(
        sourceName = "F-Droid",
        sourceUrl = "https://f-droid.org",
        detail = AppSection(
            url = "/api/v1/packages/{{packageName}}",
            itemRules = ItemRules(
                name = "json:$.name",
                packageName = "json:$.packageName",
                icon = "json:$.icon => {{sourceUrl}}/repo/{{this}}",
                description = "json:$.description",
                version = "json:$.packages[0].versionName",
                downloadName = "json:$.packages[0].apkName",
                downloadUrl = "{{sourceUrl}}/repo/{{downloadName}}",
                downloadSize = "json:$.packages[0].size"
            )
        )
    )

    @Test
    fun `详情解析出下载地址`() {
        val item = parser.parseDetail(fdroidDetail, fdroidDetailSource.detail!!, fdroidDetailSource)
        assertNotNull(item)
        assertEquals("Termux", item!!.name)
        assertEquals("0.118.1", item.version)
        assertEquals("https://f-droid.org/repo/com.termux_1187.apk", item.downloadUrl)
        assertEquals("com.termux_1187.apk", item.downloadName)
        assertEquals("12345678", item.downloadSize)
        assertEquals("Termux is a terminal emulator and Linux environment.", item.description)
    }

    // ---------------- 夹具：HTML 源 ----------------

    private val htmlPage = """
        <html><body>
          <div class="apps">
            <div class="app">
              <h3 class="title"><a href="/app/1">文本阅读器</a></h3>
              <img class="ico" src="/img/reader.png">
              <span class="v">v2.1.0</span>
            </div>
            <div class="app">
              <h3 class="title"><a href="/app/2">计算器</a></h3>
              <img class="ico" src="/img/calc.png">
              <span class="v">v1.0</span>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    private val htmlSource = AppSource(
        sourceName = "示例HTML站",
        sourceUrl = "https://example.com",
        search = AppSection(
            url = "/search?q={{key}}",
            listRule = "css:div.app",
            itemRules = ItemRules(
                name = "css:h3.title a@text",
                detailUrl = "css:h3.title a@attr:href",
                icon = "css:img.ico@attr:src",
                version = "css:span.v@text"
            )
        )
    )

    @Test
    fun `HTML 列表解析与相对 URL 补全`() {
        val items = parser.parseList(htmlPage, htmlSource.search!!, htmlSource, mapOf("key" to "reader"))
        assertEquals(2, items.size)
        assertEquals("文本阅读器", items[0].name)
        assertEquals("https://example.com/app/1", items[0].detailUrl)
        assertEquals("https://example.com/img/reader.png", items[0].icon)
        assertEquals("v2.1.0", items[0].version)
        assertEquals("v1.0", items[1].version)
    }

    @Test
    fun `华军搜索解析出安卓应用列表`() {
        val src = loadHuajun()
        val body = java.io.File("src/test/resources/huajun/search.html").readText()
        val items = parser.parseList(body, src.search!!, src)
        assertTrue("应至少 5 条", items.size >= 5)
        val item = items.first()
        assertTrue("名字含微信", item.name.contains("微信"))
        assertTrue("detailUrl 前缀", item.detailUrl!!.startsWith("https://www.onlinedown.net/soft/"))
        assertTrue("detailUrl 后缀", item.detailUrl!!.endsWith(".htm"))
        assertTrue("图标是 URL", item.icon!!.startsWith("http"))
        assertFalse("大小应去掉前缀", item.downloadSize!!.contains("大小"))
        assertFalse("应有简介", item.summary.isNullOrBlank())
    }

    @Test
    fun `华军详情解析出下载直链与版本`() {
        val src = loadHuajun()
        val body = java.io.File("src/test/resources/huajun/detail.html").readText()
        val item = parser.parseDetail(body, src.detail!!, src, mapOf("detailUrl" to "https://www.onlinedown.net/soft/510462.htm"))!!
        assertEquals("作业帮", item.name)
        assertTrue("版本号", item.version!!.matches(Regex("\\d+\\.\\d+\\.\\d+")))
        assertTrue("下载直链", item.downloadUrl!!.startsWith("https://download.") && item.downloadUrl.contains(".apk"))
        assertTrue("下载文件名", item.downloadName!!.endsWith(".apk"))
        assertEquals("2026-07-09", item.lastUpdate)
        assertFalse("应有描述", item.description.isNullOrBlank())
    }

    @Test
    fun `华军详情二解析出跳转下载链接与更新时间`() {
        val src = loadHuajun()
        val body = java.io.File("src/test/resources/huajun/detail-wechat.html").readText()
        val item = parser.parseDetail(body, src.detail!!, src, mapOf("detailUrl" to "https://www.onlinedown.net/soft/578369.htm"))!!
        assertEquals("微信", item.name)
        assertTrue("版本", item.version!!.startsWith("8.0."))
        assertTrue("下载跳转链接", item.downloadUrl!!.contains("iopdfbhjl/578369"))
        assertTrue("更新时间", item.lastUpdate!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertTrue("大小", item.downloadSize!!.contains("MB"))
    }

    @Test
    fun `华军发现页解析出推荐应用`() {
        val src = loadHuajun()
        val body = java.io.File("src/test/resources/huajun/discovery.html").readText()
        val items = parser.parseList(body, src.discovery[0].section, src)
        assertTrue("推荐条目应较多, 实际:" + items.size, items.size > 30)
        assertTrue("名字非空", items.all { it.name.isNotBlank() })
        assertTrue("detailUrl 正确", items.first().detailUrl!!.startsWith("https://www.onlinedown.net/soft/"))
    }



    private fun loadHuajun(): AppSource {
        val json = java.io.File("src/test/resources/huajun.json").readText()
        return Sources.json.decodeFromString(json)
    }

    @Test
    fun `HTML 详情解析`() {
        val detailPage = """
            <html><body>
              <h1>文本阅读器</h1>
              <img class="cover" src="https://cdn.example.com/reader.png">
              <div class="desc"><p>一个强大的阅读器</p><p>支持所有格式</p></div>
              <a class="download" href="/download/reader_2.1.0.apk">下载</a>
            </body></html>
        """.trimIndent()
        val detailSource = AppSource(
            sourceName = "示例HTML站",
            sourceUrl = "https://example.com",
            detail = AppSection(
                url = "/app/1",
                itemRules = ItemRules(
                    name = "css:h1@text",
                    icon = "css:img.cover@attr:src",
                    description = "css:div.desc@text",
                    downloadUrl = "css:a.download@attr:href"
                )
            )
        )
        val item = parser.parseDetail(detailPage, detailSource.detail!!, detailSource)
        assertNotNull(item)
        assertEquals("文本阅读器", item!!.name)
        assertEquals("https://cdn.example.com/reader.png", item.icon)
        assertEquals("https://example.com/download/reader_2.1.0.apk", item.downloadUrl)
        assertEquals("一个强大的阅读器 支持所有格式", item.description)
    }

    // ---------------- 分类字段 ----------------



    @Test
    fun `条目规则提取 category 字段`() {
        val src = AppSource(
            sourceName = "带分类源",
            sourceUrl = "https://f.example.org",
            search = AppSection(
                url = "/search?q={{key}}",
                listRule = "json:$.items",
                itemRules = ItemRules(
                    name = "json:name",
                    category = "json:category"
                )
            )
        )
        val body = """{"items": [{"name": "Firefox", "category": "browser"}, {"name": "VLC", "category": "media"}]}"""
        val items = parser.parseList(body, src.search!!, src)
        assertEquals(2, items.size)
        assertEquals("browser", items[0].category)
        assertEquals("media", items[1].category)
    }

    @Test
    fun `DiscoverySection 携带分类声明并参与序列化`() {
        val json = """
        {
          "sourceName": "分类源",
          "sourceUrl": "https://a.com",
          "discovery": [
            {
              "title": "影音专区",
              "category": "media",
              "section": {
                "url": "/media",
                "listRule": "css:.item",
                "itemRules": { "name": "css:.name@text" }
              }
            }
          ]
        }
        """
        val src = Sources.json.decodeFromString<AppSource>(json)
        assertEquals(1, src.discovery.size)
        assertEquals("media", src.discovery[0].category)
        assertEquals("影音专区", src.discovery[0].title)

        // 不声明分类时兼容旧格式（字段为 null）
        val oldJson = """
        {
          "sourceName": "旧源",
          "sourceUrl": "https://b.com",
          "discovery": [
            {
              "title": "旧栏目",
              "section": {
                "url": "/old",
                "itemRules": { "name": "css:.name@text" }
              }
            }
          ]
        }
        """
        val old = Sources.json.decodeFromString<AppSource>(oldJson)
        assertEquals(null, old.discovery[0].category)
    }
}
