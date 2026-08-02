package com.appdian.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateTest {
    @Test
    fun `展开已知变量`() {
        val out = Template.expand("https://f-droid.org/api/v1/packages/{{packageName}}", mapOf("packageName" to "com.termux"))
        assertEquals("https://f-droid.org/api/v1/packages/com.termux", out)
    }

    @Test
    fun `未命中变量展开为空`() {
        assertEquals("https://x.com/", Template.expand("https://x.com/{{missing}}", emptyMap()))
    }

    @Test
    fun `expandExisting 保留未命中变量`() {
        assertEquals("https://x.com/{{icon}}", Template.expandExisting("https://x.com/{{icon}}", mapOf("k" to "v")))
        assertEquals("https://x.com/icon.png", Template.expandExisting("https://x.com/{{icon}}", mapOf("icon" to "icon.png")))
    }
}

class JsonPathTest {
    private val json = Json.parseToJsonElement(
        """
        {"data": {"list": [{"name": "a", "ver": "1.0"}, {"name": "b", "ver": "2.0"}]}, "first": "x"}
        """.trimIndent()
    )

    @Test
    fun `绝对路径取对象列表`() {
        val r = JsonPath.query(json, "$.data.list")
        assertEquals(1, r.size)
    }

    @Test
    fun `通配符取全部元素`() {
        val r = JsonPath.query(json, "$.data.list[*]")
        assertEquals(2, r.size)
    }

    @Test
    fun `下标访问`() {
        assertEquals("a", JsonPath.queryString(json, "$.data.list[0].name"))
        assertEquals("2.0", JsonPath.queryString(json, "$.data.list[1].ver"))
    }

    @Test
    fun `相对路径以当前节点为根`() {
        val item = JsonPath.query(json, "$.data.list[0]").first()
        assertEquals("a", JsonPath.queryString(item, "name"))
        assertEquals("a", JsonPath.queryString(item, "$.name"))
    }

    @Test
    fun `路径不存在返回空`() {
        assertNull(JsonPath.queryString(json, "$.data.list[9].name"))
        assertEquals(0, JsonPath.query(json, "$.nothing[*]").size)
    }
}
