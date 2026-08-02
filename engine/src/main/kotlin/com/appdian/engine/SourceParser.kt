package com.appdian.engine

import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSection
import com.appdian.engine.model.AppSource
import com.appdian.engine.model.ItemRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup

/**
 * 源解析器：把「网络响应文本 + 区块规则」解析为统一的应用条目。
 * 网络请求由外层负责，这里只做纯解析（可单测）。
 */
class SourceParser(
    private val engine: RuleEngine = RuleEngine()
) {

    /** 展开 URL 模板并拼接为绝对地址 */
    fun buildUrl(section: AppSection, source: AppSource, vars: Map<String, String>): String {
        val baseVars = baseVars(source)
        val url = Template.expand(section.url, baseVars + vars)
        return resolveUrl(source.sourceUrl, url)
    }

    /** 解析列表（搜索 / 发现共用） */
    fun parseList(body: String, section: AppSection, source: AppSource, vars: Map<String, String> = emptyMap()): List<AppItem> {
        val type = resolveType(section, body)
        val base = baseVars(source)
        val ruleVars = base + vars

        val rootCtx: RuleContext
        val nodes: List<RNode>
        when (type) {
            "JSON" -> {
                val root = parseJson(body) ?: return emptyList()
                rootCtx = RuleContext(rootJson = root, vars = ruleVars)
                nodes = section.listRule?.let { engine.evalNodes(it, rootCtx) } ?: emptyList()
            }
            else -> {
                val doc = Jsoup.parse(body, source.sourceUrl)
                rootCtx = RuleContext(rootHtml = doc, vars = ruleVars)
                nodes = section.listRule?.let { engine.evalNodes(it, rootCtx) } ?: emptyList()
            }
        }

        return nodes.mapNotNull { node ->
            val itemCtx = when (node) {
                is HtmlRNode -> rootCtx.copy(htmlNode = node.el)
                is JsonRNode -> rootCtx.copy(jsonNode = node.el)
                is TextRNode -> rootCtx.copy()
            }
            extractItem(section.itemRules, itemCtx, source, base)
        }
    }

    /** 解析详情页 */
    fun parseDetail(body: String, section: AppSection, source: AppSource, vars: Map<String, String> = emptyMap()): AppItem? {
        val type = resolveType(section, body)
        val base = baseVars(source)
        val ruleVars = base + vars

        val ctx = when (type) {
            "JSON" -> {
                val root = parseJson(body) ?: return null
                RuleContext(rootJson = root, jsonNode = root, vars = ruleVars)
            }
            else -> RuleContext(
                rootHtml = Jsoup.parse(body, source.sourceUrl),
                htmlNode = Jsoup.parse(body, source.sourceUrl).body(),
                vars = ruleVars
            )
        }
        return extractItem(section.itemRules, ctx, source, base)
    }

    // ---------------- 内部 ----------------

    private fun extractItem(rules: ItemRules, ctx: RuleContext, source: AppSource, base: Map<String, String>): AppItem {
        // 第一步：按依赖顺序提取原始值（未解析的 {{字段}} 保留原样）
        val raw = mutableMapOf<String, String>()
        val fieldOrder = listOf(
            "packageName" to rules.packageName,
            "name" to rules.name,
            "version" to rules.version,
            "summary" to rules.summary,
            "lastUpdate" to rules.lastUpdate,
            "developer" to rules.developer,
            "downloadName" to rules.downloadName,
            "icon" to rules.icon,
            "detailUrl" to rules.detailUrl,
            "description" to rules.description,
            "downloadUrl" to rules.downloadUrl,
            "downloadSize" to rules.downloadSize,
            "category" to rules.category
        )
        for ((key, rule) in fieldOrder) {
            if (rule.isNullOrBlank()) continue
            // 规则支持 || 回退链；每个回退分支可带 => 转换（模板或 regex 管道）
            // 例：json:icon => {{sourceUrl}}/repo/{{this}}
            //    css:h1@text => regex:^(.+?)\s+[\d.]+$ || css:h1@text
            val vars = ctx.vars + raw
            val value = rule.split("||").mapNotNull { part ->
                val (extractRule, transform) = splitTransform(part.trim())
                val v = engine.evalString(extractRule, ctx.copy(vars = vars), keepUnknown = true)
                if (v.isBlank()) null
                else if (transform != null) applyTransform(transform, v, vars) else v
            }.firstOrNull { it.isNotBlank() }
            if (value != null) raw[key] = value
        }
        // 额外中间变量：先于条目字段提取，供其它字段模板引用
        for ((key, rule) in rules.extras) {
            if (rule.isBlank()) continue
            val vars = ctx.vars + raw
            val value = rule.split("||").mapNotNull { part ->
                val (extractRule, transform) = splitTransform(part.trim())
                val v = engine.evalString(extractRule, ctx.copy(vars = vars), keepUnknown = true)
                if (v.isBlank()) null
                else if (transform != null) applyTransform(transform, v, vars) else v
            }.firstOrNull { it.isNotBlank() }
            if (value != null) raw[key] = value
        }

        // 第二步：交叉引用展开 + 相对 URL 补全
        fun finalize(key: String, value: String?): String? {
            if (value.isNullOrBlank()) return null
            val v = Template.expand(value, ctx.vars + raw)
            if (v.isBlank()) return null
            return when (key) {
                "detailUrl", "downloadUrl", "icon" -> resolveUrl(source.sourceUrl, v)
                else -> v.trim()
            }
        }

        return AppItem(
            name = finalize("name", raw["name"]).orEmpty(),
            icon = finalize("icon", raw["icon"]),
            version = finalize("version", raw["version"]),
            packageName = finalize("packageName", raw["packageName"]),
            summary = finalize("summary", raw["summary"]),
            detailUrl = finalize("detailUrl", raw["detailUrl"]),
            description = finalize("description", raw["description"]),
            downloadUrl = finalize("downloadUrl", raw["downloadUrl"]),
            downloadName = finalize("downloadName", raw["downloadName"]),
            downloadSize = finalize("downloadSize", raw["downloadSize"]),
            lastUpdate = finalize("lastUpdate", raw["lastUpdate"]),
            developer = finalize("developer", raw["developer"]),
            category = finalize("category", raw["category"]),
            vars = ctx.vars + raw
        )
    }

    /** 对提取结果做二次加工：
     *  - `regex:xxx` 管道：对原始值跑正则，有捕获组取第 1 组，否则取整个匹配（提取并丢弃 HTML 标签/前后缀）
     *  - 其它：按模板展开（{{this}} 指向原始值）
     */
    private fun applyTransform(transform: String, rawValue: String, vars: Map<String, String>): String {
        if (transform.startsWith("regex:")) {
            return try {
                val m = Regex(transform.removePrefix("regex:")).find(rawValue) ?: return ""
                if (m.groups.size > 1) m.groupValues[1] else m.value
            } catch (_: Exception) {
                ""
            }
        }
        return Template.expand(transform, vars + ("this" to rawValue))
    }

    /** 拆出 => 转换模板；
     *  `json:icon => {{sourceUrl}}/repo/{{this}}`  →  extract="json:icon", transform="{{sourceUrl}}/repo/{{this}}" */
    private fun splitTransform(rule: String): Pair<String, String?> {
        val sep = listOf("=>", "→").firstOrNull { rule.contains(it) }
        if (sep == null) return rule to null
        val parts = rule.split(sep, limit = 2)
        return parts[0].trim() to parts[1].trim().ifEmpty { null }
    }

    private fun baseVars(source: AppSource): Map<String, String> = mapOf(
        "sourceUrl" to source.sourceUrl,
        "sourceName" to source.sourceName
    )

    private fun resolveType(section: AppSection, body: String): String {
        if (section.type != "AUTO") return section.type.uppercase()
        val t = body.trimStart()
        return when (t.firstOrNull()) {
            '{', '[' -> "JSON"
            else -> "HTML"
        }
    }

    private fun parseJson(body: String): JsonElement? = try {
        Json { ignoreUnknownKeys = true }.parseToJsonElement(body)
    } catch (_: Exception) {
        null
    }

    private fun resolveUrl(base: String, url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (url.startsWith("//")) return "https:" + url
        val schemeHost = base.takeWhile { it.isLetter() || it == ':' } // 简化取协议
        val slash = base.indexOf('/') + 2
        val hostEnd = base.indexOf('/', slash).takeIf { it > 0 } ?: base.length
        val origin = base.substring(0, hostEnd)
        return when {
            url.startsWith("/") -> origin + url
            else -> base.trimEnd('/') + "/" + url
        }
    }
}
