package com.appdian.store.data

import com.appdian.store.ui.GITHUB_REPO_API
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 检查到的更新信息 */
@Serializable
data class UpdateInfo(
    /** 新版本号（来自 release tag_name，如 v0.2.0） */
    val version: String,
    /** 最新版 APK 下载地址 */
    val apkUrl: String,
    /** 发布说明 */
    val notes: String,
    /** 发布时间 */
    val publishedAt: String
)

/**
 * 从 GitHub 仓库检查新版本：
 * 请求仓库的 latest release，解析出版本号 / APK 下载地址 / 发布说明。
 *
 * 返回语义：
 * - Result.success(info)  有最新发布且解析出 APK
 * - Result.success(null)  仓库没有任何 release（HTTP 404）
 * - Result.failure(e)     网络/解析失败
 */
class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val apiUrl: String = "$GITHUB_REPO_API/releases/latest"
) {

    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> null   // 仓库还没有 release
                    !resp.isSuccessful -> throw IOException("HTTP ${resp.code}")
                    else -> {
                        val body = resp.body?.string() ?: return@use null
                        parseRelease(body)
                    }
                }
            }
        }
    }

    /** 解析 GitHub latest release JSON；找不到 APK asset 返回 null */
    internal fun parseRelease(json: String): UpdateInfo? {
        val release = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<ReleaseJson>(json)
        }.getOrNull() ?: return null
        val apk = release.assets
            .firstOrNull { it.name.lowercase().endsWith(".apk") }
            ?.browser_download_url?.takeIf { it.isNotBlank() }
            ?: return null
        return UpdateInfo(
            version = release.tag_name,
            apkUrl = apk,
            notes = release.body.orEmpty(),
            publishedAt = release.published_at.orEmpty()
        )
    }

    companion object {
        /** 最新版本比当前版本新？（用 VersionSort 数字段比较，v 前缀忽略） */
        fun isNewer(latest: String?, current: String?): Boolean =
            VersionSort.compare(latest, current) > 0
    }
}

@Serializable
private data class ReleaseJson(
    val tag_name: String = "",
    val body: String? = null,
    val published_at: String? = null,
    val assets: List<AssetJson> = emptyList()
)

@Serializable
private data class AssetJson(
    val name: String = "",
    val browser_download_url: String = ""
)
