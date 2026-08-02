package com.appdian.store

import com.appdian.engine.model.AppSource
import com.appdian.engine.model.Sources
import com.appdian.store.data.SourceRepository
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 同名源去重测试：修复「源管理页 LazyColumn 重复 key 崩溃」的根因。
 * 内置源文件（huajun.json）与导入的同名源（华军软件园.json）会产生两个
 * sourceName 相同的源，必须只保留修改时间最新的文件并删除旧文件。
 */
class SourceRepositoryDedupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun source(name: String): AppSource = AppSource(
        sourceName = name,
        sourceUrl = "https://example.com/$name",
        sourceVersion = "1.0"
    )

    private fun jsonOf(src: AppSource): String = Sources.json.encodeToString(AppSource.serializer(), src)

    /** 写入源文件并返回；modStamp 用于错开修改时间 */
    private fun write(folder: File, fileName: String, src: AppSource): File {
        val f = File(folder, fileName)
        f.writeText(jsonOf(src))
        return f
    }

    @Test
    fun `同sourceName的两个文件-保留修改时间最新的`() {
        val dir = tmp.newFolder()
        // 内置 huajun.json（先写，旧）
        val huajun = write(dir, "huajun.json", source("华军软件园"))
        // 用户导入的 华军软件园.json（后写，新）
        val imported = write(dir, "华军软件园.json", source("华军软件园"))
        imported.setLastModified(huajun.lastModified() + 60_000L)

        val (kept, stale) = SourceRepository.dedupSourceFiles(dir.listFiles()!!.toList())

        assertEquals(listOf(imported), kept)
        assertEquals(listOf(huajun), stale)
        assertTrue(huajun.exists())
    }

    @Test
    fun `不同sourceName的文件全部保留`() {
        val dir = tmp.newFolder()
        val a = write(dir, "a.json", source("A源"))
        val b = write(dir, "b.json", source("B源"))

        val (kept, stale) = SourceRepository.dedupSourceFiles(listOf(a, b))

        assertEquals(setOf(a, b), kept.toSet())
        assertTrue(stale.isEmpty())
    }

    @Test
    fun `损坏的JSON文件标记为stale`() {
        val dir = tmp.newFolder()
        val good = write(dir, "good.json", source("好源"))
        val bad = File(dir, "bad.json")
        bad.writeText("{ 这不是合法 JSON")

        val (kept, stale) = SourceRepository.dedupSourceFiles(listOf(good, bad))

        assertEquals(listOf(good), kept)
        assertEquals(listOf(bad), stale)
    }

    @Test
    fun `同名时修改时间相同-保留其中一个不崩`() {
        val dir = tmp.newFolder()
        val a = write(dir, "a.json", source("同源"))
        val b = write(dir, "b.json", source("同源"))

        val (kept, stale) = SourceRepository.dedupSourceFiles(listOf(a, b))

        assertEquals(1, kept.size)
        assertEquals(1, stale.size)
        // kept 里的一定是 a 或 b 之一
        assertTrue(kept.first().absolutePath in listOf(a.absolutePath, b.absolutePath))
    }
}
