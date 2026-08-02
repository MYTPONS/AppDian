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
    // 注意：不用 /releases/latest，那个端点只返回【最新非 prerelease】发布，
    // 而本项目的测试版发布都被标记为 prerelease，会导致永远查不到更新。
    // 改为拉取 /releases 列表（含 prerelease），自己取最新一条。
    private val apiUrl: String = "$GITHUB_REPO_API/releases?per_page=3"
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
                        parseReleases(body)
                    }
                }
            }
        }
    }

    /**
     * 解析 GitHub /releases 列表 JSON（数组，按发布时间降序）。
     * 取第一个带 APK asset 的发布（即最新一个含 APK 的版本，不论是否 prerelease），
     * 找不到返回 null。
     */
    internal fun parseReleases(json: String): UpdateInfo? {
        val list = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<ReleaseJson>>(json)
        }.getOrNull() ?: return null
        for (release in list) {
            val apk = release.assets
                .firstOrNull { it.name.lowercase().endsWith(".apk") }
                ?.browser_download_url?.takeIf { it.isNotBlank() }
                ?: continue
            return UpdateInfo(
                version = release.tag_name,
                apkUrl = apk,
                notes = release.body.orEmpty(),
                publishedAt = release.published_at.orEmpty()
            )
        }
        return null
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
