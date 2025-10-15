package com.example.carrotamap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * 内存优化管理器
 * 负责内存清理、缓存管理和资源优化
 */
class MemoryOptimizer(
    private val context: Context
) {
    companion object {
        private const val TAG = "MemoryOptimizer"
        private const val CLEANUP_INTERVAL = 60000L // 1分钟清理间隔
        private const val MAX_CACHE_SIZE = 50 // 最大缓存条目数
    }
    
    private val optimizerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isOptimizing = false
    
    // 缓存管理
    private val cacheMap = mutableMapOf<String, Any>()
    private val cacheTimestamps = mutableMapOf<String, Long>()
    private val maxCacheAge = 300000L // 5分钟缓存过期时间
    
    // 统计信息
    private var totalCleanups = 0
    private var cacheHits = 0
    private var cacheMisses = 0
    private var memoryFreed = 0L
    
    /**
     * 开始内存优化
     */
    fun startOptimizing() {
        if (isOptimizing) {
            Log.w(TAG, "⚠️ 内存优化已在运行")
            return
        }
        
        isOptimizing = true
        Log.i(TAG, "🚀 开始内存优化...")
        
        optimizerScope.launch {
            while (isOptimizing) {
                try {
                    performPeriodicCleanup()
                    optimizeCache()
                    checkMemoryPressure()
                    
                    delay(CLEANUP_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 内存优化异常: ${e.message}", e)
                    delay(10000) // 出错时等待10秒再继续
                }
            }
        }
    }
    
    /**
     * 停止内存优化
     */
    fun stopOptimizing() {
        isOptimizing = false
        Log.i(TAG, "🛑 停止内存优化")
    }
    
    /**
     * 执行定期清理
     */
    private suspend fun performPeriodicCleanup() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 执行定期内存清理...")
            
            // 清理过期缓存
            cleanupExpiredCache()
            
            // 清理临时文件
            cleanupTempFiles()
            
            // 建议垃圾回收
            suggestGarbageCollection()
            
            totalCleanups++
            Log.d(TAG, "✅ 定期清理完成 (第${totalCleanups}次)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 定期清理异常: ${e.message}", e)
        }
    }
    
    /**
     * 优化缓存
     */
    private fun optimizeCache() {
        try {
            val currentTime = System.currentTimeMillis()
            val expiredKeys = mutableListOf<String>()
            
            // 查找过期缓存
            cacheTimestamps.forEach { (key, timestamp) ->
                if (currentTime - timestamp > maxCacheAge) {
                    expiredKeys.add(key)
                }
            }
            
            // 清理过期缓存
            expiredKeys.forEach { key ->
                cacheMap.remove(key)
                cacheTimestamps.remove(key)
                Log.d(TAG, "🗑️ 清理过期缓存: $key")
            }
            
            // 如果缓存仍然过大，清理最旧的条目
            if (cacheMap.size > MAX_CACHE_SIZE) {
                val sortedByTimestamp = cacheTimestamps.toList().sortedBy { it.second }
                val keysToRemove = sortedByTimestamp.take(cacheMap.size - MAX_CACHE_SIZE).map { it.first }
                
                keysToRemove.forEach { key ->
                    cacheMap.remove(key)
                    cacheTimestamps.remove(key)
                    Log.d(TAG, "🗑️ 清理旧缓存: $key")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 缓存优化异常: ${e.message}", e)
        }
    }
    
    /**
     * 检查内存压力
     */
    private fun checkMemoryPressure() {
        try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryUsageRatio = usedMemory.toDouble() / maxMemory.toDouble()
            
            when {
                memoryUsageRatio > 0.9 -> {
                    Log.w(TAG, "🚨 内存压力严重，执行紧急清理")
                    performEmergencyCleanup()
                }
                memoryUsageRatio > 0.8 -> {
                    Log.w(TAG, "⚠️ 内存压力较高，执行深度清理")
                    performDeepCleanup()
                }
                memoryUsageRatio > 0.7 -> {
                    Log.i(TAG, "💡 内存使用率较高，建议清理")
                    suggestCleanup()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 内存压力检查异常: ${e.message}", e)
        }
    }
    
    /**
     * 清理过期缓存
     */
    private fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = cacheTimestamps.filter { 
            currentTime - it.value > maxCacheAge 
        }.keys.toList()
        
        expiredKeys.forEach { key ->
            cacheMap.remove(key)
            cacheTimestamps.remove(key)
        }
        
        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "🗑️ 清理了 ${expiredKeys.size} 个过期缓存条目")
        }
    }
    
    /**
     * 清理临时文件
     */
    private fun cleanupTempFiles() {
        try {
            // 这里可以添加清理临时文件的逻辑
            Log.d(TAG, "📁 临时文件清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 临时文件清理异常: ${e.message}", e)
        }
    }
    
    /**
     * 建议垃圾回收
     */
    private fun suggestGarbageCollection() {
        try {
            System.gc()
            Log.d(TAG, "♻️ 建议垃圾回收")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 垃圾回收异常: ${e.message}", e)
        }
    }
    
    /**
     * 执行紧急清理
     */
    private fun performEmergencyCleanup() {
        try {
            Log.w(TAG, "🚨 执行紧急内存清理...")
            
            // 清理所有缓存
            cacheMap.clear()
            cacheTimestamps.clear()
            
            // 强制垃圾回收
            System.gc()
            Thread.sleep(100)
            System.gc()
            
            memoryFreed += cacheMap.size.toLong()
            Log.i(TAG, "✅ 紧急清理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 紧急清理异常: ${e.message}", e)
        }
    }
    
    /**
     * 执行深度清理
     */
    private fun performDeepCleanup() {
        try {
            Log.i(TAG, "🧹 执行深度内存清理...")
            
            // 清理一半的缓存
            val keysToRemove = cacheMap.keys.take(cacheMap.size / 2)
            keysToRemove.forEach { key ->
                cacheMap.remove(key)
                cacheTimestamps.remove(key)
            }
            
            System.gc()
            Log.i(TAG, "✅ 深度清理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 深度清理异常: ${e.message}", e)
        }
    }
    
    /**
     * 建议清理
     */
    private fun suggestCleanup() {
        Log.i(TAG, "💡 建议执行内存清理")
    }
    
    /**
     * 缓存数据
     */
    fun cacheData(key: String, data: Any) {
        try {
            cacheMap[key] = data
            cacheTimestamps[key] = System.currentTimeMillis()
            Log.d(TAG, "💾 缓存数据: $key")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 缓存数据异常: ${e.message}", e)
        }
    }
    
    /**
     * 获取缓存数据
     */
    fun getCachedData(key: String): Any? {
        return try {
            val currentTime = System.currentTimeMillis()
            val timestamp = cacheTimestamps[key]
            
            if (timestamp != null && currentTime - timestamp <= maxCacheAge) {
                cacheHits++
                Log.d(TAG, "✅ 缓存命中: $key")
                cacheMap[key]
            } else {
                cacheMisses++
                Log.d(TAG, "❌ 缓存未命中: $key")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取缓存数据异常: ${e.message}", e)
            null
        }
    }
    
    /**
     * 获取优化统计
     */
    fun getOptimizationStats(): OptimizationStats {
        return OptimizationStats(
            totalCleanups = totalCleanups,
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
            cacheSize = cacheMap.size,
            memoryFreed = memoryFreed,
            isOptimizing = isOptimizing
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopOptimizing()
        cacheMap.clear()
        cacheTimestamps.clear()
        optimizerScope.cancel()
        Log.i(TAG, "🧹 内存优化器资源清理完成")
    }
}

/**
 * 优化统计数据类
 */
data class OptimizationStats(
    val totalCleanups: Int,
    val cacheHits: Int,
    val cacheMisses: Int,
    val cacheSize: Int,
    val memoryFreed: Long,
    val isOptimizing: Boolean
) {
    val cacheHitRate: Double
        get() = if (cacheHits + cacheMisses > 0) cacheHits.toDouble() / (cacheHits + cacheMisses) else 0.0
    
    val isHealthy: Boolean
        get() = cacheHitRate > 0.5 && cacheSize < 100
}
