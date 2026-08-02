package com.appdian.store.data

import android.content.Context
import com.appdian.engine.model.AppSource
import com.appdian.engine.model.Sources
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 源仓库：应用源以 JSON 文件形式存于 filesDir/sources/ 下。
 * 天然支持导入 / 导出 / 分享（一个源就是一个 JSON 文件）。
 */
class SourceRepository(private val context: Context) {

    private val dir = File(context.filesDir, "sources")

    /** 读取源时宽容；导出时只保留有效字段 */
    private val exportJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    fun list(): List<AppSource> {
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().toList()
        // 同名源（sourceName 相同）只保留修改时间最新的文件，其余自动删除，
        // 避免 LazyColumn 重复 key 崩溃（如内置 huajun.json 与导入的 华军软件园.json 同源）
        val (kept, stale) = dedupSourceFiles(files)
        stale.forEach { runCatching { it.delete() } }
        return kept.mapNotNull { f ->
            runCatching { Sources.json.decodeFromString<AppSource>(f.readText()) }.getOrNull()
        }.sortedBy { it.sourceName }
    }

    companion object {
        /**
         * 同名源文件去重：按文件内 sourceName 分组，保留修改时间最新的文件。
         * @return 需要保留的文件 / 可删除的旧文件
         */
        fun dedupSourceFiles(files: List<File>): Pair<List<File>, List<File>> {
            val newestBy = LinkedHashMap<String, File>()
            for (f in files.sortedBy { it.lastModified() }) {
                val name = runCatching { Sources.json.decodeFromString<AppSource>(f.readText()) }
                    .getOrNull()?.sourceName ?: continue
                newestBy[name] = f
            }
            val kept = newestBy.values.toSet()
            val stale = files.filter { it !in kept }
            return newestBy.values.toList() to stale
        }
    }

    fun get(name: String): AppSource? = list().firstOrNull { it.sourceName == name }

    fun import(raw: String): Result<AppSource> = runCatching {
        val src = Sources.json.decodeFromString<AppSource>(raw.trim())
        require(src.sourceName.isNotBlank()) { "缺少 sourceName" }
        require(src.sourceUrl.isNotBlank()) { "缺少 sourceUrl" }
        require(src.search != null || src.detail != null || src.discovery.isNotEmpty()) {
            "至少需要 search / detail / discovery 之一"
        }
        save(src)
        src
    }

    fun save(src: AppSource) {
        dir.mkdirs()
        // 清理同 sourceName 的其他文件（内置文件名 ≠ sourceName 的场景），避免重复
        val target = File(dir, "${src.sourceName}.json")
        dir.listFiles { f -> f.isFile && f.absolutePath != target.absolutePath }?.forEach { f ->
            runCatching {
                if (Sources.json.decodeFromString<AppSource>(f.readText()).sourceName == src.sourceName) {
                    f.delete()
                }
            }
        }
        target.writeText(Sources.json.encodeToString(AppSource.serializer(), src))
    }

    fun delete(name: String) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().forEach { f ->
            runCatching {
                if (Sources.json.decodeFromString<AppSource>(f.readText()).sourceName == name) {
                    f.delete()
                }
            }
        }
    }

    fun setEnabled(name: String, enabled: Boolean) {
        val src = get(name) ?: return
        save(src.copy(enabled = enabled))
    }

    /** 导出全部（剔除运行时字段，便于分享） */
    fun exportAll(): String {
        val clean = list().map { it.copy(enabled = true) }
        return exportJson.encodeToString(ListSerializer(AppSource.serializer()), clean)
    }

    /** 首次启动：把内置示例源复制进来 */
    fun seedIfEmpty() {
        if (dir.exists() && (dir.listFiles()?.isNotEmpty() == true)) return
        dir.mkdirs()
        context.assets.list("app_sources")?.forEach { name ->
            val text = context.assets.open("app_sources/$name").bufferedReader().use { it.readText() }
            File(dir, name).writeText(text)
        }
    }
}
