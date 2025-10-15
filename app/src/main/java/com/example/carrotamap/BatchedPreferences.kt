package com.example.carrotamap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * 批量SharedPreferences写入器
 * 减少磁盘IO操作，提高性能
 */
class BatchedPreferences(
    private val context: Context,
    private val prefsName: String,
    private val batchDelay: Long = 500L
) {
    companion object {
        private const val TAG = "BatchedPreferences"
    }
    
    private val pendingWrites = mutableMapOf<String, Any>()
    private var writeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 添加Float值到待写入队列
     */
    @Synchronized
    fun putFloat(key: String, value: Float) {
        pendingWrites[key] = value
        scheduleWrite()
    }
    
    /**
     * 添加Int值到待写入队列
     */
    @Synchronized
    fun putInt(key: String, value: Int) {
        pendingWrites[key] = value
        scheduleWrite()
    }
    
    /**
     * 添加Boolean值到待写入队列
     */
    @Synchronized
    fun putBoolean(key: String, value: Boolean) {
        pendingWrites[key] = value
        scheduleWrite()
    }
    
    /**
     * 添加Long值到待写入队列
     */
    @Synchronized
    fun putLong(key: String, value: Long) {
        pendingWrites[key] = value
        scheduleWrite()
    }
    
    /**
     * 添加String值到待写入队列
     */
    @Synchronized
    fun putString(key: String, value: String) {
        pendingWrites[key] = value
        scheduleWrite()
    }
    
    /**
     * 调度批量写入任务
     */
    private fun scheduleWrite() {
        writeJob?.cancel()
        writeJob = scope.launch {
            delay(batchDelay)
            flushWrites()
        }
    }
    
    /**
     * 立即执行批量写入
     */
    @Synchronized
    private fun flushWrites() {
        if (pendingWrites.isEmpty()) return
        
        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val writeCount = pendingWrites.size
            
            prefs.edit().apply {
                pendingWrites.forEach { (key, value) ->
                    when (value) {
                        is Float -> putFloat(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is String -> putString(key, value)
                    }
                }
                apply()
            }
            
            Log.d(TAG, "✅ 批量写入完成: $writeCount 个键值对")
            pendingWrites.clear()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 批量写入失败: ${e.message}", e)
        }
    }
    
    /**
     * 强制立即写入所有待处理数据
     */
    fun forceFlush() {
        writeJob?.cancel()
        flushWrites()
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        writeJob?.cancel()
        forceFlush()
        scope.cancel()
    }
}
