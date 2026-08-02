package com.appdian.engine.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 应用源：仿 legado 书源设计，描述"如何从一个网站获取应用列表/详情"。
 * 以 JSON 形式分发，用户可自由编辑、导入、导出、分享。
 */
@Serializable
data class AppSource(
    /** 源名称（唯一标识） */
    val sourceName: String,
    /** 源根地址，用于拼接相对 URL */
    val sourceUrl: String,
    /** 源版本号，用于识别源更新 */
    val sourceVersion: String = "1.0.0",
    /** 源图标（可选） */
    val sourceIcon: String? = null,
    /** 请求时使用的 User-Agent，缺省用内置默认 UA */
    val userAgent: String? = null,
    /** 请求附加请求头 */
    val headers: Map<String, String> = emptyMap(),
    /** 搜索规则 */
    val search: AppSection? = null,
    /** 详情规则 */
    val detail: AppSection? = null,
    /** 发现（首页各栏目）规则 */
    val discovery: List<DiscoverySection> = emptyList(),
    /** 是否启用（运行时状态，导出时会剔除） */
    val enabled: Boolean = true
)

/** 一个发现栏目：标题 + 列表抓取规则 */
@Serializable
data class DiscoverySection(
    val title: String,
    val section: AppSection,
    /**
     * 栏目声明的分类：该栏目抓到的应用归入哪个统一分类。
     * 填分类 id 或分类名称（如 "tools" / "系统工具"），不填则走动态提取/关键词映射。
     */
    val category: String? = null
)

/**
 * 一个抓取区块：决定请求哪个 URL、如何解析列表/条目。
 * [url] 支持 {{key}}、{{detailUrl}} 等模板变量。
 */
@Serializable
data class AppSection(
    /** 请求地址模板（相对地址自动拼接 sourceUrl） */
    val url: String,
    /** GET / POST */
    val method: String = "GET",
    /** POST 请求体模板 */
    val body: String? = null,
    /** 本节额外请求头 */
    val headers: Map<String, String> = emptyMap(),
    /** 响应类型：AUTO 自动探测 / HTML / JSON */
    val type: String = "AUTO",
    /**
     * 列表容器规则：从整个文档中挑出所有"条目"节点。
     *  HTML: css:div.app-item
     *  JSON: json:$.packages
     */
    val listRule: String? = null,
    /** 条目的字段提取规则 */
    val itemRules: ItemRules
)

/** 条目字段提取规则集合。每条规则都支持「||」多规则回退。 */
@Serializable
data class ItemRules(
    val name: String? = null,
    val icon: String? = null,
    val version: String? = null,
    val packageName: String? = null,
    val summary: String? = null,
    /** 详情页地址模板（可引用 {{packageName}} 等已提取字段） */
    val detailUrl: String? = null,
    val description: String? = null,
    val downloadUrl: String? = null,
    val downloadName: String? = null,
    val downloadSize: String? = null,
    val lastUpdate: String? = null,
    val developer: String? = null,
    /** 分类字段提取规则（如 F-Droid 的 category、GitHub 的 topics），值会参与关键词映射 */
    val category: String? = null,
    /** 额外中间变量：任意 {{var}} 规则，供其它字段/详情 URL 模板引用 */
    val extras: Map<String, String> = emptyMap()
)

/** 引擎解析出的统一应用条目 */
data class AppItem(
    val name: String,
    val icon: String? = null,
    val version: String? = null,
    val packageName: String? = null,
    val summary: String? = null,
    val detailUrl: String? = null,
    val description: String? = null,
    val downloadUrl: String? = null,
    val downloadName: String? = null,
    val downloadSize: String? = null,
    val lastUpdate: String? = null,
    val developer: String? = null,
    /** 条目提取出的原始分类（如 F-Droid 的 category 字段原文），参与关键词映射 */
    val category: String? = null,
    /** 条目提取过程中的全部变量（含 extras），详情页 URL 模板可继续引用 */
    val vars: Map<String, String> = emptyMap()
)

object Sources {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
