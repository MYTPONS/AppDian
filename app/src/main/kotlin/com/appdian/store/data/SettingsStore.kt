package com.appdian.store.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局设置（SharedPreferences）：
 * 目前只有「默认 User-Agent」——源没有自带 UA 时所有请求（抓取 + 下载）用它。
 * 用户把 UA 当作"代理"来配置，这里提供统一的全局默认值。
 */
class SettingsStore(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("appdian_settings", Context.MODE_PRIVATE)

    /** 全局默认 User-Agent（源级 UA 优先于它） */
    var defaultUserAgent: String
        get() = sp.getString(KEY_UA, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_UA
        set(value) = sp.edit().putString(KEY_UA, value.trim()).apply()

    companion object {
        private const val KEY_UA = "default_user_agent"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** 从任意 Context 读当前全局 UA（Service/协程里也方便用） */
        fun currentUserAgent(context: Context): String =
            context.getSharedPreferences("appdian_settings", Context.MODE_PRIVATE)
                .getString(KEY_UA, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_UA
    }
}
