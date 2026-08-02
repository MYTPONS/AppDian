package com.appdian.store.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 统一分类体系：与应用源独立，存本地 categories.json，用户可自由增删改。
 * 分类是「内容运营」概念——数据源来自外部网站，各家分类名五花八门，
 * 所以统一分类由本机定义，数据源只负责通过多种途径「供稿」。
 */
@Serializable
data class Category(
    /** 稳定 id（英文小写），引用分类时用它 */
    val id: String,
    /** 展示名称 */
    val name: String,
    /**
     * 关键词表：用于把条目提取的原始分类 / 名称摘要映射到本分类。
     * 匹配时忽略大小写做子串匹配，中英文均可用。
     */
    val keywords: List<String> = emptyList()
)

/** 内置默认分类（首次启动 seed，用户可改） */
val DEFAULT_CATEGORIES: List<Category> = listOf(
    Category("media", "影音播放", listOf(
        "media", "multimedia", "video", "music", "audio", "tv", "player",
        "影音", "播放", "视频", "音乐", "铃声", "影视", "直播", "追剧",
        "抖音", "快手", "哔哩哔哩", "bilibili", "优酷", "爱奇艺", "腾讯视频", "芒果tv", "网易云音乐", "qq音乐", "酷狗", "酷我", "全民k歌", "喜马拉雅", "蜻蜓fm", "荔枝", "猫耳fm", "剪映", "快影", "必剪", "potplayer", "vlc", "mxplayer", "暴风影音", "迅雷看看", "电视", "电台", "听书", "小说朗读"
    )),
    Category("social", "社交通讯", listOf(
        "social", "chat", "messag", "communication", "telegram", "whatsapp", "wechat", "qq", "discord",
        "电话", "短信", "通讯", "社交", "聊天", "论坛", "交友",
        "微信", "qq", "微博", "知乎", "贴吧", "小红书", "豆瓣", "陌陌", "soul", "探探", "钉钉", "企业微信", "飞书", "slack", "telegram", "signal", "line", "viber", "skype", "zoom", "腾讯会议", "钉钉会议"
    )),
    Category("tools", "系统工具", listOf(
        "system", "tool", "utility", "launcher", "widget", "clean", "monitor", "file", "压缩", "工具", "系统", "清理", "美化", "壁纸", "桌面", "文件",
        "夸克", "uc浏览器", "qq浏览器", "360浏览器", "edge", "chrome", "firefox", "via", "alook", "搜狗输入法", "百度输入法", "讯飞输入法", "搜狗", "es文件", "mt管理器", "x-plore", "rar", "zip", "7zip", "备份", "录屏", "截屏", "字体", "主题", "电池", "闹钟", "手电筒"
    )),
    Category("dev", "开发与网络", listOf(
        "development", "programming", "code", "ssh", "terminal", "termux", "server", "git", "compiler", "ide", "network", "internet", "connectivity", "vpn", "proxy", "dns", "wifi", "浏览器", "开发", "编程", "代码", "网络", "下载", "服务器", "终端",
        "termux", "juice ssh", "vscode", "android studio", "aosp", "adb", "python", "nodejs", "nginx", "wordpress", "hugo", "nextcloud", "syncthing", "frp", "tailscale", "zerotier", "clash", "v2ray", "shadowrocket", "surfboard", "idm", "aria2", "迅雷", "百度网盘", "阿里云盘", "夸克网盘", "115", "天翼云盘", "蓝奏云", "奶牛快传"
    )),
    Category("office", "办公效率", listOf(
        "office", "document", "pdf", "note", "calendar", "keyboard", "calculator", "办公", "文档", "笔记", "日历", "键盘", "计算器", "效率", "输入法",
        "wps", "office", "word", "excel", "ppt", "pdf阅读", "福昕", "adobe", "有道", "讯飞听见", "录音", "语音转文字", "扫描", "camscanner", "全能扫描", "印象笔记", "notion", "flomo", "幕布", "xmind", "思维导图", "todo", "番茄钟", "随手记", "记账"
    )),
    Category("edu", "学习教育", listOf(
        "education", "learn", "read", "book", "reading", "dictionary", "language", "翻译", "词典", "字典", "汉字", "学习", "教育", "阅读", "看书", "英语", "单词", "课程",
        "新华字典", "成语词典", "百度翻译", "谷歌翻译", "deepl", "扇贝", "百词斩", "不背单词", "墨墨", "多邻国", "duolingo", "作业帮", "猿辅导", "学而思", "网易公开课", "中国大学mooc", "学堂在线", "知乎", "维基", "百度百科", "古诗文", "唐诗宋词"
    )),
    Category("game", "游戏娱乐", listOf(
        "game", "games", "arcade", "puzzle", "娱乐", "游戏", "休闲",
        "王者荣耀", "和平精英", "原神", "崩坏", "米哈游", "明日方舟", "部落冲突", "皇室战争", "我的世界", "minecraft", "荒野乱斗", "蛋仔派对", "第五人格", "阴阳师", "金铲铲", "英雄联盟手游", "lol手游", "梦幻西游", "大话西游", "传奇", "贪玩", "斗地主", "麻将", "连连看", "消消乐", "贪吃蛇", "赛车", "射击", "rpg", "mmo", "棋牌"
    )),
    Category("security", "安全防护", listOf(
        "security", "firewall", "antivirus", "privacy", "密码", "安全", "防护", "杀毒", "隐私", "锁屏", "加密",
        "netguard", "360", "手机管家", "腾讯管家", "卡巴斯基", "eset", "猎豹", "纯净", "adb清", "去广告", "反诈", "应用锁", "加密相册", "vault", "keepass", "bitwarden", "密码管理"
    )),
    Category("travel", "出行导航", listOf(
        "navigation", "map", "travel", "location", "transport", "地图", "导航", "出行", "打车", "公交", "地铁", "旅游", "天气",
        "高德", "百度地图", "腾讯地图", "滴滴", "花小猪", "携程", "去哪儿", "飞猪", "同程", "铁路12306", "12306", "铁路", "高铁", "航班", "飞常准", "航旅纵横", "墨迹天气", "彩云天气", "中国天气"
    )),
    Category("shopping", "购物生活", listOf(
        "shopping", "shop", "mall", "buy", "payment", "购物", "电商", "商城", "支付", "外卖", "买菜", "生活", "网购", "淘宝", "天猫", "京东", "拼多多", "闲鱼",
        "唯品会", "苏宁", "网易严选", "小红书", "美团", "饿了么", "口碑", "盒马", "永辉", "山姆", "叮咚买菜", "美团买菜", "支付宝", "微信支付", "云闪付", "12306购票"
    )),
    Category("news", "新闻资讯", listOf(
        "news", "rss", "magazine", "podcast", "新闻", "资讯", "头条", "订阅", "博客", "播客",
        "今日头条", "澎湃", "澎湃新闻", "网易新闻", "腾讯新闻", "新浪新闻", "凤凰新闻", "搜狐新闻", "央视新闻", "人民日报", "新华社", "财新", "华尔街见闻", "雪球", "东方财富", "同花顺", "微博"
    )),
    Category("health", "健康运动", listOf(
        "health", "sport", "fitness", "exercise", "sleep", "跑步", "健康", "运动", "健身", "睡眠", "心率",
        "keep", "咕咚", "悦动圈", "薄荷健康", "喝水提醒", "冥想", "呼吸", "体重", "卡路里", "步数", "小米运动", "zepp"
    )),
    Category("other", "其他", emptyList())
)

object CategoryJson {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }
}
