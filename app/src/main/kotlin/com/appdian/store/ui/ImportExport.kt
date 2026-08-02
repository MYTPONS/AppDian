package com.appdian.store.ui

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * legado 式导入导出工具：
 * - 从本地文件（SAF Uri）读取文本
 * - 写入本地文件（SAF Uri）
 * - 从网络 URL 拉取文本（导入配置/源）
 */
object ImportExport {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /** 读取 SAF 文件内容；失败返回 null */
    fun readUriText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { ins ->
            ins.bufferedReader().use { it.readText() }
        }
    }.getOrNull()

    /** 写入 SAF 文件；失败返回 false */
    fun writeUriText(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        true
    }.getOrDefault(false)

    /** 从网络 URL 拉取文本；失败抛异常（带错误信息） */
    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IllegalStateException("响应为空")
        }
    }
}
