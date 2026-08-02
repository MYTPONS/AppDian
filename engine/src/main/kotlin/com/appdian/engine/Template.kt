package com.appdian.engine

/**
 * {{变量}} 模板引擎：在 URL、请求体、提取结果中展开上下文变量。
 *
 * 变量来源：
 *  - 引擎预置：sourceUrl、sourceName
 *  - URL 展开：key（搜索关键词）
 *  - 条目上下文：已提取的字段（name、packageName、icon…）
 */
object Template {
    // 注意：Android 用 ICU 正则，结尾的 }} 必须转义为 \}\}
    private val RE = Regex("\\{\\{([^{}]+)\\}\\}")

    fun expand(tpl: String, vars: Map<String, String>): String =
        RE.replace(tpl) { m ->
            val name = m.groupValues[1].trim()
            vars[name] ?: ""
        }

    fun contains(tpl: String): Boolean = RE.containsMatchIn(tpl)

    /** 只展开模板中存在于 vars 的部分，未命中的变量保留原样（用于详情页 URL 拼接延迟解析） */
    fun expandExisting(tpl: String, vars: Map<String, String>): String {
        var out = tpl
        for ((k, v) in vars) {
            out = out.replace("{{$k}}", v)
        }
        return out
    }
}
