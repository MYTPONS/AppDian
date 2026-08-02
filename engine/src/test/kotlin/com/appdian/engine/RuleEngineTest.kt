package com.appdian.engine

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    private val html = """
        <html><body>
          <div class="list">
            <div class="app-item">
              <div class="name">应用甲</div>
              <img class="icon" src="/img/a.png">
              <div class="ver">v1.2.3</div>
              <a class="dl" href="/dl/a.apk">下载 45.6 MB</a>
            </div>
            <div class="app-item">
              <div class="name">应用乙</div>
              <img class="icon" src="/img/b.png">
              <a class="dl" href="/dl/b.apk">普通链接</a>
            </div>
          </div>
          <p>版本：9.8.7 正式版</p>
        </body></html>
    """.trimIndent()

    private val doc = Jsoup.parse(html)

    @Test
    fun `css 取文本 多元素换行拼接`() {
        assertEquals("应用甲\n应用乙", engine.evalString("css:.app-item .name", RuleContext(rootHtml = doc)))
    }

    @Test
    fun `css 取属性`() {
        assertEquals("/img/a.png\n/img/b.png", engine.evalString("css:.app-item img@attr:src", RuleContext(rootHtml = doc)))
        assertEquals("/dl/a.apk\n/dl/b.apk", engine.evalString("css:.app-item a.dl@attr:href", RuleContext(rootHtml = doc)))
    }

    @Test
    fun `css 多元素拼接`() {
        assertEquals("应用甲\n应用乙", engine.evalString("css:.app-item .name", RuleContext(rootHtml = doc)))
    }

    @Test
    fun `regex 取捕获组`() {
        assertEquals("9.8.7", engine.evalString("regex:版本[：:]\\s*([0-9.]+)", RuleContext(rootHtml = doc)))
    }

    @Test
    fun `regex 显式组号`() {
        // @2 指定取第 2 个捕获组
        assertEquals("6", engine.evalString("regex:(\\d+)[.](\\d+)[ ]*MB@2", RuleContext(rootHtml = doc)))
        assertEquals("45", engine.evalString("regex:(\\d+)[.](\\d+)[ ]*MB@1", RuleContext(rootHtml = doc)))
    }

    @Test
    fun `文本前缀`() {
        assertEquals("固定文案", engine.evalString("text:固定文案", RuleContext(rootHtml = doc)))
    }

    @Test
    fun `模板无前缀`() {
        val ctx = RuleContext(rootHtml = doc, vars = mapOf("key" to "hello"))
        assertEquals("搜索 hello", engine.evalString("text:搜索 {{key}}", ctx))
    }

    @Test
    fun `or 回退取第一个非空`() {
        assertEquals(
            "/dl/a.apk\n/dl/b.apk",
            engine.evalString("css:.app-item.none@attr:href||css:.app-item a.dl@attr:href",
                RuleContext(rootHtml = doc))
        )
        assertEquals(
            "兜底",
            engine.evalString("css:.not-exist@attr:href||css:.nope@attr:href||text:兜底",
                RuleContext(rootHtml = doc))
        )
    }

    @Test
    fun `类型不匹配时返回空并走回退`() {
        assertEquals(
            "应用甲\n应用乙",
            engine.evalString("json:$.name||css:.app-item .name",
                RuleContext(rootHtml = doc))
        )
    }

    @Test
    fun `json 规则在 json 上下文求值`() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement("""{"name": "X", "list": [{"n": 1}, {"n": 2}]}""")
        val ctx = RuleContext(rootJson = json)
        assertEquals("X", engine.evalString("json:$.name", ctx))
        assertEquals("X", engine.evalString("json:name", ctx.copy(jsonNode = json)))
    }

    @Test
    fun `listRule 取节点列表`() {
        val nodes = engine.evalNodes("css:.app-item", RuleContext(rootHtml = doc))
        assertEquals(2, nodes.size)
    }

    @Test
    fun `listRule json 通配符`() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement("""[{"n":1},{"n":2},{"n":3}]""")
        val nodes = engine.evalNodes("json:$[*]", RuleContext(rootJson = json))
        assertEquals(3, nodes.size)
    }

    @Test
    fun `css选择器支持has过滤-只取含soft链接的条目`() {
        val page = """
        <html><body>
        <ul class="m-sw-list2">
            <li class="item"><a class="tt" href="https://x/soft/1.htm">微信</a></li>
            <li class="item"><a class="tt" href="https://x/soft/2.htm">QQ</a></li>
            <li class="item"><a class="tt" href="https://x/article/3.htm">某文章</a></li>
        </ul>
        </body></html>
        """
        val doc = org.jsoup.Jsoup.parse(page)
        val ctx = RuleContext(rootHtml = doc)
        val nodes = engine.evalNodes("css:ul.m-sw-list2 li.item:has(a[href*='/soft/'])", ctx)
        assertEquals(2, nodes.size)
        val first = nodes[0] as com.appdian.engine.HtmlRNode
        assertEquals("微信", engine.evalString("css:.tt@text", ctx.copy(htmlNode = first.el)))
    }
}
