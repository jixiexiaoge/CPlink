package com.example.carrotamap

import android.util.Log

/**
 * 环形缓冲区 - 用于高效存储广播数据
 * 避免频繁的列表重建和内存分配
 */
class CircularBuffer<T>(private val capacity: Int) {
    companion object {
        private const val TAG = "CircularBuffer"
    }
    
    private val buffer = Array<Any?>(capacity) { null }
    private var head = 0
    private var size = 0
    
    /**
     * 添加元素到缓冲区
     * 时间复杂度: O(1)
     */
    @Synchronized
    fun add(item: T) {
        buffer[head] = item
        head = (head + 1) % capacity
        if (size < capacity) size++
    }
    
    /**
     * 获取所有元素（按添加顺序）
     * 时间复杂度: O(n)
     */
    @Synchronized
    fun getAll(): List<T> {
        return try {
            (0 until size).mapNotNull { index ->
                val bufferIndex = (head - size + index + capacity) % capacity
                @Suppress("UNCHECKED_CAST")
                buffer[bufferIndex] as? T
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取所有元素失败: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 清空缓冲区
     */
    @Synchronized
    fun clear() {
        for (i in buffer.indices) {
            buffer[i] = null
        }
        head = 0
        size = 0
    }
    
    /**
     * 获取当前大小
     */
    @Synchronized
    fun getSize(): Int = size
    
    /**
     * 获取容量
     */
    fun getCapacity(): Int = capacity
}
