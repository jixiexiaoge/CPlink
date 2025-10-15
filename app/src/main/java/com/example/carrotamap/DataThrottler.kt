package com.example.carrotamap

import android.util.Log

/**
 * 数据限流器 - 控制数据处理频率
 * 避免过于频繁的数据处理导致系统过载
 */
class DataThrottler(private val minInterval: Long = 100L) {
    companion object {
        private const val TAG = "DataThrottler"
    }
    
    private var lastProcessTime = 0L
    private var droppedCount = 0
    private var processedCount = 0
    
    /**
     * 检查是否应该处理当前数据
     * @return true 如果应该处理，false 如果应该跳过
     */
    @Synchronized
    fun shouldProcess(): Boolean {
        val now = System.currentTimeMillis()
        return if (now - lastProcessTime >= minInterval) {
            lastProcessTime = now
            processedCount++
            
            // 每处理100次记录一次统计
            if (processedCount % 100 == 0 && droppedCount > 0) {
                Log.d(TAG, "📊 限流统计: 已处理=$processedCount, 已丢弃=$droppedCount")
                droppedCount = 0
            }
            
            true
        } else {
            droppedCount++
            false
        }
    }
    
    /**
     * 重置限流器
     */
    @Synchronized
    fun reset() {
        lastProcessTime = 0L
        droppedCount = 0
        processedCount = 0
    }
    
    /**
     * 获取统计信息
     */
    @Synchronized
    fun getStats(): Pair<Int, Int> = Pair(processedCount, droppedCount)
}
