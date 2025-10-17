package com.example.tfgy999

import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

object DirectBufferPool {
    private const val MAX_POOL_SIZE = 120
    private val bufferPool = ConcurrentLinkedQueue<ByteBuffer>()
    private val bufferUsageCount = AtomicInteger(0)

    fun acquire(size: Int): ByteBuffer? {
        if (size <= 0) {
            Timber.w("请求的缓冲区大小无效: $size → Invalid buffer size requested: $size")
            return null
        }
        val availableBuffer = bufferPool.find { it.capacity() >= size }
        if (availableBuffer != null && bufferPool.remove(availableBuffer)) {
            availableBuffer.clear()
            bufferUsageCount.incrementAndGet()
            return availableBuffer // 明确返回 ByteBuffer?
        }
        return try {
            val newBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            newBuffer.rewind() // 确保缓冲区从头开始
            bufferUsageCount.incrementAndGet()
            newBuffer // 明确返回 ByteBuffer
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "内存分配失败，大小: $size → Memory allocation failed, size: $size")
            null
        }
    }

    fun release(buffer: ByteBuffer?) {
        if (buffer == null || !buffer.isDirect) return
        if (bufferPool.size < MAX_POOL_SIZE) bufferPool.offer(buffer)
        bufferUsageCount.decrementAndGet()
    }
}
