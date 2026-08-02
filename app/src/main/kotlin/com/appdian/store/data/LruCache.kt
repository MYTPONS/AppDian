package com.appdian.store.data

/**
 * 最近最少使用（LRU）缓存：容量上限 [maxSize]，
 * 新数据写入时若已满，自动淘汰「最久未使用」的那条。
 * get/put 都会刷新条目活跃度（最近用过的最后才被淘汰）。
 *
 * 支持「保护键」：[protect] 的键不会被淘汰（下载换源/换链接等关键数据保护），
 * 全被保护导致超出上限时允许暂时超限，保护解除后恢复淘汰。
 */
class LruCache<K, V>(private val maxSize: Int) {

    private val map = LinkedHashMap<K, V>(16, 0.75f, true)  // accessOrder=true
    private val protectedKeys = HashSet<K>()

    @Synchronized
    fun get(key: K): V? = map[key]

    /** 标记保护：该键在淘汰时跳过（直到 unprotect） */
    @Synchronized
    fun protect(key: K) { protectedKeys.add(key) }

    @Synchronized
    fun unprotect(key: K) { protectedKeys.remove(key) }

    @Synchronized
    fun isProtected(key: K): Boolean = key in protectedKeys

    /** 按条件批量保护现有项（如应用名匹配） */
    @Synchronized
    fun protectWhere(predicate: (K, V) -> Boolean) {
        map.forEach { (k, v) -> if (predicate(k, v)) protectedKeys.add(k) }
    }

    /** 按条件批量解除保护 */
    @Synchronized
    fun unprotectWhere(predicate: (K, V) -> Boolean) {
        map.forEach { (k, v) -> if (predicate(k, v)) protectedKeys.remove(k) }
    }

    /**
     * 写入（已存在则刷新值并置为最新使用）。
     * 超出上限时淘汰最旧的未保护键，返回被淘汰的 key（没有则 null）。
     */
    @Synchronized
    fun put(key: K, value: V): K? {
        map[key] = value
        var evicted: K? = null
        while (map.size > maxSize) {
            val it = map.keys.iterator()
            var found = false
            while (it.hasNext()) {
                val k = it.next()
                if (k == key || k in protectedKeys) continue  // 刚写入的与保护键跳过
                if (evicted == null) evicted = k  // 记最旧被淘汰的那条
                it.remove()
                found = true
                break
            }
            if (!found) break  // 全是保护项：允许暂时超限，等保护解除再淘汰
        }
        return evicted
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun clear() { map.clear(); protectedKeys.clear() }

    @Synchronized
    fun contains(key: K): Boolean = map.containsKey(key)

    val size: Int get() = synchronized(this) { map.size }

    val keys: List<K> get() = synchronized(this) { map.keys.toList() }

    val values: List<V> get() = synchronized(this) { map.values.toList() }
}
