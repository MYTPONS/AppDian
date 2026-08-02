package com.appdian.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * ============================================================
 *  应用大典 · 规则引擎
 * ============================================================
 *
 * 规则字符串语法（可在一条规则里用 || 串联多个回退规则，取第一个非空结果）：
 *
 *  1. css 选择器
 *       css:div.app-item
 *       css:div.app-item@attr:href      ← 取元素的 href 属性
 *       css:img.cover@attr:src
 *       css:.name@text                  ← 取文本（默认）
 *       css:.desc@html                  ← 取内部 HTML
 *
 *  2. 正则提取（作用于元素文本 / JSON 字符串 / 纯文本）
 *       regex:版本[:：]\s*([0-9.]+)
 *       regex:([0-9.]+) MB@1           ← 显式指定取第 1 个捕获组
 *       无 @N 时：有捕获组取第 1 组，否则取整个匹配
 *
 *  3. JSON 路径（作用于 JSON 响应）
 *       json:$.packages[0].apkName
 *       json:name                       ← 相对路径（列表条目上下文）
 *
 *  4. 纯文本 / 模板
 *       text:固定文案
 *       {{sourceUrl}}/repo/{{icon}}     ← 无前缀时按模板展开
 *
 *  5. 属性后缀 @attr:xxx / @text / @html / @ownText / @allText / @allAttr / @N
 *
 * 类型不匹配时（例如在 HTML 上跑 json:）返回空，交由 || 的下一条规则兜底。
 */

data class RuleContext(
    /** 根 HTML 文档（列表/详情共用的根） */
    val rootHtml: Document? = null,
    /** 根 JSON 元素 */
    val rootJson: JsonElement? = null,
    /** 当前条目节点（列表解析时指向单个条目） */
    val htmlNode: Element? = null,
    val jsonNode: JsonElement? = null,
    /** 模板变量（sourceUrl、key、已提取字段等） */
    val vars: Map<String, String> = emptyMap()
) {
    fun withVar(k: String, v: String): RuleContext =
        copy(vars = vars + (k to v))
}

sealed interface RNode {
    val text: String
}

class HtmlRNode(val el: Element) : RNode {
    override val text: String get() = el.text()
}

class JsonRNode(val el: JsonElement) : RNode {
    override val text: String get() = JsonPath.primitiveString(el) ?: ""
}

class TextRNode(override val text: String) : RNode

/** 单个规则字符串的解析结果 */
data class ParsedRule(
    val prefix: String?,      // css / json / regex / text，null=纯模板
    val body: String,
    val suffix: String        // attr:xxx / text / html / 数字 / ""
)

class RuleEngine {

    fun parseRule(rule: String): ParsedRule {
        val r = rule.trim()
        // 提取 @后缀（仅在结尾，且符合已知形式）
        val atIdx = r.lastIndexOf('@')
        var body = r
        var suffix = ""
        if (atIdx > 0) {
            val candidate = r.substring(atIdx + 1)
            if (SUFFIX_PATTERN.matches(candidate)) {
                body = r.substring(0, atIdx)
                suffix = candidate
            }
        }
        // 提取前缀
        var prefix: String? = null
        val m = PREFIX_PATTERN.find(body)
        if (m != null && m.groupValues[1] in KNOWN_PREFIXES) {
            prefix = m.groupValues[1]
            body = body.substring(prefix.length + 1)
        }
        return ParsedRule(prefix, body, suffix)
    }

    /** 求单值：返回第一个非空结果。keepUnknown=true 时模板里未命中的 {{变量}} 保留原样 */
    fun evalString(rule: String, ctx: RuleContext, keepUnknown: Boolean = false): String {
        for (part in rule.split("||")) {
            val v = evalStringPart(part.trim(), ctx, keepUnknown)
            if (v.isNotBlank()) return v
        }
        return ""
    }

    /** 求节点列表（用于 listRule） */
    fun evalNodes(rule: String, ctx: RuleContext): List<RNode> {
        for (part in rule.split("||")) {
            val nodes = evalNodesPart(part.trim(), ctx)
            if (nodes.isNotEmpty()) return nodes
        }
        return emptyList()
    }

    // ---------------- 内部实现 ----------------

    private fun evalStringPart(rule: String, ctx: RuleContext, keepUnknown: Boolean): String {
        if (rule.isEmpty()) return ""
        val pr = parseRule(rule)
        val base = when (pr.prefix) {
            null -> {
                // 纯模板 / 纯文本
                val expanded = if (keepUnknown) Template.expandExisting(pr.body, ctx.vars)
                               else Template.expand(pr.body, ctx.vars)
                if (expanded.isNotBlank()) return expanded else return ""
            }
            "text" -> {
                val expanded = Template.expand(pr.body, ctx.vars)
                return render(pr.suffix, TextRNode(expanded))
            }
            "css" -> {
                val els = evalCss(pr.body, ctx)
                if (els.isEmpty()) return ""
                val sb = StringBuilder()
                for ((i, el) in els.withIndex()) {
                    if (i > 0) sb.append('\n')
                    sb.append(render(pr.suffix, HtmlRNode(el)))
                }
                sb.toString()
            }
            "regex" -> {
                val source = when {
                    ctx.htmlNode != null -> ctx.htmlNode.text()
                    ctx.jsonNode != null -> JsonPath.primitiveString(ctx.jsonNode) ?: ""
                    ctx.rootHtml != null -> ctx.rootHtml.text()
                    ctx.rootJson != null -> JsonPath.primitiveString(ctx.rootJson) ?: ""
                    else -> ""
                }
                try {
                    val re = Regex(pr.body)
                    val match = re.find(source) ?: return ""
                    val groupIdx = pr.suffix.toIntOrNull() ?: defaultGroup(re, match)
                    match.groupValues.getOrNull(groupIdx) ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
            "json" -> {
                val target = ctx.jsonNode ?: ctx.rootJson ?: return ""
                val rel = if (pr.body.startsWith("$")) pr.body else "$." + pr.body.trimStart('.')
                val v = JsonPath.queryString(target, rel) ?: return ""
                val expanded = Template.expand(v, ctx.vars)
                expanded
            }
            else -> "" // xpath 等预留
        }
        return base
    }

    private fun evalNodesPart(rule: String, ctx: RuleContext): List<RNode> {
        if (rule.isEmpty()) return emptyList()
        val pr = parseRule(rule)
        return when (pr.prefix) {
            "css" -> evalCss(pr.body, ctx).map { HtmlRNode(it) }
            "json" -> {
                val target = ctx.jsonNode ?: ctx.rootJson ?: return emptyList()
                val rel = if (pr.body.startsWith("$")) pr.body else "$." + pr.body.trimStart('.')
                // 数组结果摊平为元素（listRule 语义：每个元素是一个条目）
                JsonPath.query(target, rel).flatMap { el ->
                    if (el is JsonArray) el.toList() else listOf(el)
                }.map { JsonRNode(it) }
            }
            null -> {
                // 模板生成多个? 不支持，返回单文本节点
                val expanded = Template.expand(pr.body, ctx.vars)
                if (expanded.isNotBlank()) listOf(TextRNode(expanded)) else emptyList()
            }
            else -> emptyList()
        }
    }

    private fun evalCss(sel: String, ctx: RuleContext): Elements {
        val root = ctx.htmlNode ?: ctx.rootHtml ?: return Elements()
        return try {
            root.select(sel)
        } catch (_: Exception) {
            Elements()
        }
    }

    private fun render(suffix: String, node: RNode): String {
        return when {
            suffix.startsWith("attr:") -> {
                val attr = suffix.removePrefix("attr:")
                (node as? HtmlRNode)?.el?.attr(attr) ?: ""
            }
            suffix == "html" -> (node as? HtmlRNode)?.el?.html() ?: ""
            suffix == "ownText" -> (node as? HtmlRNode)?.el?.ownText() ?: ""
            suffix == "allText" -> (node as? HtmlRNode)?.el?.text() ?: ""
            suffix == "allAttr" -> {
                val el = (node as? HtmlRNode)?.el ?: return ""
                el.attributes().joinToString(";") { "${it.key}=${it.value}" }
            }
            suffix == "text" || suffix.isEmpty() -> node.text
            suffix.toIntOrNull() != null -> "" // 数字后缀只对 regex 有意义
            else -> node.text
        }
    }

    private fun defaultGroup(re: Regex, match: MatchResult): Int {
        return if (match.groups.size > 1) 1 else 0
    }

    companion object {
        private val SUFFIX_PATTERN = Regex("^(attr:[A-Za-z0-9_:-]+|text|html|ownText|allText|allAttr|\\d+)$")
        private val PREFIX_PATTERN = Regex("^([a-zA-Z]+):")
        private val KNOWN_PREFIXES = setOf("css", "json", "regex", "text")
    }
}
