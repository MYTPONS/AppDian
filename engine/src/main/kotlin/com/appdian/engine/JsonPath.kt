package com.appdian.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 自研轻量 JSONPath 子集，用于 JSON 类型应用源的字段提取。
 *
 * 支持的语法：
 *  - $           根节点
 *  - a.b         键访问（相对路径省略开头的 $.）
 *  - [0]         数组下标
 *  - [*]         数组全部元素
 *  - $.a[0].b    绝对路径
 *  - a[*].b      相对路径
 */
object JsonPath {

    private sealed interface Seg {
        data class Key(val k: String) : Seg
        data class Idx(val i: Int) : Seg
        data class All(val key: String?) : Seg
    }

    private fun tokenize(path: String): List<Seg> {
        val segs = mutableListOf<Seg>()
        var i = 0
        var pendingKey = StringBuilder()
        val p = path.removePrefix("$")

        fun flush() {
            if (pendingKey.isNotEmpty()) {
                segs.add(Seg.Key(pendingKey.toString()))
                pendingKey = StringBuilder()
            }
        }

        while (i < p.length) {
            val c = p[i]
            when {
                c == '.' -> {
                    flush(); i++
                }
                c == '[' -> {
                    flush()
                    val end = p.indexOf(']', i)
                    if (end < 0) throw IllegalArgumentException("JSONPath 缺少 ]: $path")
                    val inner = p.substring(i + 1, end).trim()
                    when {
                        inner == "*" -> segs.add(Seg.All(null))
                        inner.startsWith("*.") -> segs.add(Seg.All(inner.removePrefix("*.")))
                        else -> {
                            val idx = inner.toIntOrNull()
                                ?: throw IllegalArgumentException("JSONPath 下标非法: [$inner] in $path")
                            segs.add(Seg.Idx(idx))
                        }
                    }
                    i = end + 1
                }
                else -> {
                    pendingKey.append(c); i++
                }
            }
        }
        flush()
        return segs
    }

    /** 对根节点求绝对路径；[path] 以 $ 开头则为绝对路径，否则为相对路径 */
    fun query(root: JsonElement, path: String): List<JsonElement> {
        val isAbsolute = path.trimStart().startsWith("$")
        return if (isAbsolute) {
            query(root, tokenize(path), listOf(root))
        } else {
            query(root, tokenize(path), listOf(root)) // 相对路径由调用方把当前节点当作 root 传入
        }
    }

    private fun query(root: JsonElement, segs: List<Seg>, current: List<JsonElement>): List<JsonElement> {
        if (segs.isEmpty()) return current
        val head = segs.first()
        val rest = segs.drop(1)
        val next = when (head) {
            is Seg.Key -> current.flatMap { el ->
                (el as? JsonObject)?.get(head.k)?.let { listOf(it) } ?: emptyList()
            }
            is Seg.Idx -> current.flatMap { el ->
                (el as? JsonArray)?.getOrNull(head.i)?.let { listOf(it) } ?: emptyList()
            }
            is Seg.All -> current.flatMap { el ->
                when (el) {
                    is JsonArray -> el
                    is JsonObject -> {
                        val k = head.key
                        if (k != null) el[k]?.let { listOf(it) } ?: emptyList()
                        else el.values
                    }
                    else -> emptyList()
                }
            }
        }
        return query(root, rest, next)
    }

    /** 取查询结果中第一个字符串值（对象/数组会尝试取单一键的值） */
    fun queryString(root: JsonElement, path: String): String? {
        val results = query(root, path)
        if (results.isEmpty()) return null
        return results.firstOrNull { it is JsonPrimitive }?.let { primitiveString(it) }
            ?: results.firstOrNull()?.let { flattenString(it) }
    }

    fun primitiveString(el: JsonElement): String? = (el as? JsonPrimitive)?.let {
        it.contentOrNull
            ?: it.intOrNull?.toString()
            ?: it.toString().removeSurrounding("\"")
    }

    /** 对象取第一个字符串字段；数组取第一个元素，递归摊平 */
    private fun flattenString(el: JsonElement): String? = when (el) {
        is JsonPrimitive -> primitiveString(el)
        is JsonArray -> el.firstOrNull()?.let { flattenString(it) }
        is JsonObject -> el.values.firstNotNullOfOrNull { flattenString(it) }
    }
}
