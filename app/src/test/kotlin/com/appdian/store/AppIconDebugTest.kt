package com.appdian.store

import com.appdian.engine.SourceParser
import com.appdian.engine.model.AppSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/** 临时调试：解析真实华军页面看 icon 提取结果 */
class AppIconDebugTest {
    @Test
    fun debugHuajunIcons() = runBlocking {
        val srcJson = File("/home/ama/Projects/应用大典/app/src/main/assets/app_sources/huajun.json").readText()
        val source = Json { ignoreUnknownKeys = true }.decodeFromString<AppSource>(srcJson)
        val html = File("/tmp/hj.html").readText()
        val parser = SourceParser()
        source.discovery.forEach { sec ->
            val items = parser.parseList(html, sec.section, source)
            println("== 栏目「${sec.title}」共 ${items.size} 条 ==")
            items.take(5).forEach { it ->
                println("   name=${it.name} | icon=${it.icon?.take(80)} | detailUrl=${it.detailUrl?.take(60)} | ver=${it.version}")
            }
        }
    }
}
