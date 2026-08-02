# 应用大典（AppDian）

> 像「阅读」管理书源一样，用一份 JSON 规则管理你的应用来源。

搜索"微信下载"的时候，前面几页往往全是广告和伪站。应用大典换了个思路：
**应用源（AppSource）** 是一份 JSON 规则文件，告诉 App 去哪个网站搜索、列表页和详情页怎么解析、下载地址从哪取。

用户可以自由导入 / 分享 / 维护这些源，绕过搜索引擎，直达可信来源——原理和 legado（阅读）的书源机制一致，只是把领域从**书**换成了**应用**。

## 特性

- **规则驱动**：`css` / `json` / `regex` 三种规则 + `||` 回退 + `{{模板}}` 变量，一份 JSON 同时描述列表、详情、下载地址
- **多源聚合**：发现、搜索跨源并发执行，结果逐个上屏，慢源不拖累快源
- **智能分类**：关键词自动归类 + 手动 / 批量归类，跨源统一分类视图
- **下载管理**：前台服务下载、通知栏进度、失败自动换源 / 换链接、完成后一键安装
- **数据复用**：LRU 缓存池 + 下载中数据保护，重复浏览不重复抓取
- **纯 Kotlin 规则引擎**：引擎无 Android 依赖，JVM 上可独立测试（107 个单测全绿）

## 内置演示源

| 源 | 说明 |
|---|---|
| F-Droid | 官方 API，全部开源软件，最安全 |
| GitHub Releases | NetGuard / Termux 等真实 APK |
| 华军软件园 | 真实反爬实战：Referer 过校验 + 规则管道提取直链 |
| GitHub Web 示例 | `css:` 规则抓网页的入门示例 |

## 快速开始

```bash
# 环境：JDK 17 + Android SDK（35）
export JAVA_HOME=~/devtools/jdk17
export ANDROID_HOME=~/devtools/android-sdk
./gradlew :engine:test :app:testDebugUnitTest   # 107 个测试
./gradlew :app:assembleDebug                    # debug APK
./gradlew :app:assembleRelease                  # release 签名 APK
adb install app/build/outputs/apk/release/app-release.apk
```

> 签名：release 使用 `keystore/appdian.jks`（已 gitignore，不提交）；凭据默认本地值，可通过环境变量 `APPDIAN_STORE_PASSWORD` / `APPDIAN_KEY_ALIAS` / `APPDIAN_KEY_PASSWORD` 覆盖。正式发布请换成自己的签名。

## 应用源语法速览

详见 [`docs/应用源格式.md`](docs/应用源格式.md)：

- `css:div.app-item@attr:href` — CSS 选择器 + 属性后缀
- `json:$.packages[0].apkName` / `json:name` — JSONPath（条目内相对路径）
- `regex:版本[:：]\s*([0-9.]+)` — 正则（默认取第 1 捕获组，`@2` 指定组号）
- `规则A || 规则B` — 第一个非空结果生效
- `{{key}} {{sourceUrl}} {{packageName}}` — 模板变量
- `json:icon => {{sourceUrl}}/repo/{{this}}` — 提取结果二次转换
- `extras: { "full_name": "json:full_name" }` — 任意中间变量

## 项目结构

```
:engine  规则引擎（纯 Kotlin，无 Android 依赖）
         ├─ model/AppSource.kt   源数据模型（JSON 序列化）
         ├─ RuleEngine.kt        规则求值：css / json / regex / text / || 回退
         ├─ JsonPath.kt          轻量 JSONPath（$ .key [idx] [*]）
         ├─ Template.kt          {{变量}} 模板引擎
         └─ SourceParser.kt      HTML/JSON 自动探测 → 字段提取 → URL 补全
:app     Android 应用（Jetpack Compose + Material3 + MVVM + OkHttp + Coil）
         ├─ data/SourceRepository   源以 JSON 存放 filesDir/sources/，天然支持导入导出
         ├─ data/StoreRepository    发现 / 搜索 / 详情编排 + 缓存
         ├─ download/DownloadService  前台服务下载，归档公共下载目录
         └─ ui/                     发现 · 搜索 · 分类 · 下载 · 设置
```

## 目录

```
├── app/                     Android 应用
│   └── src/main/assets/app_sources/   内置演示源
├── engine/                  规则引擎（纯 Kotlin）
├── docs/应用源格式.md        应用源完整规范
└── artifacts/screenshots/   模拟器实拍截图
```

## 开源协议

[MIT License](LICENSE) — 自由使用 / 修改 / 分发，注明出处即可。

## 后续计划

- [ ] 下载断点续传（Range 续传）
- [ ] 源分享生态（源市场 / 二维码导入 / 规则调试台）
- [ ] xpath 规则支持、正则多行模式、请求 Cookie 管理
