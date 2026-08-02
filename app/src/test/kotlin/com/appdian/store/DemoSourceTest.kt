package com.appdian.store

import com.appdian.engine.SourceParser
import com.appdian.engine.model.AppSource
import com.appdian.engine.model.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 验证 assets 里随应用发布的演示源文件能被引擎正确解析 */
class DemoSourceTest {

    private fun asset(path: String): String =
        File("src/main/assets/$path").readText()

    private val fDroidFixture = """
    {
      "packages": [
        {
          "packageName": "com.termux",
          "name": "Termux",
          "summary": "Terminal emulator with packages",
          "icon": "com.termux_1187.png",
          "suggestedVersionCode": 1187,
          "lastUpdated": 1699456165
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `f-droid 源文件可反序列化`() {
        val src = Sources.json.decodeFromString<AppSource>(asset("app_sources/f-droid.json"))
        assertEquals("F-Droid", src.sourceName)
        assertNotNull(src.search)
        assertNotNull(src.detail)
        assertTrue(src.discovery.isNotEmpty())
    }

    @Test
    fun `f-droid 源搜索规则能解析出真实条目`() {
        val src = Sources.json.decodeFromString<AppSource>(asset("app_sources/f-droid.json"))
        val parser = SourceParser()
        val items = parser.parseList(fDroidFixture, src.search!!, src, mapOf("key" to "termux"))
        assertEquals(1, items.size)
        assertEquals("Termux", items[0].name)
        assertEquals("com.termux", items[0].packageName)
        // icon 由 => 转换模板补全
        assertEquals("https://f-droid.org/repo/com.termux_1187.png", items[0].icon)
        assertEquals("https://f-droid.org/api/v1/packages/com.termux", items[0].detailUrl)
    }

    @Test
    fun `所有内置源文件都能反序列化`() {
        val parser = SourceParser()
        File("src/main/assets/app_sources").listFiles()!!.forEach { f ->
            val src = Sources.json.decodeFromString<AppSource>(f.readText())
            assertTrue("${f.name}: 缺 sourceName", src.sourceName.isNotBlank())
            assertTrue("${f.name}: 缺 sourceUrl", src.sourceUrl.isNotBlank())
            // 至少一个区块
            assertTrue("${f.name}: 无任何区块", src.search != null || src.detail != null || src.discovery.isNotEmpty())
        }
    }
}
