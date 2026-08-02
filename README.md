# 应用大典（AppDian）

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-SDK%2035-green.svg)](https://developer.android.com/)

仿照 **legado（阅读）书源机制** 的 Android 应用软件库壳子。
不再依赖任何固定的应用商店：**应用源（AppSource）** 是一份 JSON 规则文件，
描述如何从任意网站解析应用列表、详情和下载地址 —— 用户自由导入/分享源，
绕开搜索引擎里铺天盖地的病毒/伪站，直达可信来源。

> 已交付：核心规则引擎 + 源管理 + 发现/搜索/详情 + 分类 + **内置下载管理器**（OkHttp 前台服务，失败自动换源/换链接）+ 数据复用缓存。
> 99 个单元测试全绿（`./gradlew :engine:test :app:testDebugUnitTest`）。

## 架构

```
:engine  纯 Kotlin 规则引擎（无 Android 依赖，JVM 可测）
         ├─ model/AppSource.kt   源数据模型（JSON 序列化）
         ├─ RuleEngine.kt        规则求值：css / json / regex / text / || 回退 / @属性后缀
         ├─ JsonPath.kt          轻量 JSONPath（$ .key [idx] [*]）
         ├─ Template.kt          {{变量}} 模板引擎
         └─ SourceParser.kt      区块解析：HTML/JSON 自动探测 → 条目字段提取 → URL 补全
:app     Android 应用（Jetpack Compose + Material3 + MVVM + OkHttp + Coil）
         ├─ data/SourceRepository   源以 JSON 文件存放 filesDir/sources/，天然支持导入导出
         ├─ data/StoreRepository    业务编排：发现 / 搜索 / 详情
         ├─ download/DownloadService  下载服务（前台服务 + 通知进度 + 归档公共下载目录）
         └─ ui/                     发现 · 搜索 · 下载 · 详情 · 源管理
```

## 快速开始

```bash
# 环境：JDK 17 + Android SDK（35）
export JAVA_HOME=~/devtools/jdk17
export ANDROID_HOME=~/devtools/android-sdk
./gradlew :engine:test :app:testDebugUnitTest   # 99 个测试
./gradlew :app:assembleDebug                    # debug APK
./gradlew :app:assembleRelease                  # release 签名 APK（keystore/appdian.jks，凭据可用环境变量 APPDIAN_STORE_PASSWORD 覆盖）
adb install app/build/outputs/apk/release/app-release.apk
```

> 注：`keystore/`（签名私钥）与 `local.properties` 已加入 .gitignore，不会提交到仓库。
> 发布正式版请替换为自己的签名文件，凭据可通过环境变量 `APPDIAN_STORE_PASSWORD` / `APPDIAN_KEY_ALIAS` / `APPDIAN_KEY_PASSWORD` 注入。

首次启动会自动导入 4 个内置演示源：

| 源 | 演示能力 |
|---|---|
| F-Droid | JSON 源：搜索 / 详情 / 发现（官方 API，全部开源软件，最安全） |
| GitHub Releases | JSON 源：搜索（topic:android）/ 详情（release API）/ 发现（NetGuard、Termux 真实 APK） |
| GitHub Web 示例 | HTML 源：`css:` 规则抓网页（示例性质，页面结构变动可能失效） |
| 华军软件园 | HTML 源（真实反爬实战）：搜索 + 详情 + 发现，`headers` 带 Referer 过反爬，`=> regex:` 管道从 onclick 抠 APK 直链 / `iopdfbhjl` 跳转链接 |


## 接入方式：应用源

规则语法速览（详见 [`docs/应用源格式.md`](docs/应用源格式.md)）：

- `css:div.app-item@attr:href` — CSS 选择器 + 属性
- `json:$.packages[0].apkName` / `json:name`（条目内相对路径）
- `regex:版本[:：]\s*([0-9.]+)` — 正则（默认取第 1 捕获组，`@2` 指定组号）
- `规则A || 规则B` — 第一个非空结果生效
- `{{key}} {{sourceUrl}} {{packageName}}` — 模板变量
- `json:icon => {{sourceUrl}}/repo/{{this}}` — 提取结果转换模板
- `extras: { "full_name": "json:full_name" }` — 任意中间变量

## 目录

```
├── app/                     Android 应用（Compose UI + 数据层 + 下载服务）
│   └── src/main/assets/app_sources/   内置演示源
├── engine/                  规则引擎（纯 Kotlin，无 Android 依赖）
├── docs/应用源格式.md        应用源完整规范
└── artifacts/screenshots/   模拟器实拍截图
```

## 开源协议

[MIT License](LICENSE) — 自由使用 / 修改 / 分发，注明出处即可。

## 下载管理

详情页点「下载 APK」即开始下载（OkHttp 前台服务）：

- 通知栏实时进度（可取消）
- 「下载」tab 查看任务：进度 / 取消 / 失败重试 / 删除记录
- 完成后自动归档到公共目录 `Download/应用大典/`，通知栏点击可直接安装
- 真实文件名优先（华军直链 `com.tencent.mm_8.0.76.apk`），否则用应用名

## 后续计划

- [ ] 下载断点续传（Range 续传）
- [ ] 源分享生态（源市场/二维码导入）、规则调试台
- [ ] xpath 规则支持、正则多行模式、请求 Cookie 管理
