package com.appdian.store.net

import com.appdian.engine.model.AppSection
import com.appdian.engine.model.AppSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 统一网络层：按源的 userAgent / headers 发起请求，返回响应文本。
 * [defaultUa] 是懒加载的全局默认 UA（源没配 UA 时使用，可在设置里改）。
 */
class HttpFetcher(
    private val defaultUa: () -> String = { DEFAULT_UA }
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetch(url: String, source: AppSource, section: AppSection): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", (source.userAgent ?: defaultUa()).asciiSafe())
                .header("Accept", "application/json, text/html, */*")

            source.headers.forEach { (k, v) -> builder.header(k, v) }
            section.headers.forEach { (k, v) -> builder.header(k, v) }

            val request = when (section.method.uppercase()) {
                "POST" -> builder.post(
                    (section.body ?: "").toRequestBody("application/x-www-form-urlencoded".toMediaType())
                ).build()
                else -> builder.get().build()
            }

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body?.string() ?: throw IOException("响应为空")
            }
        }

    companion object {
        /** OkHttp 只接受 US-ASCII 头；UA 里出现非 ASCII 字符时丢弃（如中文描述） */
        private fun String.asciiSafe(): String =
            if (all { it.code in 0x20..0x7E }) this else DEFAULT_UA

        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
