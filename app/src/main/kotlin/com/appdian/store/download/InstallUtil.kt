package com.appdian.store.download

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 安装工具：把已下载完成的安装包交给系统安装器。
 *
 * Android 10+ 的文件存在 MediaStore（分区存储），系统安装器直接读
 * MediaStore Downloads 的 content uri 在部分机型（尤其 Android 14/15）
 * 会报 uid 无法读取的错误，因此统一策略：
 *  - 有真实文件路径（Android 9- 保存到外部 Download 目录）：FileProvider 直接暴露
 *  - MediaStore 场景：先把文件复制到本应用 cacheDir，再用 FileProvider 暴露，
 *    100% 保证安装器可读。
 */
object InstallUtil {

    private const val MIME = "application/vnd.android.package-archive"

    /** 调起系统安装器；找不到能处理的组件会抛异常，调用方自行兜底 */
    fun installApk(context: Context, localPath: String) {
        val uri = uriOf(context, localPath)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 部分系统必须显式 clipData 才向安装器授读权限
            clipData = ClipData.newRawUri("apk", uri)
        }
        context.startActivity(intent)
    }

    /** 本地路径 → 可被系统安装器读取的 content uri */
    fun uriOf(context: Context, localPath: String): Uri {
        val f = File(localPath)
        if (localPath.startsWith("/") && f.exists()) {
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }
        // MediaStore 保存的文件：localPath 存的是展示文件名
        return copyToCache(context, localPath.substringAfterLast('/'))
    }

    /**
     * 把 MediaStore 里的 APK 复制到 cacheDir/install/ 并返回 FileProvider uri。
     * 若原文件已不存在（被用户删除），返回一个不存在的 uri，安装器会报错而非崩溃。
     */
    private fun copyToCache(context: Context, displayName: String): Uri {
        val dir = File(context.cacheDir, "install").apply { mkdirs() }
        val dest = File(dir, displayName)
        val src = resolveMediaStorePath(context, displayName)
        if (src != null && src.exists() && (!dest.exists() || dest.length() != src.length())) {
            src.copyTo(dest, overwrite = true)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
    }

    /** 通过 MediaStore 查询 DISPLAY_NAME 对应的真实路径（Android 10+ DATA 列仍可读） */
    private fun resolveMediaStorePath(context: Context, displayName: String): File? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DATA),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(displayName),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val data = c.getString(0)
                if (!data.isNullOrBlank()) return File(data)
            }
        }
        return null
    }

    /** 是否还支持安装：任务已完成 */
    fun canInstall(localPath: String?): Boolean = !localPath.isNullOrBlank()
}
