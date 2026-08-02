package com.appdian.store.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource
import com.appdian.store.AppDianApp
import com.appdian.store.data.GroupResult
import com.appdian.store.data.StoreRepository
import com.appdian.store.data.VersionEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/** 从 Application 取容器的工厂 */
@Suppress("UNCHECKED_CAST")
class AppViewModelFactory(private val app: AppDianApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DiscoveryViewModel::class.java) ->
            DiscoveryViewModel(app.storeRepository) as T
        modelClass.isAssignableFrom(SearchViewModel::class.java) ->
            SearchViewModel(app.storeRepository) as T
        modelClass.isAssignableFrom(DetailViewModel::class.java) ->
            DetailViewModel(app.storeRepository, app.categoryRepository) as T
        modelClass.isAssignableFrom(CategoryViewModel::class.java) ->
            CategoryViewModel(app.storeRepository, app.categoryRepository) as T
        modelClass.isAssignableFrom(SourcesViewModel::class.java) ->
            SourcesViewModel(app.sourceRepository, app) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(app.categoryRepository, app.settingsStore) as T
        modelClass.isAssignableFrom(DownloadsViewModel::class.java) ->
            DownloadsViewModel() as T
        modelClass.isAssignableFrom(UpdateViewModel::class.java) ->
            UpdateViewModel(com.appdian.store.data.UpdateChecker()) as T
        else -> throw IllegalArgumentException("unknown VM: ${modelClass.name}")
    }
}

// ---------------- 发现页 ----------------

data class DiscoveryUiState(
    val loading: Boolean = false,
    val groups: List<GroupResult> = emptyList(),
    val hasSources: Boolean = true
)

class DiscoveryViewModel(private val repo: StoreRepository) : ViewModel() {

    private val _ui = MutableStateFlow(DiscoveryUiState())
    val ui: StateFlow<DiscoveryUiState> = _ui.asStateFlow()

    init { refresh() }

    /**
     * 刷新：并发抓取所有源的发现栏目，**谁先完成谁先上屏**。
     * [force] = true 时强制重新抓取（下拉刷新/按钮）；false 时优先用 5 分钟缓存（秒回，不重复加载）。
     * 首帧置 loading；已有内容时保留旧内容直到新结果逐条替换。
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (_ui.value.groups.isEmpty()) _ui.value = _ui.value.copy(loading = true)
            val collected = LinkedHashMap<Pair<String, String>, GroupResult>()
            repo.discoverFlow(force).collect { g ->
                collected[g.source.sourceName to g.title] = g
                _ui.value = DiscoveryUiState(
                    loading = true,
                    groups = collected.values.toList(),
                    hasSources = collected.isNotEmpty()
                )
            }
            _ui.value = DiscoveryUiState(
                loading = false,
                groups = collected.values.toList(),
                hasSources = collected.isNotEmpty()
            )
        }
    }
}

// ---------------- 搜索 ----------------

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val groups: List<GroupResult> = emptyList(),
    val searched: Boolean = false
)

class SearchViewModel(private val repo: StoreRepository) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    fun onQueryChange(q: String) { _ui.value = _ui.value.copy(query = q) }

    /**
     * 搜索：数据复用优先——
     * 1. 先用发现页缓存做本地匹配（秒出，不发起网络）
     * 2. 再并发搜所有源，哪个先出先上屏（有缓存则秒回）
     * 本地与在线结果按同名同版本去重合并。
     */
    fun search() {
        val q = _ui.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _ui.value = SearchUiState(query = q, searching = true)
            // 同一应用源只保留一个 group（本地发现缓存的多栏目 + 在线搜索合并），
            // 避免重复 sourceName 导致列表 key 冲突崩溃，也避免搜索结果里同一源出现多块
            val groups = LinkedHashMap<String, GroupResult>()
            val seen = HashSet<String>()

            // 1. 本地缓存匹配（发现页已加载的数据，立即展示）
            repo.localMatches(q).forEach { g ->
                val kept = g.items.filter { seen.add(com.appdian.store.data.StoreRepository.dedupKey(it)) }
                if (kept.isNotEmpty()) {
                    groups[g.source.sourceName] = com.appdian.store.data.StoreRepository.mergeSameSourceGroup(groups[g.source.sourceName], g, kept, preferOnlineTitle = false)
                    _ui.value = SearchUiState(query = q, searching = true, groups = groups.values.toList(), searched = true)
                }
            }

            // 2. 在线搜索（缓存优先，结果与本地去重）
            repo.searchFlow(q).collect { g ->
                val kept = g.items.filter { seen.add(com.appdian.store.data.StoreRepository.dedupKey(it)) }
                groups[g.source.sourceName] = com.appdian.store.data.StoreRepository.mergeSameSourceGroup(groups[g.source.sourceName], g, kept, preferOnlineTitle = true)
                _ui.value = SearchUiState(query = q, searching = true, groups = groups.values.toList(), searched = true)
            }
            _ui.value = SearchUiState(query = q, searching = false, groups = groups.values.toList(), searched = true)
        }
    }
}

// ---------------- 详情 ----------------

data class DetailUiState(
    val loading: Boolean = true,
    val item: AppItem? = null,
    val error: String? = null,
    val source: AppSource? = null,
    /** 同名应用的所有版本（按版本降序，高版本在前） */
    val versions: List<VersionEntry> = emptyList(),
    /** 当前选中的版本号 */
    val activeVersion: String? = null
)

class DetailViewModel(
    private val repo: StoreRepository,
    private val categoryRepo: com.appdian.store.data.CategoryRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    /**
     * 列表条目（进入详情前）的判定分类 id。
     * 详情页展示的分类必须以「列表条目」判定为准，与分类页保持一致——
     * 因为详情规则抓到的 item 摘要/字段可能不同，导致关键词映射漂移。
     */
    private var listCategoryId: String? = null

    fun load(source: AppSource, item: AppItem) {
        // 用进入详情前的列表条目判定一次（分类页同一判定链）
        listCategoryId = categoryRepo.classifier().classify(item, source)
        _ui.value = DetailUiState(loading = true, item = item, source = source)
        viewModelScope.launch {
            // 1. 先抓当前条目详情（快速显示）；详情没抓到图标时回退用列表条目图标
            val detailItem = runCatching { repo.detail(source, item) }.getOrDefault(item)
                .let { d -> if (d.icon.isNullOrBlank()) d.copy(icon = item.icon) else d }
            // 2. 聚合同名应用的所有版本（后台，失败静默）；至少保留当前条目这一个版本
            val versions = runCatching {
                com.appdian.store.data.VersionAggregator.aggregate(
                    item.name,
                    repo.searchFlow(item.name, dedup = false).toList(),
                    source.sourceName
                )
            }.getOrDefault(emptyList())
            val finalVersions = ensureCurrentVersion(versions, item, source)
            // 3. 默认显示最高版本：最高版本不是当前条目时切过去
            val best = finalVersions.firstOrNull()
            if (best != null && !sameVersion(best.version, detailItem.version)) {
                loadVersion(best, finalVersions)
            } else {
                _ui.value = DetailUiState(
                    loading = false, item = detailItem, source = source,
                    versions = finalVersions, activeVersion = detailItem.version
                )
            }
        }
    }

    /** 详情条目缺少图标/摘要时，回退用列表条目的字段 */
    private fun mergeListFields(detail: AppItem, list: AppItem): AppItem {
        var d = detail
        if (d.icon.isNullOrBlank()) d = d.copy(icon = list.icon)
        if (d.summary.isNullOrBlank()) d = d.copy(summary = list.summary)
        return d
    }

    /** 聚合结果为空或没包含当前条目时，把当前条目加进去，保证版本区始终可见 */
    private fun ensureCurrentVersion(
        versions: List<VersionEntry>,
        item: AppItem,
        source: AppSource
    ): List<VersionEntry> =
        if (versions.any { sameVersion(it.version, item.version) }) versions
        else versions + VersionEntry(item.version, item, source)

    /** 用户点版本选择器切换到某版本：抓该版本条目的详情并展示 */
    fun selectVersion(version: String) {
        val st = _ui.value
        if (st.loading) return
        val entry = st.versions.firstOrNull { sameVersion(it.version, version) } ?: return
        _ui.value = st.copy(loading = true)
        viewModelScope.launch {
            val detailItem = runCatching { repo.detail(entry.source, entry.item) }.getOrDefault(entry.item)
                .let { d -> mergeListFields(d, entry.item) }
            // 分类以该版本列表条目判定为准
            listCategoryId = categoryRepo.classifier().classify(entry.item, entry.source)
            _ui.value = st.copy(loading = false, item = detailItem, source = entry.source, activeVersion = entry.version)
        }
    }

    private suspend fun loadVersion(entry: VersionEntry, versions: List<VersionEntry>) {
        val detailItem = runCatching { repo.detail(entry.source, entry.item) }.getOrDefault(entry.item)
            .let { d -> mergeListFields(d, entry.item) }
        listCategoryId = categoryRepo.classifier().classify(entry.item, entry.source)
        _ui.value = DetailUiState(
            loading = false, item = detailItem, source = entry.source,
            versions = versions, activeVersion = entry.version
        )
    }

    private fun sameVersion(a: String?, b: String?): Boolean =
        a?.trim() == b?.trim()

    /** 当前条目的判定分类（基于进入详情前的列表条目，保证与分类页一致） */
    fun currentCategory(item: AppItem, source: AppSource): com.appdian.store.data.Category? =
        categoryRepo.get(listCategoryId ?: "")

    /** 用户手动归类：null=清除覆盖恢复自动；UNCATEGORIZED=强制未分类 */
    fun setOverride(item: AppItem, source: AppSource, categoryId: String?) {
        val key = com.appdian.store.data.CategoryClassifier.itemKey(source.sourceName, item)
        if (categoryId == null) categoryRepo.removeOverride(key) else categoryRepo.setOverride(key, categoryId)
    }
}

// ---------------- 设置 ----------------

class SettingsViewModel(
    private val categoryRepo: com.appdian.store.data.CategoryRepository,
    private val settings: com.appdian.store.data.SettingsStore
) : ViewModel() {

    /** 当前全局默认 User-Agent */
    fun userAgent(): String = settings.defaultUserAgent

    /** 保存全局默认 User-Agent */
    fun setUserAgent(v: String) {
        settings.defaultUserAgent = v
    }

    /** 导出完整分类配置（分类表 + 手动覆盖） */
    fun exportConfig(): String = categoryRepo.exportConfig()

    /** 导入分类配置；失败返回错误信息 */
    fun importConfig(raw: String): String? =
        runCatching { categoryRepo.importConfig(raw) }
            .fold(onSuccess = { null }, onFailure = { it.message ?: "导入失败" })
}

// ---------------- 源管理 ----------------

data class SourcesUiState(
    val sources: List<AppSource> = emptyList(),
    val importing: Boolean = false,
    val importError: String? = null,
    val importSuccess: String? = null
)

class SourcesViewModel(
    private val sourceRepo: com.appdian.store.data.SourceRepository,
    private val app: Application
) : ViewModel() {

    private val _ui = MutableStateFlow(SourcesUiState())
    val ui: StateFlow<SourcesUiState> = _ui.asStateFlow()

    init { reload() }

    fun reload() {
        _ui.value = _ui.value.copy(
            sources = runCatching { sourceRepo.list() }.getOrDefault(emptyList())
        )
    }

    fun toggle(name: String, enabled: Boolean) {
        sourceRepo.setEnabled(name, enabled)
        reload()
    }

    fun delete(name: String) {
        sourceRepo.delete(name)
        reload()
    }

    fun import(raw: String) {
        val result = sourceRepo.import(raw)
        result
            .onSuccess {
                _ui.value = _ui.value.copy(importSuccess = "已导入：${it.sourceName}", importError = null)
                reload()
            }
            .onFailure { e ->
                _ui.value = _ui.value.copy(importError = e.message ?: "导入失败", importSuccess = null)
            }
    }

    fun clearImportFeedback() {
        _ui.value = _ui.value.copy(importError = null, importSuccess = null)
    }

    fun exportAll(): String = sourceRepo.exportAll()

    fun readRaw(name: String): String? =
        sourceRepo.list().firstOrNull { it.sourceName == name }?.let {
            com.appdian.engine.model.Sources.json.encodeToString(com.appdian.engine.model.AppSource.serializer(), it)
        }
}

// ---------------- 检查更新 ----------------

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class HasUpdate(val info: com.appdian.store.data.UpdateInfo) : UpdateUiState
    data object NoUpdate : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateViewModel(
    private val checker: com.appdian.store.data.UpdateChecker
) : ViewModel() {

    private val _ui = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val ui: StateFlow<UpdateUiState> = _ui.asStateFlow()

    /** 检查更新：查 GitHub latest release，与本地版本号比较 */
    fun check() {
        viewModelScope.launch {
            _ui.value = UpdateUiState.Checking
            val r = checker.check()
            _ui.value = if (r.isSuccess) {
                val info = r.getOrNull()
                when {
                    info == null -> UpdateUiState.Error("仓库还没有发布版本")
                    com.appdian.store.data.UpdateChecker.isNewer(info.version, com.appdian.store.ui.APP_VERSION_NAME) ->
                        UpdateUiState.HasUpdate(info)
                    else -> UpdateUiState.NoUpdate
                }
            } else {
                UpdateUiState.Error("检查失败：${r.exceptionOrNull()?.message ?: "网络异常"}")
            }
        }
    }

    fun reset() { _ui.value = UpdateUiState.Idle }
}

fun appViewModelFactory(app: AppDianApp) = AppViewModelFactory(app)
