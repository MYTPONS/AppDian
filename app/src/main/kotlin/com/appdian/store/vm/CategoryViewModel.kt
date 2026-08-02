package com.appdian.store.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appdian.engine.model.AppItem
import com.appdian.engine.model.AppSource
import com.appdian.store.data.Category
import com.appdian.store.data.CategoryClassifier
import com.appdian.store.data.CategoryRepository
import com.appdian.store.data.GroupResult
import com.appdian.store.data.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 一条已分类条目：条目 + 来源 + 判定出的分类（categoryId=null 表示未分类） */
data class CategorizedEntry(
    val item: AppItem,
    val source: AppSource,
    val categoryId: String?,
    val category: Category?
)

data class CategoryUiState(
    val loading: Boolean = false,
    val categories: List<Category> = emptyList(),
    /** 全部已分类条目（含未分类），分类页与详情页共用 */
    val entries: List<CategorizedEntry> = emptyList(),
    val hasSources: Boolean = true
)

/**
 * 分类页共享 VM：一次性拉取所有发现栏目 → 走分类判定链打标签 →
 * 分类列表页 / 分类详情页 / 分类管理页共用同一份数据。
 */
class CategoryViewModel(
    private val repo: StoreRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(CategoryUiState())
    val ui: StateFlow<CategoryUiState> = _ui.asStateFlow()

    init { refresh() }

    /**
     * 刷新：发现栏目（缓存优先）→ 分类判定 → 更新条目表。
     * [force] = true 强制重新抓取（下拉刷新）；false 时 5 分钟内直接复用发现页缓存，
     * 打开分类页不再重复加载。已有数据时不闪 loading。
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (_ui.value.entries.isEmpty()) {
                _ui.value = CategoryUiState(loading = true, categories = categoryRepo.list(), hasSources = _ui.value.hasSources)
            }
            val groups = repo.discover(force)
            val classifier = categoryRepo.classifier()
            val entries = groups.flatMap { g ->
                g.items.map { item ->
                    val id = classifier.classify(item, g.source, g.category)
                    CategorizedEntry(item, g.source, id, categoryRepo.get(id ?: ""))
                }
            }
            _ui.value = CategoryUiState(
                loading = false,
                categories = categoryRepo.list(),
                entries = entries,
                hasSources = groups.isNotEmpty()
            )
        }
    }

    /** 某分类（或未分类 null）的条目 */
    fun entriesFor(categoryId: String?): List<CategorizedEntry> =
        _ui.value.entries.filter { it.categoryId == categoryId }

    /** 各分类计数：categoryId(null=未分类) -> 条目数 */
    fun counts(): Map<String?, Int> = _ui.value.entries.groupingBy { it.categoryId }.eachCount()

    // ---------------- 手动覆盖 ----------------

    /**
     * 用户手动归类：
     *  - categoryId=null → 清除覆盖，恢复自动判定
     *  - categoryId=UNCATEGORIZED → 强制归入未分类
     */
    fun setOverride(item: AppItem, source: AppSource, categoryId: String?) {
        val key = CategoryClassifier.itemKey(source.sourceName, item)
        when {
            categoryId == null -> categoryRepo.removeOverride(key)
            else -> categoryRepo.setOverride(key, categoryId)
        }
        refresh()
    }

    // ---------------- 批量归类 ----------------

    /**
     * 批量归类（批量编辑页用）：
     *  - categoryId=null → 清除覆盖，恢复自动判定
     *  - categoryId=UNCATEGORIZED → 强制归入未分类
     */
    fun setOverrides(entries: List<CategorizedEntry>, categoryId: String?) {
        entries.forEach { e ->
            val key = CategoryClassifier.itemKey(e.source.sourceName, e.item)
            if (categoryId == null) categoryRepo.removeOverride(key) else categoryRepo.setOverride(key, categoryId)
        }
        refresh()
    }

    // ---------------- 分类管理 ----------------

    fun addCategory(c: Category) {
        categoryRepo.add(c)
        refreshCategories()
    }

    fun deleteCategory(id: String) {
        categoryRepo.delete(id)
        refreshCategories()
    }

    /** 整体保存（编辑后） */
    fun saveCategories(list: List<Category>) {
        categoryRepo.save(list)
        refreshCategories()
    }

    private fun refreshCategories() {
        _ui.value = _ui.value.copy(categories = categoryRepo.list())
    }
}
