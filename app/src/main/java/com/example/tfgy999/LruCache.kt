package com.example.tfgy999

class LruCache<K, V>(private val maxSize: Int) {
    private val cache = LinkedHashMap<K, V>(maxSize, 0.75f, true)

    fun get(key: K, create: () -> V): V {
        return cache[key] ?: create().also { put(key, it) }
    }

    fun put(key: K, value: V) {
        cache[key] = value
        if (cache.size > maxSize) {
            val iterator = cache.iterator()
            iterator.next()
            iterator.remove()
        }
    }
}