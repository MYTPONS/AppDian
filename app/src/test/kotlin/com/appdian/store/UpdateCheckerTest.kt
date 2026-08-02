package com.appdian.store

import com.appdian.store.data.UpdateChecker
import com.appdian.store.data.UpdateInfo
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** GitHub 更新检查测试：release 解析 / HTTP 状态处理 / 版本比较 */
class UpdateCheckerTest {

    private lateinit var server: MockWebServer
    private lateinit var checker: UpdateChecker

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        checker = UpdateChecker(apiUrl = server.url("/releases").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val releaseJson = """
        {
          "tag_name": "v0.2.0",
          "name": "v0.2.0",
          "body": "修复源管理崩溃\n新增分类",
          "published_at": "2026-08-02T10:00:00Z",
          "assets": [
            { "name": "appdian-release.apk", "browser_download_url": "https://github.com/MYTPONS/AppDian/releases/download/v0.2.0/appdian-release.apk", "size": 12000000 },
            { "name": "appdian-release.aab", "browser_download_url": "https://github.com/MYTPONS/AppDian/releases/download/v0.2.0/appdian-release.aab" }
          ]
        }
    """.trimIndent()

    @Test
    fun `解析最新release得到版本号APK地址和说明`() {
        val info = checker.parseReleases("[" + releaseJson + "]")
        assertNotNull(info)
        assertEquals("v0.2.0", info!!.version)
        assertEquals(
            "https://github.com/MYTPONS/AppDian/releases/download/v0.2.0/appdian-release.apk",
            info.apkUrl
        )
        assertTrue(info.notes.contains("修复源管理崩溃"))
        assertEquals("2026-08-02T10:00:00Z", info.publishedAt)
    }

    @Test
    fun `无APK资源的release返回null`() {
        val json = """[{"tag_name":"v0.2.0","assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"}]}]"""
        assertNull(checker.parseReleases(json))
    }

    @Test
    fun `check成功-有更新信息`() = runBlocking {
        server.enqueue(MockResponse().setBody("[" + releaseJson + "]"))
        val r = checker.check()
        assertTrue(r.isSuccess)
        assertEquals("v0.2.0", r.getOrNull()!!.version)
    }

    @Test
    fun `check取第一个有APK的release忽略无APK的最新发布`() = runBlocking {
        val noApk = """{"tag_name":"v5.0.0","assets":[{"name":"readme.txt","browser_download_url":"https://x/r"}]}"""
        server.enqueue(MockResponse().setBody("[" + noApk + "," + releaseJson + "]"))
        val r = checker.check()
        assertTrue(r.isSuccess)
        assertEquals("v0.2.0", r.getOrNull()!!.version)
    }

    @Test
    fun `check 404-仓库无release返回成功null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val r = checker.check()
        assertTrue(r.isSuccess)
        assertNull(r.getOrNull())
    }

    @Test
    fun `check 500-返回失败`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val r = checker.check()
        assertTrue(r.isFailure)
    }

    @Test
    fun `版本比较-v开头的tag与本地版本`() {
        assertTrue(UpdateChecker.isNewer("v0.2.0", "0.1.0"))
        assertTrue(UpdateChecker.isNewer("v1.0.0", "v0.9.9"))
        assertFalse(UpdateChecker.isNewer("v0.1.0", "0.1.0"))
        assertFalse(UpdateChecker.isNewer("v0.1.0", "0.2.0"))
        // 无法解析的版本：latest 可解析算更新
        assertTrue(UpdateChecker.isNewer("v0.2.0", "abc"))
    }

    @Test
    fun `版本比较-多段与补齐`() {
        assertTrue(UpdateChecker.isNewer("v0.2.0.1", "0.2.0"))
        assertTrue(UpdateChecker.isNewer("v0.10.0", "v0.9.0"))
        assertFalse(UpdateChecker.isNewer("v0.2.0", "0.2.0.0"))
    }

    @Test
    fun `UpdateInfo序列化往返`() {
        val info = UpdateInfo("v1.2.3", "https://x/app.apk", "说明", "2026-01-01T00:00:00Z")
        val json = kotlinx.serialization.json.Json.encodeToString(
            com.appdian.store.data.UpdateInfo.serializer(), info
        )
        val back = kotlinx.serialization.json.Json.decodeFromString(
            com.appdian.store.data.UpdateInfo.serializer(), json
        )
        assertEquals(info, back)
    }
}
