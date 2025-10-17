package com.example.tfgy999

import java.util.concurrent.atomic.AtomicLong

class LockFreeRingBuffer<T>(private val capacity: Int) {
    private val buffer = ArrayDeque<T>(capacity)
    private val putIndex = AtomicLong(0)
    private val getIndex = AtomicLong(0)

    fun offer(item: T): Boolean {
        val pos = putIndex.getAndUpdate { (it + 1) % capacity }
        if (pos != getIndex.get()) {
            buffer.add(pos.toInt(), item)
            return true
        }
        return false
    }

    fun poll(): T? {
        val pos = getIndex.getAndUpdate { (it + 1) % capacity }
        return if (pos != putIndex.get()) buffer[pos.toInt()] else null
    }
}